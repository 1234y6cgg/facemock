import type { ReactNode } from 'react'
import Icon from './Icon'
export function PageHeading({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: ReactNode }) {
  return (
    <header className="page-heading">
      <div>
        <div className="eyebrow">{eyebrow}</div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action && <div className="heading-actions">{action}</div>}
    </header>
  )
}
export function LoadingState({ title, description = '请稍等，正在整理你的内容。' }: { title: string; description?: string }) {
  return (
    <div className="state-card" role="status">
      <div className="spinner" />
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  )
}
export function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="state-card" role="alert">
      <div className="state-icon"><Icon name="alert" size={26} /></div>
      <h2>暂时没能加载内容</h2>
      <p>{message}</p>
      <button className="btn btn-primary" onClick={onRetry}>重新加载<Icon name="arrow" size={16} /></button>
    </div>
  )
}
