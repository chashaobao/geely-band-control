export function actionIcon(action, direction) {
  if (action === 'climate') return '/cards/garage/images/climate.png'
  if (action === 'lock' && direction === 'lock') return '/common/images/lock-open.png'
  return '/common/images/' + action + '.png'
}
