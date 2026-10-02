package com.cjmckenzie.zodhttpclient.hono

import com.cjmckenzie.zodhttpclient.models.OperationDescriptor
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.model.traits.HttpQueryTrait
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
                appendLine("export type UnexpectedErrorContext = {")
                appendLine("  correlationId: string;")
                appendLine("  context: Context;")
                appendLine("};")
                appendLine()
                appendLine("export type HonoRouterOptions = {")
                appendLine("  onUnexpectedError?: (error: unknown, details: UnexpectedErrorContext) => void | Promise<void>;")
                appendLine("};")
                appendLine()
                appendLine("export function createHonoRouter(handlers: HonoHandlers, options: HonoRouterOptions = {}): Hono {")
                appendLine("  const app = new Hono();")
                appendLine()
                operations.forEach { operation ->
                    val inputBindings = operation.inputBindingsLiteral()
                    appendLine("  app.${operation.httpMethod.lowercase()}('${operation.uri.toHonoPath()}', async (c) => {")
                    appendLine("    try {")
                    appendLine(
                        "      const input = parseRequestInput(${operation.operationName}Input, await readInput(c, $inputBindings));",
                    )
                    appendLine("      const output = await handlers.${operation.methodName}(input, c);")
                    if (operation.outputSchema != null) {
                        appendLine("      const body = ${operation.operationName}Output.parse({ body: output, headers: {} });")
                        appendLine("      return c.json(body, ${operation.successStatusCode} as const);")
                    } else {
                        appendLine("      return c.body(null, ${operation.successStatusCode} as const);")
                    }
                    appendLine("    } catch (error) {")
                    appendLine("      return toErrorResponse(c, error, options);")
                    appendLine("    }")
                    appendLine("  });")
                    appendLine()
                }
                appendLine("  return app;")
                appendLine("}")
                appendLine()
                appendLine("async function readInput(")
                appendLine("  c: Context,")
                appendLine("  bindings: {")
                appendLine("    path: readonly string[];")
                appendLine("    query: readonly { memberName: string; queryName: string }[];")
                appendLine("    headers: readonly { memberName: string; headerName: string }[];")
                appendLine("    body: readonly string[];")
                appendLine("  },")
                appendLine(") {")
                appendLine("  let body: unknown = {};")
                appendLine("  const expectsBody = c.req.method !== 'GET' && c.req.method !== 'HEAD';")
                appendLine()
                appendLine("  if (expectsBody) {")
                appendLine("    const rawBody = await c.req.text();")
                appendLine()
                appendLine("    try {")
                appendLine("      body = rawBody.length > 0 ? JSON.parse(rawBody) : {};")
                appendLine("    } catch {")
                appendLine("      throw new RequestValidationError(")
                appendLine("        'Request body must be valid JSON.',")
                appendLine("        [{ path: 'body', message: 'Could not parse JSON request body.' }],")
                appendLine("      );")
                appendLine("    }")
                appendLine("  }")
                appendLine()
                appendLine("  const input: Record<string, unknown> = {};")
                appendLine("  for (const memberName of bindings.path) {")
                appendLine("    input[memberName] = c.req.param(memberName);")
                appendLine("  }")
                appendLine("  for (const { memberName, queryName } of bindings.query) {")
                appendLine("    input[memberName] = c.req.query(queryName);")
                appendLine("  }")
                appendLine("  for (const { memberName, headerName } of bindings.headers) {")
                appendLine("    input[memberName] = c.req.header(headerName);")
                appendLine("  }")
                appendLine("  if (typeof body === 'object' && body !== null && !Array.isArray(body)) {")
                appendLine("    for (const memberName of bindings.body) {")
                appendLine("      if (Object.hasOwn(body, memberName)) {")
                appendLine("        input[memberName] = (body as Record<string, unknown>)[memberName];")
                appendLine("      }")
                appendLine("    }")
                appendLine("  }")
                appendLine("  return input;")
                appendLine("}")
                appendLine()
                appendLine("class RequestValidationError {")
                appendLine("  constructor(readonly message: string, readonly issues: { path: string; message: string }[]) {}")
                appendLine("}")
                appendLine()
                appendLine("function parseRequestInput<S extends z.ZodType>(schema: S, input: unknown): z.output<S> {")
                appendLine("  try {")
                appendLine("    return schema.parse(input);")
                appendLine("  } catch (error) {")
                appendLine("    if (error instanceof z.ZodError) {")
                appendLine("      throw new RequestValidationError('Request body failed validation.', error.issues.map(formatZodIssue));")
                appendLine("    }")
                appendLine("    throw error;")
                appendLine("  }")
                appendLine("}")
                appendLine()
                appendLine("async function toErrorResponse(c: Context, error: unknown, options: HonoRouterOptions): Promise<Response> {")
                appendLine("  if (error instanceof RequestValidationError) {")
                appendLine("    return c.json({ message: error.message, issues: error.issues }, 400 as const);")
                appendLine("  }")
                appendLine()
                appendLine("  let kind: unknown;")
                appendLine("  try {")
                appendLine("    kind = (error as any)?._kind ?? (error as any)?.name;")
                appendLine("  } catch {")
                appendLine("    // Unreadable exception properties are treated as an unexpected error.")
                appendLine("  }")
                uniqueErrors.forEach { error ->
                    appendLine("  if (kind === '${error.name}') {")
                    appendLine("    return c.json(errorBody(error, '${error.name}'), ${error.httpStatusCode} as const);")
                    appendLine("  }")
                }
                appendLine("  const correlationId = crypto.randomUUID();")
                appendLine("  c.header('X-Correlation-ID', correlationId);")
                appendLine("  try {")
                appendLine("    await options.onUnexpectedError?.(error, { correlationId, context: c });")
                appendLine("  } catch {")
                appendLine("    // A failing logging hook must not change the safe public response.")
                appendLine("  }")
                appendLine("  return c.json({ message: 'InternalServerError', _kind: 'InternalServerError' }, 500 as const);")
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
                appendLine("export type { HonoHandlers, HonoRouterOptions, UnexpectedErrorContext } from './hono-router.js';")
            }

        fileManifest.writeFile("index.ts", content)
        logger.info("Generated index.ts for Hono server")
    }

    private fun OperationDescriptor.inputBindingsLiteral(): String {
        val pathMembers = inputBindings.pathParameters.keys.joinToString(", ") { "'$it'" }
        val queryBindings =
            inputBindings.queryParameters.entries.joinToString(", ") { (memberName, parameterInfo) ->
                val queryName = parameterInfo.member?.getTrait(HttpQueryTrait::class.java)?.orElse(null)?.value ?: memberName
                "{ memberName: '$memberName', queryName: '$queryName' }"
            }
        val headerBindings =
            inputBindings.headerParameters.entries.joinToString(", ") { (headerName, parameterInfo) ->
                val memberName = parameterInfo.member?.memberName ?: headerName
                "{ memberName: '$memberName', headerName: '$headerName' }"
            }
        val bodyMembers = inputBindings.bodyParameters.keys.joinToString(", ") { "'$it'" }
        return "{ path: [$pathMembers], query: [$queryBindings], headers: [$headerBindings], body: [$bodyMembers] } as const"
    }

    private fun String.toHonoPath(): String =
        replace(Regex("\\{([^}]+)}")) { match ->
            ":${match.groupValues[1]}"
        }
}
