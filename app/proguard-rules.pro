# Serialization uses explicit field names, not class/field reflection.
# Preserve annotations needed by existing Android/Compose/WorkManager dependencies.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
