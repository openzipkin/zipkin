# Test json
For testing the UI, you can simply POST or paste json in this directory.

For example, assuming you started zipkin with `java -jar zipkin.jar`
```bash
$ curl -X POST -s localhost:9411/api/v2/spans -H'Content-Type: application/json' -d @netflix.json
```
