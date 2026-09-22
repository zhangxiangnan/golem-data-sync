package io.golem.datasync.domain;

import java.util.EnumSet;
import java.util.Set;

public enum RunStatus {
    SUBMITTING,
    PENDING,
    RUNNING,
    STOPPING,
    SUCCEEDED,
    FAILED,
    CANCELED,
    UNKNOWN;

    private static final Set<RunStatus> TERMINAL = EnumSet.of(SUCCEEDED, FAILED, CANCELED, UNKNOWN);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
