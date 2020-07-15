package com.baileytye.dataresource.networkBoundResource

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import com.baileytye.dataresource.model.DefaultErrorMessages
import com.baileytye.dataresource.model.NetworkResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.IOException
import java.lang.Exception

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DefaultNetworkErrorMapperTest{
    private val dispatcher = TestCoroutineDispatcher()
    private val errorMessages = DefaultErrorMessages()
    private val defaultNetworkErrorMapper =
        DefaultNetworkErrorMapper(
            errorMessages
        )

    @Test
    fun `check IO error returns unknown message`() = dispatcher.runBlockingTest {
        //Given

        //When
        val result = defaultNetworkErrorMapper.mapNetworkError(IOException())

        //Then
        assertThat(result).isInstanceOf(NetworkResult.NetworkError::class.java)
    }

    @Test
    fun `check unimplemented error returns unimplemented message`() = dispatcher.runBlockingTest {
        //Given

        //When
        val result = defaultNetworkErrorMapper.mapNetworkError(
            NotImplementedError("Not Done")
        )

        //Then
        assertThat(result).isInstanceOf(NetworkResult.GenericError::class.java)
        assertThat((result as NetworkResult.GenericError).errorMessage).isEqualTo("Not Done")
    }

    @Test
    fun `check unknown error returns unknown message`() = dispatcher.runBlockingTest {
        //Given

        //When
        val result = defaultNetworkErrorMapper.mapNetworkError(
            Exception("Not shown")
        )

        //Then
        assertThat(result).isInstanceOf(NetworkResult.GenericError::class.java)
        assertThat((result as NetworkResult.GenericError).errorMessage).isEqualTo(errorMessages.unknown)
    }
}