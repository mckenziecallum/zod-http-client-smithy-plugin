plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":zod-smithy-hono-plugin"))
}

application {
    mainClass.set("com.cjmckenzie.examples.hono.GenerateHonoExampleKt")
}

val generatedDir = layout.buildDirectory.dir("generated/hono")
val nodeModulesDir = layout.projectDirectory.dir("node_modules")
val pnpmHome = providers.environmentVariable("PNPM_HOME").orElse("${System.getProperty("user.home")}/Library/pnpm")
val pnpmPath = pnpmHome.map { "$it:${System.getenv("PATH")}" }

tasks.register<JavaExec>("generateHono") {
    group = "verification"
    description = "Generate the Hono example router from the Smithy model."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args(
        layout.projectDirectory.file("model/example-service.smithy").asFile.absolutePath,
        generatedDir.get().asFile.absolutePath,
    )
    outputs.dir(generatedDir)
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
    dependsOn("generateHono", "pnpmInstall")
    commandLine("pnpm", "run", "typecheck")
    environment("PATH", pnpmPath.get())
    inputs.dir("src")
    inputs.dir(generatedDir)
    inputs.file("tsconfig.json")
}

tasks.register<Exec>("smokeTest") {
    group = "verification"
    description = "Run the Hono example smoke test."
    dependsOn("generateHono", "pnpmInstall")
    commandLine("pnpm", "test")
    environment("PATH", pnpmPath.get())
    inputs.dir("src")
    inputs.dir(generatedDir)
}

tasks.named("check") {
    dependsOn("typecheck", "smokeTest")
}
