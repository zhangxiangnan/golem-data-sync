import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StatusBadge } from "./StatusBadge";

describe("StatusBadge", () => {
  it("renders localized run status", () => {
    render(<StatusBadge status="RUNNING" />);
    expect(screen.getByText("运行中")).toHaveClass("status-running");
  });

  it("renders data source health", () => {
    render(<StatusBadge status="UNAVAILABLE" />);
    expect(screen.getByText("不可用")).toBeInTheDocument();
  });
});
