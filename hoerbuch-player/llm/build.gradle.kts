import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Native llm engine (built from source with CMake, see src/main/cpp/CMakeLists.txt).
android {
    namespace = "io.github.hexotronic.lauscher.llm"
    compileSdk = 35
    // On CI use the NDK that is already installed on the runner instead of downloading one.
    System.getenv("ANDROID_NDK_LATEST_HOME")?.let { ndkVersion = java.io.File(it).name }

    defaultConfig {
        minSdk = 26
        ndk {
            // Phones (arm64) and the CI emulator (x86_64).
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
