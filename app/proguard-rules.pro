# Keep ML Kit model metadata and recognizer classes available after shrinking.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }

# ONNX Runtime calls back into these classes from native code.
-keep class ai.onnxruntime.** { *; }
