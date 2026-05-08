package com.baseras.fieldpharma.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE date = :date LIMIT 1")
    suspend fun get(date: String): AttendanceEntity?

    @Query("SELECT * FROM attendance WHERE date = :date LIMIT 1")
    fun observe(date: String): Flow<AttendanceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: AttendanceEntity)
}

@Dao
interface PendingSyncDao {
    @Insert
    suspend fun add(item: PendingSyncEntity): Long

    @Query("SELECT * FROM pending_sync ORDER BY createdAt ASC LIMIT :limit")
    suspend fun next(limit: Int = 50): List<PendingSyncEntity>

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_sync SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: Long)

    @Query("SELECT COUNT(*) FROM pending_sync")
    fun observeCount(): Flow<Int>
}

@Dao
interface ClientDao {
    @Query("SELECT * FROM client_cache ORDER BY name ASC")
    fun observeAll(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM client_cache WHERE LOWER(name) LIKE '%' || LOWER(:q) || '%' ORDER BY name ASC")
    fun search(q: String): Flow<List<ClientEntity>>

    @Query("SELECT * FROM client_cache WHERE type = :type ORDER BY name ASC")
    fun byType(type: String): Flow<List<ClientEntity>>

    @Query("SELECT * FROM client_cache WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(client: ClientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(clients: List<ClientEntity>)

    @Query("DELETE FROM client_cache")
    suspend fun clear()
}

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits ORDER BY checkInAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE state = 'IN_PROGRESS' LIMIT 1")
    suspend fun activeVisit(): VisitEntity?

    @Query("SELECT * FROM visits WHERE localId = :id")
    suspend fun get(id: Long): VisitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(visit: VisitEntity): Long

    @Update
    suspend fun update(visit: VisitEntity)

    @Query("SELECT * FROM visits WHERE state = 'COMPLETED' AND serverId IS NULL")
    suspend fun unsynced(): List<VisitEntity>

    @Query("UPDATE visits SET serverId = :serverId, state = 'SYNCED' WHERE localId = :localId")
    suspend fun markSynced(localId: Long, serverId: String)
}

@Dao
interface DeckDao {
    @Query("SELECT * FROM edetail_decks ORDER BY name ASC")
    fun observeAll(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM edetail_decks WHERE id = :id")
    suspend fun get(id: String): DeckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(decks: List<DeckEntity>)
}

@Dao
interface SlideDao {
    @Query("SELECT * FROM edetail_slides WHERE deckId = :deckId ORDER BY `order` ASC")
    suspend fun byDeck(deckId: String): List<SlideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(slides: List<SlideEntity>)

    @Query("DELETE FROM edetail_slides WHERE deckId = :deckId")
    suspend fun deleteByDeck(deckId: String)
}
