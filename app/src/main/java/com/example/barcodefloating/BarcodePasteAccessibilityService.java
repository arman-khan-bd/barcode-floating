package com.example.barcodefloating;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
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

    /**
     * Snapshots the active focused/interacted node in the application before the camera window opens.
     */
    public static void captureCurrentFocusedNode() {
        BarcodePasteAccessibilityService service = (sInstance != null) ? sInstance.get() : null;
        if (service != null) {
            service.snapshotFocus();
        }
    }

    public void snapshotFocus() {
        try {
            // 1. Direct input focus
            AccessibilityNodeInfo node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (node == null) {
                node = findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            }
            if (node != null && isCandidateInputNode(node)) {
                saveTargetCandidate(node);
                node.recycle();
                return;
            }
            if (node != null) {
                node.recycle();
            }

            // 2. Search application window tree for focused or editable node
            List<AccessibilityNodeInfo> roots = getApplicationRoots();
            for (AccessibilityNodeInfo root : roots) {
                AccessibilityNodeInfo found = searchTree(root, true);
                if (found != null) {
                    saveTargetCandidate(found);
                    found.recycle();
                    recycleRoots(roots);
                    return;
                }
            }

            // 3. Fallback: find any candidate input node visible on screen
            for (AccessibilityNodeInfo root : roots) {
                AccessibilityNodeInfo candidate = searchTree(root, false);
                if (candidate != null) {
                    saveTargetCandidate(candidate);
                    candidate.recycle();
                    recycleRoots(roots);
                    return;
                }
            }
            recycleRoots(roots);
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
     * Schedules auto-paste after ScannerActivity finishes.
     */
    public static void schedulePaste(final String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        final BarcodePasteAccessibilityService service = (sInstance != null) ? sInstance.get() : null;
        if (service == null) {
            return;
        }

        // Attempt 1: 250ms after scanner activity finishes
        sMainHandler.postDelayed(() -> service.performPaste(text, (success) -> {
            if (success) {
                Toast.makeText(service, "✓ Pasted into input field & copied to clipboard", Toast.LENGTH_SHORT).show();
            } else {
                // Attempt 2: 650ms fallback for WebViews that re-render slowly
                sMainHandler.postDelayed(() -> service.performPaste(text, (retrySuccess) -> {
                    if (retrySuccess) {
                        Toast.makeText(service, "✓ Pasted into input field & copied to clipboard", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(service, "✓ Copied to clipboard (Tap input and paste)", Toast.LENGTH_SHORT).show();
                    }
                }), 400);
            }
        }), 250);
    }

    public interface PasteCallback {
        void onResult(boolean success);
    }

    /**
     * Bypasses website paste restrictions by simulating a physical tap to activate web focus,
     * then calling ACTION_SET_TEXT (which bypasses JavaScript onpaste blockers) and ACTION_PASTE.
     */
    public synchronized void performPaste(final String text, final PasteCallback callback) {
        final AccessibilityNodeInfo targetNode = findTargetInputNode();
        if (targetNode == null) {
            if (callback != null) callback.onResult(false);
            return;
        }

        final Rect bounds = new Rect();
        try {
            targetNode.getBoundsInScreen(bounds);
        } catch (Exception ignored) {
        }

        final Runnable executePaste = () -> {
            boolean success = false;
            try {
                // Request focus on the target node
                targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);

                // METHOD 1: ACTION_SET_TEXT (Bypasses website onpaste="return false" / preventDefault blockers)
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);

                // METHOD 2: ACTION_PASTE (From system clipboard)
                if (!success) {
                    success = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                }

                // METHOD 3: Fallback on parent container (for WebViews)
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

            if (callback != null) {
                callback.onResult(success);
            }
        };

        // If on Android 7.0+ (API 24+) and bounds are valid, simulate a real touch tap first.
        // This removes web browser blur and activates the input field before text insertion.
        if (bounds.width() > 0 && bounds.height() > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            simulateTap(bounds.centerX(), bounds.centerY(), executePaste);
        } else {
            executePaste.run();
        }
    }

    /**
     * Simulates a physical touch tap on the screen at (x, y) to activate the web input.
     */
    private void simulateTap(int x, int y, Runnable onCompleted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && x > 0 && y > 0) {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(clickPath, 0, 50);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);

            dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    sMainHandler.postDelayed(onCompleted, 80);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    onCompleted.run();
                }
            }, null);
        } else {
            onCompleted.run();
        }
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

        // 4. Search for node matching placeholder/hint or name (e.g. "Enter Worker Id or Passport No..." or "WorkerCode")
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

        // 6. Fallback: Search for any visible candidate input node in application windows
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
                String nodeHint = node.getHintText().toString();
                if (nodeHint.equalsIgnoreCase(hint)
                        || hint.toLowerCase().contains(nodeHint.toLowerCase())
                        || nodeHint.toLowerCase().contains(hint.toLowerCase())) {
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

        // 1. Explicitly marked editable
        if (node.isEditable()) {
            return true;
        }

        // 2. Has hint/placeholder text (like "Enter Worker Id or Passport No...")
        if (node.getHintText() != null && node.getHintText().length() > 0) {
            return true;
        }

        // 3. Class name indicates an input/editable field
        CharSequence className = node.getClassName();
        if (className != null) {
            String name = className.toString();
            if (name.contains("EditText") || name.contains("TextInputEditText")
                    || name.contains("AutoCompleteTextView") || name.contains("SearchField")
                    || name.contains("Input")) {
                return true;
            }
        }

        // 4. Declares text editing actions (SET_TEXT or PASTE)
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) {
            if (action.getId() == AccessibilityNodeInfo.ACTION_SET_TEXT
                    || action.getId() == AccessibilityNodeInfo.ACTION_PASTE) {
                return true;
            }
        }

        // 5. Check view ID / HTML name attribute (e.g. "WorkerCode")
        if (node.getViewIdResourceName() != null && !node.getViewIdResourceName().isEmpty()) {
            String id = node.getViewIdResourceName().toLowerCase();
            if (id.contains("search") || id.contains("code") || id.contains("worker")
                    || id.contains("input") || id.contains("text")) {
                return true;
            }
        }

        // 6. Focusable + Clickable leaf node in a web/app view
        if (node.isFocusable() && node.getChildCount() == 0) {
            if (className != null && !className.toString().contains("Button")) {
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
