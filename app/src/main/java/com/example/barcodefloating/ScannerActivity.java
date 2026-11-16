package com.example.barcodefloating;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import java.util.List;

public class ScannerActivity extends AppCompatActivity {

    private DecoratedBarcodeView barcodeScannerView;
    private ImageButton btnToggleTorch;
    private boolean isTorchOn = false;
    private boolean isScanned = false;

    private final BarcodeCallback scanCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result == null || result.getText() == null || isScanned) {
                return;
            }
            isScanned = true;
            handleScanResult(result.getText(), result.getBarcodeFormat() != null ? result.getBarcodeFormat().name() : "BARCODE");
        }

        @Override
        public void possibleResultPoints(List<com.google.zxing.ResultPoint> resultPoints) {
            // Optional preview points
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        barcodeScannerView = findViewById(R.id.barcode_scanner_view);
        ImageButton btnClose = findViewById(R.id.btn_close_scanner);
        btnToggleTorch = findViewById(R.id.btn_toggle_torch);

        btnClose.setOnClickListener(v -> finish());

        btnToggleTorch.setOnClickListener(v -> {
            if (isTorchOn) {
                barcodeScannerView.setTorchOff();
                isTorchOn = false;
                btnToggleTorch.setImageResource(R.drawable.ic_flash_off);
            } else {
                barcodeScannerView.setTorchOn();
                isTorchOn = true;
                btnToggleTorch.setImageResource(R.drawable.ic_flash_on);
            }
        });

        barcodeScannerView.decodeSingle(scanCallback);
    }

    private void handleScanResult(String text, String format) {
        // 1. Audio and Haptic feedback
        playFeedback();

        // 2. Copy to clipboard
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Scanned Barcode", text);
            clipboard.setPrimaryClip(clip);
        }

        // 3. Save to recent scan history
        ScanHistoryManager historyManager = new ScanHistoryManager(this);
        historyManager.addScan(text, format);

        // 4. Auto-paste into focused input if Accessibility Service is enabled
        if (BarcodePasteAccessibilityService.isServiceActive()) {
            BarcodePasteAccessibilityService.schedulePaste(text);
        } else {
            Toast.makeText(this, "✓ Scanned: " + text + "\n(Copied to clipboard. Enable Accessibility to auto-paste)", Toast.LENGTH_SHORT).show();
        }

        // 5. Close scanner immediately so target application regains focus
        finish();
    }

    private void playFeedback() {
        // Short beep
        try {
            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
        } catch (Exception ignored) {
        }

        // Short vibration
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(70);
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isScanned = false;
        if (barcodeScannerView != null) {
            barcodeScannerView.resume();
            barcodeScannerView.decodeSingle(scanCallback);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (barcodeScannerView != null) {
            barcodeScannerView.pause();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return (barcodeScannerView != null && barcodeScannerView.onKeyDown(keyCode, event)) || super.onKeyDown(keyCode, event);
    }
}
