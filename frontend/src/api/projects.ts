import { PracticeApiError, newRequestKey } from './practice'
export const fields: Record<string, string> = { background: '业务背景', contribution: '个人职责与贡献', approach: '已完成的方案', challenge: '实际难点与定位', result: '已有结果', evidence: '结果证据与测量口径', improvements: '尚未完成的改进', measurement: '测量方法', scale: '业务规模的依据' }
export const criteria: Record<string, string> = { relevance: '是否回答题意', contribution: '个人贡献是否清楚', evidence: '材料是否支持', consistency: '是否与材料一致' }
export const statuses: Record<string, string> = { COVERED: '已讲清楚', PARTIAL: '需要补充', MISSING: '尚未提到', CONFLICT: '请核对材料差异', UNCERTAIN: '当前材料无法判断' }
export const reasons: Record<string, string> = { ANSWERED: '已回应本项问题。', NEEDS_DETAIL: '需要补充具体过程或适用条件。', NO_EVIDENCE: '当前片段不足以支持这项表述，请补充证据。', CONTRADICTS_MATERIAL: '回答和材料存在差异，请核对双方原文；材料不准确时可修订后重新练习。', NOT_MENTIONED: '本次回答没有说明这一项。', UNVERIFIABLE: '根据当前材料无法作出判断。', PLAN_ONLY: '这是尚未完成的改进，不能作为已有成果。' }
export const proposals: Record<string, string> = { MEASURE: '可以补充测量方法、环境、前后对照与原始记录；实际测量后再填写结果。', CLARIFY_OWNERSHIP: '可以梳理你亲自完成、团队完成和协作完成的部分，再用过程证据说明。', COMPARE_ALTERNATIVES: '可以比较备选方案、选择依据和适用条件；未做过的实验须明确标为待验证。', TRACE_FAILURE: '可以补充现象、假设、排查步骤、修复与验证的完整链条。', VERIFY_BOUNDARIES: '可以设计失败边界验证，再根据实际结果决定是否实施改进。' }
export interface Facts { background: string; contribution: string; approach: string; challenge: string; result: string; evidence: string; improvements: string; confirmed: boolean }
export interface Material { id: string; version: number; title: string; origin: string; content: string; deleted: boolean; createdAt: string }
export interface Source { id: string; version: number; title: string; origin: string; content: string; planned: boolean }
export interface Project { id: string; name: string; resumeId: number; resumeProjectIndex: number; revision: number; indexedRevision: number; indexStatus: string; facts: Facts; extraction: string; materials: Material[]; templates: { id: string; title: string; question: string; seconds: number }[] }
export interface ResumeChoice { id: number; filename: string; projectNames: string[] }
export interface Assessment { answerHash: string; criteria: { id: string; status: string; reasonCode: string; candidateQuote: string; references: { sourceId: string; version: number; quote: string }[] }[]; missingFields: string[]; improvementActions: string[] }
export interface Evaluation { id: string; generation: number; status: string; errorCode: string | null; modelName: string | null; promptVersion: string | null; durationMs: number | null; result: Assessment | null }
export interface Attempt { id: string; clientRequestId: string; number: number; answer: string; parentAttemptId: string | null; evaluation: Evaluation; evaluations: Evaluation[]; comparison: { comparable: boolean; notice: string; criteria: { criterionId: string; before: string; after: string; change: string }[] } | null }
export interface ProjectSession { id: string; projectId: string; template: string; prompt: string; status: string; originAttemptId: string | null; snapshot: { projectName: string; revision: number; facts: Facts; sources: Source[] }; createdAt: string; attempts: Attempt[] }
async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const res = await fetch('/api/projects' + path, { method, ...(body !== undefined && { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }) })
  if (!res.ok) { const value = await res.json().catch(() => ({})); throw new PracticeApiError(value.message || '项目操作未完成，请重试。', res.status) }
  return res.status === 204 ? undefined as T : res.json()
}
export const listProjects = () => request<Project[]>('')
export const listProjectResumes = () => request<ResumeChoice[]>('/resumes')
export const importProject = (resumeId: number, projectIndex: number) => request<Project>('/from-resume', 'POST', { resumeId, projectIndex })
export const getProject = (id: string) => request<Project>('/' + id)
export const saveFacts = (id: string, name: string, facts: Facts, expectedRevision: number) => request<Project>('/' + id + '/facts', 'PUT', { name, facts, expectedRevision })
export const saveMaterial = (id: string, key: string | null, title: string, origin: string, content: string, expectedRevision: number) => request<Project>('/' + id + '/materials' + (key ? '/' + key : ''), key ? 'PUT' : 'POST', { title, origin, content, expectedRevision })
export const removeMaterial = (id: string, key: string, revision: number) => request<Project>('/' + id + '/materials/' + key + '?expectedRevision=' + revision, 'DELETE')
export const retryIndex = (id: string) => request<Project>('/' + id + '/retry-index', 'POST')
export const startProject = (id: string, template: string, revision: number, key: string) => request<ProjectSession>('/' + id + '/sessions', 'POST', { template, expectedRevision: revision, clientRequestId: key })
export const projectHistory = (id: string) => request<ProjectSession[]>('/' + id + '/sessions')
export const getProjectSession = (id: string) => request<ProjectSession>('/sessions/' + id)
export const answerProject = (id: string, body: { answer: string; parentAttemptId: string | null; clientRequestId: string }) => request<Attempt>('/sessions/' + id + '/answers', 'POST', body)
export const retryProject = (id: string, key: string) => request<Attempt>('/attempts/' + id + '/retry', 'POST', { clientRequestId: key })
export const followupProject = (id: string, key: string) => request<ProjectSession>('/attempts/' + id + '/followup', 'POST', { clientRequestId: key })
export const completeProject = (id: string) => request<ProjectSession>('/sessions/' + id + '/complete', 'POST')
export function requestKey(name: string) { let key = sessionStorage.getItem(name); if (!key) { key = newRequestKey(); sessionStorage.setItem(name, key) }; return key }
