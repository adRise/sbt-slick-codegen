#!/usr/bin/env bash

SBT_JVM_OPTS="-J-Xms4g -J-Xmx4g -J-Xss8m -J-XX:MaxMetaspaceSize=1024m -J-XX:+UseG1GC"

sbt $SBT_JVM_OPTS "release release-version $TUBI_PROJECT_VERSION with-defaults";