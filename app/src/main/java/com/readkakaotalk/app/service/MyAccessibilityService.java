package com.readkakaotalk.app.service;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Objects;

public class MyAccessibilityService extends AccessibilityService {
    private static final String TAG = "AccessibilityService";
    public static final String ACTION_NOTIFICATION_BROADCAST = "MyAccessibilityService_LocalBroadcast";
    public static final String EXTRA_TEXT = "extra_text";
    private String lastSpeaker = null;
    private String lastProcessedText = ""; // 마지막으로 처리한 텍스트 저장 변수
    public static final String TARGET_APP_PACKAGE = "com.kakao.talk";

    private static final java.util.Queue<String> recentMessages = new java.util.LinkedList<>();
    public static java.util.List<String> getRecentMessages(int count) {
        return new java.util.ArrayList<>(recentMessages);
    }

    private void addMessageToRecent(String message) {
        if (recentMessages.size() >= 5) {
            recentMessages.poll();
        }
        recentMessages.offer(message);
    }

    private void printNodeHierarchy(AccessibilityNodeInfo node, String indent) {
        if (node == null) {
            return;
        }
        String log = String.format("%s[%s] text: '%s', desc: '%s'",
                indent,
                node.getClassName(),
                node.getText(),
                node.getContentDescription());
        Log.d(TAG, log);
        for (int i = 0; i < node.getChildCount(); i++) {
            printNodeHierarchy(node.getChild(i), indent + " ");
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
        int type = event.getEventType();

        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                type != AccessibilityEvent.TYPE_VIEW_SCROLLED) return;

        final String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (!Objects.equals(packageName, TARGET_APP_PACKAGE)) return;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        AccessibilityNodeInfo recyclerView = null;

        java.util.List<AccessibilityNodeInfo> listNodes = rootNode.findAccessibilityNodeInfosByViewId("com.kakao.talk:id/chat_log_list");
        if (listNodes != null && !listNodes.isEmpty()) {
            recyclerView = listNodes.get(0);
            Log.d(TAG, "Successfully found RecyclerView by ID.");
        } else {
            Log.w(TAG, "Could not find RecyclerView by ID. Trying fallback with event source...");
            AccessibilityNodeInfo source = event.getSource();
            if (source != null && "androidx.recyclerview.widget.RecyclerView".equals(source.getClassName())) {
                recyclerView = source;
                Log.d(TAG, "Fallback successful: using event.getSource() as RecyclerView.");
            }
        }

        if (recyclerView == null) {
            Log.e(TAG, "Ultimately failed to find RecyclerView node. Aborting parse.");
            return;
        }

        StringBuilder messagesToBroadcast = new StringBuilder();

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            AccessibilityNodeInfo node = recyclerView.getChild(i);
            if (node == null || !"android.widget.FrameLayout".equals(node.getClassName())) continue;

            CharSequence name = null;
            CharSequence text = null;

            if (node.getChildCount() == 1 && isChildButton(node, 0)) {
                continue;
            }

            if (node.getChildCount() >= 3 && isChildTextView(node, 1)) {
                name = node.getChild(1).getText();
                if (name != null) {
                    lastSpeaker = name.toString();
                }

                AccessibilityNodeInfo messageBubbleNode = node.getChild(2);
                if (messageBubbleNode != null && messageBubbleNode.getChildCount() > 0) {
                    AccessibilityNodeInfo textNode = messageBubbleNode.getChild(0);
                    if (textNode != null) {
                        text = textNode.getContentDescription();
                        if (text == null) text = textNode.getText();
                    }
                }
            }
            else if (node.getChildCount() == 1 && node.getChild(0) != null && "android.view.ViewGroup".equals(node.getChild(0).getClassName())) {
                AccessibilityNodeInfo messageBubbleNode = node.getChild(0);

                if (isSelfMessage(messageBubbleNode)) {
                    name = "나";
                } else {
                    name = lastSpeaker;
                }

                if (messageBubbleNode.getChildCount() > 0) {
                    AccessibilityNodeInfo textNode = messageBubbleNode.getChild(0);
                    if (textNode != null) {
                        text = textNode.getContentDescription();
                        if (text == null) text = textNode.getText();
                    }
                }
            }

            if (name != null && text != null && text.length() > 0) {
                String fullMessage = name.toString().trim() + ": " + text.toString().trim() + "\n";
                messagesToBroadcast.append(fullMessage);
            }
        }

        String result = messagesToBroadcast.toString();
        if (!result.isEmpty()) {
            // ================== [ 변경된 부분 시작 ] ==================
            // 마지막으로 보낸 내용과 동일하다면, 다시 보내지 않고 함수를 종료합니다.
            if (result.equals(lastProcessedText)) {
                return;
            }
            // 마지막으로 보낸 내용을 현재 내용으로 업데이트합니다.
            lastProcessedText = result;
            // ================== [  변경된 부분 끝  ] ==================

            Log.e(TAG, "Captured Block:\n" + result);
            addMessageToRecent(result);
            Intent intent = new Intent(MyAccessibilityService.ACTION_NOTIFICATION_BROADCAST);
            intent.putExtra(MyAccessibilityService.EXTRA_TEXT, result);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    private boolean findImageView(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if ("android.widget.ImageView".equals(node.getClassName())) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (findImageView(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private String getAllText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder text = new StringBuilder();
        if ("android.widget.TextView".equals(node.getClassName())) {
            if (node.getText() != null) text.append(node.getText());
            else if (node.getContentDescription() != null) text.append(node.getContentDescription());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            text.append(getAllText(node.getChild(i)));
        }
        return text.toString();
    }

    private boolean checkChildClass(AccessibilityNodeInfo node, int index, String className) {
        AccessibilityNodeInfo child = node.getChild(index);
        return child != null && className.equals(child.getClassName());
    }

    private boolean isChildButton(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "android.widget.Button"); }
    private boolean isChildTextView(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "android.widget.TextView"); }
    private boolean isChildImageView(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "android.widget.ImageView"); }
    private boolean isChildRecyclerView(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "androidx.recyclerview.widget.RecyclerView"); }
    private boolean isChildFrameLayout(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "android.widget.FrameLayout"); }
    private boolean isChildLinearLayout(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "android.widget.LinearLayout"); }
    private boolean isChildRelativeLayout(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "android.widget.RelativeLayout"); }

    private boolean isSelfMessage(AccessibilityNodeInfo node) {
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        return rect.left >= 200;
    }

    @Override
    public void onInterrupt() {
        Log.e(TAG, "onInterrupt()");
    }

    @Override
    public void onServiceConnected() {
        // 연결 시 설정 가능 (사용 안함)
    }
}
