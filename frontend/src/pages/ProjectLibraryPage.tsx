import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { getResumeStatus, uploadResume, type ResumeStatus } from '../api/client'
import { importProject, listProjects, listProjectResumes, type Project, type ResumeChoice } from '../api/projects'
import { PageHeading } from '../components/Ui'
import Icon from '../components/Icon'

export default function ProjectLibraryPage() {
  const navigate = useNavigate(), [params] = useSearchParams()
  const [projects, setProjects] = useState<Project[]>([]), [resumes, setResumes] = useState<ResumeChoice[]>([])
  const [resumeId, setResumeId] = useState(Number(params.get('resumeId')) || 0), [upload, setUpload] = useState<ResumeStatus | null>(null)
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [loading, setLoading] = useState(true)
  async function load() { const [items, files] = await Promise.all([listProjects(), listProjectResumes()]); setProjects(items); setResumes(files); setLoading(false) }
  useEffect(() => { void load().catch(e => { setError(e.message); setLoading(false) }) }, [])
  useEffect(() => {
    if (!upload || upload.status !== 'PARSING') return
    const timer = window.setInterval(() => { getResumeStatus(upload.resumeId).then(value => { setUpload(value); if (value.status === 'PARSED') { setResumeId(value.resumeId); void load().catch(e => setError(e.message)) } }).catch(e => setError(e.message)) }, 2000)
    return () => window.clearInterval(timer)
  }, [upload?.resumeId, upload?.status])
  const selected = resumes.find(r => r.id === resumeId)
  async function choose(index: number) { setBusy(true); setError(''); try { const project = await importProject(resumeId, index); navigate('/projects/' + project.id) } catch (e) { setError(e instanceof Error ? e.message : '导入失败') } finally { setBusy(false) } }
  return <div className="page-scroll"><div className="page-content practice-page">
    <PageHeading eyebrow="YOUR WORK, IN YOUR WORDS" title="把做过的事，讲得具体可信。" description="从简历选一个项目，核对事实和证据，再练个人贡献、选型与结果。" />
    {error && <p className="error-inline" role="alert">{error}<button className="text-button" onClick={() => void load().catch(e => setError(e.message))}>重新加载</button></p>}
    <section className="project-training-card"><h2>从简历选择项目</h2><p>已上传的简历可直接选择，也可以上传 PDF、Word 或图片简历。提取结果需要本人核对。</p>
      <label>已有简历<select value={resumeId} onChange={e => setResumeId(Number(e.target.value))}><option value={0}>请选择简历</option>{resumes.map(r => <option value={r.id} key={r.id}>{r.filename} · {r.projectNames.length} 个项目</option>)}</select></label>
      <label className="speech-import">上传简历<input aria-label="上传项目训练简历" type="file" accept=".pdf,.doc,.docx,.png,.jpg,.jpeg,.webp" disabled={busy} onChange={async e => { const file = e.target.files?.[0]; if (!file) return; setBusy(true); setError(''); try { const value = await uploadResume(file); setUpload({ resumeId: value.resumeId, status: 'PARSING', parsed: null }) } catch (err) { setError(err instanceof Error ? err.message : '上传失败') } finally { setBusy(false); e.target.value = '' } }} /></label>
      {upload?.status === 'PARSED' && <p>识别结果请本人核对。<a href={`/api/resumes/${upload.resumeId}/text`} target="_blank" rel="noreferrer">查看提取文字</a></p>}{upload?.status === 'PARSING' && <p role="status">简历已保存，正在提取项目，请稍候…</p>}{upload?.status === 'FAILED' && <p role="alert">{upload.errorMessage || '简历解析失败，原文件已保留。请检查文件内容或模型配置后重新上传。'}</p>}
      {selected && <div className="project-picker">{selected.projectNames.map((name, index) => <button key={index} className="btn btn-ghost" disabled={busy} onClick={() => void choose(index)}>{name}<Icon name="arrow" size={16} /></button>)}</div>}
    </section>
    <div className="practice-section-heading"><h2>我的项目事实卡</h2><span>{projects.length} 个项目</span></div>
    {loading ? <p role="status">正在读取项目…</p> : <div className="question-grid">{projects.map(p => <Link className="question-card" to={'/projects/' + p.id} key={p.id}><div className="question-meta">材料版本 {p.revision} · {p.facts.confirmed ? '本人已核对' : '待核对事实'}</div><h2>{p.name}</h2><p>{p.facts.background || '先补充项目背景，再组织你的介绍。'}</p><span className="btn btn-ghost">编辑事实，开始练习<Icon name="arrow" size={15} /></span></Link>)}</div>}
    {!loading && projects.length === 0 && <p className="empty-copy">选择简历中的一个项目，开始建立自己的训练材料。</p>}
  </div></div>
}
