package com.example.herdmanagementmobile.data.repository

import com.example.herdmanagementmobile.data.local.dao.TreatmentDao
import com.example.herdmanagementmobile.data.local.entity.TreatmentEntity
import com.example.herdmanagementmobile.domain.model.Treatment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TreatmentRepository @Inject constructor(
    private val treatmentDao: TreatmentDao,
) {
    fun observeForAnimal(animalId: Long): Flow<List<Treatment>> =
        treatmentDao.observeForAnimal(animalId).map { list -> list.map(TreatmentEntity::toDomain) }

    suspend fun add(treatment: Treatment): Long =
        treatmentDao.insert(TreatmentEntity.fromDomain(treatment))

    suspend fun delete(id: Long) = treatmentDao.deleteById(id)
}
