# DataResource
[![](https://jitpack.io/v/baileytye/DataResource.svg)](https://jitpack.io/#baileytye/DataResource)

Library to handle network/caching code for simple projects.

## Gradle

```
//In app build.gradle
dependencies {
    ...
    implementation 'com.github.baileytye:DataResource:version'
}

//In project build.gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

## Usage 

### Create Mapper and Options objects
```kotlin 
//Using default options
private val dataResourceOptions = NetworkBoundResource.Options()

//Example mapper for direct conversion
private val mapper = object : Mapper<String, String> {
    override fun localToNetwork(local: String) = local
    override fun networkToLocal(network: String) = network
}
```
### Create Resource
```kotlin

private fun createGetStringResource() = NetworkBoundResource.Builder(mapper)
  .options(dataResourceOptions)
  .networkFetchBlock { "A string from the network" }
  .localCacheBlock { /* Optional caching */ }
  .localFlowFetchBlock { /* Optional flow fetch usually from Room database */ }
  .build()

```

When a local cache block is specified, on a successful network fetch, the result will be mapped to a local object by the specified
mapper, and given to this block.
When a local flow fetch block is specified, any new value emitted by this flow is emitted by getFlowResult() while obeying the flow
chart shown below.

### Use For Flow Result
```kotlin
val result : Flow<Result<String>> = createGetStringResource().getFlowResult()

```
### Use For One Shot Fetch Operation
```kotlin

suspend fun getString(): Result<String> {
    return createGetStringResource().oneShotOperation()
}
```

## Options
```kotlin
/**
 * Coroutine dispatcher used to run the resource methods
 */
val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,

/**
 * Specifies whether local data should be emitted in the event of a network error. Only valid
 * if a local flow fetch block is defined.
 */
val showDataOnError: Boolean = false,

/**
 * Specifies whether a loading result should be returned while waiting for the network
 * fetch block to complete. If false and a local flow fetch block is defined, that data will emit
 * while waiting for the network block to complete. Once the network block completes and is cached, a
 * new value will be emitted (if using room for flow fetch).
 */
val showLoading: Boolean = true,

/**
 * Error messages used for generic error types. For more control over error messages, implement
 * [NetworkErrorMapper] and return the results you want through [NetworkResult.GenericError].
 */
val errorMessages: ErrorMessagesResource = DefaultErrorMessages(),

/**
 * Network timeout used for network calls.
 */
val networkTimeout: Long = DEFAULT_NETWORK_TIMEOUT,

/**
 * Network error mapper used to map network errors to a [NetworkResult] object which is returned from
 * the internal safe network call.
 */
val networkErrorMapper: NetworkErrorMapper = DefaultNetworkErrorMapper(
    errorMessages
),

/**
 * Logging interceptor called on each [Result.Error] emitted from [getFlowResult] with the
 * error message given to the block.
 */
val loggingInterceptor: ((String) -> Unit)? = null
```

## Flow Chart

The flow of execution is shown below. This is for a Flow response, however the one shot operation will follow similar rules, ignoring loading and flowFetchBlock. The caching is still executed during a one shot operation, but the flowFetchBlock is ignored.


![alt text](https://github.com/baileytye/DataResource/blob/master/Data%20Resource%20Flow%20Chart.png)
