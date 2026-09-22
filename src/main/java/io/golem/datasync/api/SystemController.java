package io.golem.datasync.api;

import io.golem.datasync.api.ApiModels.DashboardSummary;
import io.golem.datasync.api.ApiModels.SystemStatusResponse;
import io.golem.datasync.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemController {
    private final DashboardService service;

    public SystemController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard/summary")
    public DashboardSummary summary() { return service.summary(); }

    @GetMapping("/system/status")
    public SystemStatusResponse systemStatus() { return service.systemStatus(); }
}
