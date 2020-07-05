package model

import java.io.IOException

class DefaultNetworkErrorMapper<T>(private val errorMessages: ErrorMessagesResource) : NetworkErrorMapper<T> {
    override fun mapNetworkError(throwable: Throwable): NetworkResult<T> {
        return when (throwable) {
            is IOException -> NetworkResult.NetworkError
            is NotImplementedError -> NetworkResult.GenericError(null, throwable.message)
            else -> NetworkResult.GenericError(null, errorMessages.unknown)
        }
    }
}