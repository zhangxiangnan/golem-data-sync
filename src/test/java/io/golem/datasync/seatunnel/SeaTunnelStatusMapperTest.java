package io.golem.datasync.seatunnel;

import static org.assertj.core.api.Assertions.assertThat;

import io.golem.datasync.domain.RunStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SeaTunnelStatusMapperTest {
    @ParameterizedTest
    @CsvSource({
        "INITIALIZING,PENDING", "PENDING,PENDING", "SCHEDULED,PENDING", "RUNNING,RUNNING",
        "CANCELING,STOPPING", "CANCELED,CANCELED", "FINISHED,SUCCEEDED", "FAILED,FAILED", "UNKNOWABLE,UNKNOWN"
    })
    void mapsEngineStates(String engine, RunStatus platform) {
        assertThat(SeaTunnelStatusMapper.map(engine)).isEqualTo(platform);
    }
}
