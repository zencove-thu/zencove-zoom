#include <regdef.h>
.section .text
.globl _start
_start:
move t0, zero
move t1, zero
li t2, 0x10
li a0, 0x80400000
loop:
addi t0, t0, 1
add t1, t1, t0
bne t0, t2, loop
nop
move t8, t1
sw t1, 0(a0)
li a0, 0x64
jal WRITESERIAL
nop
li a0, 0x6f
jal WRITESERIAL
nop
li a0, 0x6e
jal WRITESERIAL
nop
li a0, 0x65
jal WRITESERIAL
nop
li a0, 0x21
jal WRITESERIAL // 'DONE!'
nop
end:
li ra, 0x90000000
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
