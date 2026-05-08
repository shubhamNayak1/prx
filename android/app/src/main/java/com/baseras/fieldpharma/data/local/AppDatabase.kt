package com.baseras.fieldpharma.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "attendance")
data class AttendanceEntity(
    @PrimaryKey val date: String,
    val punchInAt: String? = null,
    val punchInLat: Double? = null,
    val punchInLng: Double? = null,
    val punchInPhotoPath: String? = null,
    val punchInPhotoUrl: String? = null,
    val punchOutAt: String? = null,
    val punchOutLat: Double? = null,
    val punchOutLng: Double? = null,
    val syncedAt: Long? = null,
)

@Entity(tableName = "pending_sync")
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payloadJson: String,
    val localPhotoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
)

@Entity(tableName = "client_cache")
data class ClientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val speciality: String? = null,
    val address: String? = null,
    val city: String? = null,
    val pincode: String? = null,
    val phone: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val syncedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "visits")
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: String? = null,
    val clientId: String,
    val checkInAt: String,
    val checkInLat: Double? = null,
    val checkInLng: Double? = null,
    val checkOutAt: String? = null,
    val checkOutLat: Double? = null,
    val checkOutLng: Double? = null,
    val notes: String? = null,
    val productsJson: String? = null,
    val state: String = "IN_PROGRESS",
)

@Entity(tableName = "edetail_decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    val product: String? = null,
    val syncedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "edetail_slides")
data class SlideEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val order: Int,
    val title: String? = null,
    val imageUrl: String,
)

@Database(
    entities = [
        AttendanceEntity::class,
        PendingSyncEntity::class,
        ClientEntity::class,
        VisitEntity::class,
        DeckEntity::class,
        SlideEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun attendanceDao(): AttendanceDao
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun clientDao(): ClientDao
    abstract fun visitDao(): VisitDao
    abstract fun deckDao(): DeckDao
    abstract fun slideDao(): SlideDao

    companion object {
        fun create(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "fieldpharma.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
