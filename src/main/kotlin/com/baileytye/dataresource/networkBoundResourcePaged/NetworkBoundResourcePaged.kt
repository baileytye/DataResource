package com.baileytye.dataresource.networkBoundResourcePaged

import androidx.paging.*
import com.baileytye.dataresource.networkBoundResource.Mapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NetworkBoundResourcePaged<NetworkData : Any, NetworkList : PagedNetworkData<NetworkData>, Local : Any>(
    private val pagingConfig: PagingConfig,
    private val startingIndex: Int = 0,
    private val networkBlock: suspend (page: Int, pageSize: Int) -> NetworkList,
    private val localCacheBlock: (suspend (List<Local>) -> Unit)?,
    private val clearCacheBlock: suspend () -> Unit,
    private val cacheFactory: () -> PagingSource<Int, Local>,
    private val mapper: Mapper<NetworkData, Local>
) {

    @ExperimentalPagingApi
    fun getPagedFlowResult(): Flow<PagingData<Local>> {
        return if (localCacheBlock == null) Pager(
            config = pagingConfig,
            pagingSourceFactory = {
                ResourcePagingSource<NetworkData, NetworkList>(
                    startingIndex,
                    networkBlock = networkBlock
                )
            }
        ).flow.map {
            it.map { networkData ->
                mapper.networkToLocal(networkData)
            }
        } else {
            Pager(
                config = pagingConfig,
                remoteMediator = ResourceRemoteMediator(
                    startingIndex = startingIndex,
                    networkBlock = networkBlock,
                    clearBlock = clearCacheBlock,
                    mapper = mapper,
                    cacheBlock = localCacheBlock
                ),
                pagingSourceFactory = cacheFactory
            ).flow
        }
    }
}