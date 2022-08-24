# TLB测试说明

在现有代码基础上，需要做以下修改才能通过TLB测试：

- ERL初始化为0。这与手册相悖。但是ERL会影响kuseg映射规则。
测试本身并未设置status寄存器，而测试需要的状态是
  - ERL=0：kuseg过TLB映射。
  - EXL=0：TLB refill异常正确引导至0xbfc00200。
  - UM=0：特权态处于kernel。
- TLB项数改为32。因为测试并不支持可变的TLB项数。

## 可能遇到的编译问题

- `MIPS.abiflags`与某某代码段重合：这是因为某些编译器版本会生成abiflags信息段，可以在链接脚本`bin.lds.S`中添加规则`/DISCARD/ : { *(.reginfo) *(.MIPS.abiflags) }`。
- ‘-march=mips1’ requires ‘-mfp32’: 这是因为编译参数没有指定浮点寄存器宽度，在没有浮点的实现中，可以添加编译参数`-msoft-float`指定软件浮点实现。
