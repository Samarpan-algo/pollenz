import { useNavigate } from 'react-router'
import { cancelPool, joinPool, leavePool, lockPool } from '../../api/pools.js'
import { useAction } from '../../hooks/useAction.js'
import { Button, ErrorText } from '../ui.jsx'

/** Join / Leave / Lock / Cancel. Final bill and payments have their own components. */
export default function PoolActions({ pool, isHost, isMember, refresh }) {
  const navigate = useNavigate()
  const { busy, error, run } = useAction(refresh)
  const act = (fn) => run(async () => {
    await fn()
    await refresh()
  })

  const confirmCancel = () => {
    if (window.confirm('Cancel this pool for everyone?')) act(() => cancelPool(pool.id))
  }

  const buttons = []
  if (pool.status === 'OPEN') {
    if (!isMember) {
      buttons.push(
        <Button key="join" disabled={busy} onClick={() => act(() => joinPool(pool.id))}>
          Join this pool
        </Button>,
      )
    }
    if (isMember && !isHost) {
      buttons.push(
        <Button
          key="leave"
          variant="secondary"
          disabled={busy}
          onClick={() =>
            run(async () => {
              await leavePool(pool.id)
              navigate(`/communities/${pool.communityId}`)
            })
          }
        >
          Leave pool
        </Button>,
      )
    }
    if (isHost) {
      buttons.push(
        <Button key="lock" disabled={busy} onClick={() => act(() => lockPool(pool.id))}>
          Lock pool
        </Button>,
      )
    }
  }
  if (isHost && (pool.status === 'OPEN' || pool.status === 'LOCKED')) {
    buttons.push(
      <Button key="cancel" variant="danger" disabled={busy} onClick={confirmCancel}>
        Cancel pool
      </Button>,
    )
  }

  if (buttons.length === 0 && !error) return null
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-2">{buttons}</div>
      <ErrorText>{error}</ErrorText>
    </div>
  )
}
