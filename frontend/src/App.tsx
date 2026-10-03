import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import UploadPage from './pages/UploadPage'
import InterviewPage from './pages/InterviewPage'
import ReportPage from './pages/ReportPage'
import ResumePage from './pages/ResumePage'
import QuestionLibraryPage from './pages/QuestionLibraryPage'
import PracticeSessionPage from './pages/PracticeSessionPage'
import PracticeHistoryPage from './pages/PracticeHistoryPage'
import ProjectLibraryPage from './pages/ProjectLibraryPage'
import ProjectWorkspacePage from './pages/ProjectWorkspacePage'
import ProjectPracticePage from './pages/ProjectPracticePage'
import ReviewPage from './pages/ReviewPage'
import ModelSettingsPage from './pages/ModelSettingsPage'
import ModelSetupNotice from './components/ModelSetupNotice'
import './models.css'
import './review.css'
import './practice.css'
import './projects.css'
import Sidebar from './components/Sidebar'
import { useCallback, useState } from 'react'
import Icon from './components/Icon'

function Routed() {
  const location = useLocation()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const closeSidebar = useCallback(() => setSidebarOpen(false), [])
  const pageName = location.pathname === '/settings/model' ? '模型设置' : location.pathname === '/' ? '今日练习' : location.pathname === '/review' ? '薄弱点与复习' : location.pathname === '/progress' ? '进步记录' : location.pathname.startsWith('/projects') ? '项目话术'
    : location.pathname.startsWith('/practice/questions') ? '八股题库'
    : location.pathname.startsWith('/practice/history') ? '我的练习'
    : location.pathname.startsWith('/practice/sessions') ? '单题训练'
    : location.pathname.includes('/resume')
    ? '简历档案'
    : location.pathname.startsWith('/report')
      ? '面试复盘'
      : location.pathname.startsWith('/interview')
        ? '面试进行室'
        : '面试准备'
  return (
    <div className="app-layout">
      <Sidebar open={sidebarOpen} onClose={closeSidebar} />
      <main className="main-area">
        <div className="workspace-topbar">
          <div className="breadcrumb">
            <button className="icon-btn mobile-menu" onClick={() => setSidebarOpen(true)} aria-label="打开导航" aria-expanded={sidebarOpen}>
              <Icon name="menu" />
            </button>
            <span className="breadcrumb-root">我的工作台</span>
            <Icon name="chevron" size={13} />
            <span>{pageName}</span>
          </div>
          <div className="workspace-note"><span className="status-dot" />练习，是最好的准备</div>
        </div>
        <ModelSetupNotice />
        <Routes location={location}>
          <Route path="/settings/model" element={<ModelSettingsPage />} />
          <Route path="/" element={<ReviewPage mode="today" />} />
          <Route path="/review" element={<ReviewPage mode="review" />} />
          <Route path="/progress" element={<ReviewPage mode="progress" />} />
          <Route path="/mock" element={<UploadPage />} />
          <Route path="/practice/questions" element={<QuestionLibraryPage />} />
          <Route path="/practice/history" element={<PracticeHistoryPage />} />
          <Route path="/projects" element={<ProjectLibraryPage />} />
          <Route path="/projects/:id" element={<ProjectWorkspacePage key={location.pathname} />} />
          <Route path="/projects/practice/:id" element={<ProjectPracticePage key={location.pathname} />} />
          <Route path="/practice/sessions/:id" element={<PracticeSessionPage key={location.pathname} />} />
          <Route path="/interview/:id" element={<InterviewPage key={location.pathname} />} />
          <Route path="/interview/:id/resume" element={<ResumePage key={location.pathname} />} />
          <Route path="/report/:id" element={<ReportPage key={location.pathname} />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Routed />
    </BrowserRouter>
  )
}
