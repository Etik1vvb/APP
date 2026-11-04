
package com.example.app.data

/*
 * ============================================================
 * 파일명: UserPreferencesRepository.kt
 * 프로젝트: RoadTrip 4D (떠나볼카)
 *
 * [이 파일의 역할]
 * 사용자가 최초 가입 시 입력한 프로필 정보를
 * 스마트폰 내부 저장소에 저장하고 관리한다.
 *
 * 저장할 정보:
 * 1. 닉네임
 * 2. 연령대 (선택)
 * 3. 성별 (선택)
 * 4. 선호하는 여행 스타일 (복수 선택)
 *
 * [사용 기술]
 * Jetpack DataStore Preferences
 *
 * DataStore는 앱을 종료하거나 스마트폰을 재부팅해도
 * 데이터를 유지할 수 있는 Android 저장 기술이다.
 *
 * [다른 파일과의 관계]
 *
 * ProfileScreen.kt
 *       ↓ 사용자가 프로필 입력
 * MainActivity.kt (현재 연결 예정)
 *       ↓ 저장 요청
 * UserPreferencesRepository.kt
 *       ↓ 데이터 저장
 * Android DataStore
 *
 * ============================================================
 */

// Android의 앱 실행 환경에 접근하기 위한 클래스
import android.content.Context

// DataStore에서 문자열과 문자열 집합을 저장하고
// 수정하기 위한 기능들을 가져온다.
import androidx.datastore.preferences.core.*

// Context에 DataStore를 연결하는 확장 기능
import androidx.datastore.preferences.preferencesDataStore

// 비동기 데이터 흐름을 표현하는 Flow
import kotlinx.coroutines.flow.Flow

// Flow의 데이터를 다른 형태로 변환하는 함수
import kotlinx.coroutines.flow.map


/*
 * ============================================================
 * 1. DataStore 생성
 * ============================================================
 *
 * Context에 userDataStore라는 확장 속성을 추가한다.
 *
 * Context는 Android 앱의 실행 환경을 나타내며,
 * 앱 내부 저장소에 접근할 때 필요하다.
 *
 * preferencesDataStore()는 DataStore를 생성하는
 * 편리한 방법을 제공한다.
 *
 * name = "user_preferences"
 * → 저장소를 구분하는 이름이다.
 *
 * 이 선언을 파일 최상위에 두어 같은 이름의
 * DataStore를 여러 번 생성하는 문제를 방지한다.
 */

private val Context.userDataStore by preferencesDataStore(
    name = "user_preferences"
)


/*
 * ============================================================
 * 2. 사용자 프로필 데이터 모델
 * ============================================================
 *
 * data class는 여러 데이터를 하나의 객체로 묶는다.
 *
 * 예를 들어 다음과 같은 사용자 정보를 표현한다.
 *
 * nickname    = "여행자"
 * ageGroup    = "20대"
 * gender      = null
 * preferences = {"바다", "맛집"}
 *
 * String?:
 * → 문자열 또는 null을 저장할 수 있다는 의미
 *
 * Set<String>:
 * → 중복되지 않는 문자열들의 집합
 *
 * 기본값을 지정했으므로 모든 값을 직접 전달하지
 * 않아도 UserProfile 객체를 생성할 수 있다.
 */

data class UserProfile(

    // 사용자가 앱에서 사용할 닉네임
    val nickname: String = "",

    // 연령대: 입력하지 않으면 null
    val ageGroup: String? = null,

    // 성별: 입력하지 않으면 null
    val gender: String? = null,

    // 선호하는 여행 스타일 목록
    val preferences: Set<String> = emptySet()
)


/*
 * ============================================================
 * 3. Repository 클래스
 * ============================================================
 *
 * Repository는 데이터를 저장하고 불러오는
 * 기능을 한곳에 모아 관리하는 클래스다.
 *
 * UI 화면이 DataStore를 직접 조작하지 않고,
 * 이 Repository를 통해 데이터를 처리하도록 한다.
 *
 * 이렇게 분리하면 나중에 UI 디자인을 변경해도
 * 데이터 저장 코드를 대부분 그대로 사용할 수 있다.
 *
 * context:
 * → Android 저장소에 접근하기 위해 전달받는다.
 */

