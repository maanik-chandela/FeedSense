package com.example.feedsense.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.feedsense.dao.CaptureDao
import com.example.feedsense.dao.FeedItemDao
import com.example.feedsense.dao.ObservationDao
import com.example.feedsense.dao.ProjectDao
import com.example.feedsense.dao.ReferenceDao
import com.example.feedsense.dao.SessionDao
import com.example.feedsense.model.CapturedFrame
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ResearchObservation
import com.example.feedsense.model.ResearchProject
import com.example.feedsense.model.ResearchSession

@Database(
    entities = [
        ResearchProject::class,
        ResearchSession::class,
        ResearchObservation::class,
        CapturedFrame::class,
        LabeledReference::class,
        FeedItem::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class FeedSenseDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao

    abstract fun sessionDao(): SessionDao

    abstract fun observationDao(): ObservationDao

    abstract fun captureDao(): CaptureDao

    abstract fun referenceDao(): ReferenceDao

    abstract fun feedItemDao(): FeedItemDao
}