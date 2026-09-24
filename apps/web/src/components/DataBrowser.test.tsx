import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DataBrowser } from "./DataBrowser";
import { api } from "@/lib/api";
import type { DataSource, TableRows } from "@/lib/types";

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
vi.mock("@/lib/api", () => ({ api: { dataSources: vi.fn(), tables: vi.fn(), columns: vi.fn(), tableRows: vi.fn() } }));
const columns = [
  { name: "id", typeName: "BIGINT", jdbcType: -5, nullable: false, primaryKey: true, ordinal: 1 },
  { name: "remark", typeName: "VARCHAR", jdbcType: 12, nullable: true, primaryKey: false, ordinal: 2 },
];
const response: TableRows = { columns, rows: [["9223372036854775807", null], ["2", ""]], total: 120, page: 1, pageSize: 50, orderedByPrimaryKey: true };

afterEach(cleanup);

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.dataSources).mockResolvedValue([
    { id: "source", name: "测试源库", database: "source_db", host: "127.0.0.1", port: 3306 },
    { id: "target", name: "测试目标库", database: "target_db", host: "127.0.0.1", port: 3306 },
  ] as DataSource[]);
  vi.mocked(api.tables).mockResolvedValue([{ name: "orders", type: "TABLE" }, { name: "customers", type: "TABLE" }]);
  vi.mocked(api.columns).mockResolvedValue(columns);
  vi.mocked(api.tableRows).mockResolvedValue(response);
});

describe("DataBrowser", () => {
  it("shows exact values, nulls, schema, and pagination", async () => {
    render(<DataBrowser sourceId="source" tableName="orders" />);
    expect(await screen.findByText("9223372036854775807")).toBeInTheDocument();
    expect(screen.getByText("NULL")).toBeInTheDocument();
    expect(screen.getByText("空字符串")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "下一页" }));
    await waitFor(() => expect(api.tableRows).toHaveBeenLastCalledWith("source", "orders", 2, 50));
    await screen.findByText("共 120 条 · 第 2 / 3 页");
    fireEvent.change(screen.getByLabelText("每页"), { target: { value: "100" } });
    await waitFor(() => expect(api.tableRows).toHaveBeenLastCalledWith("source", "orders", 1, 100));
    fireEvent.click(screen.getByRole("tab", { name: "表结构" }));
    expect(await screen.findByText("BIGINT")).toBeInTheDocument();
    expect(api.columns).toHaveBeenCalledWith("source", "orders");
  });

  it("navigates between sources and tables and resets pagination", async () => {
    const { rerender } = render(<DataBrowser sourceId="source" tableName="orders" />);
    await screen.findByText("9223372036854775807");
    fireEvent.click(screen.getByRole("button", { name: "下一页" }));
    await screen.findByText("共 120 条 · 第 2 / 3 页");
    fireEvent.click(screen.getByRole("button", { name: "customers" }));
    expect(push).toHaveBeenCalledWith("/sources/source/browse?table=customers");
    rerender(<DataBrowser sourceId="source" tableName="customers" />);
    await waitFor(() => expect(api.tableRows).toHaveBeenLastCalledWith("source", "customers", 1, 50));
    fireEvent.change(screen.getByLabelText("数据源"), { target: { value: "target" } });
    expect(push).toHaveBeenCalledWith("/sources/target/browse");
    rerender(<DataBrowser sourceId="target" tableName="orders" />);
    await waitFor(() => expect(api.tableRows).toHaveBeenLastCalledWith("target", "orders", 1, 50));
  });

  it("refreshes data and handles empty tables and empty databases", async () => {
    vi.mocked(api.tableRows).mockResolvedValue({ ...response, rows: [], total: 0 });
    render(<DataBrowser sourceId="source" />);
    expect(await screen.findByText("表中暂无数据")).toBeInTheDocument();
    const before = vi.mocked(api.tableRows).mock.calls.length;
    fireEvent.click(screen.getByRole("button", { name: "刷新" }));
    await waitFor(() => expect(vi.mocked(api.tableRows).mock.calls.length).toBeGreaterThan(before));
    vi.mocked(api.tables).mockResolvedValue([]);
    await screen.findByText("表中暂无数据");
    fireEvent.click(screen.getByRole("button", { name: "刷新" }));
    expect(await screen.findByText("暂无数据表")).toBeInTheDocument();
  });

  it("shows a missing table without querying another table", async () => {
    render(<DataBrowser sourceId="source" tableName="missing" />);
    expect(await screen.findByText("表不存在：missing，请从左侧选择其他表。")).toBeInTheDocument();
    expect(api.tableRows).not.toHaveBeenCalled();
  });

  it("keeps source switching available when a connection fails", async () => {
    vi.mocked(api.tables).mockRejectedValue(new Error("数据库连接失败"));
    render(<DataBrowser sourceId="source" />);
    expect(await screen.findByText("数据库连接失败")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "测试目标库 · target_db" })).toBeInTheDocument();
  });

  it("allows retrying a failed row query", async () => {
    vi.mocked(api.tableRows).mockRejectedValueOnce(new Error("读取表数据超时"));
    render(<DataBrowser sourceId="source" tableName="orders" />);
    expect(await screen.findByText("读取表数据超时")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "刷新" }));
    expect(await screen.findByText("9223372036854775807")).toBeInTheDocument();
  });

  it("ignores an old response after switching tables", async () => {
    let resolve!: (value: TableRows) => void;
    vi.mocked(api.tableRows).mockImplementation((_id, table) => table === "orders"
      ? new Promise<TableRows>((done) => { resolve = done; })
      : Promise.resolve({ ...response, rows: [["new customer", "ok"]] }));
    const { rerender } = render(<DataBrowser sourceId="source" tableName="orders" />);
    await waitFor(() => expect(api.tableRows).toHaveBeenCalled());
    rerender(<DataBrowser sourceId="source" tableName="customers" />);
    await screen.findByText("new customer");
    await act(async () => resolve(response));
    expect(screen.getByText("new customer")).toBeInTheDocument();
    expect(screen.queryByText("9223372036854775807")).not.toBeInTheDocument();
  });

  it("expands long content and warns for tables without primary keys", async () => {
    const text = "中文备注".repeat(30);
    vi.mocked(api.tableRows).mockResolvedValue({ ...response, rows: [["1", text]], orderedByPrimaryKey: false });
    render(<DataBrowser sourceId="source" />);
    fireEvent.click(await screen.findByRole("button", { name: "展开 remark 第 1 行" }));
    expect(screen.getByRole("dialog")).toHaveTextContent(text);
    expect(screen.getByText("此表没有主键，翻页或刷新时记录顺序可能变化。")).toBeInTheDocument();
    fireEvent.keyDown(window, { key: "Escape" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
