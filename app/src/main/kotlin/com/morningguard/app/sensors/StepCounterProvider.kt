package com.morningguard.app.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepCounterProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private fun stepCounterSensor(): Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private fun stepDetectorSensor(): Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    fun hasActivityRecognitionPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun needsActivityRecognitionPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasActivityRecognitionPermission()

    val isAvailable: Boolean
        get() = hasActivityRecognitionPermission() &&
            (stepCounterSensor() != null || stepDetectorSensor() != null)

    fun hasStepDetector(): Boolean = stepDetectorSensor() != null

    fun hasStepCounter(): Boolean = stepCounterSensor() != null

    /** Cumulative step count since last device boot. */
    fun stepCounter(): Flow<Int> = callbackFlow {
        val sensor = stepCounterSensor()
        if (!hasActivityRecognitionPermission() || sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0].toInt())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_FASTEST,
            SensorManager.SENSOR_DELAY_FASTEST,
        )
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /** One event per detected step while subscribed. */
    fun stepDetections(): Flow<Unit> = callbackFlow {
        val sensor = stepDetectorSensor()
        if (!hasActivityRecognitionPermission() || sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(Unit)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_FASTEST,
            SensorManager.SENSOR_DELAY_FASTEST,
        )
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
