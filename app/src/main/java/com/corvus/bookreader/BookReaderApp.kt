package com.corvus.bookreader

import android.app.Application
import androidx.room.Room
import com.corvus.bookreader.data.db.AppDatabase

class BookReaderApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = Room.databaseBuilder(this, AppDatabase::class.java, "bookreader.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    companion object {
        lateinit var instance: BookReaderApp
            private set
    }
}
