package work.kyanro.controllcommandcaller.repository

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.kyanro.controllcommandcaller.network.Button
import work.kyanro.controllcommandcaller.network.CccApiService
import work.kyanro.controllcommandcaller.network.Dpad

class ButtonRepository(val apiClient: CccApiService) {
    var waiting = false
    var waitingDfd: Deferred<Unit>? = null

    suspend fun hold(button: Button) {
        if (waiting) return
        waiting = true
        waitingDfd = withContext(Dispatchers.IO) { apiClient.hold(button) }
        waitingDfd?.await()
        waiting = false
    }

    suspend fun release(button: Button) {
        if (waiting) return
        waiting = true
        waitingDfd = withContext(Dispatchers.IO) { apiClient.release(button) }
        waitingDfd?.await()
        waiting = false
    }
}

class DpadRepository(val apiClient: CccApiService) {
    var waiting = false
    var waitingDfd: Deferred<Unit>? = null

    suspend fun hold(button: Dpad) {
        if (waiting) return
        waiting = true
        waitingDfd = withContext(Dispatchers.IO) { apiClient.hold(button) }
        waitingDfd?.await()
        waiting = false
    }

    suspend fun release(button: Button) {
        if (waiting) return
        waiting = true
        waitingDfd = withContext(Dispatchers.IO) { apiClient.release(button) }
        waitingDfd?.await()
        waiting = false
    }
}