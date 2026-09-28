package org.pottershouse.impactteam.android.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.pottershouse.impactteam.domain.GeoPoint
import org.pottershouse.impactteam.domain.TrackingPermission
import org.pottershouse.impactteam.transport.LocationAvailability
import org.pottershouse.impactteam.transport.LocationRequestPolicy
import org.pottershouse.impactteam.transport.LocationSample
import org.pottershouse.impactteam.transport.LocationSource

class AndroidLocationSource(
    context: Context,
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext),
) : LocationSource {
    private val appContext = context.applicationContext
    private val mutableSamples = MutableSharedFlow<LocationSample>(extraBufferCapacity = 16)
    private val mutableAvailability = MutableStateFlow<LocationAvailability>(LocationAvailability.Available)
    private val batteryManager = appContext.getSystemService(BatteryManager::class.java)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    override val samples: Flow<LocationSample> = mutableSamples.asSharedFlow()
    override val availability: StateFlow<LocationAvailability> = mutableAvailability.asStateFlow()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                mutableSamples.tryEmit(
                    LocationSample(
                        point = GeoPoint(location.latitude, location.longitude),
                        capturedAtEpochMillis = location.time,
                        horizontalAccuracyMeters = location.accuracy.toDouble().coerceAtLeast(0.0),
                        batteryPercent = batteryManager
                            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                            .coerceIn(0, 100),
                    ),
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun start(policy: LocationRequestPolicy) {
        if (!hasLocationPermission()) {
            mutableAvailability.value =
                LocationAvailability.PermissionMissing(TrackingPermission.PRECISE_LOCATION)
            return
        }
        if (!locationServicesEnabled()) {
            mutableAvailability.value = LocationAvailability.ServicesDisabled
            return
        }

        mutableAvailability.value = LocationAvailability.Available
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, policy.intervalMillis)
            .setMinUpdateIntervalMillis(policy.intervalMillis)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { error ->
                    mutableAvailability.value = LocationAvailability.Unavailable(
                        error.message ?: "Location updates failed",
                    )
                }
        } catch (_: SecurityException) {
            mutableAvailability.value =
                LocationAvailability.PermissionMissing(TrackingPermission.PRECISE_LOCATION)
        }
    }

    override suspend fun stop() {
        fusedLocationClient.removeLocationUpdates(callback)
    }

    private fun hasLocationPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun locationServicesEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
}
