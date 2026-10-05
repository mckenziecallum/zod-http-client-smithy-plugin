$version: "2"
namespace com.example.hono

use aws.protocols#restJson1

@restJson1
service ExampleService {
    version: "1.0"
    operations: [GetItem, Upload, GetContent]
}

@http(method: "POST", uri: "/matches/{matchId}/events", code: 200)
operation Upload {
    input := {
        @required
        @httpLabel
        matchId: String

        @required
        @httpHeader("X-Request-ID")
        requestId: String

        @required
        @httpHeader("X-Tenant-ID")
        tenantId: String

        @httpHeader("X-Trace-ID")
        traceId: String

        @httpQuery("source-channel")
        source: String

        @required
        events: EventList
    }

    output := {
        @required
        matchId: String

        @required
        requestId: String

        @required
        tenantId: String

        traceId: String

        source: String

        @required
        events: EventList
    }
}

list EventList {
    member: String
}

@http(method: "GET", uri: "/items/{itemId}", code: 200)
operation GetItem {
    errors: [MissingSequence, ItemNotFound]
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

@error("client")
@httpError(409)
structure MissingSequence {
    @required
    message: String
    @required
    expectedSequence: Integer
    @required
    receivedSequence: Integer
    details: ErrorDetails
}

structure ErrorDetails {
    reason: String
}

@error("client")
@httpError(404)
structure ItemNotFound {
    @required
    message: String
}

// Content is served by the redirect target, outside the generated JSON API.
@suppress(["HttpResponseCodeSemantics"])
@readonly
@http(method: "GET", uri: "/content/{contentId}", code: 303)
operation GetContent {
    errors: [ItemNotFound]
    input := {
        @required
        @httpLabel
        contentId: String

        @required
        @httpHeader("X-Content-Access")
        accessToken: String
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
