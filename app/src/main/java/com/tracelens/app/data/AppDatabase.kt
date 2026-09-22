package com.tracelens.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CollectionEntity::class, ImageEntity::class, FaceEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun collections(): CollectionDao
    abstract fun images(): ImageDao
    abstract fun faces(): FaceDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tracelens.db").build()
    }
}
