package com.cjmckenzie.zodhttpclient.core

import com.cjmckenzie.zodhttpclient.builders.ClientMethod
import com.cjmckenzie.zodhttpclient.models.ErrorShapeInfo
import com.cjmckenzie.zodhttpclient.types.ZodTypes
import software.amazon.smithy.build.FileManifest
import java.util.logging.Logger

/**
 * Handles file generation for the client-side Zod schema plugin.
 */
class FileGenerator {
    private val logger = Logger.getLogger(FileGenerator::class.java.name)

    fun generateUtilsFile(fileManifest: FileManifest) {
        val utilsContent = loadResourceTemplate("templates/utils.ts")
        fileManifest.writeFile("utils.ts", utilsContent)
        logger.info("Generated utils.ts")
    }

    fun writeInputSchema(
        fileManifest: FileManifest,
        operationName: String,
        schemaContent: String,
    ) {
        val fileName = "${operationName}Input.ts"
        fileManifest.writeFile(fileName, schemaContent)
        logger.info("Generated input schema: $fileName")
    }

    fun writeOutputSchema(
        fileManifest: FileManifest,
        operationName: String,
        schemaContent: String,
    ) {
        val fileName = "${operationName}Output.ts"
        fileManifest.writeFile(fileName, schemaContent)
        logger.info("Generated output schema: $fileName")
    }

    /**
     * Generate errors.ts — Zod schemas, typed exception classes, and a parseServiceError dispatcher.
     */
    fun generateErrorsFile(
        fileManifest: FileManifest,
        allErrors: List<ErrorShapeInfo>,
    ) {
        if (allErrors.isEmpty()) return

        val content =
            buildString {
                appendLine("import { z } from 'zod';")
                appendLine("import { ServiceError } from './utils';")
                appendLine()

                // Generate a Zod schema + class per error shape
                allErrors.forEach { error ->
                    val schemaFields = ZodTypes.obj(error.fields)
                    appendLine("export const ${error.name}Schema = ${schemaFields.render()};")
                    appendLine()
                    appendLine("export class ${error.name} extends ServiceError {")
                    appendLine("  readonly _kind = '${error.name}' as const;")

                    // Expose each field as a typed property, skip fields inherited from Error
                    val extraFields = error.fields.keys.filter { it != "message" }
                    extraFields.forEach { fieldName ->
                        appendLine("  readonly $fieldName?: unknown;")
                    }

                    appendLine("  constructor(body: z.infer<typeof ${error.name}Schema>, statusCode: number, operationName: string) {")
                    appendLine("    super({")
                    appendLine("      message: (body as any).message ?? '${error.name}',")
                    appendLine("      statusCode,")
                    appendLine("      body,")
                    appendLine("      operationName,")
                    appendLine("      _kind: '${error.name}',")
                    appendLine("    });")

                    extraFields.forEach { fieldName ->
                        appendLine("    this.$fieldName = body.$fieldName;")
                    }

                    appendLine("  }")
                    appendLine("}")
                    appendLine()
                }

                // Generate parseServiceError dispatcher
                appendLine("export function parseServiceError(operationName: string, statusCode: number, body: unknown): ServiceError {")
                allErrors.forEach { error ->
                    appendLine("  {")
                    appendLine("    const parsed = ${error.name}Schema.safeParse(body);")
                    appendLine("    if (parsed.success && statusCode === ${error.httpStatusCode}) {")
                    appendLine("      return new ${error.name}(parsed.data, statusCode, operationName);")
                    appendLine("    }")
                    appendLine("  }")
                }
                appendLine("  return new ServiceError({")
                appendLine(
                    "    message: typeof body === 'object' && body !== null && 'message' in body " +
                        "? String((body as any).message) : 'Unknown error',",
                )
                appendLine("    statusCode,")
                appendLine("    body,")
                appendLine("    operationName,")
                appendLine("    _kind: 'UnknownError',")
                appendLine("  });")
                appendLine("}")
            }

        fileManifest.writeFile("errors.ts", content)
        logger.info("Generated errors.ts with ${allErrors.size} error types")
    }

    fun generateAxiosClientFile(
        fileManifest: FileManifest,
        methods: List<ClientMethod>,
        hasErrors: Boolean,
    ) {
        val content =
            buildString {
                appendLine("import type { AxiosInstance, AxiosError } from 'axios';")
                appendLine("import { z } from 'zod';")
                appendLine("import { fromAxios } from './utils';")
                if (hasErrors) {
                    appendLine("import { parseServiceError } from './errors';")
                }

                methods.forEach { method ->
                    appendLine("import { ${method.inputSchemaName} } from './${method.inputSchemaName}';")
                    if (method.outputSchemaName != null) {
                        appendLine("import { ${method.outputSchemaName} } from './${method.outputSchemaName}';")
                    }
                }

                appendLine()
                appendLine("export function createAxiosClient(instance: AxiosInstance) {")
                appendLine("  return {")

                methods.forEach { method ->
                    val inputType = "z.input<typeof ${method.inputSchemaName}>"
                    appendLine("    async ${method.methodName}(input: $inputType) {")
                    appendLine("      const req = ${method.inputSchemaName}.parse(input);")
                    appendLine("      try {")
                    appendLine("        const response = await instance.request({")
                    appendLine("          method: req.method,")
                    appendLine("          url: req.url,")
                    appendLine("          headers: req.headers,")
                    appendLine("          params: req.query,")
                    appendLine("          data: req.body,")
                    appendLine("        });")
                    if (method.outputSchemaName != null) {
                        appendLine("        return ${method.outputSchemaName}.parse(fromAxios(response));")
                    } else {
                        appendLine("        return response.data;")
                    }
                    appendLine("      } catch (e: any) {")
                    if (hasErrors) {
                        appendLine("        if (e?.response) {")
                        appendLine("          throw parseServiceError('${method.operationName}', e.response.status, e.response.data);")
                        appendLine("        }")
                    }
                    appendLine("        throw e;")
                    appendLine("      }")
                    appendLine("    },")
                }

                appendLine("  };")
                appendLine("}")
            }

        fileManifest.writeFile("axios-client.ts", content)
        logger.info("Generated axios-client.ts with ${methods.size} methods")
    }

