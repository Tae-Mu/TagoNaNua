package com.gatita.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gatita.di.AppContainer
import com.gatita.domain.model.UNSAVED_ID
import com.gatita.feature.cost.CostFormRoute
import com.gatita.feature.cost.CostFormViewModel
import com.gatita.feature.history.HistoryRoute
import com.gatita.feature.history.HistoryViewModel
import com.gatita.feature.home.HomeRoute
import com.gatita.feature.home.HomeViewModel
import com.gatita.feature.settlement.SettlementRoute
import com.gatita.feature.settlement.SettlementViewModel
import com.gatita.feature.share.ResultRoute
import com.gatita.feature.share.ResultViewModel
import com.gatita.feature.trip.TripRoute
import com.gatita.feature.trip.TripViewModel

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val TRIP = "trip/{tripId}"
    const val COST = "cost/{tripId}/{costId}"
    const val SETTLEMENT = "settlement/{tripId}"
    const val RESULT = "result/{tripId}"

    fun trip(tripId: Long) = "trip/$tripId"

    /** [costId]가 [UNSAVED_ID]면 새 비용 등록, 아니면 해당 비용 수정. */
    fun cost(tripId: Long, costId: Long = UNSAVED_ID) = "cost/$tripId/$costId"
    fun settlement(tripId: Long) = "settlement/$tripId"
    fun result(tripId: Long) = "result/$tripId"
}

private val tripArgs = listOf(navArgument("tripId") { type = NavType.LongType })
private val costArgs = listOf(
    navArgument("tripId") { type = NavType.LongType },
    navArgument("costId") { type = NavType.LongType },
)

@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val settingsViewModel: com.gatita.feature.settings.SettingsViewModel = viewModel(factory=com.gatita.feature.settings.SettingsViewModel.factory(container))
    val settingsState by settingsViewModel.state.collectAsState()
    val appearance = settingsState.saved.appearance
    val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route
    val shellHome:HomeViewModel=viewModel(key="navigation-home",factory=HomeViewModel.factory(container))
    val shellHomeState by shellHome.state.collectAsState()
    val journey=shellHomeState.activeTrip?.let { trip ->
        com.gatita.core.ui.component.ActiveJourney(trip.name){nav.navigate(Routes.trip(trip.id)){launchSingleTop=true}}
    }
    val selected=when(route){Routes.HISTORY->Routes.HISTORY;"settings"->"settings";"settlements",Routes.SETTLEMENT,Routes.RESULT->"settlements";else->Routes.HOME}
    com.gatita.core.ui.theme.TagoTheme(if(appearance=="시스템")isSystemInDarkTheme() else appearance=="야간") {
    ConnectedShell(selected,{ target ->
        // Re-selecting the current destination must not destroy/recreate its screen.
        if (route != target) nav.navigate(target) {
            val resetFinishedHome = target == Routes.HOME && shellHomeState.activeTrip == null
            popUpTo(Routes.HOME) { inclusive = resetFinishedHome; saveState = !resetFinishedHome }
            launchSingleTop = true
            restoreState = !resetFinishedHome
        }
    }) {
    NavHost(navController = nav, startDestination = Routes.HOME,
        enterTransition={ fadeIn(tween(180, easing=FastOutSlowInEasing)) },
        exitTransition={ fadeOut(tween(180, easing=FastOutSlowInEasing)) },
        popEnterTransition={ fadeIn(tween(180, easing=FastOutSlowInEasing)) },
        popExitTransition={ fadeOut(tween(180, easing=FastOutSlowInEasing)) }) {
        journeyDestination("settings", journey) { com.gatita.feature.settings.SettingsRoute(settingsViewModel) }
        journeyDestination("settlements", journey) {
            com.gatita.feature.home.SettlementListRoute(
                viewModel=shellHome,
                onOpen={nav.navigate(Routes.settlement(it))},onHistory={nav.navigate(Routes.HISTORY)})
        }
        journeyDestination(Routes.HOME, journey) {
            HomeRoute(
                viewModel = shellHome,
                onOpenTrip = { nav.navigate(Routes.trip(it)) },
                onOpenSettlement = { nav.navigate(Routes.settlement(it)) },
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                onOpenSettlements = { nav.navigate("settlements") },
                activeContent = { activeId ->
                    TripRoute(
                        viewModel=viewModel(key="active-trip-$activeId",factory=TripViewModel.factory(container,activeId)),
                        onBack={nav.navigate(Routes.HISTORY)},
                        onAddCost={nav.navigate(Routes.cost(it))},
                        onEditCost={tripId,costId->nav.navigate(Routes.cost(tripId,costId))},
                        onOpenSettlement={nav.navigate(Routes.settlement(it))},
                        onOpenResult={nav.navigate(Routes.result(it))},
                        onDiscarded={nav.navigate(Routes.HOME){popUpTo(Routes.HOME){inclusive=true};launchSingleTop=true}},
                    )
                },
            )
        }
        journeyDestination(Routes.HISTORY, journey) {
            HistoryRoute(
                viewModel = viewModel(factory = HistoryViewModel.factory(container)),
                onBack = { nav.popBackStack() },
                onOpenTrip = { nav.navigate(Routes.trip(it)) },
                onOpenSettlement = { nav.navigate(Routes.settlement(it)) },
                onOpenResult = { nav.navigate(Routes.result(it)) },
            )
        }
        journeyDestination(Routes.TRIP, journey, arguments = tripArgs) {
            TripRoute(
                viewModel = viewModel(factory = TripViewModel.factory(container)),
                onBack = { nav.popBackStack() },
                onAddCost = { nav.navigate(Routes.cost(it)) },
                onEditCost = { tripId, costId -> nav.navigate(Routes.cost(tripId, costId)) },
                // 여행을 마친 뒤에는 기록 화면으로 돌아오지 않도록 스택에서 뺀다.
                onOpenSettlement = { id ->
                    nav.navigate(Routes.settlement(id)) {
                        popUpTo(Routes.TRIP) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenResult = { nav.navigate(Routes.result(it)) },
                // 삭제된 여행 화면으로 돌아오지 않도록 홈까지 스택을 비운다.
                onDiscarded = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        journeyDestination(Routes.COST, journey, arguments = costArgs) {
            CostFormRoute(
                viewModel = viewModel(factory = CostFormViewModel.factory(container)),
                onBack = { nav.popBackStack() },
                // 저장에 성공했을 때만 닫는다. 호출한 화면은 Room 구독으로 목록을 갱신한다.
                onSaved = { nav.popBackStack() },
            )
        }
        journeyDestination(Routes.SETTLEMENT, journey, arguments = tripArgs) { entry ->
            SettlementRoute(
                viewModel = viewModel(factory = SettlementViewModel.factory(container)),
                tripId = entry.arguments?.getLong("tripId") ?: UNSAVED_ID,
                onBack = { nav.popBackStack() },
                onOpenResult = { id ->
                    nav.navigate(Routes.result(id)) { popUpTo(Routes.SETTLEMENT) { inclusive = true } }
                },
                onOpenTrip = { nav.navigate(Routes.trip(it)) },
            )
        }
        journeyDestination(Routes.RESULT, journey, arguments = tripArgs) {
            ResultRoute(
                viewModel = viewModel(factory = ResultViewModel.factory(container)),
                onBack = { nav.popBackStack() },
                onHome = { nav.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
    }
}
    }
}

/** Each outgoing destination keeps its own banner layout until its fade has finished. */
private fun androidx.navigation.NavGraphBuilder.journeyDestination(
    route: String,
    journey: com.gatita.core.ui.component.ActiveJourney?,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable androidx.compose.animation.AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit,
) {
    composable(route, arguments = arguments) { entry ->
        val scope = this
        CompositionLocalProvider(com.gatita.core.ui.component.LocalActiveJourney provides
            journey.takeUnless { route == Routes.HOME || route == Routes.TRIP }) {
            content(scope, entry)
        }
    }
}
