$version: "2"
namespace com.example.hono

use aws.protocols#restJson1

@restJson1
service ExampleService {
    version: "1.0"
    operations: [GetItem, Upload, InspectBindings, CreateGeneration]
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

        bodyCount: Integer

        bodyEnabled: Boolean
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

@range(min: 1, max: 100)
integer PageLimit

@http(method: "GET", uri: "/bindings", code: 200)
operation InspectBindings {
    input := {
        @required
        @httpQuery("limit")
        limit: PageLimit

        @required
        @httpQuery("include-archived")
        includeArchived: Boolean

        @required
        @httpHeader("X-Retry-Count")
        retryCount: Integer

        @httpQuery("offset")
        offset: Integer

        @httpQuery("ratio")
        ratio: Double

        @httpHeader("X-Enabled")
        enabled: Boolean

        @default(7)
        @httpQuery("page-size")
        pageSize: PageLimit

        @default(true)
        @httpHeader("X-Use-Cache")
        useCache: Boolean
    }

    output := {
        @required
        limit: Integer
        @required
        includeArchived: Boolean
        @required
        retryCount: Integer
        offset: Integer
        ratio: Double
        enabled: Boolean
        @required
        pageSize: Integer
        @required
        useCache: Boolean
    }
}

@http(method: "POST", uri: "/generations", code: 202)
operation CreateGeneration {
    input := {
        @required
        id: String
    }
    output := {
        @required
        id: String

        @required
        @httpHeader("Location")
        location: String

        @httpHeader("X-Trace-ID")
        traceId: String
    }
}
