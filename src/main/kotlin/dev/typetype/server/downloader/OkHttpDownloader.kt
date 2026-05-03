package dev.typetype.server.downloader

import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Request

class OkHttpDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    companion object {
        fun instance(): OkHttpDownloader = OkHttpDownloader(
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
    }

    override fun execute(request: ExtractorRequest): Response {
        val httpRequest = buildOkHttpRequest(request)
        val httpResponse = client.newCall(httpRequest).execute()

        if (httpResponse.code == 429) {
            throw ReCaptchaException("reCaptcha required", request.url())
        }

        val responseBodyBytes = httpResponse.body.bytes()
        val responseBody = responseBodyBytes.toString(Charsets.UTF_8)
        return Response(
            httpResponse.code,
            httpResponse.message,
            httpResponse.headers.toMultimap(),
            responseBody,
            responseBodyBytes,
            httpResponse.request.url.toString()
        )
    }

    override fun executeAsync(request: ExtractorRequest, callback: AsyncCallback): CancellableCall {
        val httpRequest = buildOkHttpRequest(request)
        val call: Call = client.newCall(httpRequest)
        val cancellableCall = CancellableCall(call)

        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: okhttp3.Response) {
                val responseBodyBytes = response.body.bytes()
                val responseBody = responseBodyBytes.toString(Charsets.UTF_8)
                val extractorResponse = Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    responseBody,
                    responseBodyBytes,
                    response.request.url.toString()
                )
                callback.onSuccess(extractorResponse)
                cancellableCall.setFinished()
            }

            override fun onFailure(call: Call, e: java.io.IOException) {
                callback.onError(e)
                cancellableCall.setFinished()
            }
        })

        return cancellableCall
    }

    private fun buildOkHttpRequest(request: ExtractorRequest): Request {
        val method = request.httpMethod()
        val dataToSend = request.dataToSend()
        val body = dataToSend?.toRequestBody()
            ?: if (method == "POST" || method == "PUT" || method == "PATCH") ByteArray(0).toRequestBody() else null
        val builder = Request.Builder()
            .url(request.url())
            .method(method, body)

        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }

        return builder.build()
    }
}
