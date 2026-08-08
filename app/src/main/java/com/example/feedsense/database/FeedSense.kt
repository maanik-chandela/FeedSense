package com.example.feedsense.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.feedsense.dao.ProjectDao
import com.example.feedsense.dao.SessionDao
import com.example.feedsense.model.ResearchProject
import com.example.feedsense.model.ResearchSession

@Database(
    entities = [
        ResearchProject::class,
        ResearchSession::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class FeedSenseDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao

    abstract fun sessionDao(): SessionDao
}