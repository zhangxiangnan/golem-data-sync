export type DataSourceStatus = "UNKNOWN" | "AVAILABLE" | "UNAVAILABLE";
export type RunStatus = "SUBMITTING" | "PENDING" | "RUNNING" | "STOPPING" | "SUCCEEDED" | "FAILED" | "CANCELED" | "UNKNOWN";
export type WriteMode = "APPEND" | "REPLACE";

export interface DataSource {
  id: string; name: string; type: string; host: string; port: number; database: string; username: string;
  passwordConfigured: boolean; status: DataSourceStatus; lastTestAt?: string; lastError?: string;
  createdAt: string; updatedAt: string;
}

export interface DataSourceInput {
  name: string; host: string; port: number; database: string; username: string; password?: string;
}

export interface TableInfo { name: string; type: string }
export interface ColumnInfo {
  name: string; typeName: string; jdbcType: number; size?: number; scale?: number;
  nullable: boolean; primaryKey: boolean; ordinal: number;
}

export interface RunSummary {
  id: string; status: RunStatus; sourceReadCount: number; sinkWriteCount: number;
  startedAt?: string; finishedAt?: string;
}

export interface SyncJob {
  id: string; name: string; description?: string; sourceDataSourceId: string; sourceDataSourceName: string;
  sourceTable: string; targetDataSourceId: string; targetDataSourceName: string; targetTable: string;
  writeMode: WriteMode; parallelism: number; batchSize: number; archived: boolean;
  latestRun?: RunSummary; createdAt: string; updatedAt: string;
}

export interface SyncJobInput {
  name: string; description?: string; sourceDataSourceId: string; sourceTable: string;
  targetDataSourceId: string; targetTable: string; writeMode: WriteMode; parallelism: number; batchSize: number;
}

export interface ValidationIssue { level: "INFO" | "WARNING" | "ERROR"; code: string; message: string }
export interface JobValidation { valid: boolean; issues: ValidationIssue[]; sourceColumns: ColumnInfo[] }
export interface RunEvent { id: string; status: RunStatus; message: string; createdAt: string }

export interface SyncRun {
  id: string; jobId: string; jobName: string; seatunnelJobId?: string; status: RunStatus;
  sourceReadCount: number; sinkWriteCount: number; sourceQps: number; sinkQps: number;
  sourceBytes: number; sinkBytes: number; errorMessage?: string; stale: boolean; staleSince?: string;
  lastPollError?: string; startedAt?: string; finishedAt?: string; createdAt: string; updatedAt: string;
  events: RunEvent[];
}

export interface DashboardSummary {
  dataSourceCount: number; jobCount: number; runningCount: number; todaySucceeded: number; todayFailed: number;
  successRate: number; recentRuns: SyncRun[];
}

export interface SystemStatus {
  engineOnline: boolean; engineBaseUrl: string; engineVersion?: string; message: string; checkedAt: string;
}
