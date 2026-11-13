pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipe Extractor (search and audio streams of YouTube, PeerTube, SoundCloud, Bandcamp,
        // media.ccc.de) is only published there.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.TeamNewPipe") }
        }
    }
}

rootProject.name = "Lauscher"
include(":app", ":llm", ":llm-fast", ":whisper", ":whisper-fast", ":mp3")
