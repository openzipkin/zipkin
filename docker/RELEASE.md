The files in the [hooks/] directory control the sequence of image build and push.
Invocation of these files is based on webhooks defined in [dockerhub](https://cloud.docker.com/u/openzipkin/repository/docker/openzipkin/zipkin/builds).

In short, the `openzipkin/zipkin` dockerhub repository first creates the `openzipkin/zipkin` image,
later subordinate ones.

The current setup will push all images with the tag `master` on merges to the master branch.
It will also push tags `test-{\1}.{\2}.{\3}`,`test-{\1}.{\2}`,`test-{\1}`, and `test-latest` for all
images except `zipkin-builder` on release tag N.N.N.

TODO: transfer any relevant content from https://github.com/openzipkin/docker-zipkin/blob/master/RELEASE.md
TODO: link [../RELEASE.md] to this file, saying that a tag N.N.N triggers a dockerhub hook which
invokes this process simultaneous to [../travis/publish.sh].
