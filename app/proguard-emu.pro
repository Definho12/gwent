-keep class com.guilherme.gwent.** { *; }
# só no teste do emulador: o executor de testes usa estas bibliotecas de dentro do app
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.annotation.** { *; }
-keep class androidx.core.** { *; }
