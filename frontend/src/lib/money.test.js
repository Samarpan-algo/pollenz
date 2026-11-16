import { describe, expect, it } from 'vitest'
import { formatPaise, paiseToInput, parseRupees } from './money.js'

describe('formatPaise', () => {
  it('formats paise as rupees', () => {
    expect(formatPaise(1250)).toBe('₹12.50')
    expect(formatPaise(0)).toBe('₹0.00')
    expect(formatPaise(5)).toBe('₹0.05')
    expect(formatPaise(123456)).toBe('₹1,234.56')
  })
})

describe('parseRupees', () => {
  it('parses without float errors', () => {
    expect(parseRupees('12')).toBe(1200)
    expect(parseRupees('12.5')).toBe(1250)
    expect(parseRupees('12.50')).toBe(1250)
    expect(parseRupees('0.07')).toBe(7)
    expect(parseRupees('19.99')).toBe(1999)
    expect(parseRupees('1.15')).toBe(115) // 1.15 * 100 === 114.99999999999999
    expect(parseRupees('.5')).toBe(50)
    expect(parseRupees(' ₹1,234.50 ')).toBe(123450)
  })
  it('rejects invalid input', () => {
    for (const bad of ['', ' ', '.', 'abc', '-5', '1.234', '1.2.3', '1e3']) {
      expect(parseRupees(bad)).toBeNull()
    }
  })
  it('round-trips with paiseToInput', () => {
    for (const p of [0, 1, 99, 100, 1999, 1000000000]) {
      expect(parseRupees(paiseToInput(p))).toBe(p)
    }
  })
})
