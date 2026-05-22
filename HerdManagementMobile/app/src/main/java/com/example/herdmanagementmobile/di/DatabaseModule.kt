package com.example.herdmanagementmobile.di

import android.content.Context
import androidx.room.Room
import com.example.herdmanagementmobile.data.local.HerdDatabase
import com.example.herdmanagementmobile.data.local.dao.AnimalDao
import com.example.herdmanagementmobile.data.local.dao.EventDao
import com.example.herdmanagementmobile.data.local.dao.TreatmentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HerdDatabase =
        Room.databaseBuilder(context, HerdDatabase::class.java, HerdDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAnimalDao(db: HerdDatabase): AnimalDao = db.animalDao()

    @Provides
    fun provideEventDao(db: HerdDatabase): EventDao = db.eventDao()

    @Provides
    fun provideTreatmentDao(db: HerdDatabase): TreatmentDao = db.treatmentDao()
}
