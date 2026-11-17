---
'expo-camera': patch
---

[Android] Fixed the camera restarting when another prop of `CameraView` updates, for example `zoom` driven by Reanimated animated props. The preview jumped back to the default zoom during a pinch.
