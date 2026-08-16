package nes2.cpu

enum class Operation(val pageCrossingPenalty: Boolean,) {
    ADC(pageCrossingPenalty = true),
    AND(pageCrossingPenalty = true),
    ORA(pageCrossingPenalty = true),
    EOR(pageCrossingPenalty = true),
    LDA(pageCrossingPenalty = true),
    CMP(pageCrossingPenalty = true),
    SBC(pageCrossingPenalty = true),
    LDX(pageCrossingPenalty = true),
    LDY(pageCrossingPenalty = true),

    STA(pageCrossingPenalty = false),
    STX(pageCrossingPenalty = false),
    STY(pageCrossingPenalty = false),
    TAX(pageCrossingPenalty = false),
    TAY(pageCrossingPenalty = false),
    TXA(pageCrossingPenalty = false),
    TYA(pageCrossingPenalty = false),
    TSX(pageCrossingPenalty = false),
    TXS(pageCrossingPenalty = false),
}