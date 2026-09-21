#!/usr/bin/env bash
# Optional SDK-only Linux/macOS build: no Gradle downloads needed.
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?Set ANDROID_HOME to Android SDK with platform 35 and build-tools 35.0.0}"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
sdk_tools="$ANDROID_HOME/build-tools/35.0.0"
android_api="$ANDROID_HOME/platforms/android-35/android.jar"
build_dir="$PWD/build/manual"
rm -rf "$build_dir/classes" "$build_dir/dex"
mkdir -p "$build_dir/classes" "$build_dir/dex"
"$JAVA_HOME/bin/javac" --release 8 -cp "$android_api:app/libs/zxing-core-3.5.3.jar" -d "$build_dir/classes" app/src/main/java/com/abdulla/clonephone/*.java
"$sdk_tools/aapt2" compile --dir app/src/main/res -o "$build_dir/resources.zip"
sed 's/<manifest /<manifest package="com.abdulla.clonephone" /' app/src/main/AndroidManifest.xml > "$build_dir/AndroidManifest.xml"
"$sdk_tools/aapt2" link -o "$build_dir/base.apk" -I "$android_api" -A app/src/main/assets --manifest "$build_dir/AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 34 --version-code 35 --version-name 9.2 "$build_dir/resources.zip"
"$JAVA_HOME/bin/jar" cf "$build_dir/classes.jar" -C "$build_dir/classes" .
"$sdk_tools/d8" --release --min-api 26 --lib "$android_api" --output "$build_dir/dex" "$build_dir/classes.jar" app/libs/zxing-core-3.5.3.jar
"$JAVA_HOME/bin/jar" uf "$build_dir/base.apk" -C "$build_dir/dex" classes.dex
"$sdk_tools/zipalign" -f -p 4 "$build_dir/base.apk" "$build_dir/aligned.apk"
"$sdk_tools/apksigner" sign --ks test-signing/debug.keystore --ks-pass pass:android --key-pass pass:android --out "$build_dir/REBAR-IT-Clone-9.2.apk" "$build_dir/aligned.apk"
"$sdk_tools/apksigner" verify --verbose "$build_dir/REBAR-IT-Clone-9.2.apk"
echo "APK: $build_dir/REBAR-IT-Clone-9.2.apk"
