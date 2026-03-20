package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PipePipeBackupImporterService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.nio.file.Files

fun Route.restoreRoutes(restoreService: PipePipeBackupImporterService, authService: AuthService) {
    post("/restore/pipepipe") {
        call.withJwtAuth(authService) { userId ->
            val tmp = Files.createTempFile("pipepipe-backup-", ".zip")
            val multipart = call.receiveMultipart()
            var hasFile = false
            while (true) {
                val part = multipart.readPart() ?: break
                if (part is PartData.FileItem && part.name == "file") {
                    part.provider().toInputStream().use { input ->
                        Files.newOutputStream(tmp).use { output -> input.copyTo(output) }
                    }
                    hasFile = true
                }
                part.dispose()
            }
            if (!hasFile) {
                Files.deleteIfExists(tmp)
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing file part"))
            }
            val result = runCatching { restoreService.restore(userId, tmp) }.getOrElse {
                Files.deleteIfExists(tmp)
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Invalid PipePipe backup"))
            }
            call.respond(result)
        }
    }
}
