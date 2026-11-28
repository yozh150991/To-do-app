# Screenshot baselines

Baselines for `ui/src/jvmTest/.../ScreenshotTest.kt` (spec §12.1, Roborazzi). Each test records its
screenshot in CI and compares it with the file of the same name here (`Baselines.verify`).

- **New screenshot:** the test passes and CI prints the image base64-encoded into the log of the
  job "Build and JVM tests" (step "Print new screenshot baselines"); the PNG is committed here.
- **Intended UI change:** delete the old baseline and push; CI prints the new one.
- **Unintended change:** the test fails; the diff (changed pixels in red) is in the build artifact
  under `ui/build/outputs/roborazzi/diff/`.

Baselines are always taken from CI: fonts and antialiasing differ between machines.
