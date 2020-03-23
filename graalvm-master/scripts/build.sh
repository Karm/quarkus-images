#!/bin/bash

JDK_VERSION=$1
GRAALVM_BRANCH=${2:-master}

jdk8() {
    echo "Building GraalVM for JDK 8"
    wget -q https://github.com/graalvm/openjdk8-jvmci-builder/releases/download/jvmci-20.0-b02/openjdk-8u242-jvmci-20.0-b02-linux-amd64.tar.gz
    tar zxf openjdk-8u242-jvmci-20.0-b02-linux-amd64.tar.gz
    export JAVA_HOME=/work/openjdk1.8.0_242-jvmci-20.0-b02
}

jdk11() {
    echo "Building GraalVM for JDK 11"
    wget -q https://github.com/graalvm/labs-openjdk-11/releases/download/jvmci-20.0-b02/labsjdk-ce-11.0.6+9-jvmci-20.0-b02-linux-amd64.tar.gz
    tar zxf labsjdk-ce-11.0.6+9-jvmci-20.0-b02-linux-amd64.tar.gz
    export JAVA_HOME=/work/labsjdk-ce-11.0.6-jvmci-20.0-b02
}

if [ "${JDK_VERSION}" == "jdk8" ]; then
    jdk8
elif [ "$JDK_VERSION" == "jdk11" ]; then
    jdk11
else
    echo "Need to provide a valid JDK version: jdk8, jdk11"
    exit 1
fi

mkdir graal
cd graal || exit
export PATH=$PWD/mx:$PATH

git clone --depth=1 --branch $GRAALVM_BRANCH --single-branch https://github.com/oracle/graal
git clone --depth=1 https://github.com/graalvm/mx

cd graal/vm  || exit
echo "Git commit: `git rev-parse HEAD`"

mx clean
mx --disable-polyglot --disable-libpolyglot --dynamicimports /substratevm --skip-libraries=true build

echo "Copying Graal SDK to /output"
cp -R latest_graalvm_home/ /output/graal_dist
