const {test} = require('node:test')
const assert = require('node:assert/strict')
const {readFileSync} = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const source = readFileSync(path.resolve(__dirname, '../build/pages/garage/index.js'), 'utf8')
  .trim().replace(/^export default /, '')
const slot = {slot:'fr_window', key:'card/vehicle/window/pixels', label:'关窗'}

const square = {screenShape:'rect', screenWidth:336, screenHeight:336}
const designWidth = require('../src/manifest.json').config.designWidth
const binding = value => typeof value === 'function' ? value() : value

// Style entries in the compiled page pair a selector list with its declarations.
function compiledRule(page, name) {
  const entry = page.style.find(([selectors]) => selectors.some(([, selector]) => selector === name))
  assert.ok(entry, `Missing compiled style for ${name}`)
  return entry[1]
}

// Evaluate the compiled UX bindings, including conditional list items and click handlers.
// This is a component test, not a substitute for the firmware's text and gesture renderer.
const aiot = {
  __ci__({__opts__: opts}, children) { return opts.shown() ? children() : [] },
  __cf__({__opts__: opts}, children) { return opts.exp().__list__.map((item, i) => children(i, item)) },
  __ce__(tag, {__opts__: opts}, children) {
    if (opts.show && !opts.show()) return []
    return {tag, type:opts.type, children:Array.from(children.flat(Infinity)), click:opts.events?.click,
      value:binding(opts.value), src:binding(opts.src), disabled:binding(opts.disabled),
      classes:Array.from(binding(opts.classList) || []), style:opts.style?.() || {}}
  }
}

function setup(device = {getInfo: ({success}) => success(square)}, offline = false, hour = 12, store) {
  const sent = []
  const timers = new Map()
  let now = 100000
  let next = 0
  const connection = {send: message => sent.push(message.data)}
  const runtime = {
    Date: class extends Date { static now() { return now } getHours() { return hour } },
    aiot,
    $translateStyle$: css => Object.fromEntries(css.split(';').map(s => s.trim().split(/:\s*/))),
    $app_require$: name => ({
      '@app-module/system.interconnect':offline ? undefined : {instance: () => connection},
      '@app-module/system.storage':store, '@app-module/system.device':device
    })[name],
    setTimeout(fn, delay) { timers.set(++next, {fn, delay}); return next },
    clearTimeout(timer) { timers.delete(timer) }
  }
  const exports = {}
  vm.runInNewContext('(' + source + ')', runtime)(runtime, runtime, runtime, exports, null)
  const component = {}
  exports.entry(component)
  const page = Object.assign(component.default, component.default.private || component.default.data || {})
  page.onInit()
  page.onShow()
  function reply(payload = {}) {
    connection.onmessage({data: JSON.stringify({v:1, id:sent.at(-1).id, actions:[slot],
      data:{vehicle:'测试车辆', range:'320', battery:'52%', fuel:'17L', time:'14:30'}, ...payload})})
  }
  return {page, sent, reply, connection, timers, feed: () => page.template(page).children[1].children[0], advance(ms) { now += ms }}
}

test('home uses only current widget slots and refuses read-only actions', () => {
  const s = setup()
  assert.equal(s.sent[0].selectedActions, true)
  s.reply({actions:[{...slot, category:'WINDOW', enabled:false}]})
  assert.equal(s.page.rows.length, 1)
  assert.equal(s.page.rows[0].left.controlName, '关窗')
  assert.equal(s.page.rows[0].right.slot, '')
  s.page.prepare(slot.slot)
  assert.deepEqual(s.sent.map(m=>m.type), ['status'])
  s.page.refresh()
  s.reply({actions:[]})
  assert.equal(s.page.rows.length, 0)
})

test('both tiles show only control names; confirmation and wire payload use the action label', () => {
  const s = setup()
  const climate = {slot:'climate', key:'ac', category:'CLIMATE', label:'关空调', displayLabel:'空调.开', controlName:'前排空调', controlState:'open'}
  const loading = {...slot, displayLabel:'车窗.加载'} // Older companion without controlState or controlName.
  const actions = [loading, climate]
  s.reply({actions})
  assert.equal(s.page.rows[0].left.controlName, '车窗')
  assert.equal(s.page.rows[0].right.controlName, '前排空调')
  s.page.prepare(loading.slot)
  assert.equal(s.sent.length, 1)
  s.page.prepare(climate.slot)
  s.reply({actions, token:'wrong', label:climate.displayLabel})
  assert.equal(s.page.mode, 'home')
  s.page.prepare(climate.slot)
  s.reply({actions, token:'valid', label:climate.label})
  assert.equal(s.page.question, '关空调')
  s.advance(501)
  s.page.confirm()
  assert.deepEqual(s.sent.map(m => m.type), ['status', 'prepare', 'prepare', 'execute'])
  for (const m of s.sent) {
    assert.equal(m.supportsControlState, true)
    if (m.type !== 'status') assert.equal(m.label, '关空调')
    assert.equal('displayLabel' in m, false)
  }
})