class UserPreferencesRepository(
    private val context: Context
) {

    /*
     * --------------------------------------------------------
     * 3-1. 데이터 저장에 사용할 Key 정의
     * --------------------------------------------------------
     *
     * DataStore Preferences는 Key-Value 방식으로
     * 데이터를 저장한다.
     *
     * 예:
     * "nickname" → "여행자"
     *
     * Key는 저장할 데이터의 이름이고,
     * Value는 실제 저장되는 값이다.
     *
     * companion object:
     * → 클래스에 공통으로 속하는 상수 등을
     *   정의할 때 사용하는 Kotlin 문법이다.
     */

    companion object {

        // 닉네임을 저장할 문자열 Key
        private val NICKNAME =
            stringPreferencesKey("nickname")

        // 연령대를 저장할 문자열 Key
        private val AGE_GROUP =
            stringPreferencesKey("age_group")

        // 성별을 저장할 문자열 Key
        private val GENDER =
            stringPreferencesKey("gender")

        // 여행 취향은 여러 개이므로 문자열 집합 사용
        private val PREFERENCES =
            stringSetPreferencesKey("travel_preferences")
    }


    /*
     * ========================================================
     * 4. 저장된 사용자 프로필 불러오기
     * ========================================================
     *
     * userProfile은 Flow<UserProfile> 타입이다.
     *
     * Flow는 저장된 데이터가 변경될 때
     * 새로운 값을 전달할 수 있는 데이터 흐름이다.
     *
     * 예:
     * 사용자가 여행 취향을 변경한다.
     *       ↓
     * DataStore의 값이 변경된다.
     *       ↓
     * Flow가 변경된 프로필을 전달한다.
     *       ↓
     * UI가 새로운 정보를 표시할 수 있다.
     *
     * .map { prefs -> ... }
     *
     * DataStore에서 읽은 Key-Value 데이터를
     * UserProfile 객체로 변환하는 부분이다.
     */

    val userProfile: Flow<UserProfile> =
        context.userDataStore.data.map { prefs ->

            UserProfile(

                // 닉네임이 없으면 빈 문자열 사용
                nickname = prefs[NICKNAME] ?: "",

                // 저장되지 않았다면 null
                ageGroup = prefs[AGE_GROUP],

                // 저장되지 않았다면 null
                gender = prefs[GENDER],

                // 여행 취향이 없으면 빈 집합 사용
                preferences =
                    prefs[PREFERENCES] ?: emptySet()
            )
        }


    /*
     * ========================================================
     * 5. 사용자 프로필 저장
     * ========================================================
     *
     * suspend:
     * → 코루틴에서 호출할 수 있는 함수라는 의미다.
     *
     * DataStore의 저장 작업은 시간이 걸릴 수 있으므로
     * 일반적인 UI 실행 흐름을 막지 않도록 처리한다.
     *
     * profile:
     * → 저장할 UserProfile 객체
     *
     * 예:
     *
     * saveProfile(
     *     UserProfile(
     *         nickname = "여행자",
     *         ageGroup = "20대",
     *         gender = null,
     *         preferences = setOf("바다", "맛집")
     *     )
     * )
     *
     * edit { prefs -> ... }
     * → DataStore 데이터를 안전하게 수정하는 작업
     */

    suspend fun saveProfile(profile: UserProfile) {

        context.userDataStore.edit { prefs ->

            // 사용자가 입력한 닉네임 저장
            prefs[NICKNAME] = profile.nickname

            // 선택한 여행 스타일 전체 저장
            prefs[PREFERENCES] = profile.preferences

            /*
             * 연령대는 선택사항이다.
             *
             * 값이 있으면 저장하고,
             * null이면 기존에 저장된 값을 삭제한다.
             *
             * 이렇게 해야 사용자가 프로필 수정 화면에서
             * 연령대 입력을 취소했을 때 이전 값이
             * 계속 남아 있는 문제를 방지할 수 있다.
             */

            if (profile.ageGroup != null) {
                prefs[AGE_GROUP] = profile.ageGroup
            } else {
                prefs.remove(AGE_GROUP)
            }

            /*
             * 성별 역시 선택사항이다.
             *
             * 값이 있으면 저장하고,
             * null이면 저장된 성별 정보를 삭제한다.
             */

            if (profile.gender != null) {
                prefs[GENDER] = profile.gender
            } else {
                prefs.remove(GENDER)
            }
        }
    }


    /*
     * ========================================================
     * 6. 사용자 프로필 삭제
     * ========================================================
     *
     * 사용자가 프로필 삭제를 요청했을 때 호출한다.
     *
     * remove()는 해당 Key에 저장된 값을 제거한다.
     *
     * 이 함수는 프로필 정보만 삭제한다.
     * 향후 추가될 여행 기록이나 피드백 데이터는
     * 별도의 저장소에서 관리될 수 있으므로,
     * 전체 사용자 데이터 삭제 기능은
     * 나중에 통합해서 구현해야 한다.
     */

    suspend fun deleteProfile() {

        context.userDataStore.edit { prefs ->

            // 닉네임 삭제
            prefs.remove(NICKNAME)

            // 연령대 삭제
            prefs.remove(AGE_GROUP)

            // 성별 삭제
            prefs.remove(GENDER)

            // 여행 취향 삭제
            prefs.remove(PREFERENCES)
        }
    }
}
