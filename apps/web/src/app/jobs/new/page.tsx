"use client";

import Link from "next/link";
import { ArrowLeft, ArrowRight, Check, KeyRound } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ErrorBanner, LoadingState, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import { canContinueJobWizard } from "@/lib/job-wizard";
import type { ColumnInfo, DataSource, SyncJobInput, TableInfo, WriteMode } from "@/lib/types";

const steps = ["基本信息", "选择源表", "配置目标", "确认创建"];

export default function NewJobPage() {
  const router = useRouter();
  const [step, setStep] = useState(0); const [sources, setSources] = useState<DataSource[]>(); const [tables, setTables] = useState<TableInfo[]>([]); const [columns, setColumns] = useState<ColumnInfo[]>([]);
  const [loadingMeta, setLoadingMeta] = useState(false); const [saving, setSaving] = useState(false); const [error, setError] = useState("");
  const [form, setForm] = useState<SyncJobInput>({ name: "", description: "", sourceDataSourceId: "", sourceTable: "", targetDataSourceId: "", targetTable: "", writeMode: "REPLACE", parallelism: 1, batchSize: 1000 });
  useEffect(() => { api.dataSources().then(setSources).catch((reason) => setError(reason.message)); }, []);
  const source = sources?.find((item) => item.id === form.sourceDataSourceId); const target = sources?.find((item) => item.id === form.targetDataSourceId);
  const valid = canContinueJobWizard(step, form);
  const update = <K extends keyof SyncJobInput>(key: K, value: SyncJobInput[K]) => setForm((old) => ({ ...old, [key]: value }));

  async function selectSource(id: string) { update("sourceDataSourceId", id); update("sourceTable", ""); setColumns([]); setLoadingMeta(true); setError(""); try { setTables(await api.tables(id)); } catch (reason) { setError(reason instanceof Error ? reason.message : "读取表失败"); } finally { setLoadingMeta(false); } }
  async function selectTable(table: string) { update("sourceTable", table); setLoadingMeta(true); try { setColumns(await api.columns(form.sourceDataSourceId, table)); if (!form.targetTable) update("targetTable", table); } catch (reason) { setError(reason instanceof Error ? reason.message : "读取字段失败"); } finally { setLoadingMeta(false); } }
  async function create() { setSaving(true); setError(""); try { const job = await api.createJob(form); router.push(`/jobs/${job.id}`); } catch (reason) { setError(reason instanceof Error ? reason.message : "创建失败"); } finally { setSaving(false); } }
  function next() { if (step < 3) setStep(step + 1); else create(); }

  if (!sources) return <><PageHeader eyebrow="New pipeline" title="创建同步任务" description="正在加载数据源…" /><LoadingState /></>;
  return <>
    <PageHeader eyebrow="New pipeline" title="创建同步任务" description="用四个步骤配置单表整表同步，创建后可先校验再运行。" action={<Link className="button" href="/jobs"><ArrowLeft size={14} />返回任务</Link>} />
    <div className="stepper">{steps.map((label, index) => <div className={`step ${index === step ? "active" : index < step ? "done" : ""}`} key={label}><span>{index < step ? <Check size={13} /> : index + 1}</span>{label}</div>)}</div>
    {error && <ErrorBanner message={error} />}
    {sources.length < 2 && <div className="alert warning" style={{ marginBottom: 18 }}><strong>需要数据源</strong><span>建议至少配置源端和目标端两个 MySQL 数据源。</span><Link href="/sources">前往数据源</Link></div>}
    <div className="wizard-grid"><section className="panel"><div className="panel-header"><div><h2>{steps[step]}</h2><p>{["定义任务名称和运行参数", "选择需要整表读取的 MySQL 表", "选择目标连接、表名和写入策略", "检查链路、字段和风险提示"][step]}</p></div></div><div className="panel-body">
      {step === 0 && <div className="form-grid"><div className="field full"><label>任务名称</label><input value={form.name} onChange={(e) => update("name", e.target.value)} placeholder="例如：订单明细全量同步" /></div><div className="field full"><label>描述</label><textarea value={form.description} onChange={(e) => update("description", e.target.value)} placeholder="说明同步用途和数据负责人" /></div><div className="field"><label>并行度</label><input type="number" min={1} max={32} value={form.parallelism} onChange={(e) => update("parallelism", Number(e.target.value))} /><small>首版建议 1–4。</small></div><div className="field"><label>批大小</label><input type="number" min={1} max={100000} value={form.batchSize} onChange={(e) => update("batchSize", Number(e.target.value))} /></div></div>}
      {step === 1 && <><div className="form-grid"><div className="field"><label>源数据源</label><select value={form.sourceDataSourceId} onChange={(e) => selectSource(e.target.value)}><option value="">请选择</option>{sources.map((item) => <option value={item.id} key={item.id}>{item.name} · {item.database}</option>)}</select></div><div className="field"><label>源表</label><select disabled={!form.sourceDataSourceId || loadingMeta} value={form.sourceTable} onChange={(e) => selectTable(e.target.value)}><option value="">{loadingMeta ? "读取中…" : "请选择"}</option>{tables.map((item) => <option key={item.name} value={item.name}>{item.name}</option>)}</select></div></div>{columns.length > 0 && <Schema columns={columns} />}</>}
      {step === 2 && <div className="form-grid"><div className="field"><label>目标数据源</label><select value={form.targetDataSourceId} onChange={(e) => update("targetDataSourceId", e.target.value)}><option value="">请选择</option>{sources.map((item) => <option value={item.id} key={item.id}>{item.name} · {item.database}</option>)}</select></div><div className="field"><label>目标表</label><input value={form.targetTable} onChange={(e) => update("targetTable", e.target.value)} placeholder="target_table" /></div><div className="field full"><label>写入策略</label><div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>{(["REPLACE", "APPEND"] as WriteMode[]).map((mode) => <button type="button" key={mode} onClick={() => update("writeMode", mode)} className={`summary-card ${form.writeMode === mode ? "selected-card" : ""}`} style={{ textAlign: "left", cursor: "pointer", outline: form.writeMode === mode ? "2px solid #5b5ce2" : "none" }}><strong>{mode === "REPLACE" ? "清空后重写" : "追加写入"}</strong><p style={{ margin: "7px 0 0", color: "#667085", fontSize: 10 }}>{mode === "REPLACE" ? "运行前清空目标表，适合可重复的全量同步。" : "保留已有数据，重复运行可能产生重复记录。"}</p></button>)}</div></div></div>}
      {step === 3 && <><div className={`alert ${form.writeMode === "REPLACE" ? "warning" : "info"}`} style={{ marginBottom: 16 }}><strong>{form.writeMode === "REPLACE" ? "清空重写" : "追加模式"}</strong><span>{form.writeMode === "REPLACE" ? "任务执行前将删除目标表已有数据，请确认目标表选择正确。" : "平台不提供 Upsert，重复运行可能产生重复数据。"}</span></div><Schema columns={columns} /></>}
      <div className="form-actions"><button className="button" disabled={step === 0} onClick={() => setStep(step - 1)}>上一步</button><button className="button primary" disabled={!valid || saving} onClick={next}>{saving ? "创建中…" : step === 3 ? "创建任务" : <>下一步<ArrowRight size={13} /></>}</button></div>
    </div></section><aside className="summary-card"><h3>任务摘要</h3><div className="summary-line"><span>任务</span><strong>{form.name || "未命名"}</strong></div><div className="summary-line"><span>源端</span><strong>{source ? `${source.name}.${form.sourceTable || "?"}` : "未选择"}</strong></div><div className="summary-line"><span>目标端</span><strong>{target ? `${target.name}.${form.targetTable || "?"}` : "未选择"}</strong></div><div className="summary-line"><span>字段</span><strong>{columns.length || "—"}</strong></div><div className="summary-line"><span>策略</span><strong>{form.writeMode === "REPLACE" ? "清空重写" : "追加"}</strong></div><div className="summary-line"><span>并行度</span><strong>{form.parallelism}</strong></div><div className="alert info" style={{ marginTop: 14 }}><KeyRound size={15} /><span>SeaTunnel 配置由服务端生成，凭据不会出现在页面预览。</span></div></aside></div>
  </>;
}

function Schema({ columns }: { columns: ColumnInfo[] }) { return <div style={{ marginTop: 18 }}><div className="schema-list"><div className="schema-row header"><span>字段</span><span>类型</span><span>约束</span></div>{columns.map((column) => <div className="schema-row" key={column.name}><span className="cell-title">{column.name}</span><span>{column.typeName}{column.size ? `(${column.size}${column.scale ? `,${column.scale}` : ""})` : ""}</span><span className={column.primaryKey ? "pk" : ""}>{column.primaryKey ? "PRIMARY" : column.nullable ? "NULL" : "NOT NULL"}</span></div>)}</div></div>; }
