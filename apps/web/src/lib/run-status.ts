import type { RunStatus } from "@/lib/types";

const terminalStatuses: ReadonlySet<RunStatus> = new Set([
  "SUCCEEDED",
  "FAILED",
  "CANCELED",
  "UNKNOWN",
]);

export function isTerminalStatus(status: RunStatus): boolean {
  return terminalStatuses.has(status);
}
