import { useState } from 'react'
import { orderPool } from '../../api/pools.js'
import { useAction } from '../../hooks/useAction.js'
import { MAX_PAISE, formatPaise, paiseToInput, parseRupees } from '../../lib/money.js'
import { Button, Card, ErrorText, Field, Input, Section } from '../ui.jsx'

/** Host, LOCKED: final price for every item (line totals) plus shared fees. */
export default function FinalBillForm({ pool, refresh }) {
  const items = pool.members.flatMap((m) => m.items.map((i) => ({ ...i, owner: m.name })))
  const [finals, setFinals] = useState(() => Object.fromEntries(items.map((i) => [i.id, paiseToInput(i.estPricePaise)])))
  const [fees, setFees] = useState('0')
  const { busy, error, run } = useAction(refresh)

  const parsed = items.map((i) => parseRupees(finals[i.id] ?? ''))
  const feesPaise = parseRupees(fees)
  const valid = (v) => v != null && v >= 0 && v <= MAX_PAISE
  const allValid = parsed.every(valid) && valid(feesPaise)
  const total = allValid ? parsed.reduce((a, b) => a + b, 0) + feesPaise : null

  const submit = (e) => {
    e.preventDefault()
    if (!allValid) return
    run(async () => {
      await orderPool(pool.id, {
        feesPaise,
        items: items.map((i, idx) => ({ itemId: i.id, finalPricePaise: parsed[idx] })),
      })
      await refresh()
    })
  }

  return (
    <Section title="Enter final bill">
      <Card>
        <form onSubmit={submit} className="space-y-3">
          <p className="text-sm text-slate-600">
            Enter what each item actually cost (total for the quantity). Use 0 for an unavailable item.
          </p>
          {items.length === 0 && <p className="text-sm text-slate-500">There are no items in this pool.</p>}
          <ul className="space-y-2">
            {items.map((i) => (
              <li key={i.id} className="flex items-center justify-between gap-3">
                <div className="min-w-0 text-sm">
                  <p className="truncate font-medium">
                    {i.name} <span className="text-slate-500">x{i.quantity}</span>
                  </p>
                  <p className="text-xs text-slate-500">
                    {i.owner} · est {formatPaise(i.estPricePaise)}
                  </p>
                </div>
                <div className="w-28 shrink-0">
                  <Input
                    aria-label={`Final price for ${i.name}`}
                    inputMode="decimal"
                    value={finals[i.id] ?? ''}
                    onChange={(e) => setFinals((f) => ({ ...f, [i.id]: e.target.value }))}
                  />
                </div>
              </li>
            ))}
          </ul>
          <Field label="Fees (₹)" hint="Delivery + handling + other shared charges. 0 if you crossed the free-delivery minimum.">
            <Input inputMode="decimal" value={fees} onChange={(e) => setFees(e.target.value)} />
          </Field>
          <p className="text-lg font-semibold">Total: {total == null ? 'enter valid amounts' : formatPaise(total)}</p>
          <ErrorText>{error}</ErrorText>
          <Button type="submit" disabled={busy || !allValid}>
            {busy ? 'Placing...' : 'Mark as ordered'}
          </Button>
        </form>
      </Card>
    </Section>
  )
}
