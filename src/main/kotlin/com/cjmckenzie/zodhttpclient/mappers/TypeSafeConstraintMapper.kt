package com.cjmckenzie.zodhttpclient.mappers

import com.cjmckenzie.zodhttpclient.types.ZodType
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.traits.DefaultTrait
import software.amazon.smithy.model.traits.LengthTrait
import software.amazon.smithy.model.traits.PatternTrait
import software.amazon.smithy.model.traits.RangeTrait
import java.util.Optional

/**
 * Type-safe constraint mapper that applies Smithy constraints to ZodType instances.
 * Client-side: no pipe/coercion handling needed.
 */
class TypeSafeConstraintMapper {
    fun applyFieldLevelConstraints(
        baseType: ZodType,
        member: MemberShape,
        targetShape: Shape,
    ): ZodType =
        baseType.applyMemberLengthConstraints(member, targetShape)
            .applyMemberPatternConstraints(member, targetShape)
            .applyMemberRangeConstraints(member, targetShape)

    fun formatDefaultValue(
        defaultTrait: DefaultTrait,
        targetShape: Shape,
    ): String {
        val node = defaultTrait.toNode()

        return when {
            node.isStringNode -> "\"${node.expectStringNode().value}\""
            node.isBooleanNode -> node.expectBooleanNode().value.toString()
            node.isNumberNode -> node.expectNumberNode().value.toString()
            node.isObjectNode -> {
                val objectNode = node.expectObjectNode()
                if (objectNode.isEmpty) "{}" else objectNode.toString()
            }
            node.isArrayNode -> node.expectArrayNode().toString()
            else -> {
                when (targetShape.type) {
                    ShapeType.STRING -> "\"$node\""
                    ShapeType.BOOLEAN -> node.toString()
                    ShapeType.INTEGER, ShapeType.LONG, ShapeType.SHORT, ShapeType.BYTE,
                    ShapeType.FLOAT, ShapeType.DOUBLE,
                    -> node.toString()
                    else -> "\"$node\""
                }
            }
        }
    }

    private fun ZodType.applyMemberLengthConstraints(
        member: MemberShape,
        targetShape: Shape,
    ): ZodType {
        val memberTrait = member.getTrait(LengthTrait::class.java)
        val shapeTrait = targetShape.getTrait(LengthTrait::class.java)

        val trait =
            when {
                memberTrait.isPresent -> memberTrait.get()
                shapeTrait.isPresent -> shapeTrait.get()
                else -> return this
            }

        return this.applyMinConstraint(trait.min)
            .applyMaxConstraint(trait.max)
    }

    private fun ZodType.applyMemberPatternConstraints(
        member: MemberShape,
        targetShape: Shape,
    ): ZodType {
        val memberTrait = member.getTrait(PatternTrait::class.java)
        val shapeTrait = targetShape.getTrait(PatternTrait::class.java)

        val trait =
            when {
                memberTrait.isPresent -> memberTrait.get()
                shapeTrait.isPresent -> shapeTrait.get()
                else -> return this
            }

        val pattern = trait.pattern.pattern()
        val escapedPattern = pattern.replace(Regex("(?<!\\\\)/"), "\\\\/")
        val errorMessage = "Must match the required format: $pattern"
        return this.regex("/$escapedPattern/", errorMessage)
    }

    private fun ZodType.applyMemberRangeConstraints(
        member: MemberShape,
        targetShape: Shape,
    ): ZodType {
        val memberTrait = member.getTrait(RangeTrait::class.java)
        val shapeTrait = targetShape.getTrait(RangeTrait::class.java)

        val trait =
            when {
                memberTrait.isPresent -> memberTrait.get()
                shapeTrait.isPresent -> shapeTrait.get()
                else -> return this
            }

        return this.applyMinConstraint(trait.min)
            .applyMaxConstraint(trait.max)
    }

    private fun ZodType.applyMinConstraint(min: Optional<out Number>): ZodType = if (min.isPresent) this.min(min.get()) else this

    private fun ZodType.applyMaxConstraint(max: Optional<out Number>): ZodType = if (max.isPresent) this.max(max.get()) else this
}
