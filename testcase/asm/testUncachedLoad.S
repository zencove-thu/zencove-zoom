#include <regdef.h>
    .section .text
    .globl _start
_start:
    li t3, 0
    li t4, 26
    li t2, 'a'
loop:
    add a0, t2, zero
    jal WRITESERIAL
    addiu t3, t3, 1
    addiu t2, t2, 1
    bne t3, t4, loop
end:
    j end
WRITESERIAL:
    lui t1, 0xbfd0
.TESTW:
    lb t0, 8(t1)
    andi t0, t0, 0x8
    beqz t0, .WSERIAL 
    j .TESTW
.WSERIAL:
    sb a0, 4(t1)
    jr ra
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop
    nop