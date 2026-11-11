#!/bin/bash
set -e
export JAVA_HOME=$HOME/workspace/tools/jdk17
export PATH=$JAVA_HOME/bin:$PATH
SDK=$HOME/workspace/tools/android-sdk
BT=$SDK/build-tools/34.0.0
AJAR=$SDK/platforms/android-35/android.jar
AARS=$HOME/workspace/tools/aars
cd $HOME/workspace/grabit_android
rm -rf build && mkdir -p build/aar build/gen build/classes build/dex build/resz

# extract AARs
for n in library ffmpeg common; do mkdir -p build/aar/$n && unzip -q -o $AARS/$n-0.15.0.aar -d build/aar/$n; done

# compile resources: mine + each library's res
$BT/aapt2 compile --dir app/src/main/res -o build/resz/app.zip
# library res: only raw/ytdlp is needed by its code; its values.xml defines app_name which conflicts with ours
mkdir -p build/libres/raw && cp build/aar/library/res/raw/ytdlp build/libres/raw/ytdlp
$BT/aapt2 compile --dir build/libres -o build/resz/library.zip
ls build/resz/

# link
$BT/aapt2 link -o build/app-unsigned.apk -I $AJAR \
  --manifest app/src/main/AndroidManifest.xml \
  -R build/resz/*.zip \
  --extra-packages com.yausername.youtubedl_android:com.yausername.ffmpeg:com.yausername.youtubedl_common \
  --java build/gen --auto-add-overlay

# compile java
CP=$AJAR:build/aar/library/classes.jar:build/aar/common/classes.jar:build/aar/ffmpeg/classes.jar:$AARS/kotlin-stdlib.jar:$AARS/jackson-databind.jar:$AARS/jackson-core.jar:$AARS/jackson-annotations.jar:$AARS/commons-io.jar:$AARS/commons-compress.jar
find app/src/main/java build/gen -name "*.java" > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -cp "$CP" -d build/classes @build/sources.txt

# strip module-info / multi-release entries that d8 rejects
mkdir -p build/cleanjars
for j in $AARS/kotlin-stdlib.jar $AARS/kotlin-stdlib-jdk8.jar $AARS/kotlin-stdlib-jdk7.jar $AARS/annotations.jar $AARS/jackson-databind.jar $AARS/jackson-core.jar $AARS/jackson-annotations.jar $AARS/commons-io.jar $AARS/commons-compress.jar; do
  n=$(basename $j); cp $j build/cleanjars/$n; zip -q -d build/cleanjars/$n "module-info.class" "META-INF/versions/*" 2>/dev/null || true
done
CJ=$HOME/workspace/grabit_android/build/cleanjars

# dex everything: my classes + library jars + dependency jars
$BT/d8 --lib $AJAR --min-api 26 --output build/dex \
  $(find build/classes -name '*.class') \
  build/aar/library/classes.jar build/aar/common/classes.jar build/aar/ffmpeg/classes.jar \
  $CJ/kotlin-stdlib.jar $CJ/kotlin-stdlib-jdk8.jar $CJ/kotlin-stdlib-jdk7.jar $CJ/annotations.jar \
  $CJ/jackson-databind.jar $CJ/jackson-core.jar $CJ/jackson-annotations.jar $CJ/commons-io.jar $CJ/commons-compress.jar

# add dex at ROOT + native libs stored uncompressed
cp build/app-unsigned.apk build/app-full.apk
cd build/dex && zip -q ../app-full.apk classes.dex && cd ../..
mkdir -p build/lib/arm64-v8a
cp build/aar/library/jni/arm64-v8a/*.so build/lib/arm64-v8a/
cp build/aar/ffmpeg/jni/arm64-v8a/*.so build/lib/arm64-v8a/
ls -lh build/lib/arm64-v8a/
cd build && zip -q -0 -r app-full.apk lib && cd ..

# align + sign
$BT/zipalign -f -p 4 build/app-full.apk build/app-aligned.apk
if [ ! -f $HOME/workspace/todo_android/keystore.jks ]; then echo "keystore missing"; exit 1; fi
cp $HOME/workspace/todo_android/keystore.jks build/keystore.jks
$BT/apksigner sign --ks build/keystore.jks --ks-pass pass:neon1234 --key-pass pass:neon1234 --out $HOME/workspace/your_files/GrabIt_v4_Remake.apk build/app-aligned.apk
$BT/apksigner verify --print-certs $HOME/workspace/your_files/GrabIt_v4_Remake.apk | head -3
ls -lh $HOME/workspace/your_files/GrabIt_v4_Remake.apk
