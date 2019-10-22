# Test json
For testing the UI, you can simply POST or paste json in this directory.

For example, assuming you started zipkin allowing it to see up to 5 year old data `QUERY_LOOKBACK=157784630000 java -jar zipkin.jar`
```bash
$ curl -X POST -s localhost:9411/api/v2/spans -H'Content-Type: application/json' -d @netflix.json
$ open 'http://localhost:9411/zipkin/?lookback=custom&startTs=1'
```


## Data File Explanations

### smartthings-oauth-authorization.json


This trace captures an OAuth flow that integrates with a 3rd party. So it leaves our system and comes back.

We add trace propagation across web redirects to tie several systems together, especially when they involve round trips to other web sites we don’t own. Further we add user IDs to these traces to investigate specific customer issues or complaints. Lastly we also embed the trace context in things like oauth authorization codes so we can tie a full user actions together even when part of that action is performed on back end systems we don’t own.

### smartthings-mobile-web-install.json

This trace captures a user that is being directed to a embedded web experience on a mobile device through a multi step install process.

There are clusters of requests the long pauses in between are the user interacting with the page.

The end of the trace is the internal events noting the install starting to flow through the system.
