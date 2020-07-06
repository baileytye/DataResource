package networkBoundResource

import model.NetworkResult

interface NetworkErrorMapper<T> {
    fun mapNetworkError(throwable: Throwable) : NetworkResult<T>
}