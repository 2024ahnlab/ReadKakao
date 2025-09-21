package com.readkakaotalk.app.tokenizer;

public final class EncodedInput {
    private final int[] inputIds;
    private final int[] attentionMask;

    public EncodedInput(int[] inputIds, int[] attentionMask) {
        this.inputIds = inputIds;
        this.attentionMask = attentionMask;
    }
    public int[] getInputIds() { return inputIds; }
    public int[] getAttentionMask() { return attentionMask; }
}
