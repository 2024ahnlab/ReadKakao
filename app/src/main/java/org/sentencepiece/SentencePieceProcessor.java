package org.sentencepiece;

public class SentencePieceProcessor {
    static {
        System.loadLibrary("sentencepiece");      // 네이티브 라이브러리
        System.loadLibrary("sentencepiece_jni");  // 우리가 빌드한 JNI 브리지
    }

    public native boolean load(String modelPath);
    public native int[] encodeAsIds(String text);
}
