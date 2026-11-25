export function actionIcon(action, direction) {
  if (action === 'lock' && direction === 'lock') return '/common/images/lock-open.png'
  if (action.indexOf('lynk_') === 0) return '/common/images/settings.png'
  return '/common/images/' + action + '.png'
}
