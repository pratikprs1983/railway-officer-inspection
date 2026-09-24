package com.example.data

import com.example.MyApplication
import com.example.models.LCGate
import com.example.models.Section
import com.example.models.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MasterDataRepository {
    private val masterDataDao = MyApplication.instance.database.masterDataDao()

    fun getStations(): Flow<List<Station>> = masterDataDao.getStations()

    fun saveStation(station: Station, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                masterDataDao.insertStation(station)
                FirebaseSyncManager.syncStation(station)
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun deleteStation(station: Station, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                masterDataDao.deleteStation(station)
                FirebaseSyncManager.deleteStation(station.id)
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun getSections(): Flow<List<Section>> = masterDataDao.getSections()

    fun saveSection(section: Section, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                masterDataDao.insertSection(section)
                FirebaseSyncManager.syncSection(section)
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun deleteSection(section: Section, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                masterDataDao.deleteSection(section)
                FirebaseSyncManager.deleteSection(section.id)
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun getLCGates(): Flow<List<LCGate>> = masterDataDao.getLCGates()

    fun saveLCGate(lcGate: LCGate, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                masterDataDao.insertLCGate(lcGate)
                FirebaseSyncManager.syncLCGate(lcGate)
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun deleteLCGate(lcGate: LCGate, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                masterDataDao.deleteLCGate(lcGate)
                FirebaseSyncManager.deleteLCGate(lcGate.id)
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }
}
