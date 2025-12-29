package com.example.ambientpixel.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val createdAt: Long,
    val audioPath: String,
    val rawTranscript: String,
    val cleanedNote: String
)
