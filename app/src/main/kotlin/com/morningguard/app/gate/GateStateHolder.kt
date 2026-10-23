package com.morningguard.app.gate

import com.morningguard.data.gate.MorningGateRepository
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.gate.GateEvaluation
import com.morningguard.domain.gate.GateState
import com.morningguard.domain.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GateStateHolder @Inject constructor(
    private val morningGateRepository: MorningGateRepository,
    settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var latestEvaluation: GateEvaluation = GateEvaluation(GateState.INACTIVE, "", "init")

    @Volatile
    var latestSettings: UserSettings = UserSettings()

    init {
        scope.launch {
            refreshNow()
            combine(
                morningGateRepository.gateEvaluation,
                settingsRepository.settings,
            ) { evaluation, settings ->
                evaluation to settings
            }.collect { (evaluation, settings) ->
                latestEvaluation = evaluation
                latestSettings = settings
            }
        }
    }

    suspend fun refreshNow() {
        val (settings, evaluation) = morningGateRepository.currentSettingsAndEvaluation()
        latestSettings = settings
        latestEvaluation = evaluation
    }
}
