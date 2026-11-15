# SiteWrap

One APK for Android phones and Android TV / TV boxes. Pick a site from the grid and it opens full-screen in a WebView, "emulating" the browser you choose (Mobile, Desktop or Smart TV user agent).

## Build the APK (pick one)
- **Android Studio**: File > Open this folder > Run, or Build > Build APK(s). Output: `app/build/outputs/apk/debug/app-debug.apk`.
- **Command line**: `./gradlew assembleDebug` (needs JDK 17 + Android SDK 34).
- **No local setup**: push to a GitHub repo; Actions > "Build APK" produces the APK as a downloadable artifact.

## Install
- Phone: copy the APK over and open it (allow "install unknown apps").
- TV box: `adb connect <tv-ip>` then `adb install app-debug.apk`, or use the Downloader app / a USB stick.

## Use
- Grid: OK = open, long-press OK = edit/delete, "+" = add a site (name, URL, mode, remote cursor).
- In a site: D-pad moves an on-screen cursor (OK = click; push against a screen edge = scroll). Back = browser back, then exit.
- Long-press Back (or Menu key) = Reload / cursor on-off / switch Mobile-Desktop-TV mode / Close.

## Limits
- Google sign-in refuses embedded WebViews. DRM streaming (Netflix, Prime, Disney+) mostly will not play in a WebView.
- Only http(s) is followed; app deep links are blocked on purpose.
