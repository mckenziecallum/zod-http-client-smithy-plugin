# Zod Smithy TypeScript

Smithy build plugins that generate type-safe, Zod-validated TypeScript adapters from Smithy models. Point them at any `@restJson1` service and generate validated axios/fetch clients or Hono server routes.

## What It Generates

For a Smithy service with operations like `GetSpace`, `CreateSpace`, etc., the client plugin generates:

```
{Operation}Input.ts     — Zod schema that validates input and decomposes into HTTP parts
{Operation}Output.ts    — Zod schema that validates and flattens response data
errors.ts               — Typed exception classes from Smithy @error shapes
axios-client.ts         — Typed axios client (optional)
fetch-client.ts         — Typed fetch client (optional)
utils.ts                — ServiceError base class, fromAxios/fromFetch adapters
index.ts                — Barrel exports
```

The Hono plugin generates:

```
{Operation}Input.ts     — Zod schema that validates inbound HTTP data
{Operation}Output.ts    — Zod schema that validates handler output
hono-router.ts          — Hono router factory and typed handler interface
index.ts                — Barrel exports
```

## Quick Start

### 1. Add the plugin to your Smithy build

Publish this project to your local Maven repository:

```bash
gradle publishToMavenLocal
```

Then add GitHub Packages and the plugin dependency to the Smithy build classpath for the project that owns your model:

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

dependencies {
    implementation("com.cjmckenzie:zod-smithy-client-plugin:1.0.0")
}
```

For Hono server generation, use:

```kotlin
dependencies {
    implementation("com.cjmckenzie:zod-smithy-hono-plugin:1.0.0")
}
```

**smithy-build.json:**

```json
{
  "plugins": {
    "zod-client": {
      "service": "com.example#MyService",
      "client": ["axios"]
    }
  }
}
```

For Hono:

```json
{
  "plugins": {
    "zod-hono": {
      "service": "com.example#MyService"
    }
  }
}
```

### 2. Build

```bash
gradle build
```

Generated files appear in `build/smithyprojections/<package>/source/zod-client/`.

### 3. Use

```typescript
import { createAxiosClient, NotFoundException, ServiceError } from './generated';
import axios from 'axios';

const api = createAxiosClient(axios.create({ baseURL: 'https://api.example.com' }));

// Fully typed — input validated before request, response validated after
const space = await api.getSpace({ spaceId: 'as-abc123def' });
space.id       // string, validated
space.status   // enum, typed

// Typed error handling
try {
  await api.getSpace({ spaceId: 'as-nope' });
} catch (e) {
  if (e instanceof NotFoundException) {
    console.log(e.statusCode);  // 404
    console.log(e.message);     // "Space not found"
  }
}
```

## Configuration

The `"zod-client"` plugin accepts these settings in `smithy-build.json`:

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `service` | `string` | **required** | Fully qualified Smithy service shape ID |
| `client` | `string[]` | `["axios"]` | Which client wrappers to generate. Values: `"axios"`, `"fetch"` |

The `"zod-hono"` plugin accepts these settings in `smithy-build.json`:

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `service` | `string` | **required** | Fully qualified Smithy service shape ID |

### Examples

Generate only a fetch client:

```json
{
  "zod-client": {
    "service": "com.example#MyService",
    "client": ["fetch"]
  }
}
```

Generate both:

```json
{
  "zod-client": {
    "service": "com.example#MyService",
    "client": ["axios", "fetch"]
  }
}
```

## Usage Patterns

### Using the generated clients

The generated clients handle input validation, HTTP request construction, response validation, and error parsing in one call:

```typescript
// Axios
import { createAxiosClient } from './generated';
const api = createAxiosClient(axios.create({ baseURL: '...' }));
const result = await api.createItem({ name: 'Widget', itemType: 'gadget', itemId: 'abc-123' });

