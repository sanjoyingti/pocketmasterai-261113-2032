package com.pocketmaster.ai

import android.app.Application
import com.pocketmaster.ai.data.DataStoreManager
import com.pocketmaster.ai.data.RoomDatabase
import com.pocketmaster.ai.data.SettingsRepository
import com.pocketmaster.ai.data.WebSearchRepository
import com.pocketmaster.ai.hardware.midi.CommandQueue
import com.pocketmaster.ai.hardware.midi.PocketMasterMidiManager
import com.pocketmaster.ai.hardware.midi.fake.FakePocketMasterDevice
import com.pocketmaster.ai.midi.PocketMasterProtocol
import com.pocketmaster.ai.model.DeviceMapRepository
import com.pocketmaster.ai.provider.AgentToolExecutor
import com.pocketmaster.ai.provider.openai.OpenAiCompatProvider
import com.pocketmaster.ai.ui.navigation.PocketMasterNavigation

/**
 * Single source of truth for all app services. Every screen, ViewModel and repository
 * takes its dependencies from here so the app stays testable and replaceable.
 */
class AppContainer private constructor(
    private val app: Application
) {

    // ---------- MIDI hardware ----------
    val protocol = PocketMasterProtocol
    val midiManager = PocketMasterMidiManager(app)
    val commandQueue = CommandQueue.get
    val deviceMapRepo = DeviceMapRepository.create(app)

    // ---------- Data ----------
    val dataStoreManager = DataStoreManager.create(app)
    val settings = SettingsRepository(app, dataStoreManager)
    val chatDao = RoomDatabase.create(app).chatDao
    val webSearch = WebSearchRepository

    // ---------- Fake offline device for unit tests ----------
    val fakeDevice = FakePocketMasterDevice()

    // ---------- AI ----------
    private val aiScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )
    val llmProvider = OpenAiCompatProvider(aiScope, settings)
    val toolExecutor = AgentToolExecutor(protocol, commandQueue, midiManager, deviceMapRepo, fakeDevice)

    companion object {
        @Volatile
        private var INSTANCE: AppContainer? = null

        fun create(app: Application): AppContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppContainer(app).also { INSTANCE = it }
            }
        }
    }
}
