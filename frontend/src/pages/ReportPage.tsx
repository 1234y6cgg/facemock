import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getReport, type Report } from '../api/client'
import Icon from '../components/Icon'
import { ErrorState, LoadingState, PageHeading } from '../components/Ui'

function scoreColor(value: number) { return value >= 8 ? 'var(--success)' : value >= 6 ? 'var(--primary)' : value >= 4 ? 'var(--warning)' : 'var(--danger)' }
export default function ReportPage() {
  const sessionId = Number(useParams().id)
  const navigate = useNavigate()
  const [report, setReport] = useState<Report | null>(null)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let cancelled = false
    setError('')
    getReport(sessionId).then((data) => { if (!cancelled) setReport(data) }).catch((e) => { if (!cancelled) setError(e instanceof Error ? e.message : '加载报告失败') })
    return () => { cancelled = true }
  }, [sessionId, reload])
  const back = <button className="btn btn-ghost" onClick={() => navigate(`/interview/${sessionId}`)}><Icon name="back" size={16} />回看对话</button>
  if (error || !report) return <div className="page-scroll"><div className="page-content"><PageHeading eyebrow="REFLECT & GROW" title="每一次复盘，都有收获。" description={`面试 ${sessionId} · 把今天的发现，变成下一次的进步。`} action={back} />{error ? <ErrorState message={error} onRetry={() => setReload((n) => n + 1)} /> : <LoadingState title="正在整理你的面试复盘" description="面试官正在回顾整场对话，生成评分、薄弱点与改进建议。" />}</div></div>
  const overall = report.scores['总体'] ?? report.scores['overall']
  const dimensions = Object.entries(report.scores).filter(([name]) => name !== '总体' && name !== 'overall')
  const validOverall = typeof overall === 'number' && Number.isFinite(overall)
  const percentage = validOverall ? Math.max(0, Math.min(100, overall * 10)) : 0
  return <div className="page-scroll"><div className="page-content report-page">
    <PageHeading eyebrow="REFLECT & GROW" title="每一次复盘，都有收获。" description={`面试 ${sessionId} · 把今天的发现，变成下一次的进步。`} action={back} />
    <section className="report-overview"><div className="report-intro"><span className="pill"><Icon name="check" size={13} />面试已完成</span><h2>看见表现，<br />也看见成长的空间。</h2><p>这份报告基于本次面试对话生成。<br />分数是参考，具体的薄弱点与改进方向更值得关注。</p><span className="report-session">SESSION / {String(sessionId).padStart(3, '0')}</span></div><div className="overall-score"><div className="score-dial" role="img" aria-label={validOverall ? `综合评分 ${overall}，满分 10` : '暂无综合评分'}><svg viewBox="0 0 180 180" aria-hidden="true"><circle className="dial-track" cx="90" cy="90" r="76" /><circle className="dial-fill" cx="90" cy="90" r="76" strokeDasharray={`${percentage * 4.775} 477.5`} /></svg><div><strong>{validOverall ? overall : '—'}</strong><span>满分 10 分</span></div></div><span className="overall-label">综合表现</span></div></section>
    <section className="dimensions-section"><div className="section-heading"><h2>能力维度</h2><span>本次面试评估</span></div>{dimensions.length ? <div className="score-grid">{dimensions.map(([name, score], index) => <div className="score-card" key={name}><div className="score-card-top"><span>{name}</span><span className="score-index">0{index + 1}</span></div><div className="score-value">{Number.isFinite(score) ? score : '—'}<span>/ 10</span></div><div className="score-bar"><div style={{ width: `${Number.isFinite(score) ? Math.max(0, Math.min(100, score * 10)) : 0}%`, background: scoreColor(score) }} /></div></div>)}</div> : <p className="empty-copy">本次报告没有单独的维度评分。</p>}</section>
    <div className="report-detail-grid"><section className="report-section"><div className="report-section-heading"><span className="section-icon amber"><Icon name="target" size={20} /></span><div><h2>值得补强的地方</h2><p>先看清问题，才能有针对性地练习。</p></div><span className="detail-count">{report.weaknesses.length}</span></div>{report.weaknesses.length ? <ol className="insight-list">{report.weaknesses.map((item, i) => <li key={i}><span>{String(i + 1).padStart(2, '0')}</span><p>{item}</p></li>)}</ol> : <p className="empty-copy">本次报告未列出具体薄弱点。</p>}</section><section className="report-section"><div className="report-section-heading"><span className="section-icon green"><Icon name="book" size={20} /></span><div><h2>下一步，怎么练</h2><p>把建议带进下一次准备与练习。</p></div><span className="detail-count">{report.suggestions.length}</span></div>{report.suggestions.length ? <ol className="insight-list suggestions">{report.suggestions.map((item, i) => <li key={i}><span><Icon name="arrow" size={15} /></span><p>{item}</p></li>)}</ol> : <p className="empty-copy">本次报告未列出具体改进建议。</p>}</section></div>
    <div className="report-next"><div><span className="eyebrow">KEEP MOVING FORWARD</span><h3>带着这次的发现，再练一次。</h3></div><div className="heading-actions"><button className="btn btn-ghost" onClick={() => navigate(`/interview/${sessionId}/resume`)}><Icon name="file" size={16} />查看简历</button><button className="btn btn-primary" onClick={() => navigate('/mock')}>开始新面试<Icon name="arrow" size={17} /></button></div></div>
  </div></div>
}
