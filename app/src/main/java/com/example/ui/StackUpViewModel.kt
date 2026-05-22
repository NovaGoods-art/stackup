package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HabitChain
import com.example.data.HabitCompletion
import com.example.data.HabitRepository
import com.example.data.HabitStep
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ChainWithDetails(
    val chain: HabitChain,
    val steps: List<HabitStep>,
    val completionsToday: Set<Int>, // set of stepIds completed today
    val streak: Int,
    val isFullyCompletedToday: Boolean,
    val isAtRisk: Boolean
)

data class HeatmapDay(
    val date: LocalDate,
    val dateString: String,
    val dayOfWeekLabel: String, // e.g. "M", "T"
    val completionRatio: Float // 0f to 1f
)

data class StackUpUiState(
    val chains: List<ChainWithDetails> = emptyList(),
    val heatmap: List<HeatmapDay> = emptyList(),
    val averageStreak: Int = 0,
    val nudgeChains: List<ChainWithDetails> = emptyList()
)

class StackUpViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: HabitRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = HabitRepository(database.habitDao())
    }

    // Combine flows to produce the app state dynamically
    val uiState: StateFlow<StackUpUiState> = combine(
        repository.allChains,
        repository.allSteps,
        repository.allCompletions
    ) { chains, steps, completions ->
        val todayStr = LocalDate.now().toString()
        val stepsByChain = steps.groupBy { it.chainId }
        val completionsByStepAndDate = completions.groupBy { it.stepId }

        // Compile chains and their live dynamic status
        val chainsWithDetails = chains.map { chain ->
            val chainSteps = stepsByChain[chain.id] ?: emptyList()
            val stepIds = chainSteps.map { it.id }.toSet()

            // Completions for this chain's steps on today's date
            val completionsToday = completions
                .filter { it.chainId == chain.id && it.date == todayStr }
                .map { it.stepId }
                .toSet()

            val (streak, isAtRisk) = calculateStreakAndRisk(chain.id, stepIds, completions)
            val isFullyCompletedToday = stepIds.isNotEmpty() && completionsToday.size == stepIds.size

            ChainWithDetails(
                chain = chain,
                steps = chainSteps,
                completionsToday = completionsToday,
                streak = streak,
                isFullyCompletedToday = isFullyCompletedToday,
                isAtRisk = isAtRisk
            )
        }

        // Filter chains that are currently "at risk" to trigger nudges
        val nudgeChains = chainsWithDetails.filter { it.isAtRisk }

        // Compute average streak
        val avgStreak = if (chainsWithDetails.isNotEmpty()) {
            chainsWithDetails.map { it.streak }.average().toInt()
        } else {
            0
        }

        // Build weekly completion heatmap (last 7 days)
        val heatmap = (0..6).reversed().map { daysAgo ->
            val date = LocalDate.now().minusDays(daysAgo.toLong())
            val dateStr = date.toString()
            val dayLabel = date.dayOfWeek.name.take(1)

            // Calculate overall ratio of completed steps for that day across all active chains
            var totalStepsOnDay = 0
            var completedStepsOnDay = 0

            chainsWithDetails.forEach { chainDetails ->
                val stepIds = chainDetails.steps.map { it.id }
                if (stepIds.isNotEmpty()) {
                    totalStepsOnDay += stepIds.size
                    completedStepsOnDay += completions.count { it.chainId == chainDetails.chain.id && it.date == dateStr && it.stepId in stepIds }
                }
            }

            val ratio = if (totalStepsOnDay > 0) {
                completedStepsOnDay.toFloat() / totalStepsOnDay.toFloat()
            } else {
                0f
            }

            HeatmapDay(
                date = date,
                dateString = dateStr,
                dayOfWeekLabel = dayLabel,
                completionRatio = ratio
            )
        }

        StackUpUiState(
            chains = chainsWithDetails,
            heatmap = heatmap,
            averageStreak = avgStreak,
            nudgeChains = nudgeChains
        )
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StackUpUiState()
    )

    // Calculate live streak and risk going backward from today
    private fun calculateStreakAndRisk(
        chainId: Int,
        stepIds: Set<Int>,
        completions: List<HabitCompletion>
    ): Pair<Int, Boolean> {
        if (stepIds.isEmpty()) return Pair(0, false)

        val completionsForChain = completions.filter { it.chainId == chainId }
        val completionsByDate = completionsForChain
            .groupBy { it.date }
            .mapValues { entry -> entry.value.map { it.stepId }.toSet() }

        var streak = 0
        var isBroken = false
        val today = LocalDate.now()
        val todayStr = today.toString()

        var checkDate = today
        while (!isBroken) {
            val checkStr = checkDate.toString()
            val completedStepsForDate = completionsByDate[checkStr] ?: emptySet()
            // A day is successfully stacked if we completed all steps
            val isFullyCompletedOnDate = stepIds.isNotEmpty() && completedStepsForDate.size == stepIds.size

            if (isFullyCompletedOnDate) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else {
                if (checkDate == today) {
                    // Not completed today yet is acceptable, streak is still alive because today is today.
                    checkDate = checkDate.minusDays(1)
                } else {
                    // Missing any past day ends the streak
                    isBroken = true
                }
            }
        }

        // At risk if streak is active (meaning they completed previously) but today is not yet done
        val completedToday = (completionsByDate[todayStr]?.size ?: 0) == stepIds.size
        val isAtRisk = streak > 0 && !completedToday

        return Pair(streak, isAtRisk)
    }

    // --- ACTIONS ---

    fun createChain(name: String, anchor: String, steps: List<String>) {
        if (name.isBlank() || anchor.isBlank() || steps.isEmpty()) return
        viewModelScope.launch {
            repository.addChainWithSteps(name, anchor, steps.filter { it.isNotBlank() })
        }
    }

    fun updateChain(chainId: Int, name: String, anchor: String, steps: List<String>) {
        if (name.isBlank() || anchor.isBlank() || steps.isEmpty()) return
        viewModelScope.launch {
            repository.editChainWithSteps(chainId, name, anchor, steps.filter { it.isNotBlank() })
        }
    }

    fun deleteChain(chain: HabitChain) {
        viewModelScope.launch {
            repository.deleteChain(chain)
        }
    }

    fun toggleStep(chainId: Int, stepId: Int, isCompleted: Boolean) {
        val todayStr = LocalDate.now().toString()
        viewModelScope.launch {
            repository.toggleStepCompletion(chainId, stepId, todayStr, isCompleted)
        }
    }
}
