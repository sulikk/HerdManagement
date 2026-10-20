package com.example.herdmanagementmobile.data.repository

import com.example.herdmanagementmobile.data.local.dao.AnimalDao
import com.example.herdmanagementmobile.data.local.dao.EventDao
import com.example.herdmanagementmobile.data.local.dao.TreatmentDao
import com.example.herdmanagementmobile.data.local.entity.AnimalEntity
import com.example.herdmanagementmobile.data.local.entity.EventEntity
import com.example.herdmanagementmobile.data.local.entity.TreatmentEntity
import com.example.herdmanagementmobile.domain.stats.HerdStatistics
import com.example.herdmanagementmobile.domain.stats.HerdStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Udostępnia statystyki stada obliczane na podstawie bazy lokalnej. */
@Singleton
class StatisticsRepository @Inject constructor(
    private val animalDao: AnimalDao,
    private val eventDao: EventDao,
    private val treatmentDao: TreatmentDao,
) {
    fun observeStats(): Flow<HerdStats> = combine(
        animalDao.observeAll(),
        eventDao.observeAll(),
        treatmentDao.observeAll(),
    ) { animals, events, treatments ->
        HerdStatistics.compute(
            animals = animals.map(AnimalEntity::toDomain),
            events = events.map(EventEntity::toDomain),
            treatments = treatments.map(TreatmentEntity::toDomain),
            today = LocalDate.now(),
        )
    }
}
