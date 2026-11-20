package com.example.avancada;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
    private ProgressBar pbResidualBefore, pbResidualAfter;
    private LinearLayout cardAlert;
    private View viewStatusDot;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final GrossErrorDetector detector = new GrossErrorDetector(3.0);
    private final VibrationReconciler reconciler = new VibrationReconciler(new NoiseCharacterizer(), detector);
    private final FaultInjector faultInjector = new FaultInjector(42L);
    private int faultFramesRemaining = 0; // Mantém o alerta visual visível por ~1 segundo na apresentação

    private final Runnable uiUpdater = new Runnable() {
        @Override
        public void run() {
            updateDashboard();
            uiHandler.postDelayed(this, 200L); // Atualização fluida a 5 Hz
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
        pbResidualBefore = findViewById(R.id.pbResidualBefore);
        pbResidualAfter = findViewById(R.id.pbResidualAfter);
        cardAlert = findViewById(R.id.cardAlert);
        viewStatusDot = findViewById(R.id.viewStatusDot);

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
            tvStatus.setText("AQUISIÇÃO ATIVA EM 1º PLANO (20 Hz)");
            tvStatus.setTextColor(Color.parseColor("#38BDF8"));
            viewStatusDot.setBackgroundColor(Color.parseColor("#38BDF8"));
        });

        btnInject.setOnClickListener(v -> faultFramesRemaining = 5); // Exibe o pulso de falha por 1s (5 x 200ms)

        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, VibrationAcquisitionService.class));
            tvStatus.setText("SERVIÇO PARADO");
            tvStatus.setTextColor(Color.parseColor("#94A3B8"));
            viewStatusDot.setBackgroundColor(Color.parseColor("#64748B"));
        });

        uiHandler.post(uiUpdater);
    }

    private void updateDashboard() {
        long total = VibrationAcquisitionService.getTotalAcquiredCount();
        tvSampleCount.setText(String.format(Locale.US, "%,d", total));

        List<VibrationSample> latestList = VibrationAcquisitionService.getLatestSamplesSnapshot(1);
        if (latestList.isEmpty()) {
            return;
        }

        VibrationSample sample = latestList.get(0);
        if (faultFramesRemaining > 0) {
            List<VibrationSample> degraded = faultInjector.injectJumpFault(latestList, 6.0, 1.0);
            sample = degraded.get(0);
            faultFramesRemaining--;
        }

        double resBefore = detector.computeRawResidual(sample);
        boolean grossError = detector.isGrossError(sample);
        VibrationSample reconciled = reconciler.reconcile(sample, true);

        tvResidualBefore.setText(String.format(Locale.US, "Resíduo Bruto (Antes): %.4f m/s²", resBefore));
        // Escala visual: 0.50 m/s² preenche 100% da barra de ruído
        int progressBefore = (int) Math.min(100, Math.round((resBefore / 0.50) * 100));
        pbResidualBefore.setProgress(progressBefore);

        if (grossError || reconciled == null) {
            tvResidualAfter.setText("Resíduo Reconciliado: AMOSTRA REJEITADA");
            tvResidualAfter.setTextColor(Color.parseColor("#FCA5A5"));
            pbResidualAfter.setProgress(0);

            cardAlert.setBackgroundColor(Color.parseColor("#7F1D1D")); // Vermelho alerta
            tvGrossErrorStatus.setText("ALERTA: ERRO GROSSEIRO DETECTADO!");
        } else {
            double resAfter = detector.computeRawResidual(reconciled);
            tvResidualAfter.setText(String.format(Locale.US, "Resíduo Reconciliado (Depois): %.6f m/s²", resAfter));
            tvResidualAfter.setTextColor(Color.parseColor("#34D399"));
            pbResidualAfter.setProgress((int) Math.min(100, Math.round((resAfter / 0.50) * 100)));

            cardAlert.setBackgroundColor(Color.parseColor("#064E3B")); // Verde nominal
            tvGrossErrorStatus.setText("OPERAÇÃO NOMINAL (1g ESTÁVEL)");
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