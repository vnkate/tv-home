# Installs the launcher, grants the Wi-Fi name permission, starts it once and screenshots it.
param([string]$Apk = ".\app-debug.apk")
adb install -r $Apk
adb shell pm grant com.venky.tvhome android.permission.ACCESS_FINE_LOCATION
adb shell am start -n com.venky.tvhome/.MainActivity
Start-Sleep 3
New-Item -ItemType Directory -Force shots | Out-Null
adb shell screencap -p /sdcard/s.png
adb pull /sdcard/s.png shots\launcher-first-run.png
