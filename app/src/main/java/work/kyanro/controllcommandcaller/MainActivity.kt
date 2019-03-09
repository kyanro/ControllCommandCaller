package work.kyanro.controllcommandcaller

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import work.kyanro.controllcommandcaller.di.NetworkModule
import work.kyanro.controllcommandcaller.network.Button
import work.kyanro.controllcommandcaller.network.CccApiService
import work.kyanro.controllcommandcaller.network.Dpad
import work.kyanro.controllcommandcaller.repository.ButtonRepository
import work.kyanro.controllcommandcaller.repository.DpadRepository
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

open class MainActivity : AppCompatActivity(), SensorEventListener, CoroutineScope {

    private val bomIntervalSec = 3L

    private val yDegMargin = 5
    private val zDegMargin = 3

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    private lateinit var sensorManager: SensorManager

    private val compositeDisposable = CompositeDisposable()

    //    private fun Int.toPositiveDeg() = (this + 180) % 360
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sensorManager = getSystemService() ?: throw IllegalStateException("センサーが利用できる端末で利用してください")

        val api = getClient()

        val dpadRepository = DpadRepository(api)

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

        val buttonRepository = ButtonRepository(api)
        val disposable2 = Observable.interval(bomIntervalSec, TimeUnit.SECONDS)
            .subscribe {
                launch {
                    try {
                        buttonRepository.push(Button.B)
                    } catch (ignore: Exception) {
                    }
                }
            }
        compositeDisposable.add(disposable2)
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
