package routes

import data.TicketImageRepository
import io.ktor.http.ContentType
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.name

const val TICKET_IMAGE_FIELD_NAME = "images"
const val TICKET_IMAGE_MAX_BYTES = 2L * 1024L * 1024L
const val TICKET_IMAGE_MAX_COUNT = 3

data class TicketFormUpload(
    val fields: Map<String, String>,
    val images: List<PendingTicketImage>,
    val error: String? = null,
)

class PendingTicketImage(
    val filename: String,
    val contentType: String,
    val bytes: ByteArray,
) {
    val size: Long = bytes.size.toLong()
}

suspend fun ApplicationCall.receiveTicketFormUpload(): TicketFormUpload {
    val fields = mutableMapOf<String, String>()
    val images = mutableListOf<PendingTicketImage>()
    var error: String? = null

    receiveMultipart().forEachPart { part ->
        when (part) {
            is PartData.FormItem -> fields[part.name.orEmpty()] = part.value
            is PartData.FileItem -> {
                if (part.name == TICKET_IMAGE_FIELD_NAME && !part.originalFileName.isNullOrBlank()) {
                    val contentType = part.contentType ?: ContentType.Image.Any

                    if (!contentType.match(ContentType.Image.Any)) {
                        error = "Images must be PNG, JPG, GIF, WebP or another image format."
                    } else if (images.size >= TICKET_IMAGE_MAX_COUNT) {
                        error = "You can attach up to $TICKET_IMAGE_MAX_COUNT images."
                    } else {
                        val bytes = part.readBytesLimited(TICKET_IMAGE_MAX_BYTES)

                        if (bytes == null) {
                            error = "Each image must be 2 MB or smaller."
                        } else {
                            images +=
                                PendingTicketImage(
                                    filename = part.originalFileName.orEmpty().sanitizeFilename(),
                                    contentType = contentType.toString(),
                                    bytes = bytes,
                                )
                        }
                    }
                }
            }
            else -> Unit
        }

        part.dispose()
    }

    return TicketFormUpload(fields, images, error)
}

fun saveTicketImages(
    ticketID: Int,
    images: List<PendingTicketImage>,
) {
    val directory = Path.of("data", "ticket-images").createDirectories()

    images.forEach { image ->
        val extension = Path.of(image.filename).extension.ifBlank { "img" }
        val storedName = "$ticketID-${UUID.randomUUID()}.$extension"
        val target = directory.resolve(storedName)

        Files.write(target, image.bytes)

        TicketImageRepository.create(
            ticketID = ticketID,
            filename = image.filename,
            contentType = image.contentType,
            path = target.toString(),
            size = image.size,
        )
    }
}

private fun PartData.FileItem.readBytesLimited(maxBytes: Long): ByteArray? {
    val output = ByteArrayOutputStream()
    var total = 0L

    provider().use { input ->
        while (!input.endOfInput) {
            total += 1
            if (total > maxBytes) return null

            output.write(input.readByte().toInt())
        }
    }

    return output.toByteArray()
}

private fun String.sanitizeFilename(): String =
    Path
        .of(this)
        .fileName
        .name
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .ifBlank { "image" }
