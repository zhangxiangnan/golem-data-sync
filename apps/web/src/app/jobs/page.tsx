"use client";

import Link from "next/link";
import { Archive, ArrowRight, Play, Plus } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState, ErrorBanner, LoadingState, PageHeader, duration, formatDate, formatNumber } from "@/components/ui";
import { api } from "@/lib/api";
import type { SyncJob } from "@/lib/types";
import { useRouter } from "next/navigation";

export default function JobsPage() {
  const [jobs, setJobs] = useState<SyncJob[]>(); const [error, setError] = useState(""); const [running, setRunning] = useState("");
  const router = useRouter();
  const load = useCallback(() => api.jobs().then(setJobs).catch((reason) => setError(reason.message)), []);
  useEffect(() => { load(); }, [load]);
  async function start(job: SyncJob) { setRunning(job.id); setError(""); try { const run = await api.startRun(job.id); router.push(`/runs/${run.id}`); } catch (reason) { setError(reason instanceof Error ? reason.message : "启动失败"); } finally { setRunning(""); } }
  async function archive(job: SyncJob) { if (!window.confirm(`归档任务「${job.name}」？历史运行仍会保留。`)) return; try { await api.archiveJob(job.id); await load(); } catch (reason) { setError(reason instanceof Error ? reason.message : "归档失败"); } }
  return <>
    <PageHeader eyebrow="Pipelines" title="同步任务" description="配置并手工运行单表 MySQL 批量同步。" action={<Link className="button primary" href="/jobs/new"><Plus size={15} />创建任务</Link>} />
    {error && <ErrorBanner message={error} />}
    {!jobs ? <LoadingState /> : <section className="panel"><div className="panel-header"><div><h2>有效任务</h2><p>{jobs.length} 个手工触发任务</p></div></div>
      {jobs.length === 0 ? <EmptyState title="还没有同步任务" description="使用四步向导连接源表和目标表，系统会生成确定性的 SeaTunnel 配置。" href="/jobs/new" action="创建第一个任务" /> :
        <table className="data-table"><thead><tr><th>任务</th><th>同步链路</th><th>写入策略</th><th>最近运行</th><th>数据量</th><th>更新时间</th><th /></tr></thead><tbody>
          {jobs.map((job) => <tr key={job.id}><td><Link className="cell-title" href={`/jobs/${job.id}`}>{job.name}</Link><span className="cell-sub">并行度 {job.parallelism} · 批量 {formatNumber(job.batchSize)}</span></td><td><div className="route-flow"><span className="db-pill">{job.sourceDataSourceName}.{job.sourceTable}</span><ArrowRight size={13} className="arrow" /><span className="db-pill">{job.targetDataSourceName}.{job.targetTable}</span></div></td><td>{job.writeMode === "APPEND" ? "追加" : "清空重写"}</td><td>{job.latestRun ? <><StatusBadge status={job.latestRun.status} /><span className="cell-sub">{duration(job.latestRun.startedAt, job.latestRun.finishedAt)}</span></> : <span className="cell-sub">尚未运行</span>}</td><td>{job.latestRun ? `${formatNumber(job.latestRun.sourceReadCount)} / ${formatNumber(job.latestRun.sinkWriteCount)}` : "—"}</td><td>{formatDate(job.updatedAt)}</td><td><div className="actions"><button disabled={running === job.id || !!job.latestRun && ["SUBMITTING","PENDING","RUNNING","STOPPING"].includes(job.latestRun.status)} className="button small primary" onClick={() => start(job)}><Play size={12} />{running === job.id ? "启动中" : "运行"}</button><Link className="button ghost small" href={`/jobs/${job.id}`}>详情</Link><button aria-label="归档" className="button ghost small" onClick={() => archive(job)}><Archive size={13} /></button></div></td></tr>)}
        </tbody></table>}
    </section>}
  </>;
}
