package com.mockinterview.agent;

public enum Layer {
    L1_BACKGROUND("背景"),
    L2_SOLUTION("方案"),
    L3_DETAILS("细节"),
    L4_CHALLENGES("难点"),
    L5_TRADEOFF("权衡扩展");

    private final String label;

    Layer(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public Layer next() {
        return switch (this) {
            case L1_BACKGROUND -> L2_SOLUTION;
            case L2_SOLUTION -> L3_DETAILS;
            case L3_DETAILS -> L4_CHALLENGES;
            case L4_CHALLENGES -> L5_TRADEOFF;
            case L5_TRADEOFF -> L5_TRADEOFF;
        };
    }

    public boolean isLast() {
        return this == L5_TRADEOFF;
    }
}
