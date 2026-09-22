package io.golem.datasync.api;

import io.golem.datasync.api.ApiModels.ConfigPreviewResponse;
import io.golem.datasync.api.ApiModels.JobValidationResponse;
import io.golem.datasync.api.ApiModels.SyncJobRequest;
import io.golem.datasync.api.ApiModels.SyncJobResponse;
import io.golem.datasync.api.ApiModels.SyncRunResponse;
import io.golem.datasync.service.SyncJobService;
import io.golem.datasync.service.SyncRunService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync-jobs")
public class SyncJobController {
    private final SyncJobService jobService;
    private final SyncRunService runService;

    public SyncJobController(SyncJobService jobService, SyncRunService runService) {
        this.jobService = jobService;
        this.runService = runService;
    }

    @GetMapping
    public List<SyncJobResponse> list() { return jobService.list(); }

    @GetMapping("/{id}")
    public SyncJobResponse get(@PathVariable String id) { return jobService.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SyncJobResponse create(@Valid @RequestBody SyncJobRequest request) { return jobService.create(request); }

    @PutMapping("/{id}")
    public SyncJobResponse update(@PathVariable String id, @Valid @RequestBody SyncJobRequest request) {
        return jobService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable String id) { jobService.archive(id); }

    @PostMapping("/{id}/validate")
    public JobValidationResponse validate(@PathVariable String id) { return jobService.validate(id); }

    @GetMapping("/{id}/config-preview")
    public ConfigPreviewResponse configPreview(@PathVariable String id) { return jobService.configPreview(id); }

    @PostMapping("/{id}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public SyncRunResponse run(@PathVariable String id) { return runService.start(id); }
}
