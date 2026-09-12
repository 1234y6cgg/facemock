import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { uploadResume, getResumeStatus, createInterview } from '../api/client'

type Phase = 'idle' | 'parsing' | 'parsed' | 'error'

export default function UploadPage() {
  const navigate = useNavigate()
  const [phase, setPhase] = useState<Phase>('idle')
  const [error, setError] = useState('')
  const [resumeId, setResumeId] = useState<number | null>(null)

  async function onUpload(file: File) {
    setPhase('parsing')
    setError('')
    try {
      const { resumeId } = await uploadResume(file)
      setResumeId(resumeId)
      await poll(resumeId)
    } catch (e) {
      setError(e instanceof Error ? e.message : '上传失败')
      setPhase('error')
    }
  }

  async function poll(id: number) {
    for (let i = 0; i < 60; i++) {
      await new Promise((r) => setTimeout(r, 1000))
      const s = await getResumeStatus(id)
      if (s.status === 'PARSED') {
        setPhase('parsed')
        return
      }
      if (s.status === 'FAILED') {
        setError('简历解析失败，请重试')
        setPhase('error')
        return
      }
    }
    setError('解析超时，请重试')
    setPhase('error')
  }

  async function onStart() {
    if (!resumeId) return
    try {
      const { sessionId } = await createInterview(resumeId)
      navigate(`/interview/${sessionId}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : '创建面试失败')
    }
  }

  return (
    <main className="page">
      <h1>面经Mock</h1>
      <p className="sub">多 Agent 技术面试仿真系统</p>

      {phase === 'idle' && (
        <label className="upload-zone">
          上传简历（PDF / Word）
          <input
            type="file"
            accept=".pdf,.doc,.docx"
            style={{ display: 'none' }}
            onChange={(e) => {
              const f = e.target.files?.[0]
              if (f) onUpload(f)
            }}
          />
        </label>
      )}

      {phase === 'parsing' && <p className="status">正在解析简历…</p>}
      {phase === 'parsed' && (
        <button className="primary" onClick={onStart}>
          开始面试
        </button>
      )}
      {error && <p className="error">{error}</p>}
    </main>
  )
}
