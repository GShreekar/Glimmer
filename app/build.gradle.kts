plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  // Was pinned to a hardcoded "2.0.21" while the Kotlin compiler itself (libs.versions.toml's
  // `kotlin`) is 2.2.10 — the serialization compiler plugin must track the same Kotlin version
  // it's compiling against, so it's now sourced from the same version.ref as everything else.
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  // BUG-34: was namespace "com.example" (the Android Studio template default, never renamed) with
  // applicationId "com.aistudio.glimmer.celebration" (leftover from AI Studio scaffolding) —
  // neither reflected the actual product. Both now live under one real, owned package.
  namespace = "com.glimmer.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.glimmer.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    // debug uses the default Android debug signing automatically — no custom config needed
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    // Required for java.time (LocalDate/MonthDay/etc.) on minSdk < 26.
    isCoreLibraryDesugaringEnabled = true
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }
}

// Paired with exportSchema = true on @Database (AppDatabase.kt): writes a versioned JSON schema
// to app/schemas/ on every build, which MigrationTestHelper needs to test a Migration against the
// database's real starting shape rather than just what the code claims it looked like.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.datastore.preferences)
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)

  "ksp"(libs.androidx.room.compiler)

  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
