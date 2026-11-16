import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import * as authApi from '../api/auth.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { useAction } from '../hooks/useAction.js'
import { Button, Card, ErrorText, Field, Input } from '../components/ui.jsx'

function AuthShell({ title, children, footer }) {
  return (
    <div className="mx-auto max-w-sm space-y-4 px-4 py-10">
      <h1 className="text-center text-2xl font-bold text-accent">Pollenz</h1>
      <Card className="space-y-4">
        <h2 className="text-lg font-semibold">{title}</h2>
        {children}
      </Card>
      <p className="text-center text-sm text-slate-600">{footer}</p>
    </div>
  )
}

function useSignInRedirect() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  return (res) => {
    signIn(res)
    navigate(location.state?.from || '/', { replace: true })
  }
}

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { busy, error, run } = useAction()
  const done = useSignInRedirect()

  const submit = (e) => {
    e.preventDefault()
    run(async () => done(await authApi.login({ email: email.trim(), password })))
  }

  return (
    <AuthShell
      title="Log in"
      footer={
        <>
          New here?{' '}
          <Link to="/register" className="text-accent underline">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-3">
        <Field label="Email">
          <Input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Password">
          <Input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        <ErrorText>{error}</ErrorText>
        <Button type="submit" disabled={busy} className="w-full">
          {busy ? 'Logging in...' : 'Log in'}
        </Button>
      </form>
    </AuthShell>
  )
}

export function RegisterPage() {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { busy, error, run } = useAction()
  const done = useSignInRedirect()

  const submit = (e) => {
    e.preventDefault()
    run(async () => done(await authApi.register({ name: name.trim(), email: email.trim(), password })))
  }

  return (
    <AuthShell
      title="Create account"
      footer={
        <>
          Already registered?{' '}
          <Link to="/login" className="text-accent underline">
            Log in
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-3">
        <Field label="Name">
          <Input autoComplete="name" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Email">
          <Input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Password" hint="At least 8 characters.">
          <Input
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={72}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        <ErrorText>{error}</ErrorText>
        <Button type="submit" disabled={busy} className="w-full">
          {busy ? 'Creating...' : 'Create account'}
        </Button>
      </form>
    </AuthShell>
  )
}
