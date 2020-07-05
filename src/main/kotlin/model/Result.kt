package model


/**
 * A generic class that holds a value with its loading status.
 * @param <T>
 */
sealed class Result<out R> {

    data class Success<out T>(val data: T) : Result<T>() {
        override fun toString(): String {
            return "Success!"
        }
    }

    data class Error<out T>(val exception: Exception, val data: T? = null) : Result<T>() {
        override fun toString(): String {
            return "Error (${if (data == null) "Data null" else "Data present"}): ${exception.message}"
        }
    }

    object Loading : Result<Nothing>() {
        override fun toString(): String {
            return "Loading..."
        }
    }

    fun <K> map(block: (R) -> K): Result<K> {
        return when (this) {
            is Success -> {
                Success(block(data))
            }
            is Loading -> {
                Loading
            }
            is Error -> {
                Error(exception, if (data != null) block(data) else null)
            }
        }
    }

    fun isSuccess() = this is Success
}
