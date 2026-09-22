package io.golem.datasync.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sync_run_event")
public class SyncRunEventEntity {
    @TableId public String id;
    public String runId;
    public String status;
    public String message;
    public LocalDateTime createdAt;

    public String getRunId() { return runId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
