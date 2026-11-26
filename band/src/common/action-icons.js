const LYNK_ICONS = {
  '车锁': 'lock', '上锁': 'lock-open', '解锁': 'lock',
  '车窗': 'window_open', '窗全开': 'window_open', '窗微开': 'window_vent', '关窗': 'window_open',
  '后备箱': 'trunk', '寻车': 'find', '快升温': 'heat', '快降温': 'cool'
}

export function actionIcon(action, direction, lynkLabel) {
  if (action === 'lock' && direction === 'lock') return '/common/images/lock-open.png'
  if (action.indexOf('lynk_') === 0) {
    const glyph = Object.prototype.hasOwnProperty.call(LYNK_ICONS, lynkLabel) ? LYNK_ICONS[lynkLabel] : 'settings'
    return '/common/images/' + glyph + '.png'
  }
  return '/common/images/' + action + '.png'
}
