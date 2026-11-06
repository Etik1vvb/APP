package com.example.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.data.TripStatus
import com.example.app.ui.theme.*

@Composable
fun HomeScreen(
    nickname: String?,
    destination: String?,
    tripStatus: TripStatus?,
    onPrimaryClick: () -> Unit,
    onProfileClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val primaryLabel = when (tripStatus) {
        TripStatus.PLANNED -> "여행 확인하기"
        TripStatus.ACTIVE -> "진행 중인 여행 보기"
        TripStatus.COMPLETED -> "새 여행 만들기"
        null -> "새 여행 만들기"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "RoadTrip",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = RoadTripText
            )

            TextButton(onClick = onProfileClick) {
                Text(
                    text = "내 프로필  ›",
                    fontSize = 19.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFFE5DEFF),
                            Color(0xFFDDF2FF),
                            Color(0xFFDDF9F1)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "MOA · AI 여행 동반자",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = RoadTripPrimary
            )

            Text(
                text = if (nickname.isNullOrBlank()) {
                    "오늘은 어디로 떠날까요?"
                } else {
                    "${nickname}님, 어디로 떠날까요?"
                },
                fontSize = 32.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                color = RoadTripText
            )

            Text(
                text = "모아가 당신의 여행을 함께할게요.",
                fontSize = 21.sp,
                color = RoadTripTextSecondary
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = RoadTripSurface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "나의 여행",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoadTripText
                )

                val tripDescription = when (tripStatus) {
                    TripStatus.PLANNED ->
                        "${destination.orEmpty()} 여행이 예정되어 있어요."
                    TripStatus.ACTIVE ->
                        "${destination.orEmpty()} 여행이 진행 중이에요."
                    TripStatus.COMPLETED ->
                        "지난 여행을 완료했어요."
                    null ->
                        "아직 등록된 여행이 없어요."
                }

                Text(
                    text = tripDescription,
                    fontSize = 20.sp,
                    color = RoadTripTextSecondary
                )

                Button(
                    onClick = onPrimaryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = primaryLabel,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            HomeShortcut(
                title = "내 프로필",
                subtitle = "여행 취향 관리",
                background = Color(0xFFEAE4FF),
                onClick = onProfileClick,
                modifier = Modifier.weight(1f)
            )

            HomeShortcut(
                title = "여행 기록",
                subtitle = "지난 여행 살펴보기",
                background = Color(0xFFDDF8F1),
                onClick = onHistoryClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HomeShortcut(
    title: String,
    subtitle: String,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 128.dp),
        color = background,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = RoadTripText
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = subtitle,
                fontSize = 17.sp,
                color = RoadTripTextSecondary
            )
        }
    }
}
