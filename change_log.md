# Change Log — Phase 2: Route Quality Engine Extension

## Overview

Phase 2 extends the Route Quality Engine with three new API modules for operational use cases: Trip Review System, Driver Route Quality Summary, and Reason Codes Summary. All changes are additive — existing Phase 1 functionality is untouched.

---

## Data Model Changes

### Trip Entity (`Trip.java`)

**Before**: Trip stored only coordinates, distances, durations, chaos config, and timestamp.

**After**: Five new nullable fields added:

| Field | Type | Purpose |
|-------|------|---------|
| `driverId` | `String` (nullable) | Associates trips with drivers for aggregation |
| `trustScore` | `Double` (nullable) | Persisted trust score from scoring pipeline |
| `trustLevel` | `TrustLevel` (enum) | Persisted trust level classification |
| `billingDecision` | `BillingDecision` (enum) | Persisted billing decision |
| `reviewStatus` | `ReviewStatus` (enum, default `NOT_REVIEWED`) | Review workflow status |

All fields are nullable to maintain backward compatibility — existing trip generation without these fields continues to work unchanged.

### New Enum: `ReviewStatus.java`

```
NOT_REVIEWED → PENDING_REVIEW → APPROVED / REJECTED
```

Auto-assigned during trip generation: `PENDING_REVIEW` if `trustScore < 50.0`, else `NOT_REVIEWED`.

### DTOs Added

- `TripReviewResponse` — review-focused view of a trip (id, driverId, trustScore, trustLevel, billingDecision, reviewStatus, createdAt)
- `ReviewRequest` — review status update payload (reviewStatus with @NotNull validation)
- `DriverSummary` — aggregated driver metrics (avg/min/max trust, distributions, trend indicator)
- `ReasonCodeSummary` — anomaly frequency analysis per driver

### DTOs Modified

- `TripGenerationRequest` — added optional `driverId` field
- `TripResponse` — added `driverId`, `trustScore`, `trustLevel`, `billingDecision`, `reviewStatus`

---

## Scoring Pipeline Changes

### `TripService.processScoring()` — Modified

**Before**: `void` method that only performed map matching, physics validation, and anomaly persistence.

**After**: Returns `ScoringResult` record with `trustScore`, `trustLevel`, `billingDecision`, `correctedDistKm`. Now additionally computes trust score (via `TrustScoreCalculator`) and fare (via `FareCalculator`) inside the scoring pipeline.

### `TripService.generateTrip()` — Modified

**Before**: Called `processScoring()` and returned trip response.

**After**: Uses `ScoringResult` to persist scoring data on the Trip entity. Sets `driverId` from request. Auto-assigns `reviewStatus` based on configurable threshold (default 50.0).

### Backward Compatibility

`buildReport()` is NOT modified — it continues to re-compute everything independently for the `/route-quality/report/{tripId}` endpoint. The persisted values are used exclusively by the new Phase 2 endpoints.

---

## New API Endpoints

### Requirement 3.1: Trip Review System

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/trips/review` | List trips for review. Optional `?status=` filter |
| `POST` | `/trips/{id}/review` | Update review status (body: `{"reviewStatus": "APPROVED"}`) |

Controller: `TripReviewController` at `/trips` (separate from existing `/api/trips`)

### Requirement 3.2: Driver Route Quality Summary

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/drivers/{driverId}/summary` | Aggregated driver metrics. Optional `from`/`to` params (default: last 30 days) |

### Requirement 3.3: Reason Code Summary

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/drivers/{driverId}/reason-code-summary` | Anomaly frequency analysis per driver. Optional `from`/`to` params |

Controller: `DriverController` at `/drivers`

### New Service: `DriverService`

Aggregation logic for driver summary and reason code summary. Computes:
- Average/min/max trust scores
- Trust level and billing decision distributions
- Trend indicator (IMPROVING/STABLE/DECLINING based on comparing first 25% vs last 25% of trips)
- Anomaly frequency by type, most common anomaly, anomalies per trip

---

## Configuration Changes

### `application.yaml`

Added:
```yaml
rqe:
  review:
    auto-review-threshold: 50.0
```

---

## Dashboard UI Changes

### `index.html`

- Added sidebar tab navigation: "Data Generator" (existing) and "Trip Reviews" (new)
- Trip Reviews panel: status filter buttons (All/Pending/Approved/Rejected/Not Reviewed), trip list with trust score badges, Approve/Reject action buttons
- Added Driver ID input field to the trip generation form
- Same single-page app — no separate page or route

---

## Repository Changes

### `TripRepository` — 4 new query methods

- `findByReviewStatus(ReviewStatus status)`
- `findAllByOrderByCreatedAtDesc()`
- `findByDriverIdAndCreatedAtBetween(String driverId, Instant from, Instant to)`
- `findByDriverId(String driverId)`

### `AnomalyRepository` — 1 new query method

- `findByTripIdIn(List<UUID> tripIds)`

---

## Testing

### New Test Classes

| Test Class | Type | Tests | Coverage |
|------------|------|-------|----------|
| `DriverServiceTest` | Unit (Mockito) | 16 | Aggregation math, distributions, trend calculation, empty driver, reason codes |
| `TripReviewIntegrationTest` | Integration (@SpringBootTest) | 10 | Review CRUD, auto-review, filtering, error cases |
| `DriverIntegrationTest` | Integration (@SpringBootTest) | 10 | Summary aggregation, driver isolation, reason code cross-verification |

### Test Count

- Phase 1: 144 tests (unchanged, all pass)
- Phase 2: 36 new tests
- **Total: 180 tests, 0 failures**

---

## Backward Compatibility Guarantees

1. **Phase 1 controllers not modified**: `TripController` and `RouteQualityController` untouched
2. **`buildReport()` not modified**: Identical computation path for existing report endpoint
3. **New endpoints at separate paths**: `/trips/` and `/drivers/` (no collision with existing `/api/trips/`)
4. **All new Trip fields are nullable**: Existing trip creation without them works unchanged
5. **`TripGenerationRequest` accepts optional `driverId`**: Jackson ignores absent fields
6. **`GlobalExceptionHandler` reused**: Same `@RestControllerAdvice` handles errors for new controllers
7. **All 144 Phase 1 tests pass without modification**
