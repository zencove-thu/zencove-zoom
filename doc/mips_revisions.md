# MIPS ISA版本迭代

本文档记录了一些对MIPS ISA版本迭代的观察。其中仅记录了与我们实现相关的部分，其余例如64位指令，FPU指令都没有记录。

考虑到实现者的视角，本文档以指令类型作为分类进行介绍。本文档也会提及一些编译参数，通过编译参数屏蔽一些未实现的指令。

据简单观察，MIPS2添加了多核需要的原子指令，branch likely等。MIPS3主要添加了64位指令。MIPS4添加了一些奇奇怪怪的指令。而到MIPS32r1（以下简写为MIPS32），似乎为了一些常用计算添加了指令做硬件加速。

ZenCove的指令集可配置。若生成默认指令集，则编译参数`-mips3 -mno-llsc -mno-branch-likely -msoft-float`。

## Unaligned load/store

很不幸，LWL、LWR和SWL、SWR是MIPS1中规定的，只能实现。当然，其实它整体而言没有太大的结构破坏性，只不过需要mux一种比较复杂的load/store移位情况。

## CLO、CLZ

这两个略显复杂的ALU指令在MIPS32才出现。

## WAIT

低功耗用的WAIT指令其实也直到MIPS32才出现。

## LL、SC、SYNC

这三条均是用于多核环境下做同步互斥的，在MIPS2中出现。`-mno-llsc`可以屏蔽这三条指令。

## PREF

用于cache预取的PREF在MIPS4出现，当然它可以被实现为nop。

## SYNCI

RISC-V的FENCE.I让人很容易联想到SYNCI。实际上它是MIPS32r2才定义的指令，而在MIPS32r1中，它的功能基本可以用CACHE指令替换。CACHE指令是MIPS1中定义的。

## Trap（自陷）指令

有一点令人意外，所有的trap指令是MIPS2定义的。

## 条件移动（conditional move）指令

只是一个提醒，branch and link无论是否跳转均要link，所以branch and link并不存在条件移动。

MOVN和MOVZ是条件移动指令。MIPS32r1中只有它们是在一定条件下写寄存器而另外一些条件不写的。这尤其折磨乱序执行CPU。而比较又是两个寄存器，如果要实现为在条件为假时把寄存器原值写回那就需要3个读口。好在，它们是MIPS4中才定义的。它们也在MIPS32r6中被移除。

不过这里也为乱序提供一种思路。那就是“预测执行”，恒预测其会写回，如果实际条件为假那么不写回并冲刷流水线。另一种思路是拆成两个uop（微码），不过我想RISC里大家不会太愿意这么做。

## 乘除法指令

HI/LO寄存器在MIPS1就存在了，所以MULT, MULTU, DIV, DIVU是MIPS1中定义的4条乘除法指令。

非常幸运的是，乘累加指令MADD, MADDU, MSUB, MSUBU在MIPS32中才被定义，这倒不对执行有太大影响，但是可以省掉一小片逻辑了（尤其是长长的64位加法器）。

直接向寄存器写（但是HI/LO也可以写）的MUL指令倒也是在MIPS32中才定义，不过这个似乎也不会太影响执行。

乘法指令需要2个通用寄存器读口和HI&LO同时写，而乘累加需要再加上HI&LO同时读。看起来这是4r2w，当然可以用2个uop实现。但是在RISC中没人愿意多个uop，那么可以实现为HI&LO拼成单一64位寄存器进行单独的重命名，总体寄存器口为通用寄存器2r1w，HI&LO寄存器1r1w。MTHI指令可以实现为读出来，把HI覆盖掉，LO保持，写回去，也可以实现为不读，LO写任意值（因为手册中说LO可以变为不确定的值）。

HI&LO寄存器在r6中被全面取消，乘除法指令（新加上计算余数的指令）全部向单一的通用寄存器写回。

## Branch likely

这些指令在MIPS2中被定义，不过在MIPS32中就已经标记为淘汰的指令，并在r6中被移除。没什么好说的，要么就是允许解码，忽略likely，要么就是用`-mno-branch-likely`屏蔽掉。

## FPU指令

用`-msoft-float`替换为软件实现。注意这样浮点应用会非常慢。`-mno-float`会直接确保应用没有浮点计算。

## 参考资料（非官方）

- [MIPS32官方手册](https://www.mips.com/products/architectures/mips32-2/)，r1-5，r6均有
- [MIPS IV ISA](https://www.cs.cmu.edu/afs/cs/academic/class/15740-f97/public/doc/mips-isa.pdf)
- [MIPS III 指令列表](https://n64brew.dev/wiki/MIPS_III_instructions)
- [GCC 12.1 MIPS相关选项](https://gcc.gnu.org/onlinedocs/gcc-12.1.0/gcc/MIPS-Options.html)
