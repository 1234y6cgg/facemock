import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getReport, Report } from '../api/client'

export default function ReportPage() {
  const { id } = useParams()
  const [report, setReport] = useState<Report | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getReport(Number(id))
      .then(setReport)
      .catch((e) => setError(e instanceof Error ? e.message : '加载报告失败'))
  }, [id])

  if (error) return <main className="page"><p className="error">{error}</p></main>
  if (!report) return <main className="page"><p className="status">生成报告中…</p></main>

  return (
    <main className="page">
      <h1>复盘报告</h1>

      <section className="card">
        <h2>多维评分</h2>
        <div className="scores">
          {Object.entries(report.scores).map(([k, v]) => (
            <div key={k} className="score">
              <span>{k}</span>
              <strong>{v}</strong>
            </div>
          ))}
        </div>
      </section>

      <section className="card">
        <h2>薄弱点</h2>
        <ul>{report.weaknesses.map((w, i) => <li key={i}>{w}</li>)}</ul>
      </section>

      <section className="card">
        <h2>改进建议</h2>
        <ul>{report.suggestions.map((s, i) => <li key={i}>{s}</li>)}</ul>
      </section>

      <button className="primary" onClick={() => (window.location.href = '/')}>
        再来一次
      </button>
    </main>
  )
}
