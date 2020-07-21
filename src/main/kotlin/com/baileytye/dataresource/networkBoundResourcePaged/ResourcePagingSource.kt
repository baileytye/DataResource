package com.baileytye.dataresource.networkBoundResourcePaged

import androidx.paging.PagingSource

class ResourcePagingSource<D : Any, R : PagedNetworkData<D>>(
    private val startingIndex: Int = 0,
    private val networkBlock: suspend (page: Int, pageSize: Int) -> R
) : PagingSource<Int, D>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, D> {
        val position = params.key ?: startingIndex
        return try {
            val response = networkBlock(position, params.loadSize)
            LoadResult.Page(
                data = response.items,
                prevKey = if (position == startingIndex) null else position,
                nextKey = if (response.items.isEmpty()) null else position + 1
            )
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }
    }
}