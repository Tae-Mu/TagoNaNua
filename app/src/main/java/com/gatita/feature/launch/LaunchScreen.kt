package com.gatita.feature.launch

import android.animation.ValueAnimator
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gatita.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF202E3E)
private val Blue = Color(0xFF315F8B)
private val Suit = FontFamily(Font(R.font.suit), Font(R.font.suit_bold, FontWeight.Bold))
private val RevealEase = CubicBezierEasing(.16f, 1f, .3f, 1f)

/** A local, finite brand introduction; never reports fake data-loading progress. */
@Composable
fun LaunchScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val finish by rememberUpdatedState(onFinished)
    val reduced = remember { !ValueAnimator.areAnimatorsEnabled() }
    var drawable by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        drawable = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, R.drawable.tago_launch))
            }.getOrNull()
        }
        ready = true
    }
    // Decode failures cannot trap the user on a brand screen.
    LaunchedEffect(ready) {
        if (ready) { delay(if (reduced || drawable == null) 300 else 4900); finish() }
    }
    LaunchedEffect(Unit) { delay(7000); finish() }
    DisposableEffect(drawable, lifecycle) {
        val animation = drawable as? AnimatedImageDrawable
        animation?.repeatCount = 0
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { animation?.stop(); finish() }
        }
        lifecycle.addObserver(observer)
        if (!reduced) animation?.start()
        onDispose { animation?.stop(); lifecycle.removeObserver(observer) }
    }
    val intro = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(ready) { if (ready && !reduced) intro.animateTo(1f, tween(1050, 80, RevealEase)) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF7F9FB)).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val w = maxWidth
        val h = maxHeight
        val scale = minOf(w.value / 1184f, h.value / 740f)
        val unit = scale.dp
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color(0xFFE2EDF7), Color(0xFFF9FAFB)), center=androidx.compose.ui.geometry.Offset(size.width*.73f,size.height*.5f),radius=size.width*.75f))
            val road = Path().apply {
                moveTo(-size.width*.08f,size.height*.97f)
                cubicTo(size.width*.29f,size.height*.67f,size.width*.63f,size.height*1.12f,size.width*1.08f,size.height*.84f)
            }
            drawPath(road,Color(0xFFBDD0E1).copy(alpha=.28f),style=Stroke(2*scale*density))
        }
        Column(Modifier.offset(x=w*.08f).align(Alignment.CenterStart).width(w*.45f)) {
            Image(painterResource(R.drawable.ic_tago),"TAGO",Modifier.size(unit*52).graphicsLayer { alpha=intro.value; translationY=(1-intro.value)*8*scale*density })
            Spacer(Modifier.height(unit*18))
            RevealText("TAGO",92*scale,FontWeight.Bold,ready,reduced,120,unit*100,-4.14*scale)
            Spacer(Modifier.height(unit*28))
            RevealText("함께 타고,",30*scale,FontWeight.Normal,ready,reduced,290,unit*46.5f,-1.05*scale)
            RevealText("가볍게 나눠요.",30*scale,FontWeight.Normal,ready,reduced,460,unit*46.5f,-1.05*scale)
            Spacer(Modifier.height(unit*18))
            RevealText("함께한 여정의 시작",14*scale,FontWeight.Normal,ready,reduced,700,unit*23,0.0,Color(0xFF75879A))
        }
        val image = drawable
        if (image != null) AndroidView(
            factory = { ImageView(it).apply { scaleType=ImageView.ScaleType.FIT_CENTER; importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO } },
            update = { if(it.drawable !== image) it.setImageDrawable(image) },
            modifier=Modifier.align(Alignment.TopEnd).offset(y=h*.12f).size(w*.58f).graphicsLayer {
                alpha=intro.value
                translationY=(1-intro.value)*30*scale*density
                scaleX=.94f+.06f*intro.value;scaleY=scaleX
                if(android.os.Build.VERSION.SDK_INT>=31){
                    val blur=(1-intro.value)*9*scale*density
                    renderEffect=if(blur>.1f) android.graphics.RenderEffect.createBlurEffect(blur,blur,android.graphics.Shader.TileMode.DECAL).asComposeRenderEffect() else null
                }
            }
        )
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal=w*.08f).padding(bottom=h*.07f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(unit*10)) {
                LoadingDots(scale,reduced)
                Text("곧 만나요",fontFamily=Suit,fontSize=(13*scale).sp,color=Color(0xFF6B8194))
            }
            Text("타고, 나눠",fontFamily=Suit,fontSize=(13*scale).sp,color=Color(0xFF6B8194))
        }
    }
}

@Composable
private fun RevealText(text:String,size:Float,weight:FontWeight,ready:Boolean,reduced:Boolean,delayMs:Int,height:Dp,spacing:Double,color:Color=Ink) {
    val progress=remember { Animatable(if(reduced) 1f else 0f) }
    LaunchedEffect(ready) { if(ready && !reduced) progress.animateTo(1f,tween(1000,delayMs,RevealEase)) }
    Box(Modifier.height(height).clipToBounds()) {
        Text(text,fontFamily=Suit,fontWeight=weight,fontSize=size.sp,lineHeight=(size*if(weight==FontWeight.Bold) 1.08f else 1.55f).sp,letterSpacing=spacing.sp,color=color,
            modifier=Modifier.graphicsLayer { alpha=progress.value;translationY=(1-progress.value)*height.toPx()*1.1f;rotationZ=(1-progress.value)*2 })
    }
}

@Composable
private fun LoadingDots(scale:Float,reduced:Boolean) {
    val transition=rememberInfiniteTransition(label="launch dots")
    val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(1200,easing=LinearEasing)),label="pulse")
    Row(horizontalArrangement=Arrangement.spacedBy((4*scale).dp)) {
        repeat(3) { i ->
            val p=(phase-i*.125f+1)%1
            val alpha=if(reduced) .6f else .3f+.7f*(1-kotlin.math.abs(p-.35f)/.35f).coerceIn(0f,1f)
            Canvas(Modifier.size((4*scale).dp)) { drawCircle(Blue.copy(alpha=alpha)) }
        }
    }
}
