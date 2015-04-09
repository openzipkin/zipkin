java_thrift_library(
  name='thrift-java',
  sources=rglobs('*.thrift'),
  compiler='scrooge',
  language='java',
  provides = artifact(
    org = 'com.twitter',
    name = 'zipkin-thrift-java',
    repo = artifactory,
  ),
)

java_thrift_library(
  name='thrift-scala',
  sources=rglobs('*.thrift'),
  compiler='scrooge',
  language='scala',
  rpc_style='finagle',
  provides = artifact(
    org = 'com.twitter',
    name = 'zipkin-thrift-scala',
    repo = artifactory,
  ),
)
