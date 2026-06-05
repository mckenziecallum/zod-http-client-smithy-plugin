$version: "2"
namespace com.example.hono

service ExampleService {
    version: "1.0"
    operations: [GetItem]
}

@http(method: "GET", uri: "/items/{itemId}", code: 200)
operation GetItem {
    input := {
        @required
        @httpLabel
        itemId: String
    }

    output := {
        @required
        itemId: String

        @required
        name: String
    }
}
