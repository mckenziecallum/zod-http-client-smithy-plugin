package com.cjmckenzie.zodhttpclient

import com.cjmckenzie.zodhttpclient.builders.ClientMethod
import com.cjmckenzie.zodhttpclient.client.ClientFileGenerator
import com.cjmckenzie.zodhttpclient.core.OperationDescriptorGenerator
import com.cjmckenzie.zodhttpclient.models.OperationDescriptor
import com.google.auto.service.AutoService
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.build.SmithyBuildPlugin
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.utils.SmithyInternalApi
import java.util.logging.Logger

private const val PLUGIN_NAME = "zod-client"
private val SUPPORTED_CLIENTS = setOf("axios", "fetch")
private val DEFAULT_CLIENTS = setOf("axios")

@AutoService(SmithyBuildPlugin::class)
class ZodHttpClientSmithyPlugin : SmithyBuildPlugin {
    private val logger = Logger.getLogger(ZodHttpClientSmithyPlugin::class.java.name)
    private val descriptorGenerator = OperationDescriptorGenerator()
    private val fileGenerator = ClientFileGenerator()

    override fun getName(): String = PLUGIN_NAME

    @SmithyInternalApi
    override fun execute(context: PluginContext) {
        logger.info("Starting client-side Zod schema generation")

        runCatching {
            with(context) {
                val serviceId = settings.expectStringMember("service").value
                val service =
                    model.getShape(ShapeId.from(serviceId))
                        .orElse(null) as? ServiceShape
                        ?: throw IllegalArgumentException("Service '$serviceId' not found in model.")

                val clients = parseClientConfig(context)
                val descriptors = descriptorGenerator.generateForService(model, service)

                fileGenerator.generateUtilsFile(fileManifest)

                descriptors.forEach { descriptor ->
                    fileManifest.writeFile("${descriptor.operationName}Input.ts", descriptor.inputSchema.render())
                    descriptor.outputSchema?.let { outputSchema ->
                        fileManifest.writeFile("${descriptor.operationName}Output.ts", outputSchema.render())
                    }
                }

                val clientMethods = descriptors.map { it.toClientMethod() }
                val operationNames = descriptors.map { it.operationName }
                val outputOperationNames = descriptors.filter { it.outputSchema != null }.map { it.operationName }
                val allErrors = descriptors.flatMap { it.errors }

                // Deduplicate errors across operations
                val uniqueErrors = allErrors.distinctBy { it.name }

                if (uniqueErrors.isNotEmpty()) {
                    fileGenerator.generateErrorsFile(fileManifest, uniqueErrors)
                }

                val hasErrors = uniqueErrors.isNotEmpty()

                if (clientMethods.isNotEmpty()) {
                    if ("axios" in clients) {
                        fileGenerator.generateAxiosClientFile(fileManifest, clientMethods, hasErrors)
                    }
                    if ("fetch" in clients) {
                        fileGenerator.generateFetchClientFile(fileManifest, clientMethods, hasErrors)
                    }
                    fileGenerator.generateIndexFile(
                        fileManifest,
                        operationNames,
                        outputOperationNames,
                        clients,
                        uniqueErrors.map { it.name },
                    )
                }
            }
            logger.info("Client-side Zod schema generation completed successfully")
        }.onFailure { exception ->
            logger.severe("Error during Zod schema generation: ${exception.message}")
            throw exception
        }
    }

    private fun parseClientConfig(context: PluginContext): Set<String> {
        val clientNode = context.settings.getMember("client").orElse(null) ?: return DEFAULT_CLIENTS

        if (clientNode.isArrayNode) {
            val clients = clientNode.expectArrayNode().elements.map { it.expectStringNode().value }.toSet()
            val invalid = clients - SUPPORTED_CLIENTS
            if (invalid.isNotEmpty()) {
                throw IllegalArgumentException("Unsupported client types: $invalid. Supported: $SUPPORTED_CLIENTS")
            }
            return if (clients.isEmpty()) DEFAULT_CLIENTS else clients
        }

        if (clientNode.isStringNode) {
            val client = clientNode.expectStringNode().value
            if (client !in SUPPORTED_CLIENTS) {
                throw IllegalArgumentException("Unsupported client type: '$client'. Supported: $SUPPORTED_CLIENTS")
            }
            return setOf(client)
        }

        return DEFAULT_CLIENTS
    }

    private fun OperationDescriptor.toClientMethod(): ClientMethod =
        ClientMethod(
            operationName = operationName,
            methodName = methodName,
            inputSchemaName = "${operationName}Input",
            outputSchemaName = outputSchema?.let { "${operationName}Output" },
            inputTypeName = "${operationName}Input",
            outputTypeName = outputSchema?.let { "${operationName}Output" },
            errorNames = errors.map { it.name },
        )
}
