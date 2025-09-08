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
    public static final String TARGET_APP_PACKAGE = "com.kakao.talk";
    // public static final String TARGET_APP_PACKAGE = "jp.naver.line.android";
    private static final java.util.Queue<String> recentMessages = new java.util.LinkedList<>();
    public static java.util.List<String> getRecentMessages(int count) {
        return new java.util.ArrayList<>(recentMessages);
    }

    // 그리고 메시지 처리할 때 recentMessages에 추가
    private void addMessageToRecent(String message) {
        if (recentMessages.size() >= 5) {
            recentMessages.poll();
        }
        recentMessages.offer(message);
    }

    // 디버깅용: 노드와 모든 자식 노드의 정보를 재귀적으로 출력하는 함수
    private void printNodeHierarchy(AccessibilityNodeInfo node, String indent) {
        if (node == null) {
            return;
        }

// 현재 노드의 정보 (클래스 이름, 텍스트, contentDescription) 출력
        String log = String.format("%s[%s] text: '%s', desc: '%s'",
                indent,
                node.getClassName(),
                node.getText(),
                node.getContentDescription());
        Log.d(TAG, log);

// 모든 자식 노드에 대해 재귀적으로 함수 호출
        for (int i = 0; i < node.getChildCount(); i++) {
            printNodeHierarchy(node.getChild(i), indent + " ");
        }
    }

    /**
     * 접근성 이벤트 발생 시 호출됨
     */

// MyAccessibilityService.java

// ... (클래스 선언, TAG, lastSpeaker 변수 등은 그대로 둡니다) ...

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

// ================== [수정된 부분] ==================
        AccessibilityNodeInfo recyclerView = null;

// 1. ID로 RecyclerView를 찾는 것을 우선 시도 (가장 정확)
        java.util.List<AccessibilityNodeInfo> listNodes = rootNode.findAccessibilityNodeInfosByViewId("com.kakao.talk:id/chat_log_list");
        if (listNodes != null && !listNodes.isEmpty()) {
            recyclerView = listNodes.get(0);
            Log.d(TAG, "Successfully found RecyclerView by ID.");
        } else {
// 2. ID 찾기 실패 시, 이벤트 소스가 RecyclerView인지 확인 (대체 로직)
            Log.w(TAG, "Could not find RecyclerView by ID. Trying fallback with event source...");
            AccessibilityNodeInfo source = event.getSource();
            if (source != null && "androidx.recyclerview.widget.RecyclerView".equals(source.getClassName())) {
                recyclerView = source;
                Log.d(TAG, "Fallback successful: using event.getSource() as RecyclerView.");
            }
        }

// 두 방법 모두 실패하면 더 이상 진행하지 않음
        if (recyclerView == null) {
            Log.e(TAG, "Ultimately failed to find RecyclerView node. Aborting parse.");
            return;
        }
// ================================================

        StringBuilder messagesToBroadcast = new StringBuilder();

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            AccessibilityNodeInfo node = recyclerView.getChild(i);
            if (node == null || !"android.widget.FrameLayout".equals(node.getClassName())) continue;

            CharSequence name = null;
            CharSequence text = null;

// CASE 1: 날짜 구분선
            if (node.getChildCount() == 1 && isChildButton(node, 0)) {
                continue;
            }

// CASE 2: 상대방의 첫 메시지 (이름 있음)
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
// CASE 3: '나'의 메시지 또는 상대방의 연속 메시지 (이름 없음)
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
            Log.e(TAG, "Captured Block:\n" + result);
            addMessageToRecent(result);
            Intent intent = new Intent(MyAccessibilityService.ACTION_NOTIFICATION_BROADCAST);
            intent.putExtra(MyAccessibilityService.EXTRA_TEXT, result);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    // 이미지 뷰를 재귀적으로 찾는 헬퍼 함수 추가
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

// LocalBroadcastManager를 사용하도록 수정 권장
// getApplicationContext().sendBroadcast(intent); -> LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

    // 특정 노드 및 하위 노드에서 텍스트 수집
    private String getAllText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder text = new StringBuilder();
// TextView의 텍스트 또는 ContentDescription을 수집
        if ("android.widget.TextView".equals(node.getClassName())) {
            if (node.getText() != null) text.append(node.getText());
            else if (node.getContentDescription() != null) text.append(node.getContentDescription());
        }
// 자식 노드를 재귀적으로 탐색하여 텍스트를 수집
        for (int i = 0; i < node.getChildCount(); i++) {
            text.append(getAllText(node.getChild(i)));
        }
        return text.toString();
    }

    // 자식 노드 클래스 비교
    private boolean checkChildClass(AccessibilityNodeInfo node, int index, String className) {
        AccessibilityNodeInfo child = node.getChild(index);
        return child != null && className.equals(child.getClassName());
    }

    // 자식 타입 확인 함수 모음
    private boolean isChildButton(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "android.widget.Button"); }
    private boolean isChildTextView(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "android.widget.TextView"); }
    private boolean isChildImageView(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "android.widget.ImageView"); }
    private boolean isChildRecyclerView(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "androidx.recyclerview.widget.RecyclerView"); }
    private boolean isChildFrameLayout(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "android.widget.FrameLayout"); }
    private boolean isChildLinearLayout(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "android.widget.LinearLayout"); }
    private boolean isChildRelativeLayout(AccessibilityNodeInfo node, int i) { return checkChildClass(node, i, "android.widget.RelativeLayout"); }

    // 좌측 위치 기준으로 내가 보낸 메시지인지 확인
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
