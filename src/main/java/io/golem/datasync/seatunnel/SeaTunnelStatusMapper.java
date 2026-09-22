package io.golem.datasync.seatunnel;

import io.golem.datasync.domain.RunStatus;
import java.util.Locale;

public final class SeaTunnelStatusMapper {
    private SeaTunnelStatusMapper() {}

    public static RunStatus map(String status) {
        if (status == null) {
            return RunStatus.UNKNOWN;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "INITIALIZING", "CREATED", "PENDING", "SCHEDULED", "DOING_SAVEPOINT" -> RunStatus.PENDING;
            case "RUNNING" -> RunStatus.RUNNING;
            case "FAILING" -> RunStatus.RUNNING;
            case "CANCELING" -> RunStatus.STOPPING;
            case "FINISHED", "SAVEPOINT_DONE" -> RunStatus.SUCCEEDED;
            case "FAILED" -> RunStatus.FAILED;
            case "CANCELED" -> RunStatus.CANCELED;
            default -> RunStatus.UNKNOWN;
        };
    }
}
