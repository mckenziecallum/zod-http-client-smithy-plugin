package com.cjmckenzie.zodhttpclient.analyzers

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ResourceShape
import software.amazon.smithy.model.shapes.ServiceShape

/**
 * Discovers operations in a Smithy model
 */
class OperationAnalyzer {
    fun getOperationsForService(
        model: Model,
        service: ServiceShape,
    ): List<OperationShape> {
        val operations = mutableListOf<OperationShape>()

        // Add direct service operations
        service.operations.mapNotNullTo(operations) { operationId ->
            model.getShape(operationId).orElse(null) as? OperationShape
        }

        // Add operations from service resources
        service.resources.forEach { resourceId ->
            model.getShape(resourceId).orElse(null)?.let { shape ->
                if (shape is ResourceShape) {
                    operations.addResourceOperations(model, shape)
                }
            }
        }

        return operations.distinct()
    }

    private fun MutableList<OperationShape>.addResourceOperations(
        model: Model,
        resource: ResourceShape,
    ) {
        // Add resource lifecycle operations
        listOf(
            resource.create,
            resource.read,
            resource.update,
            resource.delete,
            resource.list,
            resource.put,
        ).forEach { operationOptional ->
            operationOptional.ifPresent { operationId ->
                model.getShape(operationId).orElse(null)?.let {
                    add(it as OperationShape)
                }
            }
        }

        // Add collection and instance operations
        (resource.collectionOperations + resource.operations).mapNotNullTo(this) { operationId ->
            model.getShape(operationId).orElse(null) as? OperationShape
        }

        // Recursively process nested resources
        resource.resources.forEach { nestedResourceId ->
            model.getShape(nestedResourceId).orElse(null)?.let { shape ->
                if (shape is ResourceShape) {
                    addResourceOperations(model, shape)
                }
            }
        }
    }
}
