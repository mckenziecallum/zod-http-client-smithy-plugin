package com.cjmckenzie.zodhttpclient.hono

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.loader.ModelAssembler
import software.amazon.smithy.model.node.Node

class ZodHonoSmithyPluginTest {
    @Test
    fun `should expose zod-hono plugin name`() {
        assertThat(ZodHonoSmithyPlugin().name).isEqualTo("zod-hono")
    }

    @Test
    fun `should generate hono router from operations`() {
        val manifest = executePlugin()

        assertThat(manifest.hasFile("hono-router.ts")).isTrue()
        assertThat(manifest.hasFile("CreateItemInput.ts")).isTrue()
        assertThat(manifest.hasFile("CreateItemOutput.ts")).isTrue()

        val router = manifest.getFileString("hono-router.ts").get()
        assertThat(router)
            .contains("import { Hono } from 'hono';")
            .contains("export type HonoHandlers = {")
            .contains("createItem(input: z.output<typeof CreateItemInput>, c: Context)")
            .contains("completeItem(input: z.output<typeof CompleteItemInput>, c: Context)")
            .contains("z.output<typeof CreateItemOutput> | Promise<z.output<typeof CreateItemOutput>>")
            .contains("app.post('/items/:itemType/:itemId', async (c) => {")
            .contains("app.post('/items/:itemId/complete', async (c) => {")
            .contains("app.get('/items/:itemId', async (c) => {")
            .contains("parseRequestInput(CreateItemInput, await readInput(c")
            .contains("parseRequestInput(CompleteItemInput, await readInput(c")
            .contains("{ memberName: 'requestId', headerName: 'X-Request-ID', type: 'string' }")
            .contains("{ memberName: 'retryCount', headerName: 'X-Retry-Count', type: 'integer' }")
            .contains("input[memberName] = decodeScalar(c.req.header(headerName), type);")
            .contains("CreateItemOutput.parse({ body: output, headers: { 'X-Request-ID': output.requestId } })")
            .contains("c.header('X-Request-ID', String(body.requestId))")
            .contains("const { requestId, ...jsonBody } = body;")
            .contains("return c.json({ ...parsed.data, _kind: 'NotFoundException' }, 404 as const);")
            .doesNotContain("__type")
    }

    @Test
    fun `should generate empty body post parsing and validation error responses`() {
        val router = executePlugin().getFileString("hono-router.ts").get()

        assertThat(router)
            .contains("let body: unknown = {};")
            .contains("const expectsBody = c.req.method !== 'GET' && c.req.method !== 'HEAD';")
            .contains("body = rawBody.length > 0 ? JSON.parse(rawBody) : {};")
            .contains("'Request body must be valid JSON.'")
            .contains("if (error instanceof z.ZodError)")
            .contains("'Request body failed validation.'")
            .contains("error.issues.map(formatZodIssue)")
            .contains("path: issue.path.length > 0 ? issue.path.join('.') : 'body'")
            .contains("if (error instanceof RequestValidationError)")
    }

    @Test
    fun `should generate index exports`() {
        val index = executePlugin().getFileString("index.ts").get()

        assertThat(index)
            .contains("export { createHonoRouter } from './hono-router.js';")
            .contains("export type { HonoHandlers, HonoRouterOptions, UnexpectedErrorContext } from './hono-router.js';")
            .contains("export { CompleteItemInput } from './CompleteItemInput.js';")
            .contains("export { GetItemInput } from './GetItemInput.js';")
    }

    private fun executePlugin(): MockManifest {
        val plugin = ZodHonoSmithyPlugin()
        val manifest = MockManifest()
        val model =
            ModelAssembler()
                .addImport(javaClass.getResource("/models/test-service.smithy"))
                .assemble().unwrap()
        val settings =
            Node.objectNode()
                .withMember("service", Node.from("com.example.test#TestService"))
        val context = PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build()
        plugin.execute(context)
        return manifest
    }
}
