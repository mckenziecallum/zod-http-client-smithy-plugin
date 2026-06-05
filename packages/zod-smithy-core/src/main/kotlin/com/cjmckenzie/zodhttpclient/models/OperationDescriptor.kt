package com.cjmckenzie.zodhttpclient.models

import com.cjmckenzie.zodhttpclient.builders.TypeScriptSchema

data class OperationDescriptor(
    val operationName: String,
    val methodName: String,
    val httpMethod: String,
    val uri: String,
    val successStatusCode: Int,
    val inputSchema: TypeScriptSchema,
    val outputSchema: TypeScriptSchema?,
    val inputBindings: HttpBindingAnalysis,
    val outputBindings: HttpBindingAnalysis?,
    val errors: List<ErrorShapeInfo>,
)
