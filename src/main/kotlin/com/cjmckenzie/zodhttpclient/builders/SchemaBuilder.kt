package com.cjmckenzie.zodhttpclient.builders

import com.cjmckenzie.zodhttpclient.types.ZodType
import com.cjmckenzie.zodhttpclient.types.ZodTypes

/**
 * Builds client-side request/response schemas.
 *
 * Input: flat validated object → .transform() decomposes into { path, headers, query, body, url, method }
 * Output: { body, headers, statusCode } → .transform() flattens into a single typed object
 */
class SchemaBuilder {
    /**
     * Build an input schema: flat Zod object + .transform() that decomposes into HTTP parts.
     */
    fun buildInputSchema(
        schemaName: String,
        allFields: Map<String, ZodType>,
        pathFields: Set<String>,
        queryFields: Map<String, String>,
        headerFields: Map<String, String>,
        bodyFields: Set<String>,
        httpMethod: String,
        uriTemplate: String,
    ): TypeScriptSchema {
        val flatSchema = ZodTypes.obj(allFields)

        val transformLines = mutableListOf<String>()

        // Spread all validated fields
        transformLines.add("  ...v,")

        // path
        if (pathFields.isNotEmpty()) {
            val pathEntries = pathFields.joinToString(", ") { "$it: v.$it" }
            transformLines.add("  path: { $pathEntries },")
        } else {
            transformLines.add("  path: {},")
        }

        // headers — conditional spread for optional fields
        if (headerFields.isNotEmpty()) {
            val headerEntries =
                headerFields.map { (fieldName, headerName) ->
                    "    ...(v.$fieldName !== undefined && { '$headerName': v.$fieldName })"
                }
            transformLines.add("  headers: {\n${headerEntries.joinToString(",\n")}\n  },")
        } else {
            transformLines.add("  headers: {},")
        }

        // query — conditional spread for optional fields
        if (queryFields.isNotEmpty()) {
            val queryEntries =
                queryFields.map { (fieldName, queryName) ->
                    "    ...(v.$fieldName !== undefined && { '$queryName': v.$fieldName })"
                }
            transformLines.add("  query: {\n${queryEntries.joinToString(",\n")}\n  },")
        } else {
            transformLines.add("  query: {},")
        }

        // body
        if (bodyFields.isNotEmpty()) {
            val bodyEntries = bodyFields.joinToString(", ") { "$it: v.$it" }
            transformLines.add("  body: { $bodyEntries },")
        } else {
            transformLines.add("  body: {},")
        }

        // url — build template literal from URI pattern
        val urlTemplate = buildUrlTemplate(uriTemplate)
        transformLines.add("  url: $urlTemplate,")

        // method
        transformLines.add("  method: '${httpMethod.uppercase()}' as const,")

        val schema = ZodTypes.transform(flatSchema, transformLines.joinToString("\n"))

        return TypeScriptSchema(
            name = schemaName,
            imports = setOf("import { z } from 'zod';"),
            schema = schema,
        )
    }

    /**
     * Build an output schema: { body, headers, statusCode } → .transform() flattens to single object.
     */
    fun buildOutputSchema(
        schemaName: String,
        bodyFields: Map<String, ZodType>,
        headerFields: Map<String, Pair<String, ZodType>>,
    ): TypeScriptSchema {
        // Build the raw input shape: { body, headers, statusCode }
        val rawFields = mutableMapOf<String, ZodType>()

        if (bodyFields.isNotEmpty()) {
            rawFields["body"] = ZodTypes.obj(bodyFields)
        } else {
            rawFields["body"] = ZodTypes.unknown()
        }

        if (headerFields.isNotEmpty()) {
            val headerZodFields =
                headerFields.map { (_, pair) ->
                    val (headerName, zodType) = pair
                    headerName to zodType
                }.toMap()
            rawFields["headers"] = ZodTypes.obj(headerZodFields).optional()
        }

        rawFields["statusCode"] = ZodTypes.number().optional()

        val rawSchema = ZodTypes.obj(rawFields)

        // Build transform that flattens body fields to top level and maps headers to field names
        val transformLines = mutableListOf<String>()

        // Spread body fields to top level
        if (bodyFields.isNotEmpty()) {
            transformLines.add("  ...v.body,")
        }

        // Map header names back to field names
        headerFields.forEach { (fieldName, pair) ->
            val (headerName, _) = pair
            val quotedHeader = if (headerName.needsQuoting()) "?.['$headerName']" else "?.$headerName"
            transformLines.add("  ...(v.headers$quotedHeader !== undefined && { $fieldName: v.headers$quotedHeader }),")
        }

        // Include statusCode
        transformLines.add("  ...(v.statusCode !== undefined && { statusCode: v.statusCode }),")

        val schema = ZodTypes.transform(rawSchema, transformLines.joinToString("\n"))

        return TypeScriptSchema(
            name = schemaName,
            imports = setOf("import { z } from 'zod';"),
            schema = schema,
        )
    }

    private fun buildUrlTemplate(uriTemplate: String): String {
        // Convert Smithy URI template "/items/{itemType}/{itemId}" to
        // template literal `/items/${encodeURIComponent(String(v.itemType))}/${encodeURIComponent(String(v.itemId))}`
        val result =
            uriTemplate.replace(Regex("\\{([^}]+)}")) { match ->
                val paramName = match.groupValues[1]
                "\${encodeURIComponent(String(v.$paramName))}"
            }
        return "`$result`"
    }

    private fun String.needsQuoting(): Boolean = !matches(Regex("^[a-zA-Z_$][a-zA-Z0-9_$]*$"))
}

/**
 * Represents a complete TypeScript schema with imports and exports
 */
data class TypeScriptSchema(
    val name: String,
    val imports: Set<String>,
    val schema: ZodType,
) {
    fun render(): String =
        buildString {
            appendLine(imports.joinToString("\n"))
            appendLine()
            appendLine("export const $name = ${schema.render()};")
            appendLine()
            val typeName = name.removeSuffix("Schema")
            append("export type $typeName = z.output<typeof $name>;")
        }
}

/**
 * Represents a client method for an operation
 */
data class ClientMethod(
    val operationName: String,
    val methodName: String,
    val inputSchemaName: String,
    val outputSchemaName: String?,
    val inputTypeName: String,
    val outputTypeName: String?,
    val errorNames: List<String> = emptyList(),
)
