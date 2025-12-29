package com.example.ambientpixel.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ambientpixel.data.NoteDatabase
import com.example.ambientpixel.data.NoteRepository

class ConsultationViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConsultationViewModel::class.java)) {
            val database = NoteDatabase.getInstance(appContext)
            val repository = NoteRepository(database.noteDao())
            @Suppress("UNCHECKED_CAST")
            return ConsultationViewModel(appContext, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
