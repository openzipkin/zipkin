# zipkin-docker rationale

## Why do we add HEALTHCHECK for test images?

While most of our images are not production, we still add HEALTHCHECK for a better ad-hoc
and automation experience. Many of our setups will not operate without a service
dependency: having HEALTHCHECK present makes triage and scripting a bit easier.

HEALTHCHECK on our test image serves primarily two purposes:
 * ad-hoc or scripted status of health using docker ps instead of knowing Kafka commands
 * to allow manual usage of the docker-compose v2 service_healthy condition

Ex. The following command can be used ad-hoc or in scripts without the user knowing Kafka:
```bash
$ docker inspect --format='{{json .State.Health.Status}}' kafka-zookeeper
"unhealthy"
```

### Why default timeout to 5s for Kafka HEALTHCHECK?

Docker `HEALTHCHECK` marks a container unhealthy on the first failure that occurs past its start
period. It is important to avoid false negatives that are host contention in nature, as they can
break orchestration such as docker-compose.

Commands like `nc` will almost never timeout launching due to contention, even if they might timeout
on network connections themselves for the same reason.

`kafka-topics.sh` uses Java and a relatively large classpath. It can be slow when many other
processes are starting at the same time. For example, when launching many containers in Docker
Compose, `kafka-topics.sh` timed out after 2s even though the prior run succeeded. This broke a condition
which broke the rest of the automation. A 5s timeout is excessive usually, but avoided this problem.
