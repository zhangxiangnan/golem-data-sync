"use client";

import Link from "next/link";
import { Activity, CheckCircle2, CircleAlert, Database, Layers3 } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { StatusBadge } from "@/components/StatusBadge";
import { EngineBadge } from "@/components/EngineBadge";
import { EmptyState, ErrorBanner, LoadingState, MetricCard, PageHeader, duration, formatDate, formatNumber } from "@/components/ui";
import { api } from "@/lib/api";
import type { DashboardSummary, SystemStatus } from "@/lib/types";

export default function OverviewPage() {
  const [summary, setSummary] = useState<DashboardSummary>();
  const [system, setSystem] = useState<SystemStatus>();
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const [nextSummary, nextSystem] = await Promise.all([api.dashboard(), api.systemStatus()]);
      setSummary(nextSummary); setSystem(nextSystem); setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "加载概览失败"); }
  }, []);

  useEffect(() => { load(); const timer = window.setInterval(load, 5_000); return () => window.clearInterval(timer); }, [load]);

  return <>
    <PageHeader eyebrow="Overview" title="数据同步概览" description="掌握数据连接、批量任务和多执行引擎状态。" action={<Link className="button primary" href="/jobs/new">创建同步任务</Link>} />
    {error && <ErrorBanner message={error} />}
    {system && !system.profiles.some((profile) => profile.id === "zeta-local" && profile.online) && <div className="alert warning" style={{ marginBottom: 18 }}><strong>Zeta 未连接</strong><span>页面和 Spark 模拟流程仍可使用；运行真实同步前请启动本机 Zeta。</span></div>}
    {!summary ? <LoadingState /> : <>
      <section className="metrics-grid">
        <MetricCard icon={Database} label="数据源" value={summary.dataSourceCount} hint="已登记 MySQL 连接" />
        <MetricCard icon={Layers3} label="同步任务" value={summary.jobCount} hint="当前有效任务" />
        <MetricCard icon={Activity} label="运行中" value={summary.runningCount} hint="含等待与停止中" tone="amber" />
        <MetricCard icon={CheckCircle2} label="今日成功" value={summary.todaySucceeded} hint={`成功率 ${summary.successRate.toFixed(1)}%`} tone="green" />
        <MetricCard icon={CircleAlert} label="今日失败" value={summary.todayFailed} hint="需要关注的运行" tone="red" />
      </section>
      {system && <section className="panel" style={{ marginBottom: 22 }}><div className="panel-header"><div><h2>执行引擎</h2><p>运行时选择执行配置；模拟执行不会连接生产集群。</p></div></div><div className="panel-body"><div className="engine-profile-grid">{system.profiles.map((profile) => <article className={`engine-profile-card ${profile.online ? "" : "offline"}`} key={profile.id}><header><h3>{profile.name}</h3><EngineBadge engine={profile.engineType} mock={profile.mock} /></header><p>{profile.message}</p><span className="cell-sub">{profile.enabled ? profile.online ? "可运行" : "不可用" : "未配置"}</span></article>)}</div></div></section>}
      <section className="panel">
        <div className="panel-header"><div><h2>最近运行</h2><p>来自平台保存的最新执行记录</p></div><Link className="button ghost small" href="/jobs">查看全部任务</Link></div>
        {summary.recentRuns.length === 0 ? <EmptyState title="还没有运行记录" description="创建同步任务并手工运行后，状态和指标会显示在这里。" href="/jobs/new" action="创建第一个任务" /> :
          <table className="data-table"><thead><tr><th>任务</th><th>状态</th><th>读取</th><th>写入</th><th>耗时</th><th>开始时间</th><th /></tr></thead><tbody>
            {summary.recentRuns.map((run) => <tr key={run.id}><td><span className="cell-title">{run.jobName}</span><span className="cell-sub">{run.externalJobId ?? "等待引擎接收"}</span></td><td><StatusBadge status={run.status} /> <EngineBadge engine={run.engineType} mock={run.engineProfileId.includes("mock")} /></td><td>{run.metricsAvailable ? formatNumber(run.sourceReadCount) : "—"}</td><td>{run.metricsAvailable ? formatNumber(run.sinkWriteCount) : "—"}</td><td>{duration(run.startedAt, run.finishedAt)}</td><td>{formatDate(run.createdAt)}</td><td><div className="actions"><Link className="button ghost small" href={`/runs/${run.id}`}>详情</Link></div></td></tr>)}
          </tbody></table>}
      </section>
    </>}
  </>;
}
