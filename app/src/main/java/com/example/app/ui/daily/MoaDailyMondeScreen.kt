package com.example.app.ui.daily

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.io.IOException
import android.util.Log
import ai.pleos.playground.location.fused.model.LocationData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.app.data.KakaoLocalRepository
import com.example.app.data.KakaoPlace
import com.example.app.data.TripPlace
import com.example.app.data.UserProfile
import com.example.app.vehicle.MoaPlaceScorer
import com.example.app.vehicle.MoaDriverDoorController
import com.example.app.vehicle.MoaDriverDoorCard
import com.example.app.vehicle.MoaEnergyRangeRecommendationCard
import com.example.app.vehicle.MoaPreferenceLearner
import com.example.app.vehicle.MoaScoringContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private data class DailyFavorite(val label: String, val place: TripPlace)

private data class DailyAddressCandidate(
    val address: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * 사용자가 입력한 주소를 좌표로 변환한다. 현재 GPS와 무관한 주소도 검색한다.
 * Android Geocoder는 단말의 지오코딩 백엔드가 없으면 사용할 수 없다.
 */
private suspend fun lookupDailyAddress(context: Context, query: String): List<DailyAddressCandidate> {
    if (!Geocoder.isPresent()) {
        throw IOException("이 기기에 주소 검색 서비스가 없습니다. Pleos 에뮬레이터의 지오코딩 지원 여부를 확인해 주세요.")
    }
    val geocoder = Geocoder(context.applicationContext, Locale.KOREAN)
    val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        withTimeout(10_000L) {
            suspendCancellableCoroutine { continuation ->
                try {
                    geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) continuation.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IOException(errorMessage ?: "주소 검색 서버 응답 오류")
                                )
                            }
                        }
                    })
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        }
    } else {
        // 구형 Android에서는 블로킹 API를 반드시 IO 스레드에서 실행한다.
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(query, 5).orEmpty()
        }
    }
    return addresses
        .filter { it.hasLatitude() && it.hasLongitude() &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
        .map { address ->
            val formatted = address.getAddressLine(0).orEmpty().ifBlank {
                listOfNotNull(address.adminArea, address.locality, address.thoroughfare)
                    .joinToString(" ").ifBlank { query }
            }
            DailyAddressCandidate(formatted, address.latitude, address.longitude)
        }
        .distinctBy { "%.6f,%.6f".format(Locale.US, it.latitude, it.longitude) }
}

/**
 * 프로필별 자주 가는 장소를 앱에 영구 저장한다.
 * 여행 목적지 저장소는 전혀 수정하지 않는다.
 */
private class DailyFavoriteStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "moa_daily_favorites_v1", Context.MODE_PRIVATE
    )

    private fun key(profileId: String) = "favorites_$profileId"

    fun load(profileId: String): List<DailyFavorite> {
        if (profileId.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(prefs.getString(key(profileId), "[]") ?: "[]")
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val latitude = item.optDouble("latitude", Double.NaN)
                    val longitude = item.optDouble("longitude", Double.NaN)
                    if (id.isBlank() || !latitude.isFinite() || !longitude.isFinite()) continue
                    val place = TripPlace(
                        id = id,
                        name = item.optString("name"),
                        address = item.optString("address"),
                        category = item.optString("category"),
                        latitude = latitude,
                        longitude = longitude
                    )
                    add(DailyFavorite(item.optString("label", place.name), place))
                }
            }
        } catch (e: Exception) {
            Log.e("MOA_DAILY", "저장 장소 읽기 실패", e)
            emptyList()
        }
    }

    private fun persist(profileId: String, items: List<DailyFavorite>) {
        if (profileId.isBlank()) return
        val json = JSONArray()
        items.forEach { favorite ->
            val place = favorite.place
            json.put(JSONObject().apply {
                put("label", favorite.label)
                put("id", place.id)
                put("name", place.name)
                put("address", place.address)
                put("category", place.category)
                put("latitude", place.latitude)
                put("longitude", place.longitude)
            })
        }
        check(prefs.edit().putString(key(profileId), json.toString()).commit()) {
            "자주 가는 장소 저장 실패"
        }
    }

    fun add(profileId: String, favorite: DailyFavorite): List<DailyFavorite> {
        val updated = load(profileId).filterNot { it.place.id == favorite.place.id } + favorite
        persist(profileId, updated)
        return updated
    }

    fun remove(profileId: String, placeId: String): List<DailyFavorite> {
        val updated = load(profileId).filterNot { it.place.id == placeId }
        persist(profileId, updated)
        return updated
    }
}

