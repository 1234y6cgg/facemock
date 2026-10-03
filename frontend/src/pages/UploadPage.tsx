import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createInterview, getResumeStatus, uploadResume, type ResumeStructured } from '../api/client'
import Icon from '../components/Icon'

type Phase = 'idle' | 'parsing' | 'parsed' | 'error'
export default function UploadPage() {
  const navigate = useNavigate()
  const [phase, setPhase] = useState<Phase>('idle')
  const [error, setError] = useState('')
  const [resumeId, setResumeId] = useState<number | null>(null)
  const [filename, setFilename] = useState('')
  const [resume, setResume] = useState<ResumeStructured | null>(null)
  const [dragOver, setDragOver] = useState(false)
  const [targetRole, setTargetRole] = useState('')
  const [detailMode, setDetailMode] = useState(false)
  const [responsibilities, setResponsibilities] = useState('')
  const [requirements, setRequirements] = useState('')
  const [interviewMode, setInterviewMode] = useState<'TEXT' | 'VOICE'>('TEXT')
  const [starting, setStarting] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const uploadEpoch = useRef(0)
  useEffect(() => () => { uploadEpoch.current++ }, [])
  async function onUpload(file: File) {
    if (phase === 'parsing' || starting) return
    if (!/\.(pdf|docx?)$/i.test(file.name)) { setError('请选择 PDF、DOC 或 DOCX 格式的简历。'); return }
    const epoch = ++uploadEpoch.current
    setPhase('parsing'); setError(''); setFilename(file.name); setResumeId(null); setResume(null)
    try {
      const result = await uploadResume(file)
      if (epoch !== uploadEpoch.current) return
      for (let i = 0; i < 60; i++) {
        await new Promise((resolve) => setTimeout(resolve, 1000))
        if (epoch !== uploadEpoch.current) return
        const status = await getResumeStatus(result.resumeId)
        if (epoch !== uploadEpoch.current) return
        if (status.status === 'PARSED') { setResumeId(result.resumeId); setResume(status.parsed); setPhase('parsed'); return }
        if (status.status === 'FAILED') throw new Error('简历解析失败，请检查文件内容后重新上传。')
      }
      throw new Error('解析时间较长，请稍后重新上传。')
    } catch (e) {
      if (epoch !== uploadEpoch.current) return
      setError(e instanceof Error ? e.message : '上传失败，请重试'); setPhase('error')
    } finally { if (inputRef.current) inputRef.current.value = '' }
  }
  async function onStart() {
    if (!resumeId || phase !== 'parsed' || !targetRole.trim() || starting) return
    setStarting(true); setError('')
    try {
      const { sessionId } = await createInterview({ resumeId, targetRole: targetRole.trim(), jobResponsibilities: detailMode ? responsibilities.trim() : undefined, jobRequirements: detailMode ? requirements.trim() : undefined, mode: interviewMode })
      navigate(`/interview/${sessionId}`)
    } catch (e) { setError(e instanceof Error ? e.message : '创建面试失败'); setStarting(false) }
  }
  return <div className="page-scroll"><div className="page-content preparation-page">
    <header className="preparation-hero"><div><div className="eyebrow"><span className="accent-line" />A LITTLE PRACTICE. A BIG DIFFERENCE.</div><h1>把下一场面试，<br />变成你的<span className="hero-emphasis">主场<span className="emphasis-dot">.</span></span></h1><p>从你的真实经历出发，在追问中发现盲区，在复盘中找到方向。</p></div><div className="hero-stamp" aria-hidden="true"><div className="stamp-ring"><Icon name="spark" size={37} /></div><span>READY FOR<br />WHAT'S NEXT</span><i>↗</i></div></header>
    <div className="preparation-grid"><section className="setup-panel" aria-label="面试设置">
      <div className="panel-heading"><div><h2>准备你的面试</h2><p>一份简历，一个目标，即可开始。</p></div><span className="pill neutral">新的开始</span></div>
      <section className="setup-section"><div className="section-title"><span className="step-number">01</span><h3>上传简历</h3><span>必填</span></div><input ref={inputRef} id="resume-file" className="visually-hidden" type="file" accept=".pdf,.doc,.docx" disabled={phase === 'parsing' || starting} onChange={(e) => { const file = e.target.files?.[0]; if (file) void onUpload(file) }} />
        {phase === 'parsed' ? <div className="uploaded-file"><div className="file-icon"><Icon name="file" size={24} /></div><div className="file-info"><strong>{filename}</strong><span><Icon name="check" size={13} />解析完成 · {resume?.projects?.length ?? 0} 段项目经历</span></div><button className="text-button" disabled={starting} onClick={() => inputRef.current?.click()}>更换</button></div> : <button type="button" className={`upload-dropzone ${dragOver ? 'drag' : ''}`} disabled={phase === 'parsing' || starting} onClick={() => inputRef.current?.click()} onDragOver={(e) => { e.preventDefault(); setDragOver(true) }} onDragLeave={() => setDragOver(false)} onDrop={(e) => { e.preventDefault(); setDragOver(false); const file = e.dataTransfer.files[0]; if (file) void onUpload(file) }}>{phase === 'parsing' ? <><div className="spinner" /><strong>正在读懂你的经历…</strong><span>{filename}</span></> : <><div className="upload-symbol"><Icon name="upload" size={24} /></div><strong>拖入简历，或<span>选择文件</span></strong><span>支持 PDF、DOC、DOCX 格式</span></>}</button>}
        <p className="field-hint">简历将用于提问与复盘，你可在面试记录中查看。</p>
      </section>
      <section className="setup-section"><div className="section-title"><span className="step-number">02</span><h3>确定目标岗位</h3><span>必填</span></div><label className="visually-hidden" htmlFor="target-role">目标岗位方向</label><input id="target-role" className="field-input" value={targetRole} disabled={starting} onChange={(e) => setTargetRole(e.target.value)} placeholder="你想应聘什么岗位？" /><div className="role-presets"><span>试试</span>{['Java 后端工程师', '前端工程师', '产品经理'].map((role) => <button key={role} type="button" disabled={starting} onClick={() => setTargetRole(role)} className={targetRole === role ? 'selected' : ''}>{role}</button>)}</div><button className="disclosure-button" type="button" aria-expanded={detailMode} aria-controls="job-details" onClick={() => setDetailMode(!detailMode)}><Icon name="plus" size={15} />补充岗位职责与要求<span>可选</span><Icon name="chevron" size={14} className={detailMode ? 'rotate' : ''} /></button>{detailMode && <div id="job-details" className="job-details"><label htmlFor="job-responsibilities">岗位职责<textarea id="job-responsibilities" className="field-input" value={responsibilities} disabled={starting} onChange={(e) => setResponsibilities(e.target.value)} placeholder="粘贴招聘描述中的主要工作职责" /></label><label htmlFor="job-requirements">任职要求<textarea id="job-requirements" className="field-input" value={requirements} disabled={starting} onChange={(e) => setRequirements(e.target.value)} placeholder="粘贴能力、经验与专业要求" /></label></div>}</section>
      <section className="setup-section last"><div className="section-title"><span className="step-number">03</span><h3>选择回答方式</h3></div><div className="answer-modes" role="group" aria-label="面试回答方式">{(['TEXT', 'VOICE'] as const).map((mode) => <button key={mode} className={`answer-mode ${interviewMode === mode ? 'selected' : ''}`} aria-pressed={interviewMode === mode} disabled={starting} onClick={() => setInterviewMode(mode)}><Icon name={mode === 'TEXT' ? 'chat' : 'mic'} size={21} /><span><strong>{mode === 'TEXT' ? '文字面试' : '语音面试'}</strong><small>{mode === 'TEXT' ? '组织思路，键入回答' : '开口练习，自动转写'}</small></span><span className="radio-indicator" /></button>)}</div>{interviewMode === 'VOICE' && <p className="field-hint">语音识别需要浏览器支持和麦克风权限，也可在面试中切换为文字。</p>}</section>
      <div className="setup-footer">{error && <div className="error-inline" role="alert"><Icon name="alert" size={17} />{error}</div>}<button className="btn btn-primary start-button" disabled={phase !== 'parsed' || !targetRole.trim() || starting} onClick={onStart}>{starting ? '正在为你准备面试…' : '进入面试'}<Icon name="arrow" size={19} /></button><p>{phase !== 'parsed' ? '上传简历后，即可开启你的专属面试' : !targetRole.trim() ? '填写目标岗位，让提问更有针对性' : '准备好了，就从第一个问题开始'}</p></div>
    </section><aside className="preparation-aside"><section className="journey-card"><div className="eyebrow">YOUR NEXT CHAPTER</div><h2>不只回答问题。<br />更要看见成长。</h2><div className="journey-art" aria-hidden="true"><div className="art-orbit orbit-one" /><div className="art-orbit orbit-two" /><div className="art-axis" /><div className="art-core"><Icon name="spark" size={38} /></div><span className="art-label label-one">真实经历</span><span className="art-label label-two">深度追问</span><span className="art-point point-one" /><span className="art-point point-two" /><span className="art-coordinate">YOU ↗</span></div><div className="journey-divider" /><div className="journey-steps">{[{ icon: 'file' as const, title: '从你的经历开始', text: '结合简历与岗位，生成有针对性的问题。' }, { icon: 'chat' as const, title: '在追问中深入', text: '从背景到方案，再到细节与权衡。' }, { icon: 'chart' as const, title: '把复盘变成下一步', text: '看见薄弱环节，带着方向继续练习。' }].map((step, i) => <div key={step.title}><span className="journey-icon"><Icon name={step.icon} size={19} /></span><div><h3>{step.title}</h3><p>{step.text}</p></div><span className="journey-index">0{i + 1}</span></div>)}</div></section><div className="preparation-note"><span>✳</span><p>不用等到准备完美再开始。<br /><strong>练习本身，就是准备的一部分。</strong></p></div></aside></div>
    <footer className="page-footnote"><span>FaceMock / YOUR INTERVIEW WORKSPACE</span><span>每一场面试，都从一次认真练习开始。</span></footer>
  </div></div>
}
