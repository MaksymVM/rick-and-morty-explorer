# Deobfuscation & crash report stack traces
-keepattributes SourceFile,LineNumberTable

# Retrofit & runtime annotations
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keep interface com.example.rickandmortyexplorer.network.RickAndMortyApiService { *; }

# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-keep,includedescriptorclasses class com.example.rickandmortyexplorer.network.**$$serializer { *; }
-keepclassmembers class com.example.rickandmortyexplorer.network.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.rickandmortyexplorer.network.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Retrofit optional platform references
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