private fun KakaoPlace.asTripPlace() = TripPlace(
    id = id,
    name = name,
    address = address,
    category = category,
    latitude = latitude,
    longitude = longitude
)

/**
 * 일상 모드 MVP: 장소 저장 / 저장 장소 길 안내 / 주변 장소 탐색.
 * 내비 호출은 기존 MainActivity의 requestSelectedPlaceRoute()를 재사용한다.
 */
@Composable
fun MoaDailyModeScreen(
    driverDoorController: MoaDriverDoorController,
    profile: UserProfile?,
    currentLocation: LocationData?,
    locationStatus: String,
    kakaoRepository: KakaoLocalRepository,
    moaScorer: MoaPlaceScorer,
    moaLearner: MoaPreferenceLearner,
    naviRouting: Boolean,
    naviStatus: String,
    onRouteRequested: (TripPlace) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { DailyFavoriteStore(context) }
    val profileId = profile?.id.orEmpty()
    val scope = rememberCoroutineScope()

    var favorites by remember(profileId) { mutableStateOf(store.load(profileId)) }
    var alias by remember(profileId) { mutableStateOf("") }
    var addressInput by remember(profileId) { mutableStateOf("") }
    var addressMatches by remember(profileId) { mutableStateOf<List<DailyAddressCandidate>>(emptyList()) }
    var addressSearchLoading by remember(profileId) { mutableStateOf(false) }
    var addressMessage by remember(profileId) { mutableStateOf<String?>(null) }
    var addressSearchVersion by remember(profileId) { mutableIntStateOf(0) }
    var keyword by remember(profileId) { mutableStateOf("맛집") }
    var personalizedMode by remember(profileId) { mutableStateOf(false) }
    var results by remember(profileId) { mutableStateOf<List<KakaoPlace>>(emptyList()) }
    var loading by remember(profileId) { mutableStateOf(false) }
    var message by remember(profileId) { mutableStateOf<String?>(null) }
    var initialSearchStarted by remember(profileId) { mutableStateOf(false) }
    var searchVersion by remember(profileId) { mutableIntStateOf(0) }

    fun searchAddress() {
        val query = addressInput.trim()
        if (query.isBlank()) {
            addressMessage = "도로명 주소나 지번 주소를 입력해 주세요."
            return
        }
        addressSearchVersion++
        val requestVersion = addressSearchVersion
        addressSearchLoading = true
        addressMatches = emptyList()
        addressMessage = "'$query' 주소를 찾는 중..."
        scope.launch {
            try {
                val found = lookupDailyAddress(context, query)
                if (requestVersion == addressSearchVersion) {
                    addressMatches = found
                    addressMessage = if (found.isEmpty()) {
                        "주소를 찾지 못했습니다. 도로명과 건물번호까지 정확히 입력해 주세요."
                    } else {
                        "검색된 주소 ${found.size}개 중 정확한 위치를 확인하고 저장하세요."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestVersion == addressSearchVersion) {
                    Log.e("MOA_DAILY_ADDRESS", "주소 검색 실패", e)
                    addressMessage = "주소 검색 실패: ${e.message ?: "단말 지오코딩 서비스를 확인하세요."}"
                }
            } finally {
                if (requestVersion == addressSearchVersion) addressSearchLoading = false
            }
        }
    }

    fun searchPlaces(searchText: String) {
        val origin = currentLocation
        if (origin == null) {
            message = "현재 위치를 수신한 후 주변 장소를 검색할 수 있습니다."
            return
        }
        val query = searchText.trim()
        if (query.isBlank()) {
            message = "검색어를 입력해 주세요."
            return
        }
        keyword = query
        personalizedMode = false
        searchVersion += 1
        val requestVersion = searchVersion
        loading = true
        message = "'$query' 주변 장소 검색 중..."
        scope.launch {
            try {
                val response = kakaoRepository.searchPlaces(
                    query = query,
                    longitude = origin.longitude,
                    latitude = origin.latitude,
                    radius = 3000
                )
                response.fold(
                    onSuccess = { places ->
                        if (requestVersion == searchVersion) {
                            results = places.distinctBy { it.id }
                            message = "'$query' 검색 결과 ${results.size}개"
                            Log.d("MOA_DAILY", "SEARCH_OK query=$query count=${results.size}")
                        }
                    },
                    onFailure = { error ->
                        if (requestVersion == searchVersion) {
                            message = "주변 장소 검색 실패: ${error.message}"
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestVersion == searchVersion) {
                    message = "검색 중 오류가 발생했습니다: ${e.message}"
                    Log.e("MOA_DAILY", "검색 오류", e)
                }
            } finally {
                if (requestVersion == searchVersion) loading = false
            }
        }
    }

    // 다양한 유형의 장소를 실제로 검색한 뒤 기존 MOA 점수 엔진으로 비교한다.
    // 검색어를 1개로 고정하지 않아 특정 장소 유형에 치우치는 것을 줄인다.
    fun searchPersonalizedPlace() {
        val origin = currentLocation
        if (origin == null) {
            message = "현재 위치를 수신한 후 맞춤 추천을 이용할 수 있습니다."
            return
        }
        personalizedMode = true
        keyword = ""
        searchVersion += 1
        val requestVersion = searchVersion
        loading = true
        results = emptyList()
        message = "MOA가 취향과 거리 점수로 맞춤 장소를 찾는 중..."
        scope.launch {
            try {
                val queries = listOf("맛집", "카페", "공원", "박물관", "쇼핑")
                val found = coroutineScope {
                    queries.map { query ->
                        async {
                            try {
                                kakaoRepository.searchPlaces(
                                    query = query,
                                    longitude = origin.longitude,
                                    latitude = origin.latitude,
                                    radius = 3000
                                ).getOrElse { error ->
                                    Log.w("MOA_DAILY", "맞춤 후보 검색 실패: $query", error)
                                    emptyList()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e("MOA_DAILY", "맞춤 검색 오류: $query", e)
                                emptyList()
                            }
                        }
                    }.awaitAll().flatten()
                }
                if (requestVersion == searchVersion) {
                    results = found.distinctBy { it.id }
                    message = if (results.isEmpty()) {
                        "현재 위치에서 맞춤 추천할 장소를 찾지 못했습니다."
                    } else {
                        "${results.size}개 후보를 비교해 MOA 점수 1위 장소를 추천합니다."
                    }
                    Log.d("MOA_DAILY", "PERSONALIZED_POOL size=${results.size}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestVersion == searchVersion) {
                    message = "MOA 맞춤 추천 중 오류가 발생했습니다: ${e.message}"
                    Log.e("MOA_DAILY", "맞춤 추천 오류", e)
                }
            } finally {
                if (requestVersion == searchVersion) loading = false
            }
        }
    }

    // 일상 모드에 들어와 처음 GPS를 확보하면 주변 맛집을 한 차례 자동 검색한다.
    LaunchedEffect(profileId, currentLocation != null) {
        if (profileId.isNotBlank() && currentLocation != null && !initialSearchStarted) {
            initialSearchStarted = true
            searchPlaces(keyword)
        }
    }

    val favoriteIds = favorites.map { it.place.id }.toSet()
    val scoringLocation = currentLocation
    val ranked = if (scoringLocation != null) {
        val scoringContext = MoaScoringContext(
            profilePreferences = profile?.preferences.orEmpty(),
            profileAtmospheres = profile?.atmospheres.orEmpty(),
            tripPurpose = if (personalizedMode) "" else "일상",
            desiredExperience = "",
            tripMood = "",
            latitude = scoringLocation.latitude,
            longitude = scoringLocation.longitude,
            learnedPreferences = profileId.takeIf { it.isNotBlank() }
                ?.let { moaLearner.getLearnedPreferences(it) }.orEmpty()
        )
        results.filter { it.id !in favoriteIds }
            .map { moaScorer.score(it, scoringContext) }
            .sortedByDescending { it.score.total }
            .take(6)
    } else {
        emptyList()
    }

    val visibleRanked = if (personalizedMode) ranked.take(1) else ranked

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🏠 MOA 일상 모드", style = MaterialTheme.typography.titleLarge)
        MoaDriverDoorCard(controller = driverDoorController)
        MoaEnergyRangeRecommendationCard(
            currentLocation = currentLocation,
            profile = profile,
            kakaoRepository = kakaoRepository,
            moaScorer = moaScorer,
            moaLearner = moaLearner,
            naviRouting = naviRouting,
            onNavigate = onRouteRequested
        )
        Text("평소 자주 가는 곳은 빠르게, 새로운 장소는 가까이서 찾아보세요.")
        Text("위치: $locationStatus", style = MaterialTheme.typography.bodySmall)
        if (naviRouting || naviStatus.isNotBlank()) {
            Text("내비게이션: $naviStatus", style = MaterialTheme.typography.bodySmall)
        }

        if (profileId.isBlank()) {
            Text("프로필을 먼저 선택해야 장소를 저장할 수 있습니다.")
            OutlinedButton(onClick = onBack) { Text("모드 선택으로 돌아가기") }
        } else {
            HorizontalDivider()
            Text("📌 자주 가는 장소", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text("저장 이름 (선택): 집, 회사, 단골 카페") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedButton(
                onClick = {
                    val loc = currentLocation ?: return@OutlinedButton
                    val label = alias.trim()
                    if (label.isBlank()) {
                        message = "현재 위치를 저장하려면 먼저 이름을 입력해 주세요."
                        return@OutlinedButton
                    }
                    // 좌표를 소수점 5자리까지 사용하여 같은 위치 중복 저장을 줄인다.
                    val latKey = String.format(Locale.US, "%.5f", loc.latitude)
                    val lonKey = String.format(Locale.US, "%.5f", loc.longitude)
                    val place = TripPlace(
                        id = "daily_gps_${latKey}_${lonKey}",
                        name = label,
                        address = "직접 저장한 위치",
                        category = "사용자 저장 장소",
                        latitude = loc.latitude,
                        longitude = loc.longitude
                    )
                    try {
                        favorites = store.add(profileId, DailyFavorite(label, place))
                        alias = ""
                        message = "'$label' 위치를 저장했습니다."
                    } catch (e: Exception) {
                        message = "위치를 저장하지 못했습니다: ${e.message}"
                    }
                },
                enabled = currentLocation != null
            ) { Text("📍 현재 위치를 자주 가는 장소로 저장") }

            // 현재 위치가 아닌 다른 주소도 좌표로 변환해 저장한다.
            Text("🏡 주소로 장소 등록", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = addressInput,
                onValueChange = { next ->
                    addressInput = next
                    addressSearchVersion++
                    addressMatches = emptyList()
                    addressSearchLoading = false
                    addressMessage = null
                },
                label = { Text("도로명/지번 주소 (예: 서울 중구 세종대로 110)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = { searchAddress() },
                enabled = addressInput.isNotBlank() && !addressSearchLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (addressSearchLoading) "주소 찾는 중..." else "🔎 주소 검색")
            }
            addressMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            addressMatches.forEach { candidate ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(candidate.address, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            val label = alias.trim().ifEmpty { candidate.address }
                            val latKey = String.format(Locale.US, "%.6f", candidate.latitude)
                            val lonKey = String.format(Locale.US, "%.6f", candidate.longitude)
                            val place = TripPlace(
                                id = "daily_address_${latKey}_${lonKey}",
                                name = label,
                                address = candidate.address,
                                category = "주소 등록 장소",
                                latitude = candidate.latitude,
                                longitude = candidate.longitude
                            )
                            try {
                                favorites = store.add(profileId, DailyFavorite(label, place))
                                alias = ""
                                addressInput = ""
                                addressMatches = emptyList()
                                addressMessage = "'$label' 주소를 자주 가는 장소에 저장했습니다."
                                addressSearchVersion++
                            } catch (e: Exception) {
                                addressMessage = "주소 저장 실패: ${e.message}"
                            }
                        }) { Text("⭐ 이 주소 저장") }
                    }
                }
            }

            if (favorites.isEmpty()) {
                Text("저장된 장소가 없습니다. 현재 위치, 주소 검색 또는 아래 추천 결과에서 등록하세요.")
            } else {
                favorites.forEach { favorite ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(favorite.label, style = MaterialTheme.typography.titleSmall)
                            if (favorite.label != favorite.place.name) Text(favorite.place.name)
                            Text(favorite.place.address, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onRouteRequested(favorite.place) },
                                    enabled = !naviRouting
                                ) { Text("🚗 길 안내") }
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            favorites = store.remove(profileId, favorite.place.id)
                                            message = "'${favorite.label}' 저장을 해제했습니다."
                                        } catch (e: Exception) {
                                            message = "삭제 실패: ${e.message}"
                                        }
                                    }
                                ) { Text("삭제") }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("✨ 주변 새로운 장소", style = MaterialTheme.typography.titleMedium)
            // 좁은 분할화면에서도 칩이 잘리지 않도록 3개씩 배치한다.
            val categories = listOf(
                "맛집", "카페", "관광명소",
                "공원", "편의점", "주유소",
                "충전소", "주차장", "병원",
                "약국", "도서관"
            )
            categories.chunked(3).forEach { rowCategories ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCategories.forEach { choice ->
                        FilterChip(
                            selected = !personalizedMode && keyword == choice,
                            onClick = { searchPlaces(choice) },
                            label = { Text(choice) },
                            enabled = currentLocation != null && !loading
                        )
                    }
                }
            }
            FilterChip(
                selected = personalizedMode,
                onClick = { searchPersonalizedPlace() },
                label = { Text("✨ MOA 맞춤 추천 1곳") },
                enabled = currentLocation != null && !loading
            )
            OutlinedTextField(
                value = keyword,
                onValueChange = { next ->
                    keyword = next
                    if (personalizedMode) {
                        personalizedMode = false
                        results = emptyList()
                    }
                },
                label = { Text("직접 검색: 예) 산책로, 영화관") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (personalizedMode) searchPersonalizedPlace()
                    else searchPlaces(keyword)
                },
                enabled = !loading && currentLocation != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (loading) "검색 중..."
                    else if (personalizedMode) "✨ MOA 맞춤 추천 새로고침"
                    else "🔎 현재 위치에서 검색 / 새로고침"
                )
            }

            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            if (!loading && visibleRanked.isEmpty()) {
                Text("아직 새로운 추천 후보가 없습니다. 검색어를 바꾸거나 현재 위치를 확인해 주세요.")
            }
            if (personalizedMode && visibleRanked.isNotEmpty()) {
                Text("✨ MOA 알고리즘 맞춤 추천", style = MaterialTheme.typography.titleMedium)
                Text(
                    "프로필 취향·과거 선택 학습·현재 위치를 기준으로 검색 후보 중 한 곳을 선정했습니다.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            visibleRanked.forEach { scored ->
                val candidate = scored.place
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(candidate.name, style = MaterialTheme.typography.titleSmall)
                        Text(candidate.address, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "MOA 추천 점수: ${"%.1f".format(Locale.US, scored.score.total)} / 100점",
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (personalizedMode) {
                            scored.reasons.take(3).forEach { reason ->
                                Text("• $reason", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onRouteRequested(candidate.asTripPlace()) },
                                enabled = !naviRouting
                            ) { Text("🚗 길 안내") }
                            OutlinedButton(
                                onClick = {
                                    val label = alias.trim().ifEmpty { candidate.name }
                                    try {
                                        favorites = store.add(
                                            profileId,
                                            DailyFavorite(label, candidate.asTripPlace())
                                        )
                                        alias = ""
                                        message = "'$label'을 자주 가는 장소에 저장했습니다."
                                    } catch (e: Exception) {
                                        message = "장소 저장 실패: ${e.message}"
                                    }
                                }
                            ) { Text("⭐ 장소 저장") }
                        }
                    }
                }
            }

            HorizontalDivider()
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("모드 선택으로 돌아가기")
            }
        }
    }
}
