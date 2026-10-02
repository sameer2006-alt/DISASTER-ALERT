import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

// Mock import.meta.env
if (!globalThis.importMetaEnv) {
  globalThis.importMetaEnv = {}
}

describe('Frontend API and WebSocket Configuration', () => {
  test('wsUrl formats correctly without doubling /ws', () => {
    function wsUrlHelper(configured, origin) {
      if (configured) {
        const trimmed = configured.replace(/\/+$/, '')
        return trimmed.endsWith('/ws') ? trimmed : `${trimmed}/ws`
      }
      if (origin) {
        return `${origin}/ws`
      }
      return '/ws'
    }

    // Default window.location.origin
    assert.equal(wsUrlHelper(undefined, 'http://localhost:5173'), 'http://localhost:5173/ws')
    assert.equal(wsUrlHelper(undefined, 'https://disaster.example.com'), 'https://disaster.example.com/ws')

    // If configured without /ws
    assert.equal(wsUrlHelper('http://localhost:8080', 'http://localhost:5173'), 'http://localhost:8080/ws')

    // If configured with /ws
    assert.equal(wsUrlHelper('http://localhost:8080/ws', 'http://localhost:5173'), 'http://localhost:8080/ws')

    // If configured with trailing slashes
    assert.equal(wsUrlHelper('http://localhost:8080/ws///', 'http://localhost:5173'), 'http://localhost:8080/ws')
  })

  test('api.js exports do not contain dead verifyLogin methods', async () => {
    const apiModule = await import('./api.js')
    assert.equal(apiModule.authApi.verifyLogin, undefined)
    assert.equal(apiModule.orgApi.verifyLogin, undefined)
  })

  test('api and nlpApi have proper relative baseURL defaults', async () => {
    const apiModule = await import('./api.js')
    assert.equal(apiModule.API_URL, '')
    assert.equal(apiModule.PYTHON_URL, '/nlp')
    assert.equal(apiModule.api.defaults.baseURL, '')
    assert.equal(apiModule.nlpApi.defaults.baseURL, '/nlp')
  })
})
