export function encodeWav(chunks: Float32Array[], rate: number): Blob {
  const count = chunks.reduce((sum, chunk) => sum + chunk.length, 0)
  const input = new Float32Array(count); let position = 0
  for (const chunk of chunks) { input.set(chunk, position); position += chunk.length }
  const samples = Math.floor(count * 16000 / rate)
  const buffer = new ArrayBuffer(44 + samples * 2), view = new DataView(buffer)
  const tag = (offset: number, value: string) => { [...value].forEach((char, index) => view.setUint8(offset + index, char.charCodeAt(0))) }
  tag(0, 'RIFF'); view.setUint32(4, buffer.byteLength - 8, true); tag(8, 'WAVE'); tag(12, 'fmt ')
  view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true)
  view.setUint32(24, 16000, true); view.setUint32(28, 32000, true); view.setUint16(32, 2, true); view.setUint16(34, 16, true)
  tag(36, 'data'); view.setUint32(40, samples * 2, true)
  for (let index = 0; index < samples; index++) {
    const source = index * rate / 16000, lower = Math.floor(source), fraction = source - lower
    const value = Math.max(-1, Math.min(1, input[lower] * (1 - fraction) + (input[Math.min(lower + 1, count - 1)] || 0) * fraction))
    view.setInt16(44 + index * 2, Math.round(value * (value < 0 ? 32768 : 32767)), true)
  }
  return new Blob([buffer], { type: 'audio/wav' })
}

export class PcmRecorder {
  private context: AudioContext | null = null
  private stream: MediaStream | null = null
  private node: AudioWorkletNode | null = null
  private chunks: Float32Array[] = []
  private ended = false
  private finish: (blob: Blob) => void
  constructor(onFinish: (blob: Blob) => void) { this.finish = onFinish }
  async start(seconds: number) {
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) throw new Error('录音需要 HTTPS 或本机 localhost，请在支持麦克风的浏览器中打开。')
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true } })
      this.context = new AudioContext()
      await this.context.resume(); await this.context.audioWorklet.addModule('/audio-recorder-worklet.js')
      if (this.ended) { this.dispose(); return }
      this.node = new AudioWorkletNode(this.context, 'interview-recorder')
      this.node.port.onmessage = (event: MessageEvent) => {
        if (event.data.type === 'samples') this.chunks.push(event.data.data)
        if (event.data.type === 'ended' && !this.ended) {
          this.ended = true
          const blob = encodeWav(this.chunks, this.context!.sampleRate)
          this.dispose(); this.finish(blob)
        }
      }
      this.node.port.postMessage({ type: 'configure', seconds })
      const mute = this.context.createGain(); mute.gain.value = 0
      this.context.createMediaStreamSource(this.stream).connect(this.node).connect(mute).connect(this.context.destination)
    } catch (error) {
      this.dispose()
      if (error instanceof DOMException && error.name === 'NotAllowedError') throw new Error('麦克风权限被拒绝。可在浏览器设置中允许后重试，也可以继续文字回答。')
      if (error instanceof DOMException && error.name === 'NotFoundError') throw new Error('未找到麦克风，请连接设备后重试。')
      throw new Error('无法开始录音，请检查麦克风是否被占用，以及浏览器是否支持录音。')
    }
  }
  stop() { void this.context?.resume().then(() => this.node?.port.postMessage({ type: 'stop' })).catch(() => this.node?.port.postMessage({ type: 'stop' })) }
  dispose() { this.ended = true; this.stream?.getTracks().forEach(track => track.stop()); this.stream = null; void this.context?.close().catch(() => {}); this.context = null; this.node = null }
}
