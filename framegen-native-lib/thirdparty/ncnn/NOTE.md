# ncnn submodule placeholder

This directory is registered in the root `.gitmodules` as a git submodule
pointing at https://github.com/Tencent/ncnn, the same way
`vulkan-loader`/`pe-dll-parser`/`dxbc-to-spirv` are. It was added by an
offline patch (no network access) so the actual upstream source tree isn't
present yet in this checkout.

Populate it with the normal submodule flow described in the root README:

```sh
git submodule sync --recursive
git submodule update --init --recursive
```

`android-app/app/src/main/cpp/CMakeLists.txt` will fail fast with a clear
`FATAL_ERROR` (rather than a confusing downstream compile error) if this
directory doesn't contain `CMakeLists.txt` when the native build runs.
