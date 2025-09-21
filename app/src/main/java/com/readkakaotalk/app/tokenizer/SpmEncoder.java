package com.readkakaotalk.app.tokenizer;

import org.sentencepiece.SentencePieceProcessor;

import java.io.IOException;

public class SpmEncoder {

    private final SentencePieceProcessor spp;
    private final int maxLen;

    // MainActivity에서 넘기는 (String path, int maxLen) 시그니처에 맞춤
    public SpmEncoder(String spModelPath, int maxLen) throws IOException {
        this.maxLen = maxLen;
        this.spp = new SentencePieceProcessor(); // JNI 바인딩 클래스
        boolean ok = spp.load(spModelPath);
        if (!ok) {
            throw new IOException("Failed to load SentencePiece model: " + spModelPath);
        }
    }

    public EncodedInput encode(String text) {
        int[] pieces = spp.encodeAsIds(text);

        int[] ids = new int[maxLen];
        int[] mask = new int[maxLen];

        int pos = 0;
        // 필요 시 CLS/SEP id는 모델에 맞게 수정
        final int CLS = 101;
        final int SEP = 102;

        if (pos < maxLen) {
            ids[pos] = CLS;
            mask[pos] = 1;
            pos++;
        }

        int take = Math.min(pieces.length, Math.max(0, maxLen - 2));
        for (int i = 0; i < take && pos < maxLen; i++) {
            ids[pos] = pieces[i];
            mask[pos] = 1;
            pos++;
        }

        if (pos < maxLen) {
            ids[pos] = SEP;
            mask[pos] = 1;
            pos++;
        }
        // 나머지는 0 패딩(이미 0)

        return new EncodedInput(ids, mask);
    }

    public static final class EncodedInput {
        public final int[] inputIds;
        public final int[] attentionMask;

        public EncodedInput(int[] ids, int[] mask) {
            this.inputIds = ids;
            this.attentionMask = mask;
        }
    }
}