// Fetch
import { createFetchClient } from './generated';
const api = createFetchClient('https://api.example.com');
const result = await api.createItem({ name: 'Widget', itemType: 'gadget', itemId: 'abc-123' });
```

The fetch client accepts an optional `RequestInit` for default options (auth headers, etc.) and per-call overrides:

```typescript
const api = createFetchClient('https://api.example.com', {
  headers: { 'Authorization': 'Bearer ...' },
});

// Per-call override
const result = await api.getItem({ itemId: '123' }, { signal: AbortSignal.timeout(5000) });
```

### Using schemas directly

The schemas are independently usable — the clients are just convenience wrappers. Use the schemas directly when you need more control or are integrating with a different HTTP library:

```typescript
import { GetSpaceInput } from './generated/GetSpaceInput';
import { GetSpaceOutput } from './generated/GetSpaceOutput';
import { fromFetch } from './generated/utils';

// Input: flat object in → validated + decomposed
const req = GetSpaceInput.parse({ spaceId: 'as-abc123def' });
req.url      // '/v1/spaces/as-abc123def'
req.method   // 'GET'
req.path     // { spaceId: 'as-abc123def' }
req.headers  // {}
req.query    // {}
req.body     // {}

// Make the request however you want
const response = await fetch(baseUrl + req.url, { method: req.method });

// Output: response parts in → flat validated object
const space = GetSpaceOutput.parse(await fromFetch(response));
space.id     // typed, validated
```

### Error handling

The plugin generates typed exception classes from Smithy `@error` shapes. Each error class:

- Extends `ServiceError` (which extends `Error`)
- Has a `_kind` discriminant for exhaustive switch matching
- Supports `instanceof` checks for simple branching
- Carries `statusCode`, `body`, and `operationName`

```typescript
import { NotFoundException, BadRequestException, ServiceError } from './generated';

try {
  await api.getSpace({ spaceId: 'as-nope' });
} catch (e) {
  // instanceof — clean type narrowing
  if (e instanceof NotFoundException) {
    e.statusCode   // 404
    e.message      // string
    e._kind        // 'NotFoundException'
  } else if (e instanceof BadRequestException) {
    e.statusCode   // 400
  } else if (e instanceof ServiceError) {
    // Catch-all for any HTTP error
    e.statusCode   // number
    e.body         // unknown (raw response)
    e._kind        // string
    e.operationName // which operation failed
  }

  // Or use switch for exhaustive matching
  if (e instanceof ServiceError) {
    switch (e._kind) {
      case 'NotFoundException': break;
      case 'BadRequestException': break;
      case 'InternalServiceException': break;
      default: // UnknownError
    }
  }
}
```

### Using with React Query

The schemas integrate naturally with React Query / TanStack Query:

```typescript
import { GetSpaceInput } from './generated/GetSpaceInput';
import { GetSpaceOutput } from './generated/GetSpaceOutput';
import { fromAxios } from './generated/utils';
import { queryOptions } from '@tanstack/react-query';

export const getSpaceQuery = (spaceId: string) =>
  queryOptions({
    queryKey: ['space', spaceId],
    queryFn: async () => {
      const req = GetSpaceInput.parse({ spaceId });
      const res = await axios({ method: req.method, url: req.url });
      return GetSpaceOutput.parse(fromAxios(res));
    },
  });
```

Or with the generated client:

```typescript
import { createAxiosClient } from './generated';

const api = createAxiosClient(axiosInstance);

export const getSpaceQuery = (spaceId: string) =>
  queryOptions({
    queryKey: ['space', spaceId],
    queryFn: () => api.getSpace({ spaceId }),
  });
```

## How It Works

### Input schemas

The user provides a flat object with all fields using native TypeScript types. The Zod schema validates everything (constraints, patterns, ranges, enums) and then a `.transform()` decomposes the validated data into HTTP parts:

```typescript
// User provides:
{ spaceId: 'as-abc123def', oboUser: 'someone' }

