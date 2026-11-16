import { useState } from 'react'
import { useAction } from '../../hooks/useAction.js'
import { MAX_PAISE, paiseToInput, parseRupees } from '../../lib/money.js'
import { Button, ErrorText, Field, Input } from '../ui.jsx'

/** Add/edit item. `onSubmit({ name, quantity, estPricePaise })` must return a promise. */
export default function ItemForm({ initial, submitLabel, onSubmit, onCancel, onConflict }) {
  const [name, setName] = useState(initial?.name ?? '')
  const [quantity, setQuantity] = useState(String(initial?.quantity ?? 1))
  const [price, setPrice] = useState(initial ? paiseToInput(initial.estPricePaise) : '')
  const [formError, setFormError] = useState(null)
  const { busy, error, run } = useAction(onConflict)

  const submit = (e) => {
    e.preventDefault()
    const qty = Number(quantity)
    const estPricePaise = parseRupees(price)
    if (!Number.isInteger(qty) || qty < 1) return setFormError('Quantity must be a whole number, at least 1.')
    if (estPricePaise == null || estPricePaise <= 0 || estPricePaise > MAX_PAISE) {
      return setFormError('Enter the estimated total price in rupees, e.g. 120 or 49.50.')
    }
    setFormError(null)
    run(async () => {
      await onSubmit({ name: name.trim(), quantity: qty, estPricePaise })
      if (!initial) {
        setName('')
        setQuantity('1')
        setPrice('')
      }
    })
  }

  return (
    <form onSubmit={submit} className="space-y-2 rounded-md bg-slate-50 p-3">
      <Field label="Item">
        <Input required maxLength={200} value={name} onChange={(e) => setName(e.target.value)} />
      </Field>
      <div className="grid grid-cols-2 gap-2">
        <Field label="Quantity">
          <Input required inputMode="numeric" value={quantity} onChange={(e) => setQuantity(e.target.value)} />
        </Field>
        <Field label="Total price (₹)">
          <Input required inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} />
        </Field>
      </div>
      <ErrorText>{formError || error}</ErrorText>
      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? 'Saving...' : submitLabel}
        </Button>
        {onCancel && (
          <Button variant="secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  )
}
