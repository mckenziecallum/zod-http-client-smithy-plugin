package com.cjmckenzie.zodhttpclient

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.loader.ModelAssembler
import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.Node
import java.nio.file.Paths

class ZodHttpClientSmithyPluginIntegrationTest {
    // --- Client config tests ---

    @Test
    fun `should generate axios client by default`() {
        val manifest = executePlugin()
        val files = manifest.files.map { it.toString().removePrefix("/") }
        assertThat(files).contains("axios-client.ts").doesNotContain("fetch-client.ts")
    }

    @Test
    fun `should generate only fetch client when configured`() {
        val manifest = executePlugin(clients = listOf("fetch"))
        val files = manifest.files.map { it.toString().removePrefix("/") }
        assertThat(files).contains("fetch-client.ts").doesNotContain("axios-client.ts")
    }

    @Test
    fun `should generate both clients when configured`() {
        val manifest = executePlugin(clients = listOf("axios", "fetch"))
        val files = manifest.files.map { it.toString().removePrefix("/") }
        assertThat(files).contains("axios-client.ts", "fetch-client.ts")
    }

    @Test
    fun `should reject unsupported client types`() {
        assertThatThrownBy { executePlugin(clients = listOf("got")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Unsupported client type")
    }

    // --- Schema file tests ---

    @Test
    fun `should generate all expected files`() {
        val manifest = executePlugin()
        val files = manifest.files.map { it.toString().removePrefix("/") }
        assertThat(files).contains(
            "utils.ts", "errors.ts", "axios-client.ts", "index.ts",
            "CreateItemInput.ts", "CreateItemOutput.ts",
            "GetItemInput.ts", "GetItemOutput.ts",
            "GetItemStatusInput.ts", "SearchItemsInput.ts", "GetVersionInput.ts",
        )
    }

    @Test
    fun `should match expected utils output`() {
        assertFileMatches("utils.ts")
    }

    @Test
    fun `should match expected errors output`() {
        assertFileMatches("errors.ts")
    }

    @Test
    fun `should match expected CreateItemInput`() {
        assertFileMatches("CreateItemInput.ts")
    }

    @Test
    fun `should match expected CreateItemOutput`() {
        assertFileMatches("CreateItemOutput.ts")
    }

    @Test
    fun `should match expected GetItemInput`() {
        assertFileMatches("GetItemInput.ts")
    }

    @Test
    fun `should match expected GetItemOutput`() {
        assertFileMatches("GetItemOutput.ts")
    }

    @Test
    fun `should match expected GetItemStatusInput`() {
        assertFileMatches("GetItemStatusInput.ts")
    }

    @Test
    fun `should match expected SearchItemsInput`() {
        assertFileMatches("SearchItemsInput.ts")
    }

    @Test
    fun `should match expected GetVersionInput`() {
        assertFileMatches("GetVersionInput.ts")
    }

    @Test
    fun `should match expected axios client`() {
        assertFileMatches("axios-client.ts")
    }

    @Test
    fun `should match expected index`() {
        assertFileMatches("index.ts")
    }

    // --- Error handling tests ---

    @Test
    fun `errors file should contain typed exception classes`() {
        val manifest = executePlugin()
        val errors = manifest.getFileString("errors.ts").get()

        assertThat(errors)
            .contains("class BadRequestException extends ServiceError")
            .contains("class NotFoundException extends ServiceError")
            .contains("class InternalServiceException extends ServiceError")
            .contains("readonly _kind = 'BadRequestException' as const")
            .contains("readonly _kind = 'NotFoundException' as const")
            .contains("parseServiceError")
    }

    @Test
    fun `errors file should have status code matching in parseServiceError`() {
        val manifest = executePlugin()
        val errors = manifest.getFileString("errors.ts").get()

        assertThat(errors)
            .contains("statusCode === 400")
            .contains("statusCode === 404")
            .contains("statusCode === 500")
    }

    @Test
    fun `axios client should catch errors and use parseServiceError`() {
        val manifest = executePlugin()
        val client = manifest.getFileString("axios-client.ts").get()

        assertThat(client)
            .contains("import { parseServiceError } from './errors.js';")
            .contains("try {")
            .contains("parseServiceError('CreateItem', e.response.status, e.response.data)")
    }

    @Test
    fun `fetch client should check response ok and use parseServiceError`() {
        val manifest = executePlugin(clients = listOf("fetch"))
        val client = manifest.getFileString("fetch-client.ts").get()

        assertThat(client)
            .contains("import { parseServiceError } from './errors.js';")
            .contains("const method: string = req.method;")
            .contains("method,")
            .contains("...(method !== 'GET' && method !== 'HEAD' && Object.keys(req.body).length > 0")
            .doesNotContain("...(req.method !== 'GET' && req.method !== 'HEAD'")
            .contains("if (!response.ok)")
            .contains("parseServiceError('CreateItem', response.status, errorBody)")
    }

    @Test
    fun `index should export error classes and ServiceError`() {
        val manifest = executePlugin()
        val index = manifest.getFileString("index.ts").get()

        assertThat(index)
            .contains("BadRequestException")
            .contains("NotFoundException")
            .contains("InternalServiceException")
            .contains("parseServiceError")
            .contains("ServiceError")
    }

    // --- Native types tests ---

    @Test
    fun `input schemas should use native types`() {
        val manifest = executePlugin()
        val schema = manifest.getFileString("GetItemStatusInput.ts").get()
        assertThat(schema)
            .contains("z.boolean()").contains("z.number().int()")
            .doesNotContain("parseStringToBoolean").doesNotContain("parseStringToInteger")
    }

    // --- Helpers ---

    private fun assertFileMatches(fileName: String) {
        val manifest = executePlugin()
        val actual = manifest.getFileString(fileName).get()
        val expected = loadExpectedFile(fileName)
        assertThat(actual.trim())
            .describedAs("$fileName should match expected output")
            .isEqualTo(expected.trim())
    }

    private fun executePlugin(clients: List<String>? = null): MockManifest {
        val plugin = ZodHttpClientSmithyPlugin()
        val manifest = MockManifest()
        val model =
            ModelAssembler()
                .addImport(Paths.get("src", "test", "resources", "models", "test-service.smithy"))
                .assemble().unwrap()
        val settings =
            Node.objectNode()
                .withMember("service", Node.from("com.example.test#TestService"))
                .let { if (clients != null) it.withMember("client", ArrayNode.fromStrings(clients)) else it }
        val context = PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build()
        plugin.execute(context)
        return manifest
    }

    private fun loadExpectedFile(fileName: String): String {
        val resourcePath = "/expected-outputs/$fileName"
        return this::class.java.getResourceAsStream(resourcePath)?.use { it.bufferedReader().readText() }
            ?: throw IllegalArgumentException("Expected file not found: $resourcePath")
    }
}
