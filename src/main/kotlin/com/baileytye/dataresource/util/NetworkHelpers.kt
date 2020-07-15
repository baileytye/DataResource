/*
 *  Copyright [2020] [Bailey Tye]
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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


/**
 * Performs a safe api which catches errors and returns a result wrapped in a [NetworkResult] object.
 * @param dispatcher dispatcher to run call on
 * @param apiBlock block to retrieve result
 * @param errorMessages messages to display in case of error
 * @param timeout timeout in ms
 * @param networkErrorMapper mapper to handle errors, by default the errors handled are: IOException, NotImplementedException,
 *                           others are returned as generic errors.
 */
suspend fun <T> safeApiCall(
    dispatcher: CoroutineDispatcher,
    apiBlock: suspend () -> T?,
    errorMessages: ErrorMessagesResource,
    timeout: Long = DEFAULT_NETWORK_TIMEOUT,
    networkErrorMapper: NetworkErrorMapper = DefaultNetworkErrorMapper(
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


/**
 * Performs a safe cache call and returns a result wrapped in a [Result] object
 * @param dispatcher dispatcher to run call on
 * @param cacheBlock block to retrieve result
 * @param errorMessages messages to display in case of error
 * @param timeout timeout in ms
 */
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