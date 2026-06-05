package com.cjmckenzie.zodhttpclient.types

/**
 * Sealed class hierarchy representing Zod types in a type-safe manner.
 * Client-side variant: no string coercion, generates request/response objects.
 */
sealed class ZodType {
    abstract fun render(): String

    open fun getChildTypes(): List<ZodType> = emptyList()

    fun optional(): ZodType = Optional(this)

    fun nullish(): ZodType = Nullish(this)

    fun default(value: String): ZodType = Default(this, value)

    fun min(value: Number): ZodType = Min(this, value)

    fun max(value: Number): ZodType = Max(this, value)

    fun regex(pattern: String): ZodType = Regex(this, pattern, null)

    fun regex(
        pattern: String,
        message: String,
    ): ZodType = Regex(this, pattern, message)

    // Primitive types
    object StringType : ZodType() {
        override fun render(): String = "z.string()"
    }

    object NumberInt : ZodType() {
        override fun render(): String = "z.number().int()"
    }

    object NumberType : ZodType() {
        override fun render(): String = "z.number()"
    }

    object BooleanType : ZodType() {
        override fun render(): String = "z.boolean()"
    }

    object DateTime : ZodType() {
        override fun render(): String = "z.string().datetime()"
    }

    object Unknown : ZodType() {
        override fun render(): String = "z.unknown()"
    }

    object RecordUnknown : ZodType() {
        override fun render(): String = "z.record(z.string(), z.unknown())"
    }

    // Complex types
    data class Array(val elementType: ZodType) : ZodType() {
        override fun render(): String = "z.array(${elementType.render()})"

        override fun getChildTypes(): List<ZodType> = listOf(elementType)
    }

    data class Record(val valueType: ZodType) : ZodType() {
        override fun render(): String = "z.record(z.string(), ${valueType.render()})"

        override fun getChildTypes(): List<ZodType> = listOf(valueType)
    }

    data class Object(val fields: Map<String, ZodType>) : ZodType() {
        override fun render(): String {
            if (fields.isEmpty()) return "z.object({})"

            val fieldStrings =
                fields.map { (name, type) ->
                    val quotedName = if (name.needsQuoting()) "'$name'" else name
                    "  $quotedName: ${type.render()}"
                }

            return "z.object({\n${fieldStrings.joinToString(",\n")}\n})"
        }

        override fun getChildTypes(): List<ZodType> = fields.values.toList()

        private fun String.needsQuoting(): Boolean = !matches(Regex("^[a-zA-Z_$][a-zA-Z0-9_$]*$"))
    }

    data class Enum(val values: List<String>) : ZodType() {
        override fun render(): String {
            if (values.isEmpty()) return StringType.render()
            val quotedValues = values.map { "'$it'" }
            return "z.enum([${quotedValues.joinToString(", ")}])"
        }
    }

    data class Union(val variants: List<ZodType>) : ZodType() {
        override fun render(): String {
            if (variants.isEmpty()) return Unknown.render()
            return "z.union([${variants.joinToString(", ") { it.render() }}])"
        }

        override fun getChildTypes(): List<ZodType> = variants
    }

    data class Optional(val baseType: ZodType) : ZodType() {
        override fun render(): String = "${baseType.render()}.optional()"

        override fun getChildTypes(): List<ZodType> = listOf(baseType)
    }

    data class Nullish(val baseType: ZodType) : ZodType() {
        override fun render(): String = "${baseType.render()}.nullish()"

        override fun getChildTypes(): List<ZodType> = listOf(baseType)
    }

    data class Default(val baseType: ZodType, val defaultValue: String) : ZodType() {
        override fun render(): String = "${baseType.render()}.default($defaultValue)"

        override fun getChildTypes(): List<ZodType> = listOf(baseType)
    }

    data class Min(val baseType: ZodType, val minValue: Number) : ZodType() {
        override fun render(): String = "${baseType.render()}.min($minValue)"

        override fun getChildTypes(): List<ZodType> = listOf(baseType)
    }

    data class Max(val baseType: ZodType, val maxValue: Number) : ZodType() {
        override fun render(): String = "${baseType.render()}.max($maxValue)"

        override fun getChildTypes(): List<ZodType> = listOf(baseType)
    }

    data class Regex(val baseType: ZodType, val pattern: String, val message: String?) : ZodType() {
        override fun render(): String =
            if (message != null) {
                val escapedMessage = message.replace("\\", "\\\\").replace("'", "\\'")
                "${baseType.render()}.regex($pattern, { message: '$escapedMessage' })"
            } else {
                "${baseType.render()}.regex($pattern)"
            }

        override fun getChildTypes(): List<ZodType> = listOf(baseType)
    }

    // Transform: wraps a schema with .transform() for decomposition/flattening
    data class Transform(val baseType: ZodType, val transformBody: String) : ZodType() {
        override fun render(): String = "${baseType.render()}.transform((v) => ({\n$transformBody\n}))"

        override fun getChildTypes(): List<ZodType> = listOf(baseType)
    }

    // Raw code block — for inline TypeScript expressions that don't map to a Zod combinator
    data class RawCode(val code: String) : ZodType() {
        override fun render(): String = code
    }
}

/**
 * Builder functions for creating ZodTypes
 */
object ZodTypes {
    fun string() = ZodType.StringType

    fun numberInt() = ZodType.NumberInt

    fun number() = ZodType.NumberType

    fun boolean() = ZodType.BooleanType

    fun dateTime() = ZodType.DateTime

    fun unknown() = ZodType.Unknown

    fun recordUnknown() = ZodType.RecordUnknown

    fun array(elementType: ZodType) = ZodType.Array(elementType)

    fun record(valueType: ZodType) = ZodType.Record(valueType)

    fun obj(fields: Map<String, ZodType>) = ZodType.Object(fields)

    fun obj(vararg fields: Pair<String, ZodType>) = ZodType.Object(fields.toMap())

    fun enum(values: List<String>) = ZodType.Enum(values)

    fun enum(vararg values: String) = ZodType.Enum(values.toList())

    fun union(variants: List<ZodType>) = ZodType.Union(variants)

    fun union(vararg variants: ZodType) = ZodType.Union(variants.toList())

    fun transform(
        baseType: ZodType,
        transformBody: String,
    ) = ZodType.Transform(baseType, transformBody)

    fun raw(code: String) = ZodType.RawCode(code)
}
