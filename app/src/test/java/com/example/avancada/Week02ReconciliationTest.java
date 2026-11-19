package com.example.avancada;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import com.example.avancada.domain.VibrationSample;
import com.example.avancada.estimation.GrossErrorDetector;
import com.example.avancada.estimation.NoiseCharacterizer;
import com.example.avancada.estimation.VibrationReconciler;
import static org.junit.Assert.*;

public class Week02ReconciliationTest {

    @Test
    public void deveReduzirResiduoEmTresJanelasDeDados() {
        NoiseCharacterizer characterizer = new NoiseCharacterizer();
        GrossErrorDetector detector = new GrossErrorDetector(3.0);
        VibrationReconciler reconciler = new VibrationReconciler(characterizer, detector);

        List<List<VibrationSample>> janelas = Arrays.asList(
                Arrays.asList(
                        new VibrationSample(100L, 0.12, -0.08, 9.95, 0.10),
                        new VibrationSample(150L, -0.10, 0.11, 9.68, 0.10)
                ),
                Arrays.asList(
                        new VibrationSample(200L, 0.05, 0.14, 9.92, 0.10),
                        new VibrationSample(250L, -0.09, -0.06, 9.70, 0.10)
                ),
                Arrays.asList(
                        new VibrationSample(300L, 0.15, 0.02, 9.98, 0.10),
                        new VibrationSample(350L, -0.12, 0.09, 9.65, 0.10)
                )
        );

        for (int w = 0; w < janelas.size(); w++) {
            double residuoAntes = 0.0;
            double residuoDepois = 0.0;

            for (VibrationSample s : janelas.get(w)) {
                residuoAntes += detector.computeRawResidual(s);
                VibrationSample reconciliada = reconciler.reconcile(s, true);
                assertNotNull(reconciliada);
                residuoDepois += detector.computeRawResidual(reconciliada);
            }

            System.out.printf("Janela %d -> Resíduo Antes: %.4f m/s² | Resíduo Depois: %.6f m/s²%n",
                    w + 1, residuoAntes, residuoDepois);
            assertTrue("Resíduo deve cair após reconciliação na janela " + (w + 1), residuoDepois < residuoAntes);
        }
    }

    @Test
    public void deveDetectarErroGrosseiroEProvarQueSemDetectorReconciliacaoPiora() {
        NoiseCharacterizer characterizer = new NoiseCharacterizer();
        GrossErrorDetector detector = new GrossErrorDetector(3.0);
        VibrationReconciler reconciler = new VibrationReconciler(characterizer, detector);

        // Estado real estacionário: ax = 0, ay = 0, az = 9.80665 (1g)
        // Erro grosseiro injetado: impacto lateral severo de +12.0 m/s² no eixo X (eixo Z permaneceu limpo em 9.80665)
        double valorVerdadeiroZ = 9.80665;
        VibrationSample amostraComErroGrosseiro = new VibrationSample(500L, 12.0, 0.0, valorVerdadeiroZ, 0.10);

        // 1. Com detector ativado: erro grosseiro é detectado e rejeitado antes de contaminar o sistema
        assertTrue(detector.isGrossError(amostraComErroGrosseiro));
        assertNull(reconciler.reconcile(amostraComErroGrosseiro, true));

        // 2. Sem detector: reconciliação força a norma do vetor para 1g e espalha o erro de X para o eixo Z (que estava perfeito!)
        VibrationSample reconciliadaSemFiltro = reconciler.reconcile(amostraComErroGrosseiro, false);
        assertNotNull(reconciliadaSemFiltro);

        double erroZAntes = Math.abs(amostraComErroGrosseiro.getAz() - valorVerdadeiroZ); // 0.0000
        double erroZDepoisSemFiltro = Math.abs(reconciliadaSemFiltro.getAz() - valorVerdadeiroZ); // ~3.61 m/s² de erro introduzido!

        System.out.printf("Prova Negativa -> Erro Z Antes: %.4f m/s² | Erro Z Sem Detector: %.4f m/s²%n",
                erroZAntes, erroZDepoisSemFiltro);

        assertTrue("Sem detector, a reconciliação piora a estimativa do eixo Z limpo",
                erroZDepoisSemFiltro > erroZAntes + 3.0);
    }
}