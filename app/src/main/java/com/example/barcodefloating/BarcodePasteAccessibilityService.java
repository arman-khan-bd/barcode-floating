package com.example.barcodefloating;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class BarcodePasteAccessibilityService extends AccessibilityService {

    private static WeakReference<BarcodePasteAccessibilityService> sInstance;
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    private AccessibilityNodeInfo mLastInteractedNode;
    private String mLastHintText = null;
    private String mLastViewId = null;
    private final Rect mLastBounds = new Rect();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = new WeakReference<>(this);

        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 50;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        }
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED
                || eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
                || eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                || eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {

            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                CharSequence pkg = event.getPackageName();
                if (pkg != null && pkg.toString().equals(getPackageName())) {
                    source.recycle();
                    return;
                }

                if (isCandidateInputNode(source) || source.isFocused()) {
                    saveTargetCandidate(source);
                }
                source.recycle();
            }
        }
    }

    private synchronized void saveTargetCandidate(AccessibilityNodeInfo node) {
        if (node == null) return;

        if (mLastInteractedNode != null) {
            try {
                mLastInteractedNode.recycle();
            } catch (Exception ignored) {
            }
        }
        mLastInteractedNode = AccessibilityNodeInfo.obtain(node);

        if (node.getHintText() != null) {
            mLastHintText = node.getHintText().toString();
        }
        if (node.getViewIdResourceName() != null) {
            mLastViewId = node.getViewIdResourceName();
        }
        try {
            node.getBoundsInScreen(mLastBounds);
        } catch (Exception ignored) {
        }
    }

    public static void captureCurrentFocusedNode() {
        BarcodePasteAccessibilityService service = (sInstance != null) ? sInstance.get() : null;
        if (service != null) {
            service.snapshotFocus();
        }
    }

    public void snapshotFocus() {
        try {
            AccessibilityNodeInfo node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (node == null) {
                node = findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            }
            if (node != null) {
                saveTargetCandidate(node);
                node.recycle();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLastInteractedNode != null) {
            try {
                mLastInteractedNode.recycle();
            } catch (Exception ignored) {
            }
            mLastInteractedNode = null;
        }
        sInstance = null;
    }

    public static boolean isServiceActive() {
        return sInstance != null && sInstance.get() != null;
    }

    /**
     * Schedules paste attempts progressively (200ms, 500ms, 900ms) to accommodate
     * WebView/Chrome virtual DOM re-attachment delays when returning from scanner.
     */
    public static void schedulePaste(final String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        final BarcodePasteAccessibilityService service = (sInstance != null) ? sInstance.get() : null;
        if (service == null) {
            return;
        }

        // Attempt 1: 200ms
        sMainHandler.postDelayed(() -> {
            boolean pasted = service.performPaste(text);
            if (pasted) {
                Toast.makeText(service, "✓ Pasted into input field & copied to clipboard", Toast.LENGTH_SHORT).show();
                return;
            }

            // Attempt 2: 500ms (vital for Chromium/WebViews to restore virtual nodes)
            sMainHandler.postDelayed(() -> {
                boolean retry1 = service.performPaste(text);
                if (retry1) {
                    Toast.makeText(service, "✓ Pasted into input field & copied to clipboard", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Attempt 3: 900ms fallback
                sMainHandler.postDelayed(() -> {
                    boolean retry2 = service.performPaste(text);
                    if (retry2) {
                        Toast.makeText(service, "✓ Pasted into input field & copied to clipboard", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(service, "✓ Copied to clipboard (Tap input and paste)", Toast.LENGTH_SHORT).show();
                    }
                }, 400);
            }, 300);
        }, 200);
    }

    /**
     * Searches for the target input across native and WebView/HTML nodes and pastes the text.
     */
    public synchronized boolean performPaste(String text) {
        AccessibilityNodeInfo targetNode = findTargetInputNode();
        if (targetNode == null) {
            return false;
        }

        boolean success = false;

        try {
            // Step 1: Request focus on the target node
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);

            // Step 2: Try ACTION_SET_TEXT (supported since API 21)
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);

            // Step 3: If SET_TEXT wasn't accepted (common in WebViews/HTML inputs), try ACTION_PASTE
            if (!success) {
                success = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            }

            // Step 4: If still unhandled, simulate a tap to open input connection, then paste
            if (!success) {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                success = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            }

            // Step 5: WebViews sometimes process paste via their parent container
            if (!success) {
                AccessibilityNodeInfo parent = targetNode.getParent();
                if (parent != null) {
                    success = parent.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                    parent.recycle();
                }
            }
        } catch (Exception ignored) {
            success = false;
        } finally {
            targetNode.recycle();
        }

        return success;
    }

    private AccessibilityNodeInfo findTargetInputNode() {
        // 1. Direct input focus
        try {
            AccessibilityNodeInfo focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null && isCandidateInputNode(focused)) {
                return focused;
            }
        } catch (Exception ignored) {
        }

        // 2. Accessibility focus
        try {
            AccessibilityNodeInfo accFocused = findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (accFocused != null && isCandidateInputNode(accFocused)) {
                return accFocused;
            }
        } catch (Exception ignored) {
        }

        // 3. Cached last interacted node from event
        if (mLastInteractedNode != null) {
            try {
                if (mLastInteractedNode.refresh() && isCandidateInputNode(mLastInteractedNode)) {
                    return AccessibilityNodeInfo.obtain(mLastInteractedNode);
                }
            } catch (Exception ignored) {
            }
        }

        List<AccessibilityNodeInfo> roots = getApplicationRoots();

        // 4. Search for node matching last known placeholder/hint or name (e.g. "Enter Worker Id or Passport No...")
        if (mLastHintText != null || mLastViewId != null) {
            for (AccessibilityNodeInfo root : roots) {
                AccessibilityNodeInfo match = findMatchingNode(root, mLastHintText, mLastViewId);
                if (match != null) {
                    recycleRoots(roots);
                    return match;
                }
            }
        }

        // 5. Search for any currently focused editable node in application windows
        for (AccessibilityNodeInfo root : roots) {
            AccessibilityNodeInfo focused = searchTree(root, true);
            if (focused != null) {
                recycleRoots(roots);
                return focused;
            }
        }

        // 6. Fallback: Search for any visible candidate editable node in application windows
        for (AccessibilityNodeInfo root : roots) {
            AccessibilityNodeInfo visible = searchTree(root, false);
            if (visible != null) {
                recycleRoots(roots);
                return visible;
            }
        }

        recycleRoots(roots);
        return null;
    }

    private AccessibilityNodeInfo findMatchingNode(AccessibilityNodeInfo node, String hint, String viewId) {
        if (node == null) return null;

        if (isCandidateInputNode(node)) {
            if (hint != null && node.getHintText() != null) {
                if (node.getHintText().toString().equalsIgnoreCase(hint)
                        || hint.toLowerCase().contains(node.getHintText().toString().toLowerCase())
                        || node.getHintText().toString().toLowerCase().contains(hint.toLowerCase())) {
                    return AccessibilityNodeInfo.obtain(node);
                }
            }
            if (viewId != null && node.getViewIdResourceName() != null) {
                if (node.getViewIdResourceName().contains(viewId) || viewId.contains(node.getViewIdResourceName())) {
                    return AccessibilityNodeInfo.obtain(node);
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findMatchingNode(child, hint, viewId);
                child.recycle();
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private AccessibilityNodeInfo searchTree(AccessibilityNodeInfo node, boolean mustBeFocused) {
        if (node == null) return null;

        if (isCandidateInputNode(node)) {
            if (mustBeFocused) {
                if (node.isFocused() || node.isAccessibilityFocused()) {
                    return AccessibilityNodeInfo.obtain(node);
                }
            } else {
                if (node.isVisibleToUser()) {
                    return AccessibilityNodeInfo.obtain(node);
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = searchTree(child, mustBeFocused);
                child.recycle();
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private boolean isCandidateInputNode(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // Check A: Explicitly marked editable (HTML input / textarea / EditText)
        if (node.isEditable()) {
            return true;
        }

        // Check B: Native EditText classes
        CharSequence className = node.getClassName();
        if (className != null) {
            String name = className.toString();
            if (name.contains("EditText") || name.contains("TextInputEditText") || name.contains("AutoCompleteTextView")) {
                return true;
            }
        }

        // Check C: Supports text actions (SET_TEXT or PASTE)
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) {
            if (action.getId() == AccessibilityNodeInfo.ACTION_SET_TEXT
                    || action.getId() == AccessibilityNodeInfo.ACTION_PASTE) {
                return true;
            }
        }

        return false;
    }

    private List<AccessibilityNodeInfo> getApplicationRoots() {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null && !windows.isEmpty()) {
                    for (AccessibilityWindowInfo window : windows) {
                        if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                            AccessibilityNodeInfo root = window.getRoot();
                            if (root != null) {
                                roots.add(root);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (roots.isEmpty()) {
            try {
                AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
                if (activeRoot != null) {
                    roots.add(activeRoot);
                }
            } catch (Exception ignored) {
            }
        }

        return roots;
    }

    private void recycleRoots(List<AccessibilityNodeInfo> roots) {
        if (roots == null) return;
        for (AccessibilityNodeInfo root : roots) {
            if (root != null) {
                try {
                    root.recycle();
                } catch (Exception ignored) {
                }
            }
        }
        roots.clear();
    }
}
