package work.kyanro.controllcommandcaller.repository

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.kyanro.controllcommandcaller.network.Button
import work.kyanro.controllcommandcaller.network.CccApiService
import work.kyanro.controllcommandcaller.network.Dpad

class ButtonRepository(private val apiClient: CccApiService) {
    var waiting = false
    var waitingDfd: Deferred<Unit>? = null

    private suspend fun exec(block: () -> Deferred<Unit>) {
        waiting = true
        waitingDfd = withContext(Dispatchers.IO) { block.invoke() }
        waitingDfd?.await()
        waiting = false
    }

    suspend fun push(button: Button) {
        if (waiting) return
        exec { apiClient.push(button) }
    }

    suspend fun hold(button: Button) {
        if (waiting) return
        exec { apiClient.hold(button) }
    }

    suspend fun release(button: Button) {
        if (waiting) return
        exec { apiClient.release(button) }
    }
}

class DpadRepository(private val apiClient: CccApiService) {
    var waiting = false
    var waitingDfd: Deferred<Unit>? = null

    private suspend fun exec(block: () -> Deferred<Unit>) {
        waiting = true
        waitingDfd = withContext(Dispatchers.IO) { block.invoke() }
        waitingDfd?.await()
        waiting = false
    }

    suspend fun push(button: Dpad) {
        if (waiting) return
        exec { apiClient.push(button) }
    }

    suspend fun hold(button: Dpad) {
        if (waiting) return
        exec { apiClient.hold(button) }
    }
}