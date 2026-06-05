pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "zod-http-client-smithy-plugin"

include(":zod-smithy-core")
include(":zod-smithy-client-plugin")
include(":zod-smithy-hono-plugin")

project(":zod-smithy-core").projectDir = file("packages/zod-smithy-core")
project(":zod-smithy-client-plugin").projectDir = file("packages/zod-smithy-client-plugin")
project(":zod-smithy-hono-plugin").projectDir = file("packages/zod-smithy-hono-plugin")
