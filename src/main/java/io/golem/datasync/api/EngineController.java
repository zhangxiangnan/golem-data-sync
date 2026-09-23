package io.golem.datasync.api;

import io.golem.datasync.api.ApiModels.EngineCapabilityResponse;
import io.golem.datasync.api.ApiModels.EngineProfileResponse;
import io.golem.datasync.service.EngineCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EngineController {
    private final EngineCatalogService service;

    public EngineController(EngineCatalogService service) { this.service = service; }

    @GetMapping("/engine-profiles")
    public List<EngineProfileResponse> profiles() { return service.profiles(); }

    @GetMapping("/engine-capabilities")
    public List<EngineCapabilityResponse> capabilities() { return service.capabilities(); }
}
