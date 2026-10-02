package com.cjmckenzie.examples.hono

import aws.smithy.kotlin.runtime.client.endpoints.Endpoint
import aws.smithy.kotlin.runtime.net.url.Url
import com.example.hono.client.ExampleClient
import com.example.hono.client.endpoints.ExampleEndpointProvider
import com.example.hono.client.model.GetItemRequest
import com.example.hono.client.model.MissingSequence
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class ModeledErrorInteropTest {
    @Test
    fun `generated Kotlin client decodes modeled Hono errors`() {
        val server =
            ProcessBuilder("node", "--import", "tsx", "src/kotlin-test-server.ts")
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        try {
            val endpoint =
                CompletableFuture.supplyAsync { server.inputReader().readLine() }
                    .get(30, TimeUnit.SECONDS)
            ExampleClient { endpointProvider = ExampleEndpointProvider { Endpoint(Url.parse(endpoint)) } }.use { client ->
                runBlocking {
                    val error =
                        assertFailsWith<MissingSequence> {
                            client.getItem(GetItemRequest { itemId = "missing-sequence" })
                        }
                    assertEquals("Expected event 1 but received 3", error.message)
                    assertEquals(1, error.expectedSequence)
                    assertEquals(3, error.receivedSequence)
                    assertEquals("out of order", error.details?.reason)
                }
            }
        } finally {
            server.destroy()
            if (!server.waitFor(5, TimeUnit.SECONDS)) server.destroyForcibly()
        }
    }
}
