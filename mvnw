#!/bin/bash

# Maven wrapper that ensures Java 17 is used
# This fixes the sun.misc.Unsafe warnings by using the correct Java version
# TODO: Resolve Error: WARNING: package sun.security.action not in java.base
# Set JAVA_HOME to Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Execute Maven with the provided arguments
exec /opt/homebrew/bin/mvn "$@"