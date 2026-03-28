package net.aginx.controller.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.aginx.controller.db.dao.*
import net.aginx.controller.db.entities.*

@Database(
    entities = [
        AginxEntity::class,
        AgentEntity::class,
        MessageEntity::class,
        ConversationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun aginxDao(): AginxDao
    abstract fun agentDao(): AgentDao
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "aginx_controller.db"
            ).fallbackToDestructiveMigration().build()
        }
    }
}