test('status displays official units and zero values', () => {
  const s = setup()
  assert.equal(s.sent[0].type, 'status')
  s.reply({data:{range:'0', battery:'0%', fuel:'0L'}})
  assert.equal(s.page.range, '0')
  assert.equal(s.page.battery, '0%')
  assert.equal(s.page.fuel, '0L')
})
test('actions require a matched prepare reply and explicit confirmation', () => {
  const s = setup()
  s.reply(); s.page.controls(); s.page.prepare(slot.slot)
  assert.equal(s.sent.at(-1).type, 'prepare')
  s.connection.onmessage({data: JSON.stringify({v:1, id:'stale', token:'bad', label:'关窗'})})
  assert.equal(s.page.mode, 'home')
  s.reply({token:'test-ticket', label:'关窗'})
  assert.equal(s.page.mode, 'confirm')
  s.page.confirm()
  assert.equal(s.sent.at(-1).type, 'prepare')
  s.advance(501)
  s.page.confirm(); s.page.confirm()
  assert.deepEqual(s.sent.map(m => m.type), ['status', 'prepare', 'execute'])
  assert.equal(s.sent.at(-1).token, 'test-ticket')
})
test('cancel, timeout and hiding a page never execute a command', () => {
  for (const action of ['cancel', 'expire', 'hide']) {
    const s = setup()
    s.reply(); s.page.prepare(slot.slot); s.reply({token:'t', label:'关窗'})
    if (action === 'cancel') s.page.cancel()
    if (action === 'expire') Array.from(s.timers.values()).find(t => t.delay === 12000).fn()
    if (action === 'hide') s.page.onHide()
    s.page.confirm()
    assert.equal(s.sent.filter(m => m.type === 'execute').length, 0)
  }
})
test('disconnected watch drops actionable slots and reports uncertain dispatch', () => {
  const s = setup()
  s.reply(); s.page.prepare(slot.slot); s.reply({token:'t', label:'关窗'}); s.advance(501); s.page.confirm()
  s.connection.onclose()
  assert.equal(s.page.actions.length, 0)
  assert.match(s.page.note, /待核对/)
  assert.equal(s.page.choice, null)
})
for (const [shape, w, h, columns] of [
  ['circle', 466, 466, 2], ['rect', 336, 336, 2], ['pill-shaped', 192, 490, 1],
  ['rect', 336, 396, 1], ['rect', 396, 336, 1], [undefined, 336, 396, 1],
  ['rect', 192, 490, 1], [undefined, 192, 490, 1],
  ['circle', 240, 240, 1], ['rect', 240, 240, 1], ['pill-shaped', 152, 486, 1]
]) {
  test(`scrollable home and confirmation on ${shape || 'unreported'} ${w}x${h}`, () => {
    const s = setup({getInfo: ({success}) => success({screenShape:shape, screenWidth:w, screenHeight:h})})
    const actions = Array.from({length:5}, (_, i) => ({...slot, slot:'s' + i, controlName:'车窗' + i,
      category:'WINDOW', label:'关闭车窗', controlState:'open'}))
    s.reply({actions, data:{vehicle:'较长的测试车辆名称', condition:'车辆已停稳，可以远程控制', battery:'100%', fuel:'52L'}})
    const feed = s.feed()
    assert.equal(feed.tag, 'list')
    const width = parseFloat(feed.style.width) * w / 100
    const height = parseFloat(feed.style.height) * h / 100
    if (shape === 'rect' && h / w < 1.65) {
      assert.ok(width >= 0.9 * w && height >= 0.9 * h, 'square should use its corners')
    } else {
      // The viewport corners must lie inside the circular caps at both scroll limits.
      assert.ok(Math.hypot(width / 2, Math.max(0, (height - (h - w)) / 2)) <= w / 2)
    }
    const energy = feed.children.find(row => row.type === 'energy')
    assert.equal(energy.style['flex-direction'], columns === 1 ? 'column' : 'row')
    assert.equal(energy.children[0].value, '电量 100%')
    const rows = feed.children.filter(row => row.type === 'actionpair')
    assert.deepEqual(rows.map(row => row.children.length), columns === 1 ? [1,1,1,1,1] : [2,2,1])
    const tiles = rows.flatMap(row => row.children)
    for (const [i, tile] of tiles.entries()) {
      assert.equal(tile.children[0].children[0].tag, 'image')
      assert.equal(tile.children[1].value, '车窗' + i)
      assert.ok(parseFloat(tile.style.height) * w / designWidth >= 44)
      assert.ok(parseFloat(tile.children[1].style['font-size']) * w / designWidth >= 16)
    }
    const refresh = feed.children.at(-1).children[0]
    assert.equal(refresh.value, '刷新车况')
    assert.ok(parseFloat(refresh.style.height) * w / designWidth >= 44)
    assert.ok(parseFloat(refresh.style['font-size']) * w / designWidth >= 16)
    tiles.at(-1).click()
    assert.equal(s.sent.at(-1).slot, 's4')
    s.reply({actions, token:'t', label:'关闭车窗'})
    const confirm = s.feed()
    assert.equal(confirm.tag, 'list')
    assert.deepEqual(confirm.style, feed.style)
    assert.equal(confirm.children[0].children[2].value, '关闭车窗')
    const buttons = confirm.children.slice(1).map(row => row.children[0])
    assert.deepEqual(buttons.map(button => button.value), ['确认操作','取消'])
    assert.ok(buttons.every(button => parseFloat(button.style.height) * w / designWidth >= 44))
    buttons[1].click()
    assert.equal(s.page.mode, 'home')
    assert.equal(s.sent.at(-1).type, 'prepare')
  })
}

