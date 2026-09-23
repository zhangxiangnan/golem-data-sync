package io.golem.datasync.engine;

public enum EngineCapability {
    MYSQL_JDBC_BATCH_SINGLE_TABLE,
    AUTO_CREATE_TARGET,
    APPEND,
    REPLACE,
    STOP,
    METRICS,
    TIMER_FLUSH
}
