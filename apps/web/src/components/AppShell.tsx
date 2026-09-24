"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Activity, Blocks, Database, Gauge, Waves } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { SystemStatus } from "@/lib/types";

const nav = [
  { href: "/", label: "概览", icon: Gauge },
  { href: "/sources", label: "数据源", icon: Database },
  { href: "/jobs", label: "同步任务", icon: Blocks },
  { href: "/lab", label: "SeaTunnel 实验台", icon: Waves },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [status, setStatus] = useState<SystemStatus>();

  useEffect(() => {
    let mounted = true;
    const load = () => api.systemStatus().then((value) => mounted && setStatus(value)).catch(() => mounted && setStatus(undefined));
    load();
    const timer = window.setInterval(load, 10_000);
    return () => { mounted = false; window.clearInterval(timer); };
  }, []);
  const enabled = status?.profiles.filter((profile) => profile.enabled) ?? [];
  const online = enabled.filter((profile) => profile.online).length;
  const zeta = status?.profiles.find((profile) => profile.id === "zeta-local");

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Link className="brand" href="/">
          <span className="brand-mark"><Waves size={21} strokeWidth={2.2} /></span>
          <span><strong>Golem</strong><small>DATA SYNC</small></span>
        </Link>
        <nav className="nav-list">
          <p className="nav-label">工作台</p>
          {nav.map(({ href, label, icon: Icon }) => {
            const active = href === "/" ? pathname === "/" : pathname.startsWith(href);
            return <Link key={href} className={`nav-item ${active ? "active" : ""}`} href={href}><Icon size={18} />{label}</Link>;
          })}
        </nav>
        <div className="sidebar-footer">
          <div className="engine-card">
            <div className="engine-row"><Activity size={16} /><span>执行引擎</span></div>
            <div className="engine-state"><i className={online > 0 ? "online" : "offline"} />{online}/{enabled.length || 2} 个配置可用</div>
            <code>{zeta?.online ? "Zeta ready" : "Zeta offline"} · Spark mock</code>
          </div>
          <p>SeaTunnel 2.3.13 · Local</p>
        </div>
      </aside>
      <main className="main-area">
        <header className="topbar">
          <div><span className="crumb">数据集成</span><span className="crumb-sep">/</span><span>{nav.find((item) => item.href === "/" ? pathname === "/" : pathname.startsWith(item.href))?.label ?? "详情"}</span></div>
          <div className={`health-pill ${online > 0 ? "healthy" : ""}`}><i />{online > 0 ? `${online} 个引擎可用` : "引擎离线"}</div>
        </header>
        <div className="page-container">{children}</div>
      </main>
    </div>
  );
}
