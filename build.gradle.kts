plugins {
    `java-library`
    `maven-publish`
    checkstyle
    jacoco
    kotlin("jvm") version "2.4.0"
    id("com.github.spotbugs") version "6.0.26"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

group = "com.cjmckenzie"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
}

val smithyVersion = "1.51.0"

dependencies {
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")

    api("software.amazon.smithy:smithy-build:$smithyVersion")
    api("software.amazon.smithy:smithy-codegen-core:$smithyVersion")
    api("software.amazon.smithy:smithy-model:$smithyVersion")
    api("software.amazon.smithy:smithy-utils:$smithyVersion")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
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

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    ignoreFailures.set(false)
}

spotbugs {
    ignoreFailures.set(true)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
