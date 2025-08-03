#!/bin/bash

# Clean build script that suppresses Java warnings while preserving important output
# 
# Usage: ./scripts/clean-build.sh [maven-goals]
# Example: ./scripts/clean-build.sh clean compile test

set -e

# Default goals if none provided
GOALS="${@:-clean compile test}"

echo "🚀 Running Maven build with goals: $GOALS"
echo "⏳ Building..."

# Run Maven with Java 17 (no more warnings!)
./mvnw $GOALS 2>&1 | \
  grep -v "WARNING: A terminally deprecated method in sun.misc.Unsafe has been called" | \
  grep -v "WARNING: sun.misc.Unsafe::staticFieldBase has been called by com.google.inject" | \
  grep -v "WARNING: Please consider reporting this to the maintainers of class" | \
  grep -v "WARNING: sun.misc.Unsafe::staticFieldBase will be removed in a future release" | \
  grep -v "Mockito is currently self-attaching to enable the inline-mock-maker" | \
  grep -v "WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading" | \
  grep -v "WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage" | \
  grep -v "WARNING: Dynamic loading of agents will be disallowed by default in a future release" | \
  grep -v "OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes" | \
  grep -v "WARNING: A Java agent has been loaded dynamically" | \
  grep -v "Warning: Could not retrieve parameter.*ParameterNotFound" || true

# Check if Maven succeeded (exit code 0)
if [ ${PIPESTATUS[0]} -eq 0 ]; then
    echo "✅ Build completed successfully!"
else
    echo "❌ Build failed!"
    exit 1
fi