#!/bin/sh

echo "===> Configuring ..."

cat > /opt/logsdb/bin/primary.conf <<- EOF
logLevel = "Debug"

storage {
  path="/data"
  walTtlSeconds = 3600
}

server {
  host="0.0.0.0"
  port=9080
}

http {
  host = "0.0.0.0"
  port = 8080
}

replication {
  isPrimary=true
}
EOF


echo "===> Launching ... "
exec /opt/logsdb/bin/server -c /opt/logsdb/bin/primary.conf