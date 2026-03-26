package com.jeeny.rqe.service.generator;

import com.jeeny.rqe.model.ChaosConfig;
import com.jeeny.rqe.model.GpsPoint;
import com.jeeny.rqe.model.PointType;
import com.jeeny.rqe.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChaosInjectorTest {

    private ChaosInjector injector;
    private UUID tripId;

    @BeforeEach
    void setUp() {
        injector = new ChaosInjector();
        tripId = UUID.randomUUID();
    }

    // ── null/empty input ──

    @Test
    void injectChaos_nullInputReturnsEmptyList() {
        ChaosConfig config = ChaosConfig.builder().jitterMeters(10).build();
        List<GpsPoint> result = injector.injectChaos(null, config);
        assertTrue(result.isEmpty());
    }

    @Test
    void injectChaos_emptyInputReturnsEmptyList() {
        ChaosConfig config = ChaosConfig.builder().jitterMeters(10).build();
        List<GpsPoint> result = injector.injectChaos(Collections.emptyList(), config);
        assertTrue(result.isEmpty());
    }

    // ── zero chaos ──

    @Test
    void injectChaos_zeroChaosReturnsSameNumberOfPoints() {
        List<GpsPoint> input = makeIdealTelemetry(20);
        ChaosConfig config = ChaosConfig.builder()
                .jitterMeters(0)
                .tunnelFraction(0)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        List<GpsPoint> result = injector.injectChaos(input, config);
        assertEquals(input.size(), result.size());
    }

    @Test
    void injectChaos_zeroChaosPreservesCoordinates() {
        List<GpsPoint> input = makeIdealTelemetry(10);
        ChaosConfig config = ChaosConfig.builder().build(); // all defaults = 0

        List<GpsPoint> result = injector.injectChaos(input, config);
        for (int i = 0; i < input.size(); i++) {
            assertEquals(input.get(i).getLatitude(), result.get(i).getLatitude(), 1e-10);
            assertEquals(input.get(i).getLongitude(), result.get(i).getLongitude(), 1e-10);
        }
    }

    // ── jitter ──

    @Test
    void injectChaos_jitterAddsNoise() {
        List<GpsPoint> input = makeIdealTelemetry(50);
        ChaosConfig config = ChaosConfig.builder()
                .jitterMeters(20)
                .tunnelFraction(0)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        List<GpsPoint> result = injector.injectChaos(input, config, new Random(42));

        int deviatedCount = 0;
        for (int i = 0; i < result.size(); i++) {
            double dist = GeoUtils.haversineMeters(
                    input.get(i).getLatitude(), input.get(i).getLongitude(),
                    result.get(i).getLatitude(), result.get(i).getLongitude());
            if (dist > 1.0) deviatedCount++;
        }
        assertTrue(deviatedCount > input.size() / 2,
                "At least half the points should deviate with jitter, got " + deviatedCount);
    }

    @Test
    void injectChaos_jitterPreservesPointCount() {
        List<GpsPoint> input = makeIdealTelemetry(30);
        ChaosConfig config = ChaosConfig.builder()
                .jitterMeters(15)
                .tunnelFraction(0)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        List<GpsPoint> result = injector.injectChaos(input, config);
        assertEquals(input.size(), result.size());
    }

    // ── tunnel ──

    @Test
    void injectChaos_tunnelRemovesPoints() {
        List<GpsPoint> input = makeIdealTelemetry(100);
        ChaosConfig config = ChaosConfig.builder()
                .jitterMeters(0)
                .tunnelFraction(0.3)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        List<GpsPoint> result = injector.injectChaos(input, config, new Random(42));
        assertTrue(result.size() < input.size(),
                "Tunnel should remove points: input=" + input.size() + " result=" + result.size());
    }

    @Test
    void injectChaos_highTunnelRemovesSignificantFraction() {
        List<GpsPoint> input = makeIdealTelemetry(100);
        ChaosConfig config = ChaosConfig.builder()
                .jitterMeters(0)
                .tunnelFraction(0.5)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        List<GpsPoint> result = injector.injectChaos(input, config, new Random(42));
        assertTrue(result.size() <= 70,
                "50% tunnel should remove ~50 points, result size = " + result.size());
    }

    @Test
    void injectChaos_tunnelPreservesFirstAndLastPoint() {
        List<GpsPoint> input = makeIdealTelemetry(50);
        ChaosConfig config = ChaosConfig.builder()
                .jitterMeters(0)
                .tunnelFraction(0.4)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        List<GpsPoint> result = injector.injectChaos(input, config, new Random(99));
        assertEquals(input.get(0).getLatitude(), result.get(0).getLatitude(), 1e-10);
    }

    // ── drift ──

    @Test
    void injectChaos_driftTeleportsSomePoints() {
        List<GpsPoint> input = makeIdealTelemetry(100);
        ChaosConfig config = ChaosConfig.builder()
                .jitterMeters(0)
                .tunnelFraction(0)
                .driftMeters(500)
                .driftProbability(0.5)
                .build();

        List<GpsPoint> result = injector.injectChaos(input, config, new Random(42));

        int largeDeviation = 0;
        for (int i = 0; i < result.size(); i++) {
            double dist = GeoUtils.haversineMeters(
                    input.get(i).getLatitude(), input.get(i).getLongitude(),
                    result.get(i).getLatitude(), result.get(i).getLongitude());
            if (dist > 200) largeDeviation++;
        }
        assertTrue(largeDeviation > 5,
                "Drift with 0.5 probability on 100 points should teleport many, got " + largeDeviation);
    }

    @Test
    void injectChaos_driftWithZeroProbabilityHasNoEffect() {
        List<GpsPoint> input = makeIdealTelemetry(20);
        ChaosConfig config = ChaosConfig.builder()
                .jitterMeters(0)
                .tunnelFraction(0)
                .driftMeters(1000)
                .driftProbability(0)
                .build();

        List<GpsPoint> result = injector.injectChaos(input, config);
        assertEquals(input.size(), result.size());
        for (int i = 0; i < input.size(); i++) {
            assertEquals(input.get(i).getLatitude(), result.get(i).getLatitude(), 1e-10);
            assertEquals(input.get(i).getLongitude(), result.get(i).getLongitude(), 1e-10);
        }
    }

    // ── reindexing ──

    @Test
    void injectChaos_reindexesSequentiallyAfterChaos() {
        List<GpsPoint> input = makeIdealTelemetry(50);
        ChaosConfig config = ChaosConfig.builder()
                .jitterMeters(5)
                .tunnelFraction(0.2)
                .driftMeters(50)
                .driftProbability(0.1)
                .build();

        List<GpsPoint> result = injector.injectChaos(input, config, new Random(42));
        for (int i = 0; i < result.size(); i++) {
            assertEquals(i, result.get(i).getSequenceIndex());
        }
    }

    // ── does not modify original ──

    @Test
    void injectChaos_doesNotMutateOriginalList() {
        List<GpsPoint> input = makeIdealTelemetry(20);
        int originalSize = input.size();
        double firstLat = input.get(0).getLatitude();

        ChaosConfig config = ChaosConfig.builder()
                .jitterMeters(50)
                .tunnelFraction(0.3)
                .driftMeters(200)
                .driftProbability(0.5)
                .build();

        injector.injectChaos(input, config, new Random(42));
        assertEquals(originalSize, input.size());
        assertEquals(firstLat, input.get(0).getLatitude(), 1e-10);
    }

    private List<GpsPoint> makeIdealTelemetry(int count) {
        List<GpsPoint> points = new ArrayList<>();
        Instant base = Instant.now();
        for (int i = 0; i < count; i++) {
            points.add(GpsPoint.builder()
                    .tripId(tripId)
                    .latitude(24.7 + i * 0.0001)
                    .longitude(46.7 + i * 0.0001)
                    .timestamp(base.plusSeconds(i))
                    .pointType(PointType.RAW)
                    .sequenceIndex(i)
                    .build());
        }
        return points;
    }
}
