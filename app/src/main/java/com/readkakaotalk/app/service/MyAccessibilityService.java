// 기존 코드 유지
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

        // CASE 1. 새로운 메시지가 온 경우
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && rootNode.getClassName().toString().equals("android.widget.FrameLayout")) {
            if (rootNode.getChildCount() >= 1) {
                rootNode = rootNode.getChild(0);
                type = AccessibilityEvent.TYPE_VIEW_SCROLLED;
            } else {
                return;
            }
        }

        // CASE 2. 사용자가 스크롤한 경우
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED && rootNode.getClassName().toString().equals("androidx.recyclerview.widget.RecyclerView")) {
            AccessibilityNodeInfo node;
            CharSequence name = null;
            CharSequence text = null;

            for (int i = 0; i < rootNode.getChildCount(); i++) {
                node = rootNode.getChild(i);
                if (node == null || !"android.widget.FrameLayout".equals(node.getClassName().toString())) continue;

                final int childCount = node.getChildCount();

                // 날짜 노드
                if (childCount == 1 && isChildTextView(node, 0)) {
                    continue;
                }
                // 공지 노드
                else if (childCount == 1 &&
                        isChildLinearLayout(node, 0) &&
                        isChildTextView(node.getChild(0), 0) &&
                        Objects.equals(node.getChild(0).getChild(0).getText().toString(), "공지가 등록되었습니다.")) {
                    name = null;
                    continue;
                }
                // 텍스트 노드 (상대방 이름 있음)
                else if (childCount >= 3 &&
                        isChildButton(node, 0) &&
                        isChildTextView(node, 1) &&
                        (isChildRelativeLayout(node, 2) || isChildLinearLayout(node, 2)) &&
                        isChildTextView(node.getChild(2), 0)) {
                    name = node.getChild(1).getText();
                    text = node.getChild(2).getChild(0).getText();

                    // text가 null인 경우 getAllText를 사용해 텍스트 수집
                    if (text == null) {
                        text = getAllText(node.getChild(2)).trim();
                    }
                }
                // 이미지 노드 (상대방 이름 있음)
                else if (childCount >= 4 &&
                        isChildButton(node, 0) &&
                        isChildTextView(node, 1) &&
                        isChildFrameLayout(node, 2) &&
                        (isChildImageView(node.getChild(2), 0) || isChildRecyclerView(node.getChild(2), 0)) &&
                        isChildImageView(node, 3)) {
                    name = node.getChild(1).getText();
                    text = "(사진)";
                }
                // 이모티콘 노드 (상대방 이름 있음)
                else if ((childCount == 3 || childCount == 4) &&
                        isChildButton(node, 0) &&
                        isChildTextView(node, 1) &&
                        ((isChildRelativeLayout(node, 2) && isChildImageView(node.getChild(2), 0)) ||
                                isChildImageView(node, 2))) {
                    name = node.getChild(1).getText();
                    text = "(이모티콘)";
                }
                // 텍스트 노드 (상대방 이름 없음)
                else if (childCount >= 1 &&
                        isChildRelativeLayout(node, 0) &&
                        isChildTextView(node.getChild(0), 0)) {
                    name = (isSelfMessage(node.getChild(0)) ? "나" : name);
                    text = node.getChild(0).getChild(0).getText();

                    // text가 null인 경우 getAllText를 사용해 텍스트 수집
                    if (text == null) {
                        text = getAllText(node.getChild(0)).trim();
                    }
                }
                // 이미지 노드 (상대방 이름 없음)
                else if (childCount == 2 &&
                        isChildImageView(node, 0) &&
                        isChildFrameLayout(node, 1)) {
                    name = (isSelfMessage(node.getChild(0)) ? "나" : name);
                    text = "(사진)";
                }
                // 기타 노드
                else {
                    // 기타 노드의 경우 getAllText를 통해 모든 텍스트를 수집
                    text = getAllText(node).trim();
                }

                message.append(name).append(": ").append(text).append("\n"); // 이름 + 대화 내용
            }
        }

        final String m = message.toString();
        if (m.length() > 0) {
            Log.e(TAG, m);
            final Intent intent = new Intent(ACTION_NOTIFICATION_BROADCAST);
            intent.putExtra(EXTRA_TEXT, m);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    private String getAllText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder text = new StringBuilder();

        // TextView의 텍스트 또는 ContentDescription을 수집
        if ("android.widget.TextView".equals(node.getClassName())) {
            if (node.getText() != null) {
                text.append(node.getText().toString()).append(" ");
            } else if (node.getContentDescription() != null) {
                text.append(node.getContentDescription().toString()).append(" ");
            }
        }

        // 자식 노드를 재귀적으로 탐색하여 텍스트를 수집
        for (int i = 0; i < node.getChildCount(); i++) {
            text.append(getAllText(node.getChild(i)));
        }

        return text.toString();
    }

    // 여기에 isChildButton, isChildTextView 등의 헬퍼 메서드를 추가합니다.


    private boolean checkChildClass(final AccessibilityNodeInfo node, final int index, final String className) {
        final AccessibilityNodeInfo child = node.getChild(index);
        if (child == null) return false;
        final CharSequence name = child.getClassName();
        if (name == null) return false;
        return Objects.equals(name.toString(), className);
    }

    private boolean isChildButton(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "android.widget.Button");
    }

    private boolean isChildTextView(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "android.widget.TextView");
    }

    private boolean isChildImageView(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "android.widget.ImageView");
    }

    private boolean isChildRecyclerView(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "androidx.recyclerview.widget.RecyclerView");
    }

    private boolean isChildFrameLayout(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "android.widget.FrameLayout");
    }

    private boolean isChildLinearLayout(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "android.widget.LinearLayout");
    }

    private boolean isChildRelativeLayout(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "android.widget.RelativeLayout");
    }

    private boolean isSelfMessage(final AccessibilityNodeInfo node) { // 내가 보낸 메시지인지 체크
        final Rect rect = new Rect();
        node.getBoundsInScreen(rect); // 노드의 화면 위치를 기준으로 내가 보냈는지 상대가 보냈는지 알아냄
        return rect.left >= 200;
    }


    public void onServiceConnected() {
//        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
//        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
//        info.feedbackType = AccessibilityServiceInfo.DEFAULT | AccessibilityServiceInfo.FEEDBACK_VISUAL;
//        info.notificationTimeout = 500;
//        setServiceInfo(info);
    }

    @Override
    public void onInterrupt() {
        Log.e(TAG, "onInterrupt()");
    }

    private void printAllViews(AccessibilityNodeInfo nodeInfo, int depth) {
        if (nodeInfo == null) return;
        if (depth > 10) return; // Max-Depth
        // 고유한 태그 생성
        String prefix = "VIEW_STRUCTURE";  // 필터링할 키워드
        String indent = ""; // 깊이에 따른 들여쓰기

        for (int i = 0; i < depth; i++) indent += "."; // 들여쓰기
        Log.d(prefix, indent + "(" + nodeInfo.getText() + " <-- " +
                nodeInfo.getViewIdResourceName() + " / " + nodeInfo.getClassName() + ")");

        for (int i = 0; i < nodeInfo.getChildCount(); i++) {
            printAllViews(nodeInfo.getChild(i), depth + 1);
        }

    }

    private String getAllText(AccessibilityNodeInfo node, int depth) {
        if (node == null) return "";
        if (depth > 5) return ""; // Max-Depth
        final CharSequence className = node.getClassName();
        StringBuilder text = new StringBuilder();
        if (className != null && "android.widget.TextView".equals(className.toString())) {
            if (node.getText() != null) {
                text.append(node.getText().toString()).append(" ");
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            text.append(getAllText(node.getChild(i), depth + 1));
        }
        return text.toString();
    }
}