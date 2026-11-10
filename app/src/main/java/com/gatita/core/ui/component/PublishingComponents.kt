@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gatita.core.ui.component
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatita.core.ui.theme.*
@Composable fun Brand(wordmark:Boolean=true){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(androidx.compose.ui.res.painterResource(com.gatita.R.drawable.ic_tago),contentDescription=if(wordmark)null else "TAGO 로고",modifier=Modifier.size(42.dp),tint=Blue);if(wordmark)Text("TAGO",fontSize=19.sp,fontWeight=FontWeight.Bold)}}
@Composable fun Panel(modifier:Modifier=Modifier,padding:Int=20,gap:Int=12,content:@Composable ColumnScope.()->Unit){ Surface(modifier,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Column(Modifier.padding(padding.dp),verticalArrangement=Arrangement.spacedBy(gap.dp),content=content)} }
@Composable fun Title(title:String,subtitle:String="",driving:Boolean=false){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontSize=28.sp,fontWeight=FontWeight.Bold)
if(subtitle.isNotEmpty())Text(subtitle,color=Muted,fontSize=16.sp,modifier=Modifier.padding(top=8.dp))}
Surface(shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Line),color=MaterialTheme.colorScheme.surface){Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Surface(color=Tint,shape=RoundedCornerShape(8.dp)){Text(if(driving)"D" else "P",Modifier.padding(horizontal=10.dp,vertical=4.dp),color=Blue,fontSize=18.sp)};Text(if(driving)"주행 중" else "정차 중",color=Muted,fontSize=14.sp,modifier=Modifier.padding(end=4.dp))}}}}
@Composable fun Action(label:String,primary:Boolean=true,enabled:Boolean=true,modifier:Modifier=Modifier,arrow:Boolean=false,leadingIcon:Int?=null,trailingIcon:Int?=null,minHeight:Int=56,textSize:Int=18,horizontalPadding:Int=22,iconGap:Int=28,onClick:()->Unit){
 val interaction=androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
 val actionModifier=modifier.then(pressMotion(interaction,enabled))
 val shape=RoundedCornerShape(12.dp)
 val body:@Composable RowScope.()->Unit={if(leadingIcon!=null){Icon(androidx.compose.ui.res.painterResource(leadingIcon),null,Modifier.size(20.dp));Spacer(Modifier.width(10.dp))};Text(label,fontSize=textSize.sp,lineHeight=(textSize*1.5f).sp,fontWeight=FontWeight.SemiBold);if(trailingIcon!=null){Spacer(Modifier.width(40.dp));Icon(androidx.compose.ui.res.painterResource(trailingIcon),null,Modifier.size(20.dp))};if(arrow){Spacer(Modifier.width(iconGap.dp));ChevronRight()}}
 if(primary)Button(onClick,interactionSource=interaction,enabled=enabled,shape=shape,modifier=actionModifier.heightIn(min=minHeight.dp),contentPadding=PaddingValues(horizontal=horizontalPadding.dp,vertical=((minHeight-textSize*1.5f)/2).coerceAtLeast(8f).dp),content=body)
 else OutlinedButton(onClick,interactionSource=interaction,enabled=enabled,shape=shape,border=BorderStroke(1.dp,Line),colors=ButtonDefaults.outlinedButtonColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=Ink),modifier=actionModifier.heightIn(min=minHeight.dp),contentPadding=PaddingValues(horizontal=horizontalPadding.dp,vertical=((minHeight-textSize*1.5f)/2).coerceAtLeast(8f).dp),content=body)
}
@Composable fun Note(text:String){Text(text,color=Muted,fontSize=13.sp,lineHeight=20.sp)}
@Composable fun Metric(label:String,value:String,light:Boolean=false){Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text(label,color=if(light)Color(0xFFC5D9E8) else Muted,fontSize=13.sp)
AmountLabel(value.substringBeforeLast(" "),value.substringAfterLast(" "),30,if(light)Color.White else MaterialTheme.colorScheme.onSurface)}}
@Composable fun Metrics(){Surface(color=Navy,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){FlowRow(Modifier.padding(24.dp),horizontalArrangement=Arrangement.spacedBy(42.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){Metric("함께 이동한 거리","30.0 km",true)
Metric("기록 시간","42 분",true)
Metric("기록한 구간","3 개",true)}}}
@Composable fun Input(label:String,value:String,onValue:(String)->Unit,placeholder:String="",error:Boolean=false){Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text(label,fontSize=15.sp,color=Muted);OutlinedTextField(value,onValue,placeholder={Text(placeholder,color=Muted.copy(alpha=.65f))},singleLine=true,isError=error,shape=RoundedCornerShape(12.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.background,focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedBorderColor=Line,focusedBorderColor=Blue),modifier=Modifier.fillMaxWidth())}}
@Composable fun EmptyState(title:String,description:String){Panel(Modifier.fillMaxWidth()){Text(title,fontSize=20.sp)
Note(description)}}

@Composable fun AmountLabel(number:String,unit:String,size:Int=30,color:Color=Ink){
 Text(androidx.compose.ui.text.buildAnnotatedString {
  append(number)
  withStyle(androidx.compose.ui.text.SpanStyle(fontSize=(size*.35f).sp,fontWeight=FontWeight.Normal)){append("  $unit")}
 },fontSize=size.sp,color=color)
}

@Composable fun ChevronRight(modifier:Modifier=Modifier) {
 val color=LocalContentColor.current
 Canvas(modifier.size(20.dp)) {
  drawLine(color,Offset(size.width*.38f,size.height*.22f),Offset(size.width*.66f,size.height*.5f),1.7.dp.toPx(),StrokeCap.Round)
  drawLine(color,Offset(size.width*.66f,size.height*.5f),Offset(size.width*.38f,size.height*.78f),1.7.dp.toPx(),StrokeCap.Round)
 }
}
