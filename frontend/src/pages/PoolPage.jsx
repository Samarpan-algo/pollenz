import { Link, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext.jsx'
import { usePoolDetail } from '../hooks/usePoolDetail.js'
import { formatPaise } from '../lib/money.js'
import { formatDateTime, timeLeft } from '../lib/time.js'
import FinalBillForm from '../components/pool/FinalBillForm.jsx'
import MemberList from '../components/pool/MemberList.jsx'
import Payments from '../components/pool/Payments.jsx'
import PoolActions from '../components/pool/PoolActions.jsx'
import { Card, ErrorText, Loading, ProgressBar, Section, StatusBadge, platformLabel } from '../components/ui.jsx'

const BILLED = ['ORDERED', 'DELIVERED', 'SETTLED']

export default function PoolPage() {
  const { id } = useParams()
  // key resets the polling state when navigating between pools
  return <PoolView key={id} id={id} />
}

function PoolView({ id }) {
  const { user } = useAuth()
  const { pool, error, loading, refresh } = usePoolDetail(id)

  if (loading && !pool) return <Loading />
  if (!pool) return <ErrorText>{error?.message || 'Pool not found.'}</ErrorText>

  const isHost = pool.hostId === user.id
  const isMember = pool.members.some((m) => m.userId === user.id)
  const showShares = BILLED.includes(pool.status) || (pool.status === 'CANCELLED' && pool.members.some((m) => m.sharePaise != null))

  return (
    <>
      <Link to={`/communities/${pool.communityId}`} className="text-sm text-accent underline">
        Back to community
      </Link>

      <Card className="space-y-2">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="text-xs font-semibold uppercase text-accent">{platformLabel(pool.platform)}</p>
            <h1 className="text-xl font-bold">{pool.title}</h1>
          </div>
          <StatusBadge status={pool.status} />
        </div>
        <ProgressBar current={pool.currentAmountPaise} target={pool.targetAmountPaise} />
        <p className="text-sm text-slate-600">
          {formatPaise(pool.currentAmountPaise)} of {formatPaise(pool.targetAmountPaise)}
        </p>
        <p className="text-xs text-slate-500">
          Host {pool.hostName} · pickup {pool.pickupSpot} · closes {formatDateTime(pool.closesAt)}
          {pool.status === 'OPEN' && ` (${timeLeft(pool.closesAt)})`}
        </p>
      </Card>

      {error && <ErrorText>Could not refresh: {error.message}</ErrorText>}

      <PoolActions pool={pool} isHost={isHost} isMember={isMember} refresh={refresh} />

      {isHost && pool.status === 'LOCKED' && <FinalBillForm pool={pool} refresh={refresh} />}
      {showShares && isMember && <Payments pool={pool} user={user} isHost={isHost} refresh={refresh} />}

      <Section title={`Members and items (${pool.members.length})`}>
        <MemberList pool={pool} user={user} refresh={refresh} />
      </Section>
    </>
  )
}
