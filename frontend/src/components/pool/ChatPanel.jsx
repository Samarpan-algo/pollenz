import { useEffect, useRef, useState } from 'react'
import { getMessages } from '../../api/pools.js'
import { Button, ErrorText, Input, Section } from '../ui.jsx'

const MAX_LENGTH = 1000

/** Merge by id, oldest first, so history and live events never duplicate a message. */
function merge(current, incoming) {
  const byId = new Map(current.map((m) => [m.id, m]))
  incoming.forEach((m) => byId.set(m.id, m))
  return [...byId.values()].sort((a, b) => a.id - b.id)
}

/** Pool chat for members: history over REST, live messages over the pool socket. */
export default function ChatPanel({ poolId, user, realtime }) {
  const { connected, sendChat, addChatListener } = realtime
  const [messages, setMessages] = useState([])
  const [text, setText] = useState('')
  const [error, setError] = useState(null)
  const bottom = useRef(null)

  useEffect(
    () =>
      addChatListener((event) => {
        if (event.type === 'CHAT_MESSAGE') setMessages((cur) => merge(cur, [event.payload]))
        else if (event.type === 'CHAT_ERROR') setError(event.payload?.message || 'Message not sent')
      }),
    [addChatListener],
  )

  // Load history on mount and after every reconnect, so nothing sent while offline is missed.
  useEffect(() => {
    let cancelled = false
    getMessages(poolId).then(
      (list) => {
        if (cancelled) return
        setMessages((cur) => merge(cur, list))
        setError(null)
      },
      (e) => !cancelled && setError(e.message),
    )
    return () => {
      cancelled = true
    }
  }, [poolId, connected])

  useEffect(() => {
    bottom.current?.scrollIntoView?.({ block: 'end' })
  }, [messages.length])

  const submit = (e) => {
    e.preventDefault()
    const body = text.trim()
    if (!body) return
    if (!sendChat(body)) {
      setError('Not connected. Your message was not sent.')
      return
    }
    setError(null)
    setText('')
  }

  return (
    <Section
      title="Chat"
      action={!connected && <span className="text-xs font-medium text-amber-700">Reconnecting...</span>}
    >
      <div className="max-h-80 min-h-24 space-y-2 overflow-y-auto rounded-lg border border-slate-200 bg-white p-3">
        {messages.length === 0 && <p className="text-sm text-slate-500">No messages yet. Say hello.</p>}
        {messages.map((m) => {
          const mine = m.senderId === user.id
          return (
            <div key={m.id} className={`flex flex-col ${mine ? 'items-end' : 'items-start'}`}>
              <span className="text-xs text-slate-500">
                {mine ? 'You' : m.senderName}
                {' · '}
                {new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </span>
              <p
                className={`max-w-[85%] whitespace-pre-wrap break-words rounded-lg px-3 py-1.5 text-sm ${
                  mine ? 'bg-accent text-white' : 'bg-slate-100 text-slate-900'
                }`}
              >
                {m.body}
              </p>
            </div>
          )
        })}
        <div ref={bottom} />
      </div>
      <ErrorText>{error}</ErrorText>
      <form onSubmit={submit} className="flex gap-2">
        <Input
          value={text}
          onChange={(e) => setText(e.target.value)}
          maxLength={MAX_LENGTH}
          placeholder="Message the pool"
          aria-label="Message"
          autoComplete="off"
        />
        <Button type="submit" disabled={!connected || !text.trim()}>
          Send
        </Button>
      </form>
    </Section>
  )
}
