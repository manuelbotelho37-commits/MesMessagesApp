package com.messageclient.app;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class MessageInsertAccessibilityService extends AccessibilityService {
    private static volatile MessageInsertAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No continuous automation: insertion only happens after the user taps a saved template.
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isConnected() {
        return instance != null;
    }

    public static boolean insertIntoCurrentMessage(String text) {
        MessageInsertAccessibilityService service = instance;
        if (service == null || text == null || text.isEmpty()) return false;

        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return false;

        CharSequence pkgCs = root.getPackageName();
        String pkg = pkgCs == null ? "" : pkgCs.toString();
        if (!isSupportedMessagingPackage(pkg)) return false;

        AccessibilityNodeInfo target = service.findFocusedEditable(root);
        if (target == null) target = service.findBestEditable(root);
        if (target == null) return false;

        CharSequence existingCs = target.getText();
        String existing = existingCs == null ? "" : existingCs.toString();
        String newText;
        if (existing.trim().isEmpty()) {
            newText = text;
        } else {
            String separator = existing.endsWith("\n") ? "" : "\n";
            newText = existing + separator + text;
        }

        Bundle args = new Bundle();
        args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
        );
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private AccessibilityNodeInfo findFocusedEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isFocused() && node.isVisibleToUser()) return node;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findFocusedEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findBestEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;

        if (node.isEditable() && node.isVisibleToUser() && node.isEnabled()) {
            return node;
        }

        for (int i = node.getChildCount() - 1; i >= 0; i--) {
            AccessibilityNodeInfo found = findBestEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private static boolean isSupportedMessagingPackage(String pkg) {
        return "com.google.android.apps.messaging".equals(pkg)
                || "com.samsung.android.messaging".equals(pkg)
                || "com.android.mms".equals(pkg);
    }
}
