package io.golem.datasync.api;

import io.golem.datasync.api.ApiModels.SyncRunResponse;
import io.golem.datasync.service.SyncRunService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync-runs")
public class SyncRunController {
    private final SyncRunService service;

    public SyncRunController(SyncRunService service) {
        this.service = service;
    }

    @GetMapping
    public List<SyncRunResponse> list(
            @RequestParam(required = false) String jobId,
            @RequestParam(defaultValue = "50") int limit) {
        return service.list(jobId, limit);
    }

    @GetMapping("/{id}")
    public SyncRunResponse get(@PathVariable String id) { return service.get(id); }

    @PostMapping("/{id}/stop")
    public SyncRunResponse stop(@PathVariable String id) { return service.stop(id); }
}
