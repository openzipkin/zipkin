# OpenZipkin Release Process

This repo uses semantic versions. Please keep this in mind when choosing version numbers.

1. **Verify all dependencies are up-to-date**

   Before you start a release, make sure all dependencies are up-to-date, or are documented why not.
   Pay special attention to the [security workflow](.github/workflows/security.yml), which should
   run clean.

1. **Alert others you are releasing**

   There should be no commits made to master while the release is in progress (about 10 minutes). Before you start
   a release, alert others on [gitter](https://gitter.im/openzipkin/zipkin) so that they don't accidentally merge
   anything. If they do, and the build fails because of that, you'll have to recreate the release tag described below.

1. **Push a git tag**

   The trigger format is `release-MAJOR.MINOR.PATCH`, ex `git tag release-1.18.1 && git push origin release-1.18.1`.

1. **Wait for CI**

   The `release-MAJOR.MINOR.PATCH` tag triggers [`build-bin/maven/maven_release`](build-bin/maven/maven_release),
   which creates commits, `MAJOR.MINOR.PATCH` tag, and increments the version (maven-release-plugin).

   The `MAJOR.MINOR.PATCH` tag triggers [`build-bin/deploy`](build-bin/deploy), which does the following:
     * Publishes jars to https://oss.sonatype.org/content/repositories/releases [`build-bin/maven/maven_deploy`](build-bin/maven/maven_deploy)
       * Later, the same jars synchronize to Maven Central
     * Publishes Javadoc to https://zipkin.io/brave into a versioned subdirectory
     * Pushes images to Docker registries [`build-bin/docker_push`](build-bin/docker_push)

   Notes:
     * https://search.maven.org/ index will take longer than direct links like https://repo1.maven.org/maven2/io/zipkin

## Credentials

The release process uses various credentials. If you notice something failing due to unauthorized,
look at the notes in [.github/workflows/deploy.yml] and check the [org secrets](https://github.com/organizations/openzipkin/settings/secrets/actions).

### Troubleshooting invalid credentials

If you receive a '401 unauthorized' failure from OSSRH, it is likely
`SONATYPE_USER` or `SONATYPE_PASSWORD` entries are invalid, or possibly the
user associated with them does not have rights to upload.

The least destructive test is to try to publish a snapshot manually. By passing
the values CI would use, you can kick off a snapshot from your laptop. This
is a good way to validate that your unencrypted credentials are authorized.

Here's an example of a snapshot deploy with specified credentials.
```bash
$ export GPG_TTY=$(tty) && GPG_PASSPHRASE=whackamole SONATYPE_USER=adrianmole SONATYPE_PASSWORD=ed6f20bde9123bbb2312b221 build-bin/build-bin/maven/maven_deploy
```

## Manually releasing

If for some reason, you lost access to CI or otherwise cannot get automation to work, bear in mind
this is a normal maven project, and can be released accordingly.

*Note:* If [Sonatype is down](https://status.sonatype.com/), the below will not work.

```bash
# First, set variable according to your personal credentials. These would normally be assigned as
# org secrets: https://github.com/organizations/openzipkin/settings/secrets/actions
export GPG_TTY=$(tty)
export GPG_PASSPHRASE=your_gpg_passphrase
export SONATYPE_USER=your_sonatype_account
export SONATYPE_PASSWORD=your_sonatype_password
release_version=xx-version-to-release-xx

# now from latest master, create the release. This creates and pushes the MAJOR.MINOR.PATCH tag
./build-bin/maven/maven_release release-${release_version}

# once this works, deploy the release
git checkout ${release_version}
./build-bin/deploy

# Finally, clean up
./mvnw release:clean
git checkout master
git reset HEAD --hard
```
