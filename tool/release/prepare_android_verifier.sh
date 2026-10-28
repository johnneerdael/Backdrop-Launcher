#!/usr/bin/env bash
# Runner images provide the Android SDK without putting sdkmanager on PATH.
set -euo pipefail

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk_root" ]; then
  printf '%s\n' 'Set ANDROID_HOME or ANDROID_SDK_ROOT to the Android SDK directory.' >&2
  exit 1
fi

verifier="$sdk_root/build-tools/36.0.0/apksigner"
if [ ! -x "$verifier" ]; then
  manager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
  if [ ! -x "$manager" ]; then
    printf 'Android SDK manager was not found at %s\n' "$manager" >&2
    exit 1
  fi
  "$manager" 'build-tools;36.0.0'
fi

if [ ! -x "$verifier" ]; then
  printf 'APK signature verifier was not found at %s\n' "$verifier" >&2
  exit 1
fi
printf 'APK signature verifier ready: %s\n' "$verifier"
