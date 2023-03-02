package me.rahimklaber.frosttestapp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ReceivedMessageDao {
    @Query("select * from receivedmessage where unix_time > :afterUnixTime")
    fun getAllAfterTime(afterUnixTime: Long) : List<ReceivedMessage>

    @Insert
    fun insertReceivedMessage(msg: ReceivedMessage)
}