export const topics: Record<string, string> = {
  JAVA_BASICS: 'Java 基础', JAVA_COLLECTIONS: 'Java 集合', JAVA_CONCURRENCY: 'Java 并发',
  JAVA_IO: 'Java IO / NIO', JVM: 'JVM', SPRING: 'Spring / Spring Boot', MYBATIS: 'MyBatis',
  MYSQL: 'MySQL', REDIS: 'Redis', KAFKA: 'Kafka', MESSAGE_QUEUE: '消息队列',
  NETWORK: '计算机网络', OS: '操作系统', DATA_STRUCTURES: '数据结构与算法',
  DISTRIBUTED: '分布式与微服务', SYSTEM_DESIGN: '系统设计', SECURITY: '应用安全', DEVOPS: 'Linux / Git / Docker'
}
export const labels: Record<string, string> = { COVERED: '已讲清楚', PARTIAL: '还需补充', MISSING: '尚未提到', INCORRECT: '需要纠正', UNCERTAIN: '无法判断' }
export const changes: Record<string, string> = { CORRECTED: '错误已纠正', NEWLY_COVERED: '新补齐', REGRESSED: '本次遗漏或出错', MAINTAINED: '保持正确', UNRESOLVED: '仍需练习', UNCERTAIN: '无法比较' }
export interface Question { id: string; version: number; topic: string; difficulty: number; title: string; prompt: string; suggestedSeconds: number }
export interface QuestionPage { items: Question[]; page: number; size: number; totalElements: number; totalPages: number }
export interface CriterionAssessment { criterionId: string; status: string; candidateQuotes: string[]; reason: string; references: { sourceId: string; documentRevision: string; quote: string }[] }
export interface EvaluationResult { questionId: string; questionVersion: number; rubricVersion: number; criteria: CriterionAssessment[]; expressionFeedback: { quote: string; issue: string; suggestion: string }[] }
export interface Source { sourceId: string; title: string; url: string; content: string; documentRevision: string }
export interface Evaluation { id: string; generation: number; status: string; errorCode: string | null; errorMessage: string | null; modelName: string | null; promptVersion: string | null; durationMs: number | null; referenceState: string | null; referenceNotice: string | null; result: EvaluationResult | null; sources: Source[]; criterionLabels: Record<string, string> }
export interface Attempt { id: string; sessionId: string; clientRequestId: string; attemptNumber: number; answer: string; parentAttemptId: string | null; inputMode: string; referenceViewed: boolean; hintsUsed: boolean; createdAt: string; evaluation: Evaluation; evaluations: Evaluation[]; comparison: { comparable: boolean; notice: string; criteria: { criterionId: string; label: string; before: string; after: string; change: string }[] } | null; oralFeedback: { durationMs: number; characterCount: number; suggestions: string[]; observations: { word: string; start: number; end: number }[] } | null }
export interface Session { id: string; kind: string; originAttemptId: string | null; originSessionId: string | null; status: string; question: Question; referenceViewed: boolean; hintsUsed: boolean; createdAt: string; endedAt: string | null; attempts: Attempt[]; reviewPointId: string | null; reviewDueDate: string | null }
export interface Reference { explanation: string; criteria: { id: string; knowledgePointId: string; expected: string; acceptedExpressions: string[]; commonMistakes: string[] }[]; sources: Source[] }
export interface HistoryPage { items: { id: string; title: string; kind: string; status: string; attemptCount: number; evaluationStatus: string | null; createdAt: string }[]; page: number; totalPages: number; totalElements: number }
export interface AnswerPayload { answer: string; parentAttemptId: string | null; inputMode: 'TEXT'; clientRequestId: string }
export class PracticeApiError extends Error { constructor(message: string, public status: number) { super(message) } }
async function request<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch('/api' + path, body === undefined ? undefined : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new PracticeApiError(error.message || '暂时无法完成请求，请重试。', response.status)
  }
  return response.json()
}
export const listQuestions = (topic: string, difficulty: string, page: number, q = '') => request<QuestionPage>('/questions?' + new URLSearchParams({ ...(topic && { topic }), ...(difficulty && { difficulty }), ...(q && { q }), page: String(page), size: '12' }))
export interface CatalogSummary { totalQuestions: number; topics: { topic: string; count: number }[] }
export const getQuestionSummary = () => request<CatalogSummary>('/questions/topics')
export const createPractice = (question: Question, key: string) => request<Session>('/practice/sessions', { questionId: question.id, questionVersion: question.version, clientRequestId: key })
export const getPractice = (id: string) => request<Session>('/practice/sessions/' + id)
export const submitPractice = (id: string, body: AnswerPayload) => request<Attempt>('/practice/sessions/' + id + '/attempts', body)
export const retryEvaluation = (id: string, key: string) => request<Attempt>('/practice/attempts/' + id + '/retry-evaluation', { clientRequestId: key })
export const getReference = (id: string, assisted = false) => request<Reference>('/practice/sessions/' + id + '/reference?assisted=' + assisted)
export const getHint = (id: string) => request<{ hint: string }>('/practice/sessions/' + id + '/hints', {})
export const createFollowup = (id: string, key: string) => request<Session>('/practice/attempts/' + id + '/followups', { clientRequestId: key })
export const completePractice = (id: string) => request<Session>('/practice/sessions/' + id + '/complete', {})
export const practiceHistory = (page: number) => request<HistoryPage>('/practice/sessions?page=' + page)
export function newRequestKey() { return crypto.randomUUID() }

