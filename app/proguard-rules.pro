# Project-specific R8 rules. Libraries in use ship their own consumer rules.
# Navigation routes are @Serializable objects; kotlinx.serialization ships the rules it needs.

# Tink (profile encryption, CL-151) parses keysets with protobuf-lite, which reads message fields by
# reflection. The Tink documentation asks apps that shrink to keep the fields of generated messages,
# so a release build cannot rename them and break reading a stored keyset.
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}
