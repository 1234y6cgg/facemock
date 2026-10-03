import { useEffect,useState } from 'react'
import { Link,useLocation } from 'react-router-dom'
import { getModelSettings } from '../api/models'
export default function ModelSetupNotice(){const location=useLocation();const [missing,setMissing]=useState(false)
  useEffect(()=>{let cancelled=false;getModelSettings().then(s=>{if(!cancelled)setMissing(!s.configured)}).catch(()=>{});return()=>{cancelled=true}},[location.pathname])
  if(!missing||location.pathname==='/settings/model')return null
  return <div className="model-setup-notice" role="status"><span>开始 AI 练习前，请先配置你自己的模型 Key。</span><Link to="/settings/model">去配置 →</Link></div>
}
