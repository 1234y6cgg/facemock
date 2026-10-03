import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getInterviewResume, type InterviewResume } from '../api/client'
import Icon from '../components/Icon'
import { ErrorState, LoadingState, PageHeading } from '../components/Ui'

function SkillTags({ items }: { items?: string[] | null }) {
  const skills = [...new Set((items ?? []).flatMap((s) => s.split(/[,，、/\\+]|\s{2,}/)).map((s) => s.trim()).filter(Boolean))]
  return <div className="tag-row">{skills.length ? skills.map((skill) => <span className="tag" key={skill}>{skill}</span>) : <span className="empty-copy">暂无相关信息</span>}</div>
}
export default function ResumePage() {
  const sessionId = Number(useParams().id)
  const navigate = useNavigate()
  const [data, setData] = useState<InterviewResume | null>(null)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [tab, setTab] = useState<'analysis' | 'original'>('analysis')
  useEffect(() => {
    let cancelled = false
    setError('')
    getInterviewResume(sessionId).then((value) => { if (!cancelled) setData(value) }).catch((e) => { if (!cancelled) setError(e instanceof Error ? e.message : '加载简历失败') })
    return () => { cancelled = true }
  }, [sessionId, reload])
  const structured = data?.structured
  return <div className="page-scroll"><div className="page-content resume-page">
    <PageHeading eyebrow="YOUR EXPERIENCE, IN FOCUS" title="每一段经历，都值得讲清楚。" description="面试官基于这些经历与能力，展开有针对性的追问。" action={<button className="btn btn-ghost" onClick={() => navigate(`/interview/${sessionId}`)}><Icon name="back" size={16} />返回面试</button>} />
    {error ? <ErrorState message={error} onRetry={() => setReload((n) => n + 1)} /> : !data ? <LoadingState title="正在读取简历档案" /> : <>
      <div className="resume-file-banner"><div className="file-icon"><Icon name="file" size={25} /></div><div className="file-info"><strong>{data.filename}</strong><span>面试 {sessionId} 的简历档案</span></div><a className="btn btn-ghost" href={`/api/resumes/${data.resumeId}/file`} target="_blank" rel="noreferrer">打开原文件<Icon name="external" size={15} /></a></div>
      <div className="practice-intro"><p>选择简历中的一个项目，核对个人贡献与证据，练习项目介绍和专项追问。</p><Link className="btn btn-primary" to={'/projects?resumeId=' + data.resumeId}>练项目话术<Icon name="arrow" size={15} /></Link></div>
      <div className="document-tabs" role="tablist" aria-label="简历视图"><button id="analysis-tab" role="tab" aria-selected={tab === 'analysis'} aria-controls="resume-content" onClick={() => setTab('analysis')} className={tab === 'analysis' ? 'active' : ''}>结构化分析</button><button id="original-tab" role="tab" aria-selected={tab === 'original'} aria-controls="resume-content" onClick={() => setTab('original')} className={tab === 'original' ? 'active' : ''}>简历原文</button></div>
      <div id="resume-content" role="tabpanel" aria-labelledby={tab === 'analysis' ? 'analysis-tab' : 'original-tab'}>{tab === 'original' ? <section className="raw-document"><div className="eyebrow">ORIGINAL DOCUMENT</div><pre>{data.rawText || '暂无提取到的简历原文。'}</pre></section> : <>
        <div className="resume-overview-grid"><section className="report-section"><div className="section-heading"><h2>整体画像</h2><Icon name="spark" size={20} /></div><p className="summary-text">{structured?.summary || '暂无整体画像。'}</p></section><section className="report-section"><div className="section-heading"><h2>技能与技术栈</h2><span>{structured?.skills?.length ?? 0} 项</span></div><SkillTags items={structured?.skills} /></section></div>
        <div className="section-heading projects-heading"><h2>项目经历</h2><span>{structured?.projects?.length ?? 0} 段经历</span></div>{structured?.projects?.length ? structured.projects.map((project, i) => <section className="project-card" key={i}><div className="project-number">{String(i + 1).padStart(2, '0')}</div><div className="project-content"><div className="project-title"><h2>{project.name || '未命名项目'}</h2>{project.role && <span className="pill neutral">{project.role}</span>}</div><p className="summary-text">{project.desc}</p><SkillTags items={project.techStack} /><div className="project-detail-grid">{project.responsibilities?.length > 0 && <div><h3>个人职责</h3><ul>{project.responsibilities.map((item, j) => <li key={j}>{item}</li>)}</ul></div>}{project.highlights?.length > 0 && <div><h3>亮点与难点</h3><ul>{project.highlights.map((item, j) => <li key={j}>{item}</li>)}</ul></div>}</div></div></section>) : <div className="state-card"><Icon name="file" size={30} /><h2>还没有提取到项目经历</h2><p>可以查看原文，确认简历中的经历是否完整。</p></div>}
      </>}</div>
    </>}
  </div></div>
}
