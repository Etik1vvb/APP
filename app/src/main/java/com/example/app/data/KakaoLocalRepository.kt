
package com.example.app.data

import com.example.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class KakaoPlace(
    val id: String,
    val name: String,
    val address: String,
    val roadAddress: String,
    val category: String,
    val longitude: Double,
    val latitude: Double,
    val placeUrl: String
)

class KakaoLocalRepository {


    suspend fun searchPlaces(
        query: String,
        page: Int = 1,
        size: Int = 15,
        longitude: Double? = null,
        latitude: Double? = null,
        radius: Int = 3000
    ): Result<List<KakaoPlace>> = withContext(Dispatchers.IO) {

    runCatching {
            require(query.isNotBlank()) {
                "검색어를 입력해 주세요."
            }

            val key = BuildConfig.KAKAO_REST_API_KEY
            check(key.isNotBlank()) {
                "카카오 REST API 키가 설정되지 않았습니다."
            }

            val encodedQuery = URLEncoder.encode(
                query.trim(),
                "UTF-8"
            )


        val locationParams =
            if (longitude != null && latitude != null) {
                require(longitude in -180.0..180.0)
                require(latitude in -90.0..90.0)

                "&x=$longitude" +
                        "&y=$latitude" +
                        "&radius=${radius.coerceIn(1, 20000)}" +
                        "&sort=distance"
            } else {
                ""
            }

        val url = URL(
            "https://dapi.kakao.com/v2/local/search/keyword.json" +
                    "?query=$encodedQuery" +
                    "&page=${page.coerceIn(1, 45)}" +
                    "&size=${size.coerceIn(1, 15)}" +
                    locationParams
        )


        val connection = (
                    url.openConnection() as HttpURLConnection
                    )

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty(
                    "Authorization",
                    "KakaoAK $key"
                )

                val status = connection.responseCode

                if (status !in 200..299) {
                    val errorBody = connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()

                    throw IllegalStateException(
                        "카카오 검색 실패 ($status): $errorBody"
                    )
                }

                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val documents = JSONObject(response)
                    .getJSONArray("documents")

                buildList {
                    for (i in 0 until documents.length()) {
                        val item = documents.getJSONObject(i)

                        add(
                            KakaoPlace(
                                id = item.optString("id"),
                                name = item.optString("place_name"),
                                address = item.optString("address_name"),
                                roadAddress = item.optString(
                                    "road_address_name"
                                ),
                                category = item.optString(
                                    "category_name"
                                ),
                                longitude = item.optString("x")
                                    .toDoubleOrNull() ?: 0.0,
                                latitude = item.optString("y")
                                    .toDoubleOrNull() ?: 0.0,
                                placeUrl = item.optString("place_url")
                            )
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun searchAddress(
        address: String
    ): Result<List<KakaoPlace>> = withContext(Dispatchers.IO) {
        runCatching {
            require(address.isNotBlank()) {
                "주소를 입력해 주세요."
            }

            val key = BuildConfig.KAKAO_REST_API_KEY
            check(key.isNotBlank()) {
                "카카오 REST API 키가 없습니다."
            }

            val encoded = URLEncoder.encode(
                address.trim(),
                "UTF-8"
            )

            val url = URL(
                "https://dapi.kakao.com/v2/local/search/address.json" +
                        "?query=$encoded"
            )

            val connection =
                url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty(
                    "Authorization",
                    "KakaoAK $key"
                )

                val status = connection.responseCode

                if (status !in 200..299) {
                    val errorBody = connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()

                    error(
                        "주소 검색 실패 ($status): $errorBody"
                    )
                }

                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val documents = JSONObject(response)
                    .getJSONArray("documents")

                buildList {
                    for (i in 0 until documents.length()) {
                        val item = documents.getJSONObject(i)

                        val road = item.optJSONObject("road_address")
                        val parcel = item.optJSONObject("address")

                        val roadName =
                            road?.optString("address_name").orEmpty()

                        val parcelName =
                            parcel?.optString("address_name").orEmpty()

                        val displayAddress = roadName.ifBlank {
                            parcelName
                        }

                        val latitude = item.optString("y")
                            .toDoubleOrNull() ?: continue

                        val longitude = item.optString("x")
                            .toDoubleOrNull() ?: continue

                        add(
                            KakaoPlace(
                                id = "address_${latitude}_${longitude}",
                                name = displayAddress,
                                address = parcelName,
                                roadAddress = roadName,
                                category = "주소",
                                longitude = longitude,
                                latitude = latitude,
                                placeUrl = ""
                            )
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

}
