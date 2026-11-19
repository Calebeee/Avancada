package com.example.avancada;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import com.example.avancada.domain.VibrationSample;
import com.example.avancada.estimation.GrossErrorDetector;
import com.example.avancada.validation.FaultInjector;
import static org.junit.Assert.*;

public class Week04FaultInjectionTest {

    private List<VibrationSample> gerarSerieBase(int n) {
        List<VibrationSample> serie = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            // Regime normal em torno de 1g (9.80665 m/s²) com acurácia declarada de 0.10 m/s²
            serie.add(new VibrationSample(i * 50L, 0.0, 0.0, 9.80665, 0.10));
        }
        return serie;
    }

    @Test
    public void deveReproduzirMesmaSaidaComSementeFixaEGerarTabelaDeSensibilidade() {
        long sementeFixa = 42L;
        FaultInjector injector1 = new FaultInjector(sementeFixa);
        FaultInjector injector2 = new FaultInjector(sementeFixa);
        GrossErrorDetector detector = new GrossErrorDetector(3.0); // Corte em 3 * 0.10 = 0.30 m/s²

        List<VibrationSample> base = gerarSerieBase(200);

        // 1. Prova de reprodutibilidade determinística (mesma semente -> resultados idênticos)
        List<VibrationSample> runA = injector1.injectJumpFault(base, 0.50, 0.25);
        List<VibrationSample> runB = injector2.injectJumpFault(base, 0.50, 0.25);
        assertEquals(runA.size(), runB.size());
        for (int i = 0; i < runA.size(); i++) {
            assertEquals(runA.get(i).getAz(), runB.get(i).getAz(), 1e-9);
        }

        // 2. Prova de degradação por perda de pacotes
        List<VibrationSample> comPerda = injector1.injectPacketLoss(base, 0.20);
        assertTrue(comPerda.size() < base.size());

        // 3. Tabela de sensibilidade da detecção conforme magnitude do salto injetado
        System.out.println("=== TABELA DE SENSIBILIDADE (SEMENTE = " + sementeFixa + ", LIMIAR = 3.0 sigma) ===");
        System.out.println("Salto_Z (m/s²) | Injetados | Detectados | Taxa_Deteccao (%)");

        double[] magnitudesSalto = { 0.05, 0.15, 0.25, 0.30, 0.35, 0.50, 1.00 };
        double taxaMinima = 100.0;
        double taxaMaxima = 0.0;

        for (double salto : magnitudesSalto) {
            List<VibrationSample> degradada = injector1.injectJumpFault(base, salto, 0.30);
            int injetados = 0;
            int detectados = 0;

            for (int i = 0; i < base.size(); i++) {
                boolean foiInjetado = Math.abs(degradada.get(i).getAz() - base.get(i).getAz()) > 1e-6;
                if (foiInjetado) {
                    injetados++;
                    if (detector.isGrossError(degradada.get(i))) {
                        detectados++;
                    }
                }
            }

            double taxa = (injetados > 0) ? (100.0 * detectados / injetados) : 0.0;
            if (taxa < taxaMinima) taxaMinima = taxa;
            if (taxa > taxaMaxima) taxaMaxima = taxa;

            System.out.printf("%-14.2f | %-9d | %-10d | %.1f%%%n", salto, injetados, detectados, taxa);
        }

        // Verifica que saltos pequenos passam despercebidos (limite de sensibilidade) e saltos grandes são 100% pegos
        assertEquals(0.0, taxaMinima, 1e-6);
        assertEquals(100.0, taxaMaxima, 1e-6);
    }
}