test('missing device API stays usable; late device info reflows existing actions', () => {
  let detected
  for (const device of [{}, {getInfo: options => { detected = options.success }}]) {
    const s = setup(device)
    s.reply({actions:[slot, {...slot, slot:'other'}]})
    assert.equal(s.page.rows.length, 2)
    if (detected) {
      detected(square)
      assert.equal(s.page.rows.length, 1)
      assert.equal(s.page.rows[0].right.slot, 'other')
      assert.equal(s.sent.length, 1)
    }
  }
})

test('icon badge uses reported state with either phone appearance; loading and unknown never arm', () => {
  for (const appearance of ['light','dark']) {
    const s = setup()
    const actions = ['open','closed','loading','unknown','unrecognized'].map((state, i) =>
      ({...slot, slot:'a' + i, controlState:state, controlName:'车窗', displayLabel:'车窗.开', enabled:true}))
    s.reply({actions, appearance})
    const tiles = s.feed().children.filter(row => row.type === 'actionpair').flatMap(row => row.children)
    const colors = {on:'#59cf91',tile:'#192b3e',loading:'#ffcc46',unknown:'#a2abb6'}
    assert.deepEqual(tiles.map(tile => tile.children[0].style['background-color']),
      [colors.on, colors.tile, colors.loading, colors.unknown, colors.unknown])
    assert.deepEqual(tiles.map(tile => tile.disabled), [false,false,true,true,true])
    for (const [i, tile] of tiles.entries()) {
      const badge = tile.children[0]
      assert.deepEqual(badge.classes, i === 2 ? ['badge','pulse'] : ['badge'])
      const darkGlyph = i !== 1
      assert.equal(badge.children[0].src, '/common/controls/' + (darkGlyph ? 'day/' : '') + 'OTHER.png')
    }
    for (const tile of tiles.slice(2)) tile.click()
    assert.equal(s.sent.length, 1)
    tiles[0].click()
    s.reply({actions:[{...actions[0], controlState:'loading'}], token:'stale', label:slot.label})
    assert.equal(s.page.mode, 'home')
    assert.equal(s.page.choice, null)
  }
})

test('compiled badge transitions color and only pulses while its slot is loading', () => {
  const s = setup()
  const badge = compiledRule(s.page, 'badge')
  assert.equal(badge.transitionProperty, 'background-color')
  assert.ok(parseFloat(badge.transitionDuration) > 0)
  const pulse = compiledRule(s.page, 'pulse')
  assert.equal(pulse.animationIterationCount, 'infinite')
  assert.ok(parseFloat(pulse.animationDuration) > 0)
  const frames = JSON.parse(compiledRule(s.page, pulse.animationName).keyframes)
  assert.deepEqual(frames.map(frame => frame.time), [0,50,100])
  assert.equal(frames[0].opacity, frames[2].opacity)
  assert.ok(frames[1].opacity > 0 && frames[1].opacity < frames[0].opacity)

  for (const states of [['closed','loading'], ['loading','open'], ['unknown','closed']]) {
    s.reply({actions:states.map((controlState, i) => ({...slot, slot:'s' + i, controlState}))})
    const tiles = s.feed().children.find(row => row.type === 'actionpair').children
    assert.deepEqual(tiles.map(tile => tile.children[0].classes.includes('pulse')),
      states.map(state => state === 'loading'))
    s.page.refresh()
  }
})

