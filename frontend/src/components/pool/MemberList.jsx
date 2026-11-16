import { useState } from 'react'
import { addItem, deleteItem, updateItem } from '../../api/pools.js'
import { useAction } from '../../hooks/useAction.js'
import { formatPaise } from '../../lib/money.js'
import { Button, Card, ErrorText } from '../ui.jsx'
import ItemForm from './ItemForm.jsx'

function MemberCard({ pool, member, isMe, canEdit, refresh }) {
  const [editingId, setEditingId] = useState(null)
  const { busy, error, run } = useAction(refresh)
  const ordered = member.items.some((i) => i.finalPricePaise != null)

  return (
    <Card className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <p className="font-medium">
          {member.name}
          {isMe && <span className="text-slate-500"> (you)</span>}
          {member.userId === pool.hostId && <span className="ml-2 text-xs font-semibold text-accent">HOST</span>}
        </p>
      </div>

      {member.items.length === 0 && <p className="text-sm text-slate-500">No items yet.</p>}
      <ul className="divide-y divide-slate-100">
        {member.items.map((item) =>
          editingId === item.id ? (
            <li key={item.id} className="py-2">
              <ItemForm
                initial={item}
                submitLabel="Save"
                onConflict={refresh}
                onCancel={() => setEditingId(null)}
                onSubmit={async (body) => {
                  await updateItem(pool.id, item.id, body)
                  setEditingId(null)
                  await refresh()
                }}
              />
            </li>
          ) : (
            <li key={item.id} className="flex items-center justify-between gap-2 py-2 text-sm">
              <span className="min-w-0 truncate">
                {item.name} <span className="text-slate-500">x{item.quantity}</span>
              </span>
              <span className="flex shrink-0 items-center gap-2">
                <span>
                  {ordered && item.finalPricePaise != null ? (
                    <>
                      {formatPaise(item.finalPricePaise)}
                      {item.finalPricePaise !== item.estPricePaise && (
                        <span className="ml-1 text-xs text-slate-400 line-through">{formatPaise(item.estPricePaise)}</span>
                      )}
                    </>
                  ) : (
                    formatPaise(item.estPricePaise)
                  )}
                </span>
                {canEdit && (
                  <>
                    <Button variant="secondary" className="px-2 py-1" disabled={busy} onClick={() => setEditingId(item.id)}>
                      Edit
                    </Button>
                    <Button
                      variant="danger"
                      className="px-2 py-1"
                      disabled={busy}
                      onClick={() =>
                        run(async () => {
                          await deleteItem(pool.id, item.id)
                          await refresh()
                        })
                      }
                    >
                      Delete
                    </Button>
                  </>
                )}
              </span>
            </li>
          ),
        )}
      </ul>
      <ErrorText>{error}</ErrorText>

      {canEdit && (
        <div className="pt-1">
          <p className="mb-2 text-sm font-medium">Add an item</p>
          <ItemForm
            submitLabel="Add item"
            onConflict={refresh}
            onSubmit={async (body) => {
              await addItem(pool.id, body)
              await refresh()
            }}
          />
        </div>
      )}
    </Card>
  )
}

export default function MemberList({ pool, user, refresh }) {
  return (
    <div className="space-y-3">
      {pool.members.map((m) => {
        const isMe = m.userId === user.id
        return (
          <MemberCard
            key={m.memberId}
            pool={pool}
            member={m}
            isMe={isMe}
            canEdit={isMe && pool.status === 'OPEN'}
            refresh={refresh}
          />
        )
      })}
    </div>
  )
}
