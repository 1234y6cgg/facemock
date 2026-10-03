import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { answerProject, completeProject, criteria, fields, followupProject, getProjectSession, proposals, reasons, requestKey, retryProject, statuses, type Attempt, type ProjectSession } from '../api/projects'
import { newRequestKey, PracticeApiError } from '../api/practice'
import { PageHeading } from '../components/Ui'

type Outbox = { answer: string; parentAttemptId: string | null; clientRequestId: string }
export default function ProjectPracticePage() {
  const id = useParams().id!, navigate = useNavigate()
  const [session, setSession] = useState<ProjectSession | null>(null), [selected, setSelected] = useState(''), [composer, setComposer] = useState(true)
  const [text, setText] = useState(''), [outbox, setOutbox] = useState<Outbox | null>(null), [error, setError] = useState(''), [busy, setBusy] = useState(''), lock = useRef(false)
  async function load() {
    const value = await getProjectSession(id); setSession(value)
    const last = value.attempts[value.attempts.length - 1]; setSelected(old => old || last?.id || '')
    const queued = JSON.parse(localStorage.getItem('project-outbox:' + id) || 'null') as Outbox | null
    if (queued && value.attempts.some(a => a.clientRequestId === queued.clientRequestId)) { localStorage.removeItem('project-outbox:' + id); localStorage.removeItem('project-answer:' + id); setOutbox(null); setText(''); setComposer(false); setSelected(last.id) }
    else if (queued) { setOutbox(queued); setText(queued.answer); setComposer(true) }
    return value
  }
  useEffect(() => {
    let cancelled = false
    setSession(null); setSelected(''); setComposer(true); setOutbox(null); setText(''); setError('')
    try { setText(localStorage.getItem('project-answer:' + id) || '') } catch { setError('此浏览器无法保存草稿，请检查存储设置。') }
    load().then(value => { if (!cancelled) setComposer(!value.attempts.length || !!localStorage.getItem('project-outbox:' + id)) }).catch(e => setError(e.message))
    const timer = window.setInterval(() => { if (!cancelled) void load().catch(e => setError(e.message)) }, 2500)
    return () => { cancelled = true; window.clearInterval(timer) }
  }, [id])
  async function task(label: string, operation: () => Promise<void>) { if (lock.current) return; lock.current = true; setBusy(label); setError(''); try { await operation() } catch (e) { setError(e instanceof Error ? e.message : '操作未完成') } finally { lock.current = false; setBusy('') } }
  async function submit() {
    if (!session) return
    await task('submit', async () => {
      const latest = session.attempts[session.attempts.length - 1], body = outbox || { answer: text, parentAttemptId: latest?.id || null, clientRequestId: newRequestKey() }
      localStorage.setItem('project-outbox:' + id, JSON.stringify(body)); setOutbox(body)
      let attempt: Attempt
      try { attempt = await answerProject(id, body) } catch (e) { if (e instanceof PracticeApiError && e.status >= 400 && e.status < 500) { localStorage.removeItem('project-outbox:' + id); setOutbox(null) }; throw e }
      localStorage.removeItem('project-outbox:' + id); localStorage.removeItem('project-answer:' + id); setOutbox(null); setText(''); setSelected(attempt.id); setComposer(false); await load()
    })
  }
  const latest = session?.attempts[session.attempts.length - 1], attempt = session?.attempts.find(a => a.id === selected) || latest
  const evaluation = attempt?.evaluation, result = evaluation?.result, pending = latest && ['PENDING', 'RUNNING'].includes(latest.evaluation.status)
  return <div className="page-scroll"><div className="page-content practice-page">
    <PageHeading eyebrow="EXPLAIN YOUR OWN WORK" title={session?.snapshot.projectName || '项目表达训练'} description="讲清个人行动与证据，不为没有依据的结果补数字。" action={session && <Link className="btn btn-ghost" to={'/projects/' + session.projectId}>编辑材料 / 新版练习</Link>} />
    {error && <p className="error-inline" role="alert">{error}<button className="text-button" onClick={() => void load().catch(e => setError(e.message))}>重新加载</button></p>}
    {!session ? <p role="status">正在读取训练记录…</p> : <>
      <section className="practice-prompt"><div>材料版本 {session.snapshot.revision} · {session.template === 'FOLLOWUP' ? '专项追问' : '项目表达'}</div><h2>{session.prompt}</h2><p>本练习固定使用开始时的材料。修订事实后，请从项目页开启新版练习；历史反馈不会随材料改变。</p></section>
      {session.status === 'COMPLETED' && <p className="project-version-banner" role="status">本次训练已结束，回答、反馈和材料依据已保留，可随时回看。</p>}
      <div className="attempt-tabs" aria-label="选择项目回答">{session.attempts.map(a => <button key={a.id} className={a.id === attempt?.id ? 'active' : ''} onClick={() => setSelected(a.id)}>第 {a.number} 次回答</button>)}</div>
      {attempt && <section className="practice-answer-record"><h2>第 {attempt.number} 次回答</h2><p>{attempt.answer}</p></section>}
      {evaluation && ['PENDING', 'RUNNING'].includes(evaluation.status) && <section className="project-training-card" role="status"><h3>{evaluation.status === 'PENDING' ? '回答已保存，等待反馈' : '正在核对回答与项目材料'}</h3><p>可以离开后回来查看，回答与引用版本会保留。</p></section>}
      {evaluation?.status === 'FAILED' && attempt && <section className="project-training-card"><h3>本次评估未完成，回答已保留</h3><p>{evaluation.errorCode === 'INVALID_OUTPUT' ? '模型输出未通过原话或引用校验，未作为反馈展示。' : '服务中断或模型不可用，请稍后重试。'}</p><button className="btn btn-primary" disabled={!!busy || attempt.evaluations.length >= 3 || session.status !== 'ACTIVE'} onClick={() => void task('retry', async () => { const key = 'project-retry:' + attempt.id; await retryProject(attempt.id, requestKey(key)); sessionStorage.removeItem(key); await load() })}>重试评估</button>{attempt.evaluations.length >= 3 && <p>已达到三次上限，请开始新的练习。</p>}</section>}
      {result && <>
        <div className="practice-section-heading"><h2>逐项核对表达与事实</h2><span>范围仅限下列材料片段</span></div>
        <div className="project-feedback-grid">{result.criteria.map(c => <section className="project-training-card" key={c.id}><div className="practice-section-heading"><h3>{criteria[c.id]}</h3><span className={'pill ' + (c.status === 'CONFLICT' ? 'practice-failed' : 'neutral')}>{statuses[c.status]}</span></div><p>{reasons[c.reasonCode]}</p>{c.candidateQuote && <blockquote><strong>你的原话</strong><p>{c.candidateQuote}</p></blockquote>}{c.references.map((ref, index) => { const source = session.snapshot.sources.find(s => s.id === ref.sourceId && s.version === ref.version && s.content.includes(ref.quote)); return <blockquote className="project-evidence" key={index}><strong>{source?.planned ? '尚未完成的改进' : '材料原文'} · {fields[source?.title || ''] || source?.title} · v{ref.version}</strong><p>{ref.quote}</p><small>出处：{source?.origin}</small></blockquote> })}</section>)}</div>
        {result.missingFields.length > 0 && <section className="project-training-card"><h2>待补充信息</h2><p>缺少的信息由你核实补充，不自动生成。</p><ul>{result.missingFields.map(field => <li key={field}>{fields[field]}：请补充实际情况、获取方式与可核对依据。</li>)}</ul><Link className="btn btn-ghost" to={'/projects/' + session.projectId}>修订事实卡</Link></section>}
        {result.improvementActions.length > 0 && <section className="project-training-card project-proposals"><h2>可以改进 · 尚未完成</h2><p>以下是后续行动建议，不能作为你已实施或已取得的成果。</p><ul>{result.improvementActions.map(action => <li key={action}>{proposals[action]}</li>)}</ul></section>}
        {attempt?.comparison && <section className="project-training-card"><h2>重答对比</h2><p>{attempt.comparison.notice}</p><div className="project-comparison">{attempt.comparison.criteria.map(c => <div key={c.criterionId}><strong>{criteria[c.criterionId]}</strong><span>{statuses[c.before]} → {statuses[c.after]}</span><small>{({ IMPROVED: '本次补齐', REGRESSED: '本次遗漏或出错', UNCHANGED: '判定保持', CHANGED: '判定变化', UNCERTAIN: '无法比较' } as Record<string, string>)[c.change]}</small></div>)}</div></section>}
        <div className="practice-actions project-training-actions"><button className="btn btn-primary" disabled={!!busy || !!pending || session.status !== 'ACTIVE'} onClick={() => { setSelected(latest!.id); setComposer(true) }}>再答一次</button><button className="btn btn-ghost" disabled={!!busy || session.status !== 'ACTIVE'} onClick={() => void task('followup', async () => { const key = 'project-followup:' + attempt!.id; const child = await followupProject(attempt!.id, requestKey(key)); sessionStorage.removeItem(key); navigate('/projects/practice/' + child.id) })}>{result.criteria.some(c => c.status !== 'COVERED') ? '围绕薄弱项追问' : '继续练一个追问'}</button></div>
      </>}
      {composer && !pending && session.status === 'ACTIVE' && <section className="practice-composer"><h2>{latest ? '把刚才没讲清楚的部分再讲一遍' : '先用自己的话讲一遍'}</h2><label htmlFor="project-answer">你的项目回答</label><textarea id="project-answer" maxLength={10000} disabled={!!busy || !!outbox} value={outbox?.answer ?? text} onChange={e => { setText(e.target.value); localStorage.setItem('project-answer:' + id, e.target.value) }} /><p>{text.length} / 10000 字 · 草稿保存在此浏览器</p><button className="btn btn-primary" disabled={!!busy || !text.trim()} onClick={() => void submit()}>{busy === 'submit' ? '正在保存…' : outbox ? '重试同一回答的提交' : '提交回答，核对材料'}</button></section>}
      <details className="project-training-card"><summary>查看本次训练保存的材料片段</summary>{session.snapshot.sources.map((source, i) => <div className="project-material-row" key={i}><strong>{fields[source.title] || source.title} · v{source.version} {source.planned ? '· 未完成改进' : ''}</strong><p>{source.origin}</p><pre>{source.content}</pre></div>)}</details>
      {session.status === 'ACTIVE' && <button className="btn btn-ghost" disabled={!!busy || !!pending || !!outbox} onClick={() => void task('complete', async () => setSession(await completeProject(id)))}>结束本次训练</button>}
    </>}
  </div></div>
}
