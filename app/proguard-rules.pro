# R8 configuration for the release build (isMinifyEnabled/isShrinkResources are both on).
#
# There is deliberately very little here. The app maps Firestore documents by hand
# (see FirebaseGameRepository/FirebaseMultiplayerRepository: explicit `data["field"]`
# reads, never toObject()/POJO reflection), so no model class needs keeping. The
# Firebase, Play Services Ads, UMP and App Check artifacts ship their own consumer
# rules. Add a keep rule here only with a real symptom behind it -- a blanket
# `-keep class com.softyorch.**` would silently undo the shrinking this build wants.

# Readable crash reports. Without these, a Play Console stack trace from a release
# build has no file or line numbers, and there is no Crashlytics in this app to
# upload a mapping file to -- the mapping in app/build/outputs/mapping/release/ is
# the only way back, so keep the traces worth mapping.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin coroutines' internal service loader entries; R8 warns about these without
# them on some versions and they cost nothing.
-dontwarn kotlinx.coroutines.**
