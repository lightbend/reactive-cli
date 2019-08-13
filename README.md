# reactive-cli

[![Build Status](https://api.travis-ci.org/lightbend/reactive-cli.png?branch=master)](https://travis-ci.org/lightbend/reactive-cli)

This project is a component of [Lightbend Orchestration](https://developer.lightbend.com/docs/lightbend-orchestration/latest/). Refer to its documentation for usage, examples, and reference information.

`reactive-cli` is a CLI tool, `rp`, that can inspect Docker images created by [sbt-reactive-app](https://github.com/lightbend/sbt-reactive-app) and generate resources for Kubernetes, DC/OS and potentially other target platforms.

## Project Status


Lightbend Orchestration is no longer actively developed and will reach its [End of Life](https://developer.lightbend.com/docs/lightbend-platform/introduction/getting-help/support-terminology.html#eol) on April 15, 2020.

We recommend [Migrating to the Improved Kubernetes Deployment Experience](https://developer.lightbend.com/docs/lightbend-orchestration/current/migration.html).

## Installation / Usage

Consult the [Lightbend Orchestration](https://developer.lightbend.com/docs/lightbend-orchestration/current/setup/cli-installation.html#install-the-cli) documentation for setup and configuration.

## Developer

### Build setup

The CLI depends on Scala Native to build, the setup scripts provided in the project follow the instructions on the [Scala Native setup](http://www.scala-native.org/en/latest/user/setup.html#installing-clang-and-runtime-dependencies) page.

Please ensure you have read through each of the platform-specific setup as there may be manual steps for you to follow.

The setup script will install the prerequisites listed below.

### Prerequisites

* LVM 3.7+
* gcc
* libcurl with `curl.h` header file and OpenSSL support
* libgc
* libunwind
* libre2
* nexe (for JS/Windows builds)

### npm specific setup

You'll need `nexe` on your path. One good way of setting up npm to do this is documented on [this StackOverflow post](https://stackoverflow.com/questions/10081293/install-npm-into-home-directory-with-distribution-nodejs-package-ubuntu).

Once setup, you can use the following command to install nexe:

```bash
npm i nexe -g
```

### macOS specific setup

Ensure XCode is updated to Apple's latest version. With Apple's latest XCode version, the minimum LLVM version is satisfied, so Homebrew install is not required.

Once XCode is updated to Apple's latest version, execute the following command to setup the project:

```bash
$ brew install bdw-gc re2 jq && \
    brew install curl --with-openssl
```

### Ubuntu 16 specific setup

Execute the following command to setup the project:

```bash
$ sudo apt-get install -y -qq \
    clang++-3.9 \
    libgc-dev \
    libunwind8-dev \
    libre2-dev \
    libcurl4-openssl-dev \
    jq
```

### IntelliJ setup

After importing into IntelliJ, there will be lots of errors. To fix, manually delete the scalalib_native
library from the project libraries, as described [here](https://github.com/twitter/rsc/issues/13#issuecomment-345429964).
This is due to lack of support for sbt-crossproject in IntelliJ.

## Building and running

Use the following sbt command to create the native executable:

```bash
$ sbt nativeLink
```

Once built, the native executable can be found in the `cli/target/scala-2.11/rp` path, i.e.

```bash
$ cli/native/target/scala-2.11/reactive-cli-out --help
reactive-cli 1.0.0
Usage: reactive-cli [options]

  --help             Print this help text
```

## Packaging

This project uses a Docker-based build system that builds `.rpm` and `.deb` files inside Docker containers for each
supported distribution. To add a distribution, add a `BuildInfo` instance in `project/BuildInfo.scala` emulating
the ones already created.

#### Prerequisites

The build system uses publicly available Docker images that are pushed to Bintray. To rebuild / update these images,
you'll need to run the following:

```bash
sbt buildAllDockerImages
```

Afterwards, it will give you the commands you must run to push these images (sbt having tagged them). For example,
below is pushing one of these images:

```bash
docker push lightbend-docker-registry.bintray.io/rp/reactive-cli-build-debian-9
```

Note that this doesn't normally need to be done as part of project setup, as the build system will simply pull down
the build images for you.

#### Building a single distribution package locally

```
sbt "build ubuntu-16-04"
```

#### Building every distribution

```
sbt buildAll
```

Once built, you can find the packages in `target/stage/<name>/output`.
