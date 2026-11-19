package com.example.avancada;

import org.junit.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.example.avancada.concurrency.CircularVibrationBuffer;
import com.example.avancada.domain.VibrationSample;
import static org.junit.Assert.*;

public class Week03ConcurrencyTest {

    @Test
    public void deveGarantirCopiaDefensivaEExecutarDezCorridasSemFalha() throws InterruptedException {
        // 1. Verifica que o snapshot devolve cópia defensiva imutável
        CircularVibrationBuffer checkBuffer = new CircularVibrationBuffer(10);
        checkBuffer.push(new VibrationSample(100L, 0.0, 0.0, 9.81, 0.05));
        List<VibrationSample> copia = checkBuffer.snapshotLatest(5);
        try {
            copia.add(new VibrationSample(200L, 0.0, 0.0, 9.81, 0.05));
            fail("A lista retornada deveria ser imutável (cópia defensiva).");
        } catch (UnsupportedOperationException expected) {
            // Comportamento esperado
        }

        // 2. Executa o teste de corrida 10 vezes consecutivas e imprime linha por linha
        System.out.println("=== TABELA DE EXECUÇÕES DE CONCORRÊNCIA (CAPACIDADE 3) ===");
        for (int execucao = 1; execucao <= 10; execucao++) {
            long inicioNs = System.nanoTime();
            final CircularVibrationBuffer buffer = new CircularVibrationBuffer(100);
            final int totalAmostrasProdutor = 2000;
            final AtomicBoolean falhaDetectada = new AtomicBoolean(false);
            final CountDownLatch startGate = new CountDownLatch(1);
            final CountDownLatch doneGate = new CountDownLatch(2);

            ExecutorService pool = Executors.newFixedThreadPool(2);

            // Thread Produtora (simula sensor em alta taxa)
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < totalAmostrasProdutor; i++) {
                        buffer.push(new VibrationSample(i * 5L, 0.1, -0.1, 9.81, 0.05));
                    }
                } catch (Exception e) {
                    falhaDetectada.set(true);
                } finally {
                    doneGate.countDown();
                }
            });

            // Thread Consumidora (simula UI/Reconciliador lendo cópias continuamente)
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < 1000; i++) {
                        List<VibrationSample> snap = buffer.snapshotLatest(50);
                        // Verifica monotonicidade dentro da cópia (garante ausência de leitura suja/rasgada)
                        for (int j = 1; j < snap.size(); j++) {
                            if (snap.get(j).getTimestampMs() <= snap.get(j - 1).getTimestampMs()) {
                                falhaDetectada.set(true);
                            }
                        }
                    }
                } catch (Exception e) {
                    falhaDetectada.set(true);
                } finally {
                    doneGate.countDown();
                }
            });

            startGate.countDown();
            assertTrue("Timeout na execução " + execucao, doneGate.await(5, TimeUnit.SECONDS));
            pool.shutdown();

            long duracaoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNs);
            assertFalse("Condição de corrida detectada na execução " + execucao, falhaDetectada.get());
            assertEquals(totalAmostrasProdutor, buffer.getTotalWritten());

            System.out.printf("Execução %02d | Escritas: %d | Tempo: %d ms | Status: PASSOU%n",
                    execucao, buffer.getTotalWritten(), duracaoMs);
        }
    }
}