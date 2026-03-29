# Prompt Log -- Route Quality Scoring Engine

## AI Tools Used
- **Cursor** (Claude) -- primary development tool for code generation, architecture design, testing, and UI development

## Prompt Iterations

### 1. Architecture & Planning

**Prompt**:
> "Here is the Route Quality Engine hackathon problem statement [full text attached]. Create a comprehensive implementation plan covering: (1) tech stack selection with justification, (2) project structure with package layout, (3) data model design for Trip/GpsPoint/Anomaly entities, (4) algorithm design for Haversine distance, map matching via perpendicular projection, and weighted trust scoring, (5) REST API contract with request/response schemas, (6) testing strategy across unit, integration, and KPI validation layers. Organize into sequenced implementation tasks with dependencies."

**Why this prompt works**: Provides full context upfront, requests structured output across multiple dimensions, and asks for dependency ordering -- ensuring the AI produces a coherent, sequenced plan rather than a flat list.

**Result**: Generated a detailed plan with module decomposition (Generator, Scoring Engine, Verification), data model (Trip, GpsPoint, Anomaly entities), trust score formula, and fare calculation logic.

### 2. Project Scaffolding

**Prompt**:
> "Implement the plan starting with the Maven project scaffold. Create: pom.xml with Spring Boot 3.2.5 parent, dependencies for Web, JPA, H2, Lombok, and Validation; application.yaml with H2 in-memory config, server port, and custom scoring/fare properties under the 'rqe' namespace; main application class; and the full package directory structure following controller/service/model/dto/repository/util layers."

**Why this prompt works**: Enumerates every concrete artifact expected, specifies exact versions and dependency names, and defines the configuration structure -- leaving no ambiguity about what "scaffold" means.

**Result**: Created Spring Boot 3.2.5 project with H2, Lombok, JPA, and validation dependencies. Required iteration to fix Lombok annotation processor compatibility with Java 21.

### 3. Domain Model & DTOs

**Prompt**:
> "Create all JPA entities and supporting types: Trip entity (UUID id, coordinates, distances, timestamps, embedded ChaosConfig), GpsPoint entity (lat/lng/timestamp/pointType/sequenceIndex with trip FK), Anomaly entity (type/description/affected indices). Create enums: TrustLevel (HIGH >90, MEDIUM 50-90, LOW <50), BillingDecision (USE_ACTUALS, HYBRID, USE_ESTIMATE), PointType (PLANNED, RAW, CORRECTED), AnomalyType (GPS_JITTER, SIGNAL_GAP, UNREALISTIC_SPEED). Create DTOs: TripGenerationRequest with Bean Validation annotations, RouteQualityReport with nested RouteSummary, AnomalyDto, and FareBreakdown."

**Why this prompt works**: Specifies every field, enum value, and validation constraint rather than leaving the AI to guess domain semantics. Includes the trust score thresholds directly in the enum definition.

**Result**: Generated all model classes. Required one fix: `@Builder(toBuilder = true)` on GpsPoint for deep copying in ChaosInjector.

### 4. Core Algorithms

**Prompt**:
> "Implement the following service classes with their core algorithms: (1) GeoUtils -- static utility with Haversine distance (both km and meters), bearing calculation, perpendicular point-to-segment projection, total polyline distance, and meter-to-degree conversion for noise injection. (2) RouteGenerator -- generates a planned route with 8 intermediate waypoints between start/end using lateral offsets for realism. (3) TelemetryGenerator -- samples the planned route at 1-second intervals with interpolated positions matching realistic driving speed (40 km/h). (4) ChaosInjector -- applies jitter (Gaussian noise to lat/lng), tunnel (contiguous chunk deletion), and drift (random teleportation with configurable probability). (5) MapMatcher -- snaps raw GPS points to nearest planned route segment via perpendicular projection, flags deviations > threshold as GPS_JITTER anomalies. (6) PhysicsValidator -- detects unrealistic speed (>150 km/h) and signal gaps (>10s) between consecutive points. (7) TrustScoreCalculator -- computes weighted score from spatial fidelity (40%), coverage ratio (30%), and temporal fidelity (30%). (8) FareCalculator -- calculates estimated/raw/final fares using distance and time rates, applies trust-based billing decision."

**Why this prompt works**: Each component is described with its specific algorithm, parameters, and thresholds. The numbered list creates a clear contract for each class. Including the mathematical basis (Haversine, perpendicular projection, weighted scoring) prevents the AI from inventing incorrect approaches.

**Result**: All service implementations generated in one pass. Key algorithms: Haversine formula for GPS distance, Gaussian noise for jitter, contiguous chunk deletion for tunnel, perpendicular projection for map matching, weighted multi-factor trust scoring.

