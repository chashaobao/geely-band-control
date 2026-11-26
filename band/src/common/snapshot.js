const CACHE_KEY = 'vehicle-snapshot'

export function rangeText(value) {
  return typeof value === 'number' && value >= 0 ? String(value) : '—'
}

export function readSnapshot(onRead, onDone) {
  let finished = false
  const finish = () => {
    if (finished) return
    finished = true
    if (typeof onDone === 'function') onDone()
  }
  try {
    const storage = require('@system.storage')
    storage.get({
      key: CACHE_KEY,
      success: value => {
        if (value) {
          let snapshot
          try {
            snapshot = JSON.parse(value)
          } catch (_) {
            snapshot = null
          }
          if (snapshot && snapshot.updatedAt > 0) onRead(snapshot, value)
        }
        finish()
      },
      fail: finish
    })
  } catch (_) {
    // Render the first screen even when storage is unavailable.
    finish()
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
