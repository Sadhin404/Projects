#!/bin/bash
set -e
export JAVA_HOME=$HOME/workspace/tools/jdk17
export PATH=$JAVA_HOME/bin:$PATH
SDK=$HOME/workspace/tools/android-sdk
BT=$SDK/build-tools/34.0.0
AJAR=$SDK/platforms/android-35/android.jar
AARS=$HOME/workspace/tools/aars
SAMPLE=$HOME/workspace/tools/sample_extract
cd $HOME/workspace/grabit_android
rm -rf build && mkdir -p build/gen build/classes build/dex build/resz build/lib/arm64-v8a build/cleanjars

# resources: mine + ytdlp raw (from 0.18.1 sample) as library resource
$BT/aapt2 compile --dir app/src/main/res -o build/resz/app.zip
mkdir -p build/libres/raw && cp $SAMPLE/res/raw/ytdlp build/libres/raw/ytdlp
$BT/aapt2 compile --dir build/libres -o build/resz/library.zip
$BT/aapt2 link -o build/app-unsigned.apk -I $AJAR \
  --manifest app/src/main/AndroidManifest.xml \
  -R build/resz/app.zip build/resz/library.zip \
  --extra-packages com.yausername.youtubedl_android:com.yausername.ffmpeg:com.yausername.youtubedl_common:com.yausername.aria2c \
  --java build/gen --auto-add-overlay

# compile my java against the 0.15 API jars (signatures verified identical in 0.18.1)
for n in library ffmpeg common; do mkdir -p build/aar/$n && unzip -q -o $AARS/$n-0.15.0.aar -d build/aar/$n; done
CP=$AJAR:build/aar/library/classes.jar:build/aar/common/classes.jar:build/aar/ffmpeg/classes.jar:$AARS/kotlin-stdlib.jar:$AARS/jackson-databind.jar:$AARS/jackson-core.jar:$AARS/jackson-annotations.jar:$AARS/commons-io.jar:$AARS/commons-compress.jar
find app/src/main/java build/gen -name "*.java" > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -cp "$CP" -d build/classes @build/sources.txt

# clean dependency jars (strip module-info that d8 rejects)
for j in $AARS/kotlin-stdlib2.jar $AARS/annotations.jar $AARS/jackson-databind.jar $AARS/jackson-core.jar $AARS/jackson-annotations.jar $AARS/commons-io.jar $AARS/commons-compress.jar; do
  n=$(basename $j); cp $j build/cleanjars/$n; zip -q -d build/cleanjars/$n "module-info.class" "META-INF/versions/*" 2>/dev/null || true
done
CJ=$HOME/workspace/grabit_android/build/cleanjars

# dex: my classes + 0.18.1 library dexes extracted from the official sample app + dependency jars
$BT/d8 --lib $AJAR --min-api 26 --output build/dex \
  $(find build/classes -name '*.class') \
  $SAMPLE/classes2.dex $SAMPLE/classes3.dex $SAMPLE/classes4.dex $SAMPLE/classes5.dex $SAMPLE/classes6.dex $SAMPLE/classes7.dex \
  $CJ/kotlin-stdlib2.jar $CJ/annotations.jar \
  $CJ/jackson-databind.jar $CJ/jackson-core.jar $CJ/jackson-annotations.jar $CJ/commons-io.jar $CJ/commons-compress.jar

# assemble: dex at root + native libs from sample (stored, uncompressed)
cp build/app-unsigned.apk build/app-full.apk
cd build/dex && zip -q ../app-full.apk classes.dex && cd ../..
cp $SAMPLE/lib/arm64-v8a/*.so build/lib/arm64-v8a/
ls -lh build/lib/arm64-v8a/
cd build && zip -q -0 -r app-full.apk lib && cd ..

$BT/zipalign -f -p 4 build/app-full.apk build/app-aligned.apk
cp $HOME/workspace/todo_android/keystore.jks build/keystore.jks
$BT/apksigner sign --ks build/keystore.jks --ks-pass env:KEYSTORE_PASS --key-pass env:KEY_PASS --out $HOME/workspace/your_files/DhoreFel_v31_GB.apk build/app-aligned.apk
ls -lh $HOME/workspace/your_files/DhoreFel_v31_GB.apk
