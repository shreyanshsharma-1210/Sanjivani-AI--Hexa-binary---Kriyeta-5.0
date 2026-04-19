package com.emergency.patient.services;

import org.junit.Test;
import static org.junit.Assert.*;

public class FallDetectionPhysicsTest {

    @Test
    public void testMagnitudeCalculation() {
        // Rest state (1g on Z axis)
        float mag = FallPhysicsEngine.magnitude(0, 0, 9.8f);
        assertEquals(9.8f, mag, 0.01f);

        // Free fall state (0g)
        mag = FallPhysicsEngine.magnitude(0, 0, 0);
        assertEquals(0f, mag, 0.01f);

        // Diagonal movement
        mag = FallPhysicsEngine.magnitude(3, 4, 0); // sqrt(3^2 + 4^2) = 5
        assertEquals(5f, mag, 0.01f);
    }

    @Test
    public void testLowPassFilter() {
        float[] gravity = {0f, 0f, 9.8f};
        float[] rawInput = {5f, 0f, 9.8f}; // Sudden X movement
        float alpha = 0.8f;

        FallPhysicsEngine.applyLowPassFilter(gravity, rawInput, alpha);

        // G_new = α·G_old + (1-α)·Raw
        // X = 0.8*0 + 0.2*5 = 1.0
        assertEquals(1.0f, gravity[0], 0.01f);
        assertEquals(0.0f, gravity[1], 0.01f);
        assertEquals(9.8f, gravity[2], 0.01f);
    }

    @Test
    public void testTiltAngle() {
        // Phone standing upright (Z matches gravity)
        float[] uprightGravity = {0f, 0f, 9.8f};
        assertEquals(0f, FallPhysicsEngine.tiltAngleDegrees(uprightGravity), 1.0f);

        // Phone lying flat (X matches gravity, Z is 0)
        float[] flatGravity = {9.8f, 0f, 0f};
        assertEquals(90f, FallPhysicsEngine.tiltAngleDegrees(flatGravity), 1.0f);
    }

    @Test
    public void testVariance() {
        // Moving device: high internal variance
        float[] movingSamples = {9.8f, 15.0f, 5.0f, 12.0f, 8.0f};
        float varMoving = FallPhysicsEngine.variance(movingSamples);
        assertTrue("Variance of moving device should be high", varMoving > 1.0f);

        // Still device: zero variance
        float[] stillSamples = {9.8f, 9.8f, 9.8f, 9.8f, 9.8f};
        float varStill = FallPhysicsEngine.variance(stillSamples);
        assertEquals(0f, varStill, 0.001f);
    }

    @Test
    public void testFallLogicThresholds() {
        // Test Free Fall Threshold
        assertTrue(0.5f < FallPhysicsEngine.FREE_FALL_THRESHOLD);
        assertTrue(5.0f > FallPhysicsEngine.FREE_FALL_THRESHOLD);

        // Test Impact Thresholds
        assertEquals(24.5f, FallPhysicsEngine.getImpactThreshold("HIGH"), 0.1f);
        assertEquals(31.4f, FallPhysicsEngine.getImpactThreshold("NORMAL"), 0.1f);
        assertEquals(44.1f, FallPhysicsEngine.getImpactThreshold("RUGGED"), 0.1f);
    }
}
