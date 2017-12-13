# reactive-cli

This project implements a CLI tool that can inspect Docker images created by [sbt-reactive-app](https://github.com/lightbend/sbt-reactive-app) and generate resources for Kubernetes and potentially other target platforms.

This is one component of Lightbend's Platform Tooling that makes it easy to deploy Akka, Lagom, and Play applications to Kubernetes.

## Installation

Consult the [Platform Tooling](https://s3-us-west-2.amazonaws.com/rp-tooling-temp-docs/deployment-setup.html#install-the-cli) documentation.

## Developer

## Build setup

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

### Argonaut native build (Temporarily)

_The build script temporary builds Argonaut native locally until artifact for Scala Native 0.3 is released_

### macOS specific setup

Ensure XCode is updated to Apple's latest version. With Apple's latest XCode version, the minimum LLVM version is satisfied, so Homebrew install is not required.

Once XCode is updated to Apple's latest version, execute the following command to setup the project:

```bash
$ bash setup-macos.sh
```

### Ubuntu 16 specific setup

Execute the following command to setup the project:

```bash
$ bash setup-ubuntu-16.sh
```

## Building and running

Use the following SBT command to create the native executable:

```bash
$ sbt cli/nativeLink
```

Once built, the native executable can be found in the `cli/target/scala-2.11/rp` path, i.e.

```bash
$ cli/target/scala-2.11/rp --help
reactive-cli 0.1.0
Usage: reactive-cli [options]

  --help             Print this help text
```

## Packaging

This project uses a Docker-based build system that builds `.rpm` and `.deb` files inside Docker containers for each
supported distribution. To add a distribution, add a `BuildInfo` instance in `project/BuildInfo.scala` emulating
the ones already created.

#### Building a single distribution package locally

```sbt "build ubuntu-16-04"```

#### Building every distribution

```sbt buildAll```

Once built, you can find the packages in `target/stage/<name>/output`.

## Releasing

Consult the _Platform Tooling Release Process_ document in Google Drive.

## Maintenance

Enterprise Suite Platform Team <es-platform@lightbend.com>
