package me.rahimklaber.frosttestapp.ipv8.message

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import java.nio.charset.Charset

data class RequestToJoinMessage(
    override val id: Long // some random id to keep track of the request
) : FrostMessage {
    override fun serialize(): ByteArray {
        return id.toString().toByteArray()
    }

    companion object Deserializer : Deserializable<RequestToJoinMessage> {
        const val MESSAGE_ID = 0
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<RequestToJoinMessage, Int> {
            return RequestToJoinMessage(
                buffer.slice(offset until buffer.size)
                    .toByteArray()
                    .toString(Charsets.UTF_8)
                    .toLong()
            ) to buffer.size
        }

    }
}