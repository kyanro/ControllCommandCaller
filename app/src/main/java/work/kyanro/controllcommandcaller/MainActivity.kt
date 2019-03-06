package work.kyanro.controllcommandcaller

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sensorManager = getSystemService() ?: throw IllegalStateException("センサーが利用できる端末で利用してください")
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

    private fun radianToDegrees(angrad: Float): Int {
        val degree = if (angrad >= 0) Math.toDegrees(angrad.toDouble()) else 360 + Math.toDegrees(angrad.toDouble())
        return Math.floor(degree).toInt()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** 地磁気行列  */
    private var magneticValues: FloatArray? = null
    /** 加速度行列  */
    private var accelerometerValues: FloatArray? = null

    /** X軸の回転角度  */
    private var pitchX: Int = 0
    /** Y軸の回転角度  */
    private var rollY: Int = 0
    /** Z軸の回転角度(方位角)  */
    private var azimuthZ: Int = 0

    val MATRIX_SIZE = 16
    val DIMENSION = 3

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> magneticValues = event.values.clone()
            Sensor.TYPE_ACCELEROMETER -> accelerometerValues = event.values.clone()
            else -> return
        }

        if (magneticValues != null && accelerometerValues != null) {
            val rotationMatrix = FloatArray(MATRIX_SIZE)
            val inclinationMatrix = FloatArray(MATRIX_SIZE)
            val remapedMatrix = FloatArray(MATRIX_SIZE)
            val orientationValues = FloatArray(DIMENSION)
            // 加速度センサーと地磁気センサーから回転行列を取得
            SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, accelerometerValues, magneticValues)
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remapedMatrix
            )
            SensorManager.getOrientation(remapedMatrix, orientationValues)
            // ラジアン値を変換し、それぞれの回転角度を取得する
            azimuthZ = radianToDegrees(orientationValues[0])
            pitchX = radianToDegrees(orientationValues[1])
            rollY = radianToDegrees(orientationValues[2])
            if (BuildConfig.DEBUG) {
                Log.d("mylog", "X=" + pitchX + "Y=" + rollY + "Z=" + azimuthZ)
            }
        }
    }
}
