package com.example.ambientpixel.data

class NoteRepository(private val noteDao: NoteDao) {
    fun observeNotes() = noteDao.observeAll()

    suspend fun saveNote(note: NoteEntity): Long {
        return noteDao.insert(note)
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.update(note)
    }

    suspend fun getNote(id: Long): NoteEntity? {
        return noteDao.getById(id)
    }
}
