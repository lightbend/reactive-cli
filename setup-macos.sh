#!/bin/bash

# Fail if command failed, variable is unassigned, also show executed commands
set -eux

echo "Install Boehm GC, RE2"
brew install bdw-gc re2

echo "Install argonaut from source"
rm -rf argonaut
git clone https://github.com/argonaut-io/argonaut.git
pushd argonaut
sbt argonautNative/publishLocal
popd
