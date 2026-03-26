# Prompt Log -- Route Quality Scoring Engine

## AI Tools Used
- **Cursor** (Claude) -- primary development tool for code generation, architecture design, and test writing

## Prompt Iterations

### 1. Architecture & Planning
**Prompt**: Provided the full hackathon problem statement and asked for a comprehensive implementation plan covering tech stack, project structure, algorithms, data model, API design, and testing strategy.

**Result**: Generated a detailed plan with module decomposition (Generator, Scoring Engine, Verification), data model (Trip, GpsPoint, Anomaly entities), trust score formula, and fare calculation logic.

### 2. Project Scaffolding
**Prompt**: Implement the plan starting with Maven project scaffold (pom.xml, application.yaml, main class, directory structure).

**Result**: Created Spring Boot 3.2.5 project with H2, Lombok, JPA, and validation dependencies. Required iteration to fix Lombok annotation processor compatibility with Java 21.

### 3. Domain Model & DTOs
**Prompt**: Create all JPA entities (Trip, GpsPoint, Anomaly), enums (TrustLevel, BillingDecision, PointType, AnomalyType), and DTOs (TripGenerationRequest, RouteQualityReport, etc.).

**Result**: Generated all model classes. Required one fix: `@Builder(toBuilder = true)` on GpsPoint for deep copying in ChaosInjector.

### 4. Core Algorithms
**Prompt**: Implement GeoUtils (Haversine, bearing, point-to-segment projection), RouteGenerator, TelemetryGenerator, ChaosInjector, MapMatcher, PhysicsValidator, TrustScoreCalculator, FareCalculator.

**Result**: All service implementations generated in one pass. Key algorithms:
- Haversine formula for GPS distance calculations
- Gaussian noise injection for jitter simulation
- Contiguous chunk deletion for tunnel simulation
- Perpendicular projection for map matching
- Weighted multi-factor trust scoring

### 5. Orchestration & REST API
**Prompt**: Implement TripService orchestrator and REST controllers (TripController, RouteQualityController).

**Result**: TripService wires all modules in the correct pipeline order. Controllers expose the required `GET /route-quality/report/{tripId}` endpoint plus trip generation/listing endpoints.

### 6. Unit Tests
**Prompt**: Write comprehensive JUnit 5 unit tests for all 8 service/utility classes with edge cases.

**Result**: Generated 128 unit tests covering normal operations, boundary conditions, and error cases across all components.

### 7. Integration & KPI Tests
**Prompt**: Write integration tests (end-to-end via MockMvc) and KPI validation tests (noise>20m=LowTrust, tunnel=EstimateFare).

**Result**: 16 additional tests verifying full pipeline correctness and both success metrics. All 144 tests pass.

## Key Iterations
- Lombok version compatibility: Updated from 1.18.34 to 1.18.36 and configured annotation processor paths for Java 21
- `toBuilder = true` annotation required on GpsPoint for immutable copying in chaos injection
- Java version alignment: Installed Java 21 LTS instead of Java 25 for Lombok compatibility
