package io.golem.datasync.engine;

import io.golem.datasync.api.RequestValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EngineRegistry {
    private final Map<String, JobEngineClient> clients;

    public EngineRegistry(List<JobEngineClient> clients) {
        Map<String, JobEngineClient> indexed = new LinkedHashMap<>();
        for (JobEngineClient client : clients) {
            if (indexed.put(client.profileId(), client) != null) {
                throw new IllegalStateException("Duplicate engine profile: " + client.profileId());
            }
        }
        this.clients = Map.copyOf(indexed);
    }

    public JobEngineClient require(String profileId) {
        String resolved = profileId == null || profileId.isBlank() ? "zeta-local" : profileId;
        JobEngineClient client = clients.get(resolved);
        if (client == null) {
            throw new RequestValidationException("Unknown or disabled engine profile: " + resolved);
        }
        return client;
    }

    public List<JobEngineClient> clients() {
        return List.copyOf(clients.values());
    }
}
