package networkBoundResource

interface Mapper<Network, Local> {
    fun networkToLocal(network : Network) : Local
    fun localToNetwork(local : Local) : Network
}