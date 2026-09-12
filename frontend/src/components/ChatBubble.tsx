import ReactMarkdown from 'react-markdown'

export default function ChatBubble({ role, content }: { role: string; content: string }) {
  const isInterviewer = role === 'INTERVIEWER'
  return (
    <div className={`bubble ${isInterviewer ? 'interviewer' : 'candidate'}`}>
      <div className="who">{isInterviewer ? '面试官' : '你'}</div>
      <div className="body">
        <ReactMarkdown>{content}</ReactMarkdown>
      </div>
    </div>
  )
}
