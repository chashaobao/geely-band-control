export const DEFAULT_LAYOUT = 'rect'

const PROFILES = {
  rect: { profile: 'rect', columns: 2, stackSplit: false, needsScroll: false },
  capsule: { profile: 'capsule', columns: 1, stackSplit: true, needsScroll: true },
  circle: { profile: 'circle', columns: 2, stackSplit: false, needsScroll: true }
}

const CLASS_KEYS = [
  'scrollPage', 'homeScreen', 'range', 'rangeHeader', 'model', 'motion',
  'vehicleImage', 'split', 'energy', 'totalEnergy', 'energyReading',
  'energyIcon', 'distance', 'distanceCompact', 'totalLabel', 'unit',
  'energyTrack', 'energyFill', 'rangeIndicators', 'rangeDot',
  'activeRangeDot', 'vehicleMeta', 'refresh', 'refreshIcon', 'updated',
  'metaDivider', 'connection', 'dot', 'mode', 'settingsIcon', 'empty',
  'grid', 'controlRow', 'toast', 'toastText', 'confirmation',
  'confirmAction', 'confirmIcon', 'confirmTitle', 'confirmButton',
  'cancelButton', 'settingsScreen', 'settingsTitle', 'hapticsSetting',
  'hapticsLabel', 'hapticsSwitch', 'diagnostics', 'diagnosticsTitle',
  'diagnosticsLine', 'settingsBack'
]

const BASE_NAMES = {
  scrollPage: 'scroll-page',
  homeScreen: 'home-screen',
  range: 'range',
  rangeHeader: 'range-header',
  model: 'model',
  motion: 'motion',
  vehicleImage: 'vehicle-image',
  split: 'split',
  energy: 'energy',
  totalEnergy: 'energy total-energy',
  energyReading: 'energy-reading',
  energyIcon: 'energy-icon',
  distance: 'distance',
  distanceCompact: 'distance compact',
  totalLabel: 'total-label',
  unit: 'unit',
  energyTrack: 'energy-track',
  energyFill: 'energy-fill',
  rangeIndicators: 'range-indicators',
  rangeDot: 'range-dot',
  activeRangeDot: 'range-dot active-range-dot',
  vehicleMeta: 'vehicle-meta',
  refresh: 'refresh',
  refreshIcon: 'refresh-icon',
  updated: 'updated',
  metaDivider: 'meta-divider',
  connection: 'connection',
  dot: 'dot',
  mode: 'mode',
  settingsIcon: 'settings-icon',
  empty: 'empty',
  grid: 'grid',
  controlRow: 'control-row',
  toast: 'toast',
  toastText: 'toast-text',
  confirmation: 'confirmation',
  confirmAction: 'confirm-action',
  confirmIcon: 'confirm-icon',
  confirmTitle: 'confirm-title',
  confirmButton: 'confirm-button',
  cancelButton: 'cancel-button',
  settingsScreen: 'settings-screen',
  settingsTitle: 'settings-title',
  hapticsSetting: 'haptics-setting',
  hapticsLabel: 'haptics-label',
  hapticsSwitch: 'haptics-switch',
  diagnostics: 'diagnostics',
  diagnosticsTitle: 'diagnostics-title',
  diagnosticsLine: 'diagnostics-line',
  settingsBack: 'settings-back'
}

export function resolveLayoutProfile(info) {
  if (!info || typeof info !== 'object') return DEFAULT_LAYOUT
  const width = Number(info.screenWidth || info.windowWidth) || 0
  const height = Number(info.screenHeight || info.windowHeight) || 0
  const shape = typeof info.screenShape === 'string' ? info.screenShape.toLowerCase() : ''
  if (shape === 'circle' || (width >= 200 && height >= 200 && Math.abs(width - height) <= 32)) {
    return 'circle'
  }
  if (shape === 'pill-shaped' || shape === 'capsule'
      || (width > 0 && width <= 260 && (height >= 360 || height / width >= 1.8))) {
    return 'capsule'
  }
  return DEFAULT_LAYOUT
}

export function layoutConfig(profile) {
  return PROFILES[profile] || PROFILES[DEFAULT_LAYOUT]
}

export function variantClass(base, profile) {
  if (!profile || profile === DEFAULT_LAYOUT) return base
  const suffix = '-' + profile
  return base + ' ' + base.split(' ').map(name => name + suffix).join(' ')
}

export function layoutClasses(profile) {
  const active = PROFILES[profile] ? profile : DEFAULT_LAYOUT
  const result = {}
  for (const key of CLASS_KEYS) {
    result[key] = variantClass(BASE_NAMES[key], active)
  }
  return result
}

export function resolveWatchHomeSize(info) {
  if (!info || typeof info !== 'object') return 'standard'
  const width = Number(info.screenWidth || info.windowWidth) || 0
  const height = Number(info.screenHeight || info.windowHeight) || 0
  const diameter = width > 0 && height > 0 ? Math.min(width, height) : (width || height)
  if (diameter > 0 && diameter <= 400) return 'compact'
  if (diameter >= 473) return 'large'
  return 'standard'
}

const WATCH_HOME_BASES = {
  mileagePage: 'watch-page watch-mileage-page',
  heroHeader: 'watch-hero-header',
  model: 'watch-model',
  rangeTap: 'watch-range-tap',
  hero: 'watch-hero',
  subrange: 'watch-subrange',
  statusRow: 'watch-status-row',
  statusText: 'watch-status-text',
  refreshIcon: 'watch-refresh-icon',
  car: 'watch-car'
}

export function watchHomeClasses(size) {
  const tier = size === 'compact' || size === 'large' ? size : 'standard'
  const result = {}
  for (const key of Object.keys(WATCH_HOME_BASES)) {
    const base = WATCH_HOME_BASES[key]
    if (tier === 'standard') {
      result[key] = base
    } else {
      const target = key === 'mileagePage' ? 'watch-mileage-page' : base
      result[key] = base + ' ' + target + '-' + tier
    }
  }
  return result
}

export function detectDeviceProfile(onResolved) {
  try {
    const device = require('@system.device')
    if (!device || typeof device.getInfo !== 'function') {
      onResolved(DEFAULT_LAYOUT, 'standard')
      return
    }
    device.getInfo({
      success: info => onResolved(resolveLayoutProfile(info), resolveWatchHomeSize(info)),
      fail: () => onResolved(DEFAULT_LAYOUT, 'standard')
    })
  } catch (_) {
    onResolved(DEFAULT_LAYOUT, 'standard')
  }
}
