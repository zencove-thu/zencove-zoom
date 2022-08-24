# 仿真

本文档介绍了当前软件架构下如何使用仿真。

## Vivado行为仿真

在任何情况下，总可以对着生成的Verilog代码在Vivado中进行行为仿真。这也是最推荐的CPU整体仿真方式。

## SpinalHDL套壳仿真

SpinalHDL提供了仿真的接口，其后端可选，默认的是采用Verilator仿真。其好处是在macOS系统上也可以运行，并且比Vivado总是要轻量级一些。

在没有blackbox的情况下，这总是可以直接使用的，这一部分可以参考SpinalHDL官方文档。对于测试单元，或者完整CPU，都可以这么做。

Verilator本质就是通过C/C++实现对Verilog的仿真，与SpinalHDL本身无关，因此从理论上，如果能提供blackbox的sim rtl，那么通过`Blackbox#addRTLPath`，也可以对包含blackbox的设计进行仿真。但我们没有尝试过这件事。

## Simulated blackbox

LLCL-MIPS中提供了simulated blackbox类，用SpinalHDL的sim whitebox特性，在scala中提供对应的软件实现，就可以对blackbox提供仿真实现。适用于乘法器、除法器这种硬件实现复杂，但软件实现简单的blackbox。考虑到常用blackbox也就乘除法、RAM，这或许可以通过不多的代码，实现所有用到的blackbox。不过目前并没有很好地使用它。

## 汇编器

在[zencove.sim.MIPSInst](/ZenCove/src/zencove/sim/MIPSInst.scala)中，其实实现了一个汇编器，原理是读取`MIPS32`中定义的指令mask，写入对应的field，然后转换成数值。可以用于直接在scala中“写汇编”，提供给CPU做仿真。

## TODO: 差分测试

考虑友队经验，推荐搭建差分测试框架与模拟器进行对拍。
