package com.gatita.feature.settings

import com.gatita.domain.repository.*
import com.gatita.domain.usecase.SaveSettingsUseCase
import com.gatita.feature.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val main=MainDispatcherRule()
    private class Store:SettingsRepository {
        val value=MutableStateFlow(AppSettings("Original",false,"야간"))
        var failRead=false;var failSave=false
        override fun observe()=value
        override suspend fun get():AppSettings { if(failRead)throw java.io.IOException();return value.value }
        override suspend fun save(settings:AppSettings) { if(failSave)throw java.io.IOException();value.value=settings }
    }
    @Test fun loadingFailureCannotOverwriteExistingDefaultsAndCanRetry()=runTest {
        val store=Store();store.failRead=true;val vm=SettingsViewModel(store,SaveSettingsUseCase(store))
        assertTrue(vm.state.value.loadFailed);vm.save(true);advanceUntilIdle()
        assertEquals("Original",store.value.value.driverName)
        store.failRead=false;vm.load();advanceUntilIdle();assertEquals("Original",vm.state.value.draft.driverName)
    }
    @Test fun invalidNameAndStorageFailureKeepExistingSettingsAndDraft()=runTest {
        val store=Store();val vm=SettingsViewModel(store,SaveSettingsUseCase(store))
        vm.edit(AppSettings("가".repeat(21)));vm.save();advanceUntilIdle();assertEquals("Original",store.value.value.driverName)
        vm.edit(AppSettings("New",true,"주간"));store.failSave=true;vm.save();advanceUntilIdle()
        assertEquals("New",vm.state.value.draft.driverName);assertEquals("Original",vm.state.value.saved.driverName);assertFalse(vm.state.value.saving)
        store.failSave=false;vm.save();advanceUntilIdle();assertEquals("New",vm.state.value.saved.driverName)
    }
    @Test fun resetRestoresOnlyPreferences()=runTest {
        val store=Store();val vm=SettingsViewModel(store,SaveSettingsUseCase(store));vm.save(true);advanceUntilIdle()
        assertEquals(AppSettings(),store.value.value);assertEquals(AppSettings(),vm.state.value.draft)
    }
}
