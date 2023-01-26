package me.rahimklaber.frosttestapp.ipv8.message

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex

data class Preprocess(val bytes: ByteArray) : FrostMessage {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Preprocess

        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun serialize(): ByteArray {
        return bytes
    }
    companion object Deserializer : Deserializable<Preprocess>{
        const val MESSAGE_ID = 4;
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<Preprocess, Int> {
            return Preprocess(buffer.slice(offset until buffer.size).toByteArray()) to buffer.size

        }

    }
}
data class SignShare(val bytes: ByteArray) : FrostMessage {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignShare

        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun serialize(): ByteArray {
        return bytes
    }

    companion object Deserializer : Deserializable<SignShare>{
        const val MESSAGE_ID = 5;
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<SignShare, Int> {
            return SignShare(buffer.slice(offset until buffer.size).toByteArray()) to buffer.size
        }

    }
}

data class SignRequest(val id: Long, val data: ByteArray) : FrostMessage {
    override fun serialize(): ByteArray {
        return "$id#${data.toHex()}".toByteArray(Charsets.UTF_8)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignRequest

        if (id != other.id) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object Deserializer : Deserializable<SignRequest>{
        const val MESSAGE_ID = 6
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<SignRequest, Int> {
            val (idstr, byteshex) = buffer.slice(offset until  buffer.size)
                .toByteArray()
                .toString(Charsets.UTF_8)
                .split("#")
            return SignRequest(
                idstr.toLong(),
                byteshex.hexToBytes()
            ) to buffer.size
        }
    }

}

data class SignRequestResponse(val id: Long, val ok: Boolean) : FrostMessage {
    override fun serialize(): ByteArray {
        return "$id#$ok".toByteArray(Charsets.UTF_8)
    }

    companion object Deserializer: Deserializable<SignRequestResponse>{
        const val MESSAGE_ID = 7
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<SignRequestResponse, Int> {
            val (idstr, okstr) = buffer.slice(offset until buffer.size)
                .toByteArray()
                .toString(Charsets.UTF_8)
                .split("#")
            return SignRequestResponse(idstr.toLong(),okstr.toBooleanStrict()) to buffer.size

        }

    }
}