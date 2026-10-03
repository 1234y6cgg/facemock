import { useEffect, useState } from 'react'
import { getModelSettings,getModelPresets,saveModelSettings,checkModelSettings,clearModelKey,type ModelSettings,type ModelPreset,type ModelUpdate,type ModelCheck } from '../api/models'
import { PageHeading,LoadingState,ErrorState } from '../components/Ui'
import Icon from '../components/Icon'
import '../models.css'

const abbreviations:Record<string,string>={deepseek:'DS',qwen:'Q',doubao:'豆',glm:'GLM',kimi:'K',minimax:'M',custom:'+'}
export default function ModelSettingsPage(){
  const [saved,setSaved]=useState<ModelSettings|null>(null),[presets,setPresets]=useState<ModelPreset[]>([]),[form,setForm]=useState<ModelUpdate|null>(null)
  const [error,setError]=useState(''),[message,setMessage]=useState(''),[check,setCheck]=useState<ModelCheck|null>(null),[busy,setBusy]=useState(''),[reload,setReload]=useState(0),[visible,setVisible]=useState(false)
  const [clearing,setClearing]=useState(false)
  function populate(value:ModelSettings){setSaved(value);setForm({providerId:value.providerId,baseUrl:value.baseUrl,modelName:value.modelName,jsonMode:value.jsonMode,apiKey:'',revision:value.revision});setVisible(false)}
  useEffect(()=>{let cancelled=false;setError('');Promise.all([getModelSettings(),getModelPresets()]).then(([settings,items])=>{if(!cancelled){populate(settings);setPresets(items)}}).catch(e=>{if(!cancelled)setError(e.message)});return()=>{cancelled=true}},[reload])
  function edit(values:Partial<ModelUpdate>){if(form){setForm({...form,...values});setCheck(null);setMessage('');setClearing(false)}}
  async function action(name:string,operation:()=>Promise<void>){if(busy)return;setBusy(name);setError('');setMessage('');try{await operation()}catch(e){setError(e instanceof Error?e.message:'操作失败，请重试。')}finally{setBusy('')}}
  if(!form||!saved)return <div className="page-scroll"><div className="page-content">{error?<ErrorState message={error} onRetry={()=>setReload(n=>n+1)}/>:<LoadingState title="正在读取模型设置"/>}</div></div>
  const selected=presets.find(p=>p.id===form.providerId)
  const canKeep=saved.configured&&form.providerId===saved.providerId&&form.baseUrl.replace(/\/+$/,'')===saved.baseUrl
  return <div className="page-scroll"><div className="page-content model-settings-page">
    <PageHeading eyebrow="YOUR MODEL, YOUR CHOICE" title="用自己的模型，开启每一次练习。" description="选择熟悉的服务，填入你自己的 API Key。简历解析、面试对话和训练反馈都会使用保存后的配置。"/>
    <section className={'model-current '+(saved.configured?'is-ready':'')} aria-label="当前模型配置"><div className="model-current-icon"><Icon name="spark" size={25}/></div><div><span>{saved.configured?'当前已配置':'还差一步，配置你的模型'}</span><strong>{presets.find(p=>p.id===saved.providerId)?.name||'自定义服务'} · {saved.modelName}</strong><p>{saved.configured?(saved.source==='environment'?'当前使用已有环境配置；在下方保存即可改为自己的配置。':'Key 已加密保存在本机，页面不会回显。'):'题库和历史可浏览；需要模型的解析与反馈将在配置后可用。'}</p></div><span className={'pill '+(saved.configured?'neutral':'')}>{saved.configured?'已配置':'待配置'}</span></section>
    <div className="model-section-heading"><h2>01 / 选择模型服务</h2><p>预设地址可修改，模型名称以你的平台权限为准。</p></div>
    <div className="model-provider-grid" role="group" aria-label="模型服务商">{presets.map(p=><button key={p.id} type="button" disabled={!!busy} aria-pressed={form.providerId===p.id} className={'model-provider '+(form.providerId===p.id?'is-selected':'')} onClick={()=>edit({providerId:p.id,baseUrl:p.baseUrl,modelName:p.models[0]||'',jsonMode:p.jsonMode,apiKey:''})}><span className={'model-provider-mark '+p.id}>{abbreviations[p.id]}</span><span><strong>{p.name}</strong><small>{p.id==='custom'?'兼容服务与本地模型':'使用自己的开放平台 Key'}</small></span>{form.providerId===p.id&&<Icon name="check" size={17}/>}</button>)}</div>
    <section className="model-form-card"><div className="model-section-heading"><h2>02 / 填写连接信息</h2>{selected?.docsUrl&&<a className="text-button" href={selected.docsUrl} target="_blank" rel="noreferrer">平台说明<Icon name="external" size={14}/></a>}</div>
      <p className="model-provider-note">{selected?.note}</p>
      <fieldset disabled={!!busy}><label>模型 ID<input list="model-options" value={form.modelName} onChange={e=>edit({modelName:e.target.value})} placeholder="选择或输入模型名称" maxLength={120}/><datalist id="model-options">{selected?.models.map(model=><option key={model} value={model}/>)}</datalist></label>
      <label>接口根地址<input value={form.baseUrl} onChange={e=>edit({baseUrl:e.target.value})} placeholder="https://…/v1" type="url" maxLength={2048}/><small>填写平台的 Base URL，无需加 /chat/completions。</small></label>
      <label>API Key<div className="model-key-input"><input value={form.apiKey} onChange={e=>edit({apiKey:e.target.value})} type={visible?'text':'password'} autoComplete="off" spellCheck={false} maxLength={4096} placeholder={canKeep?'已配置，留空保留；填写新 Key 可替换':'填写所选服务商的 API Key'}/><button type="button" className="text-button" aria-label={visible?'隐藏输入的 Key':'显示输入的 Key'} onClick={()=>setVisible(v=>!v)}>{visible?'隐藏':'显示'}</button></div><small>{canKeep?'保留已有 Key 仅适用于同一服务商、同一地址。':'切换服务商或接口地址后，需要填写对应的新 Key。'}</small></label>
      <label className="model-checkbox"><input type="checkbox" checked={form.jsonMode} onChange={e=>edit({jsonMode:e.target.checked})}/><span>评分请求启用 JSON 模式<small>服务不支持时可关闭；回答和来源仍会经过严格校验。</small></span></label></fieldset>
      {error&&<div className="model-feedback is-error" role="alert"><Icon name="alert" size={17}/>{error}</div>}
      {check&&<div className={'model-feedback '+(check.ok?'is-success':'is-error')} role="status"><Icon name={check.ok?'check':'alert'} size={17}/><span>{check.message}<small>本次耗时 {(check.durationMs/1000).toFixed(1)} 秒</small></span></div>}
      {message&&<div className="model-feedback is-success" role="status"><Icon name="check" size={17}/>{message}</div>}
      <div className="model-actions"><button className="btn btn-primary" disabled={!!busy} onClick={()=>action('save',async()=>{populate(await saveModelSettings(form));setCheck(null);setMessage('已保存，新发起的模型请求立即使用该配置。')})}>{busy==='save'?'正在保存…':'保存并使用'}<Icon name="arrow" size={16}/></button><button className="btn btn-ghost" disabled={!!busy} onClick={()=>action('test',async()=>setCheck(await checkModelSettings(form)))}>{busy==='test'?'正在测试…':'测试连接'}</button><span>测试只发送一句固定提示，会产生少量调用消耗。</span></div>
    </section>
    <section className="model-storage-note"><div><h2>由你配置，由你掌控</h2><p>Key 只在填写时提交给本机后端，用于请求你选定的模型服务；不会写入浏览器存储、日志或练习导出。语音识别仍使用独立的讯飞配置。</p><p>这是当前个人工作台的配置，会用于本实例的所有练习。正在执行的单次模型请求保留原配置。</p></div><div>{!clearing?<button className="text-button" disabled={!!busy||!saved.configured} onClick={()=>setClearing(true)}><Icon name="trash" size={16}/>清除已保存的 Key</button>:<div className="model-clear-confirm"><p>清除后模型调用将暂停，直到重新配置。</p><button className="btn btn-ghost" disabled={!!busy} onClick={()=>setClearing(false)}>取消</button><button className="btn btn-danger" disabled={!!busy} onClick={()=>action('clear',async()=>{populate(await clearModelKey(saved.revision));setCheck(null);setClearing(false);setMessage('Key 已清除，不会自动恢复旧环境 Key。')})}>确认清除</button></div>}</div></section>
  </div></div>
}
