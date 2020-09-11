package com.baileytye.dataresource.networkBoundResource

import com.baileytye.dataresource.model.DefaultErrorMessages
import com.baileytye.dataresource.model.Result
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetworkBoundResourceCacheTest {

    private val dispatcher = TestCoroutineDispatcher()
    private val errorMessages = DefaultErrorMessages()
    private val mapper = object : Mapper<String, String> {
        override fun networkToLocal(network: String) = network
        override fun localToNetwork(local: String) = local
    }

    private val options = NetworkBoundResource.Options(
        coroutineDispatcher = dispatcher,
        errorMessages = errorMessages,
        showLoading = true,
        cacheResult = true,
        cacheStaleTimeout = 100
    )

//    @Test
//    fun `check advance time with dispatcher`() = runBlocking {
//        val start = System.currentTimeMillis()
//        delay(1000)
//        assertThat(System.currentTimeMillis() - start).isAtLeast(1000)
//    }

    @Test
    fun `check get flow result with fresh value returns the same value`() = dispatcher.runBlockingTest {
        //Given
        var firstFetch = true
        val resource = NetworkBoundResource.Builder(mapper).options(options).networkFetchBlock {
            if (firstFetch) {
                firstFetch = false
                "first"
            } else "other"
        }.build()

        //When
        val firstResultFlow = resource.getFlowResult().toList()
        val secondResultFlow = resource.getFlowResult().toList()

        //Then
        assertThat(firstResultFlow).hasSize(2)
        assertThat(secondResultFlow).hasSize(1)
        assertThat((secondResultFlow[0] as Result.Success).data).isEqualTo("first")
    }

    @Test
    fun `check get flow result with stale value returns new value`() = dispatcher.runBlockingTest {
        //Given
        var firstFetch = true
        val resource = NetworkBoundResource.Builder(mapper).options(options.copy(cacheStaleTimeout = 0)).networkFetchBlock {
            if (firstFetch) {
                firstFetch = false
                "first"
            } else "other"
        }.build()

        //When
        val firstResultFlow = resource.getFlowResult().toList()
        val secondResultFlow = resource.getFlowResult().toList()

        //Then
        assertThat(firstResultFlow).hasSize(2)
        assertThat(secondResultFlow).hasSize(2)
        assertThat((secondResultFlow[1] as Result.Success).data).isEqualTo("other")
    }

    @Test
    fun `check one shot with fresh value returns the same value`() = dispatcher.runBlockingTest {
        //Given
        var firstFetch = true
        val resource = NetworkBoundResource.Builder(mapper).options(options).networkFetchBlock {
            if (firstFetch) {
                firstFetch = false
                "first"
            } else "other"
        }.build()

        //When
        val firstResult = resource.oneShotOperation()
        val secondResult = resource.oneShotOperation()

        //Then
        assertThat(firstResult).isInstanceOf(Result.Success::class.java)
        assertThat((firstResult as Result.Success).data).isEqualTo("first")
        assertThat(secondResult).isInstanceOf(Result.Success::class.java)
        assertThat((secondResult as Result.Success).data).isEqualTo("first")
    }

    @Test
    fun `check one shot with stale value returns new value`() = dispatcher.runBlockingTest {
        //Given
        var firstFetch = true
        val resource = NetworkBoundResource.Builder(mapper).options(options.copy(cacheStaleTimeout = 0)).networkFetchBlock {
            if (firstFetch) {
                firstFetch = false
                "first"
            } else "other"
        }.build()

        //When
        val firstResult = resource.oneShotOperation()
        val secondResult = resource.oneShotOperation()

        //Then
        assertThat(firstResult).isInstanceOf(Result.Success::class.java)
        assertThat((firstResult as Result.Success).data).isEqualTo("first")
        assertThat(secondResult).isInstanceOf(Result.Success::class.java)
        assertThat((secondResult as Result.Success).data).isEqualTo("other")
    }
}