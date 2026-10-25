# Puts a previous launcher back as Home. Default target: Projectivy.
# Stock Google launcher instead:  .\restore-launcher.ps1 com.google.android.tvlauncher/.MainActivity
param([string]$Target = "com.spocky.projengmenu/.ui.home.MainActivity")
adb shell cmd package set-home-activity $Target
adb shell input keyevent KEYCODE_HOME
Start-Sleep 2
adb shell dumpsys activity activities | Select-String "mResumedActivity|mFocusedApp"
