# OpenShorts R8 rules.
# kotlinx.serialization and Media3 ship their own consumer rules; only app-specific
# rules belong here.

# Keep @Serializable models (PeerTube API, stored likes/saves/follows, chats).
-keep,includedescriptorclasses class org.openshorts.data.**$$serializer { *; }
-keepclassmembers class org.openshorts.data.** {
    *** Companion;
}
-keepclasseswithmembers class org.openshorts.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
