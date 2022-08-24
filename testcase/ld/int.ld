OUTPUT_ARCH("mips:isa32r2")
OUTPUT_FORMAT("elf32-tradlittlemips")

PROVIDE(SerialData = 0xBFD003F8);
PROVIDE(SerialStat = 0xBFD003FC);

SECTIONS
{
    . = 0x80000000;
    .text : {
        *(.text.init)
        . = 0x1000;
        *(.text.ebase)
        . = 0x1180;
        *(.text.ebase180)
        . = ALIGN(0x1000);
        *(.text)
    }
}
