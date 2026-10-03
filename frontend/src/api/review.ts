import type { Question, Session } from './practice'
export interface Preferences { timezone: string; dailyLimit: number; intervals: number[]; requiredPasses: number; paused: boolean }
export interface Evidence { evaluationId: string; attemptId: string; sessionId: string; questionId: string; status: string; answeredAt: string; assisted: boolean; delayed: boolean; passed: boolean; quote: string }
export interface Point { id: string; title: string; topic: string; state: string; dueDate: string | null; lastStatus: string | null; validAnswers: number; independentPasses: number; latestSessionId: string | null; evidence: Evidence[]; uncertainAnswers: number }
export interface Overview { preferences: Preferences; points: Point[]; uncertainItems: number; failedEvaluations: number; pendingEvaluations: number; validAnswers: number; independentPasses: number }
export interface Recommendation { question: Question; pointId: string | null; reason: string; kind: string; dueDate: string | null }
export interface Today { date: string; preferences: Preferences; duePoints: number; recommendations: Recommendation[] }
async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const response = await fetch('/api' + path, { method, ...(body !== undefined && {headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}) })
  if (!response.ok) { const error = await response.json().catch(() => ({})); throw new Error(error.message || '读取失败，请稍后重试。') }
  return response.json()
}
export const getToday = () => request<Today>('/review/today')
export const getProgress = () => request<Overview>('/progress/knowledge-points')
export const configureReview = (body: Preferences) => request<Preferences>('/review/preferences', 'PUT', body)
export const skipToday = (questionId: string) => request<Today>('/review/skip', 'POST', {questionId})
export const startReview = (pointId: string, clientRequestId: string) => request<Session>('/review/sessions', 'POST', {pointId,clientRequestId})
export const states: Record<string,string> = { UNPRACTICED:'未练习', PRACTICING:'练习中', TO_CONSOLIDATE:'待巩固', CONSOLIDATED:'已巩固' }
