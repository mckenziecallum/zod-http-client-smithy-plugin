package com.cjmckenzie.zodhttpclient.models

import com.cjmckenzie.zodhttpclient.types.ZodType

/**
 * Represents a Smithy error shape with its HTTP status code and Zod schema fields.
 */
data class ErrorShapeInfo(
    val name: String,
    val httpStatusCode: Int,
    val fields: Map<String, ZodType>,
)
