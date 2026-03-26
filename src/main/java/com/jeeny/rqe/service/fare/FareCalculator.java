package com.jeeny.rqe.service.fare;

import com.jeeny.rqe.dto.FareBreakdown;
import com.jeeny.rqe.model.BillingDecision;
import com.jeeny.rqe.model.TrustLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FareCalculator {

    private final double baseFare;
    private final double perKmRate;
    private final double perMinuteRate;

    public FareCalculator(
            @Value("${rqe.fare.base-fare:5.0}") double baseFare,
            @Value("${rqe.fare.per-km-rate:2.5}") double perKmRate,
            @Value("${rqe.fare.per-minute-rate:0.5}") double perMinuteRate) {
        this.baseFare = baseFare;
        this.perKmRate = perKmRate;
        this.perMinuteRate = perMinuteRate;
    }

    /**
     * Billing decision result containing fare breakdown, decision, and explanation.
     */
    public record FareResult(FareBreakdown fareBreakdown, BillingDecision decision, String explanation) {}

    /**
     * Calculate fares and determine billing decision based on trust score.
     *
     * @param plannedDistanceKm  distance from planned route (km)
     * @param plannedDurationMin duration from planned route (minutes)
     * @param rawDistanceKm      distance from raw telemetry (km)
     * @param rawDurationMin     duration from raw telemetry (minutes)
     * @param correctedDistanceKm distance from corrected route (km)
     * @param correctedDurationMin duration from corrected route (minutes)
     * @param trustScore          trust score (0-100)
     */
    public FareResult calculate(
            double plannedDistanceKm, double plannedDurationMin,
            double rawDistanceKm, double rawDurationMin,
            double correctedDistanceKm, double correctedDurationMin,
            double trustScore) {

        double estimatedFare = computeFare(plannedDistanceKm, plannedDurationMin);
        double rawFare = computeFare(rawDistanceKm, rawDurationMin);
        double correctedFare = computeFare(correctedDistanceKm, correctedDurationMin);

        TrustLevel level = TrustLevel.fromScore(trustScore);
        BillingDecision decision;
        double finalFare;
        String explanation;

        switch (level) {
            case HIGH -> {
                decision = BillingDecision.USE_ACTUALS;
                finalFare = correctedFare;
                explanation = String.format(
                        "Trust score of %.1f%% (High) indicates clean GPS data. Billing on corrected actuals: %.2f SAR.",
                        trustScore, finalFare);
            }
            case MEDIUM -> {
                decision = BillingDecision.HYBRID;
                double trustFraction = trustScore / 100.0;
                finalFare = trustFraction * correctedFare + (1.0 - trustFraction) * estimatedFare;
                explanation = String.format(
                        "Trust score of %.1f%% (Medium) triggered hybrid billing: %.1f%% corrected actuals + %.1f%% estimate = %.2f SAR.",
                        trustScore, trustScore, 100.0 - trustScore, finalFare);
            }
            case LOW -> {
                decision = BillingDecision.USE_ESTIMATE;
                finalFare = estimatedFare;
                explanation = String.format(
                        "Trust score of %.1f%% (Low) indicates unreliable GPS data. Reverting to estimated fare: %.2f SAR.",
                        trustScore, finalFare);
            }
            default -> {
                decision = BillingDecision.USE_ESTIMATE;
                finalFare = estimatedFare;
                explanation = "Unknown trust level. Defaulting to estimated fare.";
            }
        }

        finalFare = roundFare(finalFare);

        FareBreakdown breakdown = FareBreakdown.builder()
                .estimatedFare(roundFare(estimatedFare))
                .rawFare(roundFare(rawFare))
                .finalFare(finalFare)
                .build();

        return new FareResult(breakdown, decision, explanation);
    }

    double computeFare(double distanceKm, double durationMinutes) {
        return baseFare + (distanceKm * perKmRate) + (durationMinutes * perMinuteRate);
    }

    private double roundFare(double fare) {
        return Math.round(fare * 100.0) / 100.0;
    }
}
