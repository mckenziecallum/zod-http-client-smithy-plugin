package com.cjmckenzie.zodhttpclient.extensions

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import java.util.Optional

fun Model.getShapeOrNull(shapeId: ShapeId): Shape? = getShape(shapeId).takeIf { it.isPresent }?.get()

fun Optional<Shape>.orNull(): Shape? = takeIf { it.isPresent }?.get()

inline fun <T, R> Optional<T>.ifPresent(block: (T) -> R): R? = takeIf { it.isPresent }?.let { block(it.get()) }

inline fun <T, R> Optional<T>.mapIfPresent(transform: (T) -> R): R? = takeIf { it.isPresent }?.let { transform(it.get()) }
