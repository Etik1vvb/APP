package com.example.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.data.SavedTrip
import com.example.app.data.TripDestinationRepository
import com.example.app.data.TripPlace
import com.example.app.data.TripRepository
import com.example.app.data.TripStatus
import com.example.app.data.MoaTripRatingStore
import androidx.activity.compose.BackHandler
import com.example.app.data.UserPreferencesRepository
import com.example.app.data.UserProfile
import com.example.app.ui.home.HomeScreen
import com.example.app.ui.home.ModeSelectionScreen
import com.example.app.ui.daily.MoaDailyModeScreen
import com.example.app.ui.profile.ProfileScreen
import com.example.app.ui.profile.ProfileSelectionScreen
import com.example.app.ui.theme.APPTheme
import com.example.app.ui.trip.TripInfo
import com.example.app.ui.trip.TripSetupScreen
import com.example.app.ui.trip.TripDestinationScreen
import com.example.app.ui.trip.MoaTripSatisfactionRatingRow
import com.example.app.ui.voice.AudioRecorderTestScreen
import com.example.app.ui.voice.MicrophoneTest
import com.example.app.ui.voice.MoaVoiceTestScreen
import com.example.app.ui.voice.PleosSttTestScreen
import com.example.app.ui.voice.PleosTtsTestScreen
import com.example.app.vehicle.VehicleRepository
import com.example.app.vehicle.MoaDriverDoorController
import com.example.app.vehicle.MoaDriverDoorCard
import com.example.app.vehicle.MoaEnergyRangeRecommendationCard
import kotlinx.coroutines.launch

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.app.vehicle.MoaLocationManager
import ai.pleos.playground.location.fused.model.LocationData

import com.example.app.data.KakaoLocalRepository
import com.example.app.data.KakaoPlace

import com.example.app.vehicle.MoaPlaceScorer
import com.example.app.vehicle.MoaScoringContext
import com.example.app.vehicle.MoaPreferenceLearner
import com.example.app.vehicle.MoaFeedback

import com.example.app.vehicle.MoaPlaceCategory
import com.example.app.vehicle.MoaNaviManager

import com.example.app.ui.trip.DestinationSelectionSource
import android.location.Location
import kotlinx.coroutines.CancellationException

import com.example.app.vehicle.MoaRecommendationEngine

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.saveable.rememberSaveable

import com.example.app.vehicle.MoaDocentApiClient

import ai.pleos.playground.tts.TextToSpeech as PleosTextToSpeech
import ai.pleos.playground.tts.constant.Mode as PleosTtsMode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.OnInitializeListener

import com.example.app.voice.MoaVoiceManager

import com.example.app.voice.MoaVoiceCommandParser
import com.example.app.voice.MoaVoiceAction




// 테스트가 끝나면 false로 변경하세요. 실사용: 자동 15분 / 수동 2분 / 목적지 변경 15분.
private const val MOA_TEST_MODE = true
private val MOA_AUTO_COOLDOWN_MS: Long = if (MOA_TEST_MODE) 20_000L else 15L * 60L * 1000L
private val MOA_MANUAL_COOLDOWN_MS: Long = if (MOA_TEST_MODE) 5_000L else 2L * 60L * 1000L
private val MOA_DESTINATION_COOLDOWN_MS: Long = if (MOA_TEST_MODE) 20_000L else 15L * 60L * 1000L
private val MOA_CARD_MIN_SCORE: Double = if (MOA_TEST_MODE) 40.0 else 60.0
private val MOA_AUTO_VOICE_MIN_SCORE: Double = if (MOA_TEST_MODE) 40.0 else 85.0
private const val MOA_VOICE_CONSENT_MS = 30_000L

class MainActivity : ComponentActivity() {
    private lateinit var vehicleRepository: VehicleRepository


