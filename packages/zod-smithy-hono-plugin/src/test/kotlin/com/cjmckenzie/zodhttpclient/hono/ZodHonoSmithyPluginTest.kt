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
            .contains("app.post('/items/:itemType/:itemId', async (c) => {")
            .contains("app.get('/items/:itemId', async (c) => {")
            .contains("CreateItemInput.parse(await readInput(c")
            .contains("CreateItemOutput.parse({ body: output, headers: {} })")
            .contains("return c.json(errorBody(error, 'NotFoundException'), 404 as const);")
    }

    @Test
    fun `should generate index exports`() {
        val index = executePlugin().getFileString("index.ts").get()

        assertThat(index)
            .contains("export { createHonoRouter } from './hono-router';")
            .contains("export type { HonoHandlers } from './hono-router';")
            .contains("export { GetItemInput } from './GetItemInput';")
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
