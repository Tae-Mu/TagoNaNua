package com.gatita.feature.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gatita.core.qr.QrMatrix

private const val QUIET_ZONE_MODULES = 4

/**
 * QR 격자를 그린다. 리더가 읽을 수 있도록 테마와 관계없이 흰 바탕에 검은 모듈과 4모듈 여백을 쓰고,
 * 모듈 크기는 정수 픽셀로 맞춰 이음새가 생기지 않게 한다.
 */
@Composable
fun QrCodeView(matrix: QrMatrix, contentDescription: String, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.White)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val total = matrix.size + QUIET_ZONE_MODULES * 2
        val cell = (size.minDimension / total).toInt().coerceAtLeast(1).toFloat()
        val origin = (size.minDimension - cell * total) / 2f
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(origin + (x + QUIET_ZONE_MODULES) * cell, origin + (y + QUIET_ZONE_MODULES) * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }
}
