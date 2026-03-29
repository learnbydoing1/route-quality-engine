# Route Quality Scoring Engine

A Route Quality Scoring Engine that reconciles Planned Routes (Estimates) with Actual Journeys (GPS Telemetry) for fair ride-hailing billing.

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+

### Build & Run

```bash
# Build the project
mvn clean package

# Run the application
mvn spring-boot:run
```

The application starts on `http://localhost:8080`. Open this URL in a browser to access the **Admin Dashboard**.

### Admin Dashboard

The interactive testing interface is served at the root URL (`http://localhost:8080`). It provides:

- **Route Selection** — Preset routes (Riyadh City, King Fahd Rd, Airport Run, Short Trip) or custom lat/lng coordinates
- **Chaos Control Sliders** — Jitter Intensity, Tunnel Effect, Drift Factor, and Drift Probability with real-time visual feedback
- **One-Click Trip Generation & Scoring** — Generates synthetic telemetry, injects chaos, runs the scoring engine, and displays the full quality report
- **Report Visualization** — Trust score, billing decision, fare breakdown, route distance comparison, and anomaly list
- **Trip History** — Click any previous trip to reload its report

### Run Tests

```bash
mvn test
```

180 tests covering unit, integration, and KPI validation.

## Deploy the Admin Dashboard on Netlify

Netlify hosts **static files only**; it does not run the Spring Boot server. The dashboard HTML/CSS/JS lives in `src/main/resources/static/`.

This repo includes `netlify.toml` so the **publish directory** is set correctly. If you still see Netlify’s “Page not found”, open **Site configuration → Build & deploy → Build settings** and set:

- **Publish directory:** `src/main/resources/static`
- **Build command:** leave empty (not `mvn package` unless you add a step that copies static assets to a known folder)

After deploy, the **UI loads** from Netlify, but API calls (`/api/trips`, `/trips/review`, `/drivers/...`, `/route-quality/...`) target the same Netlify hostname and will **not** work until you either:

1. Run the Spring Boot app elsewhere (Railway, Render, Fly.io, a VPS, etc.) and add **Netlify redirects** that proxy those paths to your backend URL, or  
2. Open the dashboard from the Spring Boot URL only (`http://localhost:8080` when running locally).

Example proxy rules (replace `https://your-api.example.com` with your real backend base URL) — add under **Site configuration → Redirects** or in `netlify.toml` as `[[redirects]]` entries:

| From | To |
|------|-----|
| `/api/*` | `https://your-api.example.com/api/:splat` |
| `/trips/*` | `https://your-api.example.com/trips/:splat` |
| `/drivers/*` | `https://your-api.example.com/drivers/:splat` |
| `/route-quality/*` | `https://your-api.example.com/route-quality/:splat` |

Use status **200** (rewrite/proxy) so the browser still calls same-origin paths.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/trips/generate` | Generate a synthetic trip with chaos parameters |
| GET | `/api/trips` | List all generated trips |
| GET | `/api/trips/{tripId}` | Get a specific trip |
| GET | `/route-quality/report/{tripId}` | Get the full quality report for a trip |

### Generate a Trip

```bash
curl -X POST http://localhost:8080/api/trips/generate \
  -H "Content-Type: application/json" \
  -d '{
    "startLat": 24.7136,
    "startLng": 46.6753,
    "endLat": 24.7500,
    "endLng": 46.7200,
    "jitterMeters": 15,
    "tunnelFraction": 0.1,
    "driftMeters": 50,
    "driftProbability": 0.05
  }'
```

### Get Route Quality Report

```bash
curl http://localhost:8080/route-quality/report/{tripId}
```

Response:
```json
{
  "tripId": "uuid",
  "plannedRoute": { "distanceKm": 5.2, "durationMinutes": 7.8, "pointCount": 470 },
  "rawTelemetry": { "distanceKm": 5.8, "durationMinutes": 8.1, "pointCount": 423 },
  "correctedRoute": { "distanceKm": 5.3, "durationMinutes": 8.1, "pointCount": 423 },
  "trustScore": 72.5,
  "trustLevel": "MEDIUM",
  "anomalies": [
    { "type": "GPS_JITTER", "description": "...", "affectedPoints": 15 }
  ],
  "fareBreakdown": {
    "estimatedFare": 21.90,
    "rawFare": 23.50,
    "finalFare": 22.34
  },
  "billingDecision": "HYBRID",
  "explanation": "Trust score of 72.5% (Medium) triggered hybrid billing..."
}
```

## Chaos Parameters

| Parameter | Range | Description |
|-----------|-------|-------------|
| `jitterMeters` | 0-200 | Gaussian noise standard deviation in meters |
| `tunnelFraction` | 0.0-1.0 | Fraction of telemetry points deleted (signal loss) |
| `driftMeters` | 0-500 | Teleportation offset magnitude in meters |
| `driftProbability` | 0.0-1.0 | Probability of drift at each point |

## Trust Score & Billing Decision

| Trust Level | Score Range | Billing Decision |
|-------------|-------------|------------------|
| HIGH | > 90% | Bill on corrected actuals |
| MEDIUM | 50-90% | Hybrid: weighted blend of actuals and estimate |
| LOW | < 50% | Revert to upfront estimate |

## Tech Stack

- Java 21, Spring Boot 3.2.5
- H2 in-memory database (zero configuration)
- Maven
- JUnit 5 for testing

## H2 Console

Available at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:rqedb`, user: `sa`, no password).
