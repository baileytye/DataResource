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

import com.baileytye.dataresource.model.NetworkResult

/**
 * Mapper to convert a throwable thrown in a network block to a network result
 */
interface NetworkErrorMapper {

    /**
     * Maps throwable to network result
     * @param throwable to conver
     * @return network result wrapping network data type T
     */
    fun mapNetworkError(throwable: Throwable) : NetworkResult<Nothing>
}