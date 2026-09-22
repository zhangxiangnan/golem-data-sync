import Link from "next/link";
import type { LucideIcon } from "lucide-react";

export function PageHeader({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: React.ReactNode }) {
  return <div className="page-header"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p></div>{action}</div>;
}

export function MetricCard({ label, value, hint, icon: Icon, tone = "indigo" }: { label: string; value: string | number; hint: string; icon: LucideIcon; tone?: string }) {
  return <div className="metric-card"><div className={`metric-icon ${tone}`}><Icon size={20} /></div><div><p>{label}</p><strong>{value}</strong><small>{hint}</small></div></div>;
}

export function EmptyState({ title, description, href, action }: { title: string; description: string; href?: string; action?: string }) {
  return <div className="empty-state"><div className="empty-orbit"><span /></div><h3>{title}</h3><p>{description}</p>{href && action && <Link className="button primary" href={href}>{action}</Link>}</div>;
}

export function LoadingState() { return <div className="loading-state"><span /><span /><span /></div>; }

export function ErrorBanner({ message }: { message: string }) { return <div className="alert error"><strong>操作失败</strong><span>{message}</span></div>; }

export function formatNumber(value: number) { return new Intl.NumberFormat("zh-CN").format(value ?? 0); }
export function formatDate(value?: string) { return value ? new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit" }).format(new Date(value)) : "—"; }
export function duration(start?: string, end?: string) {
  if (!start) return "—";
  const ms = new Date(end ?? Date.now()).getTime() - new Date(start).getTime();
  if (ms < 1000) return `${ms} ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)} s`;
  return `${Math.floor(ms / 60_000)}m ${Math.floor(ms % 60_000 / 1000)}s`;
}
