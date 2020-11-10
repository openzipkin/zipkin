# OpenZipkin Release Process

This repo uses semantic versions. Please keep this in mind when choosing version numbers.

1. **Alert others you are releasing**

   There should be no commits made to master while the release is in progress (about 10 minutes). Before you start
   a release, alert others on [gitter](https://gitter.im/openzipkin/zipkin) so that they don't accidentally merge
   anything. If they do, and the build fails because of that, you'll have to recreate the release tag described below.

1. **Push a git tag**

   The tag should be of the format `release-N.M.L`, ex `git tag release-1.18.1; git push origin release-1.18.1`.

1. **Wait for Travis CI**

   Release automation invokes [`travis/publish.sh`](travis/publish.sh), which does the following:
     * Creates commits, N.N.N tag, and increments the version (maven-release-plugin)
     * Publishes jars to https://oss.sonatype.org/service/local/staging/deploy/maven2 (maven-deploy-plugin)
       * Upon close, this synchronizes jars to Maven Central (nexus-staging-maven-plugin)
     * Invokes [DockerHub](docker/RELEASE.md] build (docker/bin/push_all_images)
     * Publishes Javadoc to https://zipkin.io/zipkin into a versioned subdirectory

   Notes:
     * https://search.maven.org/ index will take longer than direct links like https://repo1.maven.org/maven2/io/zipkin
     * Javadocs are also published on all builds of master; due to versioning, it doesn't
   overwrite docs built for releases.

## Credentials

The release process uses various credentials. If you notice something failing due to unauthorized,
look at the notes in [.travis.yml] and check the [project settings](https://travis-ci.com/github/openzipkin/zipkin/settings)

### Troubleshooting invalid credentials

If you receive a '401 unauthorized' failure from OSSRH, it is likely
`SONATYPE_USER` or `SONATYPE_PASSWORD` entries are invalid, or possibly the
user associated with them does not have rights to upload.

The least destructive test is to try to publish a snapshot manually. By passing
the values Travis would use, you can kick off a snapshot from your laptop. This
is a good way to validate that your unencrypted credentials are authorized.

Here's an example of a snapshot deploy with specified credentials.
```bash
$ export GPG_TTY=$(tty) && GPG_PASSPHRASE=whackamole SONATYPE_USER=adrianmole SONATYPE_PASSWORD=ed6f20bde9123bbb2312b221 TRAVIS_PULL_REQUEST=false TRAVIS_TAG= TRAVIS_BRANCH=master travis/publish.sh
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

## Manually releasing

If for some reason, you lost access to CI or otherwise cannot get automation to work, bear in mind
this is a normal maven project, and can be released accordingly.

*Note:* If [Sonatype is down](https://status.sonatype.com/), the below will not work.

```bash
# First, set variable according to your personal credentials. These would normally be decrypted from .travis.yml
export GPG_TTY=$(tty)
export GPG_PASSPHRASE=your_gpg_passphrase
export SONATYPE_USER=your_sonatype_account
export SONATYPE_PASSWORD=your_sonatype_password
VERSION=xx-version-to-release-xx

# now from latest master, prepare the release. We are intentionally deferring pushing commits
./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu -DreleaseVersion=$VERSION -Darguments="-DskipTests" release:prepare -DpushChanges=false

# once this works, deploy and synchronize to maven central
git checkout $VERSION
# -DskipBenchmarks ensures benchmarks don't end up in javadocs or in Maven Central
./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu -DskipTests -DskipBenchmarks deploy

# if all the above worked, clean up stuff and push the local changes.
./mvnw release:clean
git checkout master
git push
git push --tags
```

## Backport patch release

Usually we only release incrementing numbers. For example, if the current
release is 2.8.7, we release 2.8.8. In some rare scenarios, we might have
to release a backport on a non-current minor. To do this is manual, as we
don't have automation. Please proceed with caution when doing this.

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

Once you are here, follow the steps mentioned in "Manually releasing"

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

## Generating jdiff and javadoc

Once the release is done and the artifacts are in maven central, you can generate
the jdiff report in the `gh-pages` branch. It's not needed to do this for a
patch release.

```bash
$ wget -L -c https://search.maven.org/remotecontent?filepath=org/spf4j/spf4j-jdiff-maven-plugin/8.8.1/spf4j-jdiff-maven-plugin-8.8.1-uber.jar -O jdiff.jar
$ java -jar jdiff.jar -gId io.zipkin.zipkin2 -aId zipkin -fromVersion 2.20.2 -toVersion 2.21.3 -o jdiff/2.20_to_2.21 -p 'zipkin2 zipkin2.storage zipkin2.codec zipkin2.v1'
$ git add jdiff/2.20_to_2.21
$ git commit -m"jdiff report"
$ git push upstream gh-pages
```

Note that
* The `fromVersion` and `toVersion` reflect the latest patch version of each release.
* The output directory does not include the patch version number.
* The `-p` parameter specifies the packages to include in the jdiff report,
you can look at [bnd.bnd](zipkin/bnd.bnd) to see which packages we export. This rarely changes.
