# Zipkin Release Process

This repo uses semantic versions with a twist: we update minors on api-breaks until we hit 1.0. Please keep this
in mind when choosing version numbers.

1. **Alert others you are releasing**

   There should be no commits made to master while the release is in progress (about 10 minutes). Before you start
   a release, alert others on [gitter](https://gitter.im/openzipkin/zipkin) so that they don't accidentally merge
   anything. If they do, and the build fails because of that, you'll have to recreate the release tag described below.

1. **Push a git tag**

   The tag should be of the format `release-N.M.L`, for example `release-0.18.1`.

1. **Wait for Travis CI**

   This part is controlled by [`travis/publish.sh`](travis/publish.sh). It creates a bunch of new commits, bumps
   the version, publishes artifacts, syncs to Maven Central, and publishes Javaocs to http://zipkin.io/zipkin
   into a versioned subdirectory.
   (Note: Javaocs are also published on all builds of master; due to versioning, it doesn't overwrite docs built
   for releases.)

1. **Publish `docker-zipkin-java`**

   Refer to [docker-zipkin-java/RELEASE.md](https://github.com/openzipkin/docker-zipkin-java/blob/master/RELEASE.md).
