package NetworkBoundResource

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.yield
import model.*
import util.safeApiCall
import util.safeCacheCall


/*
 * Inspiration : https://github.com/mitchtabian/Open-API-Android-App
 * TODO: Missing one feature I want to add: if showLoading is set to false, and you want to display local
 * while the network is fetching, currently it doesn't emit anything
 */

class NetworkBoundResource<Network, Local> internal constructor(
    val mapper: Mapper<Network, Local>,
    val coroutineDispatcher: CoroutineDispatcher,
    val networkFetchBlock: (suspend () -> Network)?,
    val localFlowFetchBlock: (suspend () -> Flow<Local>)?,
    val localCacheBlock: (suspend (Local) -> Unit)?,
    val showDataOnError: Boolean,
    val showLoading: Boolean,
    val errorMessages: ErrorMessagesResource,
    val networkTimeout: Long,
    val networkErrorMapper: NetworkErrorMapper<Network>,
    val loggingInterceptor : ((String) -> Unit)?
) {

    /**
     * Execute a one shot operation, cannot show loading or data on error since it is only a suspend
     * function and not a flow.
     */
    suspend fun oneShotOperation(): Result<Local> {

        if (networkFetchBlock != null) {
            val networkResponse: NetworkResult<Network?> = safeApiCall(
                dispatcher = coroutineDispatcher,
                apiBlock = networkFetchBlock,
                errorMessages = errorMessages,
                timeout = networkTimeout,
                networkErrorMapper = networkErrorMapper
            )
            when (networkResponse) {
                is NetworkResult.Success -> {
                    if (networkResponse.value == null) {
                        return Result.Error(Exception(errorMessages.unknown))
                    } else {
                        //If there's a local save block, save and emit local, otherwise emit network
                        localCacheBlock?.let { cacheBlock ->
                            cacheBlock(mapper.networkToLocal(networkResponse.value))
                        }
                        return (Result.Success(mapper.networkToLocal(networkResponse.value)))
                    }
                }
                is NetworkResult.GenericError -> {
                    return (Result.Error(Exception(networkResponse.errorMessage)))
                }
                NetworkResult.NetworkError -> {
                    return (Result.Error(Exception(errorMessages.genericNetwork)))
                }
            }

        } else {
            return Result.Error(MissingArgumentException("No data requested"), null)
        }
    }

    /**
     * Get the flow of results/errors of the network bound resource. Call this to retrieve network data
     * with loading, and local cached results.
     */
    fun getFlowResult() : Flow<Result<Local>> = flow {

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
        if (showLoading) {
            emit(Result.Loading)
        } else {
            val local = getLocalIfAvailable()
            local?.let {
                emit(Result.Success(it))
            }
        }

        //Attempt network call if defined
        if (networkFetchBlock != null) {
            val networkResponse: NetworkResult<Network?> = safeApiCall(
                dispatcher = coroutineDispatcher,
                apiBlock = networkFetchBlock,
                errorMessages = errorMessages,
                timeout = networkTimeout,
                networkErrorMapper = networkErrorMapper
            )
            yield() //Not sure if this is needed, the safeApiCall may do it as it returns since it's a suspend function
            when (networkResponse) {
                is NetworkResult.Success -> {
                    if (networkResponse.value == null) {
                        emit(Result.Error(Exception(errorMessages.unknown)))
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
                    if (showDataOnError) {
                        emitLocalIfNotNull(Exception(networkResponse.errorMessage))
                    } else {
                        emit(Result.Error(Exception(networkResponse.errorMessage)))
                    }
                }
                NetworkResult.NetworkError -> {
                    if (showDataOnError) {
                        emitLocalIfNotNull(Exception(errorMessages.genericNetwork))
                    } else {
                        emit(Result.Error(Exception(errorMessages.genericNetwork)))
                    }
                }
            }
        } else if (localFlowFetchBlock != null) {   //No network call defined, attempt local instead
            val cacheResponse = safeCacheCall(
                dispatcher = coroutineDispatcher,
                cacheBlock = localFlowFetchBlock,
                errorMessages = errorMessages
            )
            yield()
            when (cacheResponse) {
                is Result.Success -> localFlowFetchBlock.let { localFlow ->
                    localFlow().collect {
                        emit(Result.Success(it))
                    }
                }
                is Result.Error -> emit(Result.Error(cacheResponse.exception))
                Result.Loading -> emit(Result.Error(Exception(errorMessages.unknown)))
            }
        } else {
            emit(Result.Error(MissingArgumentException("No data requested")))
        }
    }.onEach {
        if(it is Result.Error) loggingInterceptor?.invoke("")
    }

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

    class MissingArgumentException(m: String) : Exception(m)
}