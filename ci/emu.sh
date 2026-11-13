#!/bin/bash
# Roda os testes no emulador e junta prints + relatório em ./shots
echo "sdk.dir=$ANDROID_HOME" > local.properties
timeout 1200 gradle :app:connectedReleaseAndroidTest -Pemu -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --console=plain > emu.log 2>&1
echo $? > emu.status
mkdir -p shots
adb pull /sdcard/Android/data/com.guilherme.gwent/files/shots/. shots/ || true
cp emu.log shots/emu-log.txt
find app/build/outputs/androidTest-results -name "*.xml" -exec cp {} shots/ \; 2>/dev/null || true
adb logcat -d -s GwentTest:I AndroidRuntime:E > shots/logcat.txt || true
exit 0
