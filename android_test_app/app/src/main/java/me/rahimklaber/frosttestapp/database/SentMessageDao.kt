package me.rahimklaber.frosttestapp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SentMessageDao {
    @Query("select * from sentmessage where unix_time > :afterUnixTime")
    fun getAllAfterTime(afterUnixTime: Long) : List<SentMessage>

    @Insert
    fun insertSentMessage(msg: SentMessage)
}