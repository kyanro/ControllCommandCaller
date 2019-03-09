package work.kyanro.controllcommandcaller

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.databinding.DataBindingUtil
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

open class MainActivity : AppCompatActivity(), SensorEventListener, CoroutineScope {

    private val bomIntervalSec = 7L

    private val yDegMargin = 10
    private val zDegMargin = 3

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    private lateinit var sensorManager: SensorManager

    private val compositeDisposable = CompositeDisposable()

    private lateinit var binding: ActivityMainBinding
    //    private fun Int.toPositiveDeg() = (this + 180) % 360
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        sensorManager = getSystemService() ?: throw IllegalStateException("センサーが利用できる端末で利用してください")

        val api = getClient()
        val buttonRepository = ButtonRepository(api)
        val dpadRepository = DpadRepository(api)

        init(dpadRepository, buttonRepository)

        binding.start.setOnClickListener { init(dpadRepository, buttonRepository) }
        binding.stop.setOnClickListener {
            launch {
                releaseAllButton(dpadRepository, buttonRepository)
                compositeDisposable.clear()
            }
        }
    }

    private fun init(
        dpadRepository: DpadRepository,
        buttonRepository: ButtonRepository
    ) {
        launch {
            releaseAllButton(dpadRepository, buttonRepository)
            initController(dpadRepository, buttonRepository)
        }
    }

    private suspend fun releaseAllButton(dpadRepository: DpadRepository, buttonRepository: ButtonRepository) {
        dpadRepository.hold(Dpad.None)
        buttonRepository.run {
            release(Button.B)
            release(Button.A)
        }
    }

    private suspend fun initController(dpadRepository: DpadRepository, buttonRepository: ButtonRepository) {
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

        val disposable2 = Observable.interval(bomIntervalSec, TimeUnit.SECONDS)
            .subscribe {
                try {
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