### 5. Orchestration & REST API

**Prompt**:
> "Implement TripService as the central orchestrator that: (a) accepts TripGenerationRequest and coordinates the full pipeline -- route generation, telemetry sampling, chaos injection, map matching, physics validation, anomaly collection, and persistence; (b) builds RouteQualityReport by loading persisted data, re-running scoring, computing trust score, fare calculation, and generating a human-readable billing explanation. Then implement REST controllers: TripController with POST /api/trips/generate, GET /api/trips, GET /api/trips/{tripId}; RouteQualityController with GET /route-quality/report/{tripId} returning the full quality report. Add a GlobalExceptionHandler for consistent error responses with proper HTTP status codes."

**Why this prompt works**: Explicitly defines the orchestration pipeline ordering (which service calls which, in what sequence), specifies exact REST paths and HTTP methods, and includes error handling -- ensuring the API contract matches the problem statement's verification endpoint exactly.

**Result**: TripService wires all modules in the correct pipeline order. Controllers expose the required `GET /route-quality/report/{tripId}` endpoint plus trip generation/listing endpoints. GlobalExceptionHandler provides centralized 400/404/500 handling.

### 6. Admin Dashboard (Interactive Testing Interface)

**Prompt**:
> "The problem statement requires 'An Interactive Testing Interface for Admins to generate synthetic trips and test the engine's resilience' with chaos control sliders. Build a single-page admin dashboard served as a static HTML file from Spring Boot at /. It should include: (1) Route Selection -- preset buttons for common Riyadh routes (City center, King Fahd Road, Airport Run, Short Trip) plus editable lat/lng fields for custom routes. (2) Chaos Control Sliders -- Jitter Intensity (0-100m), Tunnel Effect (0-95%), Drift Factor (0-300m), Drift Probability (0-50%), each with a real-time value display and color-coded progress bar (green→yellow→red based on severity). (3) One-click 'Generate Trip & Score' button that calls POST /api/trips/generate then GET /route-quality/report/{tripId} and renders the full report. (4) Report display showing: trust score with color-coded badge, billing decision indicator, final fare, route comparison cards (planned vs raw vs corrected distances), fare breakdown with deviation analysis, scrollable anomaly list grouped by type, and human-readable billing explanation. (5) Trip history sidebar allowing reload of previous reports. Use a modern dark theme with CSS custom properties, no external dependencies."

**Why this prompt works**: Maps directly to the problem statement's Module A requirements (route selection, chaos sliders), specifies exact slider ranges matching the chaos parameters, requests specific visual treatments (color coding by severity, badges by trust level), and mandates no external dependencies for zero-setup evaluation. The numbered structure ensures nothing from the spec is missed.

**Result**: Generated a complete single-page dashboard with dark theme, all chaos sliders, preset routes, real-time report rendering, and trip history. Served directly from Spring Boot as `src/main/resources/static/index.html` with no build step required.

### 7. Map Matching Refinement

**Prompt**:
> "The corrected distance is inflated (12 km vs 6 km planned) because the map matcher snaps each noisy point to the nearest segment independently, causing oscillation along the route direction. Fix the MapMatcher to: (1) use global nearest-segment matching (search all planned route segments for each raw point), (2) track each point's route progress parameter (cumulative km along the planned route), (3) compute the corrected distance from the first and last raw points' route progress values rather than summing individual corrected-point polyline distances, (4) cap the result at the total planned route distance. Also move physics validation to run on raw telemetry points (not corrected) since the spec says 'Flag segments where speed > 150km/h' in the actual GPS data."

**Why this prompt works**: Diagnoses the exact root cause (per-point oscillation compounding into inflated distance), proposes a specific algorithmic fix (endpoint-based progress measurement), and references the spec to justify the physics validation change. This level of diagnostic detail in the prompt produces a targeted fix rather than a rewrite.

**Result**: Corrected distance now matches planned distance within ±0.1 km across all chaos scenarios: clean (6.10/6.10), 20m jitter (6.09/6.09), heavy noise (6.13/6.14), 90% tunnel (6.17/6.17).

### 8. Unit Tests

**Prompt**:
> "Write comprehensive JUnit 5 unit tests for all 8 service/utility classes. For each class, cover: normal operation with expected inputs, boundary conditions (empty lists, null inputs, single-element collections, zero values), edge cases specific to the domain (antipodal points for Haversine, 100% tunnel fraction, zero-length route segments, identical consecutive timestamps). Use descriptive method names following 'methodName_condition_expectedResult' convention. Construct test data programmatically rather than loading from files."

**Why this prompt works**: Specifies the testing methodology (JUnit 5), coverage scope (all 8 classes), boundary categories, domain-specific edge cases, naming conventions, and data construction approach. This prevents superficial "happy path only" tests.

