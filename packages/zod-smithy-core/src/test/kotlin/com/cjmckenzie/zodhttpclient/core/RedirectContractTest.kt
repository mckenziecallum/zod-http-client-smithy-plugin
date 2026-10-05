package com.cjmckenzie.zodhttpclient.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.smithy.model.loader.ModelAssembler
import software.amazon.smithy.model.shapes.OperationShape

class RedirectContractTest {
    @Test
    fun `supports only bodyless redirects with required string Location`() {
        for (code in listOf(301, 302, 303, 307, 308)) {
            val descriptor = generate(code, "@required @httpHeader(\"lOcAtIoN\") destination: String")
            assertThat(descriptor.isRedirect).isTrue()
            assertThat(descriptor.successStatusCode).isEqualTo(code)
        }
    }

    @Test
    fun `rejects unsupported redirect statuses`() {
        assertThatThrownBy { generate(304, "@required @httpHeader(\"Location\") location: String") }
            .hasMessageContaining("Content").hasMessageContaining("unsupported redirect status 304")
    }

    @Test
    fun `rejects redirect bodies and payloads`() {
        for (members in listOf("value: String", "@httpPayload value: String", "")) {
            assertThatThrownBy { generate(303, members) }
                .hasMessageContaining("Content").hasMessageContaining("only @httpHeader members")
        }
    }

    @Test
    fun `requires a required string Location header`() {
        for (members in listOf(
            "@httpHeader(\"Location\") location: String",
            "@required @httpHeader(\"Other\") location: String",
            "@required @httpHeader(\"Location\") location: Integer",
        )) {
            assertThatThrownBy { generate(303, members) }
                .hasMessageContaining("Content").hasMessageContaining("required string")
        }
    }

    @Test
    fun `rejects non JSON output payloads on ordinary operations`() {
        assertThatThrownBy { generate(200, "@httpPayload content: String") }
            .hasMessageContaining("@httpPayload trait is not supported")
    }

    private fun generate(
        code: Int,
        members: String,
    ) = ModelAssembler()
        .addUnparsedModel(
            "redirect.smithy",
            """
            ${'$'}version: "2"
            namespace test.redirect
            @suppress(["HttpResponseCodeSemantics"])
            @http(method: "GET", uri: "/content", code: $code)
            operation Content { output: ContentOutput }
            structure ContentOutput { $members }
            """.trimIndent(),
        )
        .assemble().unwrap().let { model ->
            OperationDescriptorGenerator().generateForOperation(
                model,
                model.expectShape(
                    software.amazon.smithy.model.shapes.ShapeId.from("test.redirect#Content"),
                    OperationShape::class.java,
                ),
            )
        }
}
