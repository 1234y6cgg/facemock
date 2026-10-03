import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const source = readFileSync(new URL('../src/audio/PcmRecorder.ts', import.meta.url), 'utf8')
function load(extra = {}) {
  const module = { exports: {} }
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
  const scope = { module, exports: module.exports, Blob, Float32Array, ArrayBuffer, DataView, DOMException, ...extra }
  vm.runInNewContext(code, scope)
  return module.exports
}
test('PCM WAV has trusted duration, mono header and correctly clipped samples', async () => {
  const { encodeWav } = load()
  const blob = encodeWav([new Float32Array([-2, 0, 2]), new Float32Array(15997)], 16000)
  const buffer = await blob.arrayBuffer(), view = new DataView(buffer)
  assert.equal(buffer.byteLength, 32044); assert.equal(view.getUint32(24, true), 16000); assert.equal(view.getUint16(22, true), 1)
  assert.equal(view.getInt16(44, true), -32768); assert.equal(view.getInt16(48, true), 32767); assert.equal(view.getUint32(40, true), 32000)
  assert.equal((await encodeWav([new Float32Array(48000)], 48000).arrayBuffer()).byteLength, 32044)
})
test('microphone denial and insecure origins produce actionable messages', async () => {
  const denied = load({ window: { isSecureContext: true }, navigator: { mediaDevices: { getUserMedia: async () => { throw new DOMException('Denied', 'NotAllowedError') } } } })
  await assert.rejects(new denied.PcmRecorder(() => {}).start(180), /麦克风权限被拒绝/)
  const insecure = load({ window: { isSecureContext: false }, navigator: {} })
  await assert.rejects(new insecure.PcmRecorder(() => {}).start(180), /HTTPS/)
})
test('worklet flushes the last block and ends once at the sample limit', () => {
  let Processor
  class Base { constructor() { this.messages = []; this.port = { postMessage: message => this.messages.push(message) } } }
  vm.runInNewContext(readFileSync(new URL('../public/audio-recorder-worklet.js', import.meta.url), 'utf8'), {
    AudioWorkletProcessor: Base, sampleRate: 16000, Float32Array, registerProcessor: (_name, implementation) => { Processor = implementation },
  })
  const recorder = new Processor(); recorder.port.onmessage({ data: { type: 'configure', seconds: 1 } })
  for (let i = 0; i < 130; i++) recorder.process([[new Float32Array(128).fill(.2)]])
  const samples = recorder.messages.filter(value => value.type === 'samples').reduce((sum, value) => sum + value.data.length, 0)
  assert.equal(samples, 16000); assert.equal(recorder.messages.filter(value => value.type === 'ended').length, 1)
  recorder.port.onmessage({ data: { type: 'stop' } }); assert.equal(recorder.messages.filter(value => value.type === 'ended').length, 1)
})
