import type { DataSourceStatus, RunStatus } from "@/lib/types";

const labels: Record<RunStatus | DataSourceStatus, string> = {
  SUBMITTING: "提交中", PENDING: "等待中", RUNNING: "运行中", STOPPING: "停止中",
  SUCCEEDED: "成功", FAILED: "失败", CANCELED: "已取消", UNKNOWN: "未知",
  AVAILABLE: "可用", UNAVAILABLE: "不可用",
};

export function StatusBadge({ status }: { status: RunStatus | DataSourceStatus }) {
  return <span className={`status-badge status-${status.toLowerCase()}`}><i />{labels[status]}</span>;
}
