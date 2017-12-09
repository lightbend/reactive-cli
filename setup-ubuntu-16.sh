#!/bin/bash

# Fail if command failed, variable is unassigned, also show executed commands
set -eux

sudo apt-get update -qq

echo "Install Boehm GC, libunwind, RE2"
sudo apt-get install -y -qq \
  clang++-3.9 \
  libgc-dev \
  libunwind8-dev \
  libre2-dev \
  jq

echo "Install argonaut from source"
rm -rf argonaut
git clone https://github.com/argonaut-io/argonaut.git
pushd argonaut
git checkout 2c719f155744881d30fc932dcbbf597a9ce8084c
sbt argonautNative/publishLocal
popd
