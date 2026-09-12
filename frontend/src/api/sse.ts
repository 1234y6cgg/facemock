export interface SSEHandlers {
  onMessage: (token: string) => void
  onDone: () => void
  onInterviewEnd: () => void
}

export async function streamPost(url: string, body: unknown, handlers: SSEHandlers): Promise<void> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok || !res.body) throw new Error('请求失败')

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop() ?? ''
    for (const part of parts) {
      const evt = parseEvent(part)
      if (!evt) continue
      if (evt.event === 'message') handlers.onMessage(evt.data)
      else if (evt.event === 'done') handlers.onDone()
      else if (evt.event === 'interview_end') handlers.onInterviewEnd()
    }
  }
}

function parseEvent(raw: string): { event: string; data: string } | null {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (dataLines.length === 0) return null
  return { event, data: dataLines.join('\n') }
}
