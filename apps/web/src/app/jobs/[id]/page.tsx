"use client";

import Link from "next/link";
import { ArrowLeft, ArrowRight, CheckCircle2, CircleAlert, Play } from "lucide-react";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { StatusBadge } from "@/components/StatusBadge";
import { ErrorBanner, LoadingState, duration, formatDate, formatNumber } from "@/components/ui";
import { api } from "@/lib/api";
import type { JobValidation, SyncJob, SyncRun } from "@/lib/types";

type Tab = "overview" | "runs" | "config";

export default function JobDetailPage() {
  const { id } = useParams<{ id: string }>(); const router = useRouter();
  const [job, setJob] = useState<SyncJob>(); const [runs, setRuns] = useState<SyncRun[]>([]); const [validation, setValidation] = useState<JobValidation>(); const [config, setConfig] = useState("");
  const [tab, setTab] = useState<Tab>("overview"); const [error, setError] = useState(""); const [busy, setBusy] = useState("");
  const load = useCallback(async () => { try { const [nextJob, nextRuns] = await Promise.all([api.job(id), api.runs(id)]); setJob(nextJob); setRuns(nextRuns); } catch (reason) { setError(reason instanceof Error ? reason.message : "加载失败"); } }, [id]);
  useEffect(() => { load(); }, [load]);
  async function validate() { setBusy("validate"); setError(""); try { setValidation(await api.validateJob(id)); } catch (reason) { setError(reason instanceof Error ? reason.message : "校验失败"); } finally { setBusy(""); } }
  async function preview() { setTab("config"); if (config) return; try { setConfig((await api.configPreview(id)).content); } catch (reason) { setError(reason instanceof Error ? reason.message : "配置加载失败"); } }
  async function start() { setBusy("run"); setError(""); try { const run = await api.startRun(id); router.push(`/runs/${run.id}`); } catch (reason) { setError(reason instanceof Error ? reason.message : "启动失败"); } finally { setBusy(""); } }
  if (!job) return error ? <ErrorBanner message={error} /> : <LoadingState />;
  const active = job.latestRun && ["SUBMITTING","PENDING","RUNNING","STOPPING"].includes(job.latestRun.status);
  return <>
    <div className="detail-hero"><div><Link className="eyebrow" href="/jobs"><ArrowLeft size={11} style={{ display: "inline", marginRight: 5 }} />同步任务</Link><h1>{job.name}</h1><p>{job.description || "未填写任务描述"}</p></div><div className="detail-actions"><button className="button" onClick={validate}>{busy === "validate" ? "校验中…" : "校验配置"}</button><button className="button primary" disabled={!!active || busy === "run"} onClick={start}><Play size={14} />{active ? "已有运行中任务" : busy === "run" ? "启动中…" : "立即运行"}</button></div></div>
    {error && <ErrorBanner message={error} />}
    {validation && <div className={`alert ${validation.valid ? "info" : "error"}`} style={{ marginBottom: 18 }}>{validation.valid ? <CheckCircle2 size={16} /> : <CircleAlert size={16} />}<strong>{validation.valid ? "校验通过" : "校验失败"}</strong><span>{validation.issues.map((item) => item.message).join("；") || `已识别 ${validation.sourceColumns.length} 个字段`}</span></div>}
    <section className="panel"><div className="tabs"><button className={`tab ${tab === "overview" ? "active" : ""}`} onClick={() => setTab("overview")}>配置概览</button><button className={`tab ${tab === "runs" ? "active" : ""}`} onClick={() => setTab("runs")}>运行历史 {runs.length}</button><button className={`tab ${tab === "config" ? "active" : ""}`} onClick={preview}>SeaTunnel 配置</button></div>
      <div className="panel-body">{tab === "overview" && <div className="detail-grid"><div><h3 style={{ fontSize: 13 }}>同步链路</h3><div className="route-flow" style={{ margin: "16px 0 22px" }}><span className="db-pill">{job.sourceDataSourceName}.{job.sourceTable}</span><ArrowRight size={16} className="arrow" /><span className="db-pill">{job.targetDataSourceName}.{job.targetTable}</span></div><div className="kv-grid"><div className="kv"><span>写入策略</span><strong>{job.writeMode === "APPEND" ? "追加写入" : "清空后重写"}</strong></div><div className="kv"><span>执行模式</span><strong>BATCH</strong></div><div className="kv"><span>并行度</span><strong>{job.parallelism}</strong></div><div className="kv"><span>批大小</span><strong>{formatNumber(job.batchSize)}</strong></div></div></div><aside className="summary-card"><h3>最近一次运行</h3>{job.latestRun ? <><div className="summary-line"><span>状态</span><StatusBadge status={job.latestRun.status} /></div><div className="summary-line"><span>读取 / 写入</span><strong>{formatNumber(job.latestRun.sourceReadCount)} / {formatNumber(job.latestRun.sinkWriteCount)}</strong></div><div className="summary-line"><span>耗时</span><strong>{duration(job.latestRun.startedAt, job.latestRun.finishedAt)}</strong></div><Link className="button" style={{ width: "100%", marginTop: 14 }} href={`/runs/${job.latestRun.id}`}>查看运行详情</Link></> : <p style={{ color: "#98a2b3", fontSize: 11 }}>任务尚未运行。</p>}</aside></div>}
        {tab === "runs" && (runs.length === 0 ? <p style={{ color: "#98a2b3", fontSize: 12 }}>暂无运行记录。</p> : <table className="data-table"><thead><tr><th>开始时间</th><th>状态</th><th>读取</th><th>写入</th><th>耗时</th><th /></tr></thead><tbody>{runs.map((run) => <tr key={run.id}><td>{formatDate(run.createdAt)}</td><td><StatusBadge status={run.status} /></td><td>{formatNumber(run.sourceReadCount)}</td><td>{formatNumber(run.sinkWriteCount)}</td><td>{duration(run.startedAt, run.finishedAt)}</td><td><Link className="button ghost small" href={`/runs/${run.id}`}>详情</Link></td></tr>)}</tbody></table>)}
        {tab === "config" && (config ? <pre className="code-block">{config}</pre> : <LoadingState />)}
      </div></section>
  </>;
}
