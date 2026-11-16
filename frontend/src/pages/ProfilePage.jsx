import { useState } from 'react'
import { updateMe } from '../api/auth.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { useAction } from '../hooks/useAction.js'
import { Button, Card, ErrorText, Field, Input } from '../components/ui.jsx'

export default function ProfilePage() {
  const { user, updateUser } = useAuth()
  const [name, setName] = useState(user?.name ?? '')
  const [upiId, setUpiId] = useState(user?.upiId ?? '')
  const [saved, setSaved] = useState(false)
  const { busy, error, run } = useAction()

  const submit = (e) => {
    e.preventDefault()
    setSaved(false)
    run(async () => {
      // upiId '' clears it on the server.
      const updated = await updateMe({ name: name.trim(), upiId: upiId.trim() })
      updateUser(updated)
      setName(updated.name ?? '')
      setUpiId(updated.upiId ?? '')
      setSaved(true)
    })
  }

  return (
    <>
      <h1 className="text-2xl font-bold">Profile</h1>
      <Card>
        <form onSubmit={submit} className="space-y-3">
          <p className="text-sm text-slate-600">{user?.email}</p>
          <Field label="Name">
            <Input required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label="UPI id" hint="Shown to members of pools you host so they can pay you. Leave empty to remove.">
            <Input
              placeholder="name@bank"
              maxLength={100}
              autoCapitalize="none"
              value={upiId}
              onChange={(e) => setUpiId(e.target.value)}
            />
          </Field>
          <ErrorText>{error}</ErrorText>
          {saved && <p className="text-sm text-green-700">Saved.</p>}
          <Button type="submit" disabled={busy}>
            {busy ? 'Saving...' : 'Save'}
          </Button>
        </form>
      </Card>
    </>
  )
}
