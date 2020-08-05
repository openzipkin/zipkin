# zipkin-docker rationale

## Why do we add HEALTHCHECK for test images?

While most of our images are not production, we still add HEALTHCHECK for a better ad-hoc
and automation experience. Many of our setups will not operate without a service
dependency: having HEALTHCHECK present makes triage and scripting a bit easier.

HEALTHCHECK on our test image serves primarily two purposes:
 * ad-hoc or scripted status of health using docker ps instead of knowing Kafka commands
 * to allow manual allows usage of the docker-compose v2 service_healthy condition

Ex. The following command can be used ad-hoc or in scripts without the user knowing Kafka:
```bash
$ docker inspect --format='{{json .State.Health.Status}}' kafka-zookeeper
"unhealthy"
```
