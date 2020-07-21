package com.baileytye.dataresource.networkBoundResourcePaged

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.baileytye.dataresource.networkBoundResource.Mapper

@ExperimentalPagingApi
class ResourceRemoteMediator<NetworkData : Any, NetworkPagedData : PagedNetworkData<NetworkData>, Local : Any>(
    private val startingIndex: Int = 0,
    private val networkBlock: suspend (page: Int, pageSize: Int) -> NetworkPagedData,
    private val clearBlock: suspend () -> Unit,
    private val cacheBlock: suspend (List<Local>) -> Unit,
    private val mapper: Mapper<NetworkData, Local>
) : RemoteMediator<Int, Local>() {

    private var lastLoadedPageIndex = startingIndex

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, Local>
    ): MediatorResult {
        val page: Int =
            when (loadType) {
                LoadType.REFRESH -> {
                    startingIndex
                }
                LoadType.PREPEND -> {
//                if(lastLoadedPageIndex == startingIndex) startingIndex
//                else lastLoadedPageIndex - 1
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    if (lastLoadedPageIndex == startingIndex) startingIndex
                    else lastLoadedPageIndex + 1
                }
            }
        try {
            val apiResponse = networkBlock(page, state.config.pageSize)

            val list = apiResponse.items
            val endOfPaginationReached = list.isEmpty()
            // clear all tables in the database
            if (loadType == LoadType.REFRESH) {
                clearBlock()
            }

            val prevKey = if (page == startingIndex) null else page - 1
            val nextKey = if (endOfPaginationReached) null else page + 1
//                val keys = repos.map {
//                    RemoteKeys(repoId = it.id, prevKey = prevKey, nextKey = nextKey)
//                }
//                repoDatabase.remoteKeysDao().insertAll(keys)
            cacheBlock(list.map { mapper.networkToLocal(it) })

            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (exception: Exception) {
            return MediatorResult.Error(exception)
        }
    }

}