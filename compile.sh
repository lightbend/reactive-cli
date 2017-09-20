#!/bin/bash

set -ex

mkdir -p libhttpsimple/target
rm -rf libhttpsimple/target/*

gcc -c -fPIC -o libhttpsimple/target/httpsimple.o libhttpsimple/src/main/c/httpsimple.c
gcc -shared -fPIC -lcurl -o libhttpsimple/target/libhttpsimple.so libhttpsimple/target/httpsimple.o -lc
