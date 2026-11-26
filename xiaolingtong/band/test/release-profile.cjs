// Exercise the compiled release against missing and contradictory firmware screen information.
const assert = require('node:assert/strict')
const {readFileSync} = require('node:fs')
const vm = require('node:vm')
const path = require('node:path')
const [root, profile] = process.argv.slice(2)
assert.ok(['standard', 'pill'].includes(profile))
const script = name => '(' + readFileSync(path.join(root, name), 'utf8').trim().replace(/^export default /, '') + ')'
for (const info of [null, {screenShape:'pill-shaped', screenWidth:192, screenHeight:490}, {screenShape:'rect', screenWidth:336, screenHeight:480}]) {
  const sent = []
  const runtime = {
    $app_require$: name => ({
      '@app-module/system.interconnect': {instance: () => ({send: message => sent.push(message.data)})},
      '@app-module/system.device': {getInfo({success}) { if (info) success(info) }}
    })[name],
    setTimeout() { return 1 }, clearTimeout() {}
  }
  const sandbox = vm.createContext(runtime), exports = {}, component = {}
  vm.runInContext(script('app.js'), sandbox)(runtime, runtime, runtime, {}, null)
  vm.runInContext(script('pages/garage/index.js'), sandbox)(runtime, runtime, runtime, exports, null)
  exports.entry(component)
  const page = Object.assign(component.default, component.default.private || component.default.data || {})
  page.onInit(); page.onReady(); page.onShow()
  assert.equal(page.layout.supportsTheme, profile === 'standard')
  assert.equal(!!sent[0].bandPhoto, profile === 'standard')
  assert.equal(!!sent[0].bandTheme, profile === 'standard')
  if (profile === 'pill') assert.equal(page.layout.single, true)
}
console.log(`${profile}: missing, pill-shaped and rectangular device reports passed`)
