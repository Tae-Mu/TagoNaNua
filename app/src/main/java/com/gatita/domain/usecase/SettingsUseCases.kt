package com.gatita.domain.usecase

import com.gatita.domain.repository.*
import com.gatita.domain.calculator.CostValidator
import com.gatita.domain.model.*

class SaveSettingsUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(settings: AppSettings): DomainResult<AppSettings> {
        val name = CostValidator.normalizeDisplayName(settings.driverName)
        if (name is DomainResult.Failure) return name
        require(settings.appearance in listOf("시스템", "주간", "야간"))
        val clean = settings.copy(driverName=settings.driverName.trim())
        repository.save(clean)
        return DomainResult.Success(clean)
    }
}
