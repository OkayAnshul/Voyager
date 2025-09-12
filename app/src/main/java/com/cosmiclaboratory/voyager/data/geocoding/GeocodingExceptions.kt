package com.cosmiclaboratory.voyager.data.geocoding

/** Thrown when a geocoding provider returns no usable result. */
class NoResultException(message: String) : Exception(message)

/** Thrown when a geocoding provider HTTP call fails. */
class ProviderHttpException(
    providerName: String,
    httpCode: Int
) : Exception("$providerName HTTP error: $httpCode")
