# Test json
For testing the UI, you can simply POST or paste json in this directory.

For example, assuming you started zipkin allowing it to see up to 5 year old data `QUERY_LOOKBACK=157784630000 java -jar zipkin.jar`
```bash
$ curl -X POST -s localhost:9411/api/v2/spans -H'Content-Type: application/json' -d @netflix.json
$ open 'http://localhost:9411/zipkin/?lookback=custom&startTs=0'
```
