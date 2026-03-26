package com.jeeny.rqe.service.fare;

import com.jeeny.rqe.model.BillingDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FareCalculatorTest {

    private FareCalculator calculator;

    private static final double BASE_FARE = 5.0;
    private static final double PER_KM = 2.5;
    private static final double PER_MIN = 0.5;

    @BeforeEach
    void setUp() {
        calculator = new FareCalculator(BASE_FARE, PER_KM, PER_MIN);
    }

    // ── fare formula correctness ──

    @Test
    void computeFare_formulaIsCorrect() {
        // fare = base + dist*perKm + dur*perMin = 5 + 10*2.5 + 20*0.5 = 5+25+10 = 40
        double fare = calculator.computeFare(10.0, 20.0);
        assertEquals(40.0, fare, 0.001);
    }

    @Test
    void computeFare_zeroDistanceAndDurationReturnsBaseFare() {
        double fare = calculator.computeFare(0.0, 0.0);
        assertEquals(BASE_FARE, fare, 0.001);
    }

    @Test
    void computeFare_onlyDistanceNoTime() {
        double fare = calculator.computeFare(5.0, 0.0);
        assertEquals(BASE_FARE + 5.0 * PER_KM, fare, 0.001); // 5 + 12.5 = 17.5
    }

    @Test
    void computeFare_onlyTimeNoDistance() {
        double fare = calculator.computeFare(0.0, 10.0);
        assertEquals(BASE_FARE + 10.0 * PER_MIN, fare, 0.001); // 5 + 5 = 10
    }

    // ── HIGH trust (>90) → USE_ACTUALS ──

    @Test
    void calculate_highTrustUsesActuals() {
        FareCalculator.FareResult result = calculator.calculate(
                10.0, 20.0,  // planned
                12.0, 22.0,  // raw
                11.0, 21.0,  // corrected
                95.0);        // trust

        assertEquals(BillingDecision.USE_ACTUALS, result.decision());
    }

    @Test
    void calculate_highTrustFinalFareEqualsCorrectedFare() {
        double correctedDist = 11.0;
        double correctedDur = 21.0;
        double expectedCorrectedFare = BASE_FARE + correctedDist * PER_KM + correctedDur * PER_MIN;

        FareCalculator.FareResult result = calculator.calculate(
                10.0, 20.0, 12.0, 22.0, correctedDist, correctedDur, 95.0);

        assertEquals(roundFare(expectedCorrectedFare), result.fareBreakdown().getFinalFare(), 0.01);
    }

    @Test
    void calculate_trustScoreOf91IsHigh() {
        FareCalculator.FareResult result = calculator.calculate(
                10.0, 20.0, 12.0, 22.0, 11.0, 21.0, 91.0);
        assertEquals(BillingDecision.USE_ACTUALS, result.decision());
    }

    // ── MEDIUM trust (50-90) → HYBRID ──

    @Test
    void calculate_mediumTrustUsesHybrid() {
        FareCalculator.FareResult result = calculator.calculate(
                10.0, 20.0, 12.0, 22.0, 11.0, 21.0, 70.0);
        assertEquals(BillingDecision.HYBRID, result.decision());
    }

    @Test
    void calculate_mediumTrustFinalFareIsWeightedBlend() {
        double trustScore = 70.0;
        double plannedDist = 10.0, plannedDur = 20.0;
        double correctedDist = 12.0, correctedDur = 22.0;

        double estimatedFare = BASE_FARE + plannedDist * PER_KM + plannedDur * PER_MIN;
        double correctedFare = BASE_FARE + correctedDist * PER_KM + correctedDur * PER_MIN;
        double expectedBlend = (trustScore / 100.0) * correctedFare + (1.0 - trustScore / 100.0) * estimatedFare;

        FareCalculator.FareResult result = calculator.calculate(
                plannedDist, plannedDur, 15.0, 25.0, correctedDist, correctedDur, trustScore);

        assertEquals(roundFare(expectedBlend), result.fareBreakdown().getFinalFare(), 0.01);
    }

    @Test
    void calculate_trustScoreOf50IsMedium() {
        FareCalculator.FareResult result = calculator.calculate(
                10.0, 20.0, 12.0, 22.0, 11.0, 21.0, 50.0);
        assertEquals(BillingDecision.HYBRID, result.decision());
    }

    @Test
    void calculate_trustScoreOf90IsMedium() {
        FareCalculator.FareResult result = calculator.calculate(
                10.0, 20.0, 12.0, 22.0, 11.0, 21.0, 90.0);
        assertEquals(BillingDecision.HYBRID, result.decision());
    }

    // ── LOW trust (<50) → USE_ESTIMATE ──

    @Test
    void calculate_lowTrustUsesEstimate() {
        FareCalculator.FareResult result = calculator.calculate(
                10.0, 20.0, 12.0, 22.0, 11.0, 21.0, 30.0);
        assertEquals(BillingDecision.USE_ESTIMATE, result.decision());
    }

    @Test
    void calculate_lowTrustFinalFareEqualsEstimatedFare() {
        double plannedDist = 10.0, plannedDur = 20.0;
        double expectedEstimatedFare = BASE_FARE + plannedDist * PER_KM + plannedDur * PER_MIN;

        FareCalculator.FareResult result = calculator.calculate(
                plannedDist, plannedDur, 12.0, 22.0, 11.0, 21.0, 30.0);

        assertEquals(roundFare(expectedEstimatedFare), result.fareBreakdown().getFinalFare(), 0.01);
    }

    @Test
    void calculate_trustScoreOf49IsLow() {
        FareCalculator.FareResult result = calculator.calculate(
                10.0, 20.0, 12.0, 22.0, 11.0, 21.0, 49.0);
        assertEquals(BillingDecision.USE_ESTIMATE, result.decision());
    }

    @Test
    void calculate_trustScoreZeroIsLow() {
        FareCalculator.FareResult result = calculator.calculate(
                10.0, 20.0, 12.0, 22.0, 11.0, 21.0, 0.0);
        assertEquals(BillingDecision.USE_ESTIMATE, result.decision());
    }

    // ── zero distance/duration ──

    @Test
    void calculate_zeroDistanceAndDurationReturnsBaseFareOnly() {
        FareCalculator.FareResult result = calculator.calculate(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 95.0);

        assertEquals(BASE_FARE, result.fareBreakdown().getFinalFare(), 0.01);
        assertEquals(BASE_FARE, result.fareBreakdown().getEstimatedFare(), 0.01);
    }

    // ── fare breakdown contents ──

    @Test
    void calculate_fareBreakdownContainsAllFares() {
        FareCalculator.FareResult result = calculator.calculate(
                10.0, 20.0, 12.0, 22.0, 11.0, 21.0, 95.0);

        double estimatedFare = roundFare(BASE_FARE + 10.0 * PER_KM + 20.0 * PER_MIN);
        double rawFare = roundFare(BASE_FARE + 12.0 * PER_KM + 22.0 * PER_MIN);

        assertEquals(estimatedFare, result.fareBreakdown().getEstimatedFare(), 0.01);
        assertEquals(rawFare, result.fareBreakdown().getRawFare(), 0.01);
    }

    // ── explanation non-null ──

    @Test
    void calculate_explanationIsNonNull() {
        FareCalculator.FareResult result = calculator.calculate(
                10.0, 20.0, 12.0, 22.0, 11.0, 21.0, 95.0);
        assertNotNull(result.explanation());
        assertFalse(result.explanation().isEmpty());
    }

    // ── rounding ──

    @Test
    void calculate_fareIsRoundedToTwoDecimals() {
        // Use values that would produce unrounded fare
        FareCalculator.FareResult result = calculator.calculate(
                3.333, 7.777, 3.333, 7.777, 3.333, 7.777, 95.0);

        double finalFare = result.fareBreakdown().getFinalFare();
        assertEquals(finalFare, Math.round(finalFare * 100.0) / 100.0, 1e-10,
                "Final fare should be rounded to 2 decimal places");
    }

    // ── boundary trust scores ──

    @Test
    void calculate_trustScore100IsHigh() {
        FareCalculator.FareResult result = calculator.calculate(
                10.0, 20.0, 12.0, 22.0, 11.0, 21.0, 100.0);
        assertEquals(BillingDecision.USE_ACTUALS, result.decision());
    }

    private double roundFare(double fare) {
        return Math.round(fare * 100.0) / 100.0;
    }
}
