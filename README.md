# reactive-cli #

CLI tools to generate Kubernetes resources for Akka, Play, Lagom based application.

_Currently in prototype stage - nothing usable just yet_

## Build setup

The CLI tools depends on Scala Native to build, as such follow the instructions on the [Scala Native setup](http://www.scala-native.org/en/latest/user/setup.html#installing-clang-and-runtime-dependencies) page.

Scala Native requires LLVM 3.7 and above.

### MacOS specific setup

On macOs this would mean ensuring XCode is updated to Apple's latest version. With Apple's latest XCode version, the minimum LLVM version is satisfied, so Homebrew install is not required. However, the following needs to be installed to ensure `gc.h` is available despite it being optional on the Scala Native setup page:

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

### Build libhttpsimple

The library `libhttpsimple` is a thin wrapper around `libcurl`. `libhttpsimple` is created due to difficulties binding the `CURLOption` enums from Scala Native. Normally enums in Scala Native will be wrapped around a value class containing a `CInt` or `CLong`. However, the `CURLOption` enums being generated using C macros, resulting in an enum that can't be mapped to a value class.

You must have `gcc` installed to build the `libhttpsimple` library.

To build `libhttpsimple` execute the following command.

```bash
$ sh compile.sh
```

## Building and running

Use the following SBT command to create the native executable:

```bash
$ sbt k8s-cli/nativeLink
```

Once built, the native executable can be found in the `k8s-cli/target/scala-2.11/k8s-cli-out` path, i.e.

```bash
$ k8s-cli/target/scala-2.11/k8s-cli-out --help
k8s-cli 0.1.0
Usage: k8s-cli [options]

  --help             Print this help text
```
