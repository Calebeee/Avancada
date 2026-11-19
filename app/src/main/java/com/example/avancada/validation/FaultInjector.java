package com.example.avancada.validation;

import com.example.avancada.domain.VibrationSample;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FaultInjector {
    private final long seed;

    public FaultInjector(long seed) {
        this.seed = seed;
    }

    public long getSeed() {
        return seed;
    }

    /**
     * Injeta ruído gaussiano determinístico (semente fixa) sobre os três eixos.
     */
    public List<VibrationSample> injectGaussianNoise(List<VibrationSample> clean, double noiseStdDev) {
        Random rng = new Random(seed);
        List<VibrationSample> degraded = new ArrayList<>(clean.size());
        for (VibrationSample s : clean) {
            double nx = s.getAx() + rng.nextGaussian() * noiseStdDev;
            double ny = s.getAy() + rng.nextGaussian() * noiseStdDev;
            double nz = s.getAz() + rng.nextGaussian() * noiseStdDev;
            degraded.add(new VibrationSample(s.getTimestampMs(), nx, ny, nz, s.getAccuracyStdDev()));
        }
        return degraded;
    }

    /**
     * Injeta perda aleatória determinística de amostras (packet/sample drop) dada uma taxa [0.0, 1.0].
     */
    public List<VibrationSample> injectPacketLoss(List<VibrationSample> clean, double dropRate) {
        Random rng = new Random(seed);
        List<VibrationSample> degraded = new ArrayList<>();
        for (VibrationSample s : clean) {
            if (rng.nextDouble() >= dropRate) {
                degraded.add(s);
            }
        }
        return degraded;
    }

    /**
     * Injeta saltos (impulsos mecânicos de desbalanceamento/impacto) de magnitude jumpMagnitudeMs2
     * sobre o eixo Z com probabilidade jumpProbability.
     */
    public List<VibrationSample> injectJumpFault(List<VibrationSample> clean, double jumpMagnitudeMs2, double jumpProbability) {
        Random rng = new Random(seed);
        List<VibrationSample> degraded = new ArrayList<>(clean.size());
        for (VibrationSample s : clean) {
            double az = s.getAz();
            if (rng.nextDouble() < jumpProbability) {
                az += jumpMagnitudeMs2;
            }
            degraded.add(new VibrationSample(s.getTimestampMs(), s.getAx(), s.getAy(), az, s.getAccuracyStdDev()));
        }
        return degraded;
    }
}