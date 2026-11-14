package com.gatita.core.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrEncoderTest {
    /** 화면과 같은 규칙(여백 4모듈, 흰 바탕·검은 모듈)으로 격자를 픽셀로 펼쳐 ZXing 리더로 다시 읽는다. */
    private fun decode(matrix: QrMatrix): String {
        val scale = 4
        val quiet = 4
        val side = (matrix.size + quiet * 2) * scale
        val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) {
                        pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0xFF000000.toInt()
                    }
                }
            }
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap).text
    }

    @Test
    fun `korean settlement text survives an encode and decode round trip`() {
        val text = "[같이타 정산 요약]\n총 유류비: 3,685원\n분담: 1번 922원 / 2~4번 921원\n※ 송금·입금 확인이 아닙니다."
        val result = QrEncoder.encode(text) as QrResult.Success
        assertEquals(text, decode(result.matrix))
    }

    @Test
    fun `matrix is square and has dark modules`() {
        val matrix = (QrEncoder.encode("abc") as QrResult.Success).matrix
        var dark = 0
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) if (matrix[x, y]) dark++
        assertTrue(dark > 0)
    }

    @Test
    fun `empty text is reported and never encoded`() {
        assertEquals(QrResult.Empty, QrEncoder.encode(""))
    }

    @Test
    fun `text beyond QR capacity is reported as too long`() {
        assertEquals(QrResult.TooLong, QrEncoder.encode("가".repeat(3000)))
    }
}
