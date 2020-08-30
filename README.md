# logsdb
An experimental Log database top of RocksDB

Building

```shell script
sbt assembly
```

Running Server

```shell script
java -jar server/target/scala-2.13/server-assembly-0.1.jar -p 9080 -d /path/to/db
``` 

Using client to push logs
```shell script
cat /var/log/nginx/access.log  | java -jar cli/target/scala-2.13/cli-assembly-0.1.jar push -h 127.0.0.1 -p 9080
```

Using client to query logs

```shell script
java -jar cli/target/scala-2.13/cli-assembly-0.1.jar query -h 127.0.0.1 -p 9080 --limit 100 --from '2020-08-22T20:20:30Z'
```

```shell script
java -jar cli/target/scala-2.13/cli-assembly-0.1.jar tail -h 127.0.0.1 -p 9080
```
