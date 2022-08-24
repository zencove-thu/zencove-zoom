# Known issues

## MIPS32r1未实现的指令

- Branch likely: 编译选项`-mno-branch-likely`来避免。
- ll/sc: 编译选项`-mno-llsc`来避免。
- sync: 实现为nop。单核不考虑。
- pref: 实现为nop。不需要考虑性能。另外linux可以取消选择CPU_HAS_PREFETCH来避免它。

## Unaligned load/store on uncached address

### 描述

Lwl/lwr/swl/swr在uncached地址不能正确处理。
在cached地址可以正确处理（未充分测试）。

### 进展

不会被修复。
首先是3 Bytes在AXI上并不能直接传输，其次处理起来也比较麻烦。
GCC不会轻易生成这些指令，除非汇编硬写。
（考虑到MIPS32r6根本移除了这些指令。）
在linux中可以通过CPU_HAS_NO_UNALIGNED选项根本上关闭它们的生成。

## Wait指令

### 描述

Wait结束的条件有待明确。

### 进展

事实上Linux内核根据CPU类型适配了wait结束的条件。对于MIPS 4Kc似乎是按照wait结束的条件是有可被接受的中断来临。不过不可被接受的中断结束wait并不会导致任何错误。
