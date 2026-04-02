package org.ghostsinthelab.app.rakurakuime.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DictionaryEntry::class], version = 1, exportSchema = false)
abstract class ImeDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        @Volatile
        private var INSTANCE: ImeDatabase? = null

        fun getDatabase(context: Context): ImeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ImeDatabase::class.java,
                    "ime_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
