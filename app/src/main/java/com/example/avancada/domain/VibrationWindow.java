package com.example.avancada.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VibrationWindow {
    private final int maxCapacity;
    private final VibrationSample[] buffer;
    private int head = 0;
    private int count = 0;

    public VibrationWindow(int maxCapacity) {
        if (maxCapacity <= 0) {
            throw new InvalidDomainException("A capacidade máxima da janela deve ser maior que zero.");
        }
        this.maxCapacity = maxCapacity;
        this.buffer = new VibrationSample[maxCapacity];
    }

    /**
     * Adiciona uma amostra mantendo memória estritamente constante O(1).
     * Se atingir a capacidade máxima, sobrescreve a amostra mais antiga.
     */
    public void add(VibrationSample sample) {
        if (sample == null) {
            throw new InvalidDomainException("Amostra nula não pode ser inserida na janela.");
        }
        if (count > 0) {
            VibrationSample latest = getLatest();
            if (sample.getTimestampMs() < latest.getTimestampMs()) {
                throw new InvalidDomainException("Violação de monotonicidade temporal: timestamp anterior ao último registro.");
            }
        }

        int writeIndex = (head + count) % maxCapacity;
        if (count == maxCapacity) {
            buffer[head] = sample;
            head = (head + 1) % maxCapacity;
        } else {
            buffer[writeIndex] = sample;
            count++;
        }
    }

    public int size() {
        return count;
    }

    public int getCapacity() {
        return maxCapacity;
    }

    public VibrationSample getLatest() {
        if (count == 0) return null;
        int lastIndex = (head + count - 1) % maxCapacity;
        return buffer[lastIndex];
    }

    /**
     * Calcula a média da norma da aceleração na janela em tempo linear O(N).
     */
    public double getMeanMagnitude() {
        if (count == 0) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            int idx = (head + i) % maxCapacity;
            sum += buffer[idx].getMagnitude();
        }
        return sum / count;
    }

    /**
     * Retorna uma lista imutável com o recorte temporal [startTimeMs, endTimeMs] em tempo linear O(N).
     */
    public List<VibrationSample> sliceByTime(long startTimeMs, long endTimeMs) {
        if (startTimeMs > endTimeMs) {
            throw new InvalidDomainException("Intervalo temporal inválido: início maior que o fim.");
        }
        List<VibrationSample> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = (head + i) % maxCapacity;
            VibrationSample s = buffer[idx];
            if (s.getTimestampMs() >= startTimeMs && s.getTimestampMs() <= endTimeMs) {
                result.add(s);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
