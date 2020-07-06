package com.baileytye.dataresource.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.baileytye.dataresource.networkBoundResource.DefaultNetworkErrorMapper
import com.baileytye.dataresource.model.ErrorMessagesResource
import com.baileytye.dataresource.networkBoundResource.NetworkErrorMapper
import com.baileytye.dataresource.model.NetworkResult
import java.io.IOException
import com.baileytye.dataresource.model.Result


suspend fun <T> safeApiCall(
    dispatcher: CoroutineDispatcher,
    apiBlock: suspend () -> T?,
    errorMessages: ErrorMessagesResource,
    timeout: Long = DEFAULT_NETWORK_TIMEOUT,
    networkErrorMapper: NetworkErrorMapper<T> = DefaultNetworkErrorMapper(
        errorMessages
    )
): NetworkResult<T?> {
    return withContext(dispatcher) {
        try {
            // throws TimeoutCancellationException
            withTimeout(timeout) {
                try {
                    NetworkResult.Success(apiBlock())
                } catch (throwable: Throwable) {
                        networkErrorMapper.mapNetworkError(throwable)
                }
            }
        } catch (throwable: Throwable) {
            when (throwable) {
                is TimeoutCancellationException -> {
                    val code = 408 // timeout error code
                    NetworkResult.GenericError(code, errorMessages.networkTimeout)
                }
                else -> {
                    NetworkResult.GenericError(
                        null,
                        errorMessages.unknown
                    )
                }
            }
        }
    }
}


suspend fun <T> safeCacheCall(
    dispatcher: CoroutineDispatcher,
    cacheBlock: suspend () -> T?,
    errorMessages: ErrorMessagesResource,
    timeout: Long = DEFAULT_CACHE_TIMEOUT
): Result<T?> {
    return withContext(dispatcher) {
        try {
            // throws TimeoutCancellationException
            withTimeout(timeout) {
                try {
                    Result.Success(cacheBlock())
                } catch (e: NotImplementedError) {
                    Result.Error(Exception(e.message), null)
                } catch (e: IOException) {
                    Result.Error(Exception(errorMessages.unknown), null)
                }
            }
        } catch (throwable: Throwable) {
            when (throwable) {
                is TimeoutCancellationException -> {
                    Result.Error(Exception(errorMessages.cacheTimeout), null)
                }
                else -> {
                    Result.Error(Exception(errorMessages.unknown), null)
                }
            }
        }
    }
}