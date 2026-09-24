export type Json = null | boolean | number | string | Json[] | { [key: string]: Json };
export type NodeConfig = { [key: string]: Json; plugin_name: string };
export type LabConfig = { [key: string]: Json; env: Record<string, Json>; source: NodeConfig[]; transform: NodeConfig[]; sink: NodeConfig[] };
export type Experiment = { id: string; name: string; description: string; config: LabConfig; bindings: Record<string, string>; secretNames: string[]; updatedAt: string };
export type Option = { key: string; type: string; javaType: string; defaultValue: Json; required: boolean; description: string; enumValues?: string[]; origin: string; sourceUrl: string; conditions: Json[]; condition?: string; fallbackKeys: string[]; version: string; scope: string };
export type CatalogGroup = { id: string; plugin: string; installed: boolean; documented: boolean; platformVerified: boolean; verifiedRunIds: string[]; verificationNote: string; requirements: string; documentation: string; options: Option[]; rules: Json[] };
export type Catalog = { version: string; provenance: string; groups: CatalogGroup[] };
export type Validation = { valid: boolean; errors: string[]; warnings: string[]; validationLevel: string; preview: LabConfig; config: LabConfig; tables: Json[]; sourceSchemas: Json[]; dag: Record<string,string[]> };
export type MetricSample = { time: string; metrics: Record<string,Json> | null; resources: Record<string,string>[] | null; resourceScope: string };
export type Run = { id: string; experimentId: string; experimentName: string; externalJobId: string; status: string; createdAt: string; engineVersion: string; configDigest: string; config: LabConfig; authoringConfig: LabConfig; bindings: Record<string,string>; validation: Validation; restoredFrom?: string; observation: { job?: { jobStatus: string; jobDag?: Json; metrics?: Record<string,Json>; startTime?: string; finishTime?: string; errorMsg?: string }; checkpoints?: Json; finishedAt?: string; lastError?: string; savepointAvailable?: boolean }; events?: { time: string; type: string; message: string }[]; samples?: MetricSample[] };
export type Cluster = { files: { name: string; content?: string; error?: string; restartRequired: boolean; source: string }[]; plugins: { file: string; versionSource: string }[]; overview?: Json; resources?: Json; error?: string; resourceScope: string };
async function request<T>(path: string, method="GET", body?: unknown): Promise<T> {
  const response=await fetch(`/api/lab${path}`,{method,cache:"no-store",headers:{"Content-Type":"application/json"},...(body===undefined?{}:{body:JSON.stringify(body)})});
  if(!response.ok){let message=`请求失败 (${response.status})`;try{message=(await response.json()).detail??message;}catch{}throw new Error(message);}
  return response.status===204?undefined as T:response.json();
}
export const labApi={
  catalog:()=>request<Catalog>("/catalog"),cluster:()=>request<Cluster>("/cluster"),
  experiments:()=>request<Experiment[]>("/experiments"),experiment:(id:string)=>request<Experiment>(`/experiments/${id}`),
  save:(body:Partial<Experiment>&{secrets?:Record<string,string>},id?:string)=>request<Experiment>(id?`/experiments/${id}`:"/experiments",id?"PUT":"POST",body),
  copy:(id:string)=>request<Experiment>(`/experiments/${id}/copy`,"POST"),remove:(id:string)=>request<void>(`/experiments/${id}`,"DELETE"),
  validate:(id:string)=>request<Validation>(`/experiments/${id}/validate`,"POST"),start:(id:string)=>request<Run>(`/experiments/${id}/runs`,"POST"),
  runs:(id?:string)=>request<Run[]>(`/runs${id?`?experimentId=${id}`:""}`),run:(id:string)=>request<Run>(`/runs/${id}`),
  metrics:(id:string)=>request<MetricSample[]>(`/runs/${id}/metrics`),logs:(id:string)=>request<{lines:string[];source?:string;error?:string}>(`/runs/${id}/logs`),
  action:(id:string,action:"cancel"|"savepoint"|"restore")=>request<Run>(`/runs/${id}/${action}`,"POST"),
  compare:(ids:string[])=>request<Run[]>(`/compare?ids=${ids.map(encodeURIComponent).join(",")}`),
};
