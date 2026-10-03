import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { deleteInterview, listInterviews, type HistoryItem } from '../api/client'
import Icon from './Icon'

export default function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  const location = useLocation()
  const navigate = useNavigate()
  const [history, setHistory] = useState<HistoryItem[]>([])
  const [historyLoading, setHistoryLoading] = useState(true)
  const [historyError, setHistoryError] = useState('')
  const [reload, setReload] = useState(0)
  const [query, setQuery] = useState('')
  const [pendingDelete, setPendingDelete] = useState<HistoryItem | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState('')
  const dialogRef = useRef<HTMLDivElement>(null)
  const sidebarRef = useRef<HTMLElement>(null)
  const activeId = Number(location.pathname.match(/\/(?:interview|report)\/(\d+)/)?.[1])
  useEffect(() => {
    let cancelled = false
    setHistoryLoading(true); setHistoryError('')
    listInterviews().then((items) => { if (!cancelled) setHistory(items) }).catch(() => { if (!cancelled) setHistoryError('暂时无法加载面试记录') }).finally(() => { if (!cancelled) setHistoryLoading(false) })
    return () => { cancelled = true }
  }, [location.pathname, reload])
  useEffect(() => {
    const mobile = window.matchMedia('(max-width: 760px)')
    const update = () => { if (sidebarRef.current) sidebarRef.current.inert = mobile.matches && !open }
    update()
    mobile.addEventListener('change', update)
    return () => mobile.removeEventListener('change', update)
  }, [open])
  useEffect(() => {
    if (!pendingDelete && !open) return
    const previous = document.activeElement as HTMLElement | null
    const container = pendingDelete ? dialogRef.current : sidebarRef.current
    container?.querySelector<HTMLButtonElement>('button')?.focus()
    const keydown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !deleting) { setPendingDelete(null); onClose() }
      if (e.key !== 'Tab') return
      const elements = container?.querySelectorAll<HTMLElement>('button:not(:disabled), a, input')
      if (!elements?.length) return
      const first = elements[0], last = elements[elements.length - 1]
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus() }
      if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', keydown)
    return () => { document.removeEventListener('keydown', keydown); previous?.focus() }
  }, [pendingDelete, open, deleting, onClose])
  async function confirmDelete() {
    if (!pendingDelete || deleting) return
    setDeleting(true); setError('')
    try {
      await deleteInterview(pendingDelete.id)
      setHistory((items) => items.filter((item) => item.id !== pendingDelete.id))
      if (activeId === pendingDelete.id) navigate('/')
      setPendingDelete(null)
    } catch (e) { setError(e instanceof Error ? e.message : '删除失败，请重试') }
    finally { setDeleting(false) }
  }
  const visible = history.filter((item) => `${item.resumeFilename ?? ''} 面试${item.id}`.toLowerCase().includes(query.toLowerCase()))
  return <>
    {open && <button className="sidebar-scrim" aria-label="关闭导航" onClick={onClose} />}
    <aside ref={sidebarRef} className={`sidebar ${open ? 'is-open' : ''}`} aria-label="主导航">
      <div className="sidebar-content">
      <div className="brand-row"><Link to="/" className="brand" onClick={onClose}><span className="brand-mark">f<span>·</span></span><span>Face<span className="brand-light">Mock</span></span></Link><button className="icon-btn mobile-close" aria-label="关闭导航" onClick={onClose}><Icon name="close" /></button></div>
      <div className="workspace-label"><span className="status-dot" />你的面试练习空间</div>
      <Link to="/" onClick={onClose} className="new-interview"><Icon name="plus" size={18} />今日练习<Icon name="arrow" size={17} /></Link>
      <nav>
        <Link to="/review" onClick={onClose} className={`nav-link ${location.pathname === '/review' ? 'active' : ''}`}><Icon name="target" size={19} />薄弱点与复习</Link>
        <Link to="/progress" onClick={onClose} className={`nav-link ${location.pathname === '/progress' ? 'active' : ''}`}><Icon name="spark" size={19} />进步记录</Link>
        <Link to="/practice/questions" onClick={onClose} className={`nav-link ${location.pathname.startsWith('/practice/questions') || location.pathname.startsWith('/practice/sessions') ? 'active' : ''}`}><Icon name="book" size={19} />八股题库<span className="nav-number">01</span></Link>
        <Link to="/practice/history" onClick={onClose} className={`nav-link ${location.pathname.startsWith('/practice/history') ? 'active' : ''}`}><Icon name="clock" size={19} />我的练习<span className="nav-number">02</span></Link>
        <Link to="/projects" onClick={onClose} className={`nav-link ${location.pathname.startsWith('/projects') ? 'active' : ''}`}><Icon name="file" size={19} />项目话术<span className="nav-number">03</span></Link>
        <Link to="/mock" onClick={onClose} className={`nav-link ${location.pathname === '/mock' ? 'active' : ''}`}><Icon name="target" size={19} />完整模拟<span className="nav-number">04</span></Link>
      </nav>
      <div className="sidebar-section"><span>面试记录</span><span className="count-badge">{history.length}</span></div>
      {history.length > 0 && <label className="history-search"><Icon name="search" size={15} /><input aria-label="搜索面试记录" placeholder="搜索简历或记录…" value={query} onChange={(e) => setQuery(e.target.value)} /></label>}
      <div className="sidebar-history">
        {historyLoading && history.length === 0 ? <div className="history-empty" role="status"><p>正在读取面试记录…</p></div> : historyError ? <div className="history-empty" role="status"><Icon name="clock" size={23} /><p>{historyError}</p><button className="sidebar-retry" onClick={() => setReload((n) => n + 1)}>重新加载</button></div> : visible.length === 0 && <div className="history-empty"><Icon name="chat" size={24} /><p>{query ? '没有找到相关记录' : '你的下一次进步，从这里开始。'}</p><span>{query ? '试试其他关键词' : '完成面试后，记录会保存在这里'}</span></div>}
        {visible.map((item) => <div key={item.id} className={`history-item ${activeId === item.id ? 'active' : ''}`}><Link to={`/interview/${item.id}`} onClick={onClose}><div className="history-title"><span className={`record-dot ${item.status === 'COMPLETED' ? 'complete' : ''}`} />{item.resumeFilename?.replace(/\.(pdf|docx?|png|jpe?g|webp)$/i, '') || `面试 ${item.id}`}</div><div className="history-meta">{Number.isNaN(Date.parse(item.startedAt)) ? '面试记录' : new Date(item.startedAt).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })}<span>{item.status === 'COMPLETED' ? '已完成' : '进行中'}</span></div></Link><button className="history-delete" title="删除记录" aria-label={`删除面试 ${item.id}`} onClick={() => { setError(''); setPendingDelete(item) }}><Icon name="trash" size={15} /></button></div>)}
      </div>
      </div>
      <div className="sidebar-footer"><Link to="/settings/model" onClick={onClose} className={`sidebar-settings ${location.pathname === '/settings/model' ? 'active' : ''}`} aria-label="设置：选择模型与配置 API Key" aria-current={location.pathname === '/settings/model' ? 'page' : undefined} title="模型设置"><span className="settings-symbol"><Icon name="settings" size={21} /></span><span><strong>设置</strong><small>选择模型 · 配置 API Key</small></span><Icon name="chevron" size={15} /></Link></div>
    </aside>
    {pendingDelete && <div className="modal-overlay" onClick={() => !deleting && setPendingDelete(null)}><div ref={dialogRef} className="modal-card" role="dialog" aria-modal="true" aria-labelledby="delete-title" aria-describedby="delete-description" onClick={(e) => e.stopPropagation()}><div className="state-icon danger"><Icon name="trash" size={24} /></div><h2 id="delete-title">删除这场面试？</h2><p id="delete-description">「{pendingDelete.resumeFilename || `面试 ${pendingDelete.id}`}」的对话和报告会被删除。未被其他面试使用的简历也会一并删除，此操作无法撤销。</p>{error && <p className="error-inline" role="alert">{error}</p>}<div className="modal-actions"><button className="btn btn-ghost" disabled={deleting} onClick={() => setPendingDelete(null)}>保留记录</button><button className="btn btn-danger" disabled={deleting} onClick={confirmDelete}>{deleting ? '正在删除…' : '确认删除'}</button></div></div></div>}
  </>
}
