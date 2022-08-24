#include <regdef.h>
.section .text
.globl _start
_start:
SHELL:
jal READSERIAL
nop
move a0, v0
jal WRITESERIAL
nop
j .DONE
nop
READSERIAL:                         // 读串口：将读到的数据写入v0低八位
    li t1, 0xBFD003F8
.TESTR:
    lb t0, 4(t1)      // 查看串口状态
    andi t0, t0, 0x0002             // 截取读状态位
    bne t0, zero, .RSERIAL          // 状态位非零可读进入读
    nop
    j .TESTR                        // 检测验证
    nop
.RSERIAL:
    lb v0, 0(t1)      // 读出
    jr ra
    nop
WRITESERIAL:                        // 写串口：将a0的低八位写入串口
    li t1, 0xBFD003F8
.TESTW:
    lb t0, 4(t1)      // 查看串口状态
    andi t0, t0, 0x0001             // 截取写状态位
    bne t0, zero, .WSERIAL          // 状态位非零可写进入写
    nop
    j .TESTW                        // 检测验证，忙等待
    nop
.WSERIAL:
    sb a0, 0(t1)      // 写入
    jr ra
    nop
.DONE:
li ra, 0x90000000
jr ra
    j SHELL
    nop
