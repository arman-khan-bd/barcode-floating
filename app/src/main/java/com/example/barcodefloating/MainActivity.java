package com.example.barcodefloating;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private SwitchCompat switchFloatingService;
    private TextView tvServiceStatus;

    private MaterialButton btnGrantOverlay;
    private MaterialButton btnGrantCamera;
    private MaterialButton btnSetupAccessibility;

    private TextInputEditText etTestInput;
    private MaterialButton btnClearTestInput;
    private MaterialButton btnPasteFromClip;
    private MaterialButton btnDirectScan;

    private RecyclerView rvScanHistory;
    private TextView tvEmptyHistory;
    private MaterialButton btnClearHistory;

    private ScanHistoryManager historyManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        historyManager = new ScanHistoryManager(this);

        initViews();
        setupListeners();
    }

    private void initViews() {
        switchFloatingService = findViewById(R.id.switch_floating_service);
        tvServiceStatus = findViewById(R.id.tv_service_status);

        btnGrantOverlay = findViewById(R.id.btn_grant_overlay);
        btnGrantCamera = findViewById(R.id.btn_grant_camera);
        btnSetupAccessibility = findViewById(R.id.btn_setup_accessibility);

        etTestInput = findViewById(R.id.et_test_input);
        btnClearTestInput = findViewById(R.id.btn_clear_test_input);
        btnPasteFromClip = findViewById(R.id.btn_paste_from_clip);
        btnDirectScan = findViewById(R.id.btn_direct_scan);

        rvScanHistory = findViewById(R.id.rv_scan_history);
        tvEmptyHistory = findViewById(R.id.tv_empty_history);
        btnClearHistory = findViewById(R.id.btn_clear_history);

        rvScanHistory.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupListeners() {
        // Toggle Floating Service
        switchFloatingService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startFloatingService();
            } else {
                stopFloatingService();
            }
        });

        // Overlay Permission
        btnGrantOverlay.setOnClickListener(v -> {
            if (!PermissionHelper.hasOverlayPermission(this)) {
                PermissionHelper.requestOverlayPermission(this);
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show();
            }
        });

        // Camera Permission
        btnGrantCamera.setOnClickListener(v -> {
            if (!PermissionHelper.hasCameraPermission(this)) {
                PermissionHelper.requestCameraPermission(this);
            } else {
                Toast.makeText(this, "Camera permission already granted", Toast.LENGTH_SHORT).show();
            }
        });

        // Accessibility Service setup
        btnSetupAccessibility.setOnClickListener(v -> {
            PermissionHelper.openAccessibilitySettings(this);
            Toast.makeText(this, "Find 'Barcode Auto-Paste Service' and toggle it ON", Toast.LENGTH_LONG).show();
        });

        // Direct Scan button
        btnDirectScan.setOnClickListener(v -> {
            if (!PermissionHelper.hasCameraPermission(this)) {
                PermissionHelper.requestCameraPermission(this);
                return;
            }
            Intent intent = new Intent(this, ScannerActivity.class);
            startActivity(intent);
        });

        // Test Input buttons
        btnClearTestInput.setOnClickListener(v -> etTestInput.setText(""));

        btnPasteFromClip.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                if (item != null && item.getText() != null) {
                    etTestInput.setText(item.getText());
                    etTestInput.setSelection(etTestInput.getText().length());
                    Toast.makeText(this, "Pasted from clipboard", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
        });

        // Clear History
        btnClearHistory.setOnClickListener(v -> {
            historyManager.clearHistory();
            refreshHistoryList();
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void startFloatingService() {
        if (!PermissionHelper.hasOverlayPermission(this)) {
            switchFloatingService.setChecked(false);
            Toast.makeText(this, "Please grant 'Display over other apps' permission first", Toast.LENGTH_LONG).show();
            PermissionHelper.requestOverlayPermission(this);
            return;
        }

        // Notification permission for Android 13+
        if (!PermissionHelper.hasNotificationPermission(this)) {
            PermissionHelper.requestNotificationPermission(this);
        }

        Intent serviceIntent = new Intent(this, FloatingBarcodeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }

        updateServiceStatus(true);
    }

    private void stopFloatingService() {
        Intent serviceIntent = new Intent(this, FloatingBarcodeService.class);
        stopService(serviceIntent);
        updateServiceStatus(false);
    }

    private void updateServiceStatus(boolean running) {
        if (running) {
            tvServiceStatus.setText("Status: Floating button ACTIVE on screen");
            tvServiceStatus.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            tvServiceStatus.setText("Status: Floating button stopped");
            tvServiceStatus.setTextColor(Color.parseColor("#F57C00"));
        }
    }

    private void refreshUIState() {
        // Overlay permission status
        boolean hasOverlay = PermissionHelper.hasOverlayPermission(this);
        if (hasOverlay) {
            btnGrantOverlay.setText(R.string.status_granted);
            btnGrantOverlay.setEnabled(false);
            btnGrantOverlay.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            btnGrantOverlay.setText(R.string.btn_grant);
            btnGrantOverlay.setEnabled(true);
            btnGrantOverlay.setTextColor(Color.parseColor("#1976D2"));
        }

        // Camera permission status
        boolean hasCamera = PermissionHelper.hasCameraPermission(this);
        if (hasCamera) {
            btnGrantCamera.setText(R.string.status_granted);
            btnGrantCamera.setEnabled(false);
            btnGrantCamera.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            btnGrantCamera.setText(R.string.btn_grant);
            btnGrantCamera.setEnabled(true);
            btnGrantCamera.setTextColor(Color.parseColor("#1976D2"));
        }

        // Accessibility service status
        boolean isAccessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(this, BarcodePasteAccessibilityService.class);
        if (isAccessibilityEnabled) {
            btnSetupAccessibility.setText(R.string.status_enabled);
            btnSetupAccessibility.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            btnSetupAccessibility.setText(R.string.btn_grant);
            btnSetupAccessibility.setTextColor(Color.parseColor("#1976D2"));
        }

        // Floating Service status
        switchFloatingService.setChecked(FloatingBarcodeService.isRunning);
        updateServiceStatus(FloatingBarcodeService.isRunning);

        // Refresh scan history
        refreshHistoryList();
    }

    private void refreshHistoryList() {
        List<ScanItem> items = historyManager.getHistory();
        if (items.isEmpty()) {
            tvEmptyHistory.setVisibility(View.VISIBLE);
            rvScanHistory.setVisibility(View.GONE);
            btnClearHistory.setEnabled(false);
        } else {
            tvEmptyHistory.setVisibility(View.GONE);
            rvScanHistory.setVisibility(View.VISIBLE);
            btnClearHistory.setEnabled(true);

            ScanHistoryAdapter adapter = new ScanHistoryAdapter(items, item -> {
                // When clicking a history item, populate test input
                etTestInput.setText(item.getContent());
                etTestInput.setSelection(item.getContent().length());
                Toast.makeText(this, "Selected: " + item.getContent(), Toast.LENGTH_SHORT).show();
            });
            rvScanHistory.setAdapter(adapter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUIState();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PermissionHelper.REQ_CODE_OVERLAY) {
            refreshUIState();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionHelper.REQ_CODE_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Camera permission is required to scan barcodes", Toast.LENGTH_SHORT).show();
            }
            refreshUIState();
        }
    }
}
