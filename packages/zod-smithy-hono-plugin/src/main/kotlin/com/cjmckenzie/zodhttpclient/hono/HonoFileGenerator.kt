package com.cjmckenzie.zodhttpclient.hono

import com.cjmckenzie.zodhttpclient.models.OperationDescriptor
import software.amazon.smithy.build.FileManifest
import java.util.logging.Logger

class HonoFileGenerator {
    private val logger = Logger.getLogger(HonoFileGenerator::class.java.name)

    fun generateHonoRouterFile(
        fileManifest: FileManifest,
        operations: List<OperationDescriptor>,
    ) {
        val uniqueErrors = operations.flatMap { it.errors }.distinctBy { it.name }
        val content =
            buildString {
                appendLine("import { Hono } from 'hono';")
                appendLine("import type { Context } from 'hono';")
                appendLine("import { z } from 'zod';")
                operations.forEach { operation ->
                    appendLine("import { ${operation.operationName}Input } from './${operation.operationName}Input';")
                    operation.outputSchema?.let {
                        appendLine("import { ${operation.operationName}Output } from './${operation.operationName}Output';")
                    }
                }
                appendLine()
                appendLine("export type HonoHandlers = {")
                operations.forEach { operation ->
                    val outputType =
                        operation.outputSchema?.let { "z.input<typeof ${operation.operationName}Output>" }
                            ?: "unknown"
                    appendLine(
                        "  ${operation.methodName}(input: z.output<typeof ${operation.operationName}Input>, c: Context): " +
                            "$outputType | Promise<$outputType>;",
                    )
                }
                appendLine("};")
                appendLine()
                appendLine("export function createHonoRouter(handlers: HonoHandlers): Hono {")
                appendLine("  const app = new Hono();")
                appendLine()
                operations.forEach { operation ->
                    appendLine("  app.${operation.httpMethod.lowercase()}('${operation.uri.toHonoPath()}', async (c) => {")
                    appendLine("    try {")
                    appendLine(
                        "      const input = ${operation.operationName}Input.parse(await readInput(c, ${operation.headerNamesLiteral()}));",
                    )
                    appendLine("      const output = await handlers.${operation.methodName}(input, c);")
                    if (operation.outputSchema != null) {
                        appendLine("      const body = ${operation.operationName}Output.parse({ body: output, headers: {} });")
                        appendLine("      return c.json(body, ${operation.successStatusCode} as const);")
                    } else {
                        appendLine("      return c.body(null, ${operation.successStatusCode} as const);")
                    }
                    appendLine("    } catch (error) {")
                    appendLine("      return toErrorResponse(c, error);")
                    appendLine("    }")
                    appendLine("  });")
                    appendLine()
                }
                appendLine("  return app;")
                appendLine("}")
                appendLine()
                appendLine("async function readInput(c: Context, headerNames: readonly string[]) {")
                appendLine("  const body = await c.req.json().catch(() => ({}));")
                appendLine("  const headers = Object.fromEntries(")
                appendLine("    headerNames.map((name) => [name, c.req.header(name)]).filter(([, value]) => value !== undefined),")
                appendLine("  );")
                appendLine("  return {")
                appendLine("    ...c.req.param(),")
                appendLine("    ...c.req.query(),")
                appendLine("    ...headers,")
                appendLine("    ...(typeof body === 'object' && body !== null ? body : {}),")
                appendLine("  };")
                appendLine("}")
                appendLine()
                appendLine("function toErrorResponse(c: Context, error: unknown): Response {")
                appendLine("  const kind = (error as any)?._kind ?? (error as any)?.name;")
                uniqueErrors.forEach { error ->
                    appendLine("  if (kind === '${error.name}') {")
                    appendLine("    return c.json(errorBody(error, '${error.name}'), ${error.httpStatusCode} as const);")
                    appendLine("  }")
                }
                appendLine("  return c.json(errorBody(error, 'InternalServerError'), 500 as const);")
                appendLine("}")
                appendLine()
                appendLine("function errorBody(error: unknown, fallback: string) {")
                appendLine("  const message = (error as any)?.message ?? fallback;")
                appendLine("  return { message, _kind: (error as any)?._kind ?? fallback };")
                appendLine("}")
            }

        fileManifest.writeFile("hono-router.ts", content)
        logger.info("Generated hono-router.ts with ${operations.size} routes")
    }

    fun generateIndexFile(
        fileManifest: FileManifest,
        operations: List<OperationDescriptor>,
    ) {
        val content =
            buildString {
                operations.forEach { operation ->
                    appendLine("export { ${operation.operationName}Input } from './${operation.operationName}Input';")
                    appendLine(
                        "export type { ${operation.operationName}Input as ${operation.operationName}InputType } " +
                            "from './${operation.operationName}Input';",
                    )
                    operation.outputSchema?.let {
                        appendLine("export { ${operation.operationName}Output } from './${operation.operationName}Output';")
                        appendLine(
                            "export type { ${operation.operationName}Output as ${operation.operationName}OutputType } " +
                                "from './${operation.operationName}Output';",
                        )
                    }
                }
                appendLine("export { createHonoRouter } from './hono-router';")
                appendLine("export type { HonoHandlers } from './hono-router';")
            }

        fileManifest.writeFile("index.ts", content)
        logger.info("Generated index.ts for Hono server")
    }

    private fun OperationDescriptor.headerNamesLiteral(): String {
        val headers = inputBindings.headerParameters.keys.joinToString(", ") { "'$it'" }
        return "[$headers] as const"
    }

    private fun String.toHonoPath(): String =
        replace(Regex("\\{([^}]+)}")) { match ->
            ":${match.groupValues[1]}"
        }
}
