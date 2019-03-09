package work.kyanro.controllcommandcaller

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import work.kyanro.controllcommandcaller.databinding.ActivityMainBinding
import work.kyanro.controllcommandcaller.di.NetworkModule
import work.kyanro.controllcommandcaller.network.Button
import work.kyanro.controllcommandcaller.network.CccApiService
import work.kyanro.controllcommandcaller.network.Dpad
import work.kyanro.controllcommandcaller.repository.ButtonRepository
import work.kyanro.controllcommandcaller.repository.DpadRepository
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

private val bombIntervalSecDefault = 5
private val bombIntervalSecMin = 3
private val bombIntervalSecMax = 10
private val bombIntervalStepSec = 1
private val yDegMargin = 10
private val zDegMargin = 3

open class MainActivity : AppCompatActivity(), SensorEventListener, CoroutineScope {

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    private lateinit var sensorManager: SensorManager

    private val compositeDisposable = CompositeDisposable()

    class BombManager(val job: Job, val buttonRepository: ButtonRepository) {
        private val bombIntervalMillis = 5000

        /** bombをおけるMAXの数 */
        var stock = Int.MAX_VALUE
        /** bombを置ける間隔 */
        var lastPutTimeMillis = System.currentTimeMillis()

        var noPutCallback: ((remainTimeMillis: Long) -> Unit)? = null

        fun put() {
            if (stock == 0) return
            val now = System.currentTimeMillis()
            val intervalMillis = now - lastPutTimeMillis
            if (intervalMillis < bombIntervalMillis) {
                val remainTimeMillis: Long = bombIntervalMillis - intervalMillis
                noPutCallback?.invoke(remainTimeMillis)
                return
            }
            lastPutTimeMillis = now
            stock -= 1

            CoroutineScope(Dispatchers.IO + job).launch {
                buttonRepository.push(Button.B)
            }
        }
    }

    class ViewModel {
        var currentBombIntervalTime = MutableLiveData<Int>().apply { value = bombIntervalSecDefault }

        fun incBombInterval() {
            val current = currentBombIntervalTime.value ?: bombIntervalSecDefault
            val next = current + bombIntervalStepSec
            currentBombIntervalTime.value = next.coerceIn(bombIntervalSecMin, bombIntervalSecMax)
        }

        fun decBombInterval() {
            val current = currentBombIntervalTime.value ?: bombIntervalSecDefault
            val next = current - bombIntervalStepSec
            currentBombIntervalTime.value = next.coerceIn(bombIntervalSecMin, bombIntervalSecMax)
        }
    }

    private lateinit var binding: ActivityMainBinding
    //    private fun Int.toPositiveDeg() = (this + 180) % 360
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        sensorManager = getSystemService() ?: throw IllegalStateException("センサーが利用できる端末で利用してください")

        val viewModel = ViewModel()

        val api = getClient()
        val buttonRepository = ButtonRepository(api)
        val dpadRepository = DpadRepository(api)

        init(dpadRepository, buttonRepository, viewModel)

        binding.start.setOnClickListener { init(dpadRepository, buttonRepository, viewModel) }
        binding.stop.setOnClickListener {
            launch {
                releaseAllButton(dpadRepository, buttonRepository)
                compositeDisposable.clear()
            }
        }

        val bombManager = BombManager(job, buttonRepository).apply {
            noPutCallback = { restTimeMillis ->
                val restTimeSec = String.format("%.1f", restTimeMillis / 1000f)
                Snackbar.make(binding.root, "爆弾はあと ${restTimeSec}秒でおけるようになるよ", Snackbar.LENGTH_LONG).show()
            }
        }
        binding.bomb.setOnClickListener { bombManager.put() }
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
    }

    private fun init(
        dpadRepository: DpadRepository,
        buttonRepository: ButtonRepository,
        viewModel: ViewModel
    ) {
        launch {
            releaseAllButton(dpadRepository, buttonRepository)
            initController(dpadRepository, buttonRepository, viewModel)
        }
    }

    private suspend fun releaseAllButton(dpadRepository: DpadRepository, buttonRepository: ButtonRepository) {
        dpadRepository.hold(Dpad.None)
        buttonRepository.run {
            release(Button.B)
            release(Button.A)
        }
    }

    private fun initController(
        dpadRepository: DpadRepository,
        buttonRepository: ButtonRepository,
        viewModel: ViewModel
    ) {
        val disposable = radianSubject
            .throttleFirst(500, TimeUnit.MILLISECONDS)
            .filter { Math.abs(it.degY) > yDegMargin || Math.abs(it.degZ) > zDegMargin }
            .subscribe {
                Log.d("mylog", "curr: X=${it.degX}  Y=${it.degY}  Z=${it.degZ}")
                when {
                    it.degY > yDegMargin -> move(dpadRepository, Dpad.Down)
                    it.degY < -yDegMargin -> move(dpadRepository, Dpad.Up)
                    it.degZ > zDegMargin -> move(dpadRepository, Dpad.Right)
                    it.degZ < -zDegMargin -> move(dpadRepository, Dpad.Left)
                }
            }
        compositeDisposable.add(disposable)

        Log.d("mylog", "put bomb: initialize ${viewModel.currentBombIntervalTime.value?.toLong()}")

        val disposable2 = Observable.interval(
            viewModel.currentBombIntervalTime.value?.toLong() ?: bombIntervalSecDefault.toLong(),
            TimeUnit.SECONDS
        ).subscribe {
            try {
                Log.d("mylog", "put bomb ${viewModel.currentBombIntervalTime.value?.toLong()}")
                pushButton(buttonRepository, Button.B)
            } catch (ignore: Exception) {
            }
        }
        compositeDisposable.add(disposable2)
    }

    private fun pushButton(buttonRepository: ButtonRepository, button: Button) {
        Log.d("mylog", "pushed: ${button.name}")
        launch {
            try {
                buttonRepository.push(button)
            } catch (ignore: Exception) {
            }
        }
    }

    private fun move(dpadRepository: DpadRepository, dpad: Dpad) {
        Log.d("mylog", "move to ${dpad.name}")
        launch {
            try {
                dpadRepository.hold(dpad)
            } catch (ignore: Exception) {
            }
        }
    }

    private fun getClient(): CccApiService {
        return NetworkModule().let {
            it.providesCccService(it.providesBaseUrl(), it.providesOkHttpClient())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
        job.cancel()
    }

    override fun onResume() {
        super.onResume()

        // 地磁気センサー登録
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
            SensorManager.SENSOR_DELAY_UI
        )
        // 加速度センサー登録
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_UI
        )
    }

    override fun onPause() {
        super.onPause()

        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private val radianSubject = PublishSubject.create<SensorEvent>()

    private class SensorEvent(val degX: Int, val degY: Int, val degZ: Int)


    private val RAD2DEG = 180 / Math.PI
    var rotationMatrix = FloatArray(9)
    var gravity: FloatArray? = null
    var geomagnetic: FloatArray? = null
    var attitude = FloatArray(3)

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = event.values.clone()
            Sensor.TYPE_ACCELEROMETER -> gravity = event.values.clone()
            else -> return
        }

        if (geomagnetic != null && gravity != null) {
            SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
            SensorManager.getOrientation(rotationMatrix, attitude)

            radianSubject.onNext(
                SensorEvent(
                    (attitude[0] * RAD2DEG).toInt(),
                    (attitude[1] * RAD2DEG).toInt(),
                    (attitude[2] * RAD2DEG).toInt()
                )
            )
        }
    }
}
