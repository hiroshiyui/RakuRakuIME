package org.ghostsinthelab.app.rakurakuime.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "dictionary",
    indices = [Index(value = ["keystroke"])]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val keystroke: String,
    val character: String,
    val frequency: Int = 0
)
