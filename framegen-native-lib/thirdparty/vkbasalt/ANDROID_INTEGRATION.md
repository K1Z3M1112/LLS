# DeepDrop vkBasalt integration

The `src/` tree in this directory is vendored directly from the supplied vkBasalt source.
No post-processing shader/effect implementation was rewritten.

DeepDrop's Android side owns only the connection/configuration boundary:
- `vkbasalt_bridge.*` writes the native vkBasalt-compatible configuration.
- Kotlin settings persist the effect chain and ReShade `.fx` paths.
- `.fx` files are managed as user assets rather than copied into the vkBasalt source.

Important Android loader detail: the Khronos Android Vulkan loader discovers layers through
its Android layer mechanism and does not use the desktop JSON-manifest search path. Normal
APK-private files are therefore not automatically discoverable as Vulkan layers. A true
loader-level
`VK_LAYER_VKBASALT_post_processing` deployment on Android requires an Android-compatible
layer-loader/debug deployment. The vendored source is kept intact so the render-layer adapter
can be connected without replacing vkBasalt's effect implementation.
