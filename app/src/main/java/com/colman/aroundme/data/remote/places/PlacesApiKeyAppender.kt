package com.colman.aroundme.data.remote.places

import com.colman.aroundme.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class PlacesApiKeyAppender : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = BuildConfig.MAPS_API_KEY
        require(apiKey.isNotBlank() && apiKey != "YOUR_API_KEY_HERE") {
            "Missing Google API key. Please set MAPS_API_KEY in local.properties"
        }

        val currentRequest = chain.request()
        val updatedUrl = currentRequest.url.newBuilder().apply {
            if (currentRequest.url.queryParameter("key").isNullOrBlank()) {
                addQueryParameter("key", apiKey)
            }
        }.build()

        val updatedRequest = currentRequest.newBuilder()
            .url(updatedUrl)
            .addHeader("accept", "application/json")
            .build()

        return chain.proceed(updatedRequest)
    }
}
