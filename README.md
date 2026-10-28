# Nail Status

Nail Status is the Android client for a personal status board. It writes the current status, meals, sleep, and calendar subscriptions to the Nail Status server. The public site only reads that data.

The app targets the current Android release on a Pixel 10 Pro. Minimum SDK is API 36.

## Development

Open this directory in Android Studio. Build and run with the Gradle wrapper:

```shell
./gradlew :app:assembleDebug
```

A release build is:

```shell
./gradlew :app:assembleRelease
```

The signed package is `app/build/outputs/apk/release/app-release.apk`. Release signing uses `keystore.properties` on the local machine. That file and the keystore are gitignored. A checkout without them can still build a debug package.

## AI

You may use an AI tool on a change only when you already know how to make that change yourself. AI is an aid, not a substitute for the skill the change requires.

Rules:

- Disclose every use of AI, including the model name.
- Write pull requests, issues, and discussions yourself. Do not use AI for that text.
- Review every line of AI-assisted code yourself before you submit it. You are responsible for the result.

## Release

Each installable release is a published GitHub Release on [NailFec/nfw-status-android](https://github.com/NailFec/nfw-status-android). The app checks `releases/latest` and installs a newer package only when the asset name matches `nfw-status-c{versionCode}.apk`.

1. Increase `versionCode` in `app/build.gradle.kts`. Update `versionName` when the user-visible version changes.
2. Build the signed package:

   ```shell
   ./gradlew :app:assembleRelease
   ```

3. Rename the APK so the version code is in the file name. Version code 2 becomes `nfw-status-c2.apk`.
4. Create a published GitHub Release and attach that file. Draft releases are ignored. The tag can be `v` plus `versionName`, for example `v1.1`.

```shell
./gradlew :app:assembleRelease
cp app/build/outputs/apk/release/app-release.apk /tmp/nfw-status-c2.apk
git push origin main
gh release create v1.1 /tmp/nfw-status-c2.apk \
  --title "1.1" \
  --notes "Updates since the previous release."
```

The phone compares that number with its own `versionCode`. A file named `nfw-status-c1.apk` is not an update for an install that is already version code 1. The next package must use a higher code.

Settings, **Check for updates**, shows the result. **Update** downloads the package and hands it to Android. The first time, Android asks you to allow this app to install packages. Confirm the system install dialog. The app cannot replace itself silently.

The release key must stay the same for every version. If the phone is running a debug build from Android Studio, uninstall it before installing the first signed release. Later releases then update in place.

Keep a private backup of `keystore/nfw-status-release.p12` and `keystore.properties`. Losing them makes further updates impossible.

## Copyright

Copyright NailFec.

This project is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
