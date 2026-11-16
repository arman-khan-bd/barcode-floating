package com.example.barcodefloating;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.lang.ref.WeakReference;

public class BarcodePasteAccessibilityService extends AccessibilityService {

    private static WeakReference<BarcodePasteAccessibilityService> sInstance;
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = new WeakReference<>(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Accessibility events can be monitored here if needed
    }

    @Override
    public void onInterrupt() {
        // Interrupted by system
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
    }

    public static boolean isServiceActive() {
        return sInstance != null && sInstance.get() != null;
    }

    /**
     * Schedules a paste action with a slight delay so the previous application has
     * resumed focus after ScannerActivity finishes.
     */
    public static void schedulePaste(final String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        final BarcodePasteAccessibilityService service = (sInstance != null) ? sInstance.get() : null;

        if (service == null) {
            // Service not active, paste not possible
            return;
        }

        // Delay 150ms to allow the previous window transition to finish and restore input focus
        sMainHandler.postDelayed(() -> {
            boolean pasted = service.performPaste(text);
            if (!pasted) {
                // Retry once at 350ms in case the window transition took slightly longer
                sMainHandler.postDelayed(() -> {
                    boolean retrySuccess = service.performPaste(text);
                    if (retrySuccess) {
                        Toast.makeText(service, "✓ Pasted into selected input & copied to clipboard", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(service, "✓ Copied to clipboard (No input field was selected)", Toast.LENGTH_SHORT).show();
                    }
                }, 200);
            } else {
                Toast.makeText(service, "✓ Pasted into selected input & copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        }, 150);
    }

    /**
     * Attempts to find the currently focused input and paste or set the barcode text into it.
     */
    public boolean performPaste(String text) {
        AccessibilityNodeInfo focused = null;

        // 1. First attempt: Query system for input focus
        try {
            focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        } catch (Exception ignored) {
        }

        // 2. Second attempt: Search active window root node recursively
        if (focused == null) {
            AccessibilityNodeInfo root = null;
            try {
                root = getRootInActiveWindow();
            } catch (Exception ignored) {
            }

            if (root != null) {
                focused = findFocusedEditableNode(root);
                if (focused == null) {
                    root.recycle();
                }
            }
        }

        if (focused != null) {
            boolean success = false;

            // Method A: Set text directly (Supported since API 21)
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            try {
                success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            } catch (Exception ignored) {
            }

            // Method B: If direct SET_TEXT wasn't accepted, try ACTION_PASTE (from clipboard)
            if (!success) {
                try {
                    success = focused.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                } catch (Exception ignored) {
                }
            }

            focused.recycle();
            return success;
        }

        return false;
    }

    /**
     * Helper to search for any editable focused node in an accessibility tree.
     */
    private AccessibilityNodeInfo findFocusedEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;

        if (node.isFocused() && (node.isEditable() || isEditTextClass(node.getClassName()))) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findFocusedEditableNode(child);
                if (result != null) {
                    return result;
                }
                child.recycle();
            }
        }
        return null;
    }

    private boolean isEditTextClass(CharSequence className) {
        if (className == null) return false;
        String name = className.toString();
        return name.contains("EditText") || name.contains("TextInputEditText") || name.contains("AutoCompleteTextView");
    }
}
