package com.example.avancada.domain;

public final class VibrationSample {
    private final long timestampMs;
    private final double ax; // m/s²
    private final double ay; // m/s²
    private final double az; // m/s²
    private final double accuracyStdDev; // Incerteza (desvio padrão) informada pelo sensor

    public VibrationSample(long timestampMs, double ax, double ay, double az, double accuracyStdDev) {
        if (timestampMs < 0) {
            throw new InvalidDomainException("Timestamp não pode ser negativo.");
        }
        if (Double.isNaN(ax) || Double.isInfinite(ax) ||
                Double.isNaN(ay) || Double.isInfinite(ay) ||
                Double.isNaN(az) || Double.isInfinite(az)) {
            throw new InvalidDomainException("Leituras de aceleração contêm valores inválidos (NaN/Infinito).");
        }
        if (Double.isNaN(accuracyStdDev) || Double.isInfinite(accuracyStdDev) || accuracyStdDev <= 0.0) {
            throw new InvalidDomainException("A acurácia (desvio padrão) do sensor deve ser estritamente positiva.");
        }

        this.timestampMs = timestampMs;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.accuracyStdDev = accuracyStdDev;
    }

    public long getTimestampMs() { return timestampMs; }
    public double getAx() { return ax; }
    public double getAy() { return ay; }
    public double getAz() { return az; }
    public double getAccuracyStdDev() { return accuracyStdDev; }

    /**
     * Retorna a norma vetorial euclidiana da aceleração: ||a|| = sqrt(ax² + ay² + az²).
     * Em repouso ou regime uniforme, deve convergir para ~9.80665 m/s² (1g).
     */
    public double getMagnitude() {
        return Math.sqrt(ax * ax + ay * ay + az * az);
    }
}