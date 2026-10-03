import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { practiceHistory, type HistoryPage } from '../api/practice'
import { deletePractice, savedAudio } from '../api/speech'
import Icon from '../components/Icon'
import { ErrorState, LoadingState, PageHeading } from '../components/Ui'
const status: Record<string, string> = { PENDING: '等待反馈', RUNNING: '正在评估', SUCCEEDED: '反馈已就绪', FAILED: '评估失败，可重试' }
export default function PracticeHistoryPage() {
  const [data, setData] = useState<HistoryPage | null>(null), [page, setPage] = useState(0)
  const [error, setError] = useState(''), [reload, setReload] = useState(0)
  useEffect(() => {
    let cancelled = false
    setData(null); setError('')
    practiceHistory(page).then(value => { if (!cancelled) setData(value) }).catch(e => { if (!cancelled) setError(e.message) })
    return () => { cancelled = true }
  }, [page, reload])
  return <div className="page-scroll"><div className="page-content practice-page">
    <PageHeading eyebrow="YOUR PRACTICE, KEPT IN VIEW" title="每一次回答，都留下进步的线索。" description="继续未完成的练习，回看首答和重答的具体变化。" action={<Link className="btn btn-primary" to="/practice/questions"><Icon name="plus" size={16} />开始练习</Link>} />
    {error ? <ErrorState message={error} onRetry={() => setReload(n => n + 1)} /> : !data ? <LoadingState title="正在读取练习记录" /> : data.items.length === 0 ? <div className="state-card"><Icon name="book" size={30} /><h2>从第一道题开始</h2><p>回答和反馈会保存在这里，离开页面后仍可继续。</p><Link className="btn btn-primary" to="/practice/questions">去选题<Icon name="arrow" size={16} /></Link></div> : <>
      <div className="practice-history-list">{data.items.map(item => <div className="practice-history-entry" key={item.id}><Link className="practice-history-row" to={'/practice/sessions/' + item.id}>
        <div className="history-practice-icon"><Icon name={item.kind === 'FOLLOWUP' ? 'chat' : 'book'} size={22} /></div>
        <div><h2>{item.title}</h2><p>{new Date(item.createdAt).toLocaleString('zh-CN')} · {item.attemptCount} 次回答 · {item.kind === 'FOLLOWUP' ? '专项追问' : item.kind === 'REVIEW' ? '独立延迟复测' : '单题练习'}</p></div>
        <span className={'pill ' + (item.evaluationStatus === 'FAILED' ? 'practice-failed' : 'neutral')}>{item.status === 'COMPLETED' ? '已结束' : status[item.evaluationStatus || ''] || '待回答'}</span><Icon name="arrow" size={18} />
      </Link><button className="text-button practice-delete" aria-label={'删除练习：' + item.title} onClick={async () => { if (!window.confirm('删除本练习及其追问、录音、转写、回答与评估？此操作不能撤销。')) return; try { const deleted = await deletePractice(item.id); for (const id of deleted.sessionIds) { await savedAudio(id, null); localStorage.removeItem('practice-draft:' + id); localStorage.removeItem('practice-outbox:' + id) }; for (const id of deleted.recordingIds) { localStorage.removeItem('speech-text:' + id); localStorage.removeItem('speech-confirm:' + id) }; setReload(n => n + 1) } catch (e) { setError(e instanceof Error ? e.message : '删除失败，请重试。') } }}><Icon name="trash" size={16} />删除练习</button></div>)}</div>
      {data.totalPages > 1 && <div className="practice-pagination"><button className="btn btn-ghost" disabled={page === 0} onClick={() => setPage(n => n - 1)}>上一页</button><span>第 {page + 1} / {data.totalPages} 页</span><button className="btn btn-ghost" disabled={page + 1 >= data.totalPages} onClick={() => setPage(n => n + 1)}>下一页</button></div>}
    </>}
  </div></div>
}
