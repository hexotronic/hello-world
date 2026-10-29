plugins {
    id("com.android.library")
}

// Same native sources as :llm, compiled for newer CPUs (see LAUSCHER_FAST in the CMakeLists).
android {
    namespace = "io.github.hexotronic.lauscher.llmfast"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static", "-DLAUSCHER_FAST=ON")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../llm/src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
