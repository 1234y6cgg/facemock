import type { CSSProperties, ReactNode } from 'react'

export type IconName = 'plus' | 'arrow' | 'back' | 'file' | 'upload' | 'check' | 'spark' | 'chat' | 'chart' | 'clock' | 'search' | 'trash' | 'close' | 'menu' | 'external' | 'mic' | 'stop' | 'send' | 'chevron' | 'target' | 'book' | 'alert' | 'settings'
const paths: Record<IconName, ReactNode> = {
  plus: <path d="M12 5v14M5 12h14" />,
  arrow: <path d="M4 12h16m-6-6 6 6-6 6" />,
  back: <path d="M20 12H4m6-6-6 6 6 6" />,
  file: <><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" /><path d="M14 3v6h6M8 13h8M8 17h5" /></>,
  upload: <><path d="M12 16V3m-5 5 5-5 5 5" /><path d="M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4" /></>,
  check: <path d="m5 12 4 4L19 6" />,
  spark: <><path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z" /><path d="M20 2v4m-2-2h4" /></>,
  chat: <path d="M21 11a8 8 0 0 1-8 8H7l-4 3V11a8 8 0 0 1 8-8h2a8 8 0 0 1 8 8ZM7 9h10M7 13h6" />,
  chart: <><path d="M4 3v17h17M8 15v-4m5 4V7m5 8V4" /></>,
  clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
  search: <><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4 4" /></>,
  trash: <path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7" />,
  close: <path d="m6 6 12 12M18 6 6 18" />,
  menu: <path d="M4 6h16M4 12h16M4 18h16" />,
  external: <><path d="M14 3h7v7m0-7L10 14" /><path d="M10 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-5" /></>,
  mic: <><rect x="9" y="2" width="6" height="12" rx="3" /><path d="M5 10a7 7 0 0 0 14 0M12 17v5m-4 0h8" /></>,
  stop: <rect x="6" y="6" width="12" height="12" rx="2" />,
  send: <path d="m5 12 7-7 7 7M12 5v15" />,
  chevron: <path d="m9 5 7 7-7 7" />,
  target: <><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="5" /><circle cx="12" cy="12" r="1" /></>,
  book: <path d="M12 5C9 3 5 3 2 4v15c3-1 7-1 10 1 3-2 7-2 10-1V4c-3-1-7-1-10 1v15" />,
  alert: <><path d="m12 3 10 18H2zM12 9v5" /><path d="M12 17h.01" /></>,
  settings: <><path d="M9.6 2.5h4.8l.6 2.3 1.8 1 2.2-.6 2.4 4.2-1.6 1.7v1.8l1.6 1.7-2.4 4.2-2.2-.6-1.8 1-.6 2.3H9.6L9 19.2l-1.8-1-2.2.6-2.4-4.2 1.6-1.7v-1.8L2.6 9.4 5 5.2l2.2.6 1.8-1z"/><circle cx="12" cy="12" r="3"/></>,
}
export default function Icon({ name, size = 20, style, className }: { name: IconName; size?: number; style?: CSSProperties; className?: string }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className={className} style={style}>{paths[name]}</svg>
}
