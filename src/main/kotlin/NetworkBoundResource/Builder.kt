package NetworkBoundResource

import com.squareup.moshi.Moshi
import util.DEFAULT_NETWORK_TIMEOUT
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import model.DefaultErrorMessages
import model.ErrorMessagesResource
import model.Mapper
import model.NetworkErrorMapper
import okio.BufferedSource

data class Builder<Network, Local>(
    private var mapper: Mapper<Network, Local>,
    private var coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private var networkFetchBlock: (suspend () -> Network)? = null,
    private var localFlowFetchBlock: (suspend () -> Flow<Local>)? = null,
    private var localCacheBlock: (suspend (Local) -> Unit)? = null,
    private var showDataOnError: Boolean = false,
    private var showLoading: Boolean = true,
    private var errorMessages: ErrorMessagesResource = DefaultErrorMessages(),
    private var networkTimeout: Long = DEFAULT_NETWORK_TIMEOUT,
    private var networkErrorMapper: NetworkErrorMapper<Network>,
    private var loggingInterceptor: ((String) -> Unit)? = null
) {
    /**
     * Sets coroutine dispatcher for calls to run on
     */
    fun coroutineDispatcher(coroutineDispatcher: CoroutineDispatcher) =
        apply { this.coroutineDispatcher = coroutineDispatcher }

    /**
     * Sets network fetch block that will be executed
     */
    fun networkFetchBlock(networkFetchBlock: suspend () -> Network) =
        apply { this.networkFetchBlock = networkFetchBlock }

    /**
     * Sets local flow fetch block, typically this is a flow from a Room database that will
     * emit new values when they are changed
     */
    fun localFlowFetchBlock(localFlowFetchBlock: suspend () -> Flow<Local>) =
        apply { this.localFlowFetchBlock = localFlowFetchBlock }

    /**
     * Sets cache block executed when network returns a success
     * @param localCacheBlock passes data converted to local object from network response
     */
    fun localCacheBlock(localCacheBlock: (suspend (Local) -> Unit)?) =
        apply { this.localCacheBlock = localCacheBlock }

    /**
     * Sets show data on error flag. When this is true, data will be emitted along with an error
     * message when an error occurs. When this is false, only an error message will be emitted; null data.
     */
    fun showDataOnError(showDataOnError: Boolean) =
        apply { this.showDataOnError = showDataOnError }

    /**
     * Sets whether loading result should be emitted. When false, local data will be emitted during
     * loading.
     */
    fun showLoading(showLoading: Boolean) =
        apply { this.showLoading = showLoading }

    /**
     * Sets the error messages to be displayed on errors. Useful for when different languages are
     * required.
     */
    fun errorMessages(errorMessages: ErrorMessagesResource) =
        apply { this.errorMessages = errorMessages }

    /**
     * Sets the network timeout value.
     */
    fun networkTimeout(timeout: Long) =
        apply { this.networkTimeout = timeout }

    /**
     * Sets the mapper used to convert network errors to a NetworkResult object
     */
    fun networkErrorMapper(networkErrorMapper: NetworkErrorMapper<Network>) =
        apply { this.networkErrorMapper = networkErrorMapper }

    /**
     * Sets the logging interceptor for the resource
     */
    fun loggingInterceptor(logBlock : (String) -> Unit) =
        apply { this.loggingInterceptor = logBlock }


    fun build() = NetworkBoundResource(
        mapper = this.mapper,
        coroutineDispatcher = this.coroutineDispatcher,
        networkFetchBlock = this.networkFetchBlock,
        localFlowFetchBlock = this.localFlowFetchBlock,
        localCacheBlock = this.localCacheBlock,
        showDataOnError = this.showDataOnError,
        showLoading = this.showLoading,
        errorMessages = this.errorMessages,
        networkTimeout = this.networkTimeout,
        networkErrorMapper = this.networkErrorMapper,
        loggingInterceptor = this.loggingInterceptor
    )
}