**Result**: Generated 128 unit tests covering normal operations, boundary conditions, and error cases across all components.

### 9. Integration & KPI Validation Tests

**Prompt**:
> "Write two test classes: (1) RouteQualityIntegrationTest using @SpringBootTest with MockMvc that tests end-to-end flows: generate a trip via POST, retrieve its report via GET, verify the response structure contains all required fields (trustScore, trustLevel, billingDecision, fareBreakdown, anomalies, explanation), verify clean trips get HIGH trust, and verify noisy trips get appropriate trust degradation. (2) KpiValidationTest that directly validates the two success metrics from the problem statement: 'Simulation Accuracy -- noise > 20m must produce LOW trust' and 'Revenue Protection -- 100% tunnel trips must default to Estimated Fare, never $0'. These KPI tests should generate multiple trips with controlled chaos parameters and assert the invariants hold."

**Why this prompt works**: Separates integration tests (HTTP contract verification) from KPI tests (business metric validation), references the exact success metrics from the problem statement with their pass/fail criteria, and specifies the testing approach (MockMvc for integration, direct service calls for KPI). This ensures both technical correctness and business requirement coverage.

**Result**: 16 additional tests verifying full pipeline correctness and both success metrics. All 144 tests pass.

## Key Iterations

1. **Lombok compatibility**: Updated from 1.18.34 to 1.18.36 and configured explicit annotation processor paths in maven-compiler-plugin for Java 21 compatibility
2. **Builder configuration**: Added `@Builder(toBuilder = true)` on GpsPoint to enable immutable deep copying in the ChaosInjector
3. **Java version alignment**: Installed Java 21 LTS (via Homebrew) instead of system Java 25 to resolve Lombok `TypeTag::UNKNOWN` initialization error
4. **Map matching distance correction**: Replaced per-point polyline distance summation with endpoint-based route progress measurement to eliminate jitter-induced distance inflation
5. **Physics validation target**: Moved speed/gap detection from corrected points to raw telemetry points to match the problem statement's intent of flagging issues in actual GPS data

---

## Phase 2 Prompt Iterations

### 10. Requirements Decomposition

**Prompt**:
> "Here is the Phase 2 problem statement [full text]. Analyze it as a senior product manager with system architect experience. For each of the 3 requirements (Trip Review, Driver Summary, Reason Codes): (1) identify what data currently exists vs. what's missing, (2) map required outputs to existing entities/services, (3) identify backward compatibility risks, (4) propose the minimal set of entity changes needed. Cross-reference the existing Trip entity [fields listed], TripService flow [processScoring → buildReport], and existing API paths to identify collisions."

**Why this prompt works**: Frames the analysis role (PM + architect), demands gap analysis against existing code, and explicitly calls out backward compatibility risks — forcing a thorough audit before any code changes. The cross-referencing instruction prevents the AI from proposing changes that conflict with existing behavior.

**Result**: Identified the critical gap — trust score and billing decision are computed on-the-fly in `buildReport()` but never persisted on the Trip entity. Phase 2 aggregation queries require persisted data. Solution: modify `processScoring()` to compute and return scoring results, persist them in `generateTrip()`, while leaving `buildReport()` untouched for backward compatibility.

### 11. Foundation — Persist Scoring in Generation Pipeline

**Prompt**:
> "The Trip entity currently has no trustScore, trustLevel, or billingDecision fields — these are computed on-the-fly in buildReport(). Phase 2 requires aggregation queries across drivers, which means scoring must be persisted. Modify processScoring() to: (1) compute trust score using TrustScoreCalculator (reuse existing deviations, rawPointCount, expectedPointCount, anomalies, correctedPoints already available in scope), (2) compute fare using FareCalculator (using planned/raw/corrected distances and durations), (3) return a ScoringResult record with trustScore, trustLevel, billingDecision. Then modify generateTrip() to: (4) persist these on the Trip entity, (5) set driverId from the request, (6) auto-assign reviewStatus=PENDING_REVIEW if trustScore < configurable threshold (default 50.0). Constraint: do NOT modify buildReport() — it must continue to re-compute independently for backward compatibility."

**Why this prompt works**: Diagnoses the exact data gap (on-the-fly vs. persisted), specifies which existing components to reuse (preventing code duplication), numbers each step for precise execution, and includes an explicit constraint protecting backward compatibility. The "reuse existing" instruction ensures no duplicate calculation logic.

**Result**: `processScoring()` now returns a `ScoringResult` record. `generateTrip()` persists trust score, trust level, billing decision, driver ID, and auto-assigned review status. `buildReport()` remains untouched — identical computation path. All 144 existing tests pass without modification.

### 12. Trip Review System

