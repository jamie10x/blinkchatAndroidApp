package com.jamie.blinkchat.data.remote.interceptor

import com.jamie.blinkchat.repositories.TokenStorageService
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStorageService: TokenStorageService
) : Interceptor {

    companion object {
        private val NO_AUTH_PATHS = setOf(
            "/api/v1/auth/login",
            "/api/v1/auth/register"
        )
    }

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()
        val requestPath = originalRequest.url.encodedPath

        val requiresAuth = NO_AUTH_PATHS.none { noAuthPath ->
            requestPath.endsWith(noAuthPath, ignoreCase = true)
        }

        if (!requiresAuth) {
            Timber.d("AuthInterceptor: No auth needed for path $requestPath")
            return chain.proceed(originalRequest)
        }

        val token = runBlocking {
            tokenStorageService.getAuthToken().firstOrNull()
        }

        Timber.d("AuthInterceptor: Token for $requestPath: ${if (token != null) "Present" else "Absent"}")

        if (token.isNullOrBlank()) {
            Timber.w("AuthInterceptor: No token found for authenticated request to $requestPath")
            return chain.proceed(originalRequest)
        }

        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()


        return chain.proceed(newRequest)
    }
}