import { useState } from 'react'
import { Link } from 'react-router'
import { createCommunity, joinCommunity, listCommunities } from '../api/communities.js'
import { useAction } from '../hooks/useAction.js'
import { useLoad } from '../hooks/useLoad.js'
import { Button, Card, Empty, ErrorText, Field, Input, Loading, Section } from '../components/ui.jsx'

function NameForm({ label, buttonLabel, busyLabel, onSubmit, ...inputProps }) {
  const [value, setValue] = useState('')
  const { busy, error, run } = useAction()
  const submit = (e) => {
    e.preventDefault()
    run(async () => {
      await onSubmit(value.trim())
      setValue('')
    })
  }
  return (
    <form onSubmit={submit} className="space-y-2">
      <Field label={label}>
        <Input required value={value} onChange={(e) => setValue(e.target.value)} {...inputProps} />
      </Field>
      <ErrorText>{error}</ErrorText>
      <Button type="submit" disabled={busy || !value.trim()}>
        {busy ? busyLabel : buttonLabel}
      </Button>
    </form>
  )
}

export default function CommunitiesPage() {
  const { data, error, loading, reload } = useLoad(listCommunities, [])

  return (
    <>
      <h1 className="text-2xl font-bold">Your communities</h1>
      {loading && !data && <Loading />}
      {error && <ErrorText>{error.message}</ErrorText>}
      {data && data.length === 0 && <Empty>You are not in any community yet. Create one or join with an invite code.</Empty>}
      {data && data.length > 0 && (
        <ul className="space-y-2">
          {data.map((c) => (
            <li key={c.id}>
              <Link to={`/communities/${c.id}`} className="block">
                <Card className="flex items-center justify-between hover:border-accent">
                  <span className="font-medium">{c.name}</span>
                  <span className="text-xs text-slate-500">{c.role}</span>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <Card>
          <Section title="Create a community">
            <NameForm
              label="Name"
              buttonLabel="Create"
              busyLabel="Creating..."
              maxLength={100}
              onSubmit={async (name) => {
                await createCommunity(name)
                await reload()
              }}
            />
          </Section>
        </Card>
        <Card>
          <Section title="Join with invite code">
            <NameForm
              label="Invite code"
              buttonLabel="Join"
              busyLabel="Joining..."
              maxLength={32}
              autoCapitalize="characters"
              onSubmit={async (code) => {
                await joinCommunity(code)
                await reload()
              }}
            />
          </Section>
        </Card>
      </div>
    </>
  )
}
