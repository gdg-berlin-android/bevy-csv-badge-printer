package de.berlindroid.bevybadgeprinter.bevy

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.io.IOException

class LoggingInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val requestBuffer = Buffer()
        request.body?.writeTo(requestBuffer)
        val requestBody = requestBuffer.readUtf8()

        Log.i("HTTP_LOG", "--> Sending request ${request.method} ${request.url}")
        Log.i("HTTP_LOG", "--> Headers: ${request.headers}")
        Log.i("HTTP_LOG", "--> Body: $requestBody")

        val response = chain.proceed(request)

        val responseBody = response.peekBody(Long.MAX_VALUE).string()
        Log.i("HTTP_LOG", "<-- Received response ${response.code} from ${response.request.url}")
        Log.i("HTTP_LOG", "<-- Received headers: ${response.headers}")
        if (response.headers["content-type"] == "text/html") {
            Log.i("HTTP_LOG", "<-- Received Body: <<html>>")
        } else {
            Log.i("HTTP_LOG", "<-- Received Body: $responseBody")
        }

        return response
    }
}
