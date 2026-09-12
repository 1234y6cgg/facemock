const BASE = '/api'

export interface ResumeStructured {
  summary: string
  skills: string[]
  projects: {
    name: string
    desc: string
    role: string
    techStack: string[]
    responsibilities: string[]
    highlights: string[]
  }[]
}

export interface ResumeStatus {
  resumeId: number
  status: 'PARSING' | 'PARSED' | 'FAILED'
  parsed: ResumeStructured | null
}

export interface MessageDto {
  role: string
  content: string
  layer: string | null
}

export interface Report {
  scores: Record<string, number>
  weaknesses: string[]
  suggestions: string[]
}

export async function uploadResume(file: File): Promise<{ resumeId: number }> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch(`${BASE}/resumes`, { method: 'POST', body: form })
  if (!res.ok) throw new Error('上传失败')
  return res.json()
}

export async function getResumeStatus(id: number): Promise<ResumeStatus> {
  const res = await fetch(`${BASE}/resumes/${id}`)
  if (!res.ok) throw new Error('查询失败')
  return res.json()
}

export async function createInterview(resumeId: number): Promise<{ sessionId: number }> {
  const res = await fetch(`${BASE}/interviews`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ resumeId }),
  })
  if (!res.ok) throw new Error('创建面试失败')
  return res.json()
}

export async function getMessages(sessionId: number): Promise<MessageDto[]> {
  const res = await fetch(`${BASE}/interviews/${sessionId}/messages`)
  if (!res.ok) throw new Error('获取消息失败')
  return res.json()
}

export async function getReport(sessionId: number): Promise<Report> {
  const res = await fetch(`${BASE}/interviews/${sessionId}/report`)
  if (!res.ok) throw new Error('获取报告失败')
  return res.json()
}
