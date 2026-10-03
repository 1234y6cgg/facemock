import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { createPractice, listQuestions, getQuestionSummary, newRequestKey, topics, type Question, type QuestionPage, type CatalogSummary } from '../api/practice'
import Icon from '../components/Icon'
import { ErrorState, LoadingState, PageHeading } from '../components/Ui'

export default function QuestionLibraryPage() {
  const [params, setParams] = useSearchParams()
  const topic = params.get('topic') || '', difficulty = params.get('difficulty') || '', query = params.get('q') || ''
  const [search, setSearch] = useState(query)
  const [summary, setSummary] = useState<CatalogSummary | null>(null)
  const page = Math.max(0, Number(params.get('page') || 0) || 0)
  const [data, setData] = useState<QuestionPage | null>(null)
  const [error, setError] = useState(''), [actionError, setActionError] = useState('')
  const [starting, setStarting] = useState(''), [reload, setReload] = useState(0)
  const navigate = useNavigate()
  useEffect(() => { setSearch(query) }, [query])
  useEffect(() => {
    let cancelled = false
    getQuestionSummary().then(value => { if (!cancelled) setSummary(value) }).catch(() => { /* The question list remains usable. */ })
    return () => { cancelled = true }
  }, [reload])
  useEffect(() => {
    let cancelled = false
    setData(null); setError('')
    listQuestions(topic, difficulty, page, query).then(value => { if (!cancelled) setData(value) }).catch(e => { if (!cancelled) setError(e.message) })
    return () => { cancelled = true }
  }, [topic, difficulty, page, query, reload])
  function filter(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value); else next.delete(key)
    next.set('page', '0'); setParams(next)
  }
  async function start(q: Question) {
    if (starting) return
    setStarting(q.id); setActionError('')
    const storageKey = 'practice-create:' + q.id + ':' + q.version
    let key = sessionStorage.getItem(storageKey)
    if (!key) { key = newRequestKey(); sessionStorage.setItem(storageKey, key) }
    try {
      const session = await createPractice(q, key)
      sessionStorage.removeItem(storageKey)
      navigate('/practice/sessions/' + session.id)
    } catch (e) { setActionError(e instanceof Error ? e.message : '暂时无法开始练习') }
    finally { setStarting('') }
  }
  return <div className="page-scroll"><div className="page-content practice-page">
    <PageHeading eyebrow="ONE QUESTION, ONE STEP FORWARD" title="把知道的，练成讲得清楚。" description="选一道题，先独立回答，再根据具体反馈补齐遗漏。" action={<Link className="btn btn-ghost" to="/practice/history"><Icon name="clock" size={16} />我的练习</Link>} />
    <div className="practice-intro"><Icon name="book" size={24} /><p>{summary ? `${summary.totalQuestions} 道题 · ${summary.topics.length} 个主题。` : 'Java 后端与计算机基础题库。'}从原理到场景，每道题都有评分要点、误区与来源。</p><span>先回答 · 再反馈 · 再答一次</span></div>
    <form className="question-search" onSubmit={e => { e.preventDefault(); filter('q', search.trim()) }}><label htmlFor="question-search">搜索题目</label><div><input id="question-search" value={search} onChange={e => setSearch(e.target.value)} maxLength={80} placeholder="搜索 HashMap、TCP、事务、缓存…"/><button className="btn btn-ghost" type="submit"><Icon name="search" size={16}/>搜索</button>{query && <button className="text-button" type="button" onClick={() => filter('q', '')}>清除搜索</button>}</div></form>
    <div className="practice-filters">
      <label>练习主题<select value={topic} onChange={e => filter('topic', e.target.value)}><option value="">全部主题</option>{Object.entries(topics).map(([id, name]) => { const count = summary?.topics.find(t => t.topic === id)?.count; return <option key={id} value={id}>{name}{count !== undefined ? ` · ${count} 题` : ''}</option> })}</select></label>
      <label>题目难度<select value={difficulty} onChange={e => filter('difficulty', e.target.value)}><option value="">全部难度</option>{[1, 2, 3, 4, 5].map(n => <option key={n} value={n}>难度 {n}</option>)}</select></label>
      <span className="practice-count">{data ? data.totalElements + ' 道可练习题目' : '正在读取题目…'}</span>
    </div>
    {actionError && <p className="error-inline" role="alert">{actionError}</p>}
    {error ? <ErrorState message={error} onRetry={() => setReload(n => n + 1)} /> : !data ? <LoadingState title="正在整理题库" /> : <>
      {data.items.length === 0 ? <div className="state-card"><Icon name="search" size={30} /><h2>暂时没有匹配的题目</h2><p>试试其他主题或难度。</p><button className="btn btn-ghost" onClick={() => setParams({})}>显示全部题目</button></div> :
        <div className="question-grid">{data.items.map((q, index) => <article className="question-card" key={q.id}>
          <div className="question-meta"><span className="pill neutral">{topics[q.topic]}</span><span>难度 {q.difficulty} · 约 {q.suggestedSeconds} 秒</span></div>
          <span className="question-number">{String(page * 12 + index + 1).padStart(2, '0')}</span><h2>{q.title}</h2><p>{q.prompt}</p>
          <button className="btn btn-primary" disabled={!!starting} onClick={() => start(q)}>{starting === q.id ? '正在开始…' : '独立练一题'}<Icon name="arrow" size={16} /></button>
        </article>)}</div>}
      {data.totalPages > 1 && <div className="practice-pagination"><button className="btn btn-ghost" disabled={page === 0} onClick={() => setParams({ ...Object.fromEntries(params), page: String(page - 1) })}>上一页</button><span>第 {page + 1} / {data.totalPages} 页</span><button className="btn btn-ghost" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ ...Object.fromEntries(params), page: String(page + 1) })}>下一页</button></div>}
    </>}
  </div></div>
}
