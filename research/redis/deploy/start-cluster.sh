#!/usr/bin/env bash
set -euo pipefail

echo "==> Deploying 6-node Redis Cluster in Docker..."
docker rm -f dreb-redis-cluster 2>/dev/null || true

docker run -d --name dreb-redis-cluster \
  -p 7001:7001 -p 7002:7002 -p 7003:7003 -p 7004:7004 -p 7005:7005 -p 7006:7006 \
  redis:7 sh -c '
    for port in 7001 7002 7003 7004 7005 7006; do
      mkdir -p /data/$port
      redis-server --port $port \
        --cluster-enabled yes \
        --cluster-config-file /data/$port/nodes.conf \
        --cluster-node-timeout 5000 \
        --appendonly yes \
        --dir /data/$port \
        --protected-mode no \
        --daemonize yes
    done
    sleep 2
    echo "yes" | redis-cli --cluster create \
      127.0.0.1:7001 127.0.0.1:7002 127.0.0.1:7003 \
      127.0.0.1:7004 127.0.0.1:7005 127.0.0.1:7006 \
      --cluster-replicas 1
    tail -f /data/7001/nodes.conf
  '

echo "==> Waiting for cluster initialization..."
sleep 3
docker exec dreb-redis-cluster redis-cli -c -p 7001 cluster info
echo "==> Redis Cluster successfully deployed on 127.0.0.1:7001-7006!"
