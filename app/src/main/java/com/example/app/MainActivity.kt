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
import androidx.activity.compose.BackHandler
import com.example.app.data.UserPreferencesRepository
import com.example.app.data.UserProfile
import com.example.app.ui.home.HomeScreen
import com.example.app.ui.home.ModeSelectionScreen
import com.example.app.ui.profile.ProfileScreen
import com.example.app.ui.profile.ProfileSelectionScreen
import com.example.app.ui.theme.APPTheme
import com.example.app.ui.trip.TripInfo
import com.example.app.ui.trip.TripSetupScreen
import com.example.app.ui.trip.TripDestinationScreen
import com.example.app.ui.voice.AudioRecorderTestScreen
import com.example.app.ui.voice.MicrophoneTest
import com.example.app.ui.voice.MoaVoiceTestScreen
import com.example.app.ui.voice.PleosSttTestScreen
import com.example.app.ui.voice.PleosTtsTestScreen
import com.example.app.vehicle.VehicleRepository
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
import androidx.compose.ui.platform.LocalContext

import com.example.app.voice.MoaVoiceCommandParser
import com.example.app.voice.MoaVoiceAction

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
            val autoSpokenPlaceIds = remember(destinationTripId) {
                mutableStateListOf<String>()
            }

            var autoLastSpokenAt by remember(destinationTripId) {
                mutableLongStateOf(0L)
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


            var voiceDocentPendingPlace by remember(destinationTripId) {
                mutableStateOf<KakaoPlace?>(null)
            }

            var voiceDocentAwaitingConsent by remember(destinationTripId) {
                mutableStateOf(false)
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
                if (currentScreen == "trip_active" &&
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
                if (currentScreen == "trip_active" &&
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
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("일상모드", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(12.dp))
                            Text("일상모드의 MOA 추천 기능은 다음 단계에서 연결합니다.")
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { currentScreen = "mode_select" }) {
                                Text("모드 선택으로 돌아가기")
                            }
                        }
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

                    val target = allProfileTrips.firstOrNull { it.id == destinationTripId }
                    val place = destinationTripId?.let { destinations[it] }

                    // 추천 카드별 도슨트 이야기 표시용 상태
                    var docentSelectedPlaceId by remember(destinationTripId) {
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

                        val tripVoiceManager = remember(voiceContext, destinationTripId) {
                            MoaVoiceManager(voiceContext.applicationContext)
                        }

                        DisposableEffect(tripVoiceManager) {
                            tripVoiceManager.onStatusChanged = { status ->
                                tripVoiceStatus = status
                            }

                            tripVoiceManager.onRecognized = { text, completed ->
                                val recognized = text.trim()

                                if (recognized.isNotBlank()) {
                                    if (completed) {
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
                                val sentence = tripConfirmedSpeech.trim()

                                val normalizedAnswer = sentence
                                    .lowercase()
                                    .replace(Regex("[\\s,.!?]"), "")
                                    .removePrefix("모아야")

                                val agreed = normalizedAnswer in setOf(
                                    "알려줘",
                                    "알려주세요",
                                    "자세히알려줘",
                                    "설명해줘",
                                    "설명해주세요",
                                    "네",
                                    "응",
                                    "좋아",
                                    "궁금해",
                                    "궁금해요"
                                )

                                val declined = normalizedAnswer in setOf(
                                    "아니",
                                    "아니요",
                                    "괜찮아",
                                    "괜찮아요",
                                    "필요없어",
                                    "다음에"
                                )

                                if (voiceDocentAwaitingConsent && agreed) {

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
                                                    userInterests = target?.info?.purpose.orEmpty(),
                                                    verifiedFacts = basicFacts,
                                                    sourceName = "카카오 로컬 API 장소 기본 정보"
                                                )

                                                if (
                                                    requestGeneration == voiceDocentGeneration &&
                                                    voiceDocentPendingPlace?.id == selectedPlace.id
                                                ) {
                                                    voiceDocentStory = result.storyText

                                                    if (ttsReady && result.storyText.isNotBlank()) {
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

                                } else if (voiceDocentAwaitingConsent && declined) {

                                    voiceDocentAwaitingConsent = false
                                    voiceDocentError = null
                                    tripVoiceStatus = "상세 설명을 생략했습니다."

                                } else if (sentence.isNotBlank()) {
                                    val command = MoaVoiceCommandParser.parse(sentence)

                                    if (
                                        command.action == MoaVoiceAction.RECOMMEND &&
                                        !voiceSearchLoading
                                    ) {
                                        val location = currentLocation

                                        if (location == null) {
                                            voiceSearchMessage =
                                                "현재 위치를 확인할 수 없어 검색하지 못했습니다."
                                        } else {
                                            val keyword =
                                                MoaVoiceCommandParser.searchKeyword(sentence)

                                            voiceDocentGeneration++
                                            voiceDocentPendingPlace = null
                                            voiceDocentAwaitingConsent = false
                                            voiceDocentStory = null
                                            voiceDocentError = null
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
                                                                    profile?.preferences.orEmpty(),
                                                                profileAtmospheres =
                                                                    profile?.atmospheres.orEmpty(),
                                                                tripPurpose =
                                                                    target?.info?.purpose.orEmpty(),
                                                                desiredExperience =
                                                                    target?.info?.desiredExperience.orEmpty(),
                                                                tripMood =
                                                                    target?.info?.mood.orEmpty(),
                                                                latitude = location.latitude,
                                                                longitude = location.longitude,
                                                                learnedPreferences =
                                                                    profile?.id?.let { profileId ->
                                                                        moaLearner.getLearnedPreferences(profileId)
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
                                                            voiceDocentError = null

                                                            voiceSearchMessage =
                                                                if (ranked.isEmpty()) {
                                                                    "'$keyword' 조건에 맞는 장소가 없습니다."
                                                                } else {
                                                                    "'$keyword' 조건에 맞는 장소 ${ranked.size}개"
                                                                }

                                                            val topPlace = ranked.firstOrNull()?.place
                                                            voiceDocentPendingPlace = topPlace
                                                            voiceDocentAwaitingConsent = topPlace != null

                                                            if (topPlace != null) {
                                                                voiceDocentStory = null
                                                                voiceDocentError = null
                                                                voiceDocentLoading = false

                                                                val recommendationMessage =
                                                                    "${topPlace.name}을 추천드려요. 관련 정보도 궁금하신가요?"

                                                                if (ttsReady) {
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
                                    text = "MOA 판단: ${command.description}",
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

                                                voiceDocentStory?.let { story ->
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
                                        moaLearner.getLearnedPreferences(profileId)
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

// 2. 자동 추천: 60점 이상
                            val automaticPlaces = rankedPlaces
                                .filter { it.score.total >= 40.0 }

                            val autoVoiceCandidate = rankedPlaces.firstOrNull {
                                it.score.total >= 40.0 &&
                                        it.place.id !in autoSpokenPlaceIds
                            }
/*
                            val automaticPlaces = rankedPlaces
                                .filter { it.score.total >= 60.0 }

                            val autoVoiceCandidate = rankedPlaces.firstOrNull {
                                it.score.total >= 85.0 &&
                                        it.place.id !in autoSpokenPlaceIds
                            } */

                            LaunchedEffect(
                                destinationTripId,
                                nearbyPlaces,
                                currentLocation,
                                learningVersion,
                                ttsReady,
                                tripVoiceListening,
                                voiceDocentLoading,
                                voiceDocentAwaitingConsent,
                                naviRouting,
                                destinationBusy,
                                lastManualVoiceAt
                            ) {
                                val candidate = autoVoiceCandidate
                                    ?: return@LaunchedEffect

                                if (
                                    !ttsReady ||
                                    tripVoiceListening ||
                                    voiceDocentLoading ||
                                    docentLoading ||
                                    voiceDocentAwaitingConsent ||
                                    voiceSearchLoading ||
                                    naviRouting ||
                                    destinationBusy
                                ) {
                                    return@LaunchedEffect
                                }

                                val now = android.os.SystemClock.elapsedRealtime()

                                val recommendationCooldown = 15L * 60L * 1000L
                                val manualVoiceCooldown = 2L * 60L * 1000L

                                // 마지막 자동 안내 이후 최소 15분
                                if (
                                    autoLastSpokenAt > 0L &&
                                    now - autoLastSpokenAt < recommendationCooldown
                                ) {
                                    return@LaunchedEffect
                                }

                                // 사용자가 직접 음성 명령을 내린 직후에는 방해하지 않음
                                if (
                                    lastManualVoiceAt > 0L &&
                                    now - lastManualVoiceAt < manualVoiceCooldown
                                ) {
                                    return@LaunchedEffect
                                }

                                val message =
                                    "근처에 ${candidate.place.name}이 있어요. " +
                                            "취향에 잘 맞는 장소라 추천드려요. " +
                                            "관련 정보도 궁금하신가요?"

                                try {
                                    docentTts.speak(message)

                                    autoSpokenPlaceIds.add(candidate.place.id)
                                    autoLastSpokenAt = now

                                    Log.d(
                                        "MOA_AUTO_VOICE",
                                        "자동 안내: ${candidate.place.name}, " +
                                                "점수=${candidate.score.total}"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                        "MOA_AUTO_VOICE",
                                        "자동 음성 추천 실패",
                                        e
                                    )
                                }
                            }


// 3. 사용자가 요청한 경우에는 점수 제한 해제
                            val scoredPlaces = if (showAllRequestedPlaces) {
                                rankedPlaces.take(3)
                            } else {
                                automaticPlaces.take(if (compactMode) 1 else 3)
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
                                    "현재 60점 이상인 추천 장소가 없습니다.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

// 사용자가 직접 요청하면 점수 제한을 해제
                            if (rankedPlaces.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
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


                                        Text(
                                            scored.place.name,
                                            style = MaterialTheme.typography.titleSmall
                                        )

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


                                                        if (docentSelectedPlaceId == recommendedPlace.id) {
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
                                                        docentError = e.message ?: "도슨트 연결 실패"
                                                        Log.e("MOA_DOCENT", "장소 이야기 생성 실패", e)
                                                    } finally {
                                                        docentLoading = false
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
                                                        !destinationBusy &&
                                                        destinationTripId != null &&
                                                        !naviRouting,
                                                onClick = {
                                                    val profileId = activeProfileId ?: return@Button
                                                    val tripId = destinationTripId ?: return@Button
                                                    val selectedPlace = TripPlace(
                                                        id = scored.place.id,
                                                        name = scored.place.name,
                                                        address = scored.place.address,
                                                        category = scored.place.category,
                                                        latitude = scored.place.latitude,
                                                        longitude = scored.place.longitude
                                                    )
                                                    if (
                                                        isSameDestination(
                                                            candidateId = selectedPlace.id,
                                                            candidateName = selectedPlace.name,
                                                            candidateLatitude = selectedPlace.latitude,
                                                            candidateLongitude = selectedPlace.longitude,
                                                            current = destinations[tripId]
                                                        )
                                                    ) {
                                                        nearbyError = "이미 설정된 목적지입니다. 다른 장소를 추천합니다."
                                                        return@Button
                                                    }

                                                    destinationBusy = true
                                                    naviRouting = true
                                                    nearbyError = null
                                                    nearbySuccess = null
                                                    naviStatus = "추천 목적지 경로 탐색 준비 중..."

                                                    moaNaviManager.requestRecommendedDestination(
                                                        place = selectedPlace,
                                                        onStatus = { status ->
                                                            naviStatus = status
                                                        },
                                                        onCompleted = {
                                                            // onRoutingCompleted: 경로 탐색 완료. 내비에 실제 반영됐는지 시연에서 확인.
                                                            naviConnected = true
                                                            naviRouting = false
                                                            scope.launch {
                                                                try {
                                                                    if (destinationTripId != tripId) {
                                                                        nearbyError = "진행 여행이 변경되어 앱 목적지를 저장하지 않았습니다. 내비 목적지를 확인하세요."
                                                                        return@launch
                                                                    }

                                                                    // 1. 내비 탐색 완료 확인 후 앱 목적지 저장
                                                                    destinationRepository.save(tripId, selectedPlace)
                                                                    if (scored.place.id !in acceptedPlaceIds) {
                                                                        acceptedPlaceIds.add(scored.place.id)
                                                                    }
                                                                    nearbySuccess = "'${selectedPlace.name}' 경로 탐색 완료. 앱 목적지를 저장했습니다."
                                                                    nearbyError = null
                                                                    Log.d("MOA_ACCEPT", "내비 탐색 완료 후 목적지 저장: ${selectedPlace.name}")

                                                                    // 2. 기존 선호 학습은 앱 저장 성공 후 실행
                                                                    try {
                                                                        val category = MoaPlaceCategory.classify(scored.place)
                                                                        val updated = moaLearner.recordFeedback(
                                                                            profileId = profileId,
                                                                            category = category,
                                                                            feedback = MoaFeedback.ACCEPTED
                                                                        )
                                                                        learningVersion++
                                                                        Log.d(
                                                                            "MOA_LEARNING",
                                                                            "추천 수락 학습 완료: 장소=${selectedPlace.name}, 점수=${updated.score}"
                                                                        )
                                                                    } catch (e: Exception) {
                                                                        // 경로 탐색/목적지 저장 성공과 취향 학습 오류를 구분
                                                                        Log.e("MOA_LEARNING", "추천 수락 학습 실패", e)
                                                                    }
                                                                } catch (e: kotlinx.coroutines.CancellationException) {
                                                                    throw e
                                                                } catch (e: Exception) {
                                                                    // 외부 NaviHelper와 앱 DataStore는 하나의 원자적 트랜잭션이 아님
                                                                    Log.e("MOA_ACCEPT", "내비 탐색 후 앱 목적지 저장 실패", e)
                                                                    nearbyError = "경로 탐색은 완료됐지만 앱 목적지 저장에 실패했습니다. 다시 확인하세요."
                                                                } finally {
                                                                    destinationBusy = false
                                                                }
                                                            }
                                                        },
                                                        onFailed = { message ->
                                                            naviRouting = false
                                                            destinationBusy = false
                                                            naviStatus = message
                                                            nearbyError = "경로 탐색 실패: $message. 기존 앱 목적지는 유지됩니다."
                                                            Log.e("MOA_ACCEPT", "추천 경로 실패: $message")
                                                        }
                                                    )
                                                }
                                            ) {
                                                Text(if (destinationBusy) "경로 변경 중..." else "추천 수락")
                                            }

// [선택적 안전장치]
// 같은 scoredPlaces.forEach 내부 '추천 거절' OutlinedButton에 다음 조건 추가:
// enabled = !activeProfileId.isNullOrBlank() && !destinationBusy,
//
// 하단 '다음 목적지 선택 / 변경' / '여행 관리로 돌아가기' 버튼도
// 경로 요청 중 화면 이동을 방지하려면 enabled = !destinationBusy 적용.

                                            OutlinedButton(
                                                enabled = !activeProfileId.isNullOrBlank() && !destinationBusy,
                                                onClick = {
                                                    val id = activeProfileId
                                                        ?: return@OutlinedButton

                                                    val category = MoaPlaceCategory.classify(scored.place)

                                                    moaLearner.recordFeedback(
                                                        profileId = id,
                                                        category = category,
                                                        feedback = MoaFeedback.REJECTED
                                                    )

                                                    if (scored.place.id !in rejectedPlaceIds) {
                                                        rejectedPlaceIds.add(scored.place.id)
                                                    }


                                                    learningVersion++
                                                }
                                            ) {
                                                Text("추천 거절")
                                            }
                                        }

                                    }
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