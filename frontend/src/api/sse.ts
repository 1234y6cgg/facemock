export interface SSEHandlers {
  onMessage: (token: string) => void
  onDone: () => void
  onInterviewEnd: () => void
  onLayer?: (layer: string) => void
  onFeedback?: (text: string) => void
}

export async function streamPost(
  url: string,
  body: unknown,
  handlers: SSEHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  })
  if (!res.ok || !res.body) throw new Error('请求失败')

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let doneCalled = false

  const handle = (evt: { event: string; data: string }) => {
    if (evt.event === 'message') handlers.onMessage(evt.data)
    else if (evt.event === 'done') { doneCalled = true; handlers.onDone() }
    else if (evt.event === 'interview_end') { doneCalled = true; handlers.onInterviewEnd() }
    else if (evt.event === 'layer') handlers.onLayer?.(evt.data)
    else if (evt.event === 'feedback') handlers.onFeedback?.(evt.data)
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split(/\r?\n\r?\n/)
      buffer = parts.pop() ?? ''
      for (const part of parts) {
        const evt = parseEvent(part)
        if (evt) handle(evt)
      }
    }
    buffer += decoder.decode()
    const rest = parseEvent(buffer)
    if (rest) handle(rest)
    if (!doneCalled) throw new Error('对话连接提前中断')
  } finally {
    reader.releaseLock()
  }
}

function parseEvent(raw: string): { event: string; data: string } | null {
  if (!raw.trim()) return null
  let event = 'message'
  const dataLines: string[] = []
  for (const line of raw.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
  }
  if (dataLines.length === 0) return null
  return { event, data: dataLines.join('\n') }
}
