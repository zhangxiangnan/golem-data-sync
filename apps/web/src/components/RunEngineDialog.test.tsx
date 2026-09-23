import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { RunEngineDialog } from "./RunEngineDialog";

describe("RunEngineDialog", () => {
  it("shows real, mock and disabled profiles and submits the selected profile", async () => {
    vi.spyOn(api, "engineProfiles").mockResolvedValue([
      { id: "zeta-local", name: "本机 Zeta", engineType: "ZETA", enabled: true, mock: false, online: true, message: "正常", capabilities: ["MYSQL_JDBC_BATCH_SINGLE_TABLE"] },
      { id: "spark-local-mock", name: "Spark 本地模拟", engineType: "SPARK", enabled: true, mock: true, online: true, message: "模拟", capabilities: ["MYSQL_JDBC_BATCH_SINGLE_TABLE"] },
      { id: "flink-unconfigured", name: "Flink（未配置）", engineType: "FLINK", enabled: false, mock: false, online: false, message: "未配置", capabilities: [] },
    ]);
    const onRun = vi.fn();
    render(<RunEngineDialog jobName="订单同步" busy={false} onClose={vi.fn()} onRun={onRun} />);

    await screen.findByText("Spark 本地模拟");
    expect(screen.getAllByText("MySQL JDBC 单表批量 · 兼容")).toHaveLength(2);
    expect(screen.getByText("Flink（未配置）").closest("button")).toBeDisabled();
    fireEvent.click(screen.getByText("Spark 本地模拟"));
    expect(screen.getByText("模拟执行")).toBeInTheDocument();
    fireEvent.click(screen.getByText("确认运行"));
    await waitFor(() => expect(onRun).toHaveBeenCalledWith("spark-local-mock"));
  });
});
