#!/bin/bash

# This setup script is inspired by Scala Native's setup script:
# https://github.com/scala-native/scala-native/blob/master/bin/travis_setup.sh

# Fail if command failed, variable is unassigned, also show executed commands
set -eux

sudo apt-get update -qq

echo "Remove libunwind pre-bundled with clang"
sudo find /usr -name "*libunwind*" -delete

echo "Install Boehm GC, libunwind"
sudo apt-get install -y -qq \
  clang++-3.9 \
  libgc-dev \
  libunwind8-dev

echo "Install re2 from source"
# Starting from Ubuntu 16.04 LTS, it'll be available as http://packages.ubuntu.com/xenial/libre2-dev
sudo apt-get install -y make
export CXX=clang++-3.9
rm -rf re2
git clone https://code.googlesource.com/re2
pushd re2
git checkout 2017-03-01
make -j4 test
sudo make install prefix=/usr
make testinstall prefix=/usr
popd

echo "Install argonaut from source"
git clone https://github.com/argonaut-io/argonaut.git
pushd argonaut
sbt argonautNative/publishLocal
popd
