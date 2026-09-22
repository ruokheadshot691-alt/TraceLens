# ONNX Runtime (JNI)
-keep class ai.onnxruntime.** { *; }
# Tesseract4Android (JNI)
-keep class com.googlecode.tesseract.android.** { *; }
# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
