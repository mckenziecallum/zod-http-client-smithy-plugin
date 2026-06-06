# Hono Example

Use the Hono plugin when you want generated Hono routes backed by typed operation handlers.

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.cjmckenzie:zod-smithy-hono-plugin:1.0.0")
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
