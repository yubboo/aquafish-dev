#!/bin/sh
set -eu

# 绑定挂载目录可能由 root 创建。入口只修正工作目录根节点，
# 随后以固定 uid/gid 10001 启动 Java 主进程。
if [ "$(id -u)" = "0" ]; then
  mkdir -p /app/workdir
  chown 10001:10001 /app/workdir
  exec setpriv \
    --reuid=10001 \
    --regid=10001 \
    --init-groups \
    "$@"
fi

exec "$@"
