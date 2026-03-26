# Architecture Document -- Route Quality Scoring Engine

## System Design

The engine is a Spring Boot monolith organized into three logical modules that form a processing pipeline:

```
[Module A: Data Generator] --> [Module B: Scoring Engine] --> [Module C: Verification & Billing]
```

**Module A** generates synthetic trips: `RouteGenerator` creates a planned route with realistic waypoints, `TelemetryGenerator` samples it at 1-second intervals, and `ChaosInjector` applies GPS noise (jitter, tunnel, drift).

**Module B** processes noisy telemetry: `MapMatcher` snaps raw GPS points to the nearest planned route segment using perpendicular projection, `PhysicsValidator` flags unrealistic speeds (>150 km/h) and signal gaps (>10s), and `TrustScoreCalculator` computes a weighted Trust Score (0-100) from spatial fidelity (40%), route coverage (30%), and temporal consistency (30%).

**Module C** makes billing decisions: `FareCalculator` computes estimated, raw, and final fares. Trust score determines billing -- HIGH (>90%): actuals, MEDIUM (50-89%): hybrid blend, LOW (<50%): revert to estimate.

## Key Components

| Component | Responsibility |
|-----------|---------------|
| `TripService` | Orchestrator wiring all modules; handles trip generation and report building |
| `GeoUtils` | Haversine distance, bearing, point-to-segment projection |
| `ChaosInjector` | Three chaos modes: Gaussian jitter, contiguous tunnel deletion, random drift teleportation |
| `MapMatcher` | Nearest-segment snapping with anomaly detection for points beyond 50m threshold |
| `TrustScoreCalculator` | Weighted multi-factor scoring (spatial + coverage + temporal) |
| `FareCalculator` | Trust-based billing decision with fare breakdown |

## Important Decisions

- **H2 in-memory DB**: Zero configuration for evaluators; JPA entities persist trips, GPS points, and anomalies for report retrieval.
- **Simplified map matching**: Without access to real road network data, the planned route itself serves as the reference geometry. Points are projected onto planned segments.
- **Deterministic chaos**: `ChaosInjector` accepts an optional `Random` seed for reproducible test scenarios.
- **Stateless scoring**: Reports are computed on-demand from stored data, ensuring consistency.

## Scalability Considerations

- The pipeline architecture allows each module to be extracted into a separate microservice with message queues between stages.
- GPS point storage could migrate to a time-series database (e.g., TimescaleDB) for production workloads.
- Map matching could be upgraded to use OSRM's match API for real road-network snapping.
- Trust score weights are externalized in `application.yaml` for tuning without code changes.
