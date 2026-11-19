package com.example.avancada.concurrency;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import com.example.avancada.domain.VibrationSample;
import com.example.avancada.estimation.GrossErrorDetector;
import com.example.avancada.estimation.NoiseCharacterizer;
import com.example.avancada.estimation.VibrationReconciler;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VibrationAcquisitionService extends Service implements SensorEventListener {

    public static final String CHANNEL_ID = "gat108_vibration_channel";
    private static final int NOTIFICATION_ID = 108;
    private static final long SAMPLING_PERIOD_MS = 50L; // 20 Hz (50 ms)

    // Referência estática segura para a UI consultar cópias defensivas
    private static volatile VibrationAcquisitionService instance;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private PowerManager.WakeLock wakeLock;
    private ScheduledExecutorService periodicExecutor;
    private BufferedWriter csvWriter;

    // Última leitura bruta atualizada pelo callback de hardware
    private volatile float lastAx = 0f;
    private volatile float lastAy = 0f;
    private volatile float lastAz = 9.80665f;
    private volatile double sensorStdDev = 0.05;

    private final CircularVibrationBuffer sharedBuffer = new CircularVibrationBuffer(600); // 30s a 20Hz
    private final GrossErrorDetector detector = new GrossErrorDetector(3.0);
    private final VibrationReconciler reconciler = new VibrationReconciler(new NoiseCharacterizer(), detector);
    private long sampleCounter = 0;

    public static List<VibrationSample> getLatestSamplesSnapshot(int n) {
        if (instance == null) {
            return Collections.emptyList();
        }
        return instance.sharedBuffer.snapshotLatest(n);
    }

    public static long getTotalAcquiredCount() {
        return (instance != null) ? instance.sampleCounter : 0L;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GAT108 — Monitoramento Ativo")
                .setContentText("Aquisição periódica de vibração (50 ms) em execução...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        // Mantém a CPU ativa mesmo com a tela bloqueada por 30 minutos
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GAT108::VibrationWakeLock");
            wakeLock.acquire(35 * 60 * 1000L);
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                double res = accelerometer.getResolution();
                if (res > 0) {
                    sensorStdDev = Math.max(res, 0.02);
                }
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
        }

        initLogFile();

        // Tarefa periódica de período fixo (50 ms)
        periodicExecutor = Executors.newSingleThreadScheduledExecutor();
        periodicExecutor.scheduleAtFixedRate(this::acquireAndProcessTick, 0, SAMPLING_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    private void initLogFile() {
        try {
            File dir = getExternalFilesDir(null);
            File logFile = new File(dir, "vibration_log_" + System.currentTimeMillis() + ".csv");
            csvWriter = new BufferedWriter(new FileWriter(logFile, true));
            csvWriter.write("seq,timestamp_ms,ax,ay,az,mag_raw,mag_reconciled,gross_error\n");
            csvWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void acquireAndProcessTick() {
        long now = System.currentTimeMillis();
        VibrationSample raw = new VibrationSample(now, lastAx, lastAy, lastAz, sensorStdDev);
        sharedBuffer.push(raw);

        boolean isError = detector.isGrossError(raw);
        VibrationSample reconciled = reconciler.reconcile(raw, true);
        double magRec = (reconciled != null) ? reconciled.getMagnitude() : -1.0;

        sampleCounter++;
        if (csvWriter != null) {
            try {
                csvWriter.write(String.format(Locale.US, "%d,%d,%.5f,%.5f,%.5f,%.5f,%.5f,%b\n",
                        sampleCounter, now, raw.getAx(), raw.getAy(), raw.getAz(),
                        raw.getMagnitude(), magRec, isError));
                if (sampleCounter % 20 == 0) {
                    csvWriter.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            lastAx = event.values[0];
            lastAy = event.values[1];
            lastAz = event.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        switch (accuracy) {
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                sensorStdDev = 0.03;
                break;
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                sensorStdDev = 0.08;
                break;
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                sensorStdDev = 0.20;
                break;
            default:
                sensorStdDev = 0.50;
                break;
        }
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (periodicExecutor != null) {
            periodicExecutor.shutdownNow();
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (csvWriter != null) {
            try {
                csvWriter.flush();
                csvWriter.close();
            } catch (IOException ignored) {}
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Aquisição de Vibração GAT108",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}