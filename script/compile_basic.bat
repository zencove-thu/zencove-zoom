@echo off
set name=%1
set testDir=testcase\asm
set buildDir=build
mkdir %buildDir%
mips-mti-elf-gcc -c %testDir%\%name%.S -Itestcase\include\ -D__ASSEMBLY__ -EL -g -mips32r2 -mno-abicalls -mno-shared -o %buildDir%\%name%.o
mips-mti-elf-ld %buildDir%\%name%.o -T"testcase\ld\basic.ld" -o %buildDir%\%name%.elf
mips-mti-elf-objcopy -j .text -O binary -v %buildDir%\%name%.elf %buildDir%\%name%.bin
mips-mti-elf-objdump -d %buildDir%\%name%.elf > %buildDir%\%name%.des
