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
val Suit=FontFamily(Font(R.font.suit))
@Composable fun TagoTheme(dark:Boolean=false,content:@Composable ()->Unit){
 val colors=if(dark) darkColorScheme(primary=Color(0xFF9CC3E8),background=Color(0xFF15212E),surface=Color(0xFF202E3E),onPrimary=Color(0xFF15212E),onSurface=Color(0xFFE8EFF7),onBackground=Color(0xFFE8EFF7),onSurfaceVariant=Color(0xFFB6C5D6),secondaryContainer=Color(0xFF2E4257),onSecondaryContainer=Color(0xFFC5DDF5),outlineVariant=Color(0xFF45596F)) else lightColorScheme(primary=Color(0xFF315F8B),background=Color(0xFFF5F6F8),surface=Color.White,secondaryContainer=Color(0xFFEAF0F6),onSecondaryContainer=Color(0xFF315F8B),surfaceTint=Color(0xFF315F8B),onSurface=Color(0xFF202E3E),onBackground=Color(0xFF202E3E),onSurfaceVariant=Color(0xFF637185),outlineVariant=Color(0xFFE0E5EC))
 val t=Typography()
 MaterialTheme(colorScheme=colors,typography=Typography(bodyLarge=t.bodyLarge.copy(fontFamily=Suit),bodyMedium=t.bodyMedium.copy(fontFamily=Suit),bodySmall=t.bodySmall.copy(fontFamily=Suit),titleLarge=t.titleLarge.copy(fontFamily=Suit),titleMedium=t.titleMedium.copy(fontFamily=Suit),headlineLarge=t.headlineLarge.copy(fontFamily=Suit),headlineMedium=t.headlineMedium.copy(fontFamily=Suit),labelLarge=t.labelLarge.copy(fontFamily=Suit)),content={androidx.compose.runtime.CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(lineHeight=androidx.compose.ui.unit.TextUnit.Unspecified),content=content)})
}
