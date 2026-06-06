package com.cjmckenzie.examples.hono

import com.cjmckenzie.zodhttpclient.ZodHttpClientSmithyPlugin
import com.cjmckenzie.zodhttpclient.hono.ZodHonoSmithyPlugin
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.loader.ModelAssembler
import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.Node
import java.nio.file.Path
import kotlin.io.path.createDirectories

fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: GenerateHonoExample <model> <honoOutputDir> <clientOutputDir>" }

    val modelPath = Path.of(args[0])
    val honoOutputDir = Path.of(args[1])
    val clientOutputDir = Path.of(args[2])
    honoOutputDir.createDirectories()
    clientOutputDir.createDirectories()

    val model =
        ModelAssembler()
            .addImport(modelPath)
            .assemble()
            .unwrap()

    val settings =
        Node.objectNode()
            .withMember("service", Node.from("com.example.hono#ExampleService"))

    val honoContext =
        PluginContext.builder()
            .model(model)
            .fileManifest(FileManifest.create(honoOutputDir))
            .settings(settings)
            .build()
    val clientSettings =
        settings
            .withMember("client", ArrayNode.fromStrings(listOf("fetch")))
    val clientContext =
        PluginContext.builder()
            .model(model)
            .fileManifest(FileManifest.create(clientOutputDir))
            .settings(clientSettings)
            .build()

    ZodHonoSmithyPlugin().execute(honoContext)
    ZodHttpClientSmithyPlugin().execute(clientContext)
}
