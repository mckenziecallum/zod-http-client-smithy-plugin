$version: "2"
namespace com.example.test

service TestService {
    version: "1.0"
    operations: [CreateItem, GetItem, GetItemStatus, SearchItems, GetVersion]
}

@error("client")
@httpError(400)
structure BadRequestException {
    message: String
}

@error("client")
@httpError(404)
structure NotFoundException {
    message: String
}

@error("server")
@httpError(500)
structure InternalServiceException {
    message: String
}

@http(method: "POST", uri: "/items/{itemType}/{itemId}")
operation CreateItem {
    input := {
        @required
        @httpLabel
        itemType: String

        @required
        @httpLabel
        itemId: ResourceIdentifier

        @httpHeader("X-Request-ID")
        requestId: String

        @httpQuery("version")
        version: String

        @required
        name: BoundedName

        description: String

        metadata: Document

        status: ItemStatus

        tags: ItemTags

        priority: Priority

        @default(true)
        enabled: Boolean

        @default("draft")
        stage: String

        @default(0)
        retryCount: Integer
    }

    output := {
        @required
        itemId: String

        @required
        status: ItemStatus

        @required
        createdAt: String

        @httpHeader("X-Request-ID")
        requestId: String
    }

    errors: [BadRequestException, NotFoundException, InternalServiceException]
}

@http(method: "GET", uri: "/items/{itemId}")
operation GetItem {
    input := {
        @required
        @httpLabel
        itemId: String

        @httpQuery("includeMetadata")
        includeMetadata: Boolean
    }

    output := {
        @required
        itemId: String

        @required
        name: String

        description: String
    }
}

@http(method: "GET", uri: "/items/{itemId}/status/{enabled}")
operation GetItemStatus {
    input := {
        @required
        @httpLabel
        itemId: String

        @required
        @httpLabel
        enabled: Boolean

        @httpQuery("limit")
        limit: Integer

        @httpQuery("resourceId")
        resourceId: ResourceIdentifier

        @httpQuery("priority")
        priority: Priority

        @httpHeader("X-Retry-Count")
        retryCount: Integer
    }
}

@http(method: "GET", uri: "/search")
operation SearchItems {
    input := {
        @httpQuery("maxResults")
        @default(10)
        maxResults: Priority

        @httpQuery("enabled")
        @default(true)
        enabled: Boolean

        @httpHeader("X-Page-Size")
        @default(25)
        pageSize: Integer
    }
}

@http(method: "GET", uri: "/version/{versionId}")
operation GetVersion {
    input := {
        @required
        @httpLabel
        versionId: VersionIdentifier
    }
}

// String with length and pattern constraints (like Nebula ResourceIdentifier)
@length(min: 1, max: 64)
@pattern("^[a-zA-Z0-9_-]+$")
string ResourceIdentifier

// String with escaped pattern (like version identifier)
@pattern("^([a-zA-Z0-9_-]+|\\$latest)$")
string VersionIdentifier

// String with just length constraint
@length(min: 1, max: 128)
string BoundedName

// Number with range constraint
@range(min: 1, max: 10)
integer Priority

enum ItemStatus {
    ACTIVE = "Active"
    INACTIVE = "Inactive"
    PENDING = "Pending"
}

union ItemTags {
    simple: String,
    complex: ComplexTag
}

structure ComplexTag {
    key: String,
    value: String,
    metadata: Document
}
