# Client Example

Use the client plugin when you want generated axios or fetch clients from a Smithy service.

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.cjmckenzie:zod-smithy-client-plugin:1.0.3")
}
```

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

Generated code exposes `createAxiosClient` and/or `createFetchClient` plus Zod input/output schemas.
