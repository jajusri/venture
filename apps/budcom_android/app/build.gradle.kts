plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.budcom.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.budcom.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.1.1-continuity.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Retrofit requires a syntactically valid construction-time origin even though every
        // legacy request is rewritten by DynamicBaseUrlInterceptor. This value is plumbing only:
        // it is never a configured customer endpoint and must never be displayed or persisted.
        buildConfigField("String", "CONNECTOR_BOOTSTRAP_BASE_URL", "\"https://localhost/\"")
        // Customer releases start explicitly unconfigured. Debug overrides this with the Android
        // emulator host alias so the local developer loop remains convenient.
        buildConfigField("String", "CONNECTOR_DEFAULT_BASE_URL", "\"\"")
        buildConfigField("String", "APP_NAME", "\"BudCom\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            buildConfigField("String", "CONNECTOR_DEFAULT_BASE_URL", "\"http://10.0.2.2:8080/\"")
            buildConfigField("boolean", "NETWORK_LOGGING_ENABLED", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("boolean", "NETWORK_LOGGING_ENABLED", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        animationsDisabled = true
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        // Room-exported schema JSON, required by androidx.room:room-testing's
        // MigrationTestHelper to construct a real historical-version database for migration
        // tests (see AppDatabaseMigrationTest).
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.timber)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
