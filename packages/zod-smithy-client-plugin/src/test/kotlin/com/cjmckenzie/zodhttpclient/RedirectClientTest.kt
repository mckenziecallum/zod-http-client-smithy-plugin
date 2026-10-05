package com.cjmckenzie.zodhttpclient

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.loader.ModelAssembler
import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.Node

class RedirectClientTest {
    @Test
    fun `redirect only services expose schemas and empty clients without JSON methods`() {
        val model =
            ModelAssembler().addUnparsedModel(
                "redirect.smithy",
                """
                ${'$'}version: "2"
                namespace test.redirect
                service ContentService { version: "1", operations: [Content] }
                @suppress(["HttpResponseCodeSemantics"])
                @http(method: "GET", uri: "/content", code: 303)
                operation Content { output: ContentOutput }
                structure ContentOutput {
                    @required @httpHeader("Location") location: String
                }
                """.trimIndent(),
            ).assemble().unwrap()
        val manifest = MockManifest()
        ZodHttpClientSmithyPlugin().execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(
                Node.objectNode().withMember("service", "test.redirect#ContentService")
                    .withMember("client", ArrayNode.fromStrings(listOf("axios", "fetch"))),
            ).build(),
        )
        assertThat(manifest.hasFile("ContentInput.ts")).isTrue()
        assertThat(manifest.hasFile("ContentOutput.ts")).isTrue()
        assertThat(manifest.getFileString("index.ts").get())
            .contains("ContentInput", "createAxiosClient", "createFetchClient")
        for (file in listOf("axios-client.ts", "fetch-client.ts")) {
            assertThat(manifest.getFileString(file).get())
                .doesNotContain("async content(", "response.json()", "ContentOutput.parse")
        }
    }
}
