package com.baileytye.dataresource.networkBoundResource

import com.baileytye.dataresource.model.DefaultErrorMessages
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import com.baileytye.dataresource.model.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class NetworkBoundResourceTest {

    private val dispatcher = TestCoroutineDispatcher()
    private val errorMessages = DefaultErrorMessages()
    private val mapper = object : Mapper<String, String> {
        override fun networkToLocal(network: String) = network
        override fun localToNetwork(local: String) = local
    }
    private val options = NetworkBoundResource.Options(
        coroutineDispatcher = dispatcher,
        errorMessages = errorMessages,
        showLoading = false
    )

    @Nested
    @DisplayName("Get Result")
    inner class GetResult {
        @Test
        fun `check empty builder returns missing argument error`() = runBlockingTest {
            //Given
            val resource = NetworkBoundResource.Builder(mapper).build()

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
            val builder = NetworkBoundResource.Builder(mapper)
            var localCached = false

            val resource = builder
                .networkFetchBlock {
                    delay(2000)
                    throw Exception("Should not throw")
                }
                .options(options.copy(networkTimeout = 1000))
                .localCacheBlock { localCached = true }
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
                val builder = NetworkBoundResource.Builder(mapper)
                var localCached = false

                val resource = builder
                    .networkFetchBlock {
                        throw Exception("Some unknown error")
                    }
                    .options(options)
                    .localCacheBlock { localCached = true }
                    .localFlowFetchBlock { emptyFlow() }
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
                val builder = NetworkBoundResource.Builder(mapper)
                var localCached = false

                val resource = builder
                    .networkFetchBlock {
                        throw Exception("Some unknown error")
                    }
                    .options(options)
                    .localCacheBlock { localCached = true }
                    .localFlowFetchBlock { flowOf("local") }
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
                val builder = NetworkBoundResource.Builder(mapper)
                var localCached = false

                val resource = builder
                    .networkFetchBlock {
                        throw Exception("Some unknown error")
                    }
                    .options(options.copy(showDataOnError = true))
                    .localCacheBlock { localCached = true }
                    .localFlowFetchBlock { flowOf("local") }
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
                val builder = NetworkBoundResource.Builder(mapper)
                var localCached = false

                val resource = builder
                    .networkFetchBlock {
                        throw Exception("Some unknown error")
                    }
                    .options(options)
                    .localCacheBlock { localCached = true }
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
                val builder = NetworkBoundResource.Builder(mapper)
                val networkData = "Some network data"

                val resource = builder
                    .networkFetchBlock { networkData }
                    .options(options.copy(showLoading = true))
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
                val builder = NetworkBoundResource.Builder(mapper)
                val networkData = "Some network data"
                var cachedLocally = false

                val resource = builder
                    .networkFetchBlock { networkData }
                    .options(options.copy(showLoading = true))
                    .localCacheBlock { cachedLocally = true }
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
        fun `check fetch network success with null value when specified nullable - returns null`() =
            dispatcher.runBlockingTest {
                //Given
                val nullableMapper = object : Mapper<String?, String?> {
                    override fun networkToLocal(network: String?) = network
                    override fun localToNetwork(local: String?) = local
                }
                val builder = NetworkBoundResource.Builder(nullableMapper)
                val networkData: String? = null
                var localData: String? = ""
                val resource = builder.options(options.copy(showLoading = true)).networkFetchBlock {
                    networkData
                }
                    .localCacheBlock {
                        localData = it
                    }
                    .localFlowFetchBlock { flowOf(localData) }
                    .build()

                //When
                val response = resource.getFlowResult()
                val list = response.toList()

                //Then
                assertThat(list).hasSize(2)
                assertThat(list[1]).isEqualTo(Result.Success(null))
            }


        @Test
        fun `check fetch network success save to local cache - return local data`() =
            dispatcher.runBlockingTest {
                //Given
                val builder = NetworkBoundResource.Builder(mapper)
                val networkData = "Some network data"
                var localData = "Old data"
                var localFetched = false
                var cachedLocal = false

                val resource = builder
                    .networkFetchBlock { networkData }
                    .options(options.copy(showLoading = true))
                    .localCacheBlock {
                        cachedLocal = true
                        localData = it
                    }
                    .localFlowFetchBlock {
                        localFetched = true
                        flowOf(localData)
                    }
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
                val builder = NetworkBoundResource.Builder(mapper)
                val networkData = "Some network data"

                val resource = builder
                    .networkFetchBlock { networkData }
                    .options(options)
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
                val builder = NetworkBoundResource.Builder(mapper)
                var localData = "Old data"
                var localFetched = false
                var cachedLocal = false

                val resource = builder
                    .networkFetchBlock { throw Exception("Some network exception") }
                    .options(options.copy(showLoading = true, showDataOnError = true))
                    .localCacheBlock {
                        cachedLocal = true
                        localData = it
                    }
                    .localFlowFetchBlock {
                        localFetched = true
                        flowOf(localData)
                    }
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
                val builder = NetworkBoundResource.Builder(mapper)
                var localFetched = false
                var cachedLocal = false

                val resource = builder
                    .networkFetchBlock { throw Exception("Some network exception") }
                    .options(options.copy(showLoading = true, showDataOnError = true))
                    .localCacheBlock {
                        cachedLocal = true
                    }
                    .localFlowFetchBlock {
                        localFetched = true
                        flowOf()
                    }
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

        @Test
        fun `check logging interceptor is called on result error`() = dispatcher.runBlockingTest {
            //Given
            val builder = NetworkBoundResource.Builder(mapper)
            var loggedMessage = ""

            val resource = builder
                .options(options.copy(showLoading = true, loggingInterceptor = {
                    loggedMessage = it
                }))
                .networkFetchBlock { throw Exception("Some network exception") }
                .build()

            //When
            val response = resource.getFlowResult()
            val list = response.toList()


            //Then
            assertThat(list).hasSize(2)
            assertThat(list[0]).isInstanceOf(Result.Loading::class.java)
            assertThat((list[1] as Result.Error).data).isNull()
            assertThat(loggedMessage).isEqualTo(errorMessages.unknown)
        }

    }

    @Nested
    @DisplayName("One shot")
    inner class OneShot {

        @Test
        fun `check oneshot fetch network with empty builder`() = dispatcher.runBlockingTest {
            //Given
            val builder = NetworkBoundResource.Builder(mapper)
            val resource = builder
                .options(options)
                .build()

            //When
            val result = resource.oneShotOperation()

            //Then
            assertThat(result).isInstanceOf(Result.Error::class.java)
            assertThat((result as Result.Error).exception).isInstanceOf(NetworkBoundResource.MissingArgumentException::class.java)
        }

        @Test
        fun `check oneshot fetch network success`() = dispatcher.runBlockingTest {
            //Given
            val builder = NetworkBoundResource.Builder(mapper)
            val networkData = "Network data"
            val resource = builder
                .networkFetchBlock { networkData }
                .options(options)
                .build()

            //When
            val result = resource.oneShotOperation()

            //Then
            assertThat(result).isInstanceOf(Result.Success::class.java)
            assertThat((result as Result.Success).data).isEqualTo(mapper.networkToLocal(networkData))
        }

        @Test
        fun `check oneshot fetch network with unknown error`() = dispatcher.runBlockingTest {
            //Given
            val builder = NetworkBoundResource.Builder(mapper)
            val resource = builder
                .networkFetchBlock { throw Exception("Some unknown error") }
                .options(options)
                .build()

            //When
            val result = resource.oneShotOperation()

            //Then
            assertThat(result).isInstanceOf(Result.Error::class.java)
            assertThat((result as Result.Error).data).isNull()
            assertThat(result.exception.message).isEqualTo(errorMessages.unknown)
        }

        @Test
        fun `check fetch network success with null value when specified nullable - returns null`() =
            dispatcher.runBlockingTest {
                //Given
                val nullableMapper = object : Mapper<String?, String?> {
                    override fun networkToLocal(network: String?) = network
                    override fun localToNetwork(local: String?) = local
                }
                val builder = NetworkBoundResource.Builder(nullableMapper)
                val networkData: String? = null
                var localData: String? = ""
                val resource = builder.options(options.copy(showLoading = true)).networkFetchBlock {
                    networkData
                }
                    .localCacheBlock {
                        localData = it
                    }
                    .build()

                //When
                val response = resource.oneShotOperation()

                //Then
                assertThat(response).isEqualTo(Result.Success(null))
                assertThat(localData).isNull()
            }

        @Test
        fun `check oneshot fetch network with timeout error`() = dispatcher.runBlockingTest {
            //Given
            val builder = NetworkBoundResource.Builder(mapper)
            val networkData = "Network data"
            val resource = builder
                .networkFetchBlock {
                    delay(200)
                    networkData
                }
                .options(options.copy(networkTimeout = 100))
                .build()

            //When
            val result = resource.oneShotOperation()

            //Then
            assertThat(result).isInstanceOf(Result.Error::class.java)
            assertThat((result as Result.Error).data).isNull()
            assertThat(result.exception.message).isEqualTo(errorMessages.networkTimeout)
        }

        @Test
        fun `check logging interceptor is called on result error`() = runBlockingTest {
            //Given
            val builder = NetworkBoundResource.Builder(mapper)
            var loggedMessage = ""

            val resource = builder
                .networkFetchBlock { throw Exception("Some network exception") }
                .options(options.copy(showLoading = true, showDataOnError = true, loggingInterceptor = {
                    loggedMessage = it
                }))
                .build()

            //When
            val response = resource.oneShotOperation()

            //Then
            assertThat(response).isInstanceOf(Result.Error::class.java)
            assertThat(loggedMessage).isEqualTo(errorMessages.unknown)
        }
    }


}