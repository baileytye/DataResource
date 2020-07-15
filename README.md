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

Commonly used to monitor Room database, and update with network data.

```kotlin
//In Repository
fun getStringResultFromRepository() : Flow<Result<String>> = createGetStringResource().getFlowResult()

//In viewModel, .asLiveData() requires  "androidx.lifecycle:lifecycle-livedata-ktx:version" 
val result : LiveData<Result<String>> = getStringResultFromRepository().asLiveData()

//Have states available if needed, for changing UI based on state
val isLoading : LiveData<Boolean> = result.map { it is Result.Loading }
val isError : LiveData<Boolean> = result.map { it is Result.Error }
val isSuccess : LiveData<Boolean> = result.map { it is Result.Success }

//Map data result
val string : LiveData<String> = result.map {
    when {
        it is Result.Success -> {
            it.data
        }
        it is Result.Error && it.data != null -> {
            //There was an error, but data is present. Happens when showDataOnError = true
            it.data!!
        }
        else -> "Some default or null"
    }
}

```
### Use For One Shot Fetch Operation

Commonly used for one off operations, or for POST/PUT/DELETE type operations where you aren't observing the data, and instead just need a result.

```kotlin

suspend fun getString(): Result<String> {
    return createGetStringResource().oneShotOperation()
}

//In viewModel
fun getString() : String {
    viewModelScope.launch {
        val result = getString()
        
        //process result
        when(result) {
            is Result.Success -> {/* handle successful string */}
            is Result.Error -> {/* handle error */}
            else -> {/* required but won't ever happen as only success or error can be set from one shot operation */}
        }
        
    }
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


## License
```
   Copyright [2020] [Bailey Tye]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```

