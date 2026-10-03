export interface ModelPreset { id:string;name:string;baseUrl:string;models:string[];jsonMode:boolean;note:string;docsUrl:string }
export interface ModelSettings { providerId:string;baseUrl:string;modelName:string;jsonMode:boolean;configured:boolean;source:'environment'|'user';revision:number;scope:string }
export interface ModelUpdate { providerId:string;baseUrl:string;modelName:string;jsonMode:boolean;apiKey:string;revision:number }
export interface ModelCheck { ok:boolean;message:string;durationMs:number;modelName:string;checkedAt:string }
async function request<T>(path='',method='GET',body?:unknown):Promise<T>{
  const response=await fetch('/api/settings/model'+path,{method,cache:'no-store',...(body!==undefined&&{headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})})
  if(!response.ok){const error=await response.json().catch(()=>({}));throw new Error(error.message||'模型设置暂不可用，请稍后重试。')}
  return response.json()
}
export const getModelSettings=()=>request<ModelSettings>()
export const getModelPresets=()=>request<ModelPreset[]>('/presets')
export const saveModelSettings=(body:ModelUpdate)=>request<ModelSettings>('','PUT',body)
export const checkModelSettings=(body:ModelUpdate)=>request<ModelCheck>('/test','POST',body)
export const clearModelKey=(revision:number)=>request<ModelSettings>('?revision='+revision,'DELETE')
