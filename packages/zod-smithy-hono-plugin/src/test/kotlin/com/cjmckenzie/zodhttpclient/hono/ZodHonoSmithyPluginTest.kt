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
            .contains("CreateItemInput.parse(await readInput(c")
            .contains("CompleteItemInput.parse(await readInput(c")
            .contains("{ memberName: 'requestId', headerName: 'X-Request-ID' }")
            .contains("{ memberName: 'retryCount', headerName: 'X-Retry-Count' }")
            .contains(".map(({ memberName, headerName }) => [memberName, c.req.header(headerName)])")
            .contains("CreateItemOutput.parse({ body: output, headers: {} })")
            .contains("return c.json(errorBody(error, 'NotFoundException'), 404 as const);")
    }

    @Test
    fun `should generate empty body post parsing and validation error responses`() {
        val router = executePlugin().getFileString("hono-router.ts").get()

        assertThat(router)
            .contains("let body = {};")
            .contains("const expectsBody = c.req.method !== 'GET' && c.req.method !== 'HEAD';")
            .contains("body = rawBody.length > 0 ? JSON.parse(rawBody) : {};")
            .contains("message: 'Request body must be valid JSON.'")
            .contains("if (error instanceof z.ZodError)")
            .contains("message: 'Request body failed validation.'")
            .contains("issues: error.issues.map(formatZodIssue)")
            .contains("path: issue.path.length > 0 ? issue.path.join('.') : 'body'")
            .contains("if (kind === 'ValidationError')")
    }

    @Test
    fun `should generate index exports`() {
        val index = executePlugin().getFileString("index.ts").get()

        assertThat(index)
            .contains("export { createHonoRouter } from './hono-router.js';")
            .contains("export type { HonoHandlers } from './hono-router.js';")
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
