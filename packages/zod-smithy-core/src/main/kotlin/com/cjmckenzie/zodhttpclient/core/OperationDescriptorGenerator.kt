package com.cjmckenzie.zodhttpclient.core

import com.cjmckenzie.zodhttpclient.analyzers.HttpBindingAnalyzer
import com.cjmckenzie.zodhttpclient.analyzers.OperationAnalyzer
import com.cjmckenzie.zodhttpclient.models.HttpBindingAnalysis
import com.cjmckenzie.zodhttpclient.models.OperationDescriptor
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.HttpTrait
import java.util.logging.Logger

class OperationDescriptorGenerator(
    private val operationAnalyzer: OperationAnalyzer = OperationAnalyzer(),
    private val httpBindingAnalyzer: HttpBindingAnalyzer = HttpBindingAnalyzer(),
    private val schemaGenerator: ZodSchemaGenerator = ZodSchemaGenerator.create(),
) {
    private val logger = Logger.getLogger(OperationDescriptorGenerator::class.java.name)

    fun generateForService(
        model: Model,
        service: ServiceShape,
    ): List<OperationDescriptor> {
        val operations = operationAnalyzer.getOperationsForService(model, service)
        logger.info("Found ${operations.size} operations to process")
        return operations.map { operation -> generateForOperation(model, operation) }
    }

    fun generateForOperation(
        model: Model,
        operation: OperationShape,
    ): OperationDescriptor {
        logger.info("Processing operation: ${operation.id.name}")

        val httpTrait = operation.getTrait(HttpTrait::class.java).orElse(null)
        val inputShape =
            operation.input
                .flatMap(model::getShape)
                .orElse(null) as? StructureShape
        val outputShape =
            operation.output
                .flatMap(model::getShape)
                .orElse(null) as? StructureShape

        val inputBindings =
            inputShape?.let { httpBindingAnalyzer.analyzeHttpBindings(model, it) }
                ?: emptyHttpBindings()
        val outputBindings = outputShape?.let { httpBindingAnalyzer.analyzeHttpBindings(model, it) }
        val inputSchema = schemaGenerator.generateInputSchema(model, operation, inputBindings)
        val outputSchema = outputShape?.let { schemaGenerator.generateOutputSchema(model, operation, it) }
        val errors = schemaGenerator.extractErrorShapes(model, operation)

        return OperationDescriptor(
            operationName = operation.id.name,
            methodName = operation.id.name.replaceFirstChar { it.lowercase() },
            httpMethod = httpTrait?.method ?: "GET",
            uri = httpTrait?.uri?.toString() ?: "/",
            successStatusCode = httpTrait?.code ?: 200,
            inputSchema = inputSchema,
            outputSchema = outputSchema,
            inputBindings = inputBindings,
            outputBindings = outputBindings,
            errors = errors,
        )
    }

    private fun emptyHttpBindings(): HttpBindingAnalysis =
        HttpBindingAnalysis(
            pathParameters = emptyMap(),
            queryParameters = emptyMap(),
            headerParameters = emptyMap(),
            bodyParameters = emptyMap(),
        )
}
