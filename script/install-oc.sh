#!/bin/bash

set -exu

docker --version
curl -L https://github.com/openshift/origin/releases/download/v3.10.0/openshift-origin-client-tools-v3.10.0-dd10d17-linux-64bit.tar.gz | tar xvz --strip 1 && chmod +x oc && sudo cp oc /usr/local/bin/ && rm oc
