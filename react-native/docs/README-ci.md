# CI workflow — browser-paste fallback
#
# The automation token lacks the GitHub "Workflows" permission, so
# .github/workflows/build-android.yml can't be pushed by git from here.
# This is an exact copy. To enable CI builds (2 minutes, no terminal needed):
#
#  1. Open https://github.com/maca14313/callmony/new/main
#  2. File name: .github/workflows/build-android.yml
#  3. Copy THIS file's content (below) into it, press Commit changes.
#
# After that, every push to main builds the signed APK and publishes it
# to the Releases page automatically. (Alternatively: edit the token to
# add the Workflows permission, then push the local .github folder.)
name: Android APK

on:
  push:
    branches: [main]
    tags: ["v*"]
  workflow_dispatch:

jobs:
  build-apk:
    name: Build signed release APK
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Node 20
        uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Install dependencies
        run: npm ci

      - name: Build release APK
        working-directory: android
        run: |
          echo "sdk.dir=$ANDROID_HOME" > local.properties
          chmod +x ./gradlew
          ./gradlew assembleRelease --no-daemon --stacktrace

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: callmony-release-apk
          path: android/app/build/outputs/apk/release/*.apk
          if-no-files-found: error

      - name: Publish GitHub Release with the APK
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          VERSION=$(grep -oP 'versionName\s+"\K[^"]+' android/app/build.gradle | head -1)
          TAG="v$VERSION"
          APK=$(ls android/app/build/outputs/apk/release/*.apk | head -1)
          echo "Publishing $TAG with $APK"
          if gh release view "$TAG" >/dev/null 2>&1; then
            gh release upload "$TAG" "$APK" --clobber
          else
            gh release create "$TAG" "$APK" \
              --title "callmony $TAG — Android APK" \
              --notes "Signed release APK (universal — all CPU types, Android 6.0+).

          **Install:** download the .apk below, open it on your phone, allow \\"Install from unknown sources\\" when prompted.

          Built automatically from the latest \`main\` branch."
          fi

      - name: Build summary
        working-directory: android
        run: |
          echo "### callmony APK built ✅" >> $GITHUB_STEP_SUMMARY
          echo '```' >> $GITHUB_STEP_SUMMARY
          ls -lh app/build/outputs/apk/release/*.apk >> $GITHUB_STEP_SUMMARY
          echo '```' >> $GITHUB_STEP_SUMMARY
