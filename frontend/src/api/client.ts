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

export interface HistoryItem {
  id: number
  resumeFilename: string | null
  status: string
  startedAt: string
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

export interface CreateInterviewPayload {
  resumeId: number
  targetRole: string
  jobResponsibilities?: string
  jobRequirements?: string
  mode?: string
}

export async function createInterview(payload: CreateInterviewPayload): Promise<{ sessionId: number }> {
  const res = await fetch(`${BASE}/interviews`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
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

export async function listInterviews(): Promise<HistoryItem[]> {
  const res = await fetch(`${BASE}/interviews`)
  if (!res.ok) throw new Error('暂时无法加载面试记录')
  return res.json()
}

export async function deleteInterview(sessionId: number): Promise<void> {
  const res = await fetch(`${BASE}/interviews/${sessionId}`, { method: 'DELETE' })
  if (!res.ok) throw new Error('删除失败')
}

export interface InterviewResume {
  resumeId: number
  filename: string
  rawText: string
  structured: ResumeStructured
}

export async function getInterviewResume(sessionId: number): Promise<InterviewResume> {
  const res = await fetch(`${BASE}/interviews/${sessionId}/resume`)
  if (!res.ok) throw new Error('获取简历失败')
  return res.json()
}

export interface InterviewDetail {
  id: number
  targetRole?: string
  mode?: string
  status: 'IN_PROGRESS' | 'COMPLETED' | string
  stage: string
  startedAt: string
  endedAt: string
}

export async function getInterview(sessionId: number): Promise<InterviewDetail> {
  const res = await fetch(`${BASE}/interviews/${sessionId}`)
  if (!res.ok) throw new Error('获取会话失败')
  return res.json()
}
