package com.example.avancada;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.avancada.concurrency.VibrationAcquisitionService;
import com.example.avancada.domain.VibrationSample;
import com.example.avancada.estimation.GrossErrorDetector;
import com.example.avancada.estimation.NoiseCharacterizer;
import com.example.avancada.estimation.VibrationReconciler;
import com.example.avancada.validation.FaultInjector;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvSampleCount, tvResidualBefore, tvResidualAfter, tvGrossErrorStatus;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final GrossErrorDetector detector = new GrossErrorDetector(3.0);
    private final VibrationReconciler reconciler = new VibrationReconciler(new NoiseCharacterizer(), detector);
    private final FaultInjector faultInjector = new FaultInjector(42L);
    private boolean injectFaultOnce = false;

    private final Runnable uiUpdater = new Runnable() {
        @Override
        public void run() {
            updateDashboard();
            uiHandler.postDelayed(this, 250L); // Atualiza interface a 4 Hz lendo cópia defensiva
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvSampleCount = findViewById(R.id.tvSampleCount);
        tvResidualBefore = findViewById(R.id.tvResidualBefore);
        tvResidualAfter = findViewById(R.id.tvResidualAfter);
        tvGrossErrorStatus = findViewById(R.id.tvGrossErrorStatus);

        Button btnStart = findViewById(R.id.btnStartService);
        Button btnInject = findViewById(R.id.btnInjectFault);
        Button btnStop = findViewById(R.id.btnStopService);

        requestNotificationPermissionIfNeeded();

        btnStart.setOnClickListener(v -> {
            Intent intent = new Intent(this, VibrationAcquisitionService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            tvStatus.setText("Status: Adquirindo em 1º Plano (50 ms)");
        });

        btnInject.setOnClickListener(v -> injectFaultOnce = true);

        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, VibrationAcquisitionService.class));
            tvStatus.setText("Status: Parado");
        });

        uiHandler.post(uiUpdater);
    }

    private void updateDashboard() {
        long total = VibrationAcquisitionService.getTotalAcquiredCount();
        tvSampleCount.setText("Amostras adquiridas: " + total);

        List<VibrationSample> latestList = VibrationAcquisitionService.getLatestSamplesSnapshot(1);
        if (latestList.isEmpty()) {
            return;
        }

        VibrationSample sample = latestList.get(0);
        if (injectFaultOnce) {
            List<VibrationSample> degraded = faultInjector.injectJumpFault(latestList, 6.0, 1.0);
            sample = degraded.get(0);
            injectFaultOnce = false;
        }

        double resBefore = detector.computeRawResidual(sample);
        boolean grossError = detector.isGrossError(sample);
        VibrationSample reconciled = reconciler.reconcile(sample, true);

        tvResidualBefore.setText(String.format(Locale.US, "Resíduo Bruto (Antes): %.4f m/s²", resBefore));

        if (grossError || reconciled == null) {
            tvResidualAfter.setText("Resíduo Reconciliado (Depois): AMOSTRA REJEITADA");
            tvGrossErrorStatus.setText("Detector de Erro Grosseiro: ALERTA DE FALHA DETECTADA!");
        } else {
            double resAfter = detector.computeRawResidual(reconciled);
            tvResidualAfter.setText(String.format(Locale.US, "Resíduo Reconciliado (Depois): %.6f m/s²", resAfter));
            tvGrossErrorStatus.setText("Detector de Erro Grosseiro: NORMAL (1g estável)");
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(uiUpdater);
        super.onDestroy();
    }
}