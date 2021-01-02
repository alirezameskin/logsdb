# logsdb
An experimental Log database top of RocksDB

Building

```shell script
nix-shell --run "sbt assembly"
```

Running Server

```shell script
java -jar server/target/scala-2.13/server.jar --config sample-configs/server1.conf
``` 

Using client to push logs :

```shell script
cat /var/log/nginx/access.log  | java -jar cli/target/scala-2.13/logsdb-cli.jar push -h 127.0.0.1 -p 9080


## Push Json
echo '{"time": 1599063421241, "message": "Log Message", "attributes" : {"attr1": "Value1"} }' | java -jar cli/target/scala-2.13/logsdb-cli.jar push -h 127.0.0.1 -p 9080 --json

## Push log with labels
cat /var/log/nginx/access.log  | java -jar cli/target/scala-2.13/logsdb-cli.jar push -h 127.0.0.1 -p 9080 --label app=web --label server=nginx

```
Pushing to a specific collection:

```shell script
cat /var/log/nginx/access.log  | java -jar cli/target/scala-2.13/logsdb-cli.jar push -h 127.0.0.1 -p 9080 -c webserver
```

Using client to query logs

```shell script
java -jar cli/target/scala-2.13/logsdb-cli.jar query -h 127.0.0.1 -p 9080 --limit 100 --from '2020-08-22T20:20:30Z'
```

```shell script
java -jar cli/target/scala-2.13/logsdb-cli.jar tail -h 127.0.0.1 -p 9080
```

```shell script
java -jar cli/target/scala-2.13/logsdb-cli.jar query -h 127.0.0.1 -p 9080 -c webserver --limit 100 --from '2020-08-22T20:20:30Z'
```

```shell script
java -jar cli/target/scala-2.13/logsdb-cli.jar tail -h 127.0.0.1 -p 9080 '{app="web"} |~ "Error.*"'
```

Using client to get list of collections

```shell script
java -jar cli/target/scala-2.13/logsdb-cli.jar  collections -h 127.0.0.1 -p 9080
```


### Http endpoints

* Getting collections list: http://localhost:8080/v1/collections
* Querying the logs: http://localhost:8080/v1/logs/webserverr?after=0&limit=10