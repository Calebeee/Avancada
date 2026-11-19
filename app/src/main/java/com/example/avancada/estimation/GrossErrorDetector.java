package com.example.avancada.estimation;

import com.example.avancada.domain.VibrationSample;

public class GrossErrorDetector {
    public static final double GRAVITY_MS2 = 9.80665;
    private final double normalizedThreshold; // Ex: 3.0 (regra dos 3 sigmas)

    public GrossErrorDetector(double normalizedThreshold) {
        this.normalizedThreshold = normalizedThreshold;
    }

    /**
     * Calcula o resíduo bruto em relação à restrição física de gravidade (1g).
     */
    public double computeRawResidual(VibrationSample sample) {
        return Math.abs(sample.getMagnitude() - GRAVITY_MS2);
    }

    /**
     * Calcula o resíduo normalizado pela incerteza declarada do sensor: | ||a|| - g | / sigma.
     */
    public double computeNormalizedResidual(VibrationSample sample) {
        return computeRawResidual(sample) / sample.getAccuracyStdDev();
    }

    /**
     * Retorna true se a amostra violar o critério quantitativo de corte (erro grosseiro).
     */
    public boolean isGrossError(VibrationSample sample) {
        return computeNormalizedResidual(sample) > normalizedThreshold;
    }
}