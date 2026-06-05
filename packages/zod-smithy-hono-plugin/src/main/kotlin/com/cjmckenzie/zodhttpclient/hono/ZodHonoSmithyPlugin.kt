package com.cjmckenzie.zodhttpclient.hono

import com.cjmckenzie.zodhttpclient.core.OperationDescriptorGenerator
import com.google.auto.service.AutoService
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.build.SmithyBuildPlugin
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.utils.SmithyInternalApi
import java.util.logging.Logger

private const val PLUGIN_NAME = "zod-hono"

@AutoService(SmithyBuildPlugin::class)
class ZodHonoSmithyPlugin : SmithyBuildPlugin {
    private val logger = Logger.getLogger(ZodHonoSmithyPlugin::class.java.name)
    private val descriptorGenerator = OperationDescriptorGenerator()
    private val fileGenerator = HonoFileGenerator()

    override fun getName(): String = PLUGIN_NAME

    @SmithyInternalApi
    override fun execute(context: PluginContext) {
        logger.info("Starting Hono server generation")

        runCatching {
            with(context) {
                val serviceId = settings.expectStringMember("service").value
                val service =
                    model.getShape(ShapeId.from(serviceId))
                        .orElse(null) as? ServiceShape
                        ?: throw IllegalArgumentException("Service '$serviceId' not found in model.")

                val descriptors = descriptorGenerator.generateForService(model, service)

                descriptors.forEach { descriptor ->
                    fileManifest.writeFile("${descriptor.operationName}Input.ts", descriptor.inputSchema.render())
                    descriptor.outputSchema?.let { outputSchema ->
                        fileManifest.writeFile("${descriptor.operationName}Output.ts", outputSchema.render())
                    }
                }

                fileGenerator.generateHonoRouterFile(fileManifest, descriptors)
                fileGenerator.generateIndexFile(fileManifest, descriptors)
            }
            logger.info("Hono server generation completed successfully")
        }.onFailure { exception ->
            logger.severe("Error during Hono server generation: ${exception.message}")
            throw exception
        }
    }
}
