import ReactMarkdown from 'react-markdown'
import Icon from './Icon'

export default function ChatBubble({ role, content, layer, streaming }: {
  role: string
  content: string
  layer?: string | null
  streaming?: boolean
}) {
  const isInterviewer = role === 'INTERVIEWER'
  return (
    <div className={`msg ${isInterviewer ? 'interviewer' : 'candidate'}`}>
      <div className="avatar">{isInterviewer ? <Icon name="spark" size={18} /> : '我'}</div>
      <div className="message-content">
        <div className="message-label"><strong>{isInterviewer ? '面试官' : '你的回答'}</strong>{isInterviewer && layer && <span>{layer}</span>}{streaming && <span>正在提问</span>}</div>
        <div className={`body ${streaming ? 'typing-cursor' : ''}`}>
          <ReactMarkdown>{content}</ReactMarkdown>
        </div>
      </div>
    </div>
  )
}
