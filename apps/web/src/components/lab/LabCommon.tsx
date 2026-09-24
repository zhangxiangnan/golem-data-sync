"use client";
import Link from "next/link";
import type { Json, LabConfig, MetricSample, Run } from "@/lib/lab-api";
import { ACTIVE } from "@/lib/lab-config";
import { duration, formatDate } from "@/components/ui";
export function LabNav(){return <div className="lab-links"><Link href="/lab">实验与运行</Link><Link href="/lab/catalog">参数目录</Link><Link href="/lab/cluster">本机集群</Link><Link href="/lab/compare">运行对比</Link></div>;}
const labels:Record<string,string>={SUBMITTING:"提交中",PENDING:"等待执行",RUNNING:"运行中",SAVING:"保存点创建中",SAVED:"保存点暂停",SUCCEEDED:"正常完成",FAILED:"失败",CANCELED:"已取消",CANCELING:"取消中",UNKNOWN:"状态待确认"};
export function LabStatus({status}:{status:string}){return <span className={`lab-status ${status.toLowerCase()}`}>{labels[status]??status}</span>;}
export function JsonView({value}:{value:unknown}){return <pre className="lab-json">{value===undefined||value===null?"不可用":JSON.stringify(value,null,2)}</pre>;}
export function metric(metrics:Record<string,Json>|undefined|null,key:string){const v=metrics?.[key];return v===null||v===undefined?"不可用":typeof v==="object"?JSON.stringify(v):String(v);}
export function RunsTable({runs,selected,onSelect}:{runs:Run[];selected?:string[];onSelect?:(id:string)=>void}){
  return <div className="table-wrap"><table className="data-table"><thead><tr>{onSelect&&<th>对比</th>}<th>实验 / 运行</th><th>状态</th><th>读取 / 写入 / 提交</th><th>平台耗时</th><th>提交时间</th></tr></thead><tbody>{runs.map(r=><tr key={r.id}>{onSelect&&<td><input type="checkbox" aria-label={`选择运行 ${r.id}`} checked={selected?.includes(r.id)??false} onChange={()=>onSelect(r.id)}/></td>}<td><Link className="lab-link" href={`/lab/runs/${r.id}`}>{r.experimentName}</Link><small className="lab-sub">{r.id.slice(0,8)} · Zeta {r.engineVersion}</small></td><td><LabStatus status={r.status}/>{r.restoredFrom&&<small className="lab-sub">从保存点恢复</small>}</td><td>{["SourceReceivedCount","SinkWriteCount","SinkCommittedCount"].map(k=>metric(r.observation.job?.metrics,k)).join(" / ")}</td><td>{!ACTIVE.has(r.status)&&!r.observation.finishedAt?"不可用":duration(r.createdAt,r.observation.finishedAt)}</td><td>{formatDate(r.createdAt)}</td></tr>)}</tbody></table>{runs.length===0&&<p className="lab-note">尚无运行记录</p>}</div>;
}
export function Dag({config}:{config:LabConfig}){
 return <div className="lab-dag">{(["source","transform","sink"] as const).map(section=><div key={section}><h4>{section.toUpperCase()}</h4>{config[section].map((n,i)=><div className="lab-dag-node" key={i}><small>{section}/{i} · {n.plugin_name}</small><strong>{String(n.plugin_output??n.table??"sink")}</strong>{n.plugin_input&&<span>← {Array.isArray(n.plugin_input)?n.plugin_input.join(", "):String(n.plugin_input)}</span>}</div>)}</div>)}</div>;
}
export function Chart({series,label}:{series:{name:string;values:(number|null)[];times:string[]}[];label:string}){
 const values=series.flatMap(s=>s.values).filter((x):x is number=>x!==null&&Number.isFinite(x));if(!values.length)return <p className="lab-note">{label}：不可用</p>;
 const span=Math.max(1,...series.map(s=>{const t=s.times.map(Date.parse).filter(Number.isFinite);return t.length?t[t.length-1]-t[0]:0;}));
 const max=Math.max(...values,1),width=650,height=160,colors=["#5b5ce2","#159a6b","#d68c28","#d6475d"];
 return <div className="lab-chart"><h4>{label}</h4><svg viewBox={`0 0 ${width+60} ${height+40}`} role="img" aria-label={label}><text x="0" y="14" fontSize="10" fill="#667085">{max.toFixed(1)}</text><line x1="50" y1={height} x2={width+50} y2={height} stroke="#e5e9f2"/>{series.map((s,j)=>{const start=Date.parse(s.times[0]??"");const coords=s.values.map((v,i)=>v===null?null:`${50+((Date.parse(s.times[i])-start)/span)*width},${height-((v??0)/max)*(height-16)}`);return <g key={s.name}>{coords.map((p,i)=>p?<circle key={i} cx={p.split(",")[0]} cy={p.split(",")[1]} r="2" fill={colors[j%4]}><title>{s.times[i]}：{s.values[i]}</title></circle>:null)}{coords.map((p,i)=>i>0&&p&&coords[i-1]?<line key={i} x1={coords[i-1]!.split(",")[0]} y1={coords[i-1]!.split(",")[1]} x2={p.split(",")[0]} y2={p.split(",")[1]} stroke={colors[j%4]}/>:null)}</g>})}<text x="50" y={height+25} fontSize="10" fill="#667085">从首次采样开始 · 统一横轴 0–{(span/1000).toFixed(1)} 秒</text></svg><div className="lab-links">{series.map((s,i)=><span key={s.name} style={{color:colors[i%4]}}>{s.name} · {s.values.length} 次采样</span>)}</div></div>;
}
export function numberValue(v:unknown):number|null {if(v===null||v===undefined||v==="")return null;const n=Number(v);return Number.isFinite(n)?n:null;}
export function resourceValue(sample:MetricSample,key:string){const raw=sample.resources?.[0]?.[key];if(!raw)return null;const n=parseFloat(raw);if(!Number.isFinite(n))return null;if(key==="heap.memory.used"){if(raw.endsWith("G"))return n*1024;if(raw.endsWith("K"))return n/1024;if(raw.endsWith("B"))return n/1024/1024;}return n;}

export function TableMetrics({metrics}:{metrics?:Record<string,Json>}) {
 const keys=["TableSourceReceivedCount","TableSourceReceivedQPS","TableSinkWriteCount","TableSinkCommittedCount","TableSinkWriteQPS"];
 const rows=Array.from(new Set(keys.flatMap(k=>{const v=metrics?.[k];return v&&typeof v==="object"&&!Array.isArray(v)?Object.keys(v):[];}))).sort();
 if(!rows.length)return <p className="lab-note">分表指标不可用</p>;
 const cell=(name:string,key:string)=>{const group=metrics?.[key];return group&&typeof group==="object"&&!Array.isArray(group)&&group[name]!==undefined?String(group[name]):"不可用";};
 return <div className="table-wrap"><table className="data-table"><thead><tr><th>引擎表标签</th><th>读取</th><th>读取/秒</th><th>写入</th><th>提交</th><th>写入/秒</th></tr></thead><tbody>{rows.map(row=><tr key={row}><td><code>{row}</code></td>{keys.map(key=><td key={key}>{cell(row,key)}</td>)}</tr>)}</tbody></table></div>;
}
