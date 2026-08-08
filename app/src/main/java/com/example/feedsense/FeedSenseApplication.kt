package com.example.feedsense

import android.app.Application
import androidx.room.Room
import com.example.feedsense.database.FeedSenseDatabase
import com.example.feedsense.repository.ProjectRepository
import com.example.feedsense.repository.SessionRepository

class FeedSenseApplication : Application() {

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            FeedSenseDatabase::class.java,
            "feedsense_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    val repository by lazy {
        ProjectRepository(database.projectDao())
    }

    val sessionRepository by lazy {
        SessionRepository(database.sessionDao())
    }
}