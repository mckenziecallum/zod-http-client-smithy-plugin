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

## Examples

The Hono example is executable and verifies both server-only and full-stack behavior.

```sh
./gradlew :hono-example:check
./gradlew :hono-example:fullStackTest
```

`fullStackTest` generates the Hono server and fetch client from the same Smithy model, starts a real Hono HTTP server, calls it through the generated fetch client, and validates the response end to end.

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
