# Util介绍

本文档介绍`zencove.util`包中的通用工具。

## isAnyOf

[isAnyOf](/ZenCove/src/zencove/util/package.scala)方法可以作用于任何硬件信号，例如
`a === 1 || a === b`可以写作`a.isAnyOf(1, b)`。

## 信号命名

`prunePayloadRecursive`和`Axi4Naming`用于修改信号命名，共同作用可以自动将SpinalHDL中定义的AXI4 bus改成Vivado中对应的bus信号名称。

## Types

[Types](/ZenCove/src/zencove/util/Types.scala)中定义了一些提升代码可读性的硬件类型，例如`UWord`固定为32位，不需要重复写出长度。

## 存储器

`MultiBankingRAM`简单实现了多bank存储，不发生bank conflict的情况下可以多口同时读写。由于没有使用到，因此没有测试过，**不保证正确性**。

`ReorderCacheRAM`用block RAM实现了重排序RAM，可以实现不对齐的连续多word读写。这对于超标量cache读取非常有用。

`MultiPortFIFO`基于重排序RAM实现了多口FIFO，支持多口push多口pop。这对于超标量处理器的buffer非常有用。`MultiPortFIFOVec`是使用寄存器实现的多口FIFO，在buffer小的时候可能优于`MultiPortFIFO`。其中有一些细节的实现，比如是否设置output register，这是对于输入和输出组合逻辑长度的trade-off。
