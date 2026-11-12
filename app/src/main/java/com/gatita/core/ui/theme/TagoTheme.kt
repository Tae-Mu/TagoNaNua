package com.gatita.core.ui.theme
import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.gatita.R
val Blue:Color @Composable get()=MaterialTheme.colorScheme.primary
val Navy=Color(0xFF234761)
val Ink:Color @Composable get()=MaterialTheme.colorScheme.onSurface
val Muted:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant
val Tint:Color @Composable get()=MaterialTheme.colorScheme.secondaryContainer
val Line:Color @Composable get()=MaterialTheme.colorScheme.outlineVariant
val Suit=FontFamily(
 Font(R.font.suit,weight=androidx.compose.ui.text.font.FontWeight.Normal),
 Font(R.font.suit_semibold,weight=androidx.compose.ui.text.font.FontWeight.SemiBold),
 Font(R.font.suit_bold,weight=androidx.compose.ui.text.font.FontWeight.Bold),
)
@Composable fun TagoTheme(dark:Boolean=false,content:@Composable ()->Unit){
 val view=androidx.compose.ui.platform.LocalView.current
 if(!view.isInEditMode) androidx.compose.runtime.SideEffect {
  (view.context as? android.app.Activity)?.window?.let { window ->
   androidx.core.view.WindowCompat.getInsetsController(window,view).apply {
    isAppearanceLightStatusBars=!dark
    isAppearanceLightNavigationBars=!dark
   }
  }
 }
 val colors=if(dark) darkColorScheme(primary=Color(0xFF9CC3E8),background=Color(0xFF15212E),surface=Color(0xFF202E3E),onPrimary=Color(0xFF15212E),onSurface=Color(0xFFE8EFF7),onBackground=Color(0xFFE8EFF7),onSurfaceVariant=Color(0xFFB6C5D6),secondaryContainer=Color(0xFF2E4257),onSecondaryContainer=Color(0xFFC5DDF5),outlineVariant=Color(0xFF45596F)) else lightColorScheme(primary=Color(0xFF315F8B),background=Color(0xFFF5F6F8),surface=Color.White,secondaryContainer=Color(0xFFEAF0F6),onSecondaryContainer=Color(0xFF315F8B),surfaceTint=Color(0xFF315F8B),onSurface=Color(0xFF202E3E),onBackground=Color(0xFF202E3E),onSurfaceVariant=Color(0xFF637185),outlineVariant=Color(0xFFE0E5EC))
 val t=Typography()
 // [목적] Material 기본 글자 스타일 중 작은 것(12~14sp)을 차량 화면 하한(15~16sp)으로 상향
 // [이유] style=typography.bodyMedium 처럼 스타일 이름으로 지정된 글자는 fontSize 일괄 상향에서 빠져 QR 안내·요약·빈 상태 카드가 14sp로 남아 있었음
 // [사이드 임팩트] 이 스타일을 쓰는 Material 기본 컴포넌트(TextButton, 칩, 입력칸 보조 문구 등)의 글자도 함께 커짐
 // [수정 시 고려사항] 15sp 미만으로 내리지 말 것. 줄 높이도 글자 크기에 맞춰 함께 지정함
 // [목적] 앱 전체 기본 글자 스타일에 한글 어절 단위 줄바꿈 적용
 // [이유] 글자 크기를 올린 뒤 '바뀌지 않 / 아요'처럼 단어 중간에서 줄이 바뀌는 문제가 생김
 // [사이드 임팩트] style을 직접 지정하지 않은 모든 Text의 줄바꿈 위치가 바뀜 (API 33 미만에서는 무시됨)
 // [수정 시 고려사항] typography 스타일을 직접 쓰는 Text에는 적용되지 않음
 MaterialTheme(colorScheme=colors,typography=Typography(bodyLarge=t.bodyLarge.copy(fontFamily=Suit),bodyMedium=t.bodyMedium.copy(fontFamily=Suit,fontSize=androidx.compose.ui.unit.TextUnit(16f,androidx.compose.ui.unit.TextUnitType.Sp),lineHeight=androidx.compose.ui.unit.TextUnit(24f,androidx.compose.ui.unit.TextUnitType.Sp)),bodySmall=t.bodySmall.copy(fontFamily=Suit,fontSize=androidx.compose.ui.unit.TextUnit(15f,androidx.compose.ui.unit.TextUnitType.Sp),lineHeight=androidx.compose.ui.unit.TextUnit(22f,androidx.compose.ui.unit.TextUnitType.Sp)),titleLarge=t.titleLarge.copy(fontFamily=Suit),titleMedium=t.titleMedium.copy(fontFamily=Suit),headlineLarge=t.headlineLarge.copy(fontFamily=Suit),headlineMedium=t.headlineMedium.copy(fontFamily=Suit),labelLarge=t.labelLarge.copy(fontFamily=Suit,fontSize=androidx.compose.ui.unit.TextUnit(16f,androidx.compose.ui.unit.TextUnitType.Sp),lineHeight=androidx.compose.ui.unit.TextUnit(22f,androidx.compose.ui.unit.TextUnitType.Sp)),titleSmall=t.titleSmall.copy(fontFamily=Suit,fontSize=androidx.compose.ui.unit.TextUnit(16f,androidx.compose.ui.unit.TextUnitType.Sp),lineHeight=androidx.compose.ui.unit.TextUnit(22f,androidx.compose.ui.unit.TextUnitType.Sp)),labelMedium=t.labelMedium.copy(fontFamily=Suit,fontSize=androidx.compose.ui.unit.TextUnit(15f,androidx.compose.ui.unit.TextUnitType.Sp),lineHeight=androidx.compose.ui.unit.TextUnit(20f,androidx.compose.ui.unit.TextUnitType.Sp)),labelSmall=t.labelSmall.copy(fontFamily=Suit,fontSize=androidx.compose.ui.unit.TextUnit(15f,androidx.compose.ui.unit.TextUnitType.Sp),lineHeight=androidx.compose.ui.unit.TextUnit(20f,androidx.compose.ui.unit.TextUnitType.Sp)),headlineSmall=t.headlineSmall.copy(fontFamily=Suit),displayLarge=t.displayLarge.copy(fontFamily=Suit),displayMedium=t.displayMedium.copy(fontFamily=Suit),displaySmall=t.displaySmall.copy(fontFamily=Suit)),content={androidx.compose.runtime.CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(lineHeight=androidx.compose.ui.unit.TextUnit.Unspecified,lineBreak=androidx.compose.ui.text.style.LineBreak(androidx.compose.ui.text.style.LineBreak.Strategy.HighQuality,androidx.compose.ui.text.style.LineBreak.Strictness.Normal,androidx.compose.ui.text.style.LineBreak.WordBreak.Phrase)),content=content)})
}
