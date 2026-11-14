package com.gatita.feature.settings

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gatita.di.AppContainer
import com.gatita.domain.repository.*
import com.gatita.domain.usecase.SaveSettingsUseCase
import com.gatita.domain.model.DomainResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SettingsState(val saved: AppSettings = AppSettings(), val draft: AppSettings = AppSettings(), val loading: Boolean = true, val loadFailed: Boolean = false, val saving: Boolean = false, val message: String? = null)
class SettingsViewModel(private val repository: SettingsRepository, private val saveSettings: SaveSettingsUseCase): ViewModel() {
    private val mutable = MutableStateFlow(SettingsState())
    val state = mutable.asStateFlow()
    init { load() }
    fun load() { mutable.update { it.copy(loading=true,loadFailed=false,message=null) }; viewModelScope.launch {
        try { val value = repository.get(); mutable.value = SettingsState(value,value,loading=false) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.update { it.copy(loading=false,loadFailed=true,message="설정을 불러오지 못했어요. 다시 시도해 주세요.") } }
    } }
    fun edit(value: AppSettings) { if (!state.value.saving) mutable.update { it.copy(draft=value,message=null) } }
    fun save(reset: Boolean = false) {
        if (state.value.saving || state.value.loading || state.value.loadFailed) return
        val value = if(reset) AppSettings() else state.value.draft
        mutable.update { it.copy(saving=true,message=null) }
        viewModelScope.launch {
            try { when(val result=saveSettings(value)) {
                is DomainResult.Success -> mutable.update { it.copy(saved=result.value,draft=result.value,message=if(reset) "설정을 초기화했어요. 여행 기록은 그대로 보관돼요." else "설정을 저장했어요.") }
                is DomainResult.Failure -> mutable.update { it.copy(message="이름은 제어 문자 없이 20자 이하로 입력해 주세요.") }
            } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(message="저장하지 못했어요. 입력을 확인하고 다시 시도해 주세요.") } }
            finally { mutable.update { it.copy(saving=false) } }
        }
    }
    companion object { fun factory(c:AppContainer): ViewModelProvider.Factory = viewModelFactory { initializer { SettingsViewModel(c.settingsRepository,c.saveSettings) } } }
}
