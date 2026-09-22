package io.golem.datasync.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sync_run")
public class SyncRunEntity {
    @TableId public String id;
    public String jobId;
    public String seatunnelJobId;
    public String status;
    public Long sourceReadCount;
    public Long sinkWriteCount;
    public Double sourceQps;
    public Double sinkQps;
    public Long sourceBytes;
    public Long sinkBytes;
    public String errorMessage;
    public String configSnapshot;
    public LocalDateTime staleSince;
    public String lastPollError;
    public LocalDateTime startedAt;
    public LocalDateTime finishedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public String getJobId() { return jobId; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
}
