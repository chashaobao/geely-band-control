export const DEFAULT_CAR_IMAGE = '/common/images/starshine-8.png'
const CACHE_KEY = 'car-image-cache'
const B64_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

export function decodeBase64(input) {
  if (typeof input !== 'string' || !input.length) return new Uint8Array(0)
  const clean = input.replace(/=+$/, '')
  const out = new Uint8Array(Math.floor((clean.length * 3) / 4))
  let pos = 0
  for (let i = 0; i < clean.length; i += 4) {
    const c1 = B64_ALPHABET.indexOf(clean[i])
    const c2 = B64_ALPHABET.indexOf(clean[i + 1])
    const c3 = i + 2 < clean.length ? B64_ALPHABET.indexOf(clean[i + 2]) : -1
    const c4 = i + 3 < clean.length ? B64_ALPHABET.indexOf(clean[i + 3]) : -1
    if (c1 < 0 || c2 < 0) return new Uint8Array(0)
    const triple = (c1 << 18) | (c2 << 12) | ((c3 >= 0 ? c3 : 0) << 6) | (c4 >= 0 ? c4 : 0)
    out[pos++] = (triple >> 16) & 0xff
    if (c3 >= 0 && pos < out.length) out[pos++] = (triple >> 8) & 0xff
    if (c4 >= 0 && pos < out.length) out[pos++] = triple & 0xff
  }
  return out
}

export function readCachedCarImage(onRead) {
  try {
    const storage = require('@system.storage')
    storage.get({
      key: CACHE_KEY,
      success: value => {
        if (!value) return
        try {
          const parsed = JSON.parse(value)
          if (parsed && typeof parsed.hash === 'string' && typeof parsed.uri === 'string' && parsed.uri) {
            onRead(parsed.uri, parsed.hash)
          }
        } catch (_) {
          // Ignore malformed cache.
        }
      }
    })
  } catch (_) {
    // Storage unavailable; keep bundled fallback image.
  }
}

export function saveCarImage(hash, base64Text, previousUri, onSaved) {
  const bytes = decodeBase64(base64Text)
  if (!bytes.length) return
  const targetUri = 'internal://files/car_' + hash + '.png'
  const fallbackDataUri = 'data:image/png;base64,' + base64Text
  const persistMeta = uri => {
    try {
      const storage = require('@system.storage')
      storage.set({ key: CACHE_KEY, value: JSON.stringify({ hash, uri }) })
    } catch (_) {
      // Cache persistence is optional.
    }
  }
  try {
    const file = require('@system.file')
    if (file && typeof file.writeArrayBuffer === 'function') {
      file.writeArrayBuffer({
        uri: targetUri,
        buffer: bytes,
        success: () => {
          if (previousUri && previousUri !== targetUri && previousUri.indexOf('internal://') === 0
              && typeof file.delete === 'function') {
            try {
              file.delete({ uri: previousUri })
            } catch (_) {
              // Ignore cleanup failures.
            }
          }
          persistMeta(targetUri)
          onSaved(targetUri, hash)
        },
        fail: () => {
          onSaved(fallbackDataUri, hash)
        }
      })
      return
    }
  } catch (_) {
    // File API unavailable; fall back to inline data URI for current session.
  }
  onSaved(fallbackDataUri, hash)
}
