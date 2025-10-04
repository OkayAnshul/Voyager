package com.cosmiclaboratory.voyager.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.cosmiclaboratory.voyager.data.database.converter.Converters
import com.cosmiclaboratory.voyager.data.database.dao.*
import com.cosmiclaboratory.voyager.data.database.entity.*
import com.cosmiclaboratory.voyager.data.database.migration.DatabaseMigrations
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        LocationEntity::class,
        PlaceEntity::class,
        VisitEntity::class,
        GeofenceEntity::class,
        CurrentStateEntity::class
    ],
    version = 3, // CRITICAL: Incremented version for foreign key constraints
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class VoyagerDatabase : RoomDatabase() {
    
    abstract fun locationDao(): LocationDao
    abstract fun placeDao(): PlaceDao
    abstract fun visitDao(): VisitDao
    abstract fun geofenceDao(): GeofenceDao
    abstract fun currentStateDao(): CurrentStateDao
    
    companion object {
        private const val DATABASE_NAME = "voyager_database"
        
        fun create(context: Context, passphrase: String): VoyagerDatabase {
            // Create SQLCipher factory
            val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))
            
            return Room.databaseBuilder(
                context.applicationContext,
                VoyagerDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS) // CRITICAL: Proper migrations for data integrity
                .build()
        }
    }
}