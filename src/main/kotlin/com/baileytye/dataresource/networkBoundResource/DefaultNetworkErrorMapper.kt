package com.baileytye.dataresource.networkBoundResource

import com.baileytye.dataresource.model.ErrorMessagesResource
import com.baileytye.dataresource.model.NetworkResult
import java.io.IOException

/**
 * A default network error mapper which maps IO exceptions as network errors, not implemented
 * as generic error, and everything else as 'unknown'
 */
class DefaultNetworkErrorMapper (private val errorMessages: ErrorMessagesResource) :
    NetworkErrorMapper {
    override fun mapNetworkError(throwable: Throwable): NetworkResult<Nothing> {
        return when (throwable) {
            is IOException -> NetworkResult.NetworkError
            is NotImplementedError -> NetworkResult.GenericError(null, throwable.message)
            else -> NetworkResult.GenericError(null, errorMessages.unknown)
        }
    }
}