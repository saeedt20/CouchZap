#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
if [[ -d "$project_dir/.tools/jdk-17.0.20.1+1" ]]; then
    export JAVA_HOME="$project_dir/.tools/jdk-17.0.20.1+1"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
if [[ -d "$project_dir/.tools/sdk" ]]; then
    export ANDROID_HOME="$project_dir/.tools/sdk"
fi
export GRADLE_USER_HOME="$project_dir/.tools/gradle-cache"
export ANDROID_USER_HOME="$project_dir/.tools/android-user"
if [[ $# -eq 0 ]]; then
    set -- assembleDebug testDebugUnitTest lintDebug --console=plain
fi
if [[ -x "$project_dir/.tools/gradle-9.3.1/bin/gradle" ]]; then
    exec "$project_dir/.tools/gradle-9.3.1/bin/gradle" "$@"
fi
exec "$project_dir/gradlew" "$@"
