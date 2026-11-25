const CACHE_KEY = 'vehicle-snapshot'

export function rangeText(value) {
  return typeof value === 'number' && value >= 0 ? String(value) : '—'
}

export function readSnapshot(onRead) {
  try {
    const storage = require('@system.storage')
    storage.get({
      key: CACHE_KEY,
      success: value => {
        if (!value) return
        let snapshot
        try {
          snapshot = JSON.parse(value)
        } catch (_) {
          return
        }
        if (snapshot && snapshot.updatedAt > 0) onRead(snapshot, value)
      }
    })
  } catch (_) {
    // Storage is optional on the first screen and the side card.
  }
}

export function writeSnapshot(value, onFailure) {
  try {
    const storage = require('@system.storage')
    storage.set({ key: CACHE_KEY, value, fail: onFailure })
  } catch (_) {
    onFailure()
  }
}
