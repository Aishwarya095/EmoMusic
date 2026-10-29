package com.eis.emomusic;

public class EmotionResult {
    public final String label;
    public final String emoji;
    public final int color;
    public final float[] probabilities;   // length 7, sum to 1.0
    public final int topIndex;

    public EmotionResult(String label, String emoji, int color,
                         float[] probabilities, int topIndex) {
        this.label = label;
        this.emoji = emoji;
        this.color = color;
        this.probabilities = probabilities;
        this.topIndex = topIndex;
    }

    public int getConfidencePercent() {
        return Math.round(probabilities[topIndex] * 100);
    }
}
