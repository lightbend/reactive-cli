# reactive-cli #

CLI tools to generate Kubernetes resources for Akka, Play, Lagom based application.

_Currently in prototype stage - nothing usable just yet_

## Build setup

The CLI tools depends on Scala Native to build, as such follow the instructions on the [Scala Native setup](http://www.scala-native.org/en/latest/user/setup.html#installing-clang-and-runtime-dependencies) page.

### Prerequisites

* LVM 3.7+
* gcc

### macOS specific setup

On macOS this would mean ensuring XCode is updated to Apple's latest version. With Apple's latest XCode version, the minimum LLVM version is satisfied, so Homebrew install is not required. However, the following needs to be installed to ensure `gc.h` is available despite it being optional on the Scala Native setup page:

```bash
$ brew install bdw-gc re2
```

### Build native Argonaut (Temporarily)

_This step is temporary until Argonaut artefact for Scala Native 0.3 is released_

```bash
$ git clone https://github.com/argonaut-io/argonaut.git
$ cd argonaut
$ sbt argonautNative/publishLocal
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
