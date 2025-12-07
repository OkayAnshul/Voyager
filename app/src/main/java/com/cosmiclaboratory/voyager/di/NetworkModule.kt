package com.cosmiclaboratory.voyager.di

import android.content.Context
import com.cosmiclaboratory.voyager.data.api.AndroidGeocoderService
import com.cosmiclaboratory.voyager.data.api.NominatimGeocodingService
import com.cosmiclaboratory.voyager.data.api.OverpassApiService
import com.cosmiclaboratory.voyager.data.api.OverpassApiServiceImpl
import com.cosmiclaboratory.voyager.data.api.RateLimiter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module for network-related dependencies
 * Provides OkHttp client and geocoding services
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Provides OkHttpClient for network requests
     * Configured with reasonable timeouts for geocoding APIs
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Provides Android Geocoder service (FREE, no API key)
     */
    @Provides
    @Singleton
    fun provideAndroidGeocoderService(
        @ApplicationContext context: Context
    ): AndroidGeocoderService {
        return AndroidGeocoderService(context)
    }

    /**
     * Provides Nominatim geocoding service (FREE OSM, rate limited)
     */
    @Provides
    @Singleton
    fun provideNominatimGeocodingService(
        okHttpClient: OkHttpClient
    ): NominatimGeocodingService {
        return NominatimGeocodingService(okHttpClient)
    }

    /**
     * Provides Ktor HttpClient for Overpass API
     * Configured with JSON serialization for API responses
     */
    @Provides
    @Singleton
    fun provideKtorHttpClient(okHttpClient: OkHttpClient): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    /**
     * Provides RateLimiter for API request throttling
     * Configured for 1 request per second (respectful usage)
     */
    @Provides
    @Singleton
    fun provideRateLimiter(): RateLimiter {
        return RateLimiter(minIntervalMs = 1000)
    }

    /**
     * Provides Overpass API service (FREE OSM POI queries)
     * Best source for real business names
     */
    @Provides
    @Singleton
    fun provideOverpassApiService(
        httpClient: HttpClient,
        rateLimiter: RateLimiter
    ): OverpassApiService {
        return OverpassApiServiceImpl(httpClient, rateLimiter)
    }
}
