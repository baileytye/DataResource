package model

interface NetworkErrorMapper<T> {
    fun mapNetworkError(throwable: Throwable) : NetworkResult<T>
}