# OpenShorts R8 rules.
# kotlinx.serialization and Media3 ship their own consumer rules; only app-specific
# rules belong here.

# Keep @Serializable model classes used for the PeerTube API.
-keep,includedescriptorclasses class org.openshorts.data.peertube.**$$serializer { *; }
-keepclassmembers class org.openshorts.data.peertube.** {
    *** Companion;
}
-keepclasseswithmembers class org.openshorts.data.peertube.** {
    kotlinx.serialization.KSerializer serializer(...);
}
