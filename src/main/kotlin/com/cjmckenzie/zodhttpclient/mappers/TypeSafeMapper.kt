package com.cjmckenzie.zodhttpclient.mappers

import com.cjmckenzie.zodhttpclient.extensions.mapIfPresent
import com.cjmckenzie.zodhttpclient.types.ZodType
import com.cjmckenzie.zodhttpclient.types.ZodTypes
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.EnumShape
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.DefaultTrait
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.EnumValueTrait

/**
 * Type-safe mapper that converts Smithy shapes to ZodType sealed classes.
 * Client-side: always uses native types (no string coercion).
 */
class TypeSafeMapper(
    private val constraintMapper: TypeSafeConstraintMapper = TypeSafeConstraintMapper(),
) {
    fun mapShapeToZodType(
        model: Model,
        shape: Shape,
    ): ZodType {
        return when (shape.type) {
            ShapeType.STRING -> ZodTypes.string()
            ShapeType.INTEGER, ShapeType.LONG, ShapeType.SHORT, ShapeType.BYTE -> ZodTypes.numberInt()
            ShapeType.FLOAT, ShapeType.DOUBLE -> ZodTypes.number()
            ShapeType.BOOLEAN -> ZodTypes.boolean()
            ShapeType.TIMESTAMP -> ZodTypes.dateTime()
            ShapeType.DOCUMENT -> ZodTypes.recordUnknown()
            ShapeType.LIST -> generateListType(model, shape as ListShape)
            ShapeType.MAP -> generateMapType(model, shape as MapShape)
            ShapeType.STRUCTURE -> generateInlineStructureType(model, shape as StructureShape)
            ShapeType.UNION -> generateInlineUnionType(model, shape as UnionShape)
            ShapeType.ENUM -> generateEnumType(shape)
            else -> ZodTypes.unknown()
        }
    }

    private fun generateListType(
        model: Model,
        listShape: ListShape,
    ): ZodType =
        model.getShape(listShape.member.target)
            .mapIfPresent { memberShape ->
                ZodTypes.array(mapShapeToZodType(model, memberShape))
            } ?: ZodTypes.array(ZodTypes.unknown())

    private fun generateMapType(
        model: Model,
        mapShape: MapShape,
    ): ZodType =
        model.getShape(mapShape.value.target)
            .mapIfPresent { valueShape ->
                ZodTypes.record(mapShapeToZodType(model, valueShape))
            } ?: ZodTypes.record(ZodTypes.unknown())

    private fun generateEnumType(shape: Shape): ZodType =
        when {
            shape.hasTrait(EnumTrait::class.java) -> generateEnumTypeFromTrait(shape)
            shape is EnumShape -> generateEnumTypeFromShape(shape)
            else -> ZodTypes.string()
        }

    private fun generateInlineStructureType(
        model: Model,
        structure: StructureShape,
    ): ZodType {
        val fields =
            structure.allMembers.mapNotNull { (memberName, member) ->
                createFieldFromMember(model, memberName, member)
            }.toMap()

        return ZodTypes.obj(fields)
    }

    private fun createFieldFromMember(
        model: Model,
        memberName: String,
        member: MemberShape,
    ): Pair<String, ZodType>? {
        return model.getShape(member.target)
            .mapIfPresent { memberShape ->
                val baseType = mapShapeToZodType(model, memberShape)
                val constrainedType = constraintMapper.applyFieldLevelConstraints(baseType, member, memberShape)
                val fieldType = determineFieldType(constrainedType, member, memberShape)
                memberName to fieldType
            }
    }

    private fun determineFieldType(
        constrainedType: ZodType,
        member: MemberShape,
        memberShape: Shape,
    ): ZodType {
        return when {
            member.isRequired -> constrainedType
            member.hasTrait(DefaultTrait::class.java) -> {
                val defaultTrait = member.getTrait(DefaultTrait::class.java).get()
                val defaultValue = constraintMapper.formatDefaultValue(defaultTrait, memberShape)
                constrainedType.default(defaultValue)
            }
            else -> constrainedType.optional()
        }
    }

    private fun generateInlineUnionType(
        model: Model,
        union: UnionShape,
    ): ZodType {
        val variants =
            union.allMembers.mapNotNull { (memberName, member) ->
                model.getShape(member.target)
                    .mapIfPresent { memberShape ->
                        ZodTypes.obj(memberName to mapShapeToZodType(model, memberShape))
                    }
            }

        return when {
            variants.size > 1 -> ZodTypes.union(variants)
            variants.size == 1 -> variants.first()
            else -> ZodTypes.unknown()
        }
    }

    private fun generateEnumTypeFromTrait(shape: Shape): ZodType {
        val enumTrait = shape.getTrait(EnumTrait::class.java).get()
        val enumValues = enumTrait.values.map { it.value }

        return if (enumValues.isNotEmpty()) {
            ZodTypes.enum(enumValues)
        } else {
            ZodTypes.string()
        }
    }

    private fun generateEnumTypeFromShape(enumShape: EnumShape): ZodType {
        val members = enumShape.allMembers
        if (members.isEmpty()) return ZodTypes.string()

        val enumValues =
            members.map { (memberName, member) ->
                member.getTrait(EnumValueTrait::class.java)
                    .mapIfPresent { it.stringValue.orElse(memberName) }
                    ?: memberName
            }

        return ZodTypes.enum(enumValues)
    }
}
