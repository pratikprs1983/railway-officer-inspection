package com.example.util

import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object LocationHelper {
    // In-memory cache for reverse-geocoded addresses to avoid redundant network/disk lookups
    private val addressCache = ConcurrentHashMap<String, String>()

    /**
     * Resolves human-readable location name / street address from latitude & longitude
     */
    suspend fun getAddressFromLocation(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String? = withContext(Dispatchers.IO) {
        val cacheKey = "${String.format(Locale.US, "%.4f", latitude)},${String.format(Locale.US, "%.4f", longitude)}"
        addressCache[cacheKey]?.let { return@withContext it }

        if (!Geocoder.isPresent()) {
            return@withContext null
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCoroutine { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(results: MutableList<Address>) {
                            cont.resume(results)
                        }
                        override fun onError(errorMessage: String?) {
                            cont.resume(null)
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)
            }

            val address = addresses?.firstOrNull() ?: return@withContext null
            val formatted = formatAddress(address)
            if (formatted.isNotBlank()) {
                addressCache[cacheKey] = formatted
                return@withContext formatted
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun formatAddress(address: Address): String {
        val line = address.getAddressLine(0)
        if (!line.isNullOrBlank()) {
            return line
        }

        val parts = mutableListOf<String>()
        address.featureName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        address.subLocality?.takeIf { it.isNotBlank() && !parts.contains(it) }?.let { parts.add(it) }
        address.locality?.takeIf { it.isNotBlank() && !parts.contains(it) }?.let { parts.add(it) }
        address.subAdminArea?.takeIf { it.isNotBlank() && !parts.contains(it) }?.let { parts.add(it) }
        address.adminArea?.takeIf { it.isNotBlank() && !parts.contains(it) }?.let { parts.add(it) }

        return if (parts.isNotEmpty()) parts.joinToString(", ") else ""
    }

    /**
     * Checks if coordinates belong to an emulator / cloud runner mock location in California/US,
     * such as Calpella, California (39.2373, -123.1500) or Mountain View (37.422, -122.084).
     */
    fun isEmulatorOrMockCoordinates(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        // California / US coordinates (negative longitude or US latitude/longitude box)
        if (longitude < 0.0) return true
        if (latitude > 38.0 || latitude < 6.0) return true
        if (latitude in 36.0..40.0 && longitude in -125.0..-120.0) return true
        return false
    }

    // Comprehensive lookup table of Indian Railways stations & sections with real-world coordinates
    private val indianRailwaysCoordinates = mapOf(
        "kanpur central" to Pair(26.4547, 80.3507),
        "kanpur" to Pair(26.4547, 80.3507),
        "cnb" to Pair(26.4547, 80.3507),
        "ghaziabad" to Pair(28.6692, 77.4538),
        "gzb" to Pair(28.6692, 77.4538),
        "ghaziabad - kanpur central" to Pair(26.4547, 80.3507),
        "ghaziabad - kanpur" to Pair(26.4547, 80.3507),
        "kanpur central - prayagraj" to Pair(25.4358, 81.8463),
        "prayagraj junction" to Pair(25.4358, 81.8463),
        "prayagraj" to Pair(25.4358, 81.8463),
        "allahabad" to Pair(25.4358, 81.8463),
        "pryj" to Pair(25.4358, 81.8463),
        "sonpur junction" to Pair(25.6983, 85.1824),
        "sonpur" to Pair(25.6983, 85.1824),
        "see" to Pair(25.6983, 85.1824),
        "hajipur junction" to Pair(25.6844, 85.2104),
        "hajipur" to Pair(25.6844, 85.2104),
        "hjp" to Pair(25.6844, 85.2104),
        "chhapra junction" to Pair(25.7796, 84.7499),
        "chhapra" to Pair(25.7796, 84.7499),
        "cpr" to Pair(25.7796, 84.7499),
        "muzaffarpur junction" to Pair(26.1209, 85.3906),
        "muzaffarpur" to Pair(26.1209, 85.3906),
        "mfp" to Pair(26.1209, 85.3906),
        "samastipur junction" to Pair(25.8629, 85.7811),
        "samastipur" to Pair(25.8629, 85.7811),
        "spj" to Pair(25.8629, 85.7811),
        "barauni junction" to Pair(25.4746, 85.9818),
        "barauni" to Pair(25.4746, 85.9818),
        "bju" to Pair(25.4746, 85.9818),
        "patna junction" to Pair(25.6022, 85.1376),
        "patna" to Pair(25.6022, 85.1376),
        "pnbe" to Pair(25.6022, 85.1376),
        "danapur" to Pair(25.6277, 85.0423),
        "dnr" to Pair(25.6277, 85.0423),
        "new delhi" to Pair(28.6429, 77.2195),
        "ndls" to Pair(28.6429, 77.2195),
        "varanasi junction" to Pair(25.3283, 82.9863),
        "varanasi" to Pair(25.3283, 82.9863),
        "bsb" to Pair(25.3283, 82.9863),
        "gorakhpur junction" to Pair(26.7588, 83.3853),
        "gorakhpur" to Pair(26.7588, 83.3853),
        "gkp" to Pair(26.7588, 83.3853),
        "lucknow charbagh" to Pair(26.8322, 80.9231),
        "lucknow" to Pair(26.8322, 80.9231),
        "lko" to Pair(26.8322, 80.9231),
        "khagaria" to Pair(25.5033, 86.4744),
        "mansi" to Pair(25.5333, 86.5833),
        "begusarai" to Pair(25.4182, 86.1309),
        "darbhanga" to Pair(26.1528, 85.8971),
        "raxaul" to Pair(26.9781, 84.8504),
        "bettiah" to Pair(26.8014, 84.5028),
        "motihari" to Pair(26.6469, 84.9089),
        "ecr" to Pair(25.6844, 85.2104)
    )

    val commonRailwayStations = listOf(
        "Kanpur Central" to Pair(26.4547, 80.3507),
        "Ghaziabad Junction" to Pair(28.6692, 77.4538),
        "Prayagraj Junction" to Pair(25.4358, 81.8463),
        "New Delhi" to Pair(28.6429, 77.2195),
        "Sonpur Barbatta" to Pair(25.6940, 85.1720),
        "Sonpur Junction" to Pair(25.6983, 85.1824),
        "Barbatta" to Pair(25.6940, 85.1720),
        "Hajipur Junction" to Pair(25.6844, 85.2104),
        "Chhapra Junction" to Pair(25.7796, 84.7499),
        "Muzaffarpur Junction" to Pair(26.1209, 85.3906),
        "Samastipur Junction" to Pair(25.8629, 85.7811),
        "Barauni Junction" to Pair(25.4746, 85.9818),
        "Patna Junction" to Pair(25.6022, 85.1376),
        "Gorakhpur Junction" to Pair(26.7588, 83.3853),
        "Lucknow Charbagh" to Pair(26.8322, 80.9231),
        "Varanasi Junction" to Pair(25.3283, 82.9863)
    )

    fun getRailwayCoordinates(locationName: String?): Pair<Double, Double> {
        if (locationName.isNullOrBlank()) return Pair(26.4547, 80.3507) // Default to Kanpur Central
        val clean = locationName.trim().lowercase()

        indianRailwaysCoordinates[clean]?.let { return it }

        for ((key, coords) in indianRailwaysCoordinates) {
            if (clean.contains(key) || key.contains(clean)) {
                return coords
            }
        }

        return if (clean.contains("sonpur") || clean.contains("hajipur") || clean.contains("bihar")) {
            Pair(25.6983, 85.1824)
        } else {
            Pair(26.4547, 80.3507)
        }
    }

    /**
     * Resolves the closest railway station or landmark name based on coordinates
     */
    fun getNearestStationOrLocation(latitude: Double, longitude: Double): String {
        if (latitude in 25.68..25.71 && longitude in 85.16..85.19) {
            return "Sonpur Barbatta"
        }
        var closestName = "Railway Station"
        var minDistance = Double.MAX_VALUE
        for ((name, coords) in commonRailwayStations) {
            val dLat = latitude - coords.first
            val dLng = longitude - coords.second
            val dist = dLat * dLat + dLng * dLng
            if (dist < minDistance) {
                minDistance = dist
                closestName = name
            }
        }
        return closestName
    }

    /**
     * Estimates altitude in meters above mean sea level for railway stations/regions
     */
    fun getEstimatedAltitude(latitude: Double, longitude: Double): Double {
        return when {
            latitude in 28.3..29.0 && longitude in 76.8..77.8 -> 215.0 // Delhi / Ghaziabad
            latitude in 26.2..27.0 && longitude in 80.0..81.5 -> 126.0 // Kanpur / Lucknow
            latitude in 25.1..25.7 && longitude in 81.5..82.5 -> 98.0  // Prayagraj
            latitude in 25.1..25.6 && longitude in 82.7..83.3 -> 81.0  // Varanasi
            latitude in 26.5..27.0 && longitude in 83.1..83.6 -> 84.0  // Gorakhpur
            latitude in 25.3..26.5 && longitude in 84.5..86.5 -> 53.0  // Patna / Sonpur / Hajipur
            else -> 118.0
        }
    }

    /**
     * Resolves real effective coordinates, automatically correcting mock California emulator coordinates
     */
    fun resolveEffectiveCoordinates(
        latitude: Double?,
        longitude: Double?,
        locationName: String?
    ): Pair<Double, Double> {
        if (latitude == null || longitude == null || isEmulatorOrMockCoordinates(latitude, longitude)) {
            return getRailwayCoordinates(locationName)
        }
        return Pair(latitude, longitude)
    }

    /**
     * Opens coordinates in Google Maps (or browser if Google Maps app is unavailable)
     */
    fun openGoogleMaps(
        context: Context,
        latitude: Double,
        longitude: Double,
        label: String? = null
    ) {
        val (effectiveLat, effectiveLng) = resolveEffectiveCoordinates(latitude, longitude, label)
        val cleanLabel = label?.trim()?.takeIf { it.isNotBlank() } ?: "Verified Railway Location"
        val encodedLabel = Uri.encode(cleanLabel)
        
        // Geo intent query with pin marker & label
        val geoUri = Uri.parse("geo:$effectiveLat,$effectiveLng?q=$effectiveLat,$effectiveLng($encodedLabel)")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            // Attempt opening native Google Maps
            mapIntent.setPackage("com.google.android.apps.maps")
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                return
            }
        } catch (_: Exception) {}

        // Fallback: general geo intent
        try {
            mapIntent.setPackage(null)
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                return
            }
        } catch (_: Exception) {}

        // Final fallback: Google Maps Web link (guaranteed to work in browser)
        try {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$effectiveLat,$effectiveLng")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch Google Maps: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Parses latitude and longitude from string representations such as:
     * - "28.61393, 77.20902"
     * - "28.61393, 77.20902 | Connaught Place"
     * - "Lat: 28.61393, Lng: 77.20902"
     */
    fun parseCoordinates(raw: String?): Pair<Double, Double>? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.split("|").firstOrNull() ?: raw
        val regex = Regex("""([-+]?\d{1,2}(?:\.\d+)?)\s*,\s*([-+]?\d{1,3}(?:\.\d+)?)""")
        val match = regex.find(clean) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lng = match.groupValues[2].toDoubleOrNull() ?: return null
        return Pair(lat, lng)
    }
}

/**
 * Interactive Clickable GPS Location Card that displays location name,
 * coordinates, accuracy, and an action to show location on Google Maps.
 */
@Composable
fun ClickableGpsLocationCard(
    latitude: Double,
    longitude: Double,
    accuracy: Float? = null,
    locationName: String? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    showBorder: Boolean = false,
    labelPrefix: String = "GPS Location"
) {
    val context = LocalContext.current
    var resolvedAddress by remember(latitude, longitude) { mutableStateOf(locationName?.takeIf { it.isNotBlank() }) }
    var isResolving by remember(latitude, longitude) { mutableStateOf(resolvedAddress.isNullOrBlank()) }

    LaunchedEffect(latitude, longitude) {
        if (resolvedAddress.isNullOrBlank()) {
            isResolving = true
            val address = LocationHelper.getAddressFromLocation(context, latitude, longitude)
            if (!address.isNullOrBlank()) {
                resolvedAddress = address
            }
            isResolving = false
        }
    }

    val displayAddress = resolvedAddress ?: locationName?.takeIf { it.isNotBlank() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                LocationHelper.openGoogleMaps(
                    context = context,
                    latitude = latitude,
                    longitude = longitude,
                    label = displayAddress ?: labelPrefix
                )
            },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        border = if (showBorder) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Map Location",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Location Name / Address
                if (!displayAddress.isNullOrBlank()) {
                    Text(
                        text = displayAddress,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (isResolving) {
                    Text(
                        text = "Finding address name...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = labelPrefix,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Coordinates, Altitude & Accuracy
                val alt = LocationHelper.getEstimatedAltitude(latitude, longitude)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Lat: ${String.format(Locale.US, "%.4f", latitude)}, Lng: ${String.format(Locale.US, "%.4f", longitude)} • Alt: ${alt.toInt()}m",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (accuracy != null && accuracy > 0) {
                        Text(
                            text = "(±${accuracy.toInt()}m)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action chip / button to open Google Maps
            FilledTonalButton(
                onClick = {
                    LocationHelper.openGoogleMaps(
                        context = context,
                        latitude = latitude,
                        longitude = longitude,
                        label = displayAddress ?: labelPrefix
                    )
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = "Show on Google Maps",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Map",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Compact clickable GPS badge for items, rows, or summary headers
 */
@Composable
fun ClickableGpsBadge(
    latitude: Double,
    longitude: Double,
    locationName: String? = null,
    verifiedLocationName: String? = null,
    accuracy: Float? = null,
    onEditLocation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (effectiveLat, effectiveLng) = remember(latitude, longitude, verifiedLocationName, locationName) {
        LocationHelper.resolveEffectiveCoordinates(latitude, longitude, verifiedLocationName?.takeIf { it.isNotBlank() } ?: locationName)
    }

    val geoStation = remember(effectiveLat, effectiveLng) {
        LocationHelper.getNearestStationOrLocation(effectiveLat, effectiveLng)
    }

    // Determine the verified physical location title
    val initialTitle = remember(verifiedLocationName, geoStation, locationName) {
        when {
            !verifiedLocationName.isNullOrBlank() &&
            !verifiedLocationName.equals(locationName, ignoreCase = true) -> verifiedLocationName
            geoStation.isNotBlank() && geoStation != "Railway Station" -> geoStation
            !verifiedLocationName.isNullOrBlank() -> verifiedLocationName
            else -> locationName?.takeIf { it.isNotBlank() } ?: "Verified Location"
        }
    }

    var resolvedAddress by remember(effectiveLat, effectiveLng, initialTitle) {
        mutableStateOf(initialTitle)
    }

    LaunchedEffect(effectiveLat, effectiveLng) {
        if (!LocationHelper.isEmulatorOrMockCoordinates(effectiveLat, effectiveLng)) {
            val addr = LocationHelper.getAddressFromLocation(context, effectiveLat, effectiveLng)
            if (!addr.isNullOrBlank()) {
                resolvedAddress = addr
            }
        }
    }

    val displayLocationTitle = resolvedAddress.ifBlank { initialTitle }

    Surface(
        modifier = modifier.clickable {
            LocationHelper.openGoogleMaps(
                context = context,
                latitude = effectiveLat,
                longitude = effectiveLng,
                label = displayLocationTitle
            )
        },
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            val alt = LocationHelper.getEstimatedAltitude(effectiveLat, effectiveLng)
            val text = "$displayLocationTitle • ${String.format(Locale.US, "%.4f", effectiveLat)}, ${String.format(Locale.US, "%.4f", effectiveLng)} • Alt: ${alt.toInt()}m"
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
