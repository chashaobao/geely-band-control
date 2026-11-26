const maxIconChars = 2048
// Fixed filenames bound storage. Rotate paths to avoid immediately reusing a rendered URI.
// Pending native writes hold their slots even if the page or action has been replaced.
const slots = new Array(16).fill(false)
let nextSlot = 0

function reserveSlot() {
  for (let offset = 0; offset < slots.length; offset++) {
    const slot = (nextSlot + offset) % slots.length
    if (slots[slot]) continue
    slots[slot] = true
    nextSlot = (slot + 1) % slots.length
    return slot
  }
  return -1
}

export function createBandIconCache(file, decodePng, onReady) {
  const entries = new Map()
  // Packaged images are immutable. Cache their decode status separately from the
  // reusable file slots, whose content belongs to one current widget key only.
  const bundledReady = new Map()

  function release(entry) {
    if (!entry.writing && entry.slot !== undefined) {
      slots[entry.slot] = false
      entry.slot = undefined
    }
  }

  function discard(key, entry) {
    entries.delete(key)
    release(entry)
  }

  return {
    nextKey(actions) {
      if (!file || typeof file.writeArrayBuffer !== 'function' || slots.every(Boolean)) return ''
      const missing = actions.find(item => typeof item.key === 'string' && item.key && !entries.has(item.key))
      return missing ? missing.key : ''
    },
    sync(actions) {
      entries.forEach((entry, key) => {
        if (!actions.some(item => item.key === key)) discard(key, entry)
      })
    },
    clear() {
      entries.forEach((entry, key) => discard(key, entry))
    },
    glyph(key) {
      const entry = entries.get(key)
      return entry && entry.uri
    },
    isLoaded(key, src) {
      const entry = entries.get(key)
      return bundledReady.has(src) || !!(entry && entry.uri === src && entry.loaded)
    },
    imageLoaded(key, src) {
      if (src && src.indexOf('/common/controls/') === 0) {
        if (bundledReady.has(src)) return false
        bundledReady.set(src, true)
        return true
      }
      const entry = entries.get(key)
      if (!entry || !entry.uri || entry.uri !== src || entry.loaded) return false
      entry.loaded = true
      return true
    },
    imageFailed(key, src) {
      if (bundledReady.delete(src)) return true
      const entry = entries.get(key)
      if (!entry || !entry.uri || entry.uri !== src) return false
      entry.uri = ''
      entry.loaded = false
      release(entry)
      return true
    },
    receive(key, actions) {
      const action = actions.find(item => item.key === key)
      if (!action || entries.has(key)) return
      const entry = {}
      // A missing field (old phone), explicit empty, or failed image settles this current key.
      entries.set(key, entry)
      const text = action.bandIconPng
      if (typeof text !== 'string' || text.length > maxIconChars || text.length % 4 !== 0) return
      const bytes = decodePng(text, 72)
      if (!bytes) return
      const slot = reserveSlot()
      if (slot < 0) { entries.delete(key); return }
      const uri = 'internal://cache/garage-control-' + slot + '.png'
      entry.slot = slot
      entry.writing = true
      const finish = success => {
        if (!entry.writing) return
        entry.writing = false
        if (!success || entries.get(key) !== entry) { release(entry); return }
        entry.uri = uri
        onReady(key)
      }
      // The API specifies Uint8Array. Xiaomi's cross-device sample passes its
      // backing ArrayBuffer instead. Retry that form once on a parameter error.
      function write(buffer, allowRetry) {
        const fail = (data, code) => {
          if (!entry.writing) return
          if (allowRetry && Number(code) === 202 && entries.get(key) === entry) write(bytes.buffer, false)
          else finish(false)
        }
        try { file.writeArrayBuffer({uri:uri, buffer:buffer, position:0, append:false, success:() => finish(true), fail:fail}) }
        catch (error) { fail(null, 202) }
      }
      const start = () => {
        if (entries.get(key) === entry) write(bytes, true)
        else finish(false)
      }
      // Native binary writes preserve the old tail on a reused path. Truncate
      // with a single byte first, then overwrite the file from byte zero.
      if (typeof file.writeText === 'function') {
        try { file.writeText({uri:uri, text:' ', append:false, success:start, fail:() => finish(false)}) }
        catch (error) { finish(false) }
      } else start()
    }
  }
}
