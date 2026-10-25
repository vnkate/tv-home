# TV Home (Apple TV style launcher for the Redmi / MiTV-MOOQ1)

## 1. Build the APK on GitHub (no installs on your PC)
1. Sign in to github.com, click **New repository**, name it `tvhome`, create it.
2. Click **uploading an existing file**, open this folder and drag EVERYTHING inside it
   (including the `.github` folder) into the browser. Click **Commit changes**.
3. Open the **Actions** tab, wait for the green tick (3-6 minutes), open the run, download the
   `tvhome-apk` artifact and unzip it to get `app-debug.apk`.
If there is no run in Actions, the `.github` folder did not upload: use **Add file > Create new file**,
name it `.github/workflows/build.yml` and paste in the contents of that file.
If the run is red, paste me the error from the failed step.

## 2. Install and try it
Put `app-debug.apk` and the `scripts` folder in one folder, open PowerShell there:
    .\scripts\install.ps1
Check the screenshot, then:
    .\scripts\make-default.ps1
Check the screenshot and the printed line: it must name com.venky.tvhome.
If Home still opens Projectivy, the Home Button Remapper app (flar2.homebutton) is probably
redirecting the key; change its setting.

## 3. Undo / go back
    .\scripts\restore-launcher.ps1                  (Projectivy)
    .\scripts\restore-launcher.ps1 com.google.android.tvlauncher/.MainActivity   (stock)
Re-enable any disabled app:  adb shell pm enable <package>   (or run restore.ps1 in tv-backup)

## 4. Change the dock, grid or look
- Dock apps / order / columns: `app/src/main/res/values/config.xml` (edit on GitHub, commit, rebuild).
  Keep dock_apps the same length as `columns`.
- Background is pitch black: change Color.BLACK for `root` in MainActivity.java.
- Font: the workflow tries to download Outfit; if it fails the system thin font is used.
- Remote keys: Up/Down move one row, Up from the dock goes to the top bar, Back returns to the dock.
