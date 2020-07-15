/*
 *  Copyright [2020] [Bailey Tye]
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.baileytye.dataresource.networkBoundResource

import com.baileytye.dataresource.model.ErrorMessagesResource
import com.baileytye.dataresource.model.NetworkResult
import java.io.IOException

/**
 * A default network error mapper which maps IO exceptions as network errors, not implemented
 * as generic error, and everything else as 'unknown'
 */
class DefaultNetworkErrorMapper (private val errorMessages: ErrorMessagesResource) :
    NetworkErrorMapper {
    override fun mapNetworkError(throwable: Throwable): NetworkResult<Nothing> {
        return when (throwable) {
            is IOException -> NetworkResult.NetworkError
            is NotImplementedError -> NetworkResult.GenericError(null, throwable.message)
            else -> NetworkResult.GenericError(null, errorMessages.unknown)
        }
    }
}