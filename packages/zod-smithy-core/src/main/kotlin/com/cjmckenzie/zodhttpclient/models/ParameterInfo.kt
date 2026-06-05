package com.cjmckenzie.zodhttpclient.models

import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape

data class ParameterInfo(
    val shape: Shape,
    val member: MemberShape?,
    val isRequired: Boolean,
)
