#include <regdef.h>
.set noreorder
.section .text
    /*  STREAM test program
     *  Continous memory read/write
     */
UTEST_STREAM:
    li      a0, 0x80100000
    li      a1, 0x80400000
    li      a2, 0x00003000
    addu    a2,a0,a2
stream_next:
    beq     a0,a2,stream_end
    addiu   a1,a1,4

    lw      v0,0(a0)
    sw      v0,-4(a1)
    beq     $0,$0,stream_next
    addiu   a0,a0,4

stream_end:
    li ra, 0x90000000
    jr      ra
    nop
    /* End of test */
