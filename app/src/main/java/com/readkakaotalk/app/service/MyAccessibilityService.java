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
    public static final String TARGET_APP_PACKAGE = "com.kakao.talk";
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
    /**
     * 접근성 이벤트 발생 시 호출됨
     */
    @SuppressLint("ObsoleteSdkInt")
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
        int type = event.getEventType();

        if (type != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                type != AccessibilityEvent.TYPE_VIEW_SCROLLED) return;

        final String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (!Objects.equals(packageName, TARGET_APP_PACKAGE)) return;
        if (event.getClassName() == null || event.getSource() == null) return;

        AccessibilityNodeInfo rootNode = event.getSource();
        StringBuilder message = new StringBuilder();

        // CASE 1. 새로운 메시지가 온 경우 → FrameLayout → RecyclerView 재설정
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                "android.widget.FrameLayout".equals(rootNode.getClassName().toString())) {
            if (rootNode.getChildCount() >= 1) {
                rootNode = rootNode.getChild(0);
                type = AccessibilityEvent.TYPE_VIEW_SCROLLED;
            } else return;
        }

        // CASE 2. 사용자가 스크롤한 경우 → 메시지 탐색
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
                "androidx.recyclerview.widget.RecyclerView".equals(rootNode.getClassName().toString())) {

            for (int i = 0; i < rootNode.getChildCount(); i++) {
                AccessibilityNodeInfo node = rootNode.getChild(i);
                if (node == null || !"android.widget.FrameLayout".equals(node.getClassName().toString())) continue;

                CharSequence name = null;
                CharSequence text = null;
                int childCount = node.getChildCount();

                // 날짜 노드
                if (childCount == 1 && isChildTextView(node, 0)) continue;

                    // 공지 노드
                else if (childCount == 1 &&
                        isChildLinearLayout(node, 0) &&
                        isChildTextView(node.getChild(0), 0) &&
                        "공지가 등록되었습니다.".contentEquals(node.getChild(0).getChild(0).getText())) {
                    continue;
                }

                // 텍스트 메시지 (상대방 이름 있음)
                else if (childCount >= 3 &&
                        isChildButton(node, 0) &&
                        isChildTextView(node, 1) &&
                        (isChildRelativeLayout(node, 2) || isChildLinearLayout(node, 2)) &&
                        isChildTextView(node.getChild(2), 0)) {
                    name = node.getChild(1).getText();
                    text = node.getChild(2).getChild(0).getText();
                    if (text == null) text = getAllText(node.getChild(2)).trim();
                }

                // 이미지 메시지 (상대방 이름 있음)
                else if (childCount >= 4 &&
                        isChildButton(node, 0) &&
                        isChildTextView(node, 1) &&
                        isChildFrameLayout(node, 2) &&
                        (isChildImageView(node.getChild(2), 0) || isChildRecyclerView(node.getChild(2), 0)) &&
                        isChildImageView(node, 3)) {
                    name = node.getChild(1).getText();
                    text = "(사진)";
                }

                // 이모티콘 메시지 (상대방 이름 있음)
                else if ((childCount == 3 || childCount == 4) &&
                        isChildButton(node, 0) &&
                        isChildTextView(node, 1) &&
                        ((isChildRelativeLayout(node, 2) && isChildImageView(node.getChild(2), 0)) ||
                                isChildImageView(node, 2))) {
                    name = node.getChild(1).getText();
                    text = "(이모티콘)";
                }

                // 텍스트 메시지 (상대방 이름 없음)
                else if (childCount >= 1 &&
                        isChildRelativeLayout(node, 0) &&
                        isChildTextView(node.getChild(0), 0)) {
                    name = isSelfMessage(node.getChild(0)) ? "나" : name;
                    text = node.getChild(0).getChild(0).getText();
                    // text가 null인 경우 getAllText를 사용해 텍스트 수집
                    if (text == null) text = getAllText(node.getChild(0)).trim();
                }

                // 이미지 메시지 (상대방 이름 없음)
                else if (childCount == 2 &&
                        isChildImageView(node, 0) &&
                        isChildFrameLayout(node, 1)) {
                    name = isSelfMessage(node.getChild(0)) ? "나" : name;
                    text = "(사진)";
                }

                // 그 외
                else {
                    // 기타 노드의 경우 getAllText를 통해 모든 텍스트를 수집
                    text = getAllText(node).trim();
                }

                // 이름 + 대화 내용
                message.append(name).append(": ").append(text).append("\n");
            }
        }

        String result = message.toString();
        if (!result.isEmpty()) {
            Log.e(TAG, result);
            addMessageToRecent(result);
            Intent intent = new Intent(MyAccessibilityService.ACTION_NOTIFICATION_BROADCAST);
            intent.putExtra(MyAccessibilityService.EXTRA_TEXT, result);
            getApplicationContext().sendBroadcast(intent); // ✅ 명확한 context와 상수 사용

        }
    }

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
