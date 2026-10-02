plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":zod-smithy-hono-plugin"))
    implementation(project(":zod-smithy-client-plugin"))
    implementation("aws.smithy.kotlin:codegen:1.7.4")
    runtimeOnly("aws.smithy.kotlin:aws-codegen:1.7.4")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    listOf(
        "runtime-core", "smithy-client", "http-client", "http-client-engine-default",
        "telemetry-api", "telemetry-defaults", "aws-protocol-core", "aws-signing-common",
        "serde", "serde-json", "aws-json-protocols",
    ).forEach { testImplementation("aws.smithy.kotlin:$it:1.7.4") }
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

application {
    mainClass.set("com.cjmckenzie.examples.hono.GenerateHonoExampleKt")
}

val generatedHonoDir = layout.buildDirectory.dir("generated/hono")
val generatedClientDir = layout.buildDirectory.dir("generated/client")
val generatedKotlinDir = layout.buildDirectory.dir("generated/kotlin")
val nodeModulesDir = layout.projectDirectory.dir("node_modules")
val pnpmHome = providers.environmentVariable("PNPM_HOME").orElse("${System.getProperty("user.home")}/Library/pnpm")
val pnpmPath = pnpmHome.map { "$it:${System.getenv("PATH")}" }

tasks.register<JavaExec>("generateExample") {
    group = "verification"
    description = "Generate the Hono router, fetch client, and Smithy Kotlin client from the model."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args(
        layout.projectDirectory.file("model/example-service.smithy").asFile.absolutePath,
        generatedHonoDir.get().asFile.absolutePath,
        generatedClientDir.get().asFile.absolutePath,
        generatedKotlinDir.get().asFile.absolutePath,
    )
    inputs.file("model/example-service.smithy")
    outputs.dir(generatedHonoDir)
    outputs.dir(generatedClientDir)
    outputs.dir(generatedKotlinDir)
}

tasks.register<Exec>("pnpmInstall") {
    group = "verification"
    description = "Install the Hono example TypeScript dependencies."
    commandLine("pnpm", "install")
    environment("PATH", pnpmPath.get())
    inputs.file("package.json")
    inputs.file("pnpm-lock.yaml")
    outputs.dir(nodeModulesDir)
}

tasks.register<Exec>("typecheck") {
    group = "verification"
    description = "Typecheck the generated Hono example."
    dependsOn("generateExample", "pnpmInstall")
    commandLine("pnpm", "run", "typecheck")
    environment("PATH", pnpmPath.get())
    inputs.dir("src")
    inputs.dir(generatedHonoDir)
    inputs.dir(generatedClientDir)
    inputs.file("tsconfig.json")
}

tasks.register<Exec>("smokeTest") {
    group = "verification"
    description = "Run the Hono example smoke test."
    dependsOn("generateExample", "pnpmInstall")
    commandLine("pnpm", "test")
    environment("PATH", pnpmPath.get())
    inputs.dir("src")
    inputs.dir(generatedHonoDir)
    inputs.dir(generatedClientDir)
}

tasks.register<Exec>("fullStackTest") {
    group = "verification"
    description = "Run a generated Hono server and call it with the generated fetch client."
    dependsOn("generateExample", "pnpmInstall")
    commandLine("pnpm", "run", "test:full-stack")
    environment("PATH", pnpmPath.get())
    inputs.dir("src")
    inputs.dir(generatedHonoDir)
    inputs.dir(generatedClientDir)
}

tasks.named("check") {
    dependsOn("typecheck", "smokeTest", "fullStackTest")
}

kotlin.sourceSets.test {
    kotlin.srcDir(generatedKotlinDir.map { it.dir("src/main/kotlin") })
    languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
}

tasks.named("compileTestKotlin") {
    dependsOn("generateExample")
}

tasks.test {
    useJUnitPlatform()
    dependsOn("generateExample", "pnpmInstall")
    inputs.files(fileTree("src") { include("**/*.ts") })
    inputs.dir(generatedHonoDir)
    workingDir(projectDir)
}
