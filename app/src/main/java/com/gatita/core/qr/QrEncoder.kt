package com.gatita.core.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** 여백(quiet zone)을 제외한 QR 모듈 격자. 어두운 모듈이 true다. */
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    init {
        require(modules.size == size * size) { "modules must be size*size" }
    }

    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]
}

sealed interface QrResult {
    data class Success(val matrix: QrMatrix) : QrResult

    /** 내용이 QR 용량을 넘는다. 호출자는 더 짧은 텍스트로 다시 시도해야 한다. */
    data object TooLong : QrResult

    data object Empty : QrResult
}

/**
 * 텍스트 QR 인코더(ZXing core). 네트워크를 쓰지 않으며 [text]를 UTF-8로 그대로 담는다.
 * 텍스트 QR은 암호화나 접근 제어가 아니므로 호출자가 담을 내용의 범위를 정한다.
 * 휴대폰 QR 리더에서 한글이 올바로 보이는지는 실기기로 확인해야 한다(이 코드로는 검증되지 않음).
 */
object QrEncoder {
    private val hints: Map<EncodeHintType, Any> = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        // 여백은 그리는 쪽에서 정한다.
        EncodeHintType.MARGIN to 0,
    )

    fun encode(text: String): QrResult {
        if (text.isEmpty()) return QrResult.Empty
        val bits = try {
            // 폭·높이 0이면 모듈당 1칸인 최소 크기 격자를 돌려준다.
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (e: WriterException) {
            return QrResult.TooLong
        }
        val size = bits.width
        val modules = BooleanArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                modules[y * size + x] = bits.get(x, y)
            }
        }
        return QrResult.Success(QrMatrix(size, modules))
    }
}