    override fun onStart() {
        super.onStart()
        Log.d("MOA_LIFECYCLE", "MainActivity onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d("MOA_LIFECYCLE", "MainActivity onResume")
    }

    override fun onPause() {
        Log.d("MOA_LIFECYCLE", "MainActivity onPause")
        super.onPause()
    }

    override fun onStop() {
        Log.d("MOA_LIFECYCLE", "MainActivity onStop")
        super.onStop()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vehicleRepository = VehicleRepository(this)
        vehicleRepository.initialize()
        vehicleRepository.checkDriverDoor()

        setContent {
            val context = LocalContext.current

            // 운전석 도어 상태: 화면 전환과 무관하게 Activity에 한 인스턴스 유지
            val driverDoorController = remember(context) {
                MoaDriverDoorController(context.applicationContext)
            }
            DisposableEffect(driverDoorController) {
                driverDoorController.initialize()
                onDispose { driverDoorController.release() }
            }


            val moaNaviManager = remember(context) {
                MoaNaviManager(context.applicationContext)
            }

            var naviStatus by remember {
                mutableStateOf("내비게이션 연결 전")
            }

            var naviConnecting by remember {
                mutableStateOf(false)
            }

            var naviConnected by remember {
                mutableStateOf(false)
            }

            var naviRouting by remember {
                mutableStateOf(false)
            }


            fun requestSelectedPlaceRoute(place: TripPlace) {
                if (naviRouting) {
                    naviStatus = "다른 목적지 경로를 탐색 중입니다."
                    return
                }

                naviRouting = true
                naviStatus = "선택한 목적지 안내 준비 중: ${place.name}"

                try {
                    moaNaviManager.requestRecommendedDestination(
                        place = place,
                        onStatus = { status ->
                            naviStatus = status
                        },
                        onCompleted = {
                            naviRouting = false
                            naviConnected = true
                            naviStatus = "경로 탐색 완료: ${place.name}"

                            Log.d(
                                "MOA_NAVI",
                                "선택 목적지 경로 탐색 완료: ${place.name}"
                            )
                        },
                        onFailed = { message ->
                            naviRouting = false
                            naviStatus = "경로 탐색 실패: $message"

                            Log.e("MOA_NAVI", "경로 탐색 실패: $message")
                        }
                    )
                } catch (e: Exception) {
                    naviRouting = false
                    naviStatus = "경로 요청 오류: ${e.message}"
                    Log.e("MOA_NAVI", "경로 요청 예외", e)
                }
            }



            var pendingStartTripId by remember {
                mutableStateOf<String?>(null)
            }





            DisposableEffect(moaNaviManager) {
                onDispose {
                    moaNaviManager.release()
                }
            }

            val repository = remember(context) {
                UserPreferencesRepository(context.applicationContext)
            }
            val tripRepository = remember(context) {
                TripRepository(context.applicationContext)
            }
            val tripRatingStore = remember(context) {
                MoaTripRatingStore(context.applicationContext)
            }
            var tripRatingVersion by remember { mutableIntStateOf(0) }
            var tripRatingMessage by remember { mutableStateOf<String?>(null) }
            val destinationRepository = remember(context) {
                TripDestinationRepository(context.applicationContext)
            }
            val destinations by destinationRepository.destinations.collectAsState(initial = emptyMap())
            val scope = rememberCoroutineScope()

            val docentApi = remember { MoaDocentApiClient() }

            var docentStory by remember {
                mutableStateOf<String?>(null)
            }

            var docentError by remember {
                mutableStateOf<String?>(null)
            }

            var docentLoading by remember {
                mutableStateOf(false)
            }




            // null은 아직 DataStore를 읽는 중이라는 뜻입니다.
            val profiles by repository.profiles.collectAsState(initial = null)
            val selectedProfileId by repository.selectedProfileId.collectAsState(initial = null)
            val profile by repository.userProfile.collectAsState(initial = null)
            val savedTrip by tripRepository.savedTrip.collectAsState(initial = null)
            val tripStatus by tripRepository.tripStatus.collectAsState(initial = null)
            val allProfileTrips by tripRepository.trips.collectAsState(initial = emptyList())
            val selectedTrip by tripRepository.selectedTrip.collectAsState(initial = null)

            // 앱을 시작할 때마다 프로필 선택 화면을 보여줍니다.
            var currentScreen by rememberSaveable {
                mutableStateOf("profile_select")
            }
            var profileEditReturnScreen by remember {
                mutableStateOf("home")
            }
            var profileError by remember { mutableStateOf<String?>(null) }
            var tripError by remember { mutableStateOf<String?>(null) }
            var deleteTripTarget by remember { mutableStateOf<SavedTrip?>(null) }
            var editTripTarget by remember { mutableStateOf<SavedTrip?>(null) }

            var repeatTripSource by remember {
                mutableStateOf<SavedTrip?>(null)
            }

            var destinationTripId by rememberSaveable {
                mutableStateOf<String?>(null)
            }

            // 여행별 자동 음성 추천 기록

            val autoVoiceContext = LocalContext.current

            val autoVoicePrefs = remember {
                autoVoiceContext.applicationContext
                    .getSharedPreferences(
                        "moa_auto_voice",
                        android.content.Context.MODE_PRIVATE
                    )
            }

            val autoTripKey = destinationTripId ?: "none"

            val autoSpokenPlaceIds = remember(autoTripKey) {
                mutableStateListOf<String>().apply {
                    addAll(
                        autoVoicePrefs.getStringSet(
                            "spoken_$autoTripKey",
                            emptySet()
                        ).orEmpty()
                    )
                }
            }

            var autoLastSpokenAt by remember(autoTripKey) {
                mutableLongStateOf(
                    autoVoicePrefs.getLong(
                        "last_$autoTripKey",
                        0L
                    ).takeIf {
                        it in 0L..android.os.SystemClock.elapsedRealtime()
                    } ?: 0L
                )
            }


            var lastManualVoiceAt by remember(destinationTripId) {
                mutableLongStateOf(0L)
            }

            var destinationBusy by remember { mutableStateOf(false) }


            val kakaoRepository = remember { KakaoLocalRepository() }


            val moaScorer = remember { MoaPlaceScorer() }

            val moaLearner = remember(context) {
                MoaPreferenceLearner(context.applicationContext)
            }

            var learningVersion by remember {
                mutableIntStateOf(0)
            }




            val rejectedPlaceIds = remember(
                destinationTripId,
                profile?.id
            ) {
                mutableStateListOf<String>()
            }

            // 수락한 장소도 같은 여행에서 다시 추천하지 않습니다.
            val acceptedPlaceIds = remember(destinationTripId, profile?.id) {
                mutableStateListOf<String>()
            }



            var nearbyPlaces by remember {
                mutableStateOf<List<KakaoPlace>>(emptyList())
            }

            var voiceSearchPlaces by remember(destinationTripId) {
                mutableStateOf<List<KakaoPlace>>(emptyList())
            }

            var voiceSearchLoading by remember(destinationTripId) {
                mutableStateOf(false)
            }

            var voiceSearchKeyword by remember(destinationTripId) {
                mutableStateOf("")
            }

            var voiceSearchMessage by remember(destinationTripId) {
                mutableStateOf("")
            }

            var voiceSearchScores by remember(destinationTripId) {
                mutableStateOf<Map<String, Double>>(emptyMap())
            }

            var voiceDocentLoading by remember(destinationTripId) {
                mutableStateOf(false)
            }

            var voiceDocentStory by remember(destinationTripId) {
                mutableStateOf<String?>(null)
            }
            // GPT 설명은 요청한 장소 ID와 함께 보관하여 다른 장소에 재사용하지 않는다.
            var voiceDocentStoryPlaceId by remember(destinationTripId) {
                mutableStateOf<String?>(null)
            }


            var voiceDocentPendingPlace by remember(destinationTripId) {
                mutableStateOf<KakaoPlace?>(null)
            }

            var voiceDocentAwaitingConsent by remember(destinationTripId) {
                mutableStateOf(false)
            }


            var voiceDocentConsentExpiresAt by remember(destinationTripId) {
                mutableLongStateOf(0L)
            }

            // 새로운 추천을 했을 때 30초 응답 대기 시작
            fun startVoiceConsent(place: KakaoPlace) {
                voiceDocentPendingPlace = place
                voiceDocentAwaitingConsent = true
                voiceDocentConsentExpiresAt =
                    android.os.SystemClock.elapsedRealtime() + MOA_VOICE_CONSENT_MS

                Log.d("MOA_VOICE", "응답 대기 시작: ${place.name}")
            }

            // 기존 추천에 대한 응답 대기 종료
            fun cancelVoiceConsent(reason: String) {
                if (!voiceDocentAwaitingConsent) return

                voiceDocentAwaitingConsent = false
                voiceDocentPendingPlace = null
                voiceDocentConsentExpiresAt = 0L

                Log.d("MOA_VOICE", "응답 대기 종료: $reason")
            }

// 30초 후 자동 종료
            LaunchedEffect(destinationTripId, voiceDocentConsentExpiresAt) {
                val expiresAt = voiceDocentConsentExpiresAt

                if (expiresAt <= 0L) return@LaunchedEffect

                kotlinx.coroutines.delay(
                    (expiresAt - android.os.SystemClock.elapsedRealtime())
                        .coerceAtLeast(0L)
                )

                if (
                    voiceDocentAwaitingConsent &&
                    voiceDocentConsentExpiresAt == expiresAt
                ) {
                    cancelVoiceConsent("30초 응답 시간 만료")
                }
            }



            var voiceDocentGeneration by remember(destinationTripId) {
                mutableIntStateOf(0)
            }


            var voiceDocentError by remember(destinationTripId) {
                mutableStateOf<String?>(null)
            }

            var voiceCandidatesVisibleCount by remember(destinationTripId) {
                mutableIntStateOf(4)
            }

            var nearbyLoading by remember {
                mutableStateOf(false)
            }

            // 자동 추천을 마지막으로 검색한 위치
            var lastAutoSearchLocation by remember {
                mutableStateOf<Pair<Double, Double>?>(null)
            }

// 여행이 바뀌면 자동 검색 기준도 초기화하기 위한 ID
            var lastAutoSearchTripId by remember {
                mutableStateOf<String?>(null)
            }

            var nearbyError by remember {
                mutableStateOf<String?>(null)
            }


            var nearbySuccess by remember {
                mutableStateOf<String?>(null)
            }


            val locationPermission = "pleos.car.permission.FUSED_LOCATION"

            val moaPreferenceLearner = remember(context) {
                MoaPreferenceLearner(context.applicationContext)
            }

            val locationManager = remember(context) {
                MoaLocationManager(context.applicationContext)
            }

            val autoRecommendationEngine = remember(kakaoRepository) {
                MoaRecommendationEngine(
                    searchPlaces = { latitude, longitude ->
                        kakaoRepository.searchPlaces(
                            query = "관광명소",
                            latitude = latitude,
                            longitude = longitude,
                            radius = 3000
                        )
                    },

                    onPlaces = { places ->
                        // 현재 화면에서 보이는 카드의 변화는 아래 LaunchedEffect에서 감지한다.
                        nearbyPlaces = places
                        nearbyError = null
                    },
                    onLoading = { loading ->
                        nearbyLoading = loading
                    },
                    onError = { message ->
                        nearbyError = message
                    }
                )
            }

            DisposableEffect(autoRecommendationEngine) {
                onDispose {
                    autoRecommendationEngine.close()
                }
            }

            var currentLocation by remember {
                mutableStateOf<LocationData?>(null)
            }

            var locationStatus by remember {
                mutableStateOf("위치 수신 대기 중")
            }

            var locationPermissionGranted by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        context,
                        locationPermission
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }

            val locationPermissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    locationPermissionGranted = granted
                    if (!granted) {
                        locationStatus = "위치 권한이 거부되었습니다."
                    }
                }


            fun recordDestinationAcceptance(
                place: TripPlace,
                source: DestinationSelectionSource
            ) {
                if (source != DestinationSelectionSource.MOA_RECOMMENDATION) {
                    return
                }

                val profileId = profile?.id
                if (profileId.isNullOrBlank()) return

                if (place.category == "주소") return

                val category = MoaPlaceCategory.classify(
                    name = place.name,
                    category = place.category
                )

                if (category == MoaPlaceCategory.OTHER) return

                try {
                    moaPreferenceLearner.recordFeedback(
                        profileId = profileId,
                        category = category,
                        feedback = MoaFeedback.ACCEPTED
                    )

                    Log.d(
                        "MOA_LEARNING",
                        "추천 수락 학습 완료: $category"
                    )
                } catch (e: Exception) {
                    Log.e(
                        "MOA_LEARNING",
                        "추천 수락 학습 실패",
                        e
                    )
                }
            }


            // 추천 수락의 유일한 실행 경로. 화면 버튼과 음성 명령이 공통 사용한다.
            // 안정화된 순서: 앱 목적지 저장 성공 -> NaviHelper 경로 요청.
            fun acceptRecommendation(placeToAccept: KakaoPlace, source: String) {
                val profileId = profile?.id
                val tripId = destinationTripId
                if (profileId.isNullOrBlank() || tripId == null) {
                    nearbyError = "여행과 프로필을 먼저 선택해 주세요."
                    return
                }
                if (destinationBusy || naviRouting) {
                    nearbyError = "기존 경로 요청을 처리 중입니다."
                    return
                }

                val selectedPlace = TripPlace(
                    id = placeToAccept.id,
                    name = placeToAccept.name,
                    address = placeToAccept.address,
                    category = placeToAccept.category,
                    latitude = placeToAccept.latitude,
                    longitude = placeToAccept.longitude
                )
                val alreadySaved = isSameDestination(
                    candidateId = selectedPlace.id,
                    candidateName = selectedPlace.name,
                    candidateLatitude = selectedPlace.latitude,
                    candidateLongitude = selectedPlace.longitude,
                    current = destinations[tripId]
                )

                cancelVoiceConsent("$source 추천 수락")
                destinationBusy = true
                nearbyError = null
                nearbySuccess = null
                naviStatus = "앱 목적지 저장 중..."

                scope.launch {
                    try {
                        if (destinationTripId != tripId) {
                            nearbyError = "진행 중인 여행이 변경되었습니다."
                            return@launch
                        }
                        if (!alreadySaved) {
                            destinationRepository.save(tripId, selectedPlace)
                            if (selectedPlace.id !in acceptedPlaceIds) {
                                acceptedPlaceIds.add(selectedPlace.id)
                            }
                            // 직접 음성 검색한 장소도 수락 후 추천 목록에서 제외한다.
                            voiceSearchPlaces = voiceSearchPlaces.filterNot {
                                it.id == selectedPlace.id
                            }
                            Log.d("MOA_ACCEPT", "APP_SAVE_OK [$source]: ${selectedPlace.name}")

                            try {
                                val category = MoaPlaceCategory.classify(placeToAccept)
                                moaLearner.recordFeedback(
                                    profileId = profileId,
                                    category = category,
                                    feedback = MoaFeedback.ACCEPTED
                                )
                                learningVersion++
                            } catch (e: Exception) {
                                Log.e("MOA_LEARNING", "추천 수락 학습 오류", e)
                            }
                        }

                        nearbySuccess = "앱 목적지를 '${selectedPlace.name}'(으)로 저장했습니다."
                        destinationBusy = false
                        naviRouting = true
                        naviStatus = "내비게이션 경로 요청 중..."

                        try {
                            moaNaviManager.requestRecommendedDestination(
                                place = selectedPlace,
                                onStatus = { status -> naviStatus = status },
                                onCompleted = {
                                    naviConnected = true
                                    naviRouting = false
                                    nearbySuccess =
                                        "'${selectedPlace.name}' 앱 목적지 저장 및 내비 경로 탐색 완료"
                                    nearbyError = null
                                    Log.d("MOA_ACCEPT", "NAVI_COMPLETED [$source]: ${selectedPlace.name}")
                                },
                                onFailed = { message ->
                                    naviRouting = false
                                    naviStatus = message
                                    if (message.contains("완료 이벤트")) {
                                        nearbyError = null
                                        nearbySuccess =
                                            "앱 목적지는 저장됐습니다. 내비 경로 완료 응답이 없어 화면에서 확인해 주세요."
                                    } else {
                                        nearbyError =
                                            "앱 목적지는 저장됐지만 내비 경로 요청을 확인하지 못했습니다. ($message)"
                                    }
                                    Log.w("MOA_ACCEPT", "NAVI_UNCONFIRMED [$source]: $message")
                                }
                            )
                        } catch (e: Exception) {
                            naviRouting = false
                            nearbyError = "앱 목적지는 저장됐지만 내비 경로 요청 중 오류가 발생했습니다."
                            Log.e("MOA_ACCEPT", "NAVI_REQUEST_ERROR [$source]", e)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        nearbyError = "앱 목적지 저장에 실패했습니다: ${e.message}"
                        Log.e("MOA_ACCEPT", "APP_SAVE_FAILED [$source]", e)
                    } finally {
                        destinationBusy = false
                    }
                }
            }

            // 명시적인 장소 거절만 취향 학습에 반영한다.
            // '괜찮아/아니야'는 상세 설명 거절이며, 이 함수에 들어오지 않는다.
            fun rejectRecommendation(placeToReject: KakaoPlace, source: String) {
                if (destinationBusy || naviRouting) {
                    nearbyError = "경로 요청 중에는 추천을 변경할 수 없습니다."
                    return
                }
                cancelVoiceConsent("$source 추천 거절")
                val profileId = profile?.id
                if (!profileId.isNullOrBlank()) {
                    try {
                        moaLearner.recordFeedback(
                            profileId = profileId,
                            category = MoaPlaceCategory.classify(placeToReject),
                            feedback = MoaFeedback.REJECTED
                        )
                        learningVersion++
                    } catch (e: Exception) {
                        Log.e("MOA_LEARNING", "추천 거절 학습 오류", e)
                    }
                }
                if (placeToReject.id !in rejectedPlaceIds) {
                    rejectedPlaceIds.add(placeToReject.id)
                }
                voiceSearchPlaces = voiceSearchPlaces.filterNot {
                    it.id == placeToReject.id
                }
                Log.d("MOA_VOICE_ACTION", "REJECT [$source]: ${placeToReject.name}")
            }

            DisposableEffect(locationManager) {

                locationManager.onLocationChanged = { location ->
                    currentLocation = location

                    autoRecommendationEngine.onLocation(
                        location.latitude,
                        location.longitude
                    )

                    Log.d(
                        "MOA_LOCATION",
                        "위치 업데이트: ${location.latitude}, ${location.longitude}"
                    )
                }


                locationManager.onStatusChanged = { status ->
                    locationStatus = status
                }

                onDispose {
                    locationManager.release()
                }
            }

            LaunchedEffect(currentScreen, locationPermissionGranted) {
                Log.d(
                    "MOA_AUTO_TRACE",
                    "screen=$currentScreen, " +
                            "tripId=$destinationTripId, " +
                            "location=${currentLocation?.latitude},${currentLocation?.longitude}, " +
                            "loading=$nearbyLoading"
                )
                if ((currentScreen == "trip_active" || currentScreen == "daily_mode") &&
                    !locationPermissionGranted
                ) {
                    locationPermissionLauncher.launch(locationPermission)
                }
            }

            DisposableEffect(
                currentScreen,
                locationPermissionGranted,
                locationManager
            ) {
                if ((currentScreen == "trip_active" || currentScreen == "daily_mode") &&
                    locationPermissionGranted
                ) {
                    locationManager.start()
                } else {
                    locationManager.stop()
                }

                onDispose {
                    locationManager.stop()
                }
            }


            BackHandler(enabled = currentScreen == "moa_voice_test") {
                Log.d("MOA_NAV", "VOICE_BACK -> history")
                currentScreen = "history"
            }

            LaunchedEffect(currentScreen) {
                Log.d("MOA_NAV", "SCREEN=$currentScreen")
            }

            LaunchedEffect(currentScreen, destinationTripId) {
                if (currentScreen != "trip_active") {
                    cancelVoiceConsent("여행 추천 화면 종료")
                }
            }


            LaunchedEffect(
                currentScreen,
                destinationTripId,
                allProfileTrips
            ) {
                val activeId = destinationTripId?.takeIf { id ->
                    currentScreen == "trip_active" &&
                            allProfileTrips.any {
                                it.id == id &&
                                        it.status == TripStatus.ACTIVE
                            }
                }

                autoRecommendationEngine.setActiveTrip(activeId)
            }

            APPTheme {

                val ttsContext = LocalContext.current.applicationContext

                var ttsReady by remember {
                    mutableStateOf(false)
                }

                var ttsStatus by remember {
                    mutableStateOf("TTS 초기화 대기")
                }

                val docentTts = remember(ttsContext) {
                    PleosTextToSpeech(
                        context = ttsContext,
                        mode = PleosTtsMode.ON_DEVICE
                    )
                }

                DisposableEffect(docentTts) {
                    val mainHandler = android.os.Handler(
                        android.os.Looper.getMainLooper()
                    )

                    var disposed = false

                    try {
                        docentTts.initialize(
                            object : OnInitializeListener {
                                override fun onSuccess() {
                                    mainHandler.post {
                                        if (!disposed) {
                                            ttsReady = true
                                            ttsStatus = "MOA 음성 준비 완료"
                                        }
                                    }
                                }

                                override fun onFailure(errorType: ErrorType) {
                                    mainHandler.post {
                                        if (!disposed) {
                                            ttsReady = false
                                            ttsStatus = "TTS 초기화 실패: $errorType"
                                        }
                                    }
                                }
                            }
                        )
                    } catch (e: Exception) {
                        ttsReady = false
                        ttsStatus = "TTS 초기화 오류: ${e.message}"
                    }

                    onDispose {
                        disposed = true
                        docentTts.stop()
                        docentTts.release()
                    }
                }

                when (currentScreen) {
                    "profile_select" -> {
                        val loadedProfiles = profiles
                        if (loadedProfiles == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Column {
                                profileError?.let { message ->
                                    Text(
                                        text = message,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                    )
                                }
                                ProfileSelectionScreen(
                                    profiles = loadedProfiles,
                                    selectedProfileId = selectedProfileId,
                                    onSelectProfile = { id ->
                                        profileError = null
                                        scope.launch {
                                            try {
                                                if (repository.selectProfile(id)) {
                                                    currentScreen = "mode_select"
                                                } else {
                                                    profileError = "프로필을 선택하지 못했습니다. 다시 시도해주세요."
                                                }
                                            } catch (e: Exception) {
                                                Log.e("RoadTripProfile", "프로필 선택 실패", e)
                                                profileError = "프로필 선택 중 오류가 발생했습니다."
                                            }
                                        }
                                    },
                                    onAddProfile = {
                                        profileError = null
                                        currentScreen = "profile"
                                    },
                                    onDeleteProfile = { id ->
                                        profileError = null
                                        scope.launch {
                                            try {
                                                if (repository.deleteProfile(id)) {
                                                    tripRepository.deleteTripsForProfile(id)
                                                } else {
                                                    profileError = "현재 선택된 프로필은 삭제할 수 없습니다."
                                                }
                                            } catch (e: Exception) {
                                                Log.e("RoadTripProfile", "프로필 삭제 실패", e)
                                                profileError = "프로필 삭제 중 오류가 발생했습니다."
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    "profile" -> {
                        ProfileScreen(
                            onSave = { _, _, _, _ -> },
                            onSaveProfile = { newProfile ->
                                scope.launch {
                                    try {
                                        repository.saveProfile(newProfile)
                                        profileError = null
                                        currentScreen = "profile_select"
                                    } catch (e: Exception) {
                                        Log.e("RoadTripProfile", "프로필 저장 실패", e)
                                        profileError = "프로필 저장 중 오류가 발생했습니다."
                                        currentScreen = "profile_select"
                                    }
                                }
                            },
                            onBack = {
                                currentScreen = "profile_select"
                            }

                        )
                    }

                    "profile_edit" -> {
                        val editingProfile = profile?.takeIf { it.id.isNotBlank() }
                        if (editingProfile == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            key(editingProfile.id) {
                                ProfileScreen(
                                    onSave = { _, _, _, _ -> },
                                    initialProfile = editingProfile,
                                    onSaveProfile = { updatedProfile ->
                                        scope.launch {
                                            try {
                                                if (repository.updateProfile(updatedProfile)) {
                                                    profileError = null
                                                    currentScreen = "home"
                                                } else {
                                                    profileError = "프로필을 수정하지 못했습니다."
                                                    currentScreen = "home"
                                                }
                                            } catch (e: Exception) {
                                                Log.e("RoadTripProfile", "프로필 수정 실패", e)
                                                profileError = "프로필 수정 중 오류가 발생했습니다."
                                                currentScreen = "home"
                                            }
                                        }
                                    },
                                    onBack = {
                                        currentScreen = profileEditReturnScreen
                                    }
                                )
                            }
                        }
                    }

                    "mode_select" -> {
                        ModeSelectionScreen(
                            nickname = profile?.nickname,
                            onTravelClick = { currentScreen = "home" },
                            onDailyClick = { currentScreen = "daily_mode" },
                            onChangeProfile = {
                                profileError = null
                                currentScreen = "profile_select"
                            }
                        )
                    }

                    "daily_mode" -> {
                        MoaDailyModeScreen(
                            driverDoorController = driverDoorController,
                            profile = profile,
                            currentLocation = currentLocation,
                            locationStatus = locationStatus,
                            kakaoRepository = kakaoRepository,
                            moaScorer = moaScorer,
                            moaLearner = moaLearner,
                            naviRouting = naviRouting,
                            naviStatus = naviStatus,
                            onRouteRequested = { selected ->
                                // 일상모드에서는 여행 목적지를 변경하지 않고 내비 경로만 요청한다.
                                requestSelectedPlaceRoute(selected)
                            },
                            onBack = { currentScreen = "mode_select" }
                        )
                    }

                    "home" -> {
                        HomeScreen(
                            nickname = profile?.nickname,
                            destination = savedTrip?.destination,
                            tripStatus = tripStatus,
                            onPrimaryClick = {
                                if (profile?.id.isNullOrBlank()) {
                                    currentScreen = "profile_select"
                                } else {
                                    currentScreen = "saved"
                                }
                            },
                            onProfileClick = {
                                profileEditReturnScreen = "home"
                                currentScreen = "profile_edit"
                            },
                            onProfileSelectClick = {
                                profileError = null
                                currentScreen = "profile_select"
                            },
                            onHistoryClick = {
                                currentScreen = "history"
                            }
                        )
                    }

                    "saved" -> {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("여행 관리", style = MaterialTheme.typography.headlineSmall)
                            Text("${profile?.nickname.orEmpty()} 프로필의 여행")
                            OutlinedButton(
                                onClick = {
                                    profileError = null
                                    profileEditReturnScreen = "saved"
                                    currentScreen = "profile_edit"
                                },
                                enabled = !profile?.id.isNullOrBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("내 여행 취향 수정") }
                            profileError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(
                                onClick = {
                                    tripError = null
                                    editTripTarget = null
                                    repeatTripSource = null
                                    currentScreen = "trip"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("+ 새 여행 등록하기") }
                            tripError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            val ongoing = allProfileTrips.filter { it.status != TripStatus.COMPLETED }
                            if (ongoing.isEmpty()) Text("예정되거나 진행 중인 여행이 없습니다.")
                            ongoing.forEach { trip ->
                                val isSelected = selectedTrip?.id == trip.id
                                val isActive = trip.status == TripStatus.ACTIVE

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    border = if (trip.status == TripStatus.ACTIVE) {
                                        BorderStroke(
                                            width = 2.dp,
                                            color = Color(0xFF6C45DC)
                                        )
                                    } else {
                                        null
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (trip.status == TripStatus.ACTIVE) {
                                            Color(0xFFF2EDFF)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = 2.dp
                                    )
                                ) {

                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(trip.info.destination, style = MaterialTheme.typography.titleMedium)
                                        Text("${trip.info.startDate} ~ ${trip.info.endDate}")
                                        Text("동행자: ${trip.info.companions} / 목적: ${trip.info.purpose}")
                                        Text(when(trip.status) {
                                            TripStatus.PLANNED -> "출발 전"
                                            TripStatus.ACTIVE -> "여행 중"
                                            TripStatus.COMPLETED -> "완료"
                                        } + if (isSelected) " · 현재 선택" else "")


                                        if (trip.status == TripStatus.PLANNED) {
                                            Button(
                                                onClick = {
                                                    tripError = null

                                                    val activeTrip = allProfileTrips.firstOrNull {
                                                        it.status == TripStatus.ACTIVE &&
                                                                it.profileId == trip.profileId &&
                                                                it.id != trip.id
                                                    }

                                                    if (activeTrip != null) {
                                                        pendingStartTripId = trip.id
                                                    } else {
                                                        scope.launch {
                                                            try {
                                                                val started = tripRepository.startTrip(trip.id)

                                                                if (started) {
                                                                    destinationTripId = trip.id
                                                                    currentScreen = "destination"
                                                                } else {
                                                                    tripError = "여행 시작에 실패했습니다."
                                                                }
                                                            } catch (e: Exception) {
                                                                Log.e("RoadTripTrip", "여행 시작 실패", e)
                                                                tripError = "여행 시작 중 오류가 발생했습니다."
                                                            }
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text("여행 시작하기 · 목적지 선택")
                                            }
                                        }



                                        if (trip.status == TripStatus.ACTIVE) {
                                            Text("현재 목적지: ${destinations[trip.id]?.name ?: "미설정"}")

                                            val existingDestination = destinations[trip.id]

                                            Button(
                                                onClick = {
                                                    val destination = existingDestination ?: return@Button

                                                    if (naviRouting) return@Button

                                                    destinationTripId = trip.id
                                                    currentScreen = "trip_active"

                                                    naviRouting = true
                                                    naviStatus = "저장된 목적지 안내 준비 중..."

                                                    try {
                                                        moaNaviManager.requestRecommendedDestination(
                                                            place = destination,

                                                            onStatus = { status ->
                                                                naviStatus = status
                                                            },

                                                            onCompleted = {
                                                                naviConnected = true
                                                                naviRouting = false
                                                                naviStatus = "경로 탐색 완료: ${destination.name}"

                                                                Log.d(
                                                                    "MOA_NAVI",
                                                                    "여행 이어가기 경로 탐색 완료: ${destination.name}"
                                                                )
                                                            },

                                                            onFailed = { message ->
                                                                naviRouting = false
                                                                naviStatus = "길 안내 실패: $message"

                                                                Log.e(
                                                                    "MOA_NAVI",
                                                                    "여행 이어가기 경로 요청 실패: $message"
                                                                )
                                                            }
                                                        )

                                                    } catch (e: Exception) {
                                                        naviRouting = false
                                                        naviStatus = "길 안내 오류: ${e.message}"

                                                        Log.e(
                                                            "MOA_NAVI",
                                                            "여행 이어가기 예외",
                                                            e
                                                        )
                                                    }
                                                },
                                                enabled = existingDestination != null && !naviRouting,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("🚗 여행 계속하기")
                                            }

                                            OutlinedButton(onClick = {
                                                destinationTripId = trip.id
                                                currentScreen = "destination"
                                            }) { Text("다음 목적지 선택 / 변경") }
                                            Button(onClick = {
                                                scope.launch {
                                                    try {
                                                        if (!tripRepository.completeTrip(trip.id))
                                                            tripError = "여행 종료에 실패했습니다."
                                                        else tripError = null
                                                    } catch (e: Exception) {
                                                        Log.e("RoadTripTrip", "여행 종료 실패", e)
                                                        tripError = "여행 종료 중 오류가 발생했습니다."
                                                    }
                                                }
                                            }) { Text("여행 종료하기") }
                                        }
                                        OutlinedButton(onClick = {
                                            tripError = null
                                            editTripTarget = trip
                                            currentScreen = "trip"
                                        }) { Text("체크인 수정") }
                                        TextButton(onClick = { deleteTripTarget = trip }) {
                                            Text("여행 삭제", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                            Text("완료된 여행: ${allProfileTrips.count { it.status == TripStatus.COMPLETED }}개")
                            OutlinedButton(onClick = { currentScreen = "history" }) { Text("지난 여행 기록 보기") }
                            OutlinedButton(onClick = { currentScreen = "home" }) { Text("홈으로 돌아가기") }
                        }

                        val requestedTripId = pendingStartTripId

                        if (requestedTripId != null) {
                            val activeTrip = allProfileTrips.firstOrNull {
                                it.status == TripStatus.ACTIVE &&
                                        it.id != requestedTripId
                            }

                            AlertDialog(
                                onDismissRequest = {
                                    pendingStartTripId = null
                                },
                                title = {
                                    Text("진행 중인 여행이 있습니다")
                                },
                                text = {
                                    Text(
                                        "현재 ${activeTrip?.info?.destination ?: "다른"} 여행이 진행 중입니다.\n\n" +
                                                "새 여행을 시작하면 기존 여행은 준비 상태로 전환됩니다. " +
                                                "나중에 다시 이어갈 수 있습니다.\n\n" +
                                                "새 여행의 목적지를 선택하시겠습니까?"
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val tripId = pendingStartTripId ?: return@TextButton
                                            pendingStartTripId = null

                                            scope.launch {
                                                try {
                                                    val switched = tripRepository.switchToTrip(tripId)

                                                    if (switched) {
                                                        destinationTripId = tripId
                                                        tripError = null
                                                        currentScreen = "destination"
                                                    } else {
                                                        tripError = "여행 전환에 실패했습니다."
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("RoadTripTrip", "여행 전환 실패", e)
                                                    tripError = "여행 전환 중 오류가 발생했습니다."
                                                }
                                            }
                                        }
                                    ) {
                                        Text("새 여행 준비하기")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            pendingStartTripId = null
                                        }
                                    ) {
                                        Text("취소")
                                    }
                                }
                            )
                        }

                    }

                    "destination" -> {
                        val target = allProfileTrips.firstOrNull { it.id == destinationTripId }
                        if (target == null || target.status == TripStatus.COMPLETED) {
                            Column(Modifier.fillMaxSize().padding(24.dp)) {
                                Text("선택한 여행을 찾을 수 없습니다.")
                                Button(onClick = { currentScreen = "saved" }) { Text("여행 목록으로") }
                            }
                        } else {
                            TripDestinationScreen(
                                trip = target.info,
                                profile = profile,
                                initialPlace = destinations[target.id],
                                onBack = { currentScreen = "saved" },

                                onConfirm = { place, source ->
                                    if (!destinationBusy) {
                                        destinationBusy = true

                                        scope.launch {
                                            try {
                                                destinationRepository.save(target.id, place)
                                                recordDestinationAcceptance(place, source)
                                                tripError = null
                                                currentScreen = "trip_active"

// 사전 추천 목적지를 NaviHelper에 자동 연결
                                                requestSelectedPlaceRoute(place)
                                            } catch (e: Exception) {
                                                Log.e("RoadTripDestination", "목적지 확정 실패", e)
                                                tripError = "목적지 확정 중 오류가 발생했습니다."
                                            } finally {
                                                destinationBusy = false
                                            }
                                        }
                                    }
                                }

                            )

                        }
                    }

                    "trip_active" -> {
                        val configuration = LocalConfiguration.current
                        val screenWidth = configuration.screenWidthDp

                        val compactMode = screenWidth < 900

                        Log.d(
                            "MOA_UI",
                            "screenWidthDp=$screenWidth, compactMode=$compactMode"
                        )

                        var showTripTools by remember(destinationTripId) {
                            mutableStateOf(false)
                        }
                        var showAllRequestedPlaces by remember(destinationTripId) {
                            mutableStateOf(false)
                        }



                        var tripRecommendationPage by remember(
                            destinationTripId,
                            showAllRequestedPlaces
                        ) {
                            mutableIntStateOf(0)
                        }



                        val target = allProfileTrips.firstOrNull { it.id == destinationTripId }
                        val place = destinationTripId?.let { destinations[it] }


                        var lastDestinationChangedAt by remember(destinationTripId) {
                            mutableLongStateOf(
                                autoVoicePrefs.getLong(
                                    "destination_changed_$autoTripKey",
                                    0L
                                )
                            )
                        }

                        LaunchedEffect(destinationTripId, place?.id) {
                            val currentPlaceId = place?.id
                                ?: return@LaunchedEffect

                            val savedPlaceId = autoVoicePrefs.getString(
                                "destination_id_$autoTripKey",
                                null
                            )

                            if (savedPlaceId != currentPlaceId) {
                                val now = System.currentTimeMillis()

                                lastDestinationChangedAt = now

                                autoVoicePrefs.edit()
                                    .putString(
                                        "destination_id_$autoTripKey",
                                        currentPlaceId
                                    )
                                    .putLong(
                                        "destination_changed_$autoTripKey",
                                        now
                                    )
                                    .apply()
                            }
                        }


                        // 추천 카드별 도슨트 이야기 표시용 상태
                        var docentSelectedPlaceId by remember(destinationTripId) {
                            mutableStateOf<String?>(null)
                        }

                        // 카드 도슨트의 늦게 도착한 응답도 차단하기 위한 요청 버전.
                        var cardDocentRequestVersion by remember(destinationTripId) {
                            mutableIntStateOf(0)
                        }

                        fun resetDocentForRecommendationChange(reason: String) {
                            val hadDocentAudioOrRequest =
                                voiceDocentStory != null || voiceDocentLoading ||
                                        docentStory != null || docentLoading

                            // 진행 중인 GPT 응답이 뒤늦게 기존 화면을 덮어쓰지 못하도록 무효화.
                            voiceDocentGeneration++
                            cardDocentRequestVersion++
                            cancelVoiceConsent(reason)
                            voiceDocentPendingPlace = null
                            voiceDocentStory = null
                            voiceDocentStoryPlaceId = null
                            voiceDocentError = null
                            voiceDocentLoading = false

                            docentSelectedPlaceId = null
                            docentStory = null
                            docentError = null
                            docentLoading = false
                            ttsStatus = "음성 대기"

                            // 음성 검색 결과는 별도 목록이므로 이전 장소의 카드까지 제거.
                            voiceSearchKeyword = ""
                            voiceSearchPlaces = emptyList()
                            voiceSearchScores = emptyMap()
                            voiceSearchMessage = ""
                            voiceCandidatesVisibleCount = 4

                            if (hadDocentAudioOrRequest) {
                                try {
                                    docentTts.stop()
                                } catch (e: Exception) {
                                    Log.w("MOA_DOCENT", "이전 이야기 재생 중지 실패", e)
                                }
                            }
                            Log.d("MOA_DOCENT_RESET", "추천 변경으로 도슨트 초기화: $reason")
                        }

                        // 목적지 자체를 변경해도 이전 장소의 이야기는 남기지 않는다.
                        LaunchedEffect(destinationTripId, place?.id) {
                            if (place?.id != null) {
                                resetDocentForRecommendationChange("목적지 변경: ${place.id}")
                            }
                        }

                        var tripVoiceDecision by remember(destinationTripId) {
                            mutableStateOf<String?>(null)
                        }

                        val voiceContext = LocalContext.current

                        var tripVoiceListening by remember(destinationTripId) {
                            mutableStateOf(false)
                        }

                        var tripVoiceStatus by remember(destinationTripId) {
                            mutableStateOf("음성 기능 준비 중")
                        }

                        var tripVoiceTranscript by remember(destinationTripId) {
                            mutableStateOf("")
                        }

                        var tripConfirmedSpeech by remember(destinationTripId) {
                            mutableStateOf("")
                        }

                        var tripPartialSpeech by remember(destinationTripId) {
                            mutableStateOf("")
                        }

                        // 음성 인식 자동 종료 안전장치: 최종 발화 후 1.5초, 최대 10초
                        var tripVoiceStartedAt by remember(destinationTripId) {
                            mutableLongStateOf(0L)
                        }
                        var tripVoiceLastFinalAt by remember(destinationTripId) {
                            mutableLongStateOf(0L)
                        }

                        // STT 리스너는 여행마다 한 번 등록된다. 최신 GPS/프로필을 참조해야 한다.
                        val latestVoiceLocation = rememberUpdatedState(currentLocation)
                        val latestVoiceProfile = rememberUpdatedState(profile)
                        val latestVoiceTarget = rememberUpdatedState(target)
                        val latestVoiceTtsReady = rememberUpdatedState(ttsReady)
                        val latestVoiceAcceptAction = rememberUpdatedState<(KakaoPlace) -> Unit>(
                            { selected -> acceptRecommendation(selected, "음성") }
                        )
                        val latestVoiceRejectAction = rememberUpdatedState<(KakaoPlace) -> Unit>(
                            { selected -> rejectRecommendation(selected, "음성") }
                        )

                        val tripVoiceManager = remember(voiceContext, destinationTripId) {
                            MoaVoiceManager(voiceContext.applicationContext)
                        }

                        LaunchedEffect(
                            tripVoiceListening,
                            tripVoiceStartedAt,
                            tripVoiceLastFinalAt,
                            tripVoiceManager
                        ) {
                            if (!tripVoiceListening || tripVoiceStartedAt <= 0L) {
                                return@LaunchedEffect
                            }

                            val sessionStart = tripVoiceStartedAt
                            val absoluteDeadline = sessionStart + 10_000L
                            val afterSpeechDeadline =
                                if (tripVoiceLastFinalAt > sessionStart) {
                                    tripVoiceLastFinalAt + 1_500L
                                } else {
                                    Long.MAX_VALUE
                                }
                            val stopAt = minOf(absoluteDeadline, afterSpeechDeadline)
                            kotlinx.coroutines.delay(
                                (stopAt - android.os.SystemClock.elapsedRealtime())
                                    .coerceAtLeast(0L)
                            )

                            if (tripVoiceListening && tripVoiceStartedAt == sessionStart) {
                                Log.d("MOA_TRIP_STT", "AUTO_STOP final=${tripVoiceLastFinalAt > sessionStart}")
                                // MoaVoiceManager.stopListening()이 onRecognitionEnded를 호출해 명령도 처리한다.
                                tripVoiceManager.stopListening()
                            }
                        }

                        DisposableEffect(tripVoiceManager) {
                            tripVoiceManager.onStatusChanged = { status ->
                                tripVoiceStatus = status
                            }

                            tripVoiceManager.onRecognized = { text, completed ->
                                val recognized = text.trim()

                                if (recognized.isNotBlank()) {
                                    if (completed) {
                                        tripVoiceLastFinalAt =
                                            android.os.SystemClock.elapsedRealtime()
                                        tripConfirmedSpeech = listOf(
                                            tripConfirmedSpeech,
                                            recognized
                                        ).filter { it.isNotBlank() }.joinToString(" ")

                                        tripPartialSpeech = ""
                                    } else {
                                        tripPartialSpeech = recognized
                                    }

                                    tripVoiceTranscript = listOf(
                                        tripConfirmedSpeech,
                                        tripPartialSpeech
                                    ).filter { it.isNotBlank() }.joinToString(" ")

                                    Log.d(
                                        "MOA_TRIP_STT",
                                        "completed=$completed, text=$tripVoiceTranscript"
                                    )
                                }
                            }


                            tripVoiceManager.onRecognitionEnded = {
                                lastManualVoiceAt =
                                    android.os.SystemClock.elapsedRealtime()
                                tripVoiceListening = false
                                tripVoiceStatus = "음성 입력 종료"

                                // 확정된 STT 문장만 명령으로 실행
                                val sentence = tripConfirmedSpeech.trim().ifBlank {
                                    tripPartialSpeech.trim()
                                }

                                val normalizedAnswer = sentence
                                    .lowercase()
                                    .replace(Regex("[\\s,.!?]"), "")
                                    .removePrefix("모아야")


                                tripVoiceDecision = null

                                val consentStillValid =
                                    voiceDocentAwaitingConsent &&
                                            voiceDocentPendingPlace != null &&
                                            voiceDocentConsentExpiresAt >
                                            android.os.SystemClock.elapsedRealtime()

                                if (voiceDocentAwaitingConsent && !consentStillValid) {
                                    cancelVoiceConsent("만료된 음성 응답")
                                }


                                // '알려줘/좋아'는 상세 설명 동의, '거기로 가자'는 목적지 수락.
                                // '괜찮아/아니야'는 설명 생략이며 취향 거절 학습을 하지 않는다.
                                val wantsStory = normalizedAnswer in setOf(
                                    "알려줘", "알려주세요", "자세히알려줘", "설명해줘",
                                    "설명해주세요", "궁금해", "궁금해요", "이야기해줘",
                                    "좋아", "좋아요", "응", "네", "그래", "그래요"
                                )
                                val wantsNavigation = normalizedAnswer in setOf(
                                    "거기로가자", "거기로가줘",
                                    "거기가자", "그곳으로가자", "저기로가자", "여기로가자",
                                    "그장소로가자", "거기로안내해줘", "길안내해줘",
                                    "그곳으로안내해줘", "목적지로설정해줘", "추천수락",
                                    "그곳으로출발", "여기로출발", "그리로가자",
                                    "좋아거기로가자", "응거기로가자", "좋아그곳으로가자"
                                )
                                val declinesStory = normalizedAnswer in setOf(
                                    "아니", "아니야", "아니요", "괜찮아", "괜찮아요",
                                    "필요없어", "필요없어요", "다음에", "됐어"
                                )
                                val rejectsPlace = normalizedAnswer in setOf(
                                    "추천거절", "그장소싫어", "그곳싫어", "이건싫어",
                                    "다른곳추천해줘", "다른데추천해줘", "다른장소추천해줘",
                                    "다음장소", "다음후보", "다른곳", "다른데", "다른장소"
                                )
                                // GPT 상세 설명이 끝난 뒤에는 '좋아/응'을 목적지 수락으로 해석.
                                val acceptsAfterStory =
                                    consentStillValid && voiceDocentStory != null &&
                                            normalizedAnswer in setOf("좋아", "좋아요", "응", "네")
                                val pendingPlace =
                                    if (consentStillValid) voiceDocentPendingPlace else null

                                if ((wantsNavigation || acceptsAfterStory) && pendingPlace != null) {
                                    tripVoiceDecision = "추천 수락: ${pendingPlace.name}"
                                    tripVoiceStatus = "목적지를 저장하고 길 안내를 요청합니다."
                                    Log.d("MOA_VOICE_ACTION", "ACCEPT: ${pendingPlace.name}")
                                    latestVoiceAcceptAction.value(pendingPlace)

                                } else if (rejectsPlace && pendingPlace != null) {
                                    tripVoiceDecision = "추천 거절: ${pendingPlace.name}"
                                    tripVoiceStatus = "이 장소를 제외하고 다른 후보를 보여드립니다."
                                    latestVoiceRejectAction.value(pendingPlace)

                                } else if (consentStillValid && wantsStory) {
                                    tripVoiceDecision = "상세 설명 요청"

                                    voiceDocentAwaitingConsent = false
                                    voiceDocentConsentExpiresAt = 0L

                                    val selectedPlace = voiceDocentPendingPlace

                                    if (selectedPlace != null && !voiceDocentLoading) {
                                        voiceDocentAwaitingConsent = false
                                        voiceDocentLoading = true
                                        voiceDocentStory = null
                                        voiceDocentError = null

                                        val requestGeneration = voiceDocentGeneration

                                        scope.launch {
                                            try {
                                                val basicFacts = buildList {
                                                    add("장소명: ${selectedPlace.name}")

                                                    if (selectedPlace.address.isNotBlank()) {
                                                        add("주소: ${selectedPlace.address}")
                                                    }

                                                    if (selectedPlace.category.isNotBlank()) {
                                                        add("업종: ${selectedPlace.category}")
                                                    }
                                                }

                                                val result = docentApi.generateStory(
                                                    placeName = selectedPlace.name,
                                                    region = selectedPlace.address.take(100),
                                                    topic = "해당 장소와 지역의 역사, 문화, 음식 이야기",
                                                    userInterests = latestVoiceTarget.value?.info?.purpose.orEmpty(),
                                                    verifiedFacts = basicFacts,
                                                    sourceName = "카카오 로컬 API 장소 기본 정보"
                                                )

                                                if (
                                                    requestGeneration == voiceDocentGeneration &&
                                                    voiceDocentPendingPlace?.id == selectedPlace.id
                                                ) {
                                                    voiceDocentStory = result.storyText
                                                    voiceDocentStoryPlaceId = selectedPlace.id
                                                    // 설명을 들은 뒤에도 30초 동안 '거기로 가자'를 받을 수 있게 한다.
                                                    // '좋아'는 이 단계에서만 목적지 수락으로 처리한다.
                                                    startVoiceConsent(selectedPlace)

                                                    if (latestVoiceTtsReady.value && result.storyText.isNotBlank()) {
                                                        docentTts.stop()
                                                        docentTts.speak(result.storyText)
                                                    }
                                                }

                                            } catch (
                                                e: kotlinx.coroutines.CancellationException
                                            ) {
                                                throw e
                                            } catch (e: Exception) {
                                                if (requestGeneration == voiceDocentGeneration) {
                                                    voiceDocentError =
                                                        e.message ?: "AI 설명 생성 실패"
                                                }
                                            } finally {
                                                if (requestGeneration == voiceDocentGeneration) {
                                                    voiceDocentLoading = false
                                                }
                                            }
                                        }
                                    }

                                } else if (declinesStory) {
                                    val hadPending = consentStillValid
                                    cancelVoiceConsent("사용자가 상세 설명 거절")
                                    tripVoiceStatus = if (hadPending) {
                                        "상세 설명을 생략했습니다."
                                    } else {
                                        "대기 중인 추천 질문이 없습니다."
                                    }
                                    tripVoiceDecision = if (hadPending) {
                                        "상세 설명 거절"
                                    } else {
                                        "응답 대기 없음"
                                    }
                                    Log.d("MOA_VOICE_ACTION", "STORY_DECLINED: $sentence")

                                } else if (wantsNavigation || wantsStory || rejectsPlace) {
                                    tripVoiceDecision = "응답 대기 중인 추천 장소가 없습니다."
                                    tripVoiceStatus = "새로운 추천을 받은 뒤 다시 말씀해 주세요."

                                } else if (sentence.isNotBlank()) {
                                    val command = MoaVoiceCommandParser.parse(sentence)
                                    tripVoiceDecision = command.description

                                    if (
                                        command.action == MoaVoiceAction.RECOMMEND &&
                                        !voiceSearchLoading
                                    ) {
                                        val location = latestVoiceLocation.value

                                        if (location == null) {
                                            voiceSearchMessage =
                                                "현재 위치를 확인할 수 없어 검색하지 못했습니다."
                                        } else {
                                            val keyword =
                                                MoaVoiceCommandParser.searchKeyword(sentence)

                                            resetDocentForRecommendationChange("새 음성 검색 시작")
                                            voiceSearchKeyword = keyword
                                            voiceSearchLoading = true
                                            voiceSearchPlaces = emptyList()
                                            voiceSearchMessage = "음성 요청으로 장소 검색 중..."

                                            scope.launch {
                                                try {
                                                    val result = kakaoRepository.searchPlaces(
                                                        query = keyword,
                                                        longitude = location.longitude,
                                                        latitude = location.latitude,
                                                        radius = 3000
                                                    )

                                                    result.fold(

                                                        onSuccess = { places ->

                                                            val matchingPlaces = places.filter { candidate ->
                                                                val category = candidate.category.lowercase()

                                                                when (keyword) {
                                                                    "맛집" -> {
                                                                        val isCafe =
                                                                            category.contains("카페") ||
                                                                                    category.contains("커피") ||
                                                                                    category.contains("디저트") ||
                                                                                    category.contains("베이커리") ||
                                                                                    category.contains("제과")

                                                                        val isRestaurant =
                                                                            category.contains("음식점") ||
                                                                                    category.contains("식당") ||
                                                                                    category.contains("한식") ||
                                                                                    category.contains("중식") ||
                                                                                    category.contains("일식") ||
                                                                                    category.contains("양식")

                                                                        isRestaurant && !isCafe
                                                                    }

                                                                    "카페" ->
                                                                        category.contains("카페") ||
                                                                                category.contains("커피")

                                                                    else -> true
                                                                }
                                                            }.distinctBy { it.id }

                                                            val scoringContext = MoaScoringContext(
                                                                profilePreferences =
                                                                    latestVoiceProfile.value?.preferences.orEmpty(),
                                                                profileAtmospheres =
                                                                    latestVoiceProfile.value?.atmospheres.orEmpty(),
                                                                tripPurpose =
                                                                    latestVoiceTarget.value?.info?.purpose.orEmpty(),
                                                                desiredExperience =
                                                                    latestVoiceTarget.value?.info?.desiredExperience.orEmpty(),
                                                                tripMood =
                                                                    latestVoiceTarget.value?.info?.mood.orEmpty(),
                                                                latitude = location.latitude,
                                                                longitude = location.longitude,
                                                                learnedPreferences =
                                                                    latestVoiceProfile.value?.id?.let { profileId ->
                                                                        tripRatingStore.adjustLearnedPreferences(
                                                                            profileId,
                                                                            moaLearner.getLearnedPreferences(profileId)
                                                                        )
                                                                    }.orEmpty(),


                                                                requestedKeyword = keyword
                                                            )

                                                            Log.d(
                                                                "MOA_SCORE_DEBUG",
                                                                """
                                                                    ===== 추천 조건 =====
                                                                    프로필 취향: ${scoringContext.profilePreferences}
                                                                    선호 분위기: ${scoringContext.profileAtmospheres}
                                                                    여행 목적: ${scoringContext.tripPurpose}
                                                                    원하는 경험: ${scoringContext.desiredExperience}
                                                                    현재 기분: ${scoringContext.tripMood}
                                                                    학습 가중치: ${scoringContext.learnedPreferences}
                                                                    =====================
                                                                    """.trimIndent()
                                                            )


                                                            val ranked = matchingPlaces
                                                                .map { candidate ->

                                                                    val scored = moaScorer.score(
                                                                        candidate,
                                                                        scoringContext
                                                                    )

                                                                    val distanceResult = FloatArray(1)

                                                                    android.location.Location.distanceBetween(
                                                                        location.latitude,
                                                                        location.longitude,
                                                                        candidate.latitude,
                                                                        candidate.longitude,
                                                                        distanceResult
                                                                    )

                                                                    Log.d(
                                                                        "MOA_SCORE_DEBUG",
                                                                        """
                                                                            장소: ${candidate.name}
                                                                            카테고리: ${candidate.category}
                                                                            직선거리: ${distanceResult[0].toInt()}m
                                                                            세부점수: ${scored.score}
                                                                            총점: ${scored.score.total}
                                                                            """.trimIndent()
                                                                    )
                                                                    Log.d(
                                                                        "MOA_SCORE_DEBUG",
                                                                        "장소=${candidate.name}, " +
                                                                                "장소좌표=${candidate.latitude},${candidate.longitude}, " +
                                                                                "현재좌표=${location.latitude},${location.longitude}"
                                                                    )

                                                                    scored
                                                                }
                                                                .sortedByDescending { it.score.total }


                                                            // 직접 요청이므로 60점 제한은 적용하지 않음
                                                            voiceSearchPlaces = ranked.map { it.place }

                                                            voiceSearchScores = ranked.associate {
                                                                it.place.id to it.score.total
                                                            }

                                                            voiceCandidatesVisibleCount = 4
                                                            voiceDocentStory = null
                                                            voiceDocentStoryPlaceId = null
                                                            voiceDocentError = null

                                                            voiceSearchMessage =
                                                                if (ranked.isEmpty()) {
                                                                    "'$keyword' 조건에 맞는 장소가 없습니다."
                                                                } else {
                                                                    "'$keyword' 조건에 맞는 장소 ${ranked.size}개"
                                                                }

                                                            val topPlace = ranked.firstOrNull()?.place

                                                            if (topPlace != null) {
                                                                startVoiceConsent(topPlace)
                                                            } else {
                                                                cancelVoiceConsent("추천 결과 없음")
                                                            }

                                                            if (topPlace != null) {
                                                                voiceDocentStory = null
                                                                voiceDocentStoryPlaceId = null
                                                                voiceDocentError = null
                                                                voiceDocentLoading = false

                                                                val recommendationMessage =
                                                                    "${topPlace.name}을 추천드려요. 관련 정보도 궁금하신가요?"

                                                                if (latestVoiceTtsReady.value) {
                                                                    try {
                                                                        docentTts.stop()
                                                                        docentTts.speak(recommendationMessage)

                                                                        Log.d(
                                                                            "MOA_VOICE",
                                                                            "간단 추천 안내: ${topPlace.name}"
                                                                        )
                                                                    } catch (e: Exception) {
                                                                        Log.e(
                                                                            "MOA_VOICE",
                                                                            "간단 추천 TTS 실패",
                                                                            e
                                                                        )
                                                                    }
                                                                }

                                                                voiceSearchMessage =
                                                                    "'$keyword' 검색 결과 ${ranked.size}개. " +
                                                                            "첫 번째 추천은 ${topPlace.name}입니다."
                                                            }

                                                        },

                                                        onFailure = { error ->
                                                            voiceSearchMessage =
                                                                "음성 검색 실패: ${error.message}"
                                                        }
                                                    )
                                                } catch (
                                                    e: kotlinx.coroutines.CancellationException
                                                ) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    voiceSearchMessage =
                                                        "음성 검색 오류: ${e.message}"
                                                } finally {
                                                    voiceSearchLoading = false
                                                }
                                            }
                                        }
                                    }
                                }
                            }


                            tripVoiceManager.initialize()

                            onDispose {
                                tripVoiceManager.stopListening()
                                tripVoiceManager.release()
                            }
                        }

                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (compactMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "✨ MOA 여행 도슨트",
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Text(
                                        if (nearbyLoading) "추천 검색 중"
                                        else if (currentLocation != null) "위치 연결됨"
                                        else "위치 대기",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Text(
                                    "🚗 ${target?.info?.destination.orEmpty()} 여행 중",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Text(
                                    "목적지: ${place?.name ?: "미설정"}",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                MoaDriverDoorCard(controller = driverDoorController)
                                MoaEnergyRangeRecommendationCard(
                                    currentLocation = currentLocation,
                                    profile = profile,
                                    kakaoRepository = kakaoRepository,
                                    moaScorer = moaScorer,
                                    moaLearner = moaLearner,
                                    naviRouting = naviRouting,
                                    onNavigate = { selected -> requestSelectedPlaceRoute(selected) }
                                )


                                HorizontalDivider()

                                Text(
                                    "🎙 MOA 음성 명령",
                                    style = MaterialTheme.typography.titleSmall
                                )

                                Button(
                                    onClick = {
                                        if (tripVoiceListening) {
                                            tripVoiceManager.stopListening()
                                            tripVoiceListening = false
                                        } else {
                                            // 기존 도슨트 음성이 입력을 방해하지 않도록 중지
                                            docentTts.stop()

                                            tripVoiceTranscript = ""
                                            tripConfirmedSpeech = ""
                                            tripPartialSpeech = ""
                                            tripVoiceDecision = null
                                            tripVoiceStartedAt =
                                                android.os.SystemClock.elapsedRealtime()
                                            tripVoiceLastFinalAt = 0L
                                            tripVoiceListening = true
                                            tripVoiceStatus = "말씀해 주세요"

                                            tripVoiceManager.startListening()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (tripVoiceListening)
                                            "⏹ 음성 인식 종료"
                                        else
                                            "🎙 모아에게 말하기"
                                    )
                                }

                                Text(
                                    text = "상태: $tripVoiceStatus",
                                    style = MaterialTheme.typography.labelSmall
                                )

                                if (tripVoiceTranscript.isNotBlank()) {
                                    Text(
                                        text = "인식한 문장: $tripVoiceTranscript",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                if (
                                    tripVoiceTranscript.isNotBlank() &&
                                    !tripVoiceListening
                                ) {
                                    val command = MoaVoiceCommandParser.parse(
                                        tripVoiceTranscript
                                    )

                                    Text(
                                        text = "MOA 판단: " +
                                                (tripVoiceDecision ?: command.description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

// ✅ 여기부터 4번 코드 추가


                                if (voiceSearchKeyword.isNotBlank()) {
                                    HorizontalDivider()

                                    Text(
                                        "🎙 음성 검색: $voiceSearchKeyword",
                                        style = MaterialTheme.typography.titleSmall
                                    )

                                    if (voiceSearchLoading) {
                                        CircularProgressIndicator()
                                        Text("장소를 검색하고 있습니다.")
                                    } else {
                                        Text(
                                            voiceSearchMessage,
                                            style = MaterialTheme.typography.bodySmall
                                        )

                                        // 점수순으로 정렬된 첫 번째 장소
                                        val topPlace = voiceSearchPlaces.firstOrNull()

                                        if (topPlace != null) {
                                            ElevatedCard(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement =
                                                        Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        "✨ MOA의 첫 번째 추천",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )

                                                    Text(
                                                        topPlace.name,
                                                        style = MaterialTheme.typography.titleMedium
                                                    )

                                                    Text(
                                                        topPlace.address,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )

                                                    Text(
                                                        "MOA 점수: " +
                                                                "%.1f".format(
                                                                    voiceSearchScores[topPlace.id] ?: 0.0
                                                                ) +
                                                                " / 100점",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )

                                                    // 음성 검색 결과도 여행 추천 카드와 동일한 수락·거절 로직 사용
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            enabled = !profile?.id.isNullOrBlank() &&
                                                                    destinationTripId != null &&
                                                                    !destinationBusy && !naviRouting &&
                                                                    !voiceSearchLoading,
                                                            onClick = {
                                                                acceptRecommendation(topPlace, "음성 검색 버튼")
                                                            }
                                                        ) {
                                                            Text("추천 수락")
                                                        }

                                                        OutlinedButton(
                                                            enabled = !destinationBusy && !naviRouting &&
                                                                    !voiceSearchLoading,
                                                            onClick = {
                                                                // 이전 장소에 대한 늦은 GPT 응답과 재생을 폐기
                                                                voiceDocentGeneration++
                                                                voiceDocentStory = null
                                                                voiceDocentStoryPlaceId = null
                                                                voiceDocentError = null
                                                                voiceDocentLoading = false
                                                                voiceDocentPendingPlace = null
                                                                try {
                                                                    docentTts.stop()
                                                                } catch (e: Exception) {
                                                                    Log.w("MOA_VOICE", "도슨트 재생 중지 실패", e)
                                                                }
                                                                rejectRecommendation(topPlace, "음성 검색 버튼")
                                                            }
                                                        ) {
                                                            Text("추천 거절")
                                                        }
                                                    }

                                                    HorizontalDivider()

                                                    Text(
                                                        "📖 AI 도슨트 이야기",
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                    if (
                                                        voiceDocentAwaitingConsent &&
                                                        voiceDocentStory == null &&
                                                        !voiceDocentLoading
                                                    ) {
                                                        Text(
                                                            "관련 정보가 궁금하면 마이크를 누르고 '알려줘'라고 말씀하세요.",
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                    }

                                                    if (voiceDocentLoading) {
                                                        LinearProgressIndicator(
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                        Text("장소 이야기를 생성하고 있습니다.")
                                                    }

                                                    // 반드시 현재 표시 중인 첫 번째 추천 장소와 일치하는 이야기만 노출.
                                                    voiceDocentStory
                                                        ?.takeIf { voiceDocentStoryPlaceId == topPlace.id }
                                                        ?.let { story ->
                                                            Text(
                                                                story,
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )

                                                            Row(
                                                                horizontalArrangement =
                                                                    Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                OutlinedButton(
                                                                    enabled = ttsReady,
                                                                    onClick = {
                                                                        docentTts.stop()
                                                                        docentTts.speak(story)
                                                                    }
                                                                ) {
                                                                    Text("🔊 다시 듣기")
                                                                }

                                                                OutlinedButton(
                                                                    enabled = ttsReady,
                                                                    onClick = {
                                                                        docentTts.stop()
                                                                    }
                                                                ) {
                                                                    Text("⏹ 중지")
                                                                }
                                                            }
                                                        }

                                                    voiceDocentError?.let { error ->
                                                        Text(
                                                            "설명 생성 실패: $error",
                                                            color = MaterialTheme.colorScheme.error,
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 1위 장소를 제외한 다른 후보들
                                        val otherCandidates = voiceSearchPlaces.drop(1)

                                        if (otherCandidates.isNotEmpty()) {
                                            HorizontalDivider()

                                            Text(
                                                "📍 다른 추천 후보 (${otherCandidates.size}곳)",
                                                style = MaterialTheme.typography.titleSmall
                                            )

                                            otherCandidates
                                                .take(voiceCandidatesVisibleCount)
                                                .forEach { candidate ->
                                                    ElevatedCard(
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(12.dp),
                                                            verticalArrangement =
                                                                Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Text(
                                                                candidate.name,
                                                                style = MaterialTheme.typography.titleSmall
                                                            )

                                                            Text(
                                                                candidate.address,
                                                                style = MaterialTheme.typography.bodySmall
                                                            )

                                                            Text(
                                                                "MOA 점수: " +
                                                                        "%.1f".format(
                                                                            voiceSearchScores[candidate.id]
                                                                                ?: 0.0
                                                                        ) +
                                                                        " / 100점",
                                                                style = MaterialTheme.typography.bodySmall
                                                            )
                                                        }
                                                    }
                                                }

                                            if (otherCandidates.size > voiceCandidatesVisibleCount) {
                                                OutlinedButton(
                                                    onClick = {
                                                        voiceCandidatesVisibleCount += 5
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("다른 후보 더 보기")
                                                }
                                            }
                                        }
                                    }
                                }


// ✅ 4번 코드 끝



                                TextButton(
                                    onClick = { showTripTools = !showTripTools }
                                ) {
                                    Text(
                                        if (showTripTools) "개발 도구 접기"
                                        else "개발 도구 펼치기"
                                    )
                                }
                            } else {
                                Text(
                                    "🚗 여행 진행 중",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text("여행 지역: ${target?.info?.destination.orEmpty()}")
                                Text(
                                    "현재 목적지: ${place?.name ?: "불러오는 중"}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(place?.address.orEmpty())
                                MoaDriverDoorCard(controller = driverDoorController)
                                MoaEnergyRangeRecommendationCard(
                                    currentLocation = currentLocation,
                                    profile = profile,
                                    kakaoRepository = kakaoRepository,
                                    moaScorer = moaScorer,
                                    moaLearner = moaLearner,
                                    naviRouting = naviRouting,
                                    onNavigate = { selected -> requestSelectedPlaceRoute(selected) }
                                )
                            }


                            if (!compactMode || showTripTools) {
                                Text(
                                    text = "내비게이션 상태: $naviStatus",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Button(
                                    enabled = !naviConnecting && !naviConnected,

                                    onClick = {
                                        naviConnecting = true
                                        naviStatus = "NaviHelper 연결 중..."

                                        moaNaviManager.initialize(
                                            onConnected = {
                                                naviConnected = true
                                                naviConnecting = false
                                                naviStatus = "NaviHelper 연결 성공"
                                            },
                                            onFailed = { message ->
                                                naviConnected = false
                                                naviConnecting = false
                                                naviStatus = "연결 실패: $message"
                                            }
                                        )
                                    }

                                ) {
                                    Text(
                                        if (naviConnecting) {
                                            "연결 중..."
                                        } else {
                                            "내비게이션 연결 테스트"
                                        }
                                    )
                                }

                                HorizontalDivider()

                                Button(
                                    enabled = naviConnected &&
                                            place != null &&
                                            !naviRouting,
                                    onClick = {
                                        val destination = place ?: return@Button

                                        naviRouting = true
                                        naviStatus = "목적지 경로 요청 중..."

                                        try {
                                            moaNaviManager.requestDestination(
                                                place = destination,
                                                onStatus = { status ->
                                                    naviStatus = status

                                                    if (
                                                        status.contains("완료") ||
                                                        status.contains("오류")
                                                    ) {
                                                        naviRouting = false
                                                    }
                                                }
                                            )
                                        } catch (e: Exception) {
                                            naviRouting = false
                                            naviStatus =
                                                "경로 요청 실패: ${e.message}"

                                            android.util.Log.e(
                                                "MOA_NAVI",
                                                "경로 요청 예외",
                                                e
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (naviRouting) {
                                            "경로 탐색 중..."
                                        } else {
                                            "🧭 현재 목적지 길 안내 요청"
                                        }
                                    )
                                }

                                Text(
                                    "📍 실시간 차량 위치",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text("상태: $locationStatus")

                                val location = currentLocation

                                if (location != null) {
                                    Text("위도: ${location.latitude}")
                                    Text("경도: ${location.longitude}")
                                    Text("고도: ${location.altitude} m")
                                    Text("진행 방향: ${location.heading}°")
                                    Text("측정 시각: ${location.timestampMillis}")
                                } else {
                                    Text("아직 수신된 위치 정보가 없습니다.")
                                }

                                if (!locationPermissionGranted) {
                                    OutlinedButton(
                                        onClick = {
                                            locationPermissionLauncher.launch(locationPermission)
                                        }
                                    ) {
                                        Text("위치 권한 요청")
                                    }
                                }

                                Text(
                                    "MOA 실시간 선제 추천과 도착 감지는 다음 단계에서 연결합니다.",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                HorizontalDivider()

                                Text(
                                    "🔎 MOA 주변 장소 탐색",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Button(
                                    enabled = currentLocation != null && !nearbyLoading,
                                    onClick = {
                                        val location = currentLocation ?: return@Button

                                        nearbyLoading = true
                                        nearbyError = null
                                        nearbyPlaces = emptyList()

                                        scope.launch {
                                            try {
                                                val result = kakaoRepository.searchPlaces(
                                                    query = "관광명소",
                                                    longitude = location.longitude,
                                                    latitude = location.latitude,
                                                    radius = 3000
                                                )

                                                result.fold(
                                                    onSuccess = { places ->
                                                        nearbyPlaces = places
                                                    },
                                                    onFailure = { error ->
                                                        nearbyError =
                                                            error.message ?: "주변 장소 검색 실패"
                                                    }
                                                )
                                            } finally {
                                                nearbyLoading = false
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        if (nearbyLoading) "검색 중..."
                                        else "현재 위치에서 주변 명소 찾기"
                                    )
                                }
                            }


                            nearbyError?.let { message ->
                                Text(
                                    text = message,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            nearbySuccess?.let { message ->
                                Text(
                                    text = message,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }


                            if (!nearbyLoading && nearbyPlaces.isEmpty() &&
                                nearbyError == null
                            ) {
                                Text("검색 결과가 아직 없습니다.")
                            }


                            val locationForScoring = currentLocation

                            if (locationForScoring != null) {


                                val activeProfile = profile

                                val scoringContext = MoaScoringContext(
                                    profilePreferences =
                                        activeProfile?.preferences.orEmpty(),
                                    profileAtmospheres =
                                        activeProfile?.atmospheres.orEmpty(),
                                    tripPurpose = target?.info?.purpose.orEmpty(),
                                    desiredExperience =
                                        target?.info?.desiredExperience.orEmpty(),
                                    tripMood = target?.info?.mood.orEmpty(),
                                    latitude = locationForScoring.latitude,
                                    longitude = locationForScoring.longitude,
                                    learnedPreferences =
                                        activeProfile?.id?.let { profileId ->
                                            tripRatingStore.adjustLearnedPreferences(
                                                profileId,
                                                moaLearner.getLearnedPreferences(profileId)
                                            )
                                        }.orEmpty(),



                                    )

                                // 상태 변경을 읽어 학습 후 Compose 재구성을 유도합니다.
                                val currentLearningVersion = learningVersion


                                val currentDestination =
                                    destinationTripId?.let { destinations[it] }


// 1. 기존 제외 조건과 MOA 점수 계산 유지
                                val rankedPlaces = nearbyPlaces
                                    .filter { candidate ->
                                        candidate.id !in rejectedPlaceIds &&
                                                candidate.id !in acceptedPlaceIds &&
                                                !isSameDestination(
                                                    candidateId = candidate.id,
                                                    candidateName = candidate.name,
                                                    candidateLatitude = candidate.latitude,
                                                    candidateLongitude = candidate.longitude,
                                                    current = currentDestination
                                                )
                                    }
                                    .map { candidate ->
                                        moaScorer.score(candidate, scoringContext)
                                    }
                                    .sortedByDescending { it.score.total }

// 추천 카드의 점수 기준은 음성 자동 안내 기준과 별개로 유지한다.
                                val automaticPlaces = rankedPlaces
                                    .filter { it.score.total >= MOA_CARD_MIN_SCORE }

                                val recommendationCandidates =
                                    if (showAllRequestedPlaces) rankedPlaces else automaticPlaces

                                // 전체화면은 3개, 분할화면은 1개씩 다음 페이지로 교체한다.
                                val pageSize = if (compactMode) 1 else 3
                                val pageCount =
                                    if (recommendationCandidates.isEmpty()) 0
                                    else (recommendationCandidates.size + pageSize - 1) / pageSize

                                val currentPage = tripRecommendationPage.coerceIn(
                                    0, (pageCount - 1).coerceAtLeast(0)
                                )

                                // 화면에 실제 보이는 후보만 자동 음성 안내 대상으로 삼는다.
                                val scoredPlaces = recommendationCandidates
                                    .drop(currentPage * pageSize)
                                    .take(pageSize)

                                val visiblePlaceIds = scoredPlaces.map { it.place.id }
                                var previousVisiblePlaceIds by remember(destinationTripId) {
                                    mutableStateOf<List<String>?>(null)
                                }
                                var lastAutoAnnouncedPlaceId by remember(destinationTripId) {
                                    mutableStateOf<String?>(null)
                                }

                                LaunchedEffect(destinationTripId, visiblePlaceIds) {
                                    val previous = previousVisiblePlaceIds
                                    if (previous != null && previous != visiblePlaceIds) {
                                        // 화면 추천 목록이 바뀌면 이전 도슨트 이야기/검색 결과/응답 대기 폐기.
                                        resetDocentForRecommendationChange("화면 추천 장소 목록 변경")
                                        if (lastAutoAnnouncedPlaceId != null) {
                                            try { docentTts.stop() } catch (e: Exception) {
                                                Log.e("MOA_VOICE", "이전 안내 중지 실패", e)
                                            }
                                        }
                                        lastAutoAnnouncedPlaceId = null
                                    }
                                    previousVisiblePlaceIds = visiblePlaceIds
                                }

                                // 전체 추천 목록에서 미안내 후보를 찾는다.
                                // 실제 발화 전에 해당 후보가 포함된 페이지를 화면에 표시한다.
                                val autoVoiceCandidate = recommendationCandidates.firstOrNull {
                                    it.score.total >= MOA_AUTO_VOICE_MIN_SCORE &&
                                            it.place.id !in autoSpokenPlaceIds
                                }

                                LaunchedEffect(
                                    destinationTripId,
                                    autoVoiceCandidate?.place?.id,
                                    visiblePlaceIds,
                                    lastDestinationChangedAt,
                                    autoLastSpokenAt,
                                    lastManualVoiceAt,
                                    ttsReady,
                                    tripVoiceListening,
                                    voiceDocentLoading,
                                    docentLoading,
                                    voiceDocentAwaitingConsent,
                                    voiceSearchLoading,
                                    naviRouting,
                                    destinationBusy
                                ) {
                                    val candidate = autoVoiceCandidate
                                    if (candidate == null) {
                                        Log.d(
                                            "MOA_AUTO_VOICE",
                                            "음성 후보 없음: 화면=$visiblePlaceIds, " +
                                                    "전체후보=${recommendationCandidates.size}, " +
                                                    "점수기준=$MOA_AUTO_VOICE_MIN_SCORE, " +
                                                    "이미안내=${autoSpokenPlaceIds.size}개"
                                        )
                                        return@LaunchedEffect
                                    }
                                    var loggedBlocked = false
                                    while (true) {
                                        // 세 가지 남은 시간을 매 반복마다 재계산한다.
                                        // 기존에는 destinationRemaining이 while 밖에 있어 무한 대기했다.
                                        val now = android.os.SystemClock.elapsedRealtime()
                                        val autoRemaining =
                                            if (autoLastSpokenAt <= 0L) 0L
                                            else (MOA_AUTO_COOLDOWN_MS -
                                                    (now - autoLastSpokenAt)).coerceAtLeast(0L)

                                        val manualRemaining =
                                            if (lastManualVoiceAt <= 0L) 0L
                                            else (MOA_MANUAL_COOLDOWN_MS -
                                                    (now - lastManualVoiceAt)).coerceAtLeast(0L)

                                        val destinationRemaining =
                                            if (lastDestinationChangedAt <= 0L) 0L
                                            else (MOA_DESTINATION_COOLDOWN_MS -
                                                    (System.currentTimeMillis() - lastDestinationChangedAt))
                                                .coerceIn(0L, MOA_DESTINATION_COOLDOWN_MS)

                                        val waitMillis = maxOf(
                                            autoRemaining,
                                            manualRemaining,
                                            destinationRemaining
                                        )
                                        if (waitMillis > 0L) {
                                            Log.d(
                                                "MOA_AUTO_VOICE",
                                                "자동 추천 대기 ${waitMillis / 1000L}초 " +
                                                        "(자동=$autoRemaining, 수동=$manualRemaining, 목적지=$destinationRemaining)"
                                            )
                                            kotlinx.coroutines.delay(waitMillis)
                                            continue
                                        }

                                        if (candidate.place.id in autoSpokenPlaceIds) {
                                            return@LaunchedEffect
                                        }

                                        if (
                                            !ttsReady || tripVoiceListening || voiceDocentLoading ||
                                            docentLoading || voiceDocentAwaitingConsent ||
                                            voiceSearchLoading || naviRouting || destinationBusy
                                        ) {
                                            if (!loggedBlocked) {
                                                Log.d(
                                                    "MOA_AUTO_VOICE",
                                                    "자동 안내 일시 차단: ttsReady=$ttsReady, " +
                                                            "STT=$tripVoiceListening, " +
                                                            "도슨트=$voiceDocentLoading/$docentLoading, " +
                                                            "답변대기=$voiceDocentAwaitingConsent, " +
                                                            "검색=$voiceSearchLoading, " +
                                                            "naviRouting=$naviRouting, destinationBusy=$destinationBusy"
                                                )
                                                loggedBlocked = true
                                            }
                                            kotlinx.coroutines.delay(1_000L)
                                            continue
                                        }
                                        loggedBlocked = false

                                        // 다른 페이지에 있는 추천이면 먼저 해당 페이지로 이동한다.
                                        // 페이지 변경 시 LaunchedEffect가 재시작되므로
                                        // 음성은 새 페이지가 표시된 후에만 재생된다.
                                        val candidateIndex = recommendationCandidates.indexOfFirst {
                                            it.place.id == candidate.place.id
                                        }
                                        if (candidateIndex < 0) return@LaunchedEffect
                                        val candidatePage = candidateIndex / pageSize
                                        if (candidatePage != currentPage) {
                                            Log.d(
                                                "MOA_AUTO_VOICE",
                                                "미안내 후보 페이지 표시: place=${candidate.place.name}, " +
                                                        "page=${candidatePage + 1}/$pageCount"
                                            )
                                            tripRecommendationPage = candidatePage
                                            return@LaunchedEffect
                                        }
                                        // 안전 조건: 실제로 보이는 후보만 발화한다.
                                        if (candidate.place.id !in visiblePlaceIds) {
                                            Log.w("MOA_AUTO_VOICE", "화면 동기화 실패, 음성 건너뜀")
                                            return@LaunchedEffect
                                        }

                                        val selectedPlace = candidate.place
                                        // 자동 음성과 화면은 동일한 scoredPlaces에서 선택한다.
                                        val message =
                                            "${selectedPlace.name}을 추천드려요. 관련 정보도 궁금하신가요?"

                                        try {
                                            // 새로운 자동 음성 장소가 정해지면 이전 도슨트 정보부터 정리.
                                            resetDocentForRecommendationChange("새 자동 추천: ${selectedPlace.id}")
                                            docentTts.speak(message)
                                            startVoiceConsent(selectedPlace)
                                            lastAutoAnnouncedPlaceId = selectedPlace.id

                                            autoSpokenPlaceIds.add(selectedPlace.id)
                                            autoLastSpokenAt =
                                                android.os.SystemClock.elapsedRealtime()
                                            autoVoicePrefs.edit()
                                                .putStringSet(
                                                    "spoken_$autoTripKey",
                                                    autoSpokenPlaceIds.toSet()
                                                )
                                                .putLong("last_$autoTripKey", autoLastSpokenAt)
                                                .apply()

                                            Log.d(
                                                "MOA_AUTO_VOICE",
                                                "화면 표시 일치: 장소=${selectedPlace.name}, " +
                                                        "id=${selectedPlace.id}, " +
                                                        "visibleIds=$visiblePlaceIds, " +
                                                        "점수=${candidate.score.total}"
                                            )
                                        } catch (e: Exception) {
                                            Log.e("MOA_AUTO_VOICE", "자동 음성 안내 실패", e)
                                        }
                                        return@LaunchedEffect
                                    }
                                }

                                // 테스트 전용: 앱 데이터와 여행 기록은 유지하고
                                // 이 여행의 자동 음성 중복 안내 기록만 초기화한다.
                                if (MOA_TEST_MODE && (!compactMode || showTripTools)) {
                                    OutlinedButton(
                                        onClick = {
                                            cancelVoiceConsent("자동 음성 테스트 기록 초기화")
                                            autoSpokenPlaceIds.clear()
                                            autoLastSpokenAt = 0L
                                            autoVoicePrefs.edit()
                                                .remove("spoken_$autoTripKey")
                                                .remove("last_$autoTripKey")
                                                .apply()
                                            Log.d("MOA_AUTO_VOICE", "테스트용 안내 기록 초기화: $autoTripKey")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("🧪 자동 음성 안내 기록 초기화 (테스트)")
                                    }
                                }

                                if (rankedPlaces.isEmpty() && nearbyPlaces.isNotEmpty()) {
                                    Text(
                                        "현재 추천할 수 있는 후보가 없습니다.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else if (
                                    !showAllRequestedPlaces &&
                                    automaticPlaces.isEmpty() &&
                                    rankedPlaces.isNotEmpty()
                                ) {
                                    Text(
                                        "현재 ${MOA_CARD_MIN_SCORE.toInt()}점 이상인 추천 장소가 없습니다.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

// 사용자가 직접 요청하면 점수 제한을 해제
                                if (rankedPlaces.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            resetDocentForRecommendationChange("점수 제한 보기 변경")
                                            lastAutoAnnouncedPlaceId = null
                                            showAllRequestedPlaces = !showAllRequestedPlaces
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            if (showAllRequestedPlaces)
                                                "자동 추천으로 돌아가기"
                                            else
                                                "🔎 점수 제한 없이 후보 보기"
                                        )
                                    }
                                }


                                scoredPlaces.forEach { scored ->

                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement =
                                                Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                scored.place.name,
                                                style = MaterialTheme.typography.titleSmall
                                            )

                                            if (lastAutoAnnouncedPlaceId == scored.place.id) {
                                                Text(
                                                    "🔊 방금 음성으로 안내한 장소",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }


// 전체 화면과 분할 화면 모두 점수 표시
                                            Text(
                                                text = "⭐ MOA 추천 점수: " +
                                                        "%.1f".format(scored.score.total) +
                                                        " / 100점",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            if (!compactMode) {
                                                Text(scored.place.address)
                                                Text(scored.place.category)
                                            }


                                            scored.reasons
                                                .take(if (compactMode) 2 else Int.MAX_VALUE)
                                                .forEach { reason ->
                                                    Text(
                                                        "• $reason",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }

                                            // 기존 추천 카드에 GPT 도슨트 연결 (TTS 연결 전 텍스트 검증)
                                            OutlinedButton(
                                                enabled = !docentLoading && !destinationBusy,
                                                onClick = {
                                                    val recommendedPlace = scored.place
                                                    cardDocentRequestVersion++
                                                    val requestVersion = cardDocentRequestVersion
                                                    docentSelectedPlaceId = recommendedPlace.id
                                                    docentStory = null
                                                    docentError = null
                                                    docentLoading = true

                                                    // Android에서는 카카오 로컬의 실제 장소 정보를 전달한다.
                                                    // FastAPI에서 TourAPI 관광정보 조회와 GPT 이야기 생성을 수행한다.
                                                    val basicFacts = buildList {
                                                        add("장소명: ${recommendedPlace.name}")
                                                        if (recommendedPlace.address.isNotBlank()) {
                                                            add("주소: ${recommendedPlace.address}")
                                                        }
                                                        if (recommendedPlace.category.isNotBlank()) {
                                                            add("카테고리: ${recommendedPlace.category}")
                                                        }
                                                    }

                                                    scope.launch {
                                                        try {

                                                            val result = docentApi.generateStory(
                                                                placeName = recommendedPlace.name,
                                                                region = recommendedPlace.address.take(100),
                                                                topic = "해당 장소와 지역의 역사, 문화, 음식 이야기",
                                                                userInterests = target?.info?.purpose.orEmpty(),
                                                                verifiedFacts = basicFacts,
                                                                sourceName = "카카오 로컬 API 장소 기본 정보"
                                                            )


                                                            if (requestVersion == cardDocentRequestVersion &&
                                                                docentSelectedPlaceId == recommendedPlace.id) {
                                                                docentStory = result.storyText

                                                                // 사용자가 직접 요청한 장소의 이야기를 재생
                                                                if (ttsReady && result.storyText.isNotBlank()) {
                                                                    try {
                                                                        docentTts.stop()
                                                                        docentTts.speak(result.storyText)

                                                                        ttsStatus =
                                                                            "${recommendedPlace.name} 이야기 재생 요청"

                                                                        Log.d(
                                                                            "MOA_TTS",
                                                                            "음성 출력 요청: ${recommendedPlace.name}"
                                                                        )
                                                                    } catch (e: Exception) {
                                                                        ttsStatus = "음성 출력 오류: ${e.message}"
                                                                        Log.e("MOA_TTS", "TTS 재생 실패", e)
                                                                    }
                                                                } else if (!ttsReady) {
                                                                    ttsStatus =
                                                                        "TTS 준비 전이어서 이야기만 표시합니다."
                                                                }
                                                            }

                                                            Log.d(
                                                                "MOA_DOCENT",
                                                                "장소=${recommendedPlace.name}, 상태=${result.status}"
                                                            )
                                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                                            throw e
                                                        } catch (e: Exception) {
                                                            if (requestVersion == cardDocentRequestVersion) {
                                                                docentError = e.message ?: "도슨트 연결 실패"
                                                            }
                                                            Log.e("MOA_DOCENT", "장소 이야기 생성 실패", e)
                                                        } finally {
                                                            if (requestVersion == cardDocentRequestVersion) {
                                                                docentLoading = false
                                                            }
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    if (docentLoading && docentSelectedPlaceId == scored.place.id)
                                                        "이야기 생성 중..."
                                                    else "✨ 이 장소 이야기"
                                                )
                                            }

                                            if (docentSelectedPlaceId == scored.place.id) {
                                                docentStory?.let { story ->
                                                    Text(
                                                        story,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )

                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        OutlinedButton(
                                                            enabled = ttsReady,
                                                            onClick = {
                                                                try {
                                                                    docentTts.stop()
                                                                    docentTts.speak(story)
                                                                    ttsStatus = "이야기 다시 듣기 요청"
                                                                } catch (e: Exception) {
                                                                    ttsStatus = "재생 실패: ${e.message}"
                                                                }
                                                            }
                                                        ) {
                                                            Text("🔊 다시 듣기")
                                                        }

                                                        OutlinedButton(
                                                            enabled = ttsReady,
                                                            onClick = {
                                                                docentTts.stop()
                                                                ttsStatus = "음성 중지 요청"
                                                            }
                                                        ) {
                                                            Text("⏹ 중지")
                                                        }
                                                    }

                                                    Text(
                                                        ttsStatus,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )

                                                }
                                                docentError?.let { error ->
                                                    Text(
                                                        error,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }

                                            val activeProfileId = profile?.id

                                            Row(
                                                horizontalArrangement =
                                                    Arrangement.spacedBy(8.dp)
                                            ) {


                                                Button(
                                                    enabled = !activeProfileId.isNullOrBlank() &&
                                                            !destinationBusy && destinationTripId != null &&
                                                            !naviRouting,
                                                    onClick = {
                                                        acceptRecommendation(scored.place, "버튼")
                                                    }
                                                ) {
                                                    Text(
                                                        when {
                                                            destinationBusy -> "앱 목적지 저장 중..."
                                                            naviRouting -> "경로 요청 중..."
                                                            else -> "추천 수락"
                                                        }
                                                    )
                                                }


// [선택적 안전장치]
// 같은 scoredPlaces.forEach 내부 '추천 거절' OutlinedButton에 다음 조건 추가:
// enabled = !activeProfileId.isNullOrBlank() && !destinationBusy,
//
// 하단 '다음 목적지 선택 / 변경' / '여행 관리로 돌아가기' 버튼도
// 경로 요청 중 화면 이동을 방지하려면 enabled = !destinationBusy 적용.

                                                OutlinedButton(
                                                    enabled = !activeProfileId.isNullOrBlank() &&
                                                            !destinationBusy && !naviRouting,
                                                    onClick = {
                                                        rejectRecommendation(scored.place, "버튼")
                                                    }
                                                ) {
                                                    Text("추천 거절")
                                                }
                                            }

                                        }
                                    }
                                }

                                MoaAutoRecommendationTimer(
                                    lastAutoSpokenAt = autoLastSpokenAt,
                                    lastDestinationChangedAt = lastDestinationChangedAt
                                )


// 🔄 다음 장소 3개로 교체
                                if (pageCount > 1) {
                                    OutlinedButton(
                                        onClick = {
                                            resetDocentForRecommendationChange("다른 장소 보기 클릭")
                                            lastAutoAnnouncedPlaceId = null
                                            tripRecommendationPage =
                                                if (currentPage + 1 >= pageCount) {
                                                    0
                                                } else {
                                                    currentPage + 1
                                                }
                                        },
                                        enabled = !destinationBusy && !nearbyLoading,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "🔄 다른 장소 보기 " +
                                                    "(${currentPage + 1}/$pageCount)"
                                        )
                                    }
                                }



                            }

                            Button(
                                enabled = !destinationBusy,
                                onClick = { currentScreen = "destination" }) { Text("다음 목적지 선택 / 변경") }
                            OutlinedButton(
                                enabled = !destinationBusy,
                                onClick = { currentScreen = "saved" }) { Text("여행 관리로 돌아가기") }
                        }
                    }


                    "trip" -> {
                        val editingTrip = editTripTarget
                        val repeatingTrip = repeatTripSource

                        TripSetupScreen(
                            initialTrip = editingTrip?.info ?: repeatingTrip?.info,

                            onSave = { trip: TripInfo ->
                                scope.launch {
                                    try {
                                        if (editingTrip == null) {
                                            tripRepository.saveTrip(trip)

                                            Log.d(
                                                "RoadTripTrip",
                                                if (repeatingTrip != null) {
                                                    "지난 여행 복사 저장 완료"
                                                } else {
                                                    "새 여행 저장 완료"
                                                }
                                            )

                                            tripError = null
                                        } else {
                                            val updated = tripRepository.updateTrip(
                                                editingTrip.id,
                                                trip
                                            )

                                            tripError = if (updated) {
                                                null
                                            } else {
                                                "여행 수정에 실패했습니다. 현재 프로필과 여행 상태를 확인해주세요."
                                            }

                                            if (updated) {
                                                Log.d(
                                                    "RoadTripTrip",
                                                    "여행 수정 완료: ${editingTrip.id}"
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                            "RoadTripTrip",
                                            "여행 저장 또는 수정 실패",
                                            e
                                        )

                                        tripError = "여행 정보를 저장하지 못했습니다."
                                    } finally {
                                        editTripTarget = null
                                        repeatTripSource = null
                                        currentScreen = "saved"
                                    }
                                }
                            },

                            onBack = {
                                editTripTarget = null
                                repeatTripSource = null
                                currentScreen = "saved"
                            }
                        )
                    }


                    "history" -> {
                        val completedTrips = allProfileTrips.filter { it.status == TripStatus.COMPLETED }
                        Column(

                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),

                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("여행 기록", style = MaterialTheme.typography.headlineLarge)
                            Text("${profile?.nickname.orEmpty()} 프로필의 완료된 여행")
                            if (completedTrips.isEmpty()) {
                                Text("아직 완료된 여행이 없습니다.")
                            } else {
                                Text(
                                    "기본 만족도는 3점(중립)입니다. 평가를 저장하면 다음 여행의 장소 추천에 반영됩니다.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                tripRatingMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

                                completedTrips.forEach { trip ->
                                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(trip.info.destination, style = MaterialTheme.typography.titleMedium)
                                            Text("${trip.info.startDate} ~ ${trip.info.endDate}")
                                            Text("동행자: ${trip.info.companions}")
                                            Text("여행 목적: ${trip.info.purpose}")

                                            // 평가하지 않은 여행은 기본 3점으로 보여주되 학습에는 반영하지 않습니다.
                                            val savedRating = remember(trip.id, tripRatingVersion) {
                                                tripRatingStore.getRating(trip.id)
                                            }
                                            MoaTripSatisfactionRatingRow(
                                                savedRating = savedRating,
                                                onSave = { stars ->
                                                    val finalPlace = destinations[trip.id]
                                                    val category = finalPlace?.takeIf { it.category != "주소" }?.let {
                                                        MoaPlaceCategory.classify(name = it.name, category = it.category)
                                                    }?.takeUnless { it == MoaPlaceCategory.OTHER }

                                                    if (tripRatingStore.saveRating(
                                                            tripId = trip.id,
                                                            profileId = trip.profileId,
                                                            rating = stars,
                                                            category = category
                                                        )) {
                                                        tripRatingVersion++
                                                        tripRatingMessage = "'${trip.info.destination}' 만족도 ${stars}점 저장 완료"
                                                    } else {
                                                        tripRatingMessage = "만족도 저장에 실패했습니다."
                                                    }
                                                }
                                            )

                                            Button(
                                                onClick = {
                                                    tripError = null

                                                    // 기존 여행을 수정하는 것이 아니라
                                                    // 새 여행을 등록하는 모드로 전환
                                                    editTripTarget = null

                                                    // 복사할 완료 여행의 체크인 정보
                                                    repeatTripSource = trip

                                                    currentScreen = "trip"
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("🔄 다시 여행하기")
                                            }

                                            TextButton(onClick = { deleteTripTarget = trip }) {
                                                Text("여행 기록 삭제", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                            Button(onClick = { currentScreen = "home" }) {
                                Text("홈으로 돌아가기")
                            }
                            TextButton(onClick = { currentScreen = "moa_voice_test" }) {
                                Text("음성 테스트 (개발용)")
                            }
                        }
                    }

                    "moa_voice_test" -> MoaVoiceTestScreen(onExit = {
                        Log.d("MOA_NAV", "VOICE_EXIT_BUTTON -> history")
                        currentScreen = "history"
                    })
                    "pleos_tts_test" -> PleosTtsTestScreen()
                    "record_test" -> AudioRecorderTestScreen()
                    "mic_test" -> MicrophoneTest()
                    "pleos_stt_test" -> PleosSttTestScreen()
                }
                deleteTripTarget?.let { target ->
                    AlertDialog(
                        onDismissRequest = { deleteTripTarget = null },
                        title = { Text("여행 삭제") },
                        text = { Text("'${target.info.destination}' 여행을 삭제할까요? 삭제하면 복구할 수 없습니다.") },
                        confirmButton = {
                            TextButton(onClick = {
                                deleteTripTarget = null
                                scope.launch {
                                    try {
                                        if (!tripRepository.deleteTrip(target.id))
                                            tripError = "여행 삭제에 실패했습니다."
                                        else {
                                            destinationRepository.delete(target.id)
                                            tripRatingStore.deleteRating(target.id)
                                            tripRatingVersion++
                                            tripError = null
                                        }
                                    } catch (e: Exception) {
                                        Log.e("RoadTripTrip", "여행 삭제 실패", e)
                                        tripError = "여행 삭제 중 오류가 발생했습니다."
                                    }
                                }
                            }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { deleteTripTarget = null }) { Text("취소") }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (::vehicleRepository.isInitialized) vehicleRepository.release()
        super.onDestroy()
    }
}

// 기존 호출부와의 호환을 위해 유지합니다.
@Composable
fun RoadTripHome(onStartClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🚗", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Text("떠나볼카", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "여정의 이야기를 들려주는\nAI 여행 도슨트",
            fontSize = 16.sp,
            color = Color(0xFFC5D5E8),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(60.dp))
        Button(
            onClick = onStartClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) { Text("여행 시작하기") }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) { Text("나의 여행 기록", color = Color.White) }
    }
}


private fun isSameDestination(
    candidateId: String,
    candidateName: String,
    candidateLatitude: Double,
    candidateLongitude: Double,
    current: TripPlace?
): Boolean {
    if (current == null) return false

    // 1. 카카오 장소 ID가 같으면 동일 장소
    if (
        candidateId.isNotBlank() &&
        current.id.isNotBlank() &&
        candidateId == current.id
    ) {
        return true
    }

    val distance = FloatArray(1)

    android.location.Location.distanceBetween(
        candidateLatitude,
        candidateLongitude,
        current.latitude,
        current.longitude,
        distance
    )

    // 2. 좌표가 사실상 같으면 동일 장소
    if (distance[0] <= 20f) return true

    // 3. 이름이 같고 가까운 위치면 동일 장소
    fun normalize(name: String): String =
        name.lowercase().filterNot { it.isWhitespace() }

    return normalize(candidateName) == normalize(current.name) &&
            distance[0] <= 100f
}


@Composable
private fun MoaAutoRecommendationTimer(
    lastAutoSpokenAt: Long,
    lastDestinationChangedAt: Long
) {
    val cooldownMs = MOA_AUTO_COOLDOWN_MS

    var currentElapsedTime by remember {
        mutableLongStateOf(
            android.os.SystemClock.elapsedRealtime()
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            currentElapsedTime =
                android.os.SystemClock.elapsedRealtime()
        }
    }

    val autoRemaining =
        if (lastAutoSpokenAt > 0L &&
            lastAutoSpokenAt <= currentElapsedTime
        ) {
            (cooldownMs -
                    (currentElapsedTime - lastAutoSpokenAt))
                .coerceAtLeast(0L)
        } else {
            0L
        }

    val destinationRemaining =
        if (lastDestinationChangedAt > 0L) {
            (MOA_DESTINATION_COOLDOWN_MS -
                    (System.currentTimeMillis() -
                            lastDestinationChangedAt))
                .coerceIn(0L, MOA_DESTINATION_COOLDOWN_MS)
        } else {
            0L
        }

    val remaining = maxOf(
        autoRemaining,
        destinationRemaining
    )

    if (remaining > 0L) {
        val totalSeconds = (remaining + 999L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L

        Text(
            text = "⏱ 다음 자동 음성 추천까지 " +
                    "%02d:%02d".format(minutes, seconds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        Text(
            text = "⏱ 대기 완료 · 조건에 맞는 새 장소 탐색 중",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
