import { useEffect, useRef, useState } from 'react'
import { PcmRecorder } from '../audio/PcmRecorder'
import { audioDraft, deleteRecording, listInterviewRecordings, retryTranscription, savedAudio, speechStatus, uploadInterviewRecording, type AudioDraft, type Recording, type SpeechStatus } from '../api/speech'
import { newRequestKey } from '../api/practice'
import Icon from './Icon'

export default function InterviewSpeech({ sessionId, turn, disabled, onSubmit, onTextMode, onExit }: {
  sessionId: number; turn: number; disabled: boolean; onSubmit: (text: string) => Promise<boolean>; onTextMode: () => void; onExit: () => void
}) {
  const scope = `interview:${sessionId}:turn-${turn}`
  const [status, setStatus] = useState<SpeechStatus | null>(null)
  const [active, setActive] = useState<Recording | null>(null), [draft, setDraft] = useState<AudioDraft | null>(null)
  const [recording, setRecording] = useState(false), [seconds, setSeconds] = useState(0), [preview, setPreview] = useState('')
  const [text, setText] = useState(''), [busy, setBusy] = useState(''), [error, setError] = useState(''), [loaded, setLoaded] = useState(false)
  const mounted = useRef(true), lock = useRef(false), recorder = useRef<PcmRecorder | null>(null)
  const latest = active?.jobs[active.jobs.length - 1]
  async function refresh() {
    const items = await listInterviewRecordings(sessionId)
    const current = items.filter(item => item.state === 'ACTIVE' && item.parentAttemptId === `turn-${turn}`)
    const value = current[current.length - 1] || null
    if (!mounted.current) return
    setActive(value)
    const pending = await savedAudio(scope)
    if (pending && items.some(item => item.clientRequestId === pending.key)) {
      await savedAudio(scope, null); if (mounted.current) setDraft(null)
    }
  }
  useEffect(() => {
    mounted.current = true
    void Promise.all([speechStatus(), savedAudio(scope)]).then(([configuration, pending]) => {
      if (mounted.current) { setStatus(configuration); setDraft(pending) }
    }).then(refresh).then(() => { if (mounted.current) setLoaded(true) }).catch(e => {
      if (mounted.current) { setError(e instanceof Error ? e.message : '无法读取语音状态，请刷新页面。'); setLoaded(true) }
    })
    const timer = window.setInterval(() => { void refresh().catch(() => { if (mounted.current) setError('暂时无法读取转写进度，录音已保存在后端，恢复连接后会继续查询。') }) }, 2500)
    return () => { mounted.current = false; window.clearInterval(timer); recorder.current?.dispose() }
  }, [scope])
  useEffect(() => {
    if (!draft) { setPreview(''); return }
    const url = URL.createObjectURL(draft.wav); setPreview(url); return () => URL.revokeObjectURL(url)
  }, [draft])
  useEffect(() => {
    if (active && latest?.status === 'SUCCEEDED') setText(localStorage.getItem('speech-text:' + active.id) ?? latest.originalText ?? '')
  }, [active?.id, latest?.status])
  useEffect(() => {
    if (!recording) return
    const timer = window.setInterval(() => setSeconds(value => value + 1), 1000)
    return () => window.clearInterval(timer)
  }, [recording])
  async function task(name: string, operation: () => Promise<void>) {
    if (lock.current) return
    lock.current = true; setBusy(name); setError('')
    try { await operation() } catch (e) { if (mounted.current) setError(e instanceof Error ? e.message : '操作失败，请重试。') }
    finally { lock.current = false; if (mounted.current) setBusy('') }
  }
  async function retain(wav: Blob) {
    const value = audioDraft(wav, null)
    if (mounted.current) setDraft(value)
    await savedAudio(scope, value)
  }
  async function start() {
    if (!status?.configured) return
    if (draft && !window.confirm('重新录制会替换尚未上传的录音草稿，继续吗？')) return
    await task('start', async () => {
      const capture = new PcmRecorder(wav => {
        if (!mounted.current) return
        setRecording(false); recorder.current = null
        void task('save', () => retain(wav))
      })
      recorder.current = capture
      await capture.start(status.maxSeconds)
      if (mounted.current) { setSeconds(0); setRecording(true) }
    })
  }
  async function upload() {
    if (!draft) return
    await task('upload', async () => {
      const value = await uploadInterviewRecording(sessionId, turn, draft)
      if (mounted.current) { setActive(value); setDraft(null) }
      await savedAudio(scope, null)
    })
  }
  async function submit() {
    if (disabled || !active || latest?.status !== 'SUCCEEDED' || !text.trim()) return
    await task('submit', async () => {
      if (await onSubmit(text)) { localStorage.removeItem('speech-text:' + active.id); await savedAudio(scope, null) }
    })
  }
  const blocked = disabled || !!busy || !loaded
  return <section className="voice-input interview-speech" aria-label="科大讯飞语音回答">
    <div className="composer-heading"><span><Icon name="mic" size={15} />科大讯飞语音回答</span><button className="text-button" disabled={blocked || recording} onClick={onTextMode}>切换为文字</button></div>
    <p className="input-hint">{status?.notice || '正在读取讯飞配置…'}最长 {status?.maxSeconds || 180} 秒，停止后回听并上传转写。</p>
    {error && <p className="error-inline" role="alert">{error}</p>}
    {!active && <>
      <div className="practice-actions"><button className="btn btn-primary" disabled={blocked || !status?.configured} onClick={() => recording ? recorder.current?.stop() : void start()}><Icon name={recording ? 'stop' : 'mic'} size={17} />{recording ? '停止录音' : draft ? '重新录制' : '开始录音'}</button>{recording && <span role="timer">{seconds} 秒 / {status?.maxSeconds} 秒</span>}</div>
      {recording && <p className="input-hint">录制中离开或刷新会中止录音，请先停止保存草稿。</p>}
      {!recording && preview && <div className="speech-preview"><audio controls src={preview} /><button className="btn btn-primary" disabled={blocked || !status?.configured} onClick={() => void upload()}>{busy === 'upload' ? '正在上传…' : '上传录音，讯飞转写'}</button><button className="text-button" disabled={blocked} onClick={() => void task('discard', async () => { await savedAudio(scope, null); setDraft(null) })}>丢弃草稿</button></div>}
      {!recording && <label className="speech-import">或导入录音（16kHz 单声道 WAV）<input aria-label="导入模拟面试 WAV 录音" type="file" accept="audio/wav,.wav" disabled={blocked || !status?.configured} onChange={e => { const file = e.target.files?.[0]; if (file) void task('import', () => retain(file)); e.target.value = '' }} /></label>}
    </>}
    {active && <div className="speech-transcription">
      <audio controls preload="none" src={'/api/speech/recordings/' + active.id + '/audio'} />
      {(latest?.status === 'PENDING' || latest?.status === 'RUNNING') && <p role="status">{latest.status === 'PENDING' ? '录音已保存，等待讯飞转写。' : '讯飞正在转写，长录音会分段处理。'}可刷新页面继续核对。</p>}
      {latest?.status === 'FAILED' && <><p role="alert">讯飞转写失败，录音已保留。请检查配置或稍后重试。</p><button className="btn btn-primary" disabled={blocked || !status?.configured || active.jobs.length >= 3} onClick={() => void task('retry', async () => {
        const name = 'speech-retry:' + active.id
        const key = sessionStorage.getItem(name) || newRequestKey(); sessionStorage.setItem(name, key)
        await retryTranscription(active.id, key); sessionStorage.removeItem(name); await refresh()
      })}>重试讯飞转写</button>{active.jobs.length >= 3 && <p>已达到三次上限，可删除录音后重新录制。</p>}</>}
      {latest?.status === 'SUCCEEDED' && <><details><summary>查看原始转写</summary><p>{latest.originalText}</p></details><label htmlFor="interview-speech-text">核对并修订回答</label><textarea id="interview-speech-text" aria-label="核对模拟面试转写" maxLength={10000} disabled={blocked} value={text} onChange={e => { setText(e.target.value); localStorage.setItem('speech-text:' + active.id, e.target.value) }} /><button className="btn btn-primary" disabled={blocked || !text.trim()} onClick={() => void submit()}>确认文本，提交回答</button></>}
      <button className="text-button" disabled={blocked} onClick={() => { if (window.confirm('删除这段录音和原始转写后重新录制？')) void task('delete', async () => {
        await deleteRecording(active.id); localStorage.removeItem('speech-text:' + active.id); await savedAudio(scope, null); setDraft(null); setActive(null); setText('')
      }) }}>删除录音，重新回答</button>
    </div>}
    <div className="input-hint">原音默认保留 {status?.retentionDays || 30} 天；删除面试时同时删除关联录音。转写不会自动发送。</div>
    <button className="voice-exit-btn" disabled={blocked || recording} onClick={onExit}>结束面试</button>
  </section>
}
