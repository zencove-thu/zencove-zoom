# PC跳转服务

PC跳转服务提供了所有PC跳转源的注册。
相关实现代码位于[ProgramCounter](/ZenCove/src/zencove/core/ProgramCounter.scala)。

## 简介

在标量流水中，PC在不同阶段可能都有指令跳转，例如EXE阶段提前提交branch结果，而MEM阶段左右才能计算出异常结果，因此需要对不同的跳转进行优先级选择。同时由于不同阶段stall的不一致性，这一跳转可能需要寄存。则可以通过ProgramCounter的addJumpInterface接口注册跳转，PC会提供跳转自动寄存，并且优先选择阶段更靠后的跳转（指令更老），同阶段选择优先级数值更高的跳转。

也可以为PC注册预测接口。不注册预测接口时PC自动进行顺序的状态转移，预测接口提供了nextPC的预测值，有效时会被nextPC采纳。

## 注意事项

乱序仅在commit时会有跳转，为了优化nextPC时序，所有跳转注册在backendJumpInterface上，自动进行寄存。
