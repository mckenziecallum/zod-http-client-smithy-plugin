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

The generated router owns HTTP routing, request parsing, Zod input validation, output validation, and modeled Smithy error status mapping. Scalar query and header bindings decode decimal integers, finite decimal numbers, and exact `true`/`false` values before native Zod validation. Malformed values return 400; constraints, requiredness, and defaults still apply. Declared query wire names are used by both servers and clients. Client inputs and JSON body fields retain native types.

Throw a modeled error using `_kind` (or `name`) matching the Smithy error shape and include its required members. The router validates the error and serializes modeled fields, stripping unmodeled properties, including those in nested structures. It preserves `_kind` and, for `aws.protocols#restJson1` services, adds the `__type` discriminator for Smithy client interoperability. Invalid modeled errors return a generic 500 response; request validation errors retain their existing 400 responses.

## Redirects and content downloads

Model a bodyless redirect with `@http(code: 301 | 302 | 303 | 307 | 308)` and an output containing only `@httpHeader` members. A required string `Location` header is mandatory. Smithy normally expects success codes in the 2xx range; suppress `HttpResponseCodeSemantics` on this operation to opt into this plugin's redirect contract:

```smithy
@suppress(["HttpResponseCodeSemantics"])
@readonly
@http(method: "GET", uri: "/content/{contentId}", code: 303)
operation GetContent {
    input := {
        @required
        @httpLabel
        contentId: String
    }
    output := {
        @required
        @httpHeader("Location")
        location: ContentLocation

        @httpHeader("Cache-Control")
        cacheControl: String
    }
}

@length(min: 1)
string ContentLocation
```

Return the modeled output from a typed Hono handler after checking access:

```ts
const handlers: HonoHandlers = {
  async getContent(input, c) {
    await checkAccess(c, input.path.contentId);
    return {
      location: await createShortLivedContentUrl(input.path.contentId),
      cacheControl: 'no-store',
    };
  },
};
```

The router validates the output, emits modeled headers, and returns the modeled redirect status with an empty body. Invalid output returns a generic 500. Ordinary JSON operations still validate their output; modeled response-header members are emitted as headers and excluded from the JSON body. Handlers return modeled data; returning a native `Response` is not a supported escape hatch. For custom SVG, ZIP, streaming, or other non-JSON responses, register a separate Hono route outside the generated router. The redirect target may be such a route or an external content store. Access checks, target URL policy, and URL expiry belong to the application.

Both generated clients omit methods for modeled redirect operations, while still exporting their input and output schemas. This also applies to services containing only redirects. Build the endpoint URL with its input schema:

```ts
const request = GetContentInput.parse({ contentId: 'abc-123' });
const url = new URL(request.url, 'https://api.example.com');
Object.entries(request.query).forEach(([key, value]) => {
  if (value !== undefined) url.searchParams.set(key, String(value));
});

// For a GET endpoint authenticated by cookies, navigate to download/view content:
window.location.assign(url.href);

// Or fetch the final content, using the endpoint's authentication requirements:
const response = await fetch(url, {
  method: request.method,
  headers: request.headers,
  credentials: 'include',
  redirect: 'follow',
});
if (!response.ok) throw new Error(`Content request failed: ${response.status}`);
const content = await response.blob();
```

Navigation cannot attach arbitrary authentication headers. Browser fetch must satisfy CORS and credential policies at the API and final content origin. Browser `redirect: 'manual'` yields an `opaqueredirect` response with status 0 and no readable headers/body, including `Location`; exposing `Location` through CORS does not bypass this filtering. Node's manual fetch can expose redirect headers, but that behavior must not be assumed in browsers. See the [Fetch Standard](https://fetch.spec.whatwg.org/#concept-filtered-response-opaque-redirect).

Generated methods for ordinary operations remain JSON clients: fetch follows redirects by default unless request options override it, and modeled outputs are parsed as JSON and validated. Do not model a content redirect as a 200 JSON operation. Redirect bodies, missing/optional/non-string `Location`, and other 3xx success codes fail at generation time. `@httpPayload` is unsupported for inputs and outputs and fails at generation time; raw/binary content and dynamic response modes are not supported by generated JSON routes or client methods. No response mode is inferred from a string/blob shape or a `Content-Type` header.

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
