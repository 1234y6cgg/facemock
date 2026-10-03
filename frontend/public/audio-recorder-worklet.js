class InterviewRecorder extends AudioWorkletProcessor {
  constructor() {
    super(); this.block = new Float32Array(4096); this.used = 0; this.total = 0; this.limit = sampleRate * 180; this.active = true
    this.port.onmessage = event => {
      if (event.data.type === 'configure') this.limit = sampleRate * event.data.seconds
      if (event.data.type === 'stop') this.finish()
    }
  }
  flush() {
    if (!this.used) return
    const data = this.block.slice(0, this.used); this.port.postMessage({ type: 'samples', data }, [data.buffer]); this.used = 0
  }
  finish() { if (!this.active) return; this.active = false; this.flush(); this.port.postMessage({ type: 'ended' }) }
  process(inputs) {
    if (!this.active) return false
    const input = inputs[0]?.[0]
    if (input) for (const sample of input) {
      if (this.total >= this.limit) { this.finish(); return false }
      this.block[this.used++] = sample; this.total++
      if (this.used === this.block.length) this.flush()
    }
    return true
  }
}
registerProcessor('interview-recorder', InterviewRecorder)
