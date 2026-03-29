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

## Phase 2: Operational Extensions

Phase 2 extends the engine with three new modules for operational use cases, built on top of persisted scoring data.

```
[Phase 1 Pipeline] --> [Persisted Scoring on Trip Entity]
                              |
            +-----------------+-----------------+
            |                 |                 |
    [Trip Review System] [Driver Summary] [Reason Codes]
```

### New API Endpoints

| Endpoint | Controller | Purpose |
|----------|-----------|---------|
| `GET /trips/review` | `TripReviewController` | List trips for review with optional status filter |
| `POST /trips/{id}/review` | `TripReviewController` | Update review status (approve/reject) |
| `GET /drivers/{driverId}/summary` | `DriverController` | Aggregated driver quality metrics |
| `GET /drivers/{driverId}/reason-code-summary` | `DriverController` | Anomaly frequency analysis per driver |

### New Components

| Component | Responsibility |
|-----------|---------------|
| `TripReviewController` | Review workflow endpoints at `/trips` (separate from `/api/trips`) |
| `DriverController` | Driver analytics endpoints at `/drivers` |
| `DriverService` | Aggregation logic: avg/min/max trust scores, trend computation, anomaly frequency analysis |
| `ReviewStatus` enum | NOT_REVIEWED, PENDING_REVIEW, APPROVED, REJECTED |

### Data Flow for Phase 2

1. **Trip Generation** (modified): `generateTrip()` now runs the scoring pipeline and persists `trustScore`, `trustLevel`, `billingDecision`, and auto-assigned `reviewStatus` on the Trip entity
2. **Review System**: Ops users query trips by review status, approve/reject individually
3. **Driver Summary**: Aggregates Trip entity data for a driver over a time range — computes statistical metrics and trend indicator
4. **Reason Codes**: Joins Trip and Anomaly tables for a driver — computes anomaly type frequency and distribution

### Backward Compatibility

- Phase 1 controllers, `buildReport()`, and all existing endpoints are unmodified
- New Trip entity fields are nullable — existing creation patterns still work
- New endpoints use distinct paths (`/trips/`, `/drivers/`) to avoid collision with `/api/trips/`
- All 144 Phase 1 tests pass without modification

## Scalability Considerations

- The pipeline architecture allows each module to be extracted into a separate microservice with message queues between stages.
- GPS point storage could migrate to a time-series database (e.g., TimescaleDB) for production workloads.
- Map matching could be upgraded to use OSRM's match API for real road-network snapping.
- Trust score weights are externalized in `application.yaml` for tuning without code changes.
- Phase 2 aggregation queries could be optimized with database-level aggregation (JPQL/native queries) for large datasets, but in-memory stream processing is simpler and sufficient for current scale.
