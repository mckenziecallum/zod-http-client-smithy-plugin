package com.cjmckenzie.zodhttpclient.core

import com.cjmckenzie.zodhttpclient.builders.SchemaBuilder
import com.cjmckenzie.zodhttpclient.builders.TypeScriptSchema
import com.cjmckenzie.zodhttpclient.mappers.TypeSafeConstraintMapper
import com.cjmckenzie.zodhttpclient.mappers.TypeSafeMapper
import com.cjmckenzie.zodhttpclient.models.ErrorShapeInfo
import com.cjmckenzie.zodhttpclient.models.HttpBindingAnalysis
import com.cjmckenzie.zodhttpclient.types.ZodType
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.ErrorTrait
import software.amazon.smithy.model.traits.HttpErrorTrait
import software.amazon.smithy.model.traits.HttpHeaderTrait
import software.amazon.smithy.model.traits.HttpTrait

/**
 * Generates client-side request/response object schemas.
 */
class TypeSafeSchemaGenerator(
    private val typeMapper: TypeSafeMapper,
    private val constraintMapper: TypeSafeConstraintMapper,
    private val schemaBuilder: SchemaBuilder = SchemaBuilder(),
) {
    fun generateInputSchema(
        model: Model,
        operation: OperationShape,
        bindings: HttpBindingAnalysis,
    ): TypeScriptSchema {
        val schemaName = "${operation.id.name}Input"
        val httpTrait = operation.getTrait(HttpTrait::class.java).orElse(null)
        val httpMethod = httpTrait?.method ?: "GET"
        val uriTemplate = httpTrait?.uri?.toString() ?: "/"

        // Build flat field map — all parameters with native types
        val allFields = linkedMapOf<String, ZodType>()
        val pathFieldNames = mutableSetOf<String>()
        val queryFieldMap = mutableMapOf<String, String>() // fieldName -> queryParamName
        val headerFieldMap = mutableMapOf<String, String>() // fieldName -> headerName
        val bodyFieldNames = mutableSetOf<String>()

        bindings.pathParameters.forEach { (name, paramInfo) ->
            allFields[name] = buildFieldType(model, paramInfo, forceRequired = true)
            pathFieldNames.add(name)
        }

        bindings.queryParameters.forEach { (name, paramInfo) ->
            allFields[name] = buildFieldType(model, paramInfo)
            queryFieldMap[name] = name // query param name matches field name
        }

        bindings.headerParameters.forEach { (headerName, paramInfo) ->
            // headerName is the HTTP header name (e.g., "X-Request-ID")
            // We need the field name from the member
            val fieldName = findFieldNameForHeader(bindings, headerName)
            allFields[fieldName] = buildFieldType(model, paramInfo)
            headerFieldMap[fieldName] = headerName
        }

        bindings.bodyParameters.forEach { (name, paramInfo) ->
            allFields[name] = buildFieldType(model, paramInfo)
            bodyFieldNames.add(name)
        }

        return schemaBuilder.buildInputSchema(
            schemaName = schemaName,
            allFields = allFields,
            pathFields = pathFieldNames,
            queryFields = queryFieldMap,
            headerFields = headerFieldMap,
            bodyFields = bodyFieldNames,
            httpMethod = httpMethod,
            uriTemplate = uriTemplate,
        )
    }

    fun generateOutputSchema(
        model: Model,
        operation: OperationShape,
        outputShape: StructureShape,
    ): TypeScriptSchema {
        val schemaName = "${operation.id.name}Output"

        val bodyFields = linkedMapOf<String, ZodType>()
        val headerFields = linkedMapOf<String, Pair<String, ZodType>>() // fieldName -> (headerName, zodType)

        outputShape.allMembers.forEach { (memberName, member) ->
            val targetShape = model.getShape(member.target).orElse(null) ?: return@forEach
            val baseType = typeMapper.mapShapeToZodType(model, targetShape)
            val constrainedType = constraintMapper.applyFieldLevelConstraints(baseType, member, targetShape)

            val fieldType = if (member.isRequired) constrainedType else constrainedType.optional()

            if (member.hasTrait(HttpHeaderTrait::class.java)) {
                val headerName = member.getTrait(HttpHeaderTrait::class.java).get().value
                headerFields[memberName] = headerName to fieldType
            } else {
                bodyFields[memberName] = fieldType
            }
        }

        return schemaBuilder.buildOutputSchema(
            schemaName = schemaName,
            bodyFields = bodyFields,
            headerFields = headerFields,
        )
    }

    private fun buildFieldType(
        model: Model,
        paramInfo: com.cjmckenzie.zodhttpclient.models.ParameterInfo,
        forceRequired: Boolean = false,
    ): ZodType {
        val baseType = typeMapper.mapShapeToZodType(model, paramInfo.shape)
        val constrainedType =
            paramInfo.member?.let { member ->
                constraintMapper.applyFieldLevelConstraints(baseType, member, paramInfo.shape)
            } ?: baseType

        val isRequired = forceRequired || paramInfo.isRequired

        return when {
            isRequired -> constrainedType
            paramInfo.member?.hasTrait(software.amazon.smithy.model.traits.DefaultTrait::class.java) == true -> {
                val defaultTrait = paramInfo.member.getTrait(software.amazon.smithy.model.traits.DefaultTrait::class.java).get()
                val defaultValue = constraintMapper.formatDefaultValue(defaultTrait, paramInfo.shape)
                constrainedType.default(defaultValue)
            }
            else -> constrainedType.optional()
        }
    }

    /**
     * Find the original field name for a header binding.
     * The HttpBindingAnalyzer stores headers keyed by header name, but we need the member name.
     */
    private fun findFieldNameForHeader(
        bindings: HttpBindingAnalysis,
        headerName: String,
    ): String {
        val paramInfo = bindings.headerParameters[headerName] ?: return headerName
        val member = paramInfo.member ?: return headerName
        return member.memberName
    }

    companion object {
        fun create(): TypeSafeSchemaGenerator {
            val constraintMapper = TypeSafeConstraintMapper()
            val typeMapper = TypeSafeMapper(constraintMapper)
            return TypeSafeSchemaGenerator(typeMapper, constraintMapper)
        }
    }

    /**
     * Extract error shapes for an operation, returning their names, HTTP status codes, and Zod field schemas.
     */
    fun extractErrorShapes(
        model: Model,
        operation: OperationShape,
    ): List<ErrorShapeInfo> {
        return operation.errors.mapNotNull { errorId ->
            val errorShape = model.getShape(errorId).orElse(null) as? StructureShape ?: return@mapNotNull null
            val httpStatus =
                errorShape.getTrait(HttpErrorTrait::class.java)
                    .map { it.code }
                    .orElse(
                        if (errorShape.getTrait(ErrorTrait::class.java).map { it.value }.orElse("") == "client") 400 else 500,
                    )

            val fields = linkedMapOf<String, ZodType>()
            errorShape.allMembers.forEach { (memberName, member) ->
                val targetShape = model.getShape(member.target).orElse(null) ?: return@forEach
                val baseType = typeMapper.mapShapeToZodType(model, targetShape)
                val constrained = constraintMapper.applyFieldLevelConstraints(baseType, member, targetShape)
                fields[memberName] = if (member.isRequired) constrained else constrained.optional()
            }

            ErrorShapeInfo(
                name = errorShape.id.name,
                httpStatusCode = httpStatus,
                fields = fields,
            )
        }.distinctBy { it.name }
    }
}
