import { describe, expect, it } from 'vitest'
import { buildWsUrl } from './realtime.js'

describe('buildWsUrl', () => {
  it('prefers the configured URL', () => {
    expect(buildWsUrl('wss://api.example.com/ws', { protocol: 'https:', host: 'x' })).toBe('wss://api.example.com/ws')
  })
  it('uses ws for http pages', () => {
    expect(buildWsUrl(undefined, { protocol: 'http:', host: 'localhost:5173' })).toBe('ws://localhost:5173/ws')
  })
  it('uses wss for https pages', () => {
    expect(buildWsUrl('', { protocol: 'https:', host: 'pollenz.app' })).toBe('wss://pollenz.app/ws')
  })
})
