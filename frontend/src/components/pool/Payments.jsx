import { deliverPool, markPaid } from '../../api/pools.js'
import { useAction } from '../../hooks/useAction.js'
import { formatPaise, paiseToInput } from '../../lib/money.js'
import { formatDateTime } from '../../lib/time.js'
import { Button, Card, ErrorText, Section } from '../ui.jsx'

function upiLink({ upiId, hostName, sharePaise }) {
  const e = encodeURIComponent
  return `upi://pay?pa=${e(upiId)}&pn=${e(hostName)}&am=${e(paiseToInput(sharePaise))}&cu=INR`
}

/** Shares and payment state for ORDERED / DELIVERED / SETTLED. */
export default function Payments({ pool, user, isHost, refresh }) {
  const { busy, error, run } = useAction(refresh)
  const payable = pool.status === 'ORDERED' || pool.status === 'DELIVERED'
  const me = pool.members.find((m) => m.userId === user.id)
  const act = (fn) =>
    run(async () => {
      await fn()
      await refresh()
    })

  return (
    <Section
      title="Bill and payments"
      action={
        isHost && pool.status === 'ORDERED' && (
          <Button disabled={busy} onClick={() => act(() => deliverPool(pool.id))}>
            Mark delivered
          </Button>
        )
      }
    >
      {pool.feesPaise != null && <p className="text-sm text-slate-600">Shared fees: {formatPaise(pool.feesPaise)}</p>}

      {me && !isHost && payable && me.sharePaise != null && !me.paid && (
        <Card className="space-y-2 border-accent">
          <p className="font-medium">You owe {formatPaise(me.sharePaise)}</p>
          {pool.hostUpiId ? (
            <a
              href={upiLink({ upiId: pool.hostUpiId, hostName: pool.hostName, sharePaise: me.sharePaise })}
              className="inline-flex rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent-dark"
            >
              Pay via UPI
            </a>
          ) : (
            <p className="text-sm text-slate-600">
              {pool.hostName} has not set a UPI id. Ask them to add one in their profile, or pay them directly.
            </p>
          )}
          <p className="text-xs text-slate-500">After paying, the host marks you as paid.</p>
        </Card>
      )}
      {me && !isHost && me.paid && <p className="text-sm font-medium text-green-700">You have paid your share.</p>}

      <Card>
        <ul className="divide-y divide-slate-100">
          {pool.members.map((m) => (
            <li key={m.memberId} className="flex items-center justify-between gap-2 py-2 text-sm">
              <span className="min-w-0 truncate">
                {m.name}
                {m.userId === pool.hostId && <span className="ml-2 text-xs font-semibold text-accent">HOST</span>}
              </span>
              <span className="flex shrink-0 items-center gap-2">
                <span className="font-medium">{m.sharePaise == null ? '-' : formatPaise(m.sharePaise)}</span>
                <span className={m.paid ? 'text-green-700' : 'text-amber-700'}>
                  {m.paid ? `Paid${m.paidAt ? ` ${formatDateTime(m.paidAt)}` : ''}` : 'Unpaid'}
                </span>
                {isHost && payable && !m.paid && (
                  <Button className="px-2 py-1" disabled={busy} onClick={() => act(() => markPaid(pool.id, m.memberId))}>
                    Mark paid
                  </Button>
                )}
              </span>
            </li>
          ))}
        </ul>
      </Card>
      <ErrorText>{error}</ErrorText>
    </Section>
  )
}
