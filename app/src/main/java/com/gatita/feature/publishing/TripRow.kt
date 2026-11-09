package com.gatita.feature.publishing

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatita.R
import com.gatita.core.ui.component.*
import com.gatita.core.ui.theme.*

@Composable internal fun TripRow(title:String,amount:String,go:(Page)->Unit,fixed:Boolean=false,day:String="8",detail:String="30km · 42분 · 4명",onSelect:(String)->Unit={},joined:Boolean=false,onDelete:(()->Unit)?=null){
Surface(onClick={onSelect(title);go(Page.Detail)},modifier=Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(if(joined)0.dp else 20.dp),border=if(joined)null else BorderStroke(1.dp,Line)) {
Row(Modifier.padding(horizontal=28.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
Column(horizontalAlignment=Alignment.CenterHorizontally){Text(day,fontSize=28.sp);Note("11월")}
VerticalDivider(Modifier.height(58.dp),color=Line)
Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,fontSize=20.sp,fontWeight=FontWeight.Bold);Note(detail)}
Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(8.dp)){AmountLabel(amount,"원",26);Surface(color=if(fixed)MaterialTheme.colorScheme.surface else Tint,shape=androidx.compose.foundation.shape.RoundedCornerShape(6.dp)){Text(if(fixed)"정산 확정" else "정산 전",Modifier.padding(horizontal=8.dp,vertical=4.dp),color=Blue,fontSize=12.sp)}}
if(onDelete!=null)TextButton(onDelete){Text("삭제",fontSize=12.sp)}
Text("›",fontSize=26.sp,color=Muted)
}}}
