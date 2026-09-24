"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ArrowLeft, Database, RefreshCw, X } from "lucide-react";
import { EmptyState, ErrorBanner, LoadingState, PageHeader, formatNumber } from "./ui";
import { api } from "@/lib/api";
import type { ColumnInfo, DataSource, TableInfo, TableRows } from "@/lib/types";

type Props = { sourceId: string; tableName?: string };

export function DataBrowser(props: Props) {
  return <BrowserContent key={`${props.sourceId}:${props.tableName ?? ""}`} {...props} />;
}

function BrowserContent({ sourceId, tableName }: Props) {
  const router = useRouter();
  const [sources, setSources] = useState<DataSource[]>();
  const [tables, setTables] = useState<TableInfo[]>();
  const [listError, setListError] = useState("");
  const [tab, setTab] = useState<"data" | "schema">("data");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<TableRows>();
  const [columns, setColumns] = useState<ColumnInfo[]>();
  const [error, setError] = useState("");
  const [expanded, setExpanded] = useState<string>();
  const table = tableName ?? tables?.[0]?.name ?? "";
  const missingTable = !!table && !!tables && !tables.some((item) => item.name === table);
  const source = sources?.find((item) => item.id === sourceId);

  useEffect(() => {
    let active = true;
    setListError("");
    Promise.allSettled([api.dataSources(), api.tables(sourceId)])
      .then(([nextSources, nextTables]) => {
        if (!active) return;
        if (nextSources.status === "fulfilled") setSources(nextSources.value);
        if (nextTables.status === "fulfilled") setTables(nextTables.value);
        const failure = nextSources.status === "rejected" ? nextSources : nextTables.status === "rejected" ? nextTables : undefined;
        if (failure) setListError(failure.reason instanceof Error ? failure.reason.message : "数据源加载失败");
      });
    return () => { active = false; };
  }, [sourceId, revision]);

  useEffect(() => {
    let active = true;
    setResult(undefined); setColumns(undefined); setError("");
    if (!table || !tables || missingTable) return;
    if (tab === "data") {
      api.tableRows(sourceId, table, page, pageSize)
        .then((value) => { if (active) setResult(value); })
        .catch((reason) => { if (active) setError(reason instanceof Error ? reason.message : "读取数据失败"); });
    } else {
      api.columns(sourceId, table)
        .then((value) => { if (active) setColumns(value); })
        .catch((reason) => { if (active) setError(reason instanceof Error ? reason.message : "读取结构失败"); });
    }
    return () => { active = false; };
  }, [sourceId, table, tables, missingTable, tab, page, pageSize, revision]);

  useEffect(() => {
    if (expanded === undefined) return;
    const close = (event: KeyboardEvent) => { if (event.key === "Escape") setExpanded(undefined); };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [expanded]);

  function refresh() {
    setResult(undefined); setColumns(undefined); setTables(undefined); setRevision((value) => value + 1);
  }
  function chooseTable(name: string) {
    router.push(`/sources/${sourceId}/browse?table=${encodeURIComponent(name)}`);
  }
  const loading = !listError && (!tables || (!!table && !missingTable && !error && !(tab === "data" ? result : columns)));
  const lastPage = Math.max(1, Math.ceil((result?.total ?? 0) / pageSize));

  return <>
    <Link className="eyebrow" href="/sources"><ArrowLeft size={12} style={{ display: "inline" }} /> 返回数据源</Link>
    <PageHeader eyebrow="Data browser" title="数据浏览" description="查看 MySQL 表数据与字段结构，核对同步结果。只读浏览，不修改数据。"
      action={<button className="button" disabled={loading} onClick={refresh}><RefreshCw size={14} />刷新</button>} />
    <div className="browse-toolbar panel">
      <div className="field"><label htmlFor="browse-source">数据源</label><select id="browse-source" value={sourceId} onChange={(e) => router.push(`/sources/${e.target.value}/browse`)}>
        {!sources?.length && <option value={sourceId}>当前数据源</option>}
        {sources?.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.database}</option>)}
      </select></div>
      {source && <span className="cell-sub"><Database size={13} /> {source.host}:{source.port} / {source.database}</span>}
    </div>
    {listError ? <ErrorBanner message={listError} /> : !tables ? <LoadingState /> : <div className="browse-layout">
      <aside className="panel browse-tables"><div className="panel-header"><h2>数据表</h2><span>{tables.length}</span></div>
        <nav aria-label="数据表">{tables.map((item) => <button key={item.name} className={`browse-table ${table === item.name ? "selected" : ""}`} aria-current={table === item.name ? "page" : undefined} onClick={() => chooseTable(item.name)}><Database size={14} /><span>{item.name}</span></button>)}</nav>
        {tables.length === 0 && <p className="browse-note">此数据库暂无数据表。</p>}
      </aside>
      <section className="panel browse-content">
        <div className="panel-header"><div><h2>{table || "选择数据表"}</h2><p>{source?.database ?? ""}{table ? ` / ${table}` : ""}</p></div><span className="db-pill">只读</span></div>
        <div className="tabs" role="tablist" aria-label="浏览内容"><button role="tab" aria-selected={tab === "data"} className={`tab ${tab === "data" ? "active" : ""}`} onClick={() => setTab("data")}>数据</button><button role="tab" aria-selected={tab === "schema"} className={`tab ${tab === "schema" ? "active" : ""}`} onClick={() => setTab("schema")}>表结构</button></div>
        {!table ? <EmptyState title="暂无数据表" description="当前数据源没有可浏览的表。" /> : missingTable ? <div className="panel-body"><ErrorBanner message={`表不存在：${table}，请从左侧选择其他表。`} /></div> : error ? <div className="panel-body"><ErrorBanner message={error} /></div> : tab === "schema" ? columns ? <div className="browse-scroll"><table className="data-table"><thead><tr><th>字段名</th><th>类型</th><th>主键</th><th>允许 NULL</th></tr></thead><tbody>{columns.map((column) => <tr key={column.name}><td><code>{column.name}</code></td><td>{column.typeName}{column.size != null && [1,12,-1,2,3].includes(column.jdbcType) ? `(${column.size}${column.scale != null ? `,${column.scale}` : ""})` : ""}</td><td>{column.primaryKey ? "是" : "否"}</td><td>{column.nullable ? "是" : "否"}</td></tr>)}</tbody></table></div> : <LoadingState /> : result ? <>
          {!result.orderedByPrimaryKey && <p className="browse-note">此表没有主键，翻页或刷新时记录顺序可能变化。</p>}
          <div className="browse-scroll"><table className="data-table browse-data"><thead><tr>{result.columns.map((column) => <th key={column.name} title={column.typeName}>{column.name}{column.primaryKey && <span className="pk"> · PK</span>}</th>)}</tr></thead><tbody>{result.rows.map((row, index) => <tr key={index}>{row.map((value, cell) => <td key={cell}>{value === null ? <span className="browse-null">NULL</span> : value === "" ? <span className="browse-null">空字符串</span> : value.length > 80 || /[\r\n]/.test(value) ? <button className="browse-cell" aria-label={`展开 ${result.columns[cell].name} 第 ${(page - 1) * pageSize + index + 1} 行`} onClick={() => setExpanded(value)}>{value.slice(0, 80)}…</button> : <span className="browse-value">{value}</span>}</td>)}</tr>)}</tbody></table></div>
          {result.rows.length === 0 && <EmptyState title={result.total === 0 ? "表中暂无数据" : "当前页暂无数据"} description={result.total === 0 ? "同步任务写入后，点击刷新即可查看。" : "数据可能已变化，请返回上一页或刷新。"} />}
          <div className="browse-pagination"><span>共 {formatNumber(result.total)} 条 · 第 {page} / {lastPage} 页</span><div className="actions"><label htmlFor="browse-size">每页</label><select id="browse-size" value={pageSize} onChange={(e) => { setResult(undefined); setPageSize(Number(e.target.value)); setPage(1); }}>{[20,50,100].map((size) => <option key={size} value={size}>{size}</option>)}</select><button className="button small" disabled={page <= 1} onClick={() => { setResult(undefined); setPage((value) => value - 1); }}>上一页</button><button className="button small" disabled={page >= lastPage} onClick={() => { setResult(undefined); setPage((value) => value + 1); }}>下一页</button></div></div>
          <p className="browse-note">{result.orderedByPrimaryKey ? "按主键升序。" : ""}数据为查询时的结果，翻页期间源表可能变化。</p>
        </> : <LoadingState />}
      </section>
    </div>}
    {expanded !== undefined && <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && setExpanded(undefined)}><div className="modal" role="dialog" aria-modal="true" aria-label="单元格内容"><div className="modal-header"><h2>单元格内容</h2><button autoFocus className="button ghost small" aria-label="关闭" onClick={() => setExpanded(undefined)}><X size={16} /></button></div><pre className="browse-expanded">{expanded}</pre></div></div>}
  </>;
}