    fun generateFetchClientFile(
        fileManifest: FileManifest,
        methods: List<ClientMethod>,
        hasErrors: Boolean,
    ) {
        val content =
            buildString {
                appendLine("import { z } from 'zod';")
                appendLine("import { fromFetch } from './utils';")
                if (hasErrors) {
                    appendLine("import { parseServiceError } from './errors';")
                }

                methods.forEach { method ->
                    appendLine("import { ${method.inputSchemaName} } from './${method.inputSchemaName}';")
                    if (method.outputSchemaName != null) {
                        appendLine("import { ${method.outputSchemaName} } from './${method.outputSchemaName}';")
                    }
                }

                appendLine()
                appendLine("export function createFetchClient(baseUrl: string, defaultInit?: RequestInit) {")
                appendLine("  return {")

                methods.forEach { method ->
                    val inputType = "z.input<typeof ${method.inputSchemaName}>"
                    appendLine("    async ${method.methodName}(input: $inputType, init?: RequestInit) {")
                    appendLine("      const req = ${method.inputSchemaName}.parse(input);")
                    appendLine("      const url = new URL(req.url, baseUrl);")
                    appendLine("      Object.entries(req.query).forEach(([k, v]) => {")
                    appendLine("        if (v !== undefined) url.searchParams.set(k, String(v));")
                    appendLine("      });")
                    appendLine("      const response = await fetch(url, {")
                    appendLine("        ...defaultInit,")
                    appendLine("        ...init,")
                    appendLine("        method: req.method,")
                    appendLine("        headers: { ...defaultInit?.headers, ...init?.headers, ...req.headers },")
                    appendLine("        ...(req.method !== 'GET' && req.method !== 'HEAD' && Object.keys(req.body).length > 0")
                    appendLine("          ? { body: JSON.stringify(req.body) }")
                    appendLine("          : {}),")
                    appendLine("      });")
                    if (hasErrors) {
                        appendLine("      if (!response.ok) {")
                        appendLine("        const errorBody = await response.json().catch(() => ({}));")
                        appendLine("        throw parseServiceError('${method.operationName}', response.status, errorBody);")
                        appendLine("      }")
                    }
                    if (method.outputSchemaName != null) {
                        appendLine("      return ${method.outputSchemaName}.parse(await fromFetch(response));")
                    } else {
                        appendLine("      return response.json();")
                    }
                    appendLine("    },")
                }

                appendLine("  };")
                appendLine("}")
            }

        fileManifest.writeFile("fetch-client.ts", content)
        logger.info("Generated fetch-client.ts with ${methods.size} methods")
    }

    fun generateIndexFile(
        fileManifest: FileManifest,
        operationNames: List<String>,
        outputOperationNames: List<String>,
        clients: Set<String>,
        errorNames: List<String>,
    ) {
        val content =
            buildString {
                operationNames.forEach { name ->
                    appendLine("export { ${name}Input } from './${name}Input';")
                    appendLine("export type { ${name}Input as ${name}InputType } from './${name}Input';")
                }
                outputOperationNames.forEach { name ->
                    appendLine("export { ${name}Output } from './${name}Output';")
                    appendLine("export type { ${name}Output as ${name}OutputType } from './${name}Output';")
                }
                if (errorNames.isNotEmpty()) {
                    val errorExports = errorNames.joinToString(", ") { it }
                    val schemaExports = errorNames.joinToString(", ") { "${it}Schema" }
                    appendLine("export { $errorExports, $schemaExports, parseServiceError } from './errors';")
                }
                appendLine("export { ServiceError, fromAxios, fromFetch } from './utils';")
                appendLine("export type { RawResponse } from './utils';")
                if ("axios" in clients) {
                    appendLine("export { createAxiosClient } from './axios-client';")
                }
                if ("fetch" in clients) {
                    appendLine("export { createFetchClient } from './fetch-client';")
                }
            }

        fileManifest.writeFile("index.ts", content)
        logger.info("Generated index.ts")
    }

    private fun loadResourceTemplate(resourcePath: String): String {
        return this::class.java.classLoader.getResourceAsStream(resourcePath)?.use { inputStream ->
            inputStream.bufferedReader().readText()
        } ?: throw IllegalArgumentException("Template resource not found: $resourcePath")
    }
}
