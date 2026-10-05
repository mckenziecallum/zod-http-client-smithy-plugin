# Zod Smithy TypeScript

Smithy build plugins for generating Zod-validated TypeScript from Smithy services.

This repository publishes two Smithy plugins:

- `com.cjmckenzie:zod-smithy-client-plugin` generates axios and fetch clients.
- `com.cjmckenzie:zod-smithy-hono-plugin` generates Hono server routes and typed handlers.

Shared Smithy analysis and schema generation lives in `com.cjmckenzie:zod-smithy-core`.

## Packages

```kotlin
dependencies {
    implementation("com.cjmckenzie:zod-smithy-client-plugin:1.0.3")
    implementation("com.cjmckenzie:zod-smithy-hono-plugin:1.0.3")
}
```

Artifacts are published to GitHub Packages:

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/mckenziecallum/zod-http-client-smithy-plugin")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
            password = providers.gradleProperty("gpr.key").orNull
        }
    }
}
```

For local testing:

```sh
./gradlew publishToMavenLocal
```

Local builds default to version `1.0.0-SNAPSHOT`. CI publishes `1.0.0-ci.<run_number>.<run_attempt>` from `main`, and release tags like `v1.0.3` publish stable version `1.0.3`.

## Client Plugin

Use `zod-client` when you want generated axios or fetch clients from a Smithy service.

```json
{
  "version": "1.0",
  "plugins": {
    "zod-client": {
      "service": "com.example#ExampleService",
      "client": ["axios", "fetch"]
    }
  }
}
```

The `client` setting defaults to `["axios"]`. Supported values are `"axios"` and `"fetch"`.

Generated files:

```text
{Operation}Input.ts
{Operation}Output.ts
errors.ts
axios-client.ts
fetch-client.ts
utils.ts
index.ts
```

Client usage:

```ts
import { createFetchClient } from './generated/index.js';

const api = createFetchClient('https://api.example.com');
const item = await api.getItem({ itemId: 'abc-123' });
```

The generated clients validate flat operation input before sending the request, construct the Smithy HTTP request, validate the HTTP response, and throw typed `ServiceError` subclasses for modeled Smithy errors.

## Hono Plugin

Use `zod-hono` when you want generated Hono routes backed by typed operation handlers.

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

Generated files:

```text
{Operation}Input.ts
{Operation}Output.ts
{Operation}Result.ts
hono-router.ts
index.ts
```

Server usage:

```ts
import { serve } from '@hono/node-server';
import { createHonoRouter, type HonoHandlers } from './generated/index.js';

const handlers: HonoHandlers = {
  async getItem(input) {
    return {
      itemId: input.path.itemId,
      name: 'Example item',
    };
  },
};

serve({
  fetch: createHonoRouter(handlers).fetch,
  port: 3000,
});
```

The generated router owns HTTP routing, request parsing, Zod input validation, output validation, and modeled Smithy error status mapping.

Handlers return a flat object using Smithy member names, including members bound with `@httpHeader`. Each operation with output exports a `{Operation}Result` schema and `{Operation}ResultType` for this handler API. The router validates that result, emits body members as JSON, and emits header members under their declared HTTP names. Required header members must be present; optional headers are omitted when undefined. A missing or invalid required output member produces HTTP 500.

For an operation with `@http(code: 202)` and a required `@httpHeader("Location") location: String` output member:

```ts
const handlers: HonoHandlers = {
  async createGeneration(input) {
    return { id: input.id, location: `/generations/${input.id}` };
  },
};
```

The response has status 202, JSON body `{ "id": "gen_123" }`, and `Location: /generations/gen_123`. Generated fetch and axios clients expose `{ id, location, statusCode }` and decode response header names case-insensitively. `{Operation}OutputType` describes that decoded client response, including its optional `statusCode`; `{Operation}ResultType` describes the handler's modeled result.

Throw a modeled error using `_kind` (or `name`) matching the Smithy error shape and include its required members. The router validates the error and serializes modeled fields, stripping unmodeled properties, including those in nested structures. It preserves `_kind` and, for `aws.protocols#restJson1` services, adds the `__type` discriminator for Smithy client interoperability. Invalid modeled errors return a generic 500 response; request validation errors retain their existing 400 responses.

## Examples

Unexpected handler exceptions return HTTP 500 with a fixed `InternalServerError` message and kind. Each response includes a generated `X-Correlation-ID` header. Configure `onUnexpectedError` to log the original exception alongside its ID and Hono request context:

```ts
const app = createHonoRouter(handlers, {
  onUnexpectedError(error, { correlationId, context }) {
    logger.error({ error, correlationId, path: context.req.path });
  },
});
```

The hook may be asynchronous; hook failures do not change the public response. Modeled public errors retain their configured HTTP status, and request validation still returns HTTP 400. Handler and output validation failures are treated as unexpected exceptions.

The Hono example is executable and verifies both server-only and full-stack behavior.

```sh
./gradlew :hono-example:check
./gradlew :hono-example:fullStackTest
```

`fullStackTest` generates the Hono server and fetch client from the same Smithy model, starts a real Hono HTTP server, calls it through the generated fetch client, and validates the response end to end. `:hono-example:test` also generates a Smithy Kotlin 1.7.4 client and verifies that it decodes a modeled error and its custom members from the Hono server. Both run as part of `:hono-example:check`.

Example projects:

```text
examples/
├── client/   # zod-client smithy-build.json example
└── hono/     # runnable Hono and full-stack example
```

## Smithy Support

Supported Smithy features include:

| Smithy feature | Generated behavior |
| --- | --- |
| `@http` | HTTP method and URI template |
| `@httpLabel` | Path parameter binding |
| `@httpQuery` | Query string binding |
| `@httpHeader` | Header binding |
| `@required` | Required Zod field |
| `@default` | Zod default |
| `@length` | String/list min and max validation |
| `@range` | Number min and max validation |
| `@pattern` | Regex validation |
| `@error` / `@httpError` | Typed service errors and status mapping |
| `enum` | `z.enum(...)` |
| `union` | `z.union(...)` |
| `list` | `z.array(...)` |
| `map` | `z.record(...)` |
| `structure` | `z.object(...)` |
| `timestamp` | `z.string().datetime()` |
| `document` | `z.record(z.string(), z.unknown())` |

## Development

```sh
./gradlew build
./gradlew publishToMavenLocal
```

Project layout:

```text
packages/
├── zod-smithy-core/
├── zod-smithy-client-plugin/
└── zod-smithy-hono-plugin/
examples/
├── client/
└── hono/
```

The GitHub Actions workflow builds on pull requests and publishes packages on pushes to `main` and release tags.
