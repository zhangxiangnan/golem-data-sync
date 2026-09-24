import type { ColumnInfo, DashboardSummary, DataSource, DataSourceInput, EngineCapability, EngineProfile, JobValidation, SyncJob, SyncJobInput, SyncRun, SystemStatus, TableInfo, TableRows } from "./types";

export class ApiError extends Error {
  constructor(message: string, public status: number) { super(message); }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
    cache: "no-store",
  });
  if (!response.ok) {
    let message = `请求失败 (${response.status})`;
    try { const body = await response.json(); message = body.detail ?? body.title ?? message; } catch { /* noop */ }
    throw new ApiError(message, response.status);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  dashboard: () => request<DashboardSummary>("/api/dashboard/summary"),
  systemStatus: () => request<SystemStatus>("/api/system/status"),
  engineProfiles: () => request<EngineProfile[]>("/api/engine-profiles"),
  engineCapabilities: () => request<EngineCapability[]>("/api/engine-capabilities"),
  dataSources: () => request<DataSource[]>("/api/data-sources"),
  createDataSource: (body: DataSourceInput) => request<DataSource>("/api/data-sources", { method: "POST", body: JSON.stringify(body) }),
  updateDataSource: (id: string, body: DataSourceInput) => request<DataSource>(`/api/data-sources/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  deleteDataSource: (id: string) => request<void>(`/api/data-sources/${id}`, { method: "DELETE" }),
  testDataSource: (id: string) => request<{ success: boolean; message: string; latencyMs: number }>(`/api/data-sources/${id}/test`, { method: "POST" }),
  tables: (id: string) => request<TableInfo[]>(`/api/data-sources/${id}/tables`),
  columns: (id: string, table: string) => request<ColumnInfo[]>(`/api/data-sources/${id}/tables/${encodeURIComponent(table)}/columns`),
  tableRows: (id: string, table: string, page = 1, pageSize = 50) => request<TableRows>(`/api/data-sources/${id}/tables/${encodeURIComponent(table)}/rows?page=${page}&pageSize=${pageSize}`),
  jobs: () => request<SyncJob[]>("/api/sync-jobs"),
  job: (id: string) => request<SyncJob>(`/api/sync-jobs/${id}`),
  createJob: (body: SyncJobInput) => request<SyncJob>("/api/sync-jobs", { method: "POST", body: JSON.stringify(body) }),
  updateJob: (id: string, body: SyncJobInput) => request<SyncJob>(`/api/sync-jobs/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  archiveJob: (id: string) => request<void>(`/api/sync-jobs/${id}`, { method: "DELETE" }),
  validateJob: (id: string) => request<JobValidation>(`/api/sync-jobs/${id}/validate`, { method: "POST" }),
  configPreview: (id: string, engineProfileId = "zeta-local") => request<{ format: string; content: string }>(`/api/sync-jobs/${id}/config-preview?engineProfileId=${encodeURIComponent(engineProfileId)}`),
  startRun: (id: string, engineProfileId = "zeta-local") => request<SyncRun>(`/api/sync-jobs/${id}/runs`, { method: "POST", body: JSON.stringify({ engineProfileId }) }),
  runs: (jobId?: string, limit = 50) => request<SyncRun[]>(`/api/sync-runs?limit=${limit}${jobId ? `&jobId=${jobId}` : ""}`),
  run: (id: string) => request<SyncRun>(`/api/sync-runs/${id}`),
  stopRun: (id: string) => request<SyncRun>(`/api/sync-runs/${id}/stop`, { method: "POST" }),
};
