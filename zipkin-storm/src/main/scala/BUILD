scala_library(name='scala',
  dependencies=[
    pants('3rdparty:util-logging'),
    pants('3rdparty:scrooge-serializer'),
    pants('3rdparty:commons-logging'),
    pants('3rdparty:commons-configuration'),
    pants('3rdparty:bijection-core'),
    pants('3rdparty:bijection-scrooge'),
    pants('3rdparty:storehaus-memcache'),
    pants('3rdparty:summingbird-core'),
    pants('3rdparty:summingbird-batch'),
    pants('3rdparty:tormenta-kafka'),
    pants('3rdparty:storm'),
    pants('3rdparty:storm-kafka'),
    pants('oss/zipkin-scrooge'),
  ],
  sources=rglobs('*.scala'),
  excludes=[
    exclude('org.slf4j', 'log4j-over-slf4j'),
    exclude('ch.qos.logback', 'logback-classic'),
  ]
)
