import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { fields, getProject, projectHistory, removeMaterial, requestKey, retryIndex, saveFacts, saveMaterial, startProject, type Facts, type Project, type ProjectSession } from '../api/projects'
import { PageHeading } from '../components/Ui'
import Icon from '../components/Icon'

export default function ProjectWorkspacePage() {
  const id = useParams().id!, navigate = useNavigate()
  const [project, setProject] = useState<Project | null>(null), [history, setHistory] = useState<ProjectSession[]>([])
  const [facts, setFacts] = useState<Facts | null>(null), [name, setName] = useState(''), [baseRevision, setBaseRevision] = useState(0)
  const [dirty, setDirty] = useState(false), dirtyRef = useRef(false), restored = useRef(false)
  const [materialKey, setMaterialKey] = useState<string | null>(null), [title, setTitle] = useState(''), [origin, setOrigin] = useState(''), [content, setContent] = useState(''), [materialBase, setMaterialBase] = useState(0)
  const [error, setError] = useState(''), [notice, setNotice] = useState(''), [busy, setBusy] = useState(''), lock = useRef(false)
  function adopt(value: Project) { setProject(value); if (!dirtyRef.current) { setFacts(value.facts); setName(value.name); setBaseRevision(value.revision) } }
  async function load() { const [value, sessions] = await Promise.all([getProject(id), projectHistory(id)]); adopt(value); setHistory(sessions); return value }
  useEffect(() => {
    let cancelled = false
    load().then(value => {
      if (cancelled || restored.current) return; restored.current = true
      try { const draft = JSON.parse(localStorage.getItem('project-facts:' + id) || 'null'); if (draft) { dirtyRef.current = true; setDirty(true); setFacts(draft.facts); setName(draft.name); setBaseRevision(draft.revision); if (draft.revision !== value.revision) setNotice('已恢复旧版本草稿。请先核对当前材料，再重新载入事实卡，避免覆盖更新。') } } catch { setNotice('此浏览器无法恢复事实卡草稿，请保持页面打开。') }
    }).catch(e => setError(e.message))
    const timer = window.setInterval(() => { if (!cancelled) void load().catch(e => setError(e.message)) }, 4000)
    return () => { cancelled = true; window.clearInterval(timer) }
  }, [id])
  useEffect(() => { if (dirty && facts) { try { localStorage.setItem('project-facts:' + id, JSON.stringify({ facts, name, revision: baseRevision })) } catch { setNotice('浏览器无法保存草稿，请保持页面打开并保存事实卡。') } } }, [dirty, facts, name, baseRevision, id])
  async function task(label: string, operation: () => Promise<void>) { if (lock.current) return; lock.current = true; setBusy(label); setError(''); try { await operation() } catch (e) { setError(e instanceof Error ? e.message : '操作未完成') } finally { lock.current = false; setBusy('') } }
  function change(field: string, value: string | boolean) { dirtyRef.current = true; setDirty(true); setFacts(old => old ? { ...old, [field]: value } : old) }
  function resetMaterial() { setMaterialKey(null); setTitle(''); setOrigin(''); setContent(''); setMaterialBase(0) }
  function reloadFacts() { if (dirty && !window.confirm('放弃当前事实卡草稿，重新载入已保存版本？')) return; if (!project) return; dirtyRef.current = false; setDirty(false); adopt(project); localStorage.removeItem('project-facts:' + id); setNotice('') }
  const canStart = !!project?.facts.confirmed && project.indexStatus === 'READY' && project.indexedRevision === project.revision
  return <div className="page-scroll"><div className="page-content practice-page">
    <PageHeading eyebrow="FACTS BEFORE FLUENCY" title={project?.name || '项目事实卡'} description="先把已完成的事实和未完成的改进分清，再把它们讲成自己的经历。" action={<Link className="btn btn-ghost" to="/projects">返回项目</Link>} />
    {error && <p role="alert" className="error-inline">{error}</p>}{notice && <p role="status" className="practice-intro">{notice}</p>}
    {!project || !facts ? <p role="status">正在加载项目材料…</p> : <>
      <div className="project-version-banner"><span>材料版本 {project.revision} · {project.indexStatus === 'READY' ? '检索已同步' : project.indexStatus === 'FAILED' ? '索引失败' : '正在同步私有材料'}</span>{project.indexStatus === 'FAILED' && <button className="btn btn-ghost" disabled={!!busy} onClick={() => void task('index', async () => adopt(await retryIndex(id)))}>重试索引</button>}<p>仅此项目的当前版本参与新练习；已有评估保留当时材料。</p></div>
      <div className="project-edit-grid"><section className="project-training-card"><div className="practice-section-heading"><h2>项目事实卡</h2><span>编辑基于版本 {baseRevision}</span></div>
        <p>简历自动提取只作草稿。结果、数字和个人职责由你核对；没有证据时留空，后续会列为待补。</p>
        <label>项目名称<input maxLength={120} value={name} onChange={e => { dirtyRef.current = true; setDirty(true); setName(e.target.value) }} /></label>
        {Object.keys(facts).filter(field => field !== 'confirmed').map(field => <label key={field}>{fields[field]}<textarea maxLength={3000} value={facts[field as keyof Facts] as string || ''} onChange={e => change(field, e.target.value)} placeholder={field === 'improvements' ? '仅填写尚未完成、可以改进的方案' : '填写真实信息；未知或暂无证据可留空'} /></label>)}
        <label className="project-check"><input type="checkbox" checked={facts.confirmed} onChange={e => change('confirmed', e.target.checked)} />我已核对这些项目事实，未完成的改进已单独填写</label>
        <div className="practice-actions"><button className="btn btn-primary" disabled={!!busy || !name.trim()} onClick={() => void task('facts', async () => { const value = await saveFacts(id, name, facts, baseRevision); dirtyRef.current = false; setDirty(false); localStorage.removeItem('project-facts:' + id); adopt(value); setNotice('事实卡已保存。索引同步后，可按新版材料开始练习。') })}>{busy === 'facts' ? '正在保存…' : '保存事实卡'}</button><button className="btn btn-ghost" disabled={!!busy} onClick={reloadFacts}>重新载入已保存版本</button></div>
        <details><summary>查看简历中的原始提取草稿</summary><pre className="project-extraction">{project.extraction}</pre><p>来源：简历 {project.resumeId} 中第 {project.resumeProjectIndex + 1} 个项目；这里保留提取结果，修改事实卡不会覆盖它。</p></details>
      </section><section className="project-training-card"><h2>补充项目材料</h2><p>填写项目说明、测试记录或设计权衡。保留出处和版本；删除当前材料后，历史评估仍保存原引用。</p>
        {project.materials.filter(m => !m.deleted).map(m => <div className="project-material-row" key={m.id}><strong>{m.title} · v{m.version}</strong><p>{m.origin}</p><details><summary>查看材料原文</summary><pre>{m.content}</pre></details><div className="practice-actions"><button className="text-button" disabled={!!busy} onClick={() => { setMaterialKey(m.id); setTitle(m.title); setOrigin(m.origin); setContent(m.content); setMaterialBase(project.revision) }}>编辑材料</button><button className="text-button" disabled={!!busy} onClick={() => { if (window.confirm('从当前项目检索中删除这份材料？旧版本及历史评估引用仍保留。')) void task('delete', async () => { adopt(await removeMaterial(id, m.id, project.revision)); if (materialKey === m.id) resetMaterial() }) }}>删除当前材料</button></div></div>)}
        <h3>{materialKey ? '修订现有材料' : '添加一份材料'}</h3><label>材料标题<input maxLength={120} value={title} onChange={e => { if (!materialBase) setMaterialBase(project.revision); setTitle(e.target.value) }} /></label><label>出处<input maxLength={500} value={origin} onChange={e => { if (!materialBase) setMaterialBase(project.revision); setOrigin(e.target.value) }} placeholder="例如 README 第三节 / 本人测试记录 2026-10-03" /></label>
        <label>文本或 Markdown<textarea className="project-material-editor" maxLength={10000} value={content} onChange={e => { if (!materialBase) setMaterialBase(project.revision); setContent(e.target.value) }} /></label>
        <label className="speech-import">从本地说明文件读取<input type="file" aria-label="读取项目说明文件" accept=".txt,.md,text/plain,text/markdown" onChange={async e => { const file = e.target.files?.[0]; if (!file) return; if (file.size > 50000) { setError('说明文件过大，请选取一万字以内的正文。'); return } const value = await file.text(); if (value.length > 10000) setError('材料最多一万字。'); else { setContent(value); setTitle(file.name); setOrigin('本地文件：' + file.name); setMaterialBase(project.revision) }; e.target.value = '' }} /></label>
        <div className="practice-actions"><button className="btn btn-primary" disabled={!!busy || !title.trim() || !origin.trim() || !content.trim()} onClick={() => void task('material', async () => { adopt(await saveMaterial(id, materialKey, title, origin, content, materialBase || project.revision)); resetMaterial(); setNotice('材料已保存为新版本，正在同步检索。') })}>保存材料版本</button>{materialKey && <button className="btn btn-ghost" onClick={resetMaterial}>取消修订</button>}</div>
      </section></div>
      <div className="practice-section-heading"><h2>围绕事实练六种表达</h2><span>{!project.facts.confirmed ? '请先核对并保存事实卡' : !canStart ? '等待材料索引完成' : '使用当前材料版本'}</span></div>
      <div className="project-template-grid">{project.templates.map(t => <section className="question-card" key={t.id}><div className="question-meta">建议 {t.seconds} 秒</div><h2>{t.title}</h2><p>{t.question}</p><button className="btn btn-primary" disabled={!!busy || !canStart || dirty} onClick={() => void task('start', async () => { const storage = 'project-start:' + id + ':' + project.revision + ':' + t.id; const session = await startProject(id, t.id, project.revision, requestKey(storage)); sessionStorage.removeItem(storage); navigate('/projects/practice/' + session.id) })}>开始练习<Icon name="arrow" size={15} /></button></section>)}</div>
      <section className="project-training-card"><h2>项目练习记录</h2>{history.length === 0 ? <p>从一个模板开始，回答和当时材料会一并保存。</p> : history.map(s => <Link className="project-history-row" to={'/projects/practice/' + s.id} key={s.id}><span>{s.template === 'FOLLOWUP' ? '专项追问' : project.templates.find(t => t.id === s.template)?.title} · 材料版本 {s.snapshot.revision}</span><span>{s.attempts.length} 次回答<Icon name="arrow" size={15} /></span></Link>)}</section>
    </>}
  </div></div>
}
