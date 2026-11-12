package com.example.app.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

/** 완료된 여행 카드에서 평가를 작성하거나 수정합니다. */
@Composable
fun MoaTripSatisfactionRatingRow(
    savedRating: Int?,
    onSave: (Int) -> Unit
) {
    var draftRating by remember(savedRating) { mutableIntStateOf(savedRating ?: 3) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (savedRating == null) "여행 만족도: ★★★☆☆ 3점 (기본 · 평가 미작성)"
            else "여행 만족도: ${"★".repeat(savedRating)}${"☆".repeat(5-savedRating)} ${savedRating}점 (저장됨)",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            (1..5).forEach { star ->
                Box(
                    modifier = Modifier.size(40.dp).clickable { draftRating = star },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (star <= draftRating) "★" else "☆",
                        fontSize = 25.sp,
                        color = if (star <= draftRating) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Button(
            onClick = { onSave(draftRating) },
            enabled = savedRating == null || savedRating != draftRating
        ) {
            Text(if (savedRating == null) "만족도 저장" else "만족도 수정")
        }
        Text("별점을 남기지 않으면 3점 중립이며 학습에 반영되지 않습니다.",
            style = MaterialTheme.typography.labelSmall)
    }
}
