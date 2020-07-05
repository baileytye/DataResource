package model

sealed class NetworkResult<out T> {
    data class Success<out T>(val value: T) : NetworkResult<T>()

    data class GenericError(
        val code: Int? = null,
        val errorMessage: String? = null
    ) : NetworkResult<Nothing>()

    object NetworkError : NetworkResult<Nothing>()

    fun toResult(unknownError: String): Result<T> = when (this) {
        is Success -> {
            Result.Success(value)
        }
        is GenericError -> {
            Result.Error(Exception(errorMessage), null)
        }
        is NetworkError -> {
            Result.Error(Exception(unknownError), null)
        }
    }
}