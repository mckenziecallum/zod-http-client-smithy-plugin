plugins {
    kotlin("jvm") version "2.4.0" apply false
    id("com.github.spotbugs") version "6.0.26" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2" apply false
}

allprojects {
    group = "com.cjmckenzie"
    version = "1.0.0"
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "checkstyle")
    apply(plugin = "jacoco")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "com.github.spotbugs")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withJavadocJar()
        withSourcesJar()
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        }
        ignoreFailures.set(false)
    }

    extensions.configure<com.github.spotbugs.snom.SpotBugsExtension> {
        ignoreFailures.set(true)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/mckenziecallum/zod-http-client-smithy-plugin")
                credentials {
                    username =
                        providers.gradleProperty("gpr.user")
                            .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                            .orNull
                    password =
                        providers.gradleProperty("gpr.key")
                            .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                            .orNull
                }
            }
        }
    }
}
