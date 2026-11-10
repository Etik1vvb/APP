package com.example.app.ui.trip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.app.data.KakaoLocalRepository
import com.example.app.data.TripPlace
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.example.app.voice.MoaVoiceManager

import com.example.app.data.UserProfile
import com.example.app.data.KakaoPlace
import com.example.app.vehicle.MoaPlaceScorer
import com.example.app.vehicle.MoaScoringContext
import com.example.app.vehicle.MoaPreferenceLearner

import com.example.app.vehicle.MoaFeedback
import com.example.app.vehicle.MoaPlaceCategory


enum class DestinationSelectionSource {
    MOA_RECOMMENDATION,
    KAKAO_SEARCH,
    PREVIOUS_DESTINATION
}


@Composable
fun TripDestinationScreen(
    trip: TripInfo,
    onBack: () -> Unit,
    onConfirm: (TripPlace, DestinationSelectionSource) -> Unit,
    initialPlace: TripPlace? = null,
    profile: UserProfile? = null
) {
    val repository = remember { KakaoLocalRepository() }
    val scope = rememberCoroutineScope()


    val moaScorer = remember { MoaPlaceScorer() }

    val context = LocalContext.current
    val moaLearner = remember(context) {
        MoaPreferenceLearner(context.applicationContext)
    }


    var learningVersion by remember(profile?.id) {
        mutableIntStateOf(0)
    }

    val learnedPreferences = remember(
        profile?.id,
        learningVersion
    ) {
        profile?.id?.let { id ->
            moaLearner.getLearnedPreferences(id)
        }.orEmpty()
    }


    fun scoreTripPlace(place: TripPlace): Double {

        val kakaoPlace = KakaoPlace(
            id = place.id,
            name = place.name,
            address = place.address,
            roadAddress = place.address,
            category = place.category,
            longitude = place.longitude,
            latitude = place.latitude,
            placeUrl = ""
        )

        val scoringContext = MoaScoringContext(
            profilePreferences = profile?.preferences.orEmpty(),
            profileAtmospheres = profile?.atmospheres.orEmpty(),
            tripPurpose = trip.purpose,
            desiredExperience = trip.desiredExperience,
            tripMood = trip.mood,
            latitude = place.latitude,
            longitude = place.longitude,
            learnedPreferences = learnedPreferences
        )

        return moaScorer.score(
            kakaoPlace,
            scoringContext
        ).score.total
    }


    var query by remember(trip.destination) { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TripPlace>>(emptyList()) }
    var recommendations by remember { mutableStateOf<List<TripPlace>>(emptyList()) }
    var selected by remember(initialPlace) { mutableStateOf(initialPlace) }
    var selectionSource by remember(initialPlace) {
        mutableStateOf(
            DestinationSelectionSource.PREVIOUS_DESTINATION
        )
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }

    val rejectedPlaceIds = remember(trip) {
        mutableStateListOf<String>()
    }

    var recommendationPage by remember(trip) {
        mutableIntStateOf(0)
    }

    var searchVisibleCount by remember(trip) {
        mutableIntStateOf(5)
    }



    fun isSamePlace(a: TripPlace, b: TripPlace?): Boolean {
        if (b == null) return false

        val sameId =
            a.id.isNotBlank() &&
                    b.id.isNotBlank() &&
                    a.id == b.id

        val sameName =
            a.name.trim() == b.name.trim()

        val sameAddress =
            a.address.isNotBlank() &&
                    b.address.isNotBlank() &&
                    a.address.trim() == b.address.trim()

        val sameCoordinates =
            kotlin.math.abs(a.latitude - b.latitude) < 0.00015 &&
                    kotlin.math.abs(a.longitude - b.longitude) < 0.00015

        return sameId ||
                (sameName && (sameAddress || sameCoordinates))
    }

    val availableRecommendations = recommendations
        .filter { place ->
            place.id !in rejectedPlaceIds &&
                    !isSamePlace(place, selected) &&
                    !isSamePlace(place, initialPlace)
        }
        .distinctBy { it.id }
        .sortedByDescending { place ->
            scoreTripPlace(place)
        }

// 선택 장소 변경으로 페이지 범위가 벗어난 경우 첫 페이지 표시
    val pageStart =
        (recommendationPage * 3)
            .takeIf { it < availableRecommendations.size }
            ?: 0

    val visibleRecommendations = availableRecommendations
        .sortedByDescending { place ->
            scoreTripPlace(place)
        }
        .drop(pageStart)
        .take(3)



    val voiceManager = remember(context) {
        MoaVoiceManager(context)
    }

    var isListening by remember {
        mutableStateOf(false)
    }

    var voiceStatus by remember {
        mutableStateOf("마이크를 눌러 음성으로 검색하세요")
    }

    DisposableEffect(voiceManager) {
        voiceManager.onStatusChanged = { status ->
            voiceStatus = status
        }

        voiceManager.onRecognized = { text, completed ->
            if (text.isNotBlank()) {
                query = text.trim()

                if (completed) {
                    voiceStatus = "음성 입력 완료"
                }
            }
        }

        voiceManager.onRecognitionEnded = {
            isListening = false
        }

        voiceManager.initialize()

        onDispose {
            voiceManager.release()
        }
    }

    fun startVoiceSearch() {
        if (isListening) {
            voiceManager.stopListening()
            isListening = false
        } else {
            query = ""
            error = null
            isListening = true
            voiceManager.startListening()
        }
    }


    fun performSearch(term: String, recommend: Boolean) {
        if (term.isBlank() || loading) return
        scope.launch {
            loading = true
            error = null
            try {

                val input = term.trim()

                val kakaoPlaces = if (recommend) {
                    repository.searchPlaces(
                        "${trip.destination.trim()} $input"
                    ).getOrThrow()
                } else {
                    val addressResults = repository
                        .searchAddress(input)
                        .getOrThrow()

                    if (addressResults.isNotEmpty()) {
                        addressResults
                    } else {
                        repository.searchPlaces(
                            "${trip.destination.trim()} $input"
                        ).getOrThrow()
                    }
                }


                val places = kakaoPlaces.mapNotNull { place ->
                    if (
                        place.id.isBlank() ||
                        place.latitude == 0.0 ||
                        place.longitude == 0.0
                    ) {
                        null
                    } else {
                        TripPlace(
                            id = place.id,
                            name = place.name,
                            address = place.roadAddress.ifBlank {
                                place.address
                            },
                            category = place.category,
                            latitude = place.latitude,
                            longitude = place.longitude,
                            parcelAddress = place.address
                        )
                    }
                }



                if (recommend) {
                    recommendations = places
                        .sortedByDescending { scoreTripPlace(it) }

                    recommendationPage = 0
                } else {
                    results = places
                        .sortedByDescending { scoreTripPlace(it) }

                    searchVisibleCount = 5
                    searched = true
                }


            } catch (e: Exception) {
                error = "검색 실패: ${e.message ?: "네트워크 연결을 확인해 주세요."}"
            } finally { loading = false }
        }
    }

    LaunchedEffect(trip.destination, trip.purpose, trip.desiredExperience, trip.mood) {
        val preference = (trip.desiredExperience + " " + trip.purpose).lowercase()
        val term = when {
            "카페" in preference || "커피" in preference -> "카페"
            "바다" in preference || "해변" in preference -> "해변"
            "맛집" in preference || "음식" in preference -> "맛집"
            "산책" in preference -> "공원"
            "문화" in preference -> "박물관"
            else -> "관광지"
        }
        performSearch(term, recommend = true)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("세부 목적지 선택", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text("여행 지역: ${trip.destination}  ·  목적: ${trip.purpose}")
        Text("한 번에 한 장소만 선택합니다.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = {
                Text("장소 검색 (예: 카페, 안목해변)")

            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),



            trailingIcon = {
                IconButton(
                    onClick = { startVoiceSearch() },
                    modifier = Modifier.size(64.dp)
                ) {
                    Text(
                        text = if (isListening) "⏹️" else "🎙️",
                        fontSize = 50.sp
                    )
                }
            }

        )

        Text(
            text = if (isListening) {
                "🎙️ 음성을 듣고 있어요. 말씀해 주세요."
            } else {
                voiceStatus
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { performSearch(query, recommend = false) }, enabled = !loading && query.isNotBlank()) {
                Text("카카오 검색")
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        selected?.let {
            Spacer(Modifier.height(8.dp))
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("선택한 목적지: ${it.name}", style = MaterialTheme.typography.titleMedium)
                    Text(it.address, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (searched) {
                item { Text("검색 결과", style = MaterialTheme.typography.titleMedium) }
                if (results.isEmpty() && !loading) item { Text("검색 결과가 없습니다.") }

                items(
                    results.take(searchVisibleCount),
                    key = { "search_${it.id}" }
                ) { place ->
                    PlaceChoice(
                        place = place,
                        selected = selected?.id == place.id,
                        score = scoreTripPlace(place),
                        onClick = {
                            selected = place
                            selectionSource = DestinationSelectionSource.KAKAO_SEARCH
                        }
                    )
                }

                if (results.size > searchVisibleCount) {
                    item {
                        OutlinedButton(
                            onClick = {
                                searchVisibleCount += 5
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("검색 결과 더 보기")
                        }
                    }
                }

            }
            item { HorizontalDivider(); Text("✨ MOA 사전 추천", style = MaterialTheme.typography.titleMedium) }
            item { Text("체크인 정보에 맞는 카카오 장소 후보입니다. 실시간 경로 추천은 다음 단계에서 구현합니다.", style = MaterialTheme.typography.bodySmall) }

            items(
                visibleRecommendations,
                key = { "moa_${it.id}" }
            ) { place ->

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    PlaceChoice(
                        place = place,
                        selected = selected?.id == place.id,
                        score = scoreTripPlace(place),
                        onClick = {
                            selected = place
                            selectionSource = DestinationSelectionSource.MOA_RECOMMENDATION
                        }
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                selected = place
                                selectionSource = DestinationSelectionSource.MOA_RECOMMENDATION
                            }
                        ) {
                            Text("선택하기")
                        }


                        OutlinedButton(
                            onClick = {
                                if (place.id !in rejectedPlaceIds) {

                                    val profileId = profile?.id

                                    if (!profileId.isNullOrBlank()) {
                                        val category = MoaPlaceCategory.classify(
                                            name = place.name,
                                            category = place.category
                                        )

                                        // 분류할 수 없는 장소나 주소는 학습하지 않음
                                        if (
                                            place.category != "주소" &&
                                            category != MoaPlaceCategory.OTHER
                                        ) {
                                            try {
                                                moaLearner.recordFeedback(
                                                    profileId = profileId,
                                                    category = category,
                                                    feedback = MoaFeedback.REJECTED
                                                )

                                                learningVersion += 1

                                            } catch (e: Exception) {
                                                android.util.Log.e(
                                                    "MOA_LEARNING",
                                                    "추천 거절 학습 저장 실패",
                                                    e
                                                )
                                            }
                                        }
                                    }

                                    // 학습 저장 실패 여부와 관계없이
                                    // 현재 화면에서는 거절한 장소를 제외
                                    rejectedPlaceIds.add(place.id)

                                    if (selected?.id == place.id) {
                                        selected = null
                                    }

                                    val remainingCount = availableRecommendations.size

                                    if (recommendationPage * 3 >= remainingCount) {
                                        recommendationPage = 0
                                    }
                                }
                            }
                        ) {
                            Text("거절")
                        }

                    }
                }
            }

            item {
                val remainingCount = availableRecommendations.size

                if (remainingCount > 3) {
                    OutlinedButton(
                        onClick = {
                            val nextPage = recommendationPage + 1

                            recommendationPage =
                                if (nextPage * 3 >= remainingCount) {
                                    0
                                } else {
                                    nextPage
                                }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔄 다른 장소 보기")
                    }
                }

                if (
                    recommendations.isNotEmpty() &&
                    remainingCount == 0
                ) {
                    Text(
                        "현재 추천 가능한 장소가 없습니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

        }
        Button(
            onClick = {
                selected?.let { place ->
                    onConfirm(place, selectionSource)
                }
            },
            enabled = selected != null && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Spacer(Modifier.height(8.dp))
            Text("이 목적지로 출발하기") }
    }
}



@Composable
private fun PlaceChoice(
    place: TripPlace,
    selected: Boolean,
    score: Double,
    onClick: () -> Unit
) {
    val isAddress = place.category == "주소"

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleSmall
            )

            if (place.address != place.name) {
                Text(
                    text = if (isAddress) {
                        "도로명: ${place.address}"
                    } else {
                        place.address
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (
                isAddress &&
                place.parcelAddress.isNotBlank() &&
                place.parcelAddress != place.address
            ) {
                Text(
                    text = "지번: ${place.parcelAddress}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = place.category,
                style = MaterialTheme.typography.labelSmall
            )

            if (!isAddress) {
                Text(
                    text = "✨ MOA 추천 점수: %.1f / 100점"
                        .format(score),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (selected) {
                Text(
                    text = "✓ 선택됨",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


