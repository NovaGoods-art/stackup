package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- ENTITIES ---

@Entity(tableName = "habit_chains")
data class HabitChain(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val anchor: String,
    val createdAt: Long = System.currentTimeMillis(),
    val streak: Int = 0,
    val lastCompletedDate: String? = null // Format: YYYY-MM-DD
)

@Entity(
    tableName = "habit_steps",
    foreignKeys = [
        ForeignKey(
            entity = HabitChain::class,
            parentColumns = ["id"],
            childColumns = ["chainId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chainId"])]
)
data class HabitStep(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val chainId: Int,
    val action: String,
    val orderIndex: Int
)

@Entity(
    tableName = "habit_completions",
    indices = [Index(value = ["stepId", "date"], unique = true)]
)
data class HabitCompletion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val chainId: Int,
    val stepId: Int,
    val date: String, // YYYY-MM-DD
    val timestamp: Long = System.currentTimeMillis()
)

// --- DAO ---

@Dao
interface HabitDao {
    @Query("SELECT * FROM habit_chains ORDER BY createdAt DESC")
    fun getChainsFlow(): Flow<List<HabitChain>>

    @Query("SELECT * FROM habit_chains WHERE id = :chainId LIMIT 1")
    suspend fun getChainById(chainId: Int): HabitChain?

    @Query("SELECT * FROM habit_steps ORDER BY chainId, orderIndex ASC")
    fun getStepsFlow(): Flow<List<HabitStep>>

    @Query("SELECT * FROM habit_steps WHERE chainId = :chainId ORDER BY orderIndex ASC")
    suspend fun getStepsForChain(chainId: Int): List<HabitStep>

    @Query("SELECT * FROM habit_completions")
    fun getCompletionsFlow(): Flow<List<HabitCompletion>>

    @Query("SELECT * FROM habit_completions WHERE date = :date")
    fun getCompletionsByDateFlow(date: String): Flow<List<HabitCompletion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChain(chain: HabitChain): Long

    @Update
    suspend fun updateChain(chain: HabitChain)

    @Delete
    suspend fun deleteChain(chain: HabitChain)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: HabitStep): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<HabitStep>)

    @Query("DELETE FROM habit_steps WHERE chainId = :chainId")
    suspend fun deleteStepsForChain(chainId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: HabitCompletion): Long

    @Query("DELETE FROM habit_completions WHERE stepId = :stepId AND date = :date")
    suspend fun deleteCompletion(stepId: Int, date: String)

    @Query("DELETE FROM habit_completions WHERE chainId = :chainId")
    suspend fun deleteCompletionsByChain(chainId: Int)
}

// --- DATABASE ---

@Database(
    entities = [HabitChain::class, HabitStep::class, HabitCompletion::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stackup_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- REPOSITORY ---

class HabitRepository(private val habitDao: HabitDao) {
    val allChains: Flow<List<HabitChain>> = habitDao.getChainsFlow()
    val allSteps: Flow<List<HabitStep>> = habitDao.getStepsFlow()
    val allCompletions: Flow<List<HabitCompletion>> = habitDao.getCompletionsFlow()

    suspend fun addChainWithSteps(name: String, anchor: String, steps: List<String>): Int {
        val chain = HabitChain(name = name, anchor = anchor)
        val chainId = habitDao.insertChain(chain).toInt()
        val habitSteps = steps.mapIndexed { idx, action ->
            HabitStep(chainId = chainId, action = action, orderIndex = idx)
        }
        habitDao.insertSteps(habitSteps)
        return chainId
    }

    suspend fun updateChain(chain: HabitChain) {
        habitDao.updateChain(chain)
    }

    suspend fun editChainWithSteps(chainId: Int, name: String, anchor: String, steps: List<String>) {
        val oldChain = habitDao.getChainById(chainId) ?: return
        val updatedChain = oldChain.copy(name = name, anchor = anchor)
        habitDao.updateChain(updatedChain)
        habitDao.deleteStepsForChain(chainId)
        val habitSteps = steps.mapIndexed { idx, action ->
            HabitStep(chainId = chainId, action = action, orderIndex = idx)
        }
        habitDao.insertSteps(habitSteps)
    }

    suspend fun deleteChain(chain: HabitChain) {
        habitDao.deleteChain(chain)
        // Foreign keys with cascade trigger automatic deletion of HabitSteps from DB!
        // Also manually delete completions so the integrity is kept
        habitDao.deleteCompletionsByChain(chain.id)
    }

    suspend fun toggleStepCompletion(chainId: Int, stepId: Int, date: String, isCompleted: Boolean) {
        if (isCompleted) {
            val completion = HabitCompletion(chainId = chainId, stepId = stepId, date = date)
            habitDao.insertCompletion(completion)
        } else {
            habitDao.deleteCompletion(stepId, date)
        }
    }

    suspend fun getChainSteps(chainId: Int): List<HabitStep> {
        return habitDao.getStepsForChain(chainId)
    }
}
