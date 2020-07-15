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

/**
 * Mapper used to convert between network data models and local.
 *
 * This is required for a network bound resource to convert the received network
 * data to local data. If you are using the same model for network and remote,
 * simply return the same object in each converter method.
 */
interface Mapper<Network, Local> {

    /**
     * Convert from a network model to a local model
     * @param network data to convert
     * @return local version of input data
     */
    fun networkToLocal(network : Network) : Local

    /**
     * Convert from a local model to a network model
     * @param local data to convert
     * @return network version of input data
     */
    fun localToNetwork(local : Local) : Network
}