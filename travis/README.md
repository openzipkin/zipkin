# Zipkin release process

**Note**: Publishing to Maven Central currently happens by manually pushing the "Sync" button on
[Bintray](https://bintray.com/openzipkin/zipkin/zipkin#central) after the release has finished.
This is required because automated sync requires a synchronous call to the Bintray API for
publishing the just released version, and that consistently times out (PR [#82](https://github.com/bintray/gradle-bintray-plugin/pull/82)
in [`zipkin-gradle-plugin`](https://github.com/bintray/gradle-bintray-plugin/) may help with that).

The Zipkin release process is controlled by [publish.sh](publish.sh).
Releases are initiated by pushing a tag `major.minor.revision` or `major.minor.revision-qualifier`,
where `qualifier` is usually something like `rc3`.

There is currently no automated testing during the release process. As the person pushing a release tag, you're
expected to manually check that the test [run on Travis](https://travis-ci.org/openzipkin/zipkin) is green for
the commit you're tagging. (Building tooling for this would be awesome.)

A high-level overview of how the release happens for RC and final releases:

![Release example](examples.png)

A detailed view at the process:

![Release details](flow.png)
