"use client";

import { Check, X } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { EngineProfile } from "@/lib/types";
import { EngineBadge } from "./EngineBadge";
import { ErrorBanner, LoadingState } from "./ui";

export function RunEngineDialog({ jobName, busy, onClose, onRun }: {
  jobName: string;
  busy: boolean;
  onClose: () => void;
  onRun: (profileId: string) => void;
}) {
  const [profiles, setProfiles] = useState<EngineProfile[]>();
  const [selected, setSelected] = useState("zeta-local");
  const [error, setError] = useState("");
  useEffect(() => { api.engineProfiles().then(setProfiles).catch((reason) => setError(reason.message)); }, []);
  const current = profiles?.find((profile) => profile.id === selected);
  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && !busy && onClose()}>
    <div className="modal engine-modal">
      <div className="modal-header"><div><h2>选择执行引擎</h2><p>本次运行「{jobName}」；任务定义本身不绑定引擎。</p></div><button aria-label="关闭" className="button ghost small" disabled={busy} onClick={onClose}><X size={16} /></button></div>
      <div className="modal-body">
        {error && <ErrorBanner message={error} />}
        {!profiles ? <LoadingState /> : <div className="engine-options">{profiles.map((profile) => {
          const selectable = profile.enabled && profile.online;
          const compatible = profile.capabilities.includes("MYSQL_JDBC_BATCH_SINGLE_TABLE");
          return <button type="button" className={`engine-option ${selected === profile.id ? "selected" : ""}`} disabled={!selectable || busy} key={profile.id} onClick={() => setSelected(profile.id)}>
            <span className="engine-option-check">{selected === profile.id && <Check size={13} />}</span>
            <span><strong>{profile.name}</strong><small>{profile.message}</small><small>MySQL JDBC 单表批量 · {compatible ? "兼容" : "不兼容"}</small></span>
            <EngineBadge engine={profile.engineType} mock={profile.mock} />
          </button>;
        })}</div>}
        {current?.mock && <div className="alert warning" style={{ marginTop: 14 }}><strong>模拟执行</strong><span>用于验证多引擎流程，不会向公司 Spark 集群提交任务。</span></div>}
        <div className="form-actions"><button className="button" disabled={busy} onClick={onClose}>取消</button><button className="button primary" disabled={!current?.enabled || !current.online || busy} onClick={() => onRun(selected)}>{busy ? "提交中…" : "确认运行"}</button></div>
      </div>
    </div>
  </div>;
}
