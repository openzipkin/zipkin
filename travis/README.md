# Zipkin release process

The Zipkin release process is controlled by [publish.sh](publish.sh). Releases are initiated by pushing a tag `major.minor.revision` or `major.minor.revision-qualifier`, where `qualifier` is usually something like `rc3`. A high-level overview of how the release happens in each case:

![Release example](examples.png)

A detailed view at the process:

![Release details](flow.png)