test('execute starts five seconds of status-only polling; unchanged loading keeps its badge', () => {
  const s = setup()
  s.reply(); s.page.prepare(slot.slot); s.reply({token:'once', label:slot.label})
  s.advance(501); s.page.confirm()
  const loading = {...slot, controlName:'车窗', controlState:'loading'}
  s.reply({actions:[loading]})
  const rows = s.page.rows
  for (let tick = 1; tick <= 10; tick++) {
    const timer = s.timers.get(s.page.pollTimer)
    assert.equal(timer.delay, 500)
    s.advance(timer.delay); timer.fn()
    assert.equal(s.sent.at(-1).type, 'status')
    assert.equal('token' in s.sent.at(-1), false)
    s.reply({actions:[tick === 10 ? {...loading, controlState:'closed'} : loading]})
    if (tick < 10) assert.equal(s.page.rows, rows)
  }
  assert.equal(s.page.rows[0].left.state, 'closed')
  assert.equal(s.timers.get(s.page.pollTimer).delay, 5000)
  assert.equal(s.sent.filter(m => m.type === 'execute').length, 1)
  s.page.onHide()
  assert.equal(s.page.rows.length, 0)
  assert.equal(s.timers.size, 0)
})

test('refresh is followed by a new status read', () => {
  const s = setup()
  s.reply(); s.page.refresh()
  assert.equal(s.sent.at(-1).type, 'refresh')
  s.reply({note:'等候领克更新'})
  Array.from(s.timers.values()).find(t => t.delay === 1600).fn()
  assert.equal(s.sent.at(-1).type, 'status')
})
test('official confirmation is presented on the phone', () => {
  const s = setup()
  s.reply({actions:[], status:'请在手机领克卡片确认'})
  assert.equal(s.page.actions.length, 0)
  assert.match(s.page.status, /在手机/)
})
test('leaving action page discards late prepare response', () => {
  const s = setup()
  s.reply(); s.page.controls(); s.page.prepare(slot.slot); s.page.home()
  s.reply({token:'t', label:'关窗'})
  assert.equal(s.page.mode, 'home')
  assert.equal(s.page.choice, null)
})

test('home paints a single readable default with missing firmware modules and either phone appearance', () => {
  const offline = setup({}, true)
  const title = offline.feed().children[0]
  assert.equal(title.children[0].value, '小领通')
  assert.match(title.children[1].value, /连接/)
  assert.equal(offline.sent.length, 0)
  const s = setup()
  for (const appearance of ['light', 'dark']) {
    s.reply({appearance})
    assert.equal(s.feed().children[0].children[0].value, '测试车辆')
    assert.equal(compiledRule(s.page, 'page').backgroundColor, '#0a0d14')
    s.page.refresh()
  }
})

test('a lost connection retries immediately, backs off and never repeats a prepared action', () => {
  const s = setup()
  s.reply(); s.page.prepare(slot.slot)
  s.connection.onclose()
  assert.equal(s.page.choice, null)
  const retry = delay => Array.from(s.timers.values()).find(t => t.delay === delay).fn()
  retry(0)
  assert.equal(s.sent.at(-1).type, 'status')
  s.connection.onerror()
  retry(1000)
  s.reply()
  s.connection.onclose()
  retry(0)
  assert.equal(s.sent.filter(message => message.type === 'execute').length, 0)
})

test('band sky backgrounds and manual presets stay available offline', () => {
  const s = setup({}, true)
  assert.equal(s.page.skyMode, 'auto')
  const theme = s.feed().children.find(row => row.type === 'theme').children[0]
  theme.click()
  assert.equal(s.page.template(s.page).children[0].src, '/common/sky/day.png')
  theme.click()
  assert.equal(s.page.template(s.page).children[0].src, '/common/sky/night.png')
  theme.click()
  assert.equal(s.page.skyMode, 'auto')
})

test('automatic sky uses local daylight boundaries and a failed theme store cannot prevent startup', () => {
  const broken = {get() { throw Error('unavailable') }, set() { throw Error('unavailable') }}
  for (const [hour, expect] of [[5,'night'],[6,'day'],[17,'day'],[18,'night']]) {
    const s = setup({}, true, hour, broken)
    assert.equal(s.page.skyImage, '/common/sky/' + expect + '.png')
    s.page.cycleSky()
    assert.equal(s.page.skyMode, 'day')
  }
  let delayed
  const s = setup({}, true, 12, {get({success}) { delayed = success }})
  s.page.cycleSky()
  delayed('night')
  assert.equal(s.page.skyMode, 'day')
})
