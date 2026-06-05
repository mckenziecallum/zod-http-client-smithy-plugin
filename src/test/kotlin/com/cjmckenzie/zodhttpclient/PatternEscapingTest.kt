package com.cjmckenzie.zodhttpclient

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.loader.ModelAssembler
import software.amazon.smithy.model.node.Node

class PatternEscapingTest {
    @Test
    fun `should not double-escape backslashes in regex patterns`() {
        val plugin = ZodHttpClientSmithyPlugin()
        val manifest = MockManifest()

        val model =
            ModelAssembler()
                .addImport(javaClass.getResource("/models/test-service.smithy"))
                .assemble()
                .unwrap()

        val settings =
            Node.objectNode()
                .withMember("service", Node.from("com.example.test#TestService"))
        val context =
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build()

        plugin.execute(context)

        assertThat(manifest.hasFile("GetVersionInput.ts")).isTrue()

        val actualContent = manifest.getFileString("GetVersionInput.ts").get()

        // Verify single backslash in regex pattern
        assertThat(actualContent)
            .contains("/^([a-zA-Z0-9_-]+|\\${'$'}latest)$/")
            .doesNotContain("/^([a-zA-Z0-9_-]+|\\\\${'$'}latest)$/")

        // Error message should show original pattern
        assertThat(actualContent)
            .contains("Must match the required format: ^([a-zA-Z0-9_-]+|\\\\${'$'}latest)${'$'}")
    }
}
