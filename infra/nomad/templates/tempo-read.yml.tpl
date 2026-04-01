target: querier

server:
  http_listen_port: 3200

memberlist:
  bind_port: 0

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces
    wal:
      path: /var/tempo/wal
