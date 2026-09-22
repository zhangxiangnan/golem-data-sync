"use client";

import { Database, Plus, RefreshCw, Trash2, X } from "lucide-react";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState, ErrorBanner, LoadingState, PageHeader, formatDate } from "@/components/ui";
import { api } from "@/lib/api";
import type { DataSource, DataSourceInput } from "@/lib/types";

const blank: DataSourceInput = { name: "", host: "127.0.0.1", port: 3306, database: "", username: "root", password: "" };

export default function SourcesPage() {
  const [sources, setSources] = useState<DataSource[]>();
  const [editing, setEditing] = useState<DataSource | null | undefined>();
  const [testing, setTesting] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const load = useCallback(() => api.dataSources().then(setSources).catch((reason) => setError(reason.message)), []);
  useEffect(() => { load(); }, [load]);

  async function test(id: string) {
    setTesting(id); setError(""); setMessage("");
    try { const result = await api.testDataSource(id); result.success ? setMessage(`连接成功，耗时 ${result.latencyMs} ms`) : setError(result.message); await load(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "连接测试失败"); }
    finally { setTesting(""); }
  }

  async function remove(source: DataSource) {
    if (!window.confirm(`确认删除数据源「${source.name}」？`)) return;
    try { await api.deleteDataSource(source.id); await load(); } catch (reason) { setError(reason instanceof Error ? reason.message : "删除失败"); }
  }

  return <>
    <PageHeader eyebrow="Connections" title="数据源" description="集中维护批量同步使用的 MySQL 连接，密码只在服务端加密保存。" action={<button className="button primary" onClick={() => setEditing(null)}><Plus size={15} />新增数据源</button>} />
    {error && <ErrorBanner message={error} />}{message && <div className="alert info" style={{ marginBottom: 18 }}><strong>连接测试</strong><span>{message}</span></div>}
    {!sources ? <LoadingState /> : <section className="panel">
      <div className="panel-header"><div><h2>MySQL 连接</h2><p>{sources.length} 个已登记数据源</p></div></div>
      {sources.length === 0 ? <EmptyState title="还没有数据源" description="先添加源端和目标端 MySQL 连接，然后创建同步任务。" /> :
        <table className="data-table"><thead><tr><th>名称</th><th>连接地址</th><th>数据库</th><th>账号</th><th>状态</th><th>最后测试</th><th /></tr></thead><tbody>
          {sources.map((source) => <tr key={source.id}><td><span className="cell-title">{source.name}</span><span className="cell-sub">MySQL</span></td><td><code>{source.host}:{source.port}</code></td><td><span className="db-pill"><Database size={12} />{source.database}</span></td><td>{source.username}</td><td><StatusBadge status={source.status} /></td><td>{formatDate(source.lastTestAt)}</td><td><div className="actions"><button className="button ghost small" disabled={testing === source.id} onClick={() => test(source.id)}><RefreshCw size={13} />{testing === source.id ? "测试中" : "测试"}</button><button className="button ghost small" onClick={() => setEditing(source)}>编辑</button><button aria-label="删除" className="button ghost small danger" onClick={() => remove(source)}><Trash2 size={13} /></button></div></td></tr>)}
        </tbody></table>}
    </section>}
    {editing !== undefined && <SourceModal source={editing} onClose={() => setEditing(undefined)} onSaved={async () => { setEditing(undefined); await load(); }} />}
  </>;
}

function SourceModal({ source, onClose, onSaved }: { source: DataSource | null; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<DataSourceInput>(source ? { name: source.name, host: source.host, port: source.port, database: source.database, username: source.username } : blank);
  const [saving, setSaving] = useState(false); const [error, setError] = useState("");
  const update = (key: keyof DataSourceInput, value: string | number) => setForm((old) => ({ ...old, [key]: value }));
  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError("");
    try { source ? await api.updateDataSource(source.id, form) : await api.createDataSource(form); onSaved(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "保存失败"); } finally { setSaving(false); }
  }
  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><div className="modal"><div className="modal-header"><div><h2>{source ? "编辑数据源" : "新增数据源"}</h2><p>仅支持 MySQL；保存后建议立即测试连接。</p></div><button aria-label="关闭" className="button ghost small" onClick={onClose}><X size={16} /></button></div><form className="modal-body" onSubmit={submit}>{error && <ErrorBanner message={error} />}<div className="form-grid">
    <div className="field full"><label>数据源名称</label><input required maxLength={120} value={form.name} onChange={(e) => update("name", e.target.value)} placeholder="例如：订单库（源）" /></div>
    <div className="field"><label>主机</label><input required value={form.host} onChange={(e) => update("host", e.target.value)} /></div><div className="field"><label>端口</label><input required type="number" min={1} max={65535} value={form.port} onChange={(e) => update("port", Number(e.target.value))} /></div>
    <div className="field"><label>数据库</label><input required value={form.database} onChange={(e) => update("database", e.target.value)} placeholder="source_db" /></div><div className="field"><label>用户名</label><input required value={form.username} onChange={(e) => update("username", e.target.value)} /></div>
    <div className="field full"><label>密码</label><input type="password" value={form.password ?? ""} onChange={(e) => update("password", e.target.value)} placeholder={source ? "留空表示保持原密码" : "允许空密码"} /><small>凭据使用本地 AES-GCM master key 加密保存，不会返回浏览器。</small></div>
  </div><div className="form-actions"><button type="button" className="button" onClick={onClose}>取消</button><button disabled={saving} className="button primary">{saving ? "保存中…" : "保存数据源"}</button></div></form></div></div>;
}
