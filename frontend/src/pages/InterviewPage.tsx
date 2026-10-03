import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getMessages, getInterview, MessageDto } from '../api/client'
import { streamPost, SSEHandlers } from '../api/sse'
import ChatBubble from '../components/ChatBubble'
import Icon from '../components/Icon'
import InterviewSpeech from '../components/InterviewSpeech'

const layers = ['背景', '方案', '细节', '难点', '权衡扩展']

export default function InterviewPage() {
  const { id } = useParams()
  const sessionId = Number(id)
  const navigate = useNavigate()
  const [messages, setMessages] = useState<MessageDto[]>([])
  const [streaming, setStreaming] = useState('')
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(true)
  const [finished, setFinished] = useState(false)
  const [role, setRole] = useState('')
  const [layer, setLayer] = useState('')
  const [feedback, setFeedback] = useState<string | null>(null)
  const [mode, setMode] = useState<'TEXT' | 'VOICE'>('TEXT')
  const [error, setError] = useState('')
  const bufRef = useRef('')
  const streamLayerRef = useRef('')
  const scrollRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const requestRunningRef = useRef(false)
  const abortRef = useRef<AbortController | null>(null)

  function autoResize() {
    const el = textareaRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 200) + 'px'
  }

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const [detail, existing] = await Promise.all([
          getInterview(sessionId),
          getMessages(sessionId),
        ])
        if (cancelled) return
        setMessages(existing)
        setMode(detail.mode === 'VOICE' ? 'VOICE' : 'TEXT')
        const done = detail.status === 'COMPLETED'
        setFinished(done)
        setRole(detail.targetRole || '')
        if (!done) {
          for (let i = existing.length - 1; i >= 0; i--) {
            if (existing[i].role === 'INTERVIEWER' && existing[i].layer) {
              setLayer(existing[i].layer as string)
              streamLayerRef.current = existing[i].layer as string
              break
            }
          }
        }
        if (done) {
          setBusy(false)
        } else if (existing.length === 0) {
          await streamOpening()
        } else {
          setBusy(false)
        }
      } catch (e) {
        if (!cancelled) { setError(e instanceof Error ? e.message : '无法加载面试'); setBusy(false) }
      }
    })()
    return () => {
      cancelled = true
      abortRef.current?.abort()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    if (finished) {
      el.scrollTop = 0
      return
    }
    el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
  }, [messages, streaming, feedback, finished])

  function handlers(): SSEHandlers {
    return {
      onMessage: (t) => { bufRef.current += t; setStreaming(bufRef.current) },
      onLayer: (l) => { streamLayerRef.current = l; setLayer(l) },
      onFeedback: (text) => setFeedback(text),
      onDone: () => {
        const full = bufRef.current.trim()
        if (full) setMessages((m) => [...m, { role: 'INTERVIEWER', content: full, layer: streamLayerRef.current }])
        bufRef.current = ''
        setStreaming('')
        setBusy(false)
      },
      onInterviewEnd: () => {
        setFinished(true)
        setBusy(false)
      },
    }
  }

  async function streamOpening() {
    bufRef.current = ''
    setFeedback(null)
    abortRef.current = new AbortController()
    await streamPost(`/api/interviews/${sessionId}/start`, {}, handlers(), abortRef.current.signal)
  }

  async function send(textOverride?: string): Promise<boolean> {
    const answer = (textOverride ?? input).trim()
    if (!answer || busy || finished || error || requestRunningRef.current) return false
    requestRunningRef.current = true
    setError('')
    setInput('')
    if (textareaRef.current) textareaRef.current.style.height = 'auto'
    setFeedback(null)
    setMessages((m) => [...m, { role: 'CANDIDATE', content: answer, layer: null }])
    setBusy(true)
    bufRef.current = ''
    try {
      abortRef.current = new AbortController()
      await streamPost(`/api/interviews/${sessionId}/answer`, { content: answer }, handlers(), abortRef.current.signal)
      return true
    } catch (e) {
      if (!abortRef.current?.signal.aborted) { setError(`${e instanceof Error ? e.message : '提交失败'}。请重新加载对话，确认回答是否已保存。`); setInput(answer); setBusy(false) }
      return false
    } finally { requestRunningRef.current = false }
  }

  async function sendExit() {
    if (busy || finished || error) return
    await send('结束面试吧')
  }

  return (
    <div className="interview-page">
      <div className="interview-header">
        <div>
          <div className="eyebrow">THE INTERVIEW ROOM / {String(sessionId).padStart(3, '0')}</div>
          <h1>{role || '你的专属面试'}</h1>
          <div className="meta"><span className={`status-dot ${finished ? 'muted' : ''}`} />{finished ? '面试已结束 · 可以回顾对话与复盘' : '专注当下的问题，讲清你的思考过程。'}</div>
        </div>
        <button className="btn btn-ghost" onClick={() => navigate(`/interview/${sessionId}/resume`)}><Icon name="file" size={16} />简历档案</button>
      </div>

      <div className="interview-body">
        <div className="interview-chat-col">
          <div className="chat-stream" ref={scrollRef} role="log" aria-label="面试对话" aria-live="off">
            <div className="conversation-intro"><span className="conversation-line" /><span>{finished ? '完整面试记录' : '从你的经历，开始这场对话'}</span><span className="conversation-line" /></div>
            {messages.map((m, i) => (
              <ChatBubble key={i} role={m.role} content={m.content} layer={m.layer} />
            ))}
            {streaming && <ChatBubble role="INTERVIEWER" content={streaming} layer={streamLayerRef.current} streaming />}
            {busy && !streaming && (
              <div className="msg interviewer">
                <div className="avatar"><Icon name="spark" size={18} /></div>
                <div className="thinking" role="status"><span>面试官正在思考</span><div className="typing-dots"><span /><span /><span /></div></div>
              </div>
            )}
            {feedback && <div className="feedback-note"><span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="var(--warning)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M9 18h6"/><path d="M10 22h4"/><path d="M15.09 14c.18-.98.65-1.74 1.41-2.5A4.65 4.65 0 0 0 18 8 6 6 0 0 0 6 8c0 1 .23 2.23 1.5 3.5A4.61 4.61 0 0 1 8.91 14"/></svg></span>{feedback}</div>}
          </div>

          <div className="input-area">
            {error && <div className="interview-error" role="alert"><Icon name="alert" size={18} /><span>{error}</span><button className="text-button" onClick={() => window.location.reload()}>重新加载对话</button></div>}
            {finished ? (
              <div className="finished-bar">
                <span><Icon name="check" size={18} />本次练习已完成</span>
                <button className="btn btn-primary" onClick={() => navigate(`/report/${sessionId}`)}>查看复盘报告</button>
                <button className="btn btn-ghost" onClick={() => navigate(`/interview/${sessionId}/resume`)}>查看简历</button>
                <button className="btn btn-ghost" onClick={() => navigate('/mock')}>开始新面试</button>
              </div>
            ) : mode === 'VOICE' ? (
              <InterviewSpeech key={`${sessionId}:${messages.length}`} sessionId={sessionId} turn={messages.length}
                disabled={busy || Boolean(error)} onSubmit={text => send(text)} onTextMode={() => setMode('TEXT')}
                onExit={() => { void sendExit() }} />
            ) : (
              <>
                <div className="composer-heading"><span><Icon name="chat" size={15} />你的回答</span><button className="text-button" disabled={busy} onClick={() => setMode('VOICE')}>切换为语音</button></div>
                <div className="input-box">
                  <textarea
                    ref={textareaRef}
                    value={input} rows={1} disabled={busy}
                    placeholder={busy ? '面试官正在思考…' : '输入你的回答，Enter 发送（Shift+Enter 换行）'}
                    aria-label="面试回答"
                    onChange={(e) => { setInput(e.target.value); autoResize() }}
                    onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); void send() } }}
                  />
                  <div className="composer-actions"><button className="voice-exit-btn" disabled={busy || Boolean(error)} onClick={() => sendExit()}>结束面试</button><button className="send-btn" disabled={busy || Boolean(error) || !input.trim()} onClick={() => send()} aria-label="发送回答"><Icon name="send" size={18} /></button></div>
                </div>
                <div className="input-hint"><span>Enter 发送 · Shift + Enter 换行</span><span>讲清过程，比堆砌术语更重要。</span></div>
              </>
            )}
          </div>
        </div>
        <aside className="interview-rail"><div className="rail-section"><div className="eyebrow">IN THIS SESSION</div><h2>一次有深度的练习</h2><div className="session-stats"><div><strong>{messages.filter((m) => m.role === 'INTERVIEWER').length}</strong><span>已提问</span></div><div><strong>{messages.filter((m) => m.role === 'CANDIDATE').length}</strong><span>已回答</span></div></div></div><div className="rail-section"><div className="section-heading"><h3>追问路径</h3><span>{finished ? '已结束' : '当前考察层级'}</span></div><ol className="layer-path">{layers.map((item, i) => <li key={item} className={!finished && layer === item ? 'current' : ''}><span>{String(i + 1).padStart(2, '0')}</span><div><strong>{item}</strong><small>{['先说明场景与目标', '解释为什么这样设计', '展开具体实现方式', '说清挑战与解决过程', '讨论取舍与改进空间'][i]}</small></div>{!finished && layer === item && <span className="current-indicator" />}</li>)}</ol></div><div className="rail-tip"><Icon name="spark" size={21} /><h3>给自己一点思考空间</h3><p>可以先说明你的思路，再展开细节。遇到不确定的地方，如实说明，也是一种专业表达。</p></div><button className="rail-link" disabled={!finished} onClick={() => navigate(`/report/${sessionId}`)}><Icon name="chart" size={18} /><span>{finished ? '查看本次复盘' : '结束后生成复盘报告'}</span><Icon name="arrow" size={16} /></button></aside>
      </div>
    </div>
  )
}
