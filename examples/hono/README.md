# Hono Example

Use the Hono plugin when you want generated Hono routes backed by typed operation handlers.

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.cjmckenzie:zod-smithy-hono-plugin:1.0.3")
}
```

```json
{
  "version": "1.0",
  "plugins": {
    "zod-hono": {
      "service": "com.example#ExampleService"
    }
  }
}
```

Generated code exposes `createHonoRouter` and a `HonoHandlers` type. Your app supplies the handlers; generated code owns routing, request parsing, Zod validation, response validation, and Smithy error status mapping.

Handlers return flat modeled members, including header-bound members: `createGeneration` returns `{ id, location, traceId? }`. The router validates the generated `CreateGenerationResult` schema and sends `location` as the `Location` header, `traceId` as `X-Trace-ID`, and only `id` in the JSON body.

## Verify

```sh
./gradlew :hono-example:check
```

That task generates the Hono router and fetch/axios clients from `model/example-service.smithy`, installs the TypeScript dependencies with pnpm, typechecks the generated code, runs a smoke test against the generated Hono app, and runs a full-stack test that calls the Hono server through both generated clients. The 202 `CreateGeneration` operation verifies required `Location` and optional `X-Trace-ID` response headers, body/header separation, mixed header casing, and rejection of missing required headers.

```sh
./gradlew :hono-example:fullStackTest
```

The example also models `GetContent` as a bodyless 303 with required `Location` and optional `Cache-Control` headers. Its illustrative access check uses `X-Content-Access`; production applications should use their own authentication middleware. `GetContentInput` builds the request, while `getContent` is omitted from the generated JSON client. The full-stack test checks denied access, invalid redirect output, and following the redirect to SVG bytes served by a separate Hono route. Its manual redirect assertions run in Node; browsers do not expose `Location` in manual mode. See the [redirect/content contract](../../README.md#redirects-and-content-downloads).
