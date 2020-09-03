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

package com.baileytye.dataresource.networkBoundResource

import com.baileytye.dataresource.model.DefaultErrorMessages
import com.baileytye.dataresource.model.ErrorMessagesResource
import com.baileytye.dataresource.model.NetworkResult
import com.baileytye.dataresource.model.Result
import com.baileytye.dataresource.util.DEFAULT_NETWORK_TIMEOUT
import com.baileytye.dataresource.util.safeApiCall
import com.baileytye.dataresource.util.safeCacheCall
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield


/**
 * Network resource to help direct the flow of data from network to a local cache, and then to the UI.
 * For detailed description of data flow, refer to github readme.
 *
 * Inspiration : https://github.com/mitchtabian/Open-API-Android-App
 */

class NetworkBoundResource<Network, Local> internal constructor(
    /**
     * Mapper used to convert between local and network data models
     */
    val mapper: Mapper<Network, Local>,

    /**
     * Network fetch block. This is the main source of data. Can be null in which case local
     * flow fetch will be used as the main data source instead.
     */
    val networkFetchBlock: (suspend () -> Network)?,

    /**
     * Local flow fetch block, after a successful network call, and a successful local cache,
     * data is emitted from this source. This is usually a room database source since a cache
     * will automatically emit a new value. When a network block is defined and this is null,
     * the network data will be mapped to local and returned through oneShotOperation, or result.
     */
    val localFlowFetchBlock: (suspend () -> Flow<Local>)?,

    /**
     * Local cache block to save a successful network call.
     */
    val localCacheBlock: (suspend (Local) -> Unit)?,

    /**
     * Contains options that can be general for many different resources
     */
    val options: Options

) {

    data class Options(
        /**
         * Coroutine dispatcher used to run the resource methods
         */
        val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,

        /**
         * Specifies whether local data should be emitted in the event of a network error. Only valid
         * if a local flow fetch block is defined.
         */
        val showDataOnError: Boolean = false,

        /**
         * Specifies whether a loading result should be returned while waiting for the network
         * fetch block to complete. If false and a local flow fetch block is defined, that data will emit
         * while waiting for the network block to complete. Once the network block completes and is cached, a
         * new value will be emitted (if using room for flow fetch).
         */
        val showLoading: Boolean = true,

        /**
         * Error messages used for generic error types. For more control over error messages, implement
         * [NetworkErrorMapper] and return the results you want through [NetworkResult.GenericError].
         */
        val errorMessages: ErrorMessagesResource = DefaultErrorMessages(),

        /**
         * Network timeout used for network calls.
         */
        val networkTimeout: Long = DEFAULT_NETWORK_TIMEOUT,

        /**
         * Network error mapper used to map network errors to a [NetworkResult] object which is returned from
         * the internal safe network call.
         */
        val networkErrorMapper: NetworkErrorMapper = DefaultNetworkErrorMapper(
            errorMessages
        ),

        /**
         * Logging interceptor called on each [Result.Error] emitted from [getFlowResult] with the
         * error message given to the block.
         */
        val loggingInterceptor: ((String) -> Unit)? = null
    )


    /**
     * Builder to construct NetworkBoundResources.
     *
     * To create a network bound resource, use this class to assign the required parameters for
     * your use case. When ready, call .build() to construct the resource.
     */
    class Builder<Network, Local> private constructor(
        private val mapper: Mapper<Network, Local>,
        private var networkFetchBlock: (suspend () -> Network)? = null,
        private var localFlowFetchBlock: (suspend () -> Flow<Local>)? = null,
        private var localCacheBlock: (suspend (Local) -> Unit)? = null,
        private var options: Options = Options()
    ) {

        constructor(mapper: Mapper<Network, Local>) : this(
            mapper,
            networkFetchBlock = null,
            localFlowFetchBlock = null,
            localCacheBlock = null,
            options = Options()
        )


        /**
         * Sets network fetch block that will be executed
         */
        fun networkFetchBlock(networkFetchBlock: suspend () -> Network) =
            apply { this.networkFetchBlock = networkFetchBlock }

        /**
         * Sets local flow fetch block, typically this is a flow from a Room database that will
         * emit new values when they are changed
         */
        fun localFlowFetchBlock(localFlowFetchBlock: suspend () -> Flow<Local>) =
            apply { this.localFlowFetchBlock = localFlowFetchBlock }

        /**
         * Sets cache block executed when network returns a success
         * @param localCacheBlock passes data converted to local object from network response
         */
        fun localCacheBlock(localCacheBlock: (suspend (Local) -> Unit)?) =
            apply { this.localCacheBlock = localCacheBlock }

        /**
         * Sets the options for the network bound resource
         */
        fun options(options: Options) =
            apply { this.options = options }


        /**
         * Builds the NetworkBoundResource
         */
        fun build() = NetworkBoundResource(
            mapper = this.mapper,
            networkFetchBlock = this.networkFetchBlock,
            localFlowFetchBlock = this.localFlowFetchBlock,
            localCacheBlock = this.localCacheBlock,
            options = this.options
        )
    }

    /**
     * Execute a one shot operation, cannot show loading or data on error since it is only a suspend
     * function and not a flow.
     */
    suspend fun oneShotOperation(): Result<Local> = withContext(options.coroutineDispatcher) {

        val result: Result<Local> = if (networkFetchBlock != null) {
            val networkResponse: NetworkResult<Network?> =
                safeApiCall(
                    dispatcher = options.coroutineDispatcher,
                    apiBlock = networkFetchBlock,
                    errorMessages = options.errorMessages,
                    timeout = options.networkTimeout,
                    networkErrorMapper = options.networkErrorMapper
                )
            when (networkResponse) {
                is NetworkResult.Success -> {
                    if (networkResponse.value == null) {
                        Result.Error(Exception(options.errorMessages.unknown))
                    } else {
                        //If there's a local save block, save and emit local, otherwise emit network
                        localCacheBlock?.let { cacheBlock ->
                            cacheBlock(mapper.networkToLocal(networkResponse.value))
                        }
                        (Result.Success(mapper.networkToLocal(networkResponse.value)))
                    }
                }
                is NetworkResult.GenericError -> {
                    (Result.Error(Exception(networkResponse.errorMessage)))
                }
                NetworkResult.NetworkError -> {
                    (Result.Error(Exception(options.errorMessages.genericNetwork)))
                }
            }

        } else {
            Result.Error(
                MissingArgumentException(
                    "No data requested"
                ), null
            )
        }

        if (result is Result.Error) {
            result.exception.message?.let { options.loggingInterceptor?.invoke(it) }
        }
        result
    }

    /**
     * Get the flow of results/errors of the network bound resource. Call this to retrieve network data
     * with loading, and local cached results.
     */
    fun getFlowResult(): Flow<Result<Local>> = flow {

        suspend fun emitLocalIfNotNull(e: Exception) {
            localFlowFetchBlock?.let { localFlow ->
                if (flowFromFetchIsEmpty()) {
                    emit(Result.Error(e, null))
                } else {
                    localFlow().collect { value ->
                        emit(Result.Error(e, value))
                    }
                }
            } ?: emit(Result.Error(e, null))
        }

        //Emit loading if requested
        if (options.showLoading) {
            emit(Result.Loading)
        } else {
            val local = getLocalIfAvailable()
            local?.let {
                emit(Result.Success(it))
            }
        }

        //Attempt network call if defined
        if (networkFetchBlock != null) {
            val networkResponse: NetworkResult<Network?> =
                safeApiCall(
                    dispatcher = options.coroutineDispatcher,
                    apiBlock = networkFetchBlock,
                    errorMessages = options.errorMessages,
                    timeout = options.networkTimeout,
                    networkErrorMapper = options.networkErrorMapper
                )
            yield() //Not sure if this is needed, the safeApiCall may do it as it returns since it's a suspend function

            when (networkResponse) {
                is NetworkResult.Success -> {
                    if (networkResponse.value == null) {
                        emit(Result.Error(Exception(options.errorMessages.unknown)))
                    } else {
                        //If there's a local save block, save and emit local, otherwise emit network
                        localCacheBlock?.let { cacheBlock ->
                            cacheBlock(mapper.networkToLocal(networkResponse.value))

                            //If there's a local fetch block emit those values, otherwise emit network
                            localFlowFetchBlock?.let { localFlow ->
                                localFlow().collect { value ->
                                    emit(Result.Success(value))
                                }
                            } ?: emit(Result.Success(mapper.networkToLocal(networkResponse.value)))
                        } ?: emit(Result.Success(mapper.networkToLocal(networkResponse.value)))
                    }
                }
                is NetworkResult.GenericError -> {
                    if (options.showDataOnError) {
                        emitLocalIfNotNull(Exception(networkResponse.errorMessage))
                    } else {
                        emit(Result.Error(Exception(networkResponse.errorMessage)))
                    }
                }
                NetworkResult.NetworkError -> {
                    if (options.showDataOnError) {
                        emitLocalIfNotNull(Exception(options.errorMessages.genericNetwork))
                    } else {
                        emit(Result.Error(Exception(options.errorMessages.genericNetwork)))
                    }
                }
            }
        } else if (localFlowFetchBlock != null) {   //No network call defined, attempt local instead
            val cacheResponse = safeCacheCall(
                dispatcher = options.coroutineDispatcher,
                cacheBlock = localFlowFetchBlock,
                errorMessages = options.errorMessages
            )
            yield()
            when (cacheResponse) {
                is Result.Success -> localFlowFetchBlock.let { localFlow ->
                    localFlow().collect {
                        emit(Result.Success(it))
                    }
                }
                is Result.Error -> emit(Result.Error(cacheResponse.exception))
                Result.Loading -> emit(Result.Error(Exception(options.errorMessages.unknown)))
            }
        } else {
            emit(
                Result.Error(
                    MissingArgumentException(
                        "No data requested"
                    )
                )
            )
        }
    }
        .flowOn(options.coroutineDispatcher)
        .onEach { result ->
            if (result is Result.Error) {
                result.exception.message?.let { options.loggingInterceptor?.invoke(it) }
            }
        }

    /**
     * Check if local flow fetch block is defined and if so return the first value emitted
     */
    private suspend fun getLocalIfAvailable(): Local? {
        localFlowFetchBlock?.let {
            return try {
                val result = it().first()
                if ((result as? List<*>) == null) result
                else {
                    if ((result as List<*>).isEmpty()) null
                    else result
                }
            } catch (e: NoSuchElementException) {
                null
            }
        }
        return null
    }

    /**
     * Check if flow fetch block returns an empty value
     */
    private suspend fun flowFromFetchIsEmpty(): Boolean {
        localFlowFetchBlock?.let {
            return try {
                val result = it().first()
                if ((result as? List<*>) == null) result == null else (result as List<*>).isEmpty()
            } catch (e: NoSuchElementException) {
                true
            }
        }
        return true
    }

    /**
     * Exception thrown when neither a network, or local block is defined
     */
    class MissingArgumentException(m: String) : Exception(m)
}