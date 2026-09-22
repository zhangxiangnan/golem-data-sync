package io.golem.datasync.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sync_job")
public class SyncJobEntity {
    @TableId public String id;
    public String name;
    public String description;
    public String sourceDataSourceId;
    public String sourceTable;
    public String targetDataSourceId;
    public String targetTable;
    public String writeMode;
    public Integer parallelism;
    public Integer batchSize;
    public Boolean archived;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public String getName() { return name; }
    public String getSourceDataSourceId() { return sourceDataSourceId; }
    public String getTargetDataSourceId() { return targetDataSourceId; }
    public Boolean getArchived() { return archived; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
