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
                    appendLine("import { ${operation.operationName}Input } from './${operation.operationName}Input.js';")
                    operation.outputSchema?.let {
                        appendLine("import { ${operation.operationName}Output } from './${operation.operationName}Output.js';")
                    }
                }
                appendLine()
                appendLine("export type HonoHandlers = {")
                operations.forEach { operation ->
                    val outputType =
                        operation.outputSchema?.let { "z.output<typeof ${operation.operationName}Output>" }
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
                    val headerBindings = operation.headerBindingsLiteral()
                    appendLine("  app.${operation.httpMethod.lowercase()}('${operation.uri.toHonoPath()}', async (c) => {")
                    appendLine("    try {")
                    appendLine(
                        "      const input = ${operation.operationName}Input.parse(await readInput(c, $headerBindings));",
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
                appendLine("async function readInput(")
                appendLine("  c: Context,")
                appendLine("  headerBindings: readonly { memberName: string; headerName: string }[],")
                appendLine(") {")
                appendLine("  let body = {};")
                appendLine("  const expectsBody = c.req.method !== 'GET' && c.req.method !== 'HEAD';")
                appendLine()
                appendLine("  if (expectsBody) {")
                appendLine("    const rawBody = await c.req.text();")
                appendLine()
                appendLine("    try {")
                appendLine("      body = rawBody.length > 0 ? JSON.parse(rawBody) : {};")
                appendLine("    } catch {")
                appendLine("      throw {")
                appendLine("        name: 'ValidationError',")
                appendLine("        message: 'Request body must be valid JSON.',")
                appendLine("        issues: [{ path: 'body', message: 'Could not parse JSON request body.' }],")
                appendLine("      };")
                appendLine("    }")
                appendLine("  }")
                appendLine()
                appendLine("  const headers = Object.fromEntries(")
                appendLine("    headerBindings")
                appendLine("      .map(({ memberName, headerName }) => [memberName, c.req.header(headerName)])")
                appendLine("      .filter(([, value]) => value !== undefined),")
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
                appendLine("  if (error instanceof z.ZodError) {")
                appendLine("    return c.json({")
                appendLine("      message: 'Request body failed validation.',")
                appendLine("      issues: error.issues.map(formatZodIssue),")
                appendLine("    }, 400 as const);")
                appendLine("  }")
                appendLine()
                appendLine("  const kind = (error as any)?._kind ?? (error as any)?.name;")
                appendLine("  if (kind === 'ValidationError') {")
                appendLine("    return c.json({")
                appendLine("      message: (error as any)?.message ?? 'Request body failed validation.',")
                appendLine("      issues: (error as any)?.issues ?? [],")
                appendLine("    }, 400 as const);")
                appendLine("  }")
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
                appendLine()
                appendLine("function formatZodIssue(issue: z.ZodIssue) {")
                appendLine("  return {")
                appendLine("    path: issue.path.length > 0 ? issue.path.join('.') : 'body',")
                appendLine("    message: issue.message,")
                appendLine("  };")
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
                    appendLine("export { ${operation.operationName}Input } from './${operation.operationName}Input.js';")
                    appendLine(
                        "export type { ${operation.operationName}Input as ${operation.operationName}InputType } " +
                            "from './${operation.operationName}Input.js';",
                    )
                    operation.outputSchema?.let {
                        appendLine("export { ${operation.operationName}Output } from './${operation.operationName}Output.js';")
                        appendLine(
                            "export type { ${operation.operationName}Output as ${operation.operationName}OutputType } " +
                                "from './${operation.operationName}Output.js';",
                        )
                    }
                }
                appendLine("export { createHonoRouter } from './hono-router.js';")
                appendLine("export type { HonoHandlers } from './hono-router.js';")
            }

        fileManifest.writeFile("index.ts", content)
        logger.info("Generated index.ts for Hono server")
    }

    private fun OperationDescriptor.headerBindingsLiteral(): String {
        val bindings =
            inputBindings.headerParameters.entries.joinToString(", ") { (headerName, parameterInfo) ->
                val memberName = parameterInfo.member?.memberName ?: headerName
                "{ memberName: '$memberName', headerName: '$headerName' }"
            }
        return "[$bindings] as const"
    }

    private fun String.toHonoPath(): String =
        replace(Regex("\\{([^}]+)}")) { match ->
            ":${match.groupValues[1]}"
        }
}
