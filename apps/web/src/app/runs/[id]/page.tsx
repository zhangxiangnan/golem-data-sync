"use client";

import Link from "next/link";
import { ArrowLeft, Ban, DatabaseZap, Gauge, Timer, UploadCloud } from "lucide-react";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { StatusBadge } from "@/components/StatusBadge";
import { EngineBadge } from "@/components/EngineBadge";
import { ErrorBanner, LoadingState, MetricCard, duration, formatDate, formatNumber } from "@/components/ui";
import { api } from "@/lib/api";
import { isTerminalStatus } from "@/lib/run-status";
import type { SyncRun } from "@/lib/types";

export default function RunDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [run, setRun] = useState<SyncRun>();
  const [error, setError] = useState("");
  const [stopping, setStopping] = useState(false);
  const load = useCallback(async () => {
    try { setRun(await api.run(id)); setError(""); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "加载运行详情失败"); }
  }, [id]);
  useEffect(() => {
    load();
    if (run && isTerminalStatus(run.status)) return;
    const timer = window.setInterval(load, 2_000);
    return () => window.clearInterval(timer);
  }, [load, run?.status]);
  async function stop() {
    setStopping(true);
    try { setRun(await api.stopRun(id)); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "停止失败"); }
    finally { setStopping(false); }
  }
  if (!run) return error ? <ErrorBanner message={error} /> : <LoadingState />;
  const active = !isTerminalStatus(run.status);
  return <>
    <div className="detail-hero"><div><Link className="eyebrow" href={`/jobs/${run.jobId}`}><ArrowLeft size={11} style={{ display: "inline", marginRight: 5 }} />{run.jobName}</Link><h1>运行详情</h1><p>平台运行 ID {run.id}</p></div><div className="detail-actions"><StatusBadge status={run.status} />{active && <button className="button danger" disabled={stopping || run.status === "STOPPING"} onClick={stop}><Ban size={14} />{stopping ? "发送中…" : "停止任务"}</button>}</div></div>
    {error && <ErrorBanner message={error} />}
    {run.stale && <div className="alert warning" style={{ marginBottom: 18 }}><strong>指标可能已过期</strong><span>暂时无法连接执行引擎：{run.lastPollError}</span></div>}
    {run.errorMessage && <div className="alert error" style={{ marginBottom: 18 }}><strong>执行错误</strong><span>{run.errorMessage}</span></div>}
    <section className="metrics-grid" style={{ gridTemplateColumns: "repeat(4, minmax(0,1fr))" }}><MetricCard icon={DatabaseZap} label="源端读取" value={run.metricsAvailable ? formatNumber(run.sourceReadCount) : "—"} hint={run.metricsAvailable ? `${formatNumber(run.sourceBytes)} bytes` : "当前引擎未提供"} /><MetricCard icon={UploadCloud} label="目标写入" value={run.metricsAvailable ? formatNumber(run.sinkWriteCount) : "—"} hint={run.metricsAvailable ? `${formatNumber(run.sinkBytes)} bytes` : "当前引擎未提供"} tone="green" /><MetricCard icon={Gauge} label="当前吞吐" value={run.metricsAvailable ? `${run.sinkQps.toFixed(1)} r/s` : "—"} hint={run.metricsAvailable ? `读取 ${run.sourceQps.toFixed(1)} r/s` : "当前引擎未提供"} tone="amber" /><MetricCard icon={Timer} label="运行耗时" value={duration(run.startedAt ?? run.createdAt, run.finishedAt)} hint={active ? "持续更新" : "最终耗时"} /></section>
    <div className="detail-grid"><section className="panel"><div className="panel-header"><div><h2>状态时间线</h2><p>仅记录真实状态变化，不生成伪进度。</p></div></div><div className="panel-body"><div className="timeline">{run.events.map((event) => <div className="timeline-item" key={event.id}><strong><StatusBadge status={event.status} /></strong><p>{event.message}</p><time>{formatDate(event.createdAt)}</time></div>)}</div></div></section><aside className="panel"><div className="panel-header"><div><h2>运行信息</h2><p>平台与引擎关联信息</p></div></div><div className="panel-body"><div className="summary-line"><span>执行引擎</span><strong><EngineBadge engine={run.engineType} mock={run.engineProfileId.includes("mock")} /></strong></div><div className="summary-line"><span>执行配置</span><strong>{run.engineProfileId}</strong></div><div className="summary-line"><span>外部任务 ID</span><strong>{run.externalJobId ?? "等待分配"}</strong></div>{run.trackingUrl && <div className="summary-line"><span>引擎详情</span><strong><a href={run.trackingUrl} target="_blank" rel="noreferrer">打开追踪页面</a></strong></div>}<div className="summary-line"><span>创建时间</span><strong>{formatDate(run.createdAt)}</strong></div><div className="summary-line"><span>开始时间</span><strong>{formatDate(run.startedAt)}</strong></div><div className="summary-line"><span>结束时间</span><strong>{formatDate(run.finishedAt)}</strong></div><div className="summary-line"><span>最后更新</span><strong>{formatDate(run.updatedAt)}</strong></div></div></aside></div>
  </>;
}
