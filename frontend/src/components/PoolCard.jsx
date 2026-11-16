import { Link } from 'react-router'
import { formatPaise } from '../lib/money.js'
import { timeLeft } from '../lib/time.js'
import { Card, ProgressBar, StatusBadge, platformLabel } from './ui.jsx'

export default function PoolCard({ pool }) {
  return (
    <Link to={`/pools/${pool.id}`} className="block">
      <Card className="space-y-2 hover:border-accent">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="text-xs font-semibold uppercase text-accent">{platformLabel(pool.platform)}</p>
            <p className="truncate font-medium">{pool.title}</p>
          </div>
          <StatusBadge status={pool.status} />
        </div>
        <ProgressBar current={pool.currentAmountPaise} target={pool.targetAmountPaise} />
        <p className="text-sm text-slate-600">
          {formatPaise(pool.currentAmountPaise)} of {formatPaise(pool.targetAmountPaise)}
        </p>
        <p className="text-xs text-slate-500">
          {pool.memberCount} {pool.memberCount === 1 ? 'member' : 'members'} · host {pool.hostName}
          {pool.status === 'OPEN' && ` · ${timeLeft(pool.closesAt)}`}
        </p>
      </Card>
    </Link>
  )
}
