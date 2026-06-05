package com.cjmckenzie.zodhttpclient

import com.cjmckenzie.zodhttpclient.analyzers.HttpBindingAnalyzer
import com.cjmckenzie.zodhttpclient.analyzers.OperationAnalyzer
import com.cjmckenzie.zodhttpclient.builders.ClientMethod
import com.cjmckenzie.zodhttpclient.core.FileGenerator
import com.cjmckenzie.zodhttpclient.core.TypeSafeSchemaGenerator
import com.cjmckenzie.zodhttpclient.models.ErrorShapeInfo
import com.google.auto.service.AutoService
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.build.SmithyBuildPlugin
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.utils.SmithyInternalApi
import java.util.logging.Logger

private const val PLUGIN_NAME = "zod-schema"
private val SUPPORTED_CLIENTS = setOf("axios", "fetch")
private val DEFAULT_CLIENTS = setOf("axios")

@AutoService(SmithyBuildPlugin::class)
class ZodHttpClientSmithyPlugin : SmithyBuildPlugin {
    private val logger = Logger.getLogger(ZodHttpClientSmithyPlugin::class.java.name)
    private val operationAnalyzer = OperationAnalyzer()
    private val httpBindingAnalyzer = HttpBindingAnalyzer()
    private val schemaGenerator = TypeSafeSchemaGenerator.create()
    private val fileGenerator = FileGenerator()

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
                val operations = operationAnalyzer.getOperationsForService(model, service)
                logger.info("Found ${operations.size} operations to process")

                fileGenerator.generateUtilsFile(fileManifest)

                val clientMethods = mutableListOf<ClientMethod>()
                val operationNames = mutableListOf<String>()
                val outputOperationNames = mutableListOf<String>()
                val allErrors = mutableListOf<ErrorShapeInfo>()

                operations.forEach { operation ->
                    val result = processOperation(model, operation, fileManifest)
                    if (result != null) {
                        clientMethods.add(result.first)
                        operationNames.add(operation.id.name)
                        if (result.first.outputSchemaName != null) {
                            outputOperationNames.add(operation.id.name)
                        }
                        allErrors.addAll(result.second)
                    }
                }

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

    private fun processOperation(
        model: Model,
        operation: OperationShape,
        fileManifest: FileManifest,
    ): Pair<ClientMethod, List<ErrorShapeInfo>>? {
        logger.info("Processing operation: ${operation.id.name}")

        val inputShape =
            operation.input
                .flatMap(model::getShape)
                .orElse(null) as? StructureShape

        if (inputShape == null) {
            logger.warning("Operation ${operation.id.name} has no input shape, skipping")
            return null
        }

        val httpBindings = httpBindingAnalyzer.analyzeHttpBindings(model, inputShape)
        val inputSchema = schemaGenerator.generateInputSchema(model, operation, httpBindings)
        fileGenerator.writeInputSchema(fileManifest, operation.id.name, inputSchema.render())

        val outputShape =
            operation.output
                .flatMap(model::getShape)
                .orElse(null) as? StructureShape

        var outputSchemaName: String? = null
        var outputTypeName: String? = null

        if (outputShape != null) {
            val outputSchema = schemaGenerator.generateOutputSchema(model, operation, outputShape)
            fileGenerator.writeOutputSchema(fileManifest, operation.id.name, outputSchema.render())
            outputSchemaName = "${operation.id.name}Output"
            outputTypeName = "${operation.id.name}Output"
        }

        val errors = schemaGenerator.extractErrorShapes(model, operation)
        val methodName = operation.id.name.replaceFirstChar { it.lowercase() }

        val clientMethod =
            ClientMethod(
                operationName = operation.id.name,
                methodName = methodName,
                inputSchemaName = "${operation.id.name}Input",
                outputSchemaName = outputSchemaName,
                inputTypeName = "${operation.id.name}Input",
                outputTypeName = outputTypeName,
                errorNames = errors.map { it.name },
            )

        return clientMethod to errors
    }
}
