package io.github.metavee.machinetobeacursedother;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A camera QR-code scanner for viewer calibration. Shows a live back-camera preview and runs
 * ML Kit barcode scanning (QR only) over the frames; the first decoded QR is returned to the
 * caller as the raw scanned string in {@link #EXTRA_QR_TEXT}. The caller
 * ({@link MainActivity#calibrate}) feeds that string into the existing profile-resolution
 * logic, so this activity does not know anything about Cardboard profiles or URLs.
 *
 * <p>Result contract:
 * <ul>
 *   <li>{@code RESULT_OK} with {@link #EXTRA_QR_TEXT} — a QR was scanned.</li>
 *   <li>{@code RESULT_OK} without the extra — the user tapped "Enter URL manually"; the caller
 *       should open its manual-paste dialog.</li>
 *   <li>{@code RESULT_CANCELED} — the user backed out, or the camera was unavailable.</li>
 * </ul>
 */
public class QrScanActivity extends AppCompatActivity {

    public static final String EXTRA_QR_TEXT = "qr_text";

    private static final String TAG = "QrScanActivity";

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private ProcessCameraProvider cameraProvider;

    /** Debounce: once the first QR (or the manual-entry tap) is acted on, ignore later frames. */
    private volatile boolean handled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scan);

        previewView = findViewById(R.id.preview_view);
        findViewById(R.id.manual_entry_button).setOnClickListener(v -> onManualEntry());

        cameraExecutor = Executors.newSingleThreadExecutor();

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        scanner = BarcodeScanning.getClient(options);

        // Permission is requested up front in MainActivity; re-check defensively so the
        // activity is safe even if launched directly, but do not request from here.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        startCamera();
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
            } catch (Exception e) {
                Log.e(TAG, "Failed to get camera provider", e);
                Toast.makeText(this, "Couldn't open the camera", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // Analyze at ~1080p (higher-then-lower fallback) rather than the ImageAnalysis
            // default of 640x480. Dense QR codes — like the small older "Viewer profile" code
            // printed on classic Cardboard viewers — don't have enough pixels per module to
            // decode at 640x480, so they'd silently never scan; the extra resolution fixes that.
            ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                    .setResolutionStrategy(new ResolutionStrategy(
                            new Size(1920, 1080),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build();

            ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            analysis.setAnalyzer(cameraExecutor, this::analyze);

            try {
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                Log.e(TAG, "Failed to bind camera use cases", e);
                Toast.makeText(this, "Couldn't open the camera", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void analyze(@NonNull ImageProxy imageProxy) {
        if (handled) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String raw = barcode.getRawValue();
                        if (raw != null && !raw.trim().isEmpty()) {
                            onQrFound(raw);
                            return;
                        }
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Barcode scan failed", e))
                // Exactly one close per frame, regardless of success/failure.
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void onQrFound(String raw) {
        if (handled) {
            return;
        }
        handled = true;
        runOnUiThread(() -> {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }
            Intent data = new Intent();
            data.putExtra(EXTRA_QR_TEXT, raw);
            setResult(RESULT_OK, data);
            finish();
        });
    }

    private void onManualEntry() {
        if (handled) {
            return;
        }
        handled = true;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        // RESULT_OK with no EXTRA_QR_TEXT tells MainActivity to open its manual-paste dialog.
        setResult(RESULT_OK, new Intent());
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanner != null) {
            scanner.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