// Schema validates and transforms to:
{
  spaceId: 'as-abc123def',
  oboUser: 'someone',
  path: { spaceId: 'as-abc123def' },
  headers: { 'x-on-behalf-of': 'someone' },
  query: {},
  body: {},
  url: '/v1/spaces/as-abc123def',
  method: 'GET',
}
```

The decomposition is driven by Smithy HTTP binding traits (`@httpLabel`, `@httpQuery`, `@httpHeader`).

### Output schemas

The user passes in the raw response parts. The schema validates each part according to Smithy HTTP bindings and flattens everything into a single typed object — the consumer doesn't need to know which field came from the body vs. a header:

```typescript
// fromAxios/fromFetch normalizes to:
{ body: { id: '...', status: 'LAUNCHED' }, headers: { 'x-request-id': '123' }, statusCode: 200 }

// Schema validates and flattens to:
{ id: '...', status: 'LAUNCHED', requestId: '123', statusCode: 200 }
```

### Error matching

Error shapes are matched by HTTP status code. When an HTTP error occurs, the client tries each error schema against the response body, paired with the expected status code from `@httpError`. If a match is found, the corresponding typed exception is thrown. Otherwise, a generic `ServiceError` with `_kind: 'UnknownError'` is thrown.

## Smithy Trait Support

| Trait | How it's used |
|-------|--------------|
| `@http` | Extracts method and URI template for request construction |
| `@httpLabel` | Maps field to URL path parameter |
| `@httpQuery` | Maps field to query string parameter |
| `@httpHeader` | Maps field to HTTP header (input and output) |
| `@required` | Field is required in the Zod schema |
| `@default` | Applies `.default()` in the Zod schema |
| `@length` | Applies `.min()` / `.max()` on strings and arrays |
| `@range` | Applies `.min()` / `.max()` on numbers |
| `@pattern` | Applies `.regex()` with the pattern |
| `@error` | Generates a typed exception class |
| `@httpError` | Sets the HTTP status code for error matching |
| `enum` | Generates `z.enum([...])` |
| `union` | Generates `z.union([...])` |
| `list` | Generates `z.array(...)` |
| `map` | Generates `z.record(z.string(), ...)` |
| `structure` | Generates `z.object({...})` |
| `timestamp` | Generates `z.string().datetime()` |
| `document` | Generates `z.record(z.string(), z.unknown())` |

## Creating a Client Package

To publish the generated code as a consumable npm package:

### Package structure

```
MyServiceAxiosClient/
├── smithy-build.json         # zod-client plugin config
├── build.gradle.kts          # smithy build -> copy schemas -> npm install -> tsc
├── settings.gradle.kts
└── npm-config/
    ├── package.json
    └── tsconfig.json
```

### build.gradle.kts

```kotlin
plugins {
    id("smithy-model-package-plugin")
}

tasks {
    val copyZodSchemas by registering(Copy::class) {
        dependsOn("smithyBuild")
        from(layout.buildDirectory.dir("smithyprojections/my-service/source/zod-client"))
        into(layout.buildDirectory.dir("zod-client/src"))
    }

    val copyNpmConfig by registering(Copy::class) {
        from("npm-config")
        into(layout.buildDirectory.dir("zod-client"))
    }

    val npmInstall by registering(Exec::class) {
        dependsOn(copyZodSchemas, copyNpmConfig)
        workingDir(layout.buildDirectory.dir("zod-client"))
        commandLine("npx", "npm", "install")
    }

    val npmBuild by registering(Exec::class) {
        dependsOn(npmInstall)
        workingDir(layout.buildDirectory.dir("zod-client"))
        commandLine("npx", "npm", "run", "build")
    }

    assemble {
        dependsOn(npmBuild)
    }
}
```

## Development

### Build and test

```bash
gradle build          # Full build with tests
gradle test           # Tests only
```

### Project structure

```
packages/
├── zod-smithy-core/                # Smithy analysis and Zod schema generation
├── zod-smithy-client-plugin/       # zod-client axios/fetch Smithy plugin
└── zod-smithy-hono-plugin/         # zod-hono Hono server Smithy plugin
examples/
├── client/                         # Client plugin config example
└── hono/                           # Hono plugin config and handler example
```
