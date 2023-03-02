package me.rahimklaber.frosttestapp.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Me::class, ReceivedMessage::class, SentMessage::class, Request::class],
    version = 1
)
@TypeConverters(StringListConverter::class)
abstract class FrostDatabase : RoomDatabase(){
    abstract fun meDao(): MeDao
    abstract fun receivedMessageDao(): ReceivedMessageDao
    abstract fun sentMessageDao(): SentMessageDao
    abstract fun requestDao(): RequestDao
}