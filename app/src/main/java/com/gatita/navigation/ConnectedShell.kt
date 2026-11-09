package com.gatita.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gatita.R
import com.gatita.core.ui.component.Brand
import com.gatita.core.ui.theme.*

/** Product navigation. The publishing fixture toolbar is never shown here. */
@Composable
internal fun ConnectedShell(selected:String,onNavigate:(String)->Unit,content:@Composable ()->Unit) {
    val items=listOf(Triple(Routes.HOME,"여행",R.drawable.ic_car),Triple("settlements","정산",R.drawable.ic_receipt),
        Triple(Routes.HISTORY,"여행 내역",R.drawable.ic_history),Triple("settings","설정",R.drawable.ic_settings))
    Surface(color=MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            if(maxWidth>=600.dp) Row {
                Surface(Modifier.width(116.dp).fillMaxHeight()) {
                    Column(Modifier.padding(12.dp,28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        Brand();Spacer(Modifier.height(12.dp))
                        items.forEach { (route,label,icon)->
                            if(route=="settings")Spacer(Modifier.weight(1f))
                            TextButton({onNavigate(route)},Modifier.fillMaxWidth().height(84.dp),
                                shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                colors=ButtonDefaults.textButtonColors(containerColor=if(selected==route)Tint else androidx.compose.ui.graphics.Color.Transparent,
                                    contentColor=if(selected==route)Blue else Muted)) {
                                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Icon(painterResource(icon),null);Text(label)
                                }
                            }
                        }

                    }
                }
                Box(Modifier.weight(1f)){content()}
            } else Column {
                Box(Modifier.weight(1f)){content()}
                NavigationBar {items.forEach { (route,label,icon)-> NavigationBarItem(selected==route,{onNavigate(route)},icon={Icon(painterResource(icon),null)},label={Text(label)}) }}
            }
        }
    }
}
