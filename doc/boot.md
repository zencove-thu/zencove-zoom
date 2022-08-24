全功能展示流程
---------------
1. trivial bootloader
  - 直接烧写比特流启动
  - reset
2. u-boot
  - 在主机上配置好ip和tftp
    ```bash
    ip a add 192.168.1.1/24 dev eth0
    ip link set eth0 up
    sudo service restart tftpd-hpa
    ```
  - cpu端加载uImage并启动
    ```uboot
    tftp 0x800fffc0 uImage-usb.bin
    bootm 0x800fffc0
    ```
3. linux
  - nfs
    - 主机端配置好nfs
      ```bash
      cat /etc/exports
      # /home/cykbracket/nfs *(rw, sync, no_root_squash)
      ls -al
      # nfs directory should be 777
      sudo /etc/init.d/nfs-kernel-server start # or restart
      # test nfs availability
      sudo mount -t ./test 127.0.0.1:/home/cykbraket/nfs -o nolock
      ```
    - linux配置ip挂载nfs
      ```bash
      ip a add 192.168.1.2/24 dev eth0
      ip link set eth0 up
      mkdir /mnt
      mount -n -o nolock 192.168.1.1:/home/cykbracket/nfs /mnt
      ```  
  - lcd屏
    ```bash
    cat /mnt/lcd<r/g/b>.bin > /dev/nt35510
    cat /mnt/megumin_lcd.bin > /dev/nt35510
    ```
  - 应用
    ```
    micropython
    micropython /mnt/test.py
    ```
  - 访问外网
    - 主机端
      1. 修改`/etc/sysctl.conf`开启转发
         ```
         net.ipv4.ip_forward=1
         ```
      2. 配置NAT
         ```bash
         # 先看interface
         ip a
         # 下面以eno1连cpu， wlo1连外网为例子
         /sbin/iptables -t nat -A POSTROUTING -o wlo1 -j MASQUERADE
         /sbin/iptables -A FORWARD -i wlo1 -o eno1 -m state --state RELATED,ESTABLISHED -j ACCEPT
         /sbin/iptables -A FORWARD -i eno1 -o wlo1 -j ACCEPT
         ```
    - cpu端
       1. 配置默认网关
          ```bash
          route add default gw 192.168.1.1 dev eth0
          route # 查看网关配置
          ```
       2. 修改DNS，`/etc/resolv.conf`
          ```bash
          nameserver 166.111.8.28 # DNS服务器自己找
          ```
       3. `/etc/init.d/S40network restart`
  - PS2 & VGA
    ```bash
    exec </dev/tty1 >/dev/tty1 2>/dev/tty1 # 将输入输出转到ps2和vga
    ascii_invaders
    ```