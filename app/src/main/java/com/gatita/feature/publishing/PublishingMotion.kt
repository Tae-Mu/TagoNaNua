package com.gatita.feature.publishing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatita.core.ui.component.Brand
import com.gatita.core.ui.component.Note
import com.gatita.core.ui.theme.Blue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/** Timed publishing transition, matching workflow.js. No recording or persistence. */
@Composable
fun DepartureScreen(tripName:String,onReveal:()->Unit={},onFinished:()->Unit) {
    val motionBlue=Blue
    val finish by rememberUpdatedState(onFinished)
    val reveal by rememberUpdatedState(onReveal)
    var leaving by remember { mutableStateOf(false) }
    val entrance=remember { Animatable(0f) }
    val progress=remember { Animatable(0f) }
    val exit=remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { entrance.animateTo(1f,tween(720,easing=RevealEase)) }
        launch { progress.animateTo(1f,tween(3320,easing=LinearEasing)) }
        delay(if(coroutineContext[MotionDurationScale]?.scaleFactor==0f)200 else 3320)
        leaving=true
    }
    LaunchedEffect(leaving) {
        if(leaving) { reveal(); exit.animateTo(0f,tween(480));finish() }
    }
    BackHandler { leaving=true }
    Dialog(onDismissRequest={leaving=true},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
    Surface(Modifier.fillMaxSize().graphicsLayer{alpha=exit.value},color=MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().graphicsLayer { alpha=exit.value },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Box(Modifier.size(150.dp).graphicsLayer { alpha=entrance.value;scaleX=.8f+.2f*entrance.value;scaleY=scaleX;rotationZ=-8f*(1-entrance.value) },contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(motionBlue.copy(alpha=.06f))
                val phase=progress.value
                repeat(3){index->
                    val ring=(phase*2f+index/3f)%1f
                    drawCircle(motionBlue.copy(alpha=(1-ring)*.2f),radius=size.minDimension*(.2f+ring*.7f),style=Stroke(1.dp.toPx()))
                }
                val angle=phase*6.283f
                drawCircle(motionBlue.copy(alpha=.6f),radius=3.dp.toPx(),center=center+androidx.compose.ui.geometry.Offset(kotlin.math.cos(angle)*size.width*.6f,kotlin.math.sin(angle)*size.height*.6f))
                drawCircle(motionBlue.copy(alpha=.18f),radius=size.minDimension*.43f,style=Stroke(1.dp.toPx()))
                drawCircle(motionBlue.copy(alpha=.1f),radius=size.minDimension*.49f,style=Stroke(1.dp.toPx()))
            }
            Box(Modifier.graphicsLayer{scaleX=1.8f;scaleY=1.8f}){Brand(false)}
        }
        Spacer(Modifier.height(26.dp))
        Text("여행 기록을 시작했어요",color=motionBlue,fontSize=14.sp)
        Spacer(Modifier.height(14.dp))
        MotionSection(true,180,8f){Text(tripName,modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontSize=36.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
        Spacer(Modifier.height(22.dp))
        MotionSection(true,320,8f){Text("안전 운전하세요.",modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontSize=26.sp,fontWeight=FontWeight.Bold)}
        Spacer(Modifier.height(8.dp))
        Note("함께한 길은 타고가 기록할게요.")
        Spacer(Modifier.height(32.dp))
        LinearProgressIndicator(progress={progress.value},modifier=Modifier.width(240.dp),color=motionBlue)
        Spacer(Modifier.height(14.dp))
        Note("잠시 후 여행 기록 화면으로 이동해요")
        TextButton(onClick={leaving=true},enabled=!leaving) { Text("바로 기록 보기 →") }
    }
}
}
}
