import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { getCommunity } from '../api/communities.js'
import { createPool, listPools } from '../api/pools.js'
import { useAction } from '../hooks/useAction.js'
import { useLoad } from '../hooks/useLoad.js'
import { MAX_PAISE, parseRupees } from '../lib/money.js'
import PoolCard from '../components/PoolCard.jsx'
import {
  Button,
  Card,
  Empty,
  ErrorText,
  Field,
  Input,
  Loading,
  PLATFORMS,
  POOL_STATUSES,
  Section,
  Select,
  platformLabel,
} from '../components/ui.jsx'

function InviteCode({ code }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      window.prompt('Copy the invite code:', code)
    }
  }
  return (
    <div className="flex items-center gap-2 text-sm">
      <span className="text-slate-600">Invite code</span>
      <code className="rounded bg-slate-100 px-2 py-1 font-mono text-base tracking-wider">{code}</code>
      <Button variant="secondary" onClick={copy} className="px-3 py-1">
        {copied ? 'Copied' : 'Copy'}
      </Button>
    </div>
  )
}

function StartPoolForm({ communityId }) {
  const navigate = useNavigate()
  const [platform, setPlatform] = useState('BLINKIT')
  const [title, setTitle] = useState('')
  const [target, setTarget] = useState('')
  const [closesAt, setClosesAt] = useState('')
  const [pickupSpot, setPickupSpot] = useState('')
  const [formError, setFormError] = useState(null)
  const { busy, error, run } = useAction()

  const submit = (e) => {
    e.preventDefault()
    const targetAmountPaise = parseRupees(target)
    if (targetAmountPaise == null || targetAmountPaise <= 0 || targetAmountPaise > MAX_PAISE) {
      return setFormError('Enter a valid target amount in rupees, e.g. 500 or 499.50.')
    }
    const closes = new Date(closesAt)
    if (Number.isNaN(closes.getTime()) || closes.getTime() <= Date.now()) {
      return setFormError('Closing time must be in the future.')
    }
    setFormError(null)
    run(async () => {
      const pool = await createPool(communityId, {
        platform,
        title: title.trim(),
        targetAmountPaise,
        closesAt: closes.toISOString(),
        pickupSpot: pickupSpot.trim(),
      })
      navigate(`/pools/${pool.id}`)
    })
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <Field label="App">
        <Select value={platform} onChange={(e) => setPlatform(e.target.value)}>
          {PLATFORMS.map((p) => (
            <option key={p} value={p}>
              {platformLabel(p)}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="Title">
        <Input required maxLength={200} placeholder="Late-night snacks" value={title} onChange={(e) => setTitle(e.target.value)} />
      </Field>
      <Field label="Target amount (₹)" hint="Usually the free-delivery minimum.">
        <Input required inputMode="decimal" placeholder="500" value={target} onChange={(e) => setTarget(e.target.value)} />
      </Field>
      <Field label="Closes at">
        <Input required type="datetime-local" value={closesAt} onChange={(e) => setClosesAt(e.target.value)} />
      </Field>
      <Field label="Pickup spot">
        <Input required maxLength={200} placeholder="Hostel B gate" value={pickupSpot} onChange={(e) => setPickupSpot(e.target.value)} />
      </Field>
      <ErrorText>{formError || error}</ErrorText>
      <Button type="submit" disabled={busy}>
        {busy ? 'Starting...' : 'Start pool'}
      </Button>
    </form>
  )
}

export default function CommunityPage() {
  const { id } = useParams()
  const [status, setStatus] = useState('')
  const [showForm, setShowForm] = useState(false)
  const community = useLoad(() => getCommunity(id), [id])
  const pools = useLoad(() => listPools(id, status), [id, status])

  if (community.loading && !community.data) return <Loading />
  if (community.error) return <ErrorText>{community.error.message}</ErrorText>
  const c = community.data

  return (
    <>
      <div className="space-y-2">
        <h1 className="text-2xl font-bold">{c.name}</h1>
        <InviteCode code={c.inviteCode} />
      </div>

      <Section
        title="Pools"
        action={
          <Button variant={showForm ? 'secondary' : 'primary'} onClick={() => setShowForm((v) => !v)}>
            {showForm ? 'Close' : 'Start a pool'}
          </Button>
        }
      >
        {showForm && (
          <Card>
            <StartPoolForm communityId={id} />
          </Card>
        )}
        <Select aria-label="Filter by status" value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">All statuses</option>
          {POOL_STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </Select>
        {pools.loading && !pools.data && <Loading />}
        {pools.error && <ErrorText>{pools.error.message}</ErrorText>}
        {pools.data && pools.data.length === 0 && <Empty>No pools here yet.</Empty>}
        {pools.data && pools.data.length > 0 && (
          <ul className="space-y-2">
            {pools.data.map((p) => (
              <li key={p.id}>
                <PoolCard pool={p} />
              </li>
            ))}
          </ul>
        )}
      </Section>

      <Section title={`Members (${c.members.length})`}>
        <Card>
          <ul className="divide-y divide-slate-100">
            {c.members.map((m) => (
              <li key={m.userId} className="flex justify-between py-2 text-sm">
                <span>{m.name}</span>
                <span className="text-xs text-slate-500">{m.role}</span>
              </li>
            ))}
          </ul>
        </Card>
      </Section>
    </>
  )
}
