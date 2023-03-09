package me.rahimklaber.frosttestapp.database

import androidx.room.*

class StringListConverter {
    @TypeConverter
    fun fromString(stringListString: String): List<String> {
        return stringListString.split(",").map { it }
    }

    @TypeConverter
    fun toString(stringList: List<String>): String {
        return stringList.joinToString(separator = ",")
    }
}
@Entity
data class Me(
    @PrimaryKey(autoGenerate = true) val id: Int? = null,
   @ColumnInfo("frost_key_share") val frostKeyShare: ByteArray,
    @ColumnInfo("frost_index") val frostIndex: Int,
    @ColumnInfo("frost_threshold") val frostThresholod: Int,
    @ColumnInfo("frost_n") val frostN: Int,
    @ColumnInfo("frost_members") val frostMembers: List<String>,//list of mids of members excluding me
)

@Entity
data class Request(
    @PrimaryKey(autoGenerate = true) val id : Int? = null,
    @ColumnInfo("unix_time") val unixTime: Long,
    @ColumnInfo("from_mid") val fromMid: String,
    @ColumnInfo("data", typeAffinity = ColumnInfo.BLOB) val data: ByteArray, // this is bassically the serialized msg
    @ColumnInfo("type") val type: Int,
    @ColumnInfo("request_id") val requestId: Long, // same as message_id
    @ColumnInfo("done") val done: Boolean = false
    )

@Entity
data class ReceivedMessage(
    @PrimaryKey(autoGenerate = true) val id : Int? = null,
    @ColumnInfo("from_mid") val fromMid: String,
    @ColumnInfo("unix_time") val unixTime: Long,
    @ColumnInfo("data", typeAffinity = ColumnInfo.BLOB) val data: ByteArray, // this is bassically the serialized msg
    @ColumnInfo("type") val type: Int,
    @ColumnInfo("message_id") val messageId: Long
)

@Entity
data class SentMessage(
    @PrimaryKey(autoGenerate = true) val id : Int? = null,
    @ColumnInfo("to_mid") val toMid: String?,
    @ColumnInfo("broadcast") val broadcast: Boolean,
    @ColumnInfo("unix_time") val unixTime: Long,
    @ColumnInfo("data", typeAffinity = ColumnInfo.BLOB) val data: ByteArray, // this is bassically the serialized msg
    @ColumnInfo("type") val type: Int,
    @ColumnInfo("message_id") val messageId: Long
)