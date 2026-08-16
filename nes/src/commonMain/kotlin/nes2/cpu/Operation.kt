package nes2.cpu

enum class Operation(val supportsPageCrossingPenalty: Boolean,) {
    ADC(supportsPageCrossingPenalty = true),
    AND(supportsPageCrossingPenalty = true),
    ORA(supportsPageCrossingPenalty = true),
    EOR(supportsPageCrossingPenalty = true),
    LDA(supportsPageCrossingPenalty = true),
    CMP(supportsPageCrossingPenalty = true),
    SBC(supportsPageCrossingPenalty = true),
    LDX(supportsPageCrossingPenalty = true),
}