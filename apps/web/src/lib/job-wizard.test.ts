import { describe, expect, it } from "vitest";
import type { SyncJobInput } from "./types";
import { canContinueJobWizard } from "./job-wizard";

const complete: SyncJobInput = {
  name: "订单同步",
  description: "",
  sourceDataSourceId: "source",
  sourceTable: "orders",
  targetDataSourceId: "target",
  targetTable: "orders_copy",
  writeMode: "REPLACE",
  parallelism: 1,
  batchSize: 1000,
};

describe("canContinueJobWizard", () => {
  it("requires a valid name and runtime parameters on the first step", () => {
    expect(canContinueJobWizard(0, { ...complete, name: " " })).toBe(false);
    expect(canContinueJobWizard(0, { ...complete, parallelism: 0 })).toBe(false);
    expect(canContinueJobWizard(0, complete)).toBe(true);
  });

  it("requires source and target selections on their respective steps", () => {
    expect(canContinueJobWizard(1, { ...complete, sourceTable: "" })).toBe(false);
    expect(canContinueJobWizard(2, { ...complete, targetDataSourceId: "" })).toBe(false);
    expect(canContinueJobWizard(1, complete)).toBe(true);
    expect(canContinueJobWizard(2, complete)).toBe(true);
  });
});
