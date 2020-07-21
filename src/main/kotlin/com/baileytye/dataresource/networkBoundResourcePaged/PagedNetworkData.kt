package com.baileytye.dataresource.networkBoundResourcePaged

interface PagedNetworkData<T> {
    val items: List<T>
}