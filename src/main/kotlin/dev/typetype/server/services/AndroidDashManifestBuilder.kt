package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import java.io.StringWriter
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

internal object AndroidDashManifestBuilder {
    fun build(
        sessionId: String,
        generation: Long,
        audio: AndroidDashTrack,
        video: AndroidDashTrack,
        durationMs: Long,
    ): String {
        val output = StringWriter()
        val xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output)
        xml.writeStartDocument("UTF-8", "1.0")
        xml.writeStartElement("MPD")
        xml.writeDefaultNamespace(DASH_NAMESPACE)
        xml.writeAttribute("profiles", "urn:mpeg:dash:profile:full:2011")
        xml.writeAttribute("type", "static")
        xml.writeAttribute("mediaPresentationDuration", duration(durationMs))
        xml.writeAttribute("minBufferTime", "PT2S")
        xml.writeStartElement("Period")
        xml.writeAttribute("id", "0")
        xml.writeAttribute("start", "PT0S")
        xml.writeTrack(sessionId, generation, video)
        xml.writeTrack(sessionId, generation, audio)
        xml.writeEndElement()
        xml.writeEndElement()
        xml.writeEndDocument()
        xml.close()
        return output.toString()
    }

    private fun XMLStreamWriter.writeTrack(
        sessionId: String,
        generation: Long,
        track: AndroidDashTrack,
    ) {
        val format = track.format
        val (mimeType, codecs) = splitMime(format.mimeType.orEmpty())
        writeStartElement("AdaptationSet")
        writeAttribute("id", format.itag.toString())
        writeAttribute("contentType", if (format.isAudio) "audio" else "video")
        writeAttribute("mimeType", mimeType)
        writeAttribute("segmentAlignment", "true")
        writeAttribute("startWithSAP", "1")
        writeStartElement("Representation")
        writeAttribute("id", format.itag.toString())
        writeAttribute("bandwidth", format.bitrate.coerceAtLeast(1).toString())
        if (codecs.isNotBlank()) writeAttribute("codecs", codecs)
        if (format.isVideo) {
            writeAttribute("width", format.width.coerceAtLeast(1).toString())
            writeAttribute("height", format.height.coerceAtLeast(1).toString())
        }
        writeSegmentTemplate(sessionId, generation, track)
        writeEndElement()
        writeEndElement()
    }

    private fun XMLStreamWriter.writeSegmentTemplate(
        sessionId: String,
        generation: Long,
        track: AndroidDashTrack,
    ) {
        val format = track.format
        val base = "/api/android/youtube/playback/$sessionId/${format.itag}"
        val query = "?session=$sessionId&generation=$generation"
        writeStartElement("SegmentTemplate")
        writeAttribute("timescale", "1000")
        writeAttribute("startNumber", track.timeline.startNumber.toString())
        writeAttribute("initialization", "$base/init$query")
        writeAttribute("media", "$base/segment/\$Number\$$query")
        writeStartElement("SegmentTimeline")
        compressed(track.timeline.segments).forEach { segment ->
            writeEmptyElement("S")
            writeAttribute("t", segment.startMs.toString())
            writeAttribute("d", segment.durationMs.toString())
            if (segment.repeat > 0) writeAttribute("r", segment.repeat.toString())
        }
        writeEndElement()
        writeEndElement()
    }

    private fun compressed(segments: List<AndroidDashTimelineSegment>): List<CompressedSegment> {
        val result = ArrayList<CompressedSegment>()
        for (segment in segments) {
            val previous = result.lastOrNull()
            if (previous != null && previous.durationMs == segment.durationMs && previous.endMs == segment.startMs) {
                previous.repeat++
            } else {
                result += CompressedSegment(segment.startMs, segment.durationMs)
            }
        }
        return result
    }

    private fun duration(ms: Long): String = "PT${ms / 1_000}.${(ms % 1_000).toString().padStart(3, '0')}S"

    private class CompressedSegment(val startMs: Long, val durationMs: Long, var repeat: Int = 0) {
        val endMs: Long get() = startMs + durationMs * (repeat + 1L)
    }

    private const val DASH_NAMESPACE = "urn:mpeg:dash:schema:mpd:2011"
}

internal data class AndroidDashTrack(
    val format: YoutubeSabrFormat,
    val timeline: AndroidDashTimeline,
)
