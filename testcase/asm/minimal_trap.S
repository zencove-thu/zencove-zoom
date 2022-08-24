#include <regdef.h>
#include <mipsregs.h>
#include <unistd.h>
#include <exception.h>
.set noreorder
.section .text.init
.globl _start
_start:
j INITLOC
nop
.section .text
INITLOC:
mfc0 t0, CP0_STATUS             // 取得cp0的status Reg
li t1, (ST0_BEV | ST0_ERL | ST0_IE) // 中断没有配置，不要使能，避免干扰
xor t1, t0, t1
and t0, t0, t1
li t1, (ST0_EXL | ST0_UM | ST0_IM)
or t0, t0, t1
// 把ERL切成EXL绕开error EPC，undefined位初始化成异常返回状态
// status Reg 的BEV位置零，中断处理转为正常模式
mtc0 t0, CP0_STATUS
li t2, PAGE_SIZE
mtc0 t2, CP0_EBASE              // 设定中断响应基址为0x8000.1000
mfc0 t0, CP0_CAUSE
li t1, CAUSEF_IV
xor t1, t1, t0
and t0, t0, t1                  // Cause IV位置零
mtc0 t0, CP0_CAUSE              // 关闭中断特殊入口
la a0, USER // 跳OP_G准备进入用户程序
j .OP_G
nop

.OP_G:
mtc0 a0, CP0_EPC // 写入EPC，回用户程序
eret

USER:
// 空的用户程序
li v0, SYS_exit
syscall

.filling: // 避免USER越界杀死仿真
nop
nop
nop
nop
nop
nop
nop

.section .text.ebase
TLBREFILL:                          // TLB快速重填
j FATAL // 暂时不支持tlb refill
nop
mfc0 k1, CP0_CONTEXT // TODO: context需要初始化
lw k0, 0x0(k1)
lw k1, 0x8(k1)
mtc0 k0, CP0_ENTRYLO0
mtc0 k1, CP0_ENTRYLO1
nop
tlbwr
eret
nop

.section .text.ebase180
EHANDLERLOCATE:                     // 定位普通中断处理程序
la k0, EXCEPTIONHANDLER
jr k0
nop

EXCEPTIONHANDLER:
// IE根本不需要置0，EXL=1就禁用中断了
// 仿真比较方便看寄存器
mfc0 s0, CP0_CAUSE
mfc0 s1, CP0_STATUS
mfc0 s2, CP0_EPC
mfc0 s3, CP0_BADVADDR
andi k1, s0, 0x00FF             // 截取CP0_CAUSE 的ExcCode
srl k1, k1, 2
li k0, EX_SYS            // 系统调用
beq k1, k0, SYSCALL             // 内核处理
nop
j FATAL // 不支持的异常，挂掉
nop

SYSCALL: // v0调用号，a0开始传参
addiu s2, s2, 0x4               // EPC+4,退出中断后执行SYSCALL的下一条语句
li t0, SYS_exit
beq v0, t0, .syscall_exit
nop
j FATAL // 不支持的syscall号，挂掉
nop

.syscall_exit:
// 正常的syscall应该用.OP_G返回
j .END
nop

FATAL:
.END:
li t0, 0x90000000 // 一个kseg0无效地址
jr t0 // 飞出去作为仿真停止条件
nop
