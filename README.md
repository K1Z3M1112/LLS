# LSFG-Android (combined repo)

This repo bundles two projects as sibling directories, matching the layout
their build files expect:

```
.
├── framegen-native-lib/        # patched lsfg-vk native library (C++/Vulkan)
│   └── thirdparty/
│       ├── vulkan-loader/      # volk — loads Vulkan API function pointers
│       ├── toml-parser/        # toml11 — reads TOML config files
│       ├── pe-dll-parser/      # pe-parse — parses the user's Lossless.dll (PE format)
│       └── dxbc-to-spirv/      # dxbc — translates DirectX shaders (DXBC) to Vulkan (SPIR-V)
├── android-app/                 # Android app (Kotlin + JNI), the buildable project
└── .github/workflows/build-apk.yml
```

`android-app/app/src/main/cpp/CMakeLists.txt` reaches into
`../../../../../framegen-native-lib` by relative path, so **do not rename
either top-level folder** — the sibling names must stay exactly as they are.
The same applies to the four `thirdparty/` folder names above; they're
wired into `framegen-native-lib/CMakeLists.txt`,
`android-app/app/src/main/cpp/CMakeLists.txt`, and `.gitmodules` by exact
path.

## One-time repo setup (do this after uploading/pushing this zip's contents)

The zip does not (and cannot) contain the contents of
`framegen-native-lib`'s third-party submodules — `.zip` exports never
include submodule content, only the pointer. A root-level `.gitmodules` is
already included here, but you need to register the submodules for real
once this is a git repo:

```sh
git init            # if not already a repo
git add .
git commit -m "Initial import"

# Re-add each submodule so git tracks it properly (this reads .gitmodules
# and clones each dependency into its placeholder directory):
git submodule sync --recursive
git submodule update --init --recursive
git add .gitmodules framegen-native-lib/thirdparty
git commit -m "Wire up submodules"

git remote add origin <your-repo-url>
git push -u origin main
```

If you already pushed without doing this, just run the two `git submodule`
commands above locally, commit, and push again — the CI workflow also runs
them itself on every build, so as long as `.gitmodules` is correct in the
repo, GitHub Actions will fetch the submodule sources even if your local
checkout hasn't.

## Building

Building is fully automated via GitHub Actions
(`.github/workflows/build-apk.yml`). It triggers on every push to `main`,
every pull request, and can also be run manually from the **Actions** tab
("Run workflow").

It produces two downloadable artifacts per run:

- **deepdrop-debug-apk** — unsigned/debug-signed, always built, ready to
  `adb install` immediately.
- **deepdrop-release-apk** — release build. Falls back to debug signing
  automatically unless you configure a real release keystore (optional, see
  below).

To download: open the **Actions** tab → the latest workflow run → scroll to
**Artifacts**.

### Optional: real release signing

By default the release APK is signed with the debug key (fine for personal
sideloading). To sign with your own key instead, add these **Repository
secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `LSFGA_STORE_FILE_B64` | Your `.jks`/`.keystore` file, base64-encoded (`base64 -w0 your.keystore`) |
| `LSFGA_STORE_PASSWORD` | Keystore password |
| `LSFGA_KEY_ALIAS` | Key alias |
| `LSFGA_KEY_PASSWORD` | Key password |

The workflow only writes these into `gradle.properties` when
`LSFGA_STORE_FILE_B64` is present, so leaving the secrets unset is safe and
just uses the debug-signing fallback.

## Building locally instead

```sh
cd android-app
git submodule update --init --recursive   # from repo root, if not done yet
./gradlew :app:assembleDebug               # or :app:assembleRelease
```

Toolchain: Android Studio Ladybug+, NDK 27.0.12077973, CMake 3.22.1, JDK 17.
See `android-app/README.md` for full architecture docs.

## Before you can actually use the app

There are now two frame-generation engines, selectable in-app under
Frame generation & pacing → Frame gen engine:

- **Lossless.dll** — the original reverse-engineered pipeline. Needs a
  **user-supplied `Lossless.dll`** from a legitimately owned Steam copy of
  Lossless Scaling — it is never bundled, committed, or distributed by this
  repo (see the license notes in `android-app/README.md`). The app prompts
  you to pick the DLL via the Storage Access Framework on first launch.
- **ncnn** — an alternative engine backed by the bundled
  `frame_interpolator_v2` ncnn model (`android-app/app/src/main/assets/models/`).
  No DLL required; just pick it from the engine selector. Runs entirely on
  the ncnn submodule (`framegen-native-lib/thirdparty/ncnn`, pulled in by
  `git submodule update --init --recursive` like the other thirdparty deps).
  See `android-app/app/src/main/cpp/ncnn_framegen.hpp` for the tiling/
  blending implementation notes and known limitations (fixed 256x256
  internal resolution, non-power-of-two multipliers approximated via
  recursive bisection).

Settings that apply to both engines (multiplier, flow scale, render
resolution, HDR, anti-artifacts) work the same either way; settings specific
to one engine (FP16 SPIR-V shaders for Lossless.dll, FP16 model weights for
ncnn) are hidden automatically when the other engine is selected.

## Licensing

- `android-app/` — custom license: personal/non-commercial use only, **no
  Play Store / app-store distribution, no commercial use**. See
  `android-app/LICENSE`.
- `framegen-native-lib/` — MIT License (inherited from upstream `lsfg-vk`).
  See `framegen-native-lib/LICENSE.md`.
