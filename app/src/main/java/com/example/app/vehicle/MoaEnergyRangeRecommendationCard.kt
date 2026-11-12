package com.example.app.vehicle

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ai.pleos.playground.location.fused.model.LocationData
import com.example.app.data.KakaoLocalRepository
import com.example.app.data.KakaoPlace
import com.example.app.data.TripPlace
import com.example.app.data.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * AAOS 표준 RANGE_REMAINING(291504904): 남은 주행 가능 거리, 미터 단위.
 * Java reflection으로 public Android Car API를 호출한다. Android Car 라이브러리가
 * 현재 앱의 컴파일 classpath에 없어도 기존 프로젝트를 빌드할 수 있게 분리한 어댑터.
 * Car Service / 차량 property 지원이 없으면 반드시 null을 반환한다.
 * AndroidManifest.xml: <uses-permission android:name="android.car.permission.CAR_ENERGY" />
 */
private object MoaRemainingRangeReader {
    private const val RANGE_REMAINING = 291504904

    fun readKm(context: Context): Double? {
        var car: Any? = null
        try {
            val carClass = Class.forName("android.car.Car")
            car = carClass.getMethod("createCar", Context::class.java)
                .invoke(null, context.applicationContext)
                ?: return null
            val manager = carClass.getMethod("getCarManager", String::class.java)
                .invoke(car, "property") ?: return null
            val read = manager.javaClass.getMethod(
                "getFloatProperty",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val meters = (read.invoke(manager, RANGE_REMAINING, 0) as? Number)
                ?.toDouble() ?: return null
            return (meters / 1000.0).takeIf { it.isFinite() && it >= 0.0 }
        } catch (e: Exception) {
            Log.w("MOA_RANGE", "RANGE_REMAINING 조회 불가: ${e.message}")
            return null
        } finally {
            if (car != null) {
                try {
                    car.javaClass.getMethod("disconnect").invoke(car)
                } catch (e: Exception) {
                    Log.w("MOA_RANGE", "Car 서비스 해제 오류: ${e.message}")
                }
            }
        }
    }
}

private enum class MoaEnergyType(val label: String, val query: String) {
    EV("전기차", "전기차 충전소"),
    FUEL("내연기관·하이브리드", "주유소")
}

private fun KakaoPlace.toEnergyTripPlace() = TripPlace(
    id = id, name = name, address = address, category = category,
    latitude = latitude, longitude = longitude
)

/**
 * 현재 차량의 주행 가능 거리 <= 80km인 경우 주변 에너지 보충 장소를 MOA 점수로 추천.
 * 실제 차량 데이터 없이 추천을 시작하거나, 내비 목적지를 임의로 변경하지 않는다.
 */
@Composable
fun MoaEnergyRangeRecommendationCard(
    currentLocation: LocationData?,
    profile: UserProfile?,
    kakaoRepository: KakaoLocalRepository,
    moaScorer: MoaPlaceScorer,
    moaLearner: MoaPreferenceLearner,
    naviRouting: Boolean,
    onNavigate: (TripPlace) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(
            "moa_energy_preference", Context.MODE_PRIVATE
        )
    }
    val prefKey = "powertrain_${profile?.id.orEmpty()}"
    var vehicleType by remember(prefKey) {
        mutableStateOf(
            runCatching { MoaEnergyType.valueOf(prefs.getString(prefKey, "") ?: "") }
                .getOrNull()
        )
    }
    var energyPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(
            context, "android.car.permission.CAR_ENERGY"
        ) == PackageManager.PERMISSION_GRANTED)
    }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var rangeLoading by remember { mutableStateOf(false) }
    var rangeKm by remember { mutableStateOf<Double?>(null) }
    var rangeMessage by remember { mutableStateOf("차량 주행 가능 거리 조회 전") }
    var searchLoading by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var recommended by remember { mutableStateOf<KakaoPlace?>(null) }
    var recommendedMoaScore by remember { mutableStateOf<Double?>(null) }
    var searchVersion by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        energyPermissionGranted = granted
        if (granted) refreshVersion++
        else rangeMessage = "차량 에너지 정보 접근 권한이 없어 조회할 수 없습니다."
    }

    // 현재 차량의 실제 주행 가능 거리만 읽는다. 가상 수치/임의 기본값은 사용하지 않는다.
    LaunchedEffect(refreshVersion, energyPermissionGranted) {
        if (!energyPermissionGranted) {
            rangeKm = null
            rangeMessage = "CAR_ENERGY 권한 필요"
            return@LaunchedEffect
        }
        rangeLoading = true
        rangeMessage = "차량 주행 가능 거리 확인 중..."
        try {
            val reading = withTimeoutOrNull(5_000L) {
                withContext(Dispatchers.IO) {
                    MoaRemainingRangeReader.readKm(context.applicationContext)
                }
            }
            rangeKm = reading
            rangeMessage = if (reading != null) "남은 주행 가능 거리 약 %.0f km".format(reading)
            else "주행 가능 거리 조회 불가 (차량·에뮬레이터 지원 여부 확인)"
            Log.d("MOA_RANGE", "rangeKm=$reading")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            rangeKm = null
            rangeMessage = "차량 정보 조회 오류: ${e.message}"
            Log.e("MOA_RANGE", "주행 가능 거리 조회 실패", e)
        } finally {
            rangeLoading = false
        }
    }

    fun findStation() {
        val origin = currentLocation ?: run {
            searchMessage = "GPS 위치를 수신한 후 추천할 수 있습니다."
            return
        }
        val type = vehicleType ?: run {
            searchMessage = "먼저 차량 종류를 선택해 주세요."
            return
        }
        val distanceLeft = rangeKm ?: run {
            searchMessage = "실제 주행 가능 거리 값이 없어 자동 추천하지 않습니다."
            return
        }
        if (distanceLeft > 80.0) {
            searchMessage = "현재 잔여 거리 %.0f km: 80 km 초과로 보충 추천을 보류합니다.".format(distanceLeft)
            return
        }
        if (searchLoading) return
        val version = ++searchVersion
        searchLoading = true
        recommended = null
        recommendedMoaScore = null
        searchMessage = "MOA가 가까운 ${type.query} 후보를 비교하는 중..."
        scope.launch {
            try {
                val result = kakaoRepository.searchPlaces(
                    query = type.query,
                    longitude = origin.longitude,
                    latitude = origin.latitude,
                    radius = 5000
                )
                if (version != searchVersion) return@launch
                result.fold(
                    onSuccess = { places ->
                        val scoringContext = MoaScoringContext(
                            profilePreferences = profile?.preferences.orEmpty(),
                            profileAtmospheres = profile?.atmospheres.orEmpty(),
                            tripPurpose = type.query,
                            desiredExperience = "",
                            tripMood = "",
                            latitude = origin.latitude,
                            longitude = origin.longitude,
                            learnedPreferences = profile?.id?.let {
                                moaLearner.getLearnedPreferences(it)
                            }.orEmpty(),
                            requestedKeyword = type.query
                        )
                        val validFacilities = places.filter { p ->
                            val description = (p.name + " " + p.category).lowercase()
                            when (type) {
                                MoaEnergyType.EV -> description.contains("충전")
                                MoaEnergyType.FUEL -> description.contains("주유")
                            }
                        }
                        val ranked = validFacilities.distinctBy { it.id }
                            .map { p ->
                                val distance = FloatArray(1)
                                Location.distanceBetween(
                                    origin.latitude, origin.longitude,
                                    p.latitude, p.longitude, distance
                                )
                                val moaScore = moaScorer.score(p, scoringContext).score.total
                                // 실제 차량 보충은 접근성이 중요. 65% 거리 + 35% MOA 개인화 점수.
                                val proximity = (1.0 - distance[0] / 5000.0)
                                    .coerceIn(0.0, 1.0) * 100.0
                                Triple(p, moaScore, 0.65 * proximity + 0.35 * moaScore)
                            }
                            .sortedByDescending { it.third }
                        val top = ranked.firstOrNull()
                        recommended = top?.first
                        recommendedMoaScore = top?.second
                        searchMessage = if (top == null) "5km 내 ${type.query} 검색 결과가 없습니다."
                        else "차량 주행 가능 거리 %.0f km에 따른 MOA 보충 추천".format(distanceLeft)
                        Log.d("MOA_RANGE", "recommended=${top?.first?.name} count=${validFacilities.size}")
                    },
                    onFailure = { e ->
                        searchMessage = "시설 검색 실패: ${e.message}"
                        Log.e("MOA_RANGE", "에너지 시설 검색 실패", e)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == searchVersion) searchMessage = "추천 오류: ${e.message}"
                Log.e("MOA_RANGE", "MOA 추천 실패", e)
            } finally {
                if (version == searchVersion) searchLoading = false
            }
        }
    }

    // 낮은 잔여 거리가 확인되면 차량 종류와 연계하여 1회 자동 검색한다.
    LaunchedEffect(rangeKm, vehicleType, currentLocation != null) {
        if (rangeKm != null && rangeKm!! <= 80.0 && vehicleType != null) {
            findStation()
        } else {
            searchVersion++
            recommended = null
            recommendedMoaScore = null
            searchMessage = null
        }
    }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("⛽ 차량 주행 가능 거리 · MOA 보충 추천", style = MaterialTheme.typography.titleSmall)
            Text(rangeMessage, style = MaterialTheme.typography.bodySmall)
            Text("남은 거리가 80km 이하일 때 주변 에너지 보충 장소 1곳 추천",
                style = MaterialTheme.typography.labelSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MoaEnergyType.values().forEach { type ->
                    FilterChip(
                        selected = vehicleType == type,
                        onClick = {
                            vehicleType = type
                            prefs.edit().putString(prefKey, type.name).apply()
                            recommended = null
                            searchMessage = null
                        },
                        label = { Text(type.label) }
                    )
                }
            }

            if (!energyPermissionGranted) {
                OutlinedButton(onClick = {
                    permissionLauncher.launch("android.car.permission.CAR_ENERGY")
                }) { Text("차량 정보 접근 허용") }
            } else {
                OutlinedButton(onClick = { refreshVersion++ }, enabled = !rangeLoading) {
                    Text(if (rangeLoading) "차량 정보 조회 중" else "차량 정보 새로고침")
                }
            }

            if (rangeKm != null && rangeKm!! <= 80.0 && vehicleType != null) {
                OutlinedButton(
                    enabled = !searchLoading && currentLocation != null,
                    onClick = { findStation() }
                ) { Text(if (searchLoading) "추천 검색 중" else "에너지 보충 장소 다시 추천") }
            }
            searchMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            recommended?.let { place ->
                HorizontalDivider()
                Text("✨ ${place.name}", style = MaterialTheme.typography.titleSmall)
                Text(place.address, style = MaterialTheme.typography.bodySmall)
                Text("MOA 취향 점수 %.1f/100 · 근접성 우선 추천".format(
                    recommendedMoaScore ?: 0.0
                ), style = MaterialTheme.typography.labelSmall)
                OutlinedButton(
                    enabled = !naviRouting,
                    onClick = { onNavigate(place.toEnergyTripPlace()) }
                ) { Text(if (naviRouting) "내비 경로 요청 중" else "이 장소로 길 안내") }
                Text("길 안내를 선택하면 현재 내비 경로가 변경될 수 있습니다.",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
