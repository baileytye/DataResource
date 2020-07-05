package model

data class DefaultErrorMessages(
    override val networkTimeout: String = "Network timeout",
    override val genericNetwork: String = "Network error",
    override val cacheTimeout: String = "Cache timeout",
    override val unknown: String = "Something went wrong",
    override val noConnection : String = "No connection"
) : ErrorMessagesResource