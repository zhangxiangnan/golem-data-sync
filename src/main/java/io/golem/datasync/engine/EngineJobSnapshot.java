package io.golem.datasync.engine;

import io.golem.datasync.domain.RunStatus;

public record EngineJobSnapshot(
        RunStatus status,
        Long sourceReadCount,
        Long sinkWriteCount,
        Double sourceQps,
        Double sinkQps,
        Long sourceBytes,
        Long sinkBytes,
        String errorMessage) {}
