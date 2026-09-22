package io.golem.datasync.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("data_source_config")
public class DataSourceConfigEntity {
    @TableId public String id;
    public String name;
    public String type;
    public String host;
    public Integer port;
    public String databaseName;
    public String username;
    public String encryptedPassword;
    public String status;
    public LocalDateTime lastTestAt;
    public String lastError;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public String getName() { return name; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
