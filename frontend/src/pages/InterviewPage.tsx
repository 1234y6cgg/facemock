import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getMessages, MessageDto } from '../api/client'
import { streamPost, SSEHandlers } from '../api/sse'
import ChatBubble from '../components/ChatBubble'

export default function InterviewPage() {
  const { id } = useParams()
  const sessionId = Number(id)
  const navigate = useNavigate()
  const [messages, setMessages] = useState<MessageDto[]>([])
  const [streaming, setStreaming] = useState('')
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(true)
  const bufRef = useRef('')

  useEffect(() => {
    ;(async () => {
      try {
        const existing = await getMessages(sessionId)
        setMessages(existing)
        if (existing.length === 0) await streamOpening()
        else setBusy(false)
      } catch {
        setBusy(false)
      }
    })()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function handlers(): SSEHandlers {
    return {
      onMessage: (t) => {
        bufRef.current += t
        setStreaming(bufRef.current)
      },
      onDone: () => {
        setMessages((m) => [...m, { role: 'INTERVIEWER', content: bufRef.current, layer: null }])
        bufRef.current = ''
        setStreaming('')
        setBusy(false)
      },
      onInterviewEnd: () => {
        navigate(`/report/${sessionId}`)
      },
    }
  }

  async function streamOpening() {
    bufRef.current = ''
    await streamPost(`/api/interviews/${sessionId}/start`, {}, handlers())
  }

  async function send() {
    if (!input.trim() || busy) return
    const answer = input.trim()
    setInput('')
    setMessages((m) => [...m, { role: 'CANDIDATE', content: answer, layer: null }])
    setBusy(true)
    bufRef.current = ''
    await streamPost(`/api/interviews/${sessionId}/answer`, { content: answer }, handlers())
  }

  return (
    <main className="page" style={{ maxWidth: 860 }}>
      <div className="chat">
        {messages.map((m, i) => (
          <ChatBubble key={i} role={m.role} content={m.content} />
        ))}
        {streaming && <ChatBubble role="INTERVIEWER" content={streaming} />}
      </div>
      <div className="input-row">
        <textarea
          value={input}
          disabled={busy}
          placeholder={busy ? '面试官正在提问…' : '输入你的回答，Enter 发送（Shift+Enter 换行）'}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              send()
            }
          }}
        />
        <button className="primary" disabled={busy || !input.trim()} onClick={send}>
          发送
        </button>
      </div>
    </main>
  )
}
