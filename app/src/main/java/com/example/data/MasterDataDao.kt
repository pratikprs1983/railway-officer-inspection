package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.models.LCGate
import com.example.models.Section
import com.example.models.Station
import kotlinx.coroutines.flow.Flow

@Dao
interface MasterDataDao {
    @Query("SELECT * FROM stations ORDER BY stationName ASC")
    fun getStations(): Flow<List<Station>>

    @Query("SELECT * FROM stations")
    suspend fun getAllStationsList(): List<Station>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: Station)

    @Delete
    suspend fun deleteStation(station: Station)

    @Query("SELECT * FROM sections ORDER BY sectionName ASC")
    fun getSections(): Flow<List<Section>>

    @Query("SELECT * FROM sections")
    suspend fun getAllSectionsList(): List<Section>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(section: Section)

    @Delete
    suspend fun deleteSection(section: Section)

    @Query("SELECT * FROM lc_gates ORDER BY lcNumber ASC")
    fun getLCGates(): Flow<List<LCGate>>

    @Query("SELECT * FROM lc_gates")
    suspend fun getAllLCGatesList(): List<LCGate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLCGate(lcGate: LCGate)

    @Delete
    suspend fun deleteLCGate(lcGate: LCGate)
}
