package com.cjmckenzie.zodhttpclient.models

data class HttpBindingAnalysis(
    val pathParameters: Map<String, ParameterInfo>,
    val queryParameters: Map<String, ParameterInfo>,
    val headerParameters: Map<String, ParameterInfo>,
    val bodyParameters: Map<String, ParameterInfo>,
)
