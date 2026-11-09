package com.gatita.feature.publishing

// Publishing fixtures only. Not a repository or settlement engine.
data class TripPreview(val title:String,val total:Long,val distance:Int,val minutes:Int,val people:Int,val day:Int,val fixed:Boolean=false) {
 val subtitle get()="2026. 11. $day · ${distance}km · ${if(fixed)"더 뉴 그랜저" else "아반떼 CN8"}"
}
fun previewTrip(title:String)=when(title){
 "함께한 주말 여행"->TripPreview(title,60000,82,95,3,2,true)
 "주말 나들이"->TripPreview(title,50000,30,42,4,8)
 else->TripPreview(title,149600,30,42,4,8)
}
fun won(value:Long)=String.format(java.util.Locale.KOREA,"%,d",value)
