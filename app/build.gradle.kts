plugins {
    // Android 应用模块和 Kotlin Android 插件。
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // namespace 与 AndroidManifest/applicationId 对应当前包名。
    namespace = "com.scut.chudadi"
    compileSdk = 34

    defaultConfig {
        // 课程演示版应用标识和版本号。
        applicationId = "com.scut.chudadi"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 当前课程版本不启用混淆，便于调试和答辩排查。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // 与 Kotlin jvmTarget 保持一致。
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 基础 AndroidX、AppCompat、Material 和 Activity KTX 依赖。
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")

    // 本地 JVM 单元测试。
    testImplementation("junit:junit:4.13.2")
}
