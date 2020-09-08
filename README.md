# logsdb
An experimental Log database top of RocksDB

Building

```shell script
sbt assembly
```

Running Server

```shell script
java -jar server/target/scala-2.13/server.jar --config sample-configs/primary-server.conf
``` 

Using client to push logs
```shell script
cat /var/log/nginx/access.log  | java -jar cli/target/scala-2.13/cli.jar push -h 127.0.0.1 -p 9080


## Push Json
echo '{"time": 1599063421241, "message": "Log Message", "attributes" : {"attr1": "Value1"} }' | java -jar cli/target/scala-2.13/cli.jar push -h 127.0.0.1 -p 9080 --json

```

Using client to query logs

```shell script
java -jar cli/target/scala-2.13/cli.jar query -h 127.0.0.1 -p 9080 --limit 100 --from '2020-08-22T20:20:30Z'
```

```shell script
java -jar cli/target/scala-2.13/cli.jar tail -h 127.0.0.1 -p 9080
```
