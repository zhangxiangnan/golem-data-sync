import { describe, expect, it } from "vitest";
import { isTerminalStatus } from "./run-status";

describe("isTerminalStatus", () => {
  it.each(["SUCCEEDED", "FAILED", "CANCELED", "UNKNOWN"] as const)("treats %s as terminal", (status) => {
    expect(isTerminalStatus(status)).toBe(true);
  });

  it.each(["SUBMITTING", "PENDING", "RUNNING", "STOPPING"] as const)("keeps polling for %s", (status) => {
    expect(isTerminalStatus(status)).toBe(false);
  });
});
