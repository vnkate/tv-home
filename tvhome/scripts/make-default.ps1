# Makes the launcher the default Home, presses Home, and shows who is actually in front.
adb shell cmd package set-home-activity com.venky.tvhome/.MainActivity
adb shell input keyevent KEYCODE_HOME
Start-Sleep 2
adb shell dumpsys activity activities | Select-String "mResumedActivity|mFocusedApp"
New-Item -ItemType Directory -Force shots | Out-Null
adb shell screencap -p /sdcard/s.png
adb pull /sdcard/s.png shots\after-make-default.png
