import { useEffect, useRef, useState } from 'react'
import { PcmRecorder } from '../audio/PcmRecorder'
import { audioDraft, confirmTranscription, deleteRecording, listRecordings, retryTranscription, savedAudio, speechStatus, uploadRecording, type AudioDraft, type Recording, type SpeechStatus } from '../api/speech'
import { getPractice, newRequestKey, PracticeApiError, type Attempt } from '../api/practice'
import Icon from './Icon'

function persistentKey(name: string) { let value = sessionStorage.getItem(name); if (!value) { value = newRequestKey(); sessionStorage.setItem(name, value) } return value }
export default function SpeechPractice({ sessionId, parentAttemptId, canRecord, currentAttemptId, onSubmitted }: { sessionId: string; parentAttemptId: string | null; canRecord: boolean; currentAttemptId?: string; onSubmitted: (attempt: Attempt) => void }) {
  const [status, setStatus] = useState<SpeechStatus | null>(null), [records, setRecords] = useState<Recording[]>([]), [draft, setDraft] = useState<AudioDraft | null>(null)
  const [error, setError] = useState(''), [busy, setBusy] = useState(''), [recording, setRecording] = useState(false), [seconds, setSeconds] = useState(0), [preview, setPreview] = useState('')
  const [text, setText] = useState(''), [confirming, setConfirming] = useState<{ text: string; key: string } | null>(null)
  const recorder = useRef<PcmRecorder | null>(null), mounted = useRef(true), lock = useRef(false)
  const unconfirmed = records.filter(r => r.state === 'ACTIVE' && !r.attemptId)
  const active = unconfirmed[unconfirmed.length - 1]
  const playback = records.find(r => r.attemptId === currentAttemptId)
  const latest = active?.jobs[active.jobs.length - 1]
  async function refresh() {
    const items = await listRecordings(sessionId); if (!mounted.current) return; setRecords(items)
    const pendingAudio = await savedAudio(sessionId).catch(() => null)
    if (pendingAudio && items.some(item => item.clientRequestId === pendingAudio.key)) { await savedAudio(sessionId, null); if (mounted.current) setDraft(null) }
    for (const item of items) if (item.attemptId && localStorage.getItem('speech-confirm:' + item.id)) {
      const session = await getPractice(sessionId), attempt = session.attempts.find(value => value.id === item.attemptId)
      if (attempt && mounted.current) { localStorage.removeItem('speech-confirm:' + item.id); localStorage.removeItem('speech-text:' + item.id); setConfirming(null); onSubmitted(attempt) }
    }
  }
  useEffect(() => {
    mounted.current = true
    speechStatus().then(value => { if (mounted.current) setStatus(value) }).catch(() => { if (mounted.current) setError('无法读取语音配置，文字回答仍可使用。') })
    void refresh().catch(() => {})
    savedAudio(sessionId).then(value => { if (mounted.current) setDraft(value) }).catch(e => { if (mounted.current) setError(e.message) })
    const timer = window.setInterval(() => { void refresh().catch(() => {}) }, 2500)
    return () => { mounted.current = false; window.clearInterval(timer); recorder.current?.dispose() }
  }, [sessionId])
  useEffect(() => { if (!draft) { setPreview(''); return }; const url = URL.createObjectURL(draft.wav); setPreview(url); return () => URL.revokeObjectURL(url) }, [draft])
  useEffect(() => {
    if (!active || latest?.status !== 'SUCCEEDED') return
    const storage = 'speech-text:' + active.id
    setText(localStorage.getItem(storage) ?? latest.originalText ?? '')
    try { const queued = JSON.parse(localStorage.getItem('speech-confirm:' + active.id) || 'null'); setConfirming(queued) } catch { setConfirming(null) }
  }, [active?.id, latest?.status])
  useEffect(() => { if (!recording) return; const timer = window.setInterval(() => setSeconds(n => n + 1), 1000); return () => window.clearInterval(timer) }, [recording])
  useEffect(() => { if (recording && seconds >= (status?.maxSeconds || 180)) recorder.current?.stop() }, [recording, seconds, status?.maxSeconds])
  async function task(name: string, operation: () => Promise<void>) {
    if (lock.current) return; lock.current = true; setBusy(name); setError('')
    try { await operation() } catch (e) { if (mounted.current) setError(e instanceof Error ? e.message : '操作未完成，请重试。') }
    finally { lock.current = false; if (mounted.current) setBusy('') }
  }
  async function retain(wav: Blob) {
    const value = audioDraft(wav, parentAttemptId); setDraft(value)
    await savedAudio(sessionId, value)
  }
  async function start() {
    if (!status?.configured) return
    if (draft && !window.confirm('重新录制会替换本浏览器尚未上传的录音草稿。继续吗？')) return
    await task('start', async () => {
      const capture = new PcmRecorder(blob => { if (!mounted.current) return; setRecording(false); void retain(blob).catch(e => setError(e.message)); recorder.current = null })
      recorder.current = capture; await capture.start(status.maxSeconds); if (mounted.current) { setSeconds(0); setRecording(true) }
    })
  }
  async function upload() {
    if (!draft) return
    await task('upload', async () => { const value = await uploadRecording(sessionId, draft); setRecords(items => [...items.filter(r => r.id !== value.id), value]); setDraft(null); await savedAudio(sessionId, null) })
  }
  async function confirm() {
    if (!active || !text.trim()) return
    await task('confirm', async () => {
      const body = confirming || { text, key: newRequestKey() }; setConfirming(body); localStorage.setItem('speech-confirm:' + active.id, JSON.stringify(body))
      let attempt: Attempt
      try { attempt = await confirmTranscription(active.id, body.text, body.key) }
      catch (error) {
        if (error instanceof PracticeApiError && error.status >= 400 && error.status < 500) {
          localStorage.removeItem('speech-confirm:' + active.id); setConfirming(null)
        }
        throw error
      }
      localStorage.removeItem('speech-confirm:' + active.id); localStorage.removeItem('speech-text:' + active.id); setConfirming(null); await refresh(); onSubmitted(attempt)
    })
  }
  const audioCard = playback?.state === 'ACTIVE' ? <section className="speech-playback"><h3>回听这次回答</h3><audio controls preload="none" src={'/api/speech/recordings/' + playback.id + '/audio'} /><p>录音到期日：{new Date(playback.expiresAt).toLocaleDateString('zh-CN')}</p><button className="text-button" disabled={!!busy} onClick={() => { if (window.confirm('删除原音及原始转写？已确认的文字回答和评估会保留。')) void task('delete', async () => { await deleteRecording(playback.id); await refresh() }) }}>删除这段录音</button></section> : null
  if (!canRecord && !active && !audioCard) return null
  return <section className="speech-panel">
    <div className="practice-section-heading"><h2><Icon name="mic" size={19} />开口讲一遍</h2><span>最长 {status?.maxSeconds || 180} 秒</span></div>
    {error && <p className="error-inline" role="alert">{error}</p>}
    {audioCard}
    {canRecord && !active && <>
      <p>{status?.notice || '正在读取语音配置…'}</p><p className="speech-policy">原音默认保留 {status?.retentionDays || 30} 天，可主动删除。停止后先回听，再上传转写。</p>
      {recording && <p>录制中离开或刷新页面会中止录音，请先停止以保存草稿。</p>}
      <div className="practice-actions"><button className="btn btn-primary" disabled={!!busy || !status?.configured} onClick={() => recording ? recorder.current?.stop() : void start()}><Icon name={recording ? 'stop' : 'mic'} size={17} />{recording ? '停止录音' : draft ? '重新录制' : '开始录音'}</button>{recording && <span role="timer">{seconds} 秒 / {status?.maxSeconds} 秒</span>}</div>
      {!recording && preview && <div className="speech-preview"><audio controls src={preview} /><button className="btn btn-primary" disabled={!!busy || !status?.configured} onClick={() => void upload()}>{busy === 'upload' ? '正在保存…' : '上传录音，开始转写'}</button></div>}
      {!recording && <label className="speech-import">或导入已有录音（16kHz 单声道 WAV）<input aria-label="导入 WAV 录音" type="file" accept="audio/wav,.wav" disabled={!!busy || !status?.configured} onChange={e => { const file = e.target.files?.[0]; if (file) void task('import', () => retain(file)); e.target.value = '' }} /></label>}
    </>}
    {active && <div className="speech-transcription"><audio controls preload="none" src={'/api/speech/recordings/' + active.id + '/audio'} /><p>{Math.round(active.durationMs / 1000)} 秒 · 原音保留至 {new Date(active.expiresAt).toLocaleDateString('zh-CN')}</p>
      {(latest?.status === 'PENDING' || latest?.status === 'RUNNING') && <p role="status">{latest.status === 'PENDING' ? '录音已保存，等待转写。' : '正在识别语音，长录音需要分段处理。'}可以离开页面，回来继续核对。</p>}
      {latest?.status === 'FAILED' && <><p role="alert">转写未完成，原音已保留。请检查语音服务配置或稍后重试。</p><button className="btn btn-primary" disabled={!!busy || active.jobs.length >= 3} onClick={() => void task('retry', async () => { const name = 'speech-retry:' + active.id; await retryTranscription(active.id, persistentKey(name)); sessionStorage.removeItem(name); await refresh() })}>重试这段录音的转写</button>{active.jobs.length >= 3 && <p>已达到三次上限，可删除此录音后重新练习。</p>}</>}
      {latest?.status === 'SUCCEEDED' && <><details><summary>查看原始转写（保留不改）</summary><p>{latest.originalText}</p></details><label htmlFor="speech-confirmed-text">核对并修订转写文本</label><textarea id="speech-confirmed-text" disabled={!!busy || !!confirming} maxLength={10000} value={confirming?.text ?? text} onChange={e => { setText(e.target.value); localStorage.setItem('speech-text:' + active.id, e.target.value) }} /><p>只有下面确认的文本参与知识评估；修订不会更改原音或原始转写。</p><button className="btn btn-primary" disabled={!!busy || !text.trim() || !canRecord} onClick={() => void confirm()}>{confirming ? '重试同一文本的确认' : '确认文本，提交评估'}</button></>}
      <button className="text-button" disabled={!!busy} onClick={() => { if (window.confirm('删除这段录音和原始转写？此操作不能撤销。')) void task('delete', async () => { await deleteRecording(active.id); localStorage.removeItem('speech-text:' + active.id); localStorage.removeItem('speech-confirm:' + active.id); setConfirming(null); await refresh() }) }}>删除录音，重新练习</button>
    </div>}
  </section>
}
