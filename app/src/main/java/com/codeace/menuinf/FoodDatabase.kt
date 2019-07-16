package com.codeace.menuinf

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FoodData::class], version = 1)
abstract class FoodDatabase : RoomDatabase() {

    abstract fun foodDataDao(): FoodDataDao

    companion object {

        @Volatile
        private var INSTANCE: FoodDatabase? = null

        internal fun getDatabase(context: Context): FoodDatabase? {
            if (INSTANCE == null) {
                synchronized(FoodDatabase::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = Room.databaseBuilder(
                            context.applicationContext,
                            FoodDatabase::class.java, "word_database"
                        )
                            .build()
                    }
                }
            }
            return INSTANCE
        }
    }
}