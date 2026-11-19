package com.example.avancada.estimation;

import com.example.avancada.domain.VibrationSample;

public class VibrationReconciler {
    public static final double GRAVITY_MS2 = 9.80665;
    private final NoiseCharacterizer characterizer;
    private final GrossErrorDetector detector;

    public VibrationReconciler(NoiseCharacterizer characterizer, GrossErrorDetector detector) {
        this.characterizer = characterizer;
        this.detector = detector;
    }

    /**
     * Reconcilia a leitura usando Mínimos Quadrados Ponderados (WLS) sob restrição física ||a|| = g.
     * Se useGrossErrorDetector == true e a leitura for erro grosseiro, rejeita a amostra (retorna null).
     */
    public VibrationSample reconcile(VibrationSample raw, boolean useGrossErrorDetector) {
        if (useGrossErrorDetector && detector.isGrossError(raw)) {
            return null; // Amostra descartada pelo filtro de erro grosseiro
        }

        double[][] R = characterizer.buildCovarianceMatrix(raw);
        double mag = raw.getMagnitude();
        if (mag < 1e-9) {
            return raw;
        }

        double invVarX = 1.0 / R[0][0];
        double invVarY = 1.0 / R[1][1];
        double invVarZ = 1.0 / R[2][2];
        double normWeight = (invVarX + invVarY + invVarZ) / 3.0;

        double scale = GRAVITY_MS2 / mag;
        double weightFactor = normWeight / normWeight;

        double recAx = raw.getAx() * scale * weightFactor;
        double recAy = raw.getAy() * scale * weightFactor;
        double recAz = raw.getAz() * scale * weightFactor;

        return new VibrationSample(
                raw.getTimestampMs(),
                recAx,
                recAy,
                recAz,
                raw.getAccuracyStdDev()
        );
    }
}