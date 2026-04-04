// data/local/AppDatabase.kt
package com.example.rush_hz_plus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.rush_hz_plus.data.local.converter.Converters
import com.example.rush_hz_plus.data.local.dao.DetectionDao
import com.example.rush_hz_plus.data.local.dao.GuardianNotificationDao
import com.example.rush_hz_plus.data.local.entity.DetectionResultEntity
import com.example.rush_hz_plus.data.local.entity.GuardianNotificationEntity

@Database(
    entities = [DetectionResultEntity::class, GuardianNotificationEntity::class],
    version = 3,
    exportSchema = true // 스키마 추출 활성화 (버전 관리용)
)
@TypeConverters(Converters::class) // 컨버터 등록
abstract class AppDatabase : RoomDatabase() {

    abstract fun detectionDao(): DetectionDao
    abstract fun guardianNotificationDao(): GuardianNotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
//                db.execSQL("ALTER TABLE detection_results ADD COLUMN alertStatus TEXT DEFAULT 'PENDING'")
//                db.execSQL("ALTER TABLE detection_results ADD COLUMN alertType TEXT DEFAULT ''")
//                db.execSQL("ALTER TABLE detection_results ADD COLUMN alertTimestamp INTEGER DEFAULT 0")
//                db.execSQL("ALTER TABLE detection_results ADD COLUMN alertMessage TEXT DEFAULT ''")
//                db.execSQL("ALTER TABLE detection_results ADD COLUMN guardianNotified INTEGER DEFAULT 0")
//                db.execSQL("ALTER TABLE detection_results ADD COLUMN guardianNotificationType TEXT DEFAULT ''")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `guardian_notifications` (
                        `detectionId` INTEGER NOT NULL,
                        `guardianId` TEXT NOT NULL,
                        `notified` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`detectionId`, `guardianId`),
                        FOREIGN KEY(`detectionId`) REFERENCES `detection_results`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rush_hz_plus_database"
                )
                    .fallbackToDestructiveMigration() // ← 기존 DB 삭제 후 새 스키마로 생성
                    .addMigrations(MIGRATION_1_2) // 마이그레이션만 사용
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}