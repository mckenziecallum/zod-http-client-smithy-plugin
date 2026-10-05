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

## Verify

```sh
./gradlew :hono-example:check
```

That task generates the Hono router and fetch client from `model/example-service.smithy`, installs the TypeScript dependencies with pnpm, typechecks the generated code, runs a smoke test against the generated Hono app, and runs a full-stack test that calls the Hono server through the generated client.

```sh
./gradlew :hono-example:fullStackTest
```

The example also models `GetContent` as a bodyless 303 with required `Location` and optional `Cache-Control` headers. Its illustrative access check uses `X-Content-Access`; production applications should use their own authentication middleware. `GetContentInput` builds the request, while `getContent` is omitted from the generated JSON client. The full-stack test checks denied access, invalid redirect output, and following the redirect to SVG bytes served by a separate Hono route. Its manual redirect assertions run in Node; browsers do not expose `Location` in manual mode. See the [redirect/content contract](../../README.md#redirects-and-content-downloads).