**Prompt**:
> "Implement Requirement 3.1 — Trip Review System. Create: (1) ReviewStatus enum with NOT_REVIEWED, PENDING_REVIEW, APPROVED, REJECTED. (2) TripReviewController at path /trips (matching spec exactly, not /api/trips) with GET /trips/review (optional ?status= filter, returns List of TripReviewResponse) and POST /trips/{id}/review (body: ReviewRequest with target status, returns updated TripReviewResponse). (3) TripReviewResponse DTO with id, driverId, trustScore, trustLevel, billingDecision, reviewStatus, createdAt. (4) ReviewRequest DTO with @NotNull reviewStatus. (5) TripRepository query methods: findByReviewStatus, findAllByOrderByCreatedAtDesc. (6) TripService methods: getTripsForReview and updateReviewStatus. Handle 404 for non-existent trip and 400 for invalid request body using existing GlobalExceptionHandler patterns."

**Why this prompt works**: Specifies exact API paths matching the problem statement spec, includes both DTOs with their fields, lists required repository query methods by name, references the existing exception handling pattern for consistency, and covers both happy path and error handling. The explicit "not /api/trips" note prevents path collision with Phase 1.

**Result**: Created `TripReviewController`, `TripReviewResponse`, `ReviewRequest`, and two new TripRepository query methods. The controller handles all edge cases (404, 400) via the existing `GlobalExceptionHandler`. Integration tests verify end-to-end flow including auto-review assignment.

### 13. Driver Summary and Reason Codes

**Prompt**:
> "Implement Requirements 3.2 and 3.3 as a single DriverController at /drivers with two endpoints. For GET /drivers/{driverId}/summary: accept optional query params 'from' and 'to' (ISO-8601, default last 30 days), return DriverSummary DTO with: totalTrips, averageTrustScore, minTrustScore, maxTrustScore, trustLevelDistribution (Map), billingDecisionDistribution (Map), totalDistanceKm, lowTrustTripPercentage, trendIndicator (IMPROVING/STABLE/DECLINING based on comparing avg trust of most recent 25% of trips vs oldest 25%), periodStart, periodEnd. For GET /drivers/{driverId}/reason-code-summary: return ReasonCodeSummary DTO with: totalTrips, totalAnomalies, lowTrustTripCount, lowTrustTripPercentage, billingDecisionDistribution, anomalyFrequency (Map of AnomalyType to count), mostCommonAnomaly, anomaliesPerTrip. Create DriverService with aggregation logic using TripRepository.findByDriverIdAndCreatedAtBetween and AnomalyRepository.findByTripIdIn. Return empty/zeroed results for unknown drivers (not 404)."

**Why this prompt works**: Combines two related requirements into a cohesive implementation unit (shared controller and service), specifies the exact DTO field names and types for each endpoint, defines the trend calculation algorithm precisely (25% quartile comparison), lists the required repository methods, and specifies the empty-result behavior (zeroed response, not 404) which is important for new driver onboarding scenarios.

**Result**: Created `DriverController`, `DriverService`, `DriverSummary`, and `ReasonCodeSummary`. The aggregation logic uses Java streams for in-memory computation. Unknown drivers return zeroed summaries. Trend indicator uses quartile comparison with a ±5 point threshold for stability.

### 14. Comprehensive Test Suite

**Prompt**:
> "Write tests for all Phase 2 functionality organized into 3 test classes: (1) DriverServiceTest — 16 unit tests covering aggregation math (avg/min/max, distributions, trend calculation, time filtering, empty driver, reason code frequency, anomalies per trip, low trust percentage). (2) TripReviewIntegrationTest — 10 @SpringBootTest tests covering GET /trips/review and POST /trips/{id}/review (auto-review assignment, status transitions, filtering, driver ID persistence, error cases 404/400). (3) DriverIntegrationTest — 10 @SpringBootTest tests covering GET /drivers/{driverId}/summary and reason-code-summary (multi-trip aggregation, driver isolation, time ranges, trust distribution, total distance, anomaly cross-verification, billing decision distribution). Verify all 144 existing Phase 1 tests pass unchanged. Each test must use descriptive method names, construct test data programmatically, and test both happy paths and edge cases."

**Why this prompt works**: Specifies exact test counts and test class organization, lists specific test scenarios by name for each class, mandates backward compatibility verification, requires both unit tests (Mockito) and integration tests (@SpringBootTest with MockMvc), and insists on programmatic test data construction rather than file-based fixtures. The cross-verification tests (e.g., anomaly count matching) ensure consistency between related endpoints.

**Result**: 36 new tests across 3 classes. All 180 tests (144 Phase 1 + 36 Phase 2) pass with zero failures. Backward compatibility verified — no Phase 1 test modifications required.
