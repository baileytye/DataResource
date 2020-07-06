package com.baileytye.dataresource.networkBoundResource

import com.baileytye.dataresource.model.DefaultErrorMessages
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import com.baileytye.dataresource.model.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class NetworkBoundResourceTest {

    private val dispatcher = TestCoroutineDispatcher()
    private val errorMessages = DefaultErrorMessages()
    private val mapper = object : Mapper<String, String> {
        override fun networkToLocal(network: String) = network
        override fun localToNetwork(local: String) = local
    }

    @Nested
    @DisplayName("Get Result")
    inner class GetResult {
        @Test
        fun `check empty builder returns missing argument error`() = runBlockingTest {
            //Given
            val resource = NetworkBoundResourceBuilder(mapper).build()

            //When
            val resultFlow = resource.getFlowResult()

            //Then
            val values = resultFlow.toList()
            assertThat(values[0]).isInstanceOf(Result.Loading::class.java)
            assertThat(values[1]).isInstanceOf(Result.Error::class.java)
            assertThat((values[1] as Result.Error).exception).isInstanceOf(NetworkBoundResource.MissingArgumentException::class.java)
        }

        @Test
        fun `check timeout returns timeout error`() = dispatcher.runBlockingTest {
            //Given
            val builder = NetworkBoundResourceBuilder(mapper)
            var localCached = false

            val resource = builder
                .networkFetchBlock {
                    delay(2000)
                    throw Exception("Should not throw")
                }
                .showLoading(false)
                .localCacheBlock { localCached = true }
                .coroutineDispatcher(dispatcher)
                .networkTimeout(1000)
                .build()

            //When
            val response = resource.getFlowResult()
            val list = response.toList()

            //Then
            assertThat(list).hasSize(1)
            assertThat(list[0]).isInstanceOf(Result.Error::class.java)
            assertThat((list[0] as Result.Error).exception.message).isEqualTo(errorMessages.networkTimeout)
            assertThat(localCached).isFalse()
        }

        @Test
        fun `check fetch network and throw unknown error - local is empty - return error`() =
            dispatcher.runBlockingTest {
                //Given
                val builder = NetworkBoundResourceBuilder(mapper)
                var localCached = false

                val resource = builder
                    .networkFetchBlock {
                        throw Exception("Some unknown error")
                    }
                    .showLoading(false)
                    .localCacheBlock { localCached = true }
                    .localFlowFetchBlock { emptyFlow() }
                    .coroutineDispatcher(dispatcher)
                    .build()

                //When
                val response = resource.getFlowResult()
                val list = response.toList()

                //Then
                assertThat(list).hasSize(1)
                assertThat(list[0]).isInstanceOf(Result.Error::class.java)
                assertThat((list[0] as Result.Error).exception.message).isEqualTo(errorMessages.unknown)
                assertThat(localCached).isFalse()
            }

        @Test
        fun `check fetch network and throw unknown error - local is present - return local, then error`() =
            dispatcher.runBlockingTest {
                //Given
                val builder = NetworkBoundResourceBuilder(mapper)
                var localCached = false

                val resource = builder
                    .networkFetchBlock {
                        throw Exception("Some unknown error")
                    }
                    .showLoading(false)
                    .localCacheBlock { localCached = true }
                    .localFlowFetchBlock { flowOf("local") }
                    .coroutineDispatcher(dispatcher)
                    .build()

                //When
                val response = resource.getFlowResult()
                val list = response.toList()

                //Then
                assertThat(list).hasSize(2)
                assertThat(list[0]).isInstanceOf(Result.Success::class.java)
                assertThat((list[0] as Result.Success).data).isEqualTo("local")
                assertThat((list[1] as Result.Error).exception.message).isEqualTo(errorMessages.unknown)
                assertThat(localCached).isFalse()
            }

        @Test
        fun `check fetch network and throw unknown error - local is present - return local, then error with local`() =
            dispatcher.runBlockingTest {
                //Given
                val builder = NetworkBoundResourceBuilder(mapper)
                var localCached = false

                val resource = builder
                    .networkFetchBlock {
                        throw Exception("Some unknown error")
                    }
                    .showDataOnError(true)
                    .showLoading(false)
                    .localCacheBlock { localCached = true }
                    .localFlowFetchBlock { flowOf("local") }
                    .coroutineDispatcher(dispatcher)
                    .build()

                //When
                val response = resource.getFlowResult()
                val list = response.toList()

                //Then
                assertThat(list).hasSize(2)
                assertThat(list[0]).isInstanceOf(Result.Success::class.java)
                assertThat((list[0] as Result.Success).data).isEqualTo("local")
                assertThat((list[1] as Result.Error).exception.message).isEqualTo(errorMessages.unknown)
                assertThat((list[1] as Result.Error).data).isEqualTo("local")
                assertThat(localCached).isFalse()
            }

        @Test
        fun `check fetch network and throw unknown error - no local block - return error`() =
            dispatcher.runBlockingTest {
                //Given
                val builder = NetworkBoundResourceBuilder(mapper)
                var localCached = false

                val resource = builder
                    .networkFetchBlock {
                        throw Exception("Some unknown error")
                    }
                    .showLoading(false)
                    .localCacheBlock { localCached = true }
                    .coroutineDispatcher(dispatcher)
                    .build()

                //When
                val response = resource.getFlowResult()
                val list = response.toList()

                //Then
                assertThat(list).hasSize(1)
                assertThat(list[0]).isInstanceOf(Result.Error::class.java)
                assertThat((list[0] as Result.Error).exception.message).isEqualTo(errorMessages.unknown)
                assertThat(localCached).isFalse()
            }

        @Test
        fun `check fetch network success avoid local cache - return network data`() =
            dispatcher.runBlockingTest {
                //Given
                val builder = NetworkBoundResourceBuilder(mapper)
                val networkData = "Some network data"

                val resource = builder
                    .networkFetchBlock { networkData }
                    .showLoading(true)
                    .coroutineDispatcher(dispatcher)
                    .build()

                //When
                val response = resource.getFlowResult()
                val list = response.toList()

                //Then
                assertThat(list).hasSize(2)
                assertThat(list[0]).isInstanceOf(Result.Loading::class.java)
                assertThat((list[1] as Result.Success).data).isEqualTo(mapper.networkToLocal(networkData))
            }

        @Test
        fun `check fetch network success save to local cache - return network data`() =
            dispatcher.runBlockingTest {
                //Given
                val builder = NetworkBoundResourceBuilder(mapper)
                val networkData ="Some network data"
                var cachedLocally = false

                val resource = builder
                    .networkFetchBlock { networkData }
                    .showLoading(true)
                    .localCacheBlock { cachedLocally = true }
                    .coroutineDispatcher(dispatcher)
                    .build()

                //When
                val response = resource.getFlowResult()
                val list = response.toList()

                //Then
                assertThat(list).hasSize(2)
                assertThat(list[0]).isInstanceOf(Result.Loading::class.java)
                assertThat((list[1] as Result.Success).data).isEqualTo(mapper.networkToLocal(networkData))
                assertThat(cachedLocally).isTrue()
            }

        @Test
        fun `check fetch network success save to local cache - return local data`() =
            dispatcher.runBlockingTest {
                //Given
                val builder = NetworkBoundResourceBuilder(mapper)
                val networkData ="Some network data"
                var localData = "Old data"
                var localFetched = false
                var cachedLocal = false

                val resource = builder
                    .networkFetchBlock { networkData }
                    .showLoading(true)
                    .localCacheBlock {
                        cachedLocal = true
                        localData = it
                    }
                    .localFlowFetchBlock {
                        localFetched = true
                        flowOf(localData)
                    }
                    .coroutineDispatcher(dispatcher)
                    .build()

                //When
                val response = resource.getFlowResult()
                val list = response.toList()

                //Then
                assertThat(list).hasSize(2)
                assertThat(list[0]).isInstanceOf(Result.Loading::class.java)
                assertThat((list[1] as Result.Success).data).isEqualTo(mapper.networkToLocal(networkData))
                assertThat(localFetched && cachedLocal).isTrue()
                assertThat(localData).isEqualTo(mapper.networkToLocal(localData))
            }

        @Test
        fun `check fetch network test not displaying loading - return network`() =
            dispatcher.runBlockingTest {
                //Given
                val builder = NetworkBoundResourceBuilder(mapper)
                val networkData ="Some network data"

                val resource = builder
                    .networkFetchBlock { networkData }
                    .showLoading(false)
                    .coroutineDispatcher(dispatcher)
                    .build()

                //When
                val response = resource.getFlowResult()
                val list = response.toList()

                //Then
                assertThat(list).hasSize(1)
                assertThat((list[0] as Result.Success).data).isEqualTo(mapper.networkToLocal(networkData))
            }

        @Test
        fun `check fetch network with error, return error with non null local data`() =
            dispatcher.runBlockingTest {
                //Given
                val builder = NetworkBoundResourceBuilder(mapper)
                var localData = "Old data"
                var localFetched = false
                var cachedLocal = false

                val resource = builder
                    .networkFetchBlock { throw Exception("Some network exception") }
                    .showLoading(true)
                    .showDataOnError(true)
                    .localCacheBlock {
                        cachedLocal = true
                        localData = it
                    }
                    .localFlowFetchBlock {
                        localFetched = true
                        flowOf(localData)
                    }
                    .coroutineDispatcher(dispatcher)
                    .build()

                //When
                val response = resource.getFlowResult()
                val list = response.toList()

                //Then
                assertThat(list).hasSize(2)
                assertThat(list[0]).isInstanceOf(Result.Loading::class.java)
                assertThat((list[1] as Result.Error).data).isEqualTo(localData)
                assertThat(localFetched).isTrue()
                assertThat(cachedLocal).isFalse()
            }

        @Test
        fun `check fetch network with error, return error with null local data`() =
            dispatcher.runBlockingTest {
                //Given
                val builder = NetworkBoundResourceBuilder(mapper)
                var localFetched = false
                var cachedLocal = false

                val resource = builder
                    .networkFetchBlock { throw Exception("Some network exception") }
                    .showLoading(true)
                    .showDataOnError(true)
                    .localCacheBlock {
                        cachedLocal = true
                    }
                    .localFlowFetchBlock {
                        localFetched = true
                        flowOf()
                    }
                    .coroutineDispatcher(dispatcher)
                    .build()

                //When
                val response = resource.getFlowResult()
                val list = response.toList()

                //Then
                assertThat(list).hasSize(2)
                assertThat(list[0]).isInstanceOf(Result.Loading::class.java)
                assertThat((list[1] as Result.Error).data).isNull()
                assertThat(localFetched).isTrue()
                assertThat(cachedLocal).isFalse()
            }

    }

}