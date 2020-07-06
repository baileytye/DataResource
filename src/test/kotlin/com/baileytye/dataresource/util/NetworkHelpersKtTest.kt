package com.baileytye.dataresource.util

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import com.baileytye.dataresource.model.DefaultErrorMessages
import com.baileytye.dataresource.model.NetworkResult
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import com.baileytye.dataresource.model.Result
import com.baileytye.dataresource.networkBoundResource.NetworkErrorMapper
import java.io.IOException

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetworkHelpersKtTest{

    private val dispatcher = TestCoroutineDispatcher()
    private val errorMessages = DefaultErrorMessages()

    @Test
    fun `Test withTimeout`() = dispatcher.runBlockingTest {
        var timedOut = false
        withContext(dispatcher) {
            try {
                withTimeout(100) {
                    delay(200)
                }
            } catch (e: TimeoutCancellationException) {
                timedOut = true
            }
        }
        assertThat(timedOut).isTrue()
    }

    @Nested
    @DisplayName("SafeApiCall")
    inner class SafeApiCall {

        @Test
        fun `check custom error mapper works as expected`() = dispatcher.runBlockingTest {
            //Given
            val networkErrorMapper = object :
                NetworkErrorMapper<String> {
                override fun mapNetworkError(throwable: Throwable): NetworkResult<String> {
                    return when(throwable){
                        is IOException -> {
                            NetworkResult.GenericError(errorMessage = "IO")}
                        else -> {
                            NetworkResult.GenericError(errorMessage = "else")}
                    }
                }
            }

            //When
            val result = safeApiCall(
                dispatcher = dispatcher,
                apiBlock = {
                    throw(IOException("DONT SHOW THIS MESSAGE"))
                },
                networkErrorMapper = networkErrorMapper,
                errorMessages = errorMessages
            )

            //Then
            assertThat(result).isInstanceOf(NetworkResult.GenericError::class.java)
            assertThat((result as NetworkResult.GenericError).errorMessage).isEqualTo("IO")
        }

        @Test
        fun `check timeout returns error`() = dispatcher.runBlockingTest {
            //Given
            val data = "Successful network data that should never return"

            //When
            val result = safeApiCall(
                timeout = 100,
                dispatcher = dispatcher,
                apiBlock = {
                    delay(200)
                    data
                },
                errorMessages = errorMessages
            )

            //Then
            assertThat(result).isInstanceOf(NetworkResult.GenericError::class.java)
            assertThat((result as NetworkResult.GenericError).errorMessage).isEqualTo(errorMessages.networkTimeout)
        }

        @Test
        fun `check throw error with unknown error message returns unknown error`() =
            runBlockingTest {
                //Given
                val error = Exception("This went horribly wrong, don't show this message")

                //When
                val result = safeApiCall(
                    dispatcher = dispatcher,
                    apiBlock = {
                        throw(error)
                    },
                    errorMessages = errorMessages
                )

                //Then
                assertThat(result).isInstanceOf(NetworkResult.GenericError::class.java)
                assertThat((result as NetworkResult.GenericError).errorMessage).isEqualTo(errorMessages.unknown)
            }

        @Test
        fun `check success returns success`() = dispatcher.runBlockingTest {
            //Given
            val data = "Successful network data"

            //When
            val result = safeApiCall(
                dispatcher = dispatcher,
                apiBlock = { data },
                errorMessages = errorMessages
            )

            //Then
            assertThat(result).isInstanceOf(NetworkResult.Success::class.java)
            assertThat((result as NetworkResult.Success).value).isEqualTo(data)
        }

    }

    @Nested
    @DisplayName("SafeCacheCall")
    inner class SafeCacheCall {

        @Test
        fun `Call safeCacheCall - throw IO error`() = dispatcher.runBlockingTest {
            //Given
            val ioError = IOException("Some io exception")

            //When
            val result = safeCacheCall(
                dispatcher = dispatcher,
                cacheBlock = { throw ioError },
                errorMessages = errorMessages
            )

            //Then
            assertThat(result).isInstanceOf(Result.Error::class.java)
            assertThat((result as Result.Error).exception.message).isEqualTo(errorMessages.unknown)
        }

        @Test
        fun `Call safeCacheCall - throw unknown error`() = dispatcher.runBlockingTest {
            //Given
            val unknownError = Exception("Some unknown exception")

            //When
            val result = safeCacheCall(
                dispatcher = dispatcher,
                cacheBlock = { throw unknownError },
                errorMessages = errorMessages
            )

            //Then
            assertThat(result).isInstanceOf(Result.Error::class.java)
            assertThat((result as Result.Error).exception.message).isEqualTo(errorMessages.unknown)
        }

        @Test
        fun `Call safeCacheCall - return a success`() = dispatcher.runBlockingTest {
            //Given
            val data = "Successful local data"

            //When
            val result = safeCacheCall(
                dispatcher = dispatcher,
                cacheBlock = { data },
                errorMessages = errorMessages
            )

            //Then
            assertThat(result).isInstanceOf(Result.Success::class.java)
            assertThat((result as Result.Success).data).isEqualTo(data)
        }

        @Test
        fun `Call safeCacheCall - return a timeout error`() = dispatcher.runBlockingTest {
            //Given
            val data = "Successful local data"

            //When
            val result = safeCacheCall(
                dispatcher = dispatcher,
                cacheBlock = {
                    delay(200)
                    data
                },
                errorMessages = errorMessages,
                timeout = 100
            )

            //Then
            assertThat(result).isInstanceOf(Result.Error::class.java)
            assertThat((result as Result.Error).exception.message).isEqualTo(errorMessages.cacheTimeout)
        }
    }

}