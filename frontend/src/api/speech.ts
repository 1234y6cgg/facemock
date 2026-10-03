import { newRequestKey, PracticeApiError, type Attempt } from './practice'
export interface SpeechStatus { configured: boolean; provider: string; maxSeconds: number; retentionDays: number; notice: string }
export interface Recording { id: string; sessionId: string; clientRequestId: string; parentAttemptId: string | null; state: string; durationMs: number; byteSize: number; expiresAt: string; attemptId: string | null; jobs: { id: string; generation: number; status: string; originalText: string | null; errorCode: string | null }[] }
export interface AudioDraft { wav: Blob; key: string; parentAttemptId: string | null }
async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch('/api' + url, options)
  if (!response.ok) { const error = await response.json().catch(() => ({})); throw new PracticeApiError(error.message || '录音操作失败，请重试。', response.status) }
  return response.status === 204 ? undefined as T : response.json()
}
export const speechStatus = () => request<SpeechStatus>('/speech/status')
export const listInterviewRecordings = (sessionId: number) => request<Recording[]>('/interviews/' + sessionId + '/speech/recordings')
export const uploadInterviewRecording = (sessionId: number, turn: number, draft: AudioDraft) => {
  const body = new FormData(); body.append('turn', String(turn)); body.append('clientRequestId', draft.key)
  body.append('file', draft.wav, 'recording.wav')
  return request<Recording>('/interviews/' + sessionId + '/speech/recordings', { method: 'POST', body })
}
export const listRecordings = (sessionId: string) => request<Recording[]>('/speech/recordings?' + new URLSearchParams({ sessionId }))
export const uploadRecording = (sessionId: string, draft: AudioDraft) => {
  const body = new FormData(); body.append('sessionId', sessionId); body.append('clientRequestId', draft.key)
  if (draft.parentAttemptId) body.append('parentAttemptId', draft.parentAttemptId)
  body.append('file', draft.wav, 'recording.wav')
  return request<Recording>('/speech/recordings', { method: 'POST', body })
}
export const retryTranscription = (id: string, key: string) => request<Recording>('/speech/recordings/' + id + '/retry', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ clientRequestId: key }) })
export const confirmTranscription = (id: string, text: string, key: string) => request<Attempt>('/speech/recordings/' + id + '/confirm', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text, clientRequestId: key }) })
export const deleteRecording = (id: string) => request<void>('/speech/recordings/' + id, { method: 'DELETE' })
export const deletePractice = (id: string) => request<{ sessionIds: string[]; recordingIds: string[] }>('/practice/sessions/' + id, { method: 'DELETE' })
export function audioDraft(wav: Blob, parentAttemptId: string | null): AudioDraft { return { wav, parentAttemptId, key: newRequestKey() } }
export async function savedAudio(sessionId: string, value?: AudioDraft | null): Promise<AudioDraft | null> {
  return new Promise((resolve, reject) => {
    const opening = indexedDB.open('interview-audio-drafts', 1)
    opening.onupgradeneeded = () => opening.result.createObjectStore('drafts')
    opening.onerror = () => reject(new Error('浏览器无法保存录音草稿，请保持页面打开后重试上传。'))
    opening.onsuccess = () => {
      const db = opening.result, transaction = db.transaction('drafts', value === undefined ? 'readonly' : 'readwrite'), store = transaction.objectStore('drafts')
      const operation = value === undefined ? store.get(sessionId) : value === null ? store.delete(sessionId) : store.put(value, sessionId)
      transaction.oncomplete = () => { resolve(value === undefined ? operation.result || null : value); db.close() }
      transaction.onerror = () => { reject(new Error('无法保存本浏览器录音草稿，请保持页面打开。')); db.close() }
    }
  })
}
