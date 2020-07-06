package com.baileytye.dataresource.model

interface ErrorMessagesResource {
    val networkTimeout: String
    val genericNetwork: String
    val cacheTimeout: String
    val unknown: String
    val noConnection : String
}