# reactive-cli #

CLI tools to generate Kubernetes resources for Akka, Play, Lagom based application.

_Currently in prototype stage - nothing usable just yet_

## Build setup

The CLI tools depends on Scala Native to build, the setup scripts provided in the project follow the instructions on the [Scala Native setup](http://www.scala-native.org/en/latest/user/setup.html#installing-clang-and-runtime-dependencies) page.

Please ensure you have read through each of the platform-specific setup as there may be manual steps for you to follow.

The setup script will install the prerequisites listed below.

### Prerequisites

* LVM 3.7+
* gcc
* libcurl with `curl.h` header file
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

This project uses [SBT Native Packager](https://github.com/sbt/sbt-native-packager) to produce release artifacts.

#### deb
`sbt clean debian:packageBin`

#### rpm
`sbt clean rpm:packageBin`

## Maintenance

Enterprise Suite Platform Team <es-platform@lightbend.com>
