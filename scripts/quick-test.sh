#!/bin/bash

# Quick test script for service module only
# Skips integration tests for faster feedback

set -e

echo "🧪 Running quick tests (service module only)..."

cd service
../mvnw test 2>&1 | \
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

if [ ${PIPESTATUS[0]} -eq 0 ]; then
    echo "✅ Quick tests passed!"
else
    echo "❌ Quick tests failed!"
    exit 1
fi