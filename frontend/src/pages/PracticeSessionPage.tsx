import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { changes, completePractice, createFollowup, getHint, getPractice, getReference, labels, newRequestKey, PracticeApiError, retryEvaluation, submitPractice, topics, type AnswerPayload, type Attempt, type Reference, type Session } from '../api/practice'
import Icon from '../components/Icon'
import { ErrorState, LoadingState, PageHeading } from '../components/Ui'
import SpeechPractice from '../components/SpeechPractice'

function readSaved<T>(key: string): T | null { try { return JSON.parse(localStorage.getItem(key) || 'null') } catch { return null } }
function save(key: string, value: unknown) { try { if (value === null) localStorage.removeItem(key); else localStorage.setItem(key, JSON.stringify(value)) } catch { /* Submission stays usable when local browser storage is unavailable. */ } }
function Feedback({ attempt }: { attempt: Attempt }) {
  const e = attempt.evaluation
  if (e.status === 'PENDING' || e.status === 'RUNNING') return <div className="practice-wait" role="status"><div className="spinner" /><div><h3>{e.status === 'PENDING' ? '回答已保存，等待反馈' : '正在逐项核对你的回答'}</h3><p>可以离开页面，回来后继续查看。评估不会覆盖你的回答。</p></div></div>
  if (e.status === 'FAILED') return <div className="error-inline" role="alert"><Icon name="alert" size={20} /><div><strong>本次评估未完成</strong><p>{e.errorMessage}</p></div></div>
  if (!e.result) return null
  return <div className="practice-feedback">
    <div className="practice-section-heading"><h2>这次，哪些讲清楚了？</h2><span>按要点反馈</span></div>
    {e.referenceNotice && <p className={'reference-notice ' + (e.referenceState !== 'AVAILABLE' ? 'limited' : '')}><Icon name="book" size={17} />{e.referenceNotice}</p>}
    <div className="criterion-list">{e.result.criteria.map(c => <article className={'criterion-card status-' + c.status.toLowerCase()} key={c.criterionId}>
      <header><h3>{e.criterionLabels[c.criterionId] || c.criterionId}</h3><span className={'criterion-status status-' + c.status.toLowerCase()}>{labels[c.status]}</span></header>
      {c.candidateQuotes.length > 0 && <div className="candidate-evidence"><span>你的原话</span>{c.candidateQuotes.map((quote, i) => <blockquote key={i}>{quote}</blockquote>)}</div>}
      <p>{c.reason}</p>
      {c.references.length > 0 && <details className="citation-detail"><summary>查看判断依据</summary>{c.references.map((ref, i) => {
        const source = e.sources.find(s => s.sourceId === ref.sourceId)
        return <div key={i}><blockquote>{ref.quote}</blockquote>{source && <a href={source.url} target="_blank" rel="noreferrer">{source.title} · 核对资料<Icon name="external" size={12} /></a>}</div>
      })}</details>}
    </article>)}</div>
    {e.result.expressionFeedback.length > 0 && <section className="practice-expression"><h2>下一次可以怎样表达</h2>{e.result.expressionFeedback.map((f, i) => <article key={i}><blockquote>{f.quote}</blockquote><p>{f.issue}</p><strong>{f.suggestion}</strong></article>)}</section>}
    {attempt.comparison && <section className="practice-comparison"><div className="practice-section-heading"><h2>与上次回答相比</h2><Icon name="chart" size={21} /></div><p>{attempt.comparison.notice}</p>{attempt.comparison.comparable && <div>{attempt.comparison.criteria.map(c => <div className={'comparison-row change-' + c.change.toLowerCase()} key={c.criterionId}><strong>{c.label}</strong><span>{labels[c.before]} → {labels[c.after]}</span><b>{changes[c.change]}</b></div>)}</div>}</section>}
    <p className="practice-disclaimer">反馈用于帮助练习，可能存在误判。请结合原话和参考依据核对；一次答对不代表长期掌握。</p>
  </div>
}

export default function PracticeSessionPage() {
  const id = useParams().id || '', navigate = useNavigate()
  const [data, setData] = useState<Session | null>(null), [error, setError] = useState(''), [actionError, setActionError] = useState('')
  const [reload, setReload] = useState(0), [busy, setBusy] = useState('')
  const [selected, setSelected] = useState(''), [composer, setComposer] = useState(false)
  const [draft, setDraft] = useState<AnswerPayload>({ answer: '', parentAttemptId: null, inputMode: 'TEXT', clientRequestId: newRequestKey() })
  const [outbox, setOutbox] = useState<AnswerPayload | null>(readSaved('practice-outbox:' + id))
  const [reference, setReference] = useState<Reference | null>(null), [hint, setHint] = useState('')
  const initialized = useRef(false), sending = useRef(false)
  const latest = data?.attempts[data.attempts.length - 1]
  const current = data?.attempts.find(a => a.id === selected) || latest
  const pending = latest?.evaluation.status === 'PENDING' || latest?.evaluation.status === 'RUNNING'

  useEffect(() => {
    let cancelled = false
    async function refresh() {
      try {
        const s = await getPractice(id)
        if (cancelled) return
        setData(s); setError('')
        const last = s.attempts[s.attempts.length - 1]
        if (!initialized.current) {
          initialized.current = true; setSelected(last?.id || '')
          const saved = readSaved<AnswerPayload>('practice-draft:' + id)
          if (saved && (!last || saved.parentAttemptId === last.id)) { setDraft(saved); setComposer(true) }
          else { setComposer(!last); if (last) save('practice-draft:' + id, null) }
        }
        const queued = readSaved<AnswerPayload>('practice-outbox:' + id)
        if (queued && s.attempts.some(a => a.clientRequestId === queued.clientRequestId)) {
          save('practice-outbox:' + id, null); save('practice-draft:' + id, null)
          setOutbox(null); setComposer(false)
          setSelected(s.attempts.find(a => a.clientRequestId === queued.clientRequestId)!.id)
        }
      } catch (e) { if (!cancelled) setError(e instanceof Error ? e.message : '暂时无法读取练习') }
    }
    void refresh()
    const interval = window.setInterval(() => { if (pending || error || readSaved('practice-outbox:' + id)) void refresh() }, 2000)
    return () => { cancelled = true; window.clearInterval(interval) }
  }, [id, reload, pending, error])

  function updateAnswer(answer: string) {
    const next = { ...draft, answer }
    setDraft(next); save('practice-draft:' + id, next)
  }
  async function submit() {
    if (sending.current || busy || (!outbox && !draft.answer.trim())) return
    sending.current = true; setBusy('submit'); setActionError('')
    const body = outbox || draft
    save('practice-outbox:' + id, body); setOutbox(body)
    try {
      const a = await submitPractice(id, body)
      setSelected(a.id); setComposer(false); setOutbox(null)
      save('practice-outbox:' + id, null); save('practice-draft:' + id, null)
      setReload(n => n + 1)
    } catch (e) {
      if (e instanceof PracticeApiError && e.status >= 400 && e.status < 500) { setOutbox(null); save('practice-outbox:' + id, null) }
      setActionError(e instanceof Error ? e.message : '提交未确认，请用同一回答重试。')
      setReload(n => n + 1)
    } finally { sending.current = false; setBusy('') }
  }
  function reanswer() {
    if (!latest) return
    const next: AnswerPayload = { answer: '', parentAttemptId: latest.id, inputMode: 'TEXT', clientRequestId: newRequestKey() }
    setDraft(next); save('practice-draft:' + id, next); setComposer(true); setSelected(latest.id)
    window.setTimeout(() => document.getElementById('practice-answer')?.focus(), 0)
  }
  async function action(name: string, operation: () => Promise<unknown>) {
    if (busy) return
    setBusy(name); setActionError('')
    try { await operation(); setReload(n => n + 1) }
    catch (e) { setActionError(e instanceof Error ? e.message : '操作失败，请重试。') }
    finally { setBusy('') }
  }
  async function withKey(name: string, attemptId: string, operation: (key: string) => Promise<unknown>) {
    const storage = 'practice-action:' + name + ':' + attemptId
    let key = sessionStorage.getItem(storage)
    if (!key) { key = newRequestKey(); sessionStorage.setItem(storage, key) }
    await operation(key); sessionStorage.removeItem(storage)
  }

  if (error && !data) return <div className="page-scroll"><div className="page-content"><ErrorState message={error} onRetry={() => setReload(n => n + 1)} /></div></div>
  if (!data) return <div className="page-scroll"><div className="page-content"><LoadingState title="正在恢复练习" /></div></div>
  return <div className="page-scroll"><div className="page-content practice-page session-practice">
    <PageHeading eyebrow={data.kind === 'FOLLOWUP' ? 'A LITTLE DEEPER' : 'SAY IT IN YOUR OWN WORDS'} title={data.question.title} description={data.kind === 'FOLLOWUP' ? '针对刚才的要点，再用一个场景讲清楚。' : '先按自己的理解回答，再看反馈。无需背统一措辞。'} action={<Link className="btn btn-ghost" to="/practice/questions"><Icon name="back" size={16} />返回题库</Link>} />
    {data.kind === 'REVIEW' && <p className="practice-hint">本次为到期独立复测。首次提交前隐藏提示、解析和前次答案；重答只用于学习，不重复累计独立复测。</p>}
    {data.originSessionId && <Link className="practice-parent-link" to={'/practice/sessions/' + data.originSessionId}><Icon name="back" size={15} />回到原题练习</Link>}
    <section className="practice-prompt"><div><span className="pill neutral">{topics[data.question.topic]}</span><span>难度 {data.question.difficulty} · 建议 {data.question.suggestedSeconds} 秒</span>{data.status === 'COMPLETED' && <span className="pill">已结束</span>}</div><h2>{data.question.prompt}</h2></section>
    {error && <p className="error-inline" role="alert">连接暂时中断，正在重新读取。已保存的回答不会丢失。</p>}
    {actionError && <p className="error-inline" role="alert">{actionError}</p>}
    <div className="practice-workspace">
      <div className="practice-main">
        {data.attempts.length > 0 && <div className="attempt-tabs" role="group" aria-label="选择回答记录">{data.attempts.map(a => <button key={a.id} className={current?.id === a.id ? 'active' : ''} onClick={() => setSelected(a.id)}>第 {a.attemptNumber} 次回答<span>{a.referenceViewed || a.hintsUsed ? '有辅助' : '独立'}</span></button>)}</div>}
        {current && <section className="practice-answer-record"><div className="practice-section-heading"><h2>第 {current.attemptNumber} 次回答</h2><span>{new Date(current.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })} · {current.referenceViewed || current.hintsUsed ? '有辅助' : '独立回答'}</span></div><p>{current.answer}</p></section>}
        {current && <Feedback attempt={current} />}
        {current?.oralFeedback && <section className="speech-oral-feedback"><h2>回听与表达练习</h2><p>原音 {Math.round(current.oralFeedback.durationMs / 1000)} 秒 · 确认文本 {current.oralFeedback.characterCount} 字；这些数据不代表知识掌握。</p>{current.oralFeedback.suggestions.map((suggestion, index) => <p key={index}>{suggestion}</p>)}{current.oralFeedback.observations.length > 0 && <details><summary>核对可能的口头词：{current.oralFeedback.observations.length} 处</summary>{current.oralFeedback.observations.map((item, index) => <p key={index}>“{item.word}” · 文本位置 {item.start + 1}–{item.end}：{current.answer.slice(Math.max(0, item.start - 8), Math.min(current.answer.length, item.end + 8))}</p>)}</details>}</section>}
        <SpeechPractice sessionId={id} parentAttemptId={latest?.id || null} currentAttemptId={current?.id} canRecord={data.status === 'ACTIVE' && composer && !outbox && !pending} onSubmitted={attempt => { setSelected(attempt.id); setComposer(false); setOutbox(null); save('practice-draft:' + id, null); setReload(n => n + 1) }} />
        {current?.evaluation.status === 'FAILED' && data.status === 'ACTIVE' && <div className="practice-actions"><button className="btn btn-primary" disabled={!!busy || current.evaluations.length >= 3} onClick={() => action('retry', () => withKey('retry', current.id, async key => { await retryEvaluation(current.id, key) }))}>{busy === 'retry' ? '正在重试…' : '重试评估'}</button><span>{current.evaluations.length >= 3 ? '已达到三次上限，可以重新选题练习。' : '重试使用原回答，不会新增回答记录。'}</span></div>}
        {current?.evaluation.status === 'SUCCEEDED' && data.status === 'ACTIVE' && <div className="practice-actions">
          <button className="btn btn-primary" disabled={!!busy || pending || composer} onClick={reanswer}>再答一次<Icon name="arrow" size={16} /></button>
          <button className="btn btn-ghost" disabled={!!busy} onClick={() => action('reference', async () => { setReference(await getReference(id)) })}>查看解析<Icon name="book" size={16} /></button>
          <button className="btn btn-ghost" disabled={!!busy || pending} onClick={() => action('followup', () => withKey('followup', current.id, async key => { const child = await createFollowup(current.id, key); navigate('/practice/sessions/' + child.id) }))}>练一个追问<Icon name="chat" size={16} /></button>
        </div>}
        {(composer || outbox) && data.status === 'ACTIVE' && <section className="practice-composer">
          <div className="practice-section-heading"><h2>{latest ? '用自己的话，再答一次' : '先独立讲一遍'}</h2><span>{data.hintsUsed || data.referenceViewed ? '本次有辅助' : '独立练习'}</span></div>
          <label className="visually-hidden" htmlFor="practice-answer">你的回答</label><textarea id="practice-answer" value={outbox?.answer ?? draft.answer} disabled={!!busy || !!outbox || pending} maxLength={10000} onChange={e => updateAnswer(e.target.value)} placeholder="说清楚机制，给一个场景，再说明边界。写下你现在能独立讲出的内容。" />
          <div className="composer-footer"><span>{(outbox?.answer ?? draft.answer).length} / 10000 字 · 草稿保存在此浏览器</span><button className="btn btn-primary" disabled={!!busy || pending || !(outbox?.answer ?? draft.answer).trim()} onClick={submit}>{busy === 'submit' ? '正在保存…' : outbox ? '重试同一回答的提交' : '提交回答，查看反馈'}<Icon name="send" size={16} /></button></div>
          {hint && <p className="practice-hint" role="status">{hint}</p>}
          {(data.kind !== 'REVIEW' || latest) && <div className="practice-assistance"><button className="text-button" disabled={!!busy} onClick={() => action('hint', async () => { setHint((await getHint(id)).hint) })}>需要一点表达提示</button>{!latest && <button className="text-button" disabled={!!busy} onClick={() => action('reference', async () => { setReference(await getReference(id, true)) })}>提前看解析（有辅助）</button>}</div>}
        </section>}
        {reference && <section className="practice-reference"><div className="practice-section-heading"><h2>参考解析与边界</h2><button className="text-button" onClick={() => setReference(null)}>收起</button></div><p className="reference-warning">看过解析后的重答记为有辅助。学习后试着合上解析，再用自己的话解释。</p><p className="reference-explanation">{reference.explanation}</p>{reference.criteria.map(c => <article key={c.id}><h3>{c.expected}</h3><p>也可以这样说：{c.acceptedExpressions.join('；')}</p><p className="common-mistake">易错说法：{c.commonMistakes.join('；')}</p></article>)}<p className="practice-disclaimer">这里是项目整理的训练要点，可通过下方参考链接核对；并非网站原文转载。</p><div className="reference-links">{reference.sources.map(s => <a key={s.sourceId} href={s.url} target="_blank" rel="noreferrer">{s.title} · 来源链接<Icon name="external" size={13} /></a>)}</div></section>}
        {data.status === 'ACTIVE' && <div className="practice-mobile-end"><button className="btn btn-ghost" disabled={!!busy || pending} onClick={() => action('complete', async () => { setData(await completePractice(id)); setComposer(false) })}>结束这次练习</button></div>}
        {data.status === 'COMPLETED' && <div className="practice-actions"><Link className="btn btn-primary" to="/practice/questions">换一道题继续练</Link>{current && <button className="btn btn-ghost" disabled={!!busy} onClick={() => action('reference', async () => { setReference(await getReference(id)) })}>回看解析</button>}</div>}
      </div>
      <aside className="practice-guide"><span className="eyebrow">PRACTICE NOTES</span><h2>让回答更有层次</h2><ol><li><strong>先讲机制</strong><span>它解决什么问题，为什么有效？</span></li><li><strong>再讲场景</strong><span>换成实际情况，你会怎样处理？</span></li><li><strong>最后讲边界</strong><span>哪些条件下会失效，怎样应对？</span></li></ol><p>反馈里的原话和依据都可核对。遇到无法判断的要点，可以补充背景后再答。</p><Link to="/practice/history" className="btn btn-ghost"><Icon name="clock" size={16} />我的练习</Link>{data.status === 'ACTIVE' && <button className="text-button" disabled={!!busy || pending} onClick={() => action('complete', async () => { setData(await completePractice(id)); setComposer(false) })}>结束这次练习</button>}</aside>
    </div>
  </div></div>
}
