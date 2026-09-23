import type { EngineType } from "@/lib/types";

const labels: Record<EngineType, string> = { ZETA: "Zeta", SPARK: "Spark", FLINK: "Flink" };

export function EngineBadge({ engine, mock = false }: { engine: EngineType; mock?: boolean }) {
  return <span className={`engine-badge engine-${engine.toLowerCase()}`}>{labels[engine]}{mock ? " · 模拟" : ""}</span>;
}
