#!/bin/bash
# Plain build (no Gradle/Maven needed): aapt2 + javac + dx + apksigner
set -e
cd "$(dirname "$0")"
BT=/usr/lib/android-sdk/build-tools/debian
JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
OUT=build; rm -rf $OUT; mkdir -p $OUT/res $OUT/classes $OUT/gen
$BT/aapt2 compile --dir res -o $OUT/res
$BT/aapt2 link -o $OUT/base.apk -I $JAR --manifest AndroidManifest.xml \
  --java $OUT/gen --min-sdk-version 14 --target-sdk-version 26 \
  --version-code 3 --version-name "2.0.1" $OUT/res/*.flat
javac -nowarn -source 8 -target 8 -Xlint:-options -bootclasspath $JAR -classpath $JAR -d $OUT/classes \
  $(find src $OUT/gen -name '*.java')
$BT/dx --dex --output=$OUT/classes.dex $OUT/classes
cd $OUT && cp base.apk unsigned.apk && zip -q -j unsigned.apk classes.dex && cd ..
$BT/zipalign -f 4 $OUT/unsigned.apk $OUT/aligned.apk
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Techno" >/dev/null 2>&1
$BT/apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out HoloGallery_v2.apk $OUT/aligned.apk
echo "Built: $(pwd)/HoloGallery_v2.apk"
