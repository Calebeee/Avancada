package com.example.avancada.estimation;

import com.example.avancada.domain.VibrationSample;
import java.util.List;

public class NoiseCharacterizer {

    /**
     * Constrói a matriz de covariância R (3x3) usando a variância (sigma^2)
     * informada pela acurácia do próprio sensor (nunca valor arbitrado).
     */
    public double[][] buildCovarianceMatrix(VibrationSample sample) {
        double sigma = sample.getAccuracyStdDev();
        double variance = sigma * sigma;
        return new double[][] {
                { variance, 0.0,      0.0      },
                { 0.0,      variance, 0.0      },
                { 0.0,      0.0,      variance }
        };
    }

    /**
     * Calcula a razão de otimismo do sensor: (desvio padrão empírico medido) / (acurácia declarada).
     * Se > 1.0, o sensor é mais ruidoso na prática do que declara.
     */
    public double computeOptimismRatio(List<VibrationSample> staticSamples) {
        if (staticSamples == null || staticSamples.size() < 2) {
            return 1.0;
        }
        double sum = 0.0;
        double sumSigma = 0.0;
        for (VibrationSample s : staticSamples) {
            sum += s.getMagnitude();
            sumSigma += s.getAccuracyStdDev();
        }
        double mean = sum / staticSamples.size();
        double meanDeclaredSigma = sumSigma / staticSamples.size();

        double sqDiffSum = 0.0;
        for (VibrationSample s : staticSamples) {
            double diff = s.getMagnitude() - mean;
            sqDiffSum += diff * diff;
        }
        double empiricalStdDev = Math.sqrt(sqDiffSum / (staticSamples.size() - 1));
        return empiricalStdDev / meanDeclaredSigma;
    }
}