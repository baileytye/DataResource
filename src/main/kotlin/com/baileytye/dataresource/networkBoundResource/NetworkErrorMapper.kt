package com.baileytye.dataresource.networkBoundResource

import com.baileytye.dataresource.model.NetworkResult

interface NetworkErrorMapper<T> {
    fun mapNetworkError(throwable: Throwable) : NetworkResult<T>
}