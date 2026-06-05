package com.cjmckenzie.zodhttpclient

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.pattern.UriPattern
import software.amazon.smithy.model.shapes.DoubleShape
import software.amazon.smithy.model.shapes.IntegerShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.HttpHeaderTrait
import software.amazon.smithy.model.traits.HttpLabelTrait
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.model.traits.RangeTrait
import software.amazon.smithy.model.traits.RequiredTrait

class ZodHttpClientSmithyPluginTest {
    @Test
    fun `plugin name is correct`() {
        assertThat(ZodHttpClientSmithyPlugin().name).isEqualTo("zod-client")
    }

    @Test
    fun `should generate utils file`() {
        val manifest = executePlugin(createSimpleTestModel())

        assertThat(manifest.hasFile("utils.ts")).isTrue()
        val utilsContent = manifest.getFileString("utils.ts").get()
        assertThat(utilsContent)
            .contains("fromAxios")
            .contains("fromFetch")
            .contains("RawResponse")
    }

    @Test
    fun `should generate input schema with path parameters and transform`() {
        val manifest = executePlugin(createTestModelWithPathParameters())

        assertThat(manifest.hasFile("CreateItemInput.ts")).isTrue()
        val schema = manifest.getFileString("CreateItemInput.ts").get()
        assertThat(schema)
            .contains("z.object({")
            .contains("itemId: z.string()")
            .contains(".transform((v) => ({")
            .contains("path:")
            .contains("headers:")
            .contains("body:")
            .contains("url:")
            .contains("method:")
            .contains("export type CreateItemInput = z.output<typeof CreateItemInput>;")
            .doesNotContain("parseStringTo")
            .doesNotContain("JsonStringified")
    }

    @Test
    fun `should handle operation without input`() {
        val manifest = executePlugin(createTestModelWithoutInput())

        assertThat(manifest.hasFile("utils.ts")).isTrue()
        assertThat(manifest.hasFile("GetItemInput.ts")).isTrue()
        assertThat(manifest.getFileString("GetItemInput.ts").get())
            .contains("export const GetItemInput = z.object({")
            .contains("path: {},")
            .contains("body: {},")
    }

    @Test
    fun `should use native types for HTTP-bound integers`() {
        val manifest = executePlugin(createTestModelWithHttpBoundIntegerWithRange())

        val schema = manifest.getFileString("SearchItemsInput.ts").get()
        assertThat(schema)
            .contains("z.number().int().min(1).max(10).default(5)")
            .doesNotContain("parseStringToInteger")
            .doesNotContain(".pipe(")
    }

    @Test
    fun `should use native types for HTTP-bound numbers with range`() {
        val manifest = executePlugin(createTestModelWithHttpBoundNumberWithRange())

        val schema = manifest.getFileString("GetItemWithScoreInput.ts").get()
        assertThat(schema)
            .contains("z.number().min(0.0).max(100.0)")
            .doesNotContain("parseStringToNumber")
            .doesNotContain(".pipe(")
    }

    @Test
    fun `should handle default values correctly`() {
        val manifest = executePlugin(createTestModelWithDefaults())

        val schema = manifest.getFileString("CreateItemInput.ts").get()
        assertThat(schema)
            .contains(".default(13)")
            .doesNotContain(".default(\"13\")")
            .contains(".default({})")
            .contains(".default(\"default-name\")")
    }

    @Test
    fun `should generate output schema when operation has output`() {
        val manifest = executePlugin(createTestModelWithOutput())

        assertThat(manifest.hasFile("CreateItemOutput.ts")).isTrue()
        val schema = manifest.getFileString("CreateItemOutput.ts").get()
        assertThat(schema)
            .contains("body: z.object({")
            .contains("statusCode: z.number().optional()")
            .contains(".transform((v) => ({")
            .contains("...v.body,")
    }

    @Test
    fun `should generate client with typed methods`() {
        val manifest = executePlugin(createTestModelWithOutput())

        assertThat(manifest.hasFile("axios-client.ts")).isTrue()
        val client = manifest.getFileString("axios-client.ts").get()
        assertThat(client)
            .contains("createAxiosClient")
            .contains("async createItem")
            .contains("CreateItemInput.parse(input)")
            .contains("CreateItemOutput.parse(fromAxios(response))")
    }

