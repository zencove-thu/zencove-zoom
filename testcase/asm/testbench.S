#include <regdef.h>
    .section .text
    .globl _start
_start:
    li t0, 10
    li t1, 0
    li t2, 0
loop:
    addiu t1, t1, 1
    addu t2, t1, t2
    bne t0, t1, loop
end:
    jal test_func
    jal test_func
    jal test_func
    j end
test_func:
    jr ra
    li t0, 0x1
    li t1, 0x2
    li t2, 0x3
    addu t0, t1, t0 //test t0 rename
    and t2, t0, t2  
    addu t2, t0, t1 //test t2 rename
    lui t0, 0x0020
    clo t1, t0
    clz t2, t0
    lui t0, 0xffe0
    clo t3, t0
    clz t4, t0
    lui t0, 0x8000
    li t1, 0x1234
    sw t1, 0(t0)
    lw t2, 0(t0)
    sw t2, 4(t0)
    lui t0, 0x8040
    sw t2, 0(t0)
    lw t3, 0(t0)
    addi t0, zero, 0x3
    lui t1, 0xbfd0
    sb t0, 0xc(t1) 
    li a0, 'O'
    sb a0, 0x4(t1)
    li a0, 'K'
    sb a0, 0x4(t1)
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
    