#!/bin/bash

# Fail if command failed, variable is unassigned, also show executed commands
set -eux

echo "Install Boehm GC, RE2, jq"
brew install bdw-gc re2 jq

echo "Install curl (with OpenSSL support)"
brew install curl --with-openssl

echo "Install argonaut from source"
rm -rf argonaut
git clone https://github.com/argonaut-io/argonaut.git
pushd argonaut
git checkout 2c719f155744881d30fc932dcbbf597a9ce8084c
sbt argonautNative/publishLocal
popd
