# Test and Deploy scripts

This is a Maven+Docker project, which uses standard conventions for test and deploy with some
exceptions.

On [../zipkin-lens]:
* [maybe_install_npm] is used to build on an unsupported node.js architecture.
* [maven_go_offline] additionally seeds the NPM cache

On test:
* [test], used by [../.github/workflows/test.yml] runs Maven unit and integration tests.
  * Its "test" job skips docker, as they are run in parallel in "test_docker"
* [../.github/workflows/readme_test.yml] tests build commands in [../zipkin-server] and [../docker]
  * zipkin, zipkin-lens and zipkin-slim Docker builds use `RELEASE_FROM_MAVEN_BUILD=true`
    * this avoids invoking Maven builds from within Docker, which are costly and fragile
  * Docker tests run in sequence to avoid queueing delays, which take longer than builds themselves.

On deploy:
* [deploy], used by [../.github/workflows/deploy.yml] publishes jars and Docker images.
* [javadoc_to_gh_pages] pushes Javadoc to the gh-pages branch on MAJOR.MINOR.PATCH branch, but not master.
  * gh-pages is addressable via https://zipkin.io/zipkin/
* Besides production Docker images, this project includes [../docker/test-images].
  * [docker_push] pushes test-images, but only to ghcr.io

[//]: # (Below here should be standard for all projects)

## Build Overview
`build-bin` holds portable scripts used in CI to test and deploy the project.

The scripts here are portable. They do not include any CI provider-specific logic or ENV variables.
This helps `.travis.yml` and `test.yml` (GitHub Actions) contain nearly the same contents, even if
certain OpenZipkin projects need slight adjustments here. Portability has proven necessary, as
OpenZipkin has had to transition CI providers many times due to feature and quota constraints.

These scripts serve a second purpose, which is to facilitate manual releases, which has also
happened many times due usually to service outages of CI providers. While tempting to use
CI-provider specific tools, doing so can easily create a dependency where no one knows how to
release anymore. Do not use provider-specific mechanisms to implement release flow. Instead,
automate triggering of the scripts here.

The only scripts that should be modified per project are in the base directory. Those in sub
directories, such as [docker], should not vary project to project except accident of version drift.
Intentional changes in sub directories should be relevant and tested on multiple projects to ensure
they can be blindly copy/pasted.

Conversely, the files in the base directory are project specific entry-points for test and deploy
actions and are entirely appropriate to vary per project. Here's an overview:

## Test

Test builds and runs any tests of the project, including integration tests. CI providers should be
configured to run tests on pull requests or pushes to the master branch, notably when the tag is
blank. Tests should not run on documentation-only commits. Tests must not depend on authenticated
resources, as running tests can leak credentials. Git checkouts should include the full history so
that license headers or other git analysis can take place.

 * [configure_test] - Sets up build environment for tests.
 * [test] - Builds and runs tests for this project.

### Example GitHub Actions setup

A simplest GitHub Actions `test.yml` runs tests after configuring them, but only on relevant event
conditions. The name `test.yml` and job `test` allows easy references to status badges and parity of
the scripts it uses.

The `on:` section obviates job creation and resource usage for irrelevant events. Notably, GitHub
Actions includes the ability to skip documentation-only jobs.

Combine [configure_test] and [test] into the same `run:` when `configure_test` primes file system
cache.

Here's a partial `test.yml` including only the aspects mentioned above.
```yaml
on:
  push:
    tags: ''
    branches: master
    paths-ignore: '**/*.md'
  pull_request:
    branches: master
    paths-ignore: '**/*.md'

jobs:
  test:
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v2
        with:
          fetch-depth: 0  # full git history
      - name: Test
        run: build-bin/configure_test && build-bin/test
```

### Example Travis setup
`.travis.yml` is a monolithic configuration file broken into stages, of which the default is "test".
A simplest Travis `test` job configures tests in `install` and runs them as `script`, but only on
relevant event conditions.

The `if:` section obviates job creation and resource usage for irrelevant events. Travis does not
support file conditions. A `before_install` step to skip documentation-only commits will likely
complete in less than a minute (10 credit cost).

Here's a partial `.travis.yml` including only the aspects mentioned above.
```yaml
git:
  depth: false  # TRAVIS_COMMIT_RANGE requires full commit history.

jobs:
  include:
    - stage: test
      if: branch = master AND tag IS blank AND type IN (push, pull_request)
      name: Run unit and integration tests
      before_install: |  # Prevent test build of a documentation-only change.
        if [ -n "${TRAVIS_COMMIT_RANGE}" ] && ! git diff --name-only "${TRAVIS_COMMIT_RANGE}" -- | grep -qv '\.md$'; then
          echo "Stopping job as changes only affect documentation (ex. README.md)"
          travis_terminate 0
        fi
      install: ./build-bin/configure_test
      script: ./build-bin/test
```

When Travis only runs tests (something else does deploy), there's no need to use stages:
```yaml
git:
  depth: false  # TRAVIS_COMMIT_RANGE requires full commit history.

if: branch = master AND tag IS blank AND type IN (push, pull_request)
before_install: |  # Prevent test build of a documentation-only change.
  if [ -n "${TRAVIS_COMMIT_RANGE}" ] && ! git diff --name-only "${TRAVIS_COMMIT_RANGE}" -- | grep -qv '\.md$'; then
    echo "Stopping job as changes only affect documentation (ex. README.md)"
    travis_terminate 0
  fi
install: ./build-bin/configure_test
script: ./build-bin/test
```

## Deploy

Deploy builds and pushes artifacts to a remote repository for master and release commits on it. CI
providers deploy pushes to master on when the tag is blank, but not on documentation-only commits.
Releases should deploy on version tags (ex `/^[0-9]+\.[0-9]+\.[0-9]+/`), without consideration of if
the commit is documentation only or not.

 * [configure_deploy] - Sets up environment and logs in, assuming [configure_test] was not called.
 * [deploy] - deploys the project, with arg0 being "master" or a release commit like "1.2.3"

### Example GitHub Actions setup

A simplest GitHub Actions `deploy.yml` deploys after logging in, but only on relevant event
conditions. The name `deploy.yml` and job `deploy` allows easy references to status badges and
parity of the scripts it uses.

The `on:` section obviates job creation and resource usage for irrelevant events. GitHub Actions
cannot implement "master, except documentation only-commits" in the same file. Hence, deployments of
master will happen even on README change.

Combine [configure_deploy] and [deploy] into the same `run:` when `configure_deploy` primes file
system cache.

Here's a partial `deploy.yml` including only the aspects mentioned above. Notice env variables are
explicitly defined and `on.tags` is a [glob pattern](https://docs.github.com/en/free-pro-team@latest/actions/reference/workflow-syntax-for-github-actions#filter-pattern-cheat-sheet).
```yaml
on:
  push:
    tags: '[0-9]+.[0-9]+.[0-9]+**'  # Ex. 8.272.10 or 15.0.1_p9
    branches: master

jobs:
  deploy:
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v2
        with:
          fetch-depth: 1  # only needed to get the sha label
      - name: Deploy
        env:
          GH_USER: ${{ secrets.GH_USER }}
          GH_TOKEN: ${{ secrets.GH_TOKEN }}
        run: |  # GITHUB_REF will be refs/heads/master or refs/tags/MAJOR.MINOR.PATCH
          build-bin/configure_deploy &&
          build-bin/deploy $(echo ${GITHUB_REF} | cut -d/ -f 3)
```

### Example Travis setup
`.travis.yml` is a monolithic configuration file broken into stages. This means `test` and `deploy`
are in the same file. A simplest Travis `deploy` stage has two jobs: one for master pushes and
another for version tags. These jobs are controlled by event conditions.

The `if:` section obviates job creation and resource usage for irrelevant events. Travis does not
support file conditions. A `before_install` step to skip documentation-only commits will likely
complete in less than a minute (10 credit cost).

As billing is by the minute, it is most cost effective to combine test and deploy on master push.

Here's a partial `.travis.yml` including only the aspects mentioned above. Notice YAML anchors work
in Travis and `tag =~` [condition](https://github.com/travis-ci/travis-conditions) is a regular
expression.
```yaml
git:
  depth: false  # full git history for license check, and doc-only skipping

_terminate_if_only_docs: &terminate_if_only_docs |
  if [ -n "${TRAVIS_COMMIT_RANGE}" ] && ! git diff --name-only "${TRAVIS_COMMIT_RANGE}" -- | grep -qv '\.md$'; then
    echo "Stopping job as changes only affect documentation (ex. README.md)"
    travis_terminate 0
  fi

jobs:
  include:
    - stage: test
      if: branch = master AND tag IS blank AND type IN (push, pull_request)
      before_install: *terminate_if_only_docs
      install: |
        if [ "${TRAVIS_SECURE_ENV_VARS}" = "true" ] && [ "${TRAVIS_PULL_REQUEST}" = "false" ]; then
          export SHOULD_DEPLOY=true
          ./build-bin/configure_deploy
        else
          export SHOULD_DEPLOY=false
          ./build-bin/configure_test
        fi
      script:
        - ./build-bin/test || travis_terminate 1
        - if [ "${SHOULD_DEPLOY}" != "true" ]; then travis_terminate 0; fi
        - travis_wait ./build-bin/deploy master
    - stage: deploy
      # Ex. 8.272.10 or 15.0.1_p9
      if: tag =~ /^[0-9]+\.[0-9]+\.[0-9]+/ AND type = push AND env(GH_TOKEN) IS present
      install: ./build-bin/configure_deploy
      script: ./build-bin/deploy ${TRAVIS_TAG}
```
