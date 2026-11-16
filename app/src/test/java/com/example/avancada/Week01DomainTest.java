package com.example.avancada;

import org.junit.Test;
import java.util.List;
import com.example.avancada.domain.InvalidDomainException;
import com.example.avancada.domain.VibrationSample;
import com.example.avancada.domain.VibrationWindow;
import static org.junit.Assert.*;

public class Week01DomainTest {

    @Test(expected = InvalidDomainException.class)
    public void deveRejeitarAcuraciaZeroOuNegativa() {
        new VibrationSample(1000L, 0.0, 0.0, 9.81, 0.0);
    }

    @Test(expected = InvalidDomainException.class)
    public void deveRejeitarLeituraNaN() {
        new VibrationSample(1000L, Double.NaN, 0.0, 9.81, 0.05);
    }

    @Test(expected = InvalidDomainException.class)
    public void deveRejeitarOrdemTemporalInvertida() {
        VibrationWindow window = new VibrationWindow(5);
        window.add(new VibrationSample(2000L, 0.0, 0.0, 9.81, 0.05));
        window.add(new VibrationSample(1950L, 0.0, 0.0, 9.81, 0.05));
    }

    @Test
    public void deveManterMemoriaConstanteESobrescreverMaisAntigo() {
        int capacidade = 10;
        VibrationWindow window = new VibrationWindow(capacidade);

        // Insere 1000 amostras; tamanho nunca pode ultrapassar 10 (Memória Constante O(1))
        for (int i = 0; i < 1000; i++) {
            window.add(new VibrationSample(i * 50L, 0.0, 0.0, 9.81, 0.02));
            assertTrue(window.size() <= capacidade);
        }

        assertEquals(capacidade, window.size());
        assertEquals(999 * 50L, window.getLatest().getTimestampMs());
    }

    @Test
    public void deveCalcularMediaERecorteTemporalCorretamente() {
        VibrationWindow window = new VibrationWindow(5);
        window.add(new VibrationSample(100L, 0.0, 0.0, 10.0, 0.01));
        window.add(new VibrationSample(200L, 0.0, 0.0, 10.0, 0.01));
        window.add(new VibrationSample(300L, 0.0, 0.0, 10.0, 0.01));

        assertEquals(10.0, window.getMeanMagnitude(), 1e-6);

        List<VibrationSample> recorte = window.sliceByTime(150L, 300L);
        assertEquals(2, recorte.size());
        assertEquals(200L, recorte.get(0).getTimestampMs());
    }
}