package com.cjmckenzie.zodhttpclient.analyzers

import com.cjmckenzie.zodhttpclient.models.HttpBindingAnalysis
import com.cjmckenzie.zodhttpclient.models.ParameterInfo
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.HttpHeaderTrait
import software.amazon.smithy.model.traits.HttpLabelTrait
import software.amazon.smithy.model.traits.HttpPayloadTrait
import software.amazon.smithy.model.traits.HttpQueryTrait

/**
 * Analyzes HTTP bindings on operation input shapes to classify parameters
 */
class HttpBindingAnalyzer {
    fun analyzeHttpBindings(
        model: Model,
        inputShape: StructureShape,
    ): HttpBindingAnalysis {
        val pathParameters = mutableMapOf<String, ParameterInfo>()
        val queryParameters = mutableMapOf<String, ParameterInfo>()
        val headerParameters = mutableMapOf<String, ParameterInfo>()
        val bodyParameters = mutableMapOf<String, ParameterInfo>()

        inputShape.allMembers.forEach { (memberName, member) ->
            model.getShape(member.target).orElse(null)?.let { memberShape ->
                val parameterInfo = ParameterInfo(memberShape, member, member.isRequired)

                when {
                    member.hasTrait(HttpLabelTrait::class.java) ->
                        pathParameters[memberName] = parameterInfo

                    member.hasTrait(HttpQueryTrait::class.java) ->
                        queryParameters[memberName] = parameterInfo

                    member.hasTrait(HttpHeaderTrait::class.java) -> {
                        val headerName = member.getTrait(HttpHeaderTrait::class.java).get().value
                        headerParameters[headerName] = parameterInfo
                    }

                    member.hasTrait(HttpPayloadTrait::class.java) ->
                        throw IllegalArgumentException("@httpPayload trait is not supported in this plugin")

                    else -> bodyParameters[memberName] = parameterInfo
                }
            }
        }

        return HttpBindingAnalysis(
            pathParameters = pathParameters,
            queryParameters = queryParameters,
            headerParameters = headerParameters,
            bodyParameters = bodyParameters,
        )
    }
}
