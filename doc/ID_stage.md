# 解码阶段

解码阶段可以解出decode width条指令（一般等于fetch width）。

decode结果并不会放进buffer，而是经过重命名之后扔进各种缓存，开始后端（乱序）执行。

因此要求一次不可decode出过多uop，然而MIPS还是有一条指令不止一个uop的可能（乘累加），
但是要保证uop的顺序性，因此遇到这种指令，则后面的uop都需要暂停，直至前面的指令发射最后一条uop的时候才会发射。因此decoder要记录状态。观察到仅有第一个decoder需要发射非首条的uop，但是所有decoder都可能发射多uop中首条的uop，这里有不对称性。
