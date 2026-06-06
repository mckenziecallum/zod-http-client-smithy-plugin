plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":zod-smithy-hono-plugin"))
    implementation(project(":zod-smithy-client-plugin"))
}

application {
    mainClass.set("com.cjmckenzie.examples.hono.GenerateHonoExampleKt")
}

val generatedHonoDir = layout.buildDirectory.dir("generated/hono")
val generatedClientDir = layout.buildDirectory.dir("generated/client")
val nodeModulesDir = layout.projectDirectory.dir("node_modules")
val pnpmHome = providers.environmentVariable("PNPM_HOME").orElse("${System.getProperty("user.home")}/Library/pnpm")
val pnpmPath = pnpmHome.map { "$it:${System.getenv("PATH")}" }

tasks.register<JavaExec>("generateExample") {
    group = "verification"
    description = "Generate the Hono example router and fetch client from the Smithy model."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args(
        layout.projectDirectory.file("model/example-service.smithy").asFile.absolutePath,
        generatedHonoDir.get().asFile.absolutePath,
        generatedClientDir.get().asFile.absolutePath,
    )
    outputs.dir(generatedHonoDir)
    outputs.dir(generatedClientDir)
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
