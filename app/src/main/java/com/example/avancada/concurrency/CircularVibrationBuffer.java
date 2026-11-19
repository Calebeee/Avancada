package com.example.avancada.concurrency;

import com.example.avancada.domain.InvalidDomainException;
import com.example.avancada.domain.VibrationSample;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class CircularVibrationBuffer {
    private final int capacity;
    private final VibrationSample[] storage;
    private int head = 0;
    private int count = 0;
    private long totalWritten = 0;
    private final ReentrantLock lock = new ReentrantLock(true); // Lock justo para evitar starvation

    public CircularVibrationBuffer(int capacity) {
        if (capacity <= 0) {
            throw new InvalidDomainException("Capacidade do buffer concorrente deve ser maior que zero.");
        }
        this.capacity = capacity;
        this.storage = new VibrationSample[capacity];
    }

    /**
     * Região crítica de escrita: insere uma nova leitura de sensor em O(1).
     */
    public void push(VibrationSample sample) {
        if (sample == null) {
            throw new InvalidDomainException("Amostra nula não permitida no buffer.");
        }
        lock.lock();
        try {
            int writeIdx = (head + count) % capacity;
            if (count == capacity) {
                storage[head] = sample;
                head = (head + 1) % capacity;
            } else {
                storage[writeIdx] = sample;
                count++;
            }
            totalWritten++;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Região crítica de leitura: retorna uma CÓPIA DEFENSIVA imutável das últimas N amostras.
     * Nunca expõe a referência interna do array.
     */
    public List<VibrationSample> snapshotLatest(int maxItems) {
        if (maxItems <= 0) {
            return Collections.emptyList();
        }
        lock.lock();
        try {
            int toCopy = Math.min(maxItems, count);
            List<VibrationSample> defensiveCopy = new ArrayList<>(toCopy);
            int startOffset = count - toCopy;
            for (int i = 0; i < toCopy; i++) {
                int idx = (head + startOffset + i) % capacity;
                defensiveCopy.add(storage[idx]); // VibrationSample já é final e imutável
            }
            return Collections.unmodifiableList(defensiveCopy);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    public long getTotalWritten() {
        lock.lock();
        try {
            return totalWritten;
        } finally {
            lock.unlock();
        }
    }
}