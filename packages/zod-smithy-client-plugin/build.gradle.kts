dependencies {
    api(project(":zod-smithy-core"))
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
