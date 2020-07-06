package com.baileytye.dataresource.networkBoundResource

import com.baileytye.dataresource.model.NetworkResult

/**
 * Mapper to convert a throwable thrown in a network block to a network result
 */
interface NetworkErrorMapper<T> {

    /**
     * Maps throwable to network result
     * @param throwable to conver
     * @return network result wrapping network data type T
     */
    fun mapNetworkError(throwable: Throwable) : NetworkResult<T>
}