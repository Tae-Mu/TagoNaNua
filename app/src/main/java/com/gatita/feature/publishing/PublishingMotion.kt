package com.gatita.feature.publishing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.gatita.core.ui.component.VehicleDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatita.core.ui.component.Note
import com.gatita.core.ui.theme.Blue
import kotlinx.coroutines.delay

private val RevealEase = CubicBezierEasing(.16f,1f,.3f,1f)

@Composable
fun pageFadeModifier(enabled: Boolean): Modifier {
    val alpha = remember { Animatable(if(enabled) .65f else 1f) }
    LaunchedEffect(enabled) { alpha.animateTo(1f,tween(160)) }
    return Modifier.graphicsLayer { this.alpha=alpha.value }
}

@Composable
internal fun MotionSection(enabled:Boolean, delayMs:Int, offset:Float, content:@Composable ColumnScope.()->Unit) {
    val progress=remember { Animatable(if(enabled) 0f else 1f) }
    LaunchedEffect(enabled) {
        if(enabled && coroutineContext[MotionDurationScale]?.scaleFactor != 0f) delay(delayMs.toLong())
        progress.animateTo(1f,tween(720,easing=RevealEase))
    }
    Column(Modifier.fillMaxWidth().graphicsLayer {
        alpha=progress.value
        translationY=(1-progress.value)*offset*density
    },verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}

/** Approved B departure: Blender spin, then reveal the existing trip screen. */
@Composable
fun DepartureScreen(tripName:String,onReveal:()->Unit={},onFinished:()->Unit) {
    val finish by rememberUpdatedState(onFinished)
    val reveal by rememberUpdatedState(onReveal)
    var leaving by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    val entrance=remember { Animatable(0f) }
    val exit=remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f,tween(650,easing=RevealEase))
        // Decode failure must never trap the user on the transition.
        delay(6500)
        leaving=true
    }
    LaunchedEffect(ready) {
        if(ready) {
            delay(if(coroutineContext[MotionDurationScale]?.scaleFactor==0f)200 else 5000)
            leaving=true
        }
    }
    LaunchedEffect(leaving) {
        if(leaving) { reveal(); exit.animateTo(0f,tween(480));finish() }
    }
    BackHandler { leaving=true }
    VehicleDialog(onDismissRequest={leaving=true},scrimAlpha=0f,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize().graphicsLayer{alpha=exit.value},color=MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(40.dp,35.dp)) {
                val wide=maxWidth>=760.dp
                val mascotSize=if(wide) (maxWidth*.48f).coerceIn(340.dp,540.dp) else 260.dp
                val copy: @Composable () -> Unit = {
                    Column(Modifier.widthIn(max=480.dp).graphicsLayer {
                        alpha=entrance.value
                        translationY=(1-entrance.value)*10.dp.toPx()
                    }, horizontalAlignment=if(wide) Alignment.Start else Alignment.CenterHorizontally) {
                        Text("여행 기록을 시작했어요",color=Blue,fontSize=if(wide)19.sp else 16.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(tripName,fontSize=if(wide)44.sp else 30.sp,lineHeight=54.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,textAlign=if(wide) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Text("안전 운전하세요.",fontSize=if(wide)32.sp else 25.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Note("함께한 길은 타고가 기록할게요.")
                    }
                }
                val mascot: @Composable () -> Unit = {
                    com.gatita.feature.launch.JourneyMascot(Modifier.size(mascotSize),onReady={ready=true})
                }
                Column(Modifier.align(Alignment.Center).widthIn(max=1080.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                    if(wide) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        mascot()
                        Box(Modifier.weight(1f)){copy()}
                    } else Column(horizontalAlignment=Alignment.CenterHorizontally) { mascot();copy() }
                }
                Spacer(Modifier.height(if(wide)24.dp else 32.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        DepartureDots()
                        Text("잠시 후 기록 화면으로 이동해요",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=16.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    com.gatita.core.ui.component.Action("바로 기록 보기",primary=false,enabled=!leaving,minHeight=54,textSize=17,horizontalPadding=22,onClick={leaving=true})
                }
                }
            }
        }
    }
}

@Composable
private fun DepartureDots() {
    val transition=rememberInfiniteTransition(label="departure dots")
    val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(1200,easing=LinearEasing)),label="dot phase")
    val dotColor=Blue
    Canvas(Modifier.size(25.dp,5.dp)) {
        repeat(3) { index ->
            val position=(phase-index*.125f+1f)%1f
            val opacity=.25f+.75f*(1f-kotlin.math.abs(position-.35f)/.35f).coerceIn(0f,1f)
            drawCircle(dotColor.copy(alpha=opacity),radius=2.5.dp.toPx(),center=androidx.compose.ui.geometry.Offset((2.5f+index*10).dp.toPx(),size.height/2))
        }
    }
}
