# 前端逻辑描述

在这里用文字的形式描述前端逻辑，理清思路。

## NextPC逻辑

- 默认情况下，PC应当随着fetch前进。一次fetch满足不超过fetch width且不超过cache line，PC由此选择顺序的NPC (next PC)。
- 如果有单周期predictor（BTB、RAS），PC take这一直接的结果。
- 如果前端有redirect（例如复杂predictor，predict快速修复），take前端的结果。
- 后端实际算出控制流转移，take后端的结果。

## BTB逻辑

原则：在预测正确的情况下，整个fetch buffer中的指令流应当与正确的指令流完全一致。

由于分支指令密度不高（尤其是有延迟槽的情况下），BTB不必对一次fetch的所有PC进行预测，可以通过对齐的方式进行预测。

BTB可能对一次fetch出的某一个PC预测出跳转，那么就需要将这一信息传递下去，mask掉延迟槽之后的指令，不要放进fetch buffer。有一种特殊情况，预测出最后一条指令是branch且跳转，那么delay slot需要下一周期才能取，此时下一周期就仅取delay slot一条指令，再下一周期取跳转之后的位置，否则可以直接在当前fetch packet中找到delay slot。

并且BTB要以某种方式将预测信息存到指令里，以便后端知道预测结果。

Sol: 给指令加`PREDICT_JUMP`和`PREDICT_TARGET`(RAS要求)的流水线信号

BTB在指令缓存刷新时也需要刷新？否则预测的target可能错误。或者通过前端的branch micro-decode快速修复这个问题。

[预测器设计方案](predictor_design.md)

## Fetch buffer

Fetch buffer按照每条指令的FIFO，存储了每条指令的前端信息，包括PC，分支预测信息，前端异常信息。
Fetch buffer中的每条指令将直接被decoder取用。
