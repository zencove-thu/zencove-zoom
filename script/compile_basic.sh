#!/bin/sh
test_dir=testcase/asm
build_dir=build
mkdir -p $build_dir
base_name=$1
mips-mti-elf-gcc -c $test_dir/$base_name.S -Itestcase/include/ -D__ASSEMBLY__ -EL -g -mips32r2 -mno-abicalls -mno-shared -o $build_dir/$base_name.o
mips-mti-elf-ld $build_dir/$base_name.o -Ttestcase/ld/basic.ld -o $build_dir/$base_name.elf
mips-mti-elf-objcopy -j .text -O binary -v $build_dir/$base_name.elf $build_dir/$base_name.bin
