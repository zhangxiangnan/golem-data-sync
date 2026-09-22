# Architecture

`golem-data-sync` is a local single-user control plane. Apache SeaTunnel Zeta 2.3.13 runs as a separate process and remains the data plane.

```text
Next.js :3200 -> Spring Boot :8090 -> SeaTunnel REST :8081 -> MySQL source / target
                              \-> H2 metadata store
```

The platform never accepts arbitrary SeaTunnel configuration. A typed sync job is validated and converted to deterministic JSON on the server. Credentials are encrypted at rest and redacted from responses, snapshots and application logs.

## Run lifecycle

`SUBMITTING -> PENDING -> RUNNING -> SUCCEEDED | FAILED | CANCELED | UNKNOWN`

Stopping transitions through `STOPPING`. Transport failures do not change the last known engine state; the run is marked stale until reconciliation succeeds again.
