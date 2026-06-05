package com.cjmckenzie.examples.hono

import com.cjmckenzie.zodhttpclient.hono.ZodHonoSmithyPlugin
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.loader.ModelAssembler
import software.amazon.smithy.model.node.Node
import java.nio.file.Path
import kotlin.io.path.createDirectories

fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: GenerateHonoExample <model> <outputDir>" }

    val modelPath = Path.of(args[0])
    val outputDir = Path.of(args[1])
    outputDir.createDirectories()

    val model =
        ModelAssembler()
            .addImport(modelPath)
            .assemble()
            .unwrap()

    val settings =
        Node.objectNode()
            .withMember("service", Node.from("com.example.hono#ExampleService"))

    val context =
        PluginContext.builder()
            .model(model)
            .fileManifest(FileManifest.create(outputDir))
            .settings(settings)
            .build()

    ZodHonoSmithyPlugin().execute(context)
}
