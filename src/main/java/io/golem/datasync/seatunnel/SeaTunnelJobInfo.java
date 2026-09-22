package io.golem.datasync.seatunnel;

public record SeaTunnelJobInfo(
        String jobId,
        String jobName,
        String status,
        long sourceReadCount,
        long sinkWriteCount,
        double sourceQps,
        double sinkQps,
        long sourceBytes,
        long sinkBytes,
        String errorMessage) {}
