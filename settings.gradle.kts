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
        ivy {
            url = uri("https://raw.githubusercontent.com/Nothing-Developer-Programme/Glyph-Developer-Kit/main/sdk")
            patternLayout {
                artifact("[artifact]-[revision].[ext]")
            }
            metadataSources {
                artifact()
            }
        }
    }
}

rootProject.name = "Nothing Lyric Widget"
include(":app")
