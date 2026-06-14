pluginManagement {
    repositories {
        // Android Gradle 插件、Kotlin 插件和第三方插件解析仓库。
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 依赖仓库集中声明在 settings，避免子项目自行添加仓库导致不可复现。
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Android Studio 中显示的根工程名。
rootProject.name = "ChuDaDiApp"
// 当前只有一个 Android 应用模块。
include(":app")
