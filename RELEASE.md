# Zipkin Release Process

This repo uses semantic versions with a twist: we update minors on api-breaks until we hit 1.0. Please keep this
in mind when choosing version numbers.

1. **Alert others you are releasing**

   There should be no commits made to master while the release is in progress (about 10 minutes). Before you start
   a release, alert others on [gitter](https://gitter.im/openzipkin/zipkin) so that they don't accidentally merge
   anything. If they do, and the build fails because of that, you'll have to recreate the release tag described below.

1. **Push a git tag**

   The tag should be of the format `release-N.M.L`, ex `git tag release-1.18.1; git push origin release-1.18.1`.

1. **Wait for Travis CI**

   This part is controlled by [`travis/publish.sh`](travis/publish.sh). It creates a bunch of new commits, bumps
   the version, publishes artifacts, syncs to Maven Central, and publishes Javaocs to http://zipkin.io/zipkin
   into a versioned subdirectory.
   (Note: Javaocs are also published on all builds of master; due to versioning, it doesn't overwrite docs built
   for releases.)

1. **Publish `docker-zipkin`**

   Refer to [docker-zipkin/RELEASE.md](https://github.com/openzipkin/docker-zipkin/blob/master/RELEASE.md).
   
## Credentials

Credentials of various kind are needed for the release process to work. If you notice something
failing due to unauthorized, re-encrypt them using instructions at the bottom of the `.travis.yml`

Ex You'll see comments like this:
```yaml
env:
  global:
  # Ex. travis encrypt BINTRAY_USER=your_github_account
  - secure: "VeTO...
```

To re-encrypt, you literally run the commands with relevant values and replace the "secure" key with the output:

```bash
$ travis encrypt BINTRAY_USER=adrianmole
Please add the following to your .travis.yml file:

  secure: "mQnECL+dXc5l9wCYl/wUz+AaYFGt/1G31NAZcTLf2RbhKo8mUenc4hZNjHCEv+4ZvfYLd/NoTNMhTCxmtBMz1q4CahPKLWCZLoRD1ExeXwRymJPIhxZUPzx9yHPHc5dmgrSYOCJLJKJmHiOl9/bJi123456="
```

### Troubleshooting invalid credentials

If you receive a '401 unauthorized' failure from jCenter or Bintray, it is
likely `BINTRAY_USER` or `BINTRAY_KEY` entries are invalid, or possibly the user
associated with them does not have rights to upload.

The least destructive test is to try to publish a snapshot manually. By passing
the values Travis would use, you can kick off a snapshot from your laptop. This
is a good way to validate that your unencrypted credentials are authorized.

Here's an example of a snapshot deploy with specified credentials.
```bash
$ BINTRAY_USER=adrianmole BINTRAY_KEY=ed6f20bde9123bbb2312b221 TRAVIS_PULL_REQUEST=false TRAVIS_TAG= TRAVIS_BRANCH=master travis/publish.sh
```

## First release of the year

The license plugin verifies license headers of files include a copyright notice indicating the years a file was affected.
This information is taken from git history. There's a once-a-year problem with files that include version numbers (pom.xml).
When a release tag is made, it increments version numbers, then commits them to git. On the first release of the year,
further commands will fail due to the version increments invalidating the copyright statement. The way to sort this out is
the following:

Before you do the first release of the year, move the SNAPSHOT version back and forth from whatever the current is.
In-between, re-apply the licenses.
```bash
$ ./mvnw versions:set -DnewVersion=1.3.3-SNAPSHOT -DgenerateBackupPoms=false
$ ./mvnw com.mycila:license-maven-plugin:format
$ ./mvnw versions:set -DnewVersion=1.3.2-SNAPSHOT -DgenerateBackupPoms=false
$ git commit -am"Adjusts copyright headers for this year"
```

## Backport patch release

Usually we only release incrementing numbers. For example, if the current
release is 2.8.7, we release 2.8.8. In some rare scenarios, we might have
to release a backport on a non-current minor. To do this is manual, as we
don't have automation. Please proceed with caution when doing this.

Notably, watch https://circleci.com/gh/openzipkin/zipkin carefully as
travis does not build version tags!

### Find or create a N.N.x branch

If a backport release already existed for a minor, you'll find a N.N.x branch. For example, if the last minor version was 2.4.4, the branch would
be 2.4.x. Check this out.

If there is no branch, find the last commit before the next minor. For
example, using `git log`, you look for the commit for "prepare release"
and branch off the one right before it.

Ex. With the following git log
```
commit 1f5808b0b5bd7ad911cc2e21d3336540bd4ec83d (tag: 2.5.0)
Author: zipkinci <zipkinci+zipkin-dev@googlegroups.com>
Date:   Tue Mar 20 11:08:41 2018 +0000

    [maven-release-plugin] prepare release 2.5.0

commit 9a4ec17cf741bc4dcabef8aad41c1071dd5cfb77 (tag: release-2.5.0)
```

You would checkout and branch off `9a4ec17cf741bc4dcabef8aad41c1071dd5cfb77` like so:

```bash
$ git checkout 9a4ec17cf741bc4dcabef8aad41c1071dd5cfb77
$ git checkout -b 2.4.x
# pushing the branch just so that circleci will check it
$ git push origin 2.4.x
```

### Add the changes you need
Once you are on the branch, you'd use `git cherry-pick` to add the
commits you need. Once you have what you need, make sure you push
them so that circleci can check it.

Assuming abcdef1 is the commit ID needed, cherry-pick it like so:
```bash
$ git cherry-pick abcdef1
$ git push origin 2.4.x
```

### Do a release locally
With all the changes staged and ready, you need to do a release.
This involves changing the "pom" files which is mostly automatic,
creating a couple commit, pushing a tag, and running deploy.

Assuming you are on branch 2.4.x and you want to release 2.4.5.
```bash
$ ./mvnw versions:set -DnewVersion=2.4.5 -DgenerateBackupPoms=false 
# edit the pom and change <tag>HEAD</tag> to <tag>2.4.5</tag>
$ git commit -am"prepare to release 2.4.5"
$ git tag 2.4.5
$ git push origin 2.4.5
```

Once you are here, actually do the release. You'll need bintray
access:
```bash
$ BINTRAY_USER=adrianmole \
BINTRAY_KEY=abcdef1abcdef1abcdef1abcdef1 \
./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu -DskipTests deploy -X
```

Note: this will release to bintray, but not sync central. Test the
release and delete if it is screwed up. Once it is ready, release
to maven central via bintray: https://bintray.com/openzipkin/zipkin/zipkin/view#central

### Prepare the next version number
Once all of that is done, push the next snapshot version to the
release branch.

Assuming you are on branch 2.4.x and you just released 2.4.5.
```bash
$ ./mvnw versions:set -DnewVersion=2.4.6-SNAPSHOT -DgenerateBackupPoms=false 
# edit the pom and change <tag>2.4.5</tag> to <tag>HEAD</tag>
$ git commit -am"prepare next version"
$ git push origin 2.4.x
```