    @Test
    fun `should fail when service not found`() {
        val model = createSimpleTestModel()
        val manifest = MockManifest()
        val settings =
            Node.objectNode()
                .withMember("service", Node.from("com.example.test#NonExistentService"))
        val context =
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build()

        assertThatThrownBy { ZodHttpClientSmithyPlugin().execute(context) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Service 'com.example.test#NonExistentService' not found in model")
    }

    private fun executePlugin(model: Model): MockManifest {
        val manifest = MockManifest()
        val settings =
            Node.objectNode()
                .withMember("service", Node.from("com.example.test#TestService"))
        val context =
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build()
        ZodHttpClientSmithyPlugin().execute(context)
        return manifest
    }

    private fun createSimpleTestModel(): Model =
        Model.builder()
            .addShape(ServiceShape.builder().id("com.example.test#TestService").version("1.0").build())
            .build()

    private fun createTestModelWithPathParameters(): Model {
        val itemIdMember =
            MemberShape.builder()
                .id("com.example.test#CreateItemInput\$itemId")
                .target("smithy.api#String")
                .addTrait(HttpLabelTrait())
                .addTrait(RequiredTrait())
                .build()
        val nameMember =
            MemberShape.builder()
                .id("com.example.test#CreateItemInput\$name")
                .target("smithy.api#String")
                .addTrait(RequiredTrait())
                .build()
        val headerMember =
            MemberShape.builder()
                .id("com.example.test#CreateItemInput\$requestId")
                .target("smithy.api#String")
                .addTrait(HttpHeaderTrait("X-Request-ID"))
                .build()
        val inputShape =
            StructureShape.builder()
                .id("com.example.test#CreateItemInput")
                .addMember(itemIdMember).addMember(nameMember).addMember(headerMember)
                .build()
        val operation =
            OperationShape.builder()
                .id("com.example.test#CreateItem")
                .input(inputShape.id)
                .addTrait(HttpTrait.builder().method("POST").uri(UriPattern.parse("/items/{itemId}")).build())
                .build()
        val service =
            ServiceShape.builder()
                .id("com.example.test#TestService").version("1.0")
                .addOperation(operation.id)
                .build()
        return Model.builder()
            .addShape(service).addShape(operation).addShape(inputShape)
            .addShape(itemIdMember).addShape(nameMember).addShape(headerMember)
            .addShape(StringShape.builder().id("smithy.api#String").build())
            .build()
    }

    private fun createTestModelWithDefaults(): Model {
        val itemIdMember =
            MemberShape.builder()
                .id("com.example.test#CreateItemInput\$itemId")
                .target("smithy.api#String")
                .addTrait(HttpLabelTrait()).addTrait(RequiredTrait())
                .build()
        val countMember =
            MemberShape.builder()
                .id("com.example.test#CreateItemInput\$count")
                .target("smithy.api#Integer")
                .addTrait(software.amazon.smithy.model.traits.DefaultTrait(Node.from(13)))
                .build()
        val metadataMember =
            MemberShape.builder()
                .id("com.example.test#CreateItemInput\$metadata")
                .target("smithy.api#String")
                .addTrait(software.amazon.smithy.model.traits.DefaultTrait(Node.objectNode()))
                .build()
        val nameMember =
            MemberShape.builder()
                .id("com.example.test#CreateItemInput\$name")
                .target("smithy.api#String")
                .addTrait(software.amazon.smithy.model.traits.DefaultTrait(Node.from("default-name")))
                .build()
        val inputShape =
            StructureShape.builder()
                .id("com.example.test#CreateItemInput")
                .addMember(itemIdMember).addMember(countMember).addMember(metadataMember).addMember(nameMember)
                .build()
        val operation =
            OperationShape.builder()
                .id("com.example.test#CreateItem")
                .input(inputShape.id)
                .addTrait(HttpTrait.builder().method("POST").uri(UriPattern.parse("/items/{itemId}")).build())
                .build()
        val service =
            ServiceShape.builder()
                .id("com.example.test#TestService").version("1.0")
                .addOperation(operation.id)
                .build()
        return Model.builder()
            .addShape(service).addShape(operation).addShape(inputShape)
            .addShape(itemIdMember).addShape(countMember).addShape(metadataMember).addShape(nameMember)
            .addShape(StringShape.builder().id("smithy.api#String").build())
            .addShape(IntegerShape.builder().id("smithy.api#Integer").build())
            .build()
    }

    private fun createTestModelWithHttpBoundIntegerWithRange(): Model {
        val maxResultsMember =
            MemberShape.builder()
                .id("com.example.test#SearchItemsInput\$maxResults")
                .target("com.example.test#BoundedInteger")
                .addTrait(software.amazon.smithy.model.traits.HttpQueryTrait("maxResults"))
                .addTrait(software.amazon.smithy.model.traits.DefaultTrait(Node.from(5)))
                .build()
        val boundedInteger =
            IntegerShape.builder()
                .id("com.example.test#BoundedInteger")
                .addTrait(RangeTrait.builder().min(java.math.BigDecimal.valueOf(1)).max(java.math.BigDecimal.valueOf(10)).build())
                .build()
        val inputShape =
            StructureShape.builder()
                .id("com.example.test#SearchItemsInput")
                .addMember(maxResultsMember)
                .build()
        val operation =
            OperationShape.builder()
                .id("com.example.test#SearchItems")
                .input(inputShape.id)
                .addTrait(HttpTrait.builder().method("GET").uri(UriPattern.parse("/search")).build())
                .build()
        val service =
            ServiceShape.builder()
                .id("com.example.test#TestService").version("1.0")
                .addOperation(operation.id)
                .build()
        return Model.builder()
            .addShape(service).addShape(operation).addShape(inputShape)
            .addShape(maxResultsMember).addShape(boundedInteger)
            .build()
    }

    private fun createTestModelWithHttpBoundNumberWithRange(): Model {
        val scoreMember =
            MemberShape.builder()
                .id("com.example.test#GetItemWithScoreInput\$score")
                .target("com.example.test#BoundedDouble")
                .addTrait(software.amazon.smithy.model.traits.HttpQueryTrait("score"))
                .build()
        val itemIdMember =
            MemberShape.builder()
                .id("com.example.test#GetItemWithScoreInput\$itemId")
                .target("smithy.api#String")
                .addTrait(HttpLabelTrait()).addTrait(RequiredTrait())
                .build()
        val boundedDouble =
            DoubleShape.builder()
                .id("com.example.test#BoundedDouble")
                .addTrait(RangeTrait.builder().min(java.math.BigDecimal.valueOf(0.0)).max(java.math.BigDecimal.valueOf(100.0)).build())
                .build()
        val inputShape =
            StructureShape.builder()
                .id("com.example.test#GetItemWithScoreInput")
                .addMember(scoreMember).addMember(itemIdMember)
                .build()
        val operation =
            OperationShape.builder()
                .id("com.example.test#GetItemWithScore")
                .input(inputShape.id)
                .addTrait(HttpTrait.builder().method("GET").uri(UriPattern.parse("/items/{itemId}")).build())
                .build()
        val service =
            ServiceShape.builder()
                .id("com.example.test#TestService").version("1.0")
                .addOperation(operation.id)
                .build()
        return Model.builder()
            .addShape(service).addShape(operation).addShape(inputShape)
            .addShape(scoreMember).addShape(itemIdMember).addShape(boundedDouble)
            .build()
    }

    private fun createTestModelWithoutInput(): Model {
        val operation = OperationShape.builder().id("com.example.test#GetItem").build()
        val service =
            ServiceShape.builder()
                .id("com.example.test#TestService").version("1.0")
                .addOperation(operation.id)
                .build()
        return Model.builder().addShape(service).addShape(operation).build()
    }

    private fun createTestModelWithOutput(): Model {
        val itemIdMember =
            MemberShape.builder()
                .id("com.example.test#CreateItemInput\$itemId")
                .target("smithy.api#String")
                .addTrait(HttpLabelTrait()).addTrait(RequiredTrait())
                .build()
        val inputShape =
            StructureShape.builder()
                .id("com.example.test#CreateItemInput")
                .addMember(itemIdMember)
                .build()
        val outputItemId =
            MemberShape.builder()
                .id("com.example.test#CreateItemOutput\$itemId")
                .target("smithy.api#String")
                .addTrait(RequiredTrait())
                .build()
        val outputShape =
            StructureShape.builder()
                .id("com.example.test#CreateItemOutput")
                .addMember(outputItemId)
                .build()
        val operation =
            OperationShape.builder()
                .id("com.example.test#CreateItem")
                .input(inputShape.id)
                .output(outputShape.id)
                .addTrait(HttpTrait.builder().method("POST").uri(UriPattern.parse("/items/{itemId}")).build())
                .build()
        val service =
            ServiceShape.builder()
                .id("com.example.test#TestService").version("1.0")
                .addOperation(operation.id)
                .build()
        return Model.builder()
            .addShape(service).addShape(operation)
            .addShape(inputShape).addShape(itemIdMember)
            .addShape(outputShape).addShape(outputItemId)
            .addShape(StringShape.builder().id("smithy.api#String").build())
            .build()
    }
}
