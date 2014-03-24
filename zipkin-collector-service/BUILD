maven_layout()


jar_library(name='zipkin-collector-service',
  dependencies=[
    pants('oss/zipkin-collector-service/src/main/scala'),
  ]
)

jar_library(name='tests',
  dependencies=[
    pants('oss/zipkin-collector-service/src/test/scala')
  ]
)

dependencies(
  name='main',
  dependencies=[
    pants('oss/zipkin-collector-service/src/main/scala')
  ]
)
