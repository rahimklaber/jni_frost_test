package me.rahimklaber.frosttestapp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MeDao{
    @Query("Select * from me limit 1;")
    fun get() : Me?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(me: Me)
}