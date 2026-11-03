// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import android.os.Bundle
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.rounded.Call
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidphone.core.calls.CallGroup
import dev.liquidphone.data.Repo
import dev.liquidphone.data.Stores
import dev.liquidphone.ui.glass.ActionSheetHost
import dev.liquidphone.ui.glass.DialogHost
import dev.liquidphone.ui.glass.DialogSpec
import dev.liquidphone.ui.glass.FlatNavBar
import dev.liquidphone.ui.glass.FlatNavBarHeight
import dev.liquidphone.ui.glass.GlassButton
import dev.liquidphone.ui.glass.GlassNavBar
import dev.liquidphone.ui.glass.GlassRoot
import dev.liquidphone.ui.glass.GlassTheme
import dev.liquidphone.ui.glass.LocalActionSheet
import dev.liquidphone.ui.glass.LocalDialog
import dev.liquidphone.ui.glass.LocalGlass
import dev.liquidphone.ui.glass.LocalPrompt
import dev.liquidphone.ui.glass.LocalReducedMotion
import dev.liquidphone.ui.glass.LocalTabActive
import dev.liquidphone.ui.glass.NavItem
import dev.liquidphone.ui.glass.PromptHost
import dev.liquidphone.ui.glass.PromptSpec
import dev.liquidphone.ui.glass.SheetSpec
import dev.liquidphone.ui.glass.isDarkTheme
import dev.liquidphone.ui.screens.CallDetailScreen
import dev.liquidphone.incall.CallManager
import dev.liquidphone.incall.CallNotifications
import dev.liquidphone.incall.InCallActivity
import dev.liquidphone.ui.screens.BlockedScreen
import dev.liquidphone.ui.screens.CallerHost
import dev.liquidphone.ui.screens.ContactDetailScreen
import dev.liquidphone.ui.screens.ContactsTab
import dev.liquidphone.ui.screens.DialTab
import dev.liquidphone.ui.screens.FavoritesTab
import dev.liquidphone.ui.screens.LocalToast
import dev.liquidphone.ui.screens.RecentsTab
import dev.liquidphone.ui.screens.SettingsScreen
import dev.liquidphone.ui.screens.ToastHost
import dev.liquidphone.ui.screens.countryName
import dev.liquidphone.util.preferHighestRefreshRate
import dev.liquidphone.util.str

/** Pantallas que se abren encima de las pestañas. */
sealed interface Overlay {
    data class Settings(val toAreaCode: Boolean = false) : Overlay
    data object Blocked : Overlay
    data class Contact(val contactId: Long) : Overlay
    data class Call(val group: CallGroup) : Overlay
}

private const val TAB_KEYPAD = 0
private const val TAB_RECENTS = 1
private const val TAB_FAVORITES = 2
private const val TAB_COUNT = 4

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_RECENTS = "recents"
        const val EXTRA_DIAL = "dial"
    }

    override fun onResume() {
        super.onResume()
        Repo.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** Un número de otra app o de la notificación queda escrito en el teclado: nunca marca solo. */
    private fun handle(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_RECENTS, false)) Repo.requestRecents()
        intent.getStringExtra(EXTRA_DIAL)?.let(Repo::requestDial)
        if ((intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW) && intent.data?.scheme == "tel") {
            Repo.requestDial(intent.data?.schemeSpecificPart.orEmpty())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        preferHighestRefreshRate()
        setContent {
            val settings by Stores.settings.state.collectAsStateWithLifecycle()
            DevTools.WindowGuard()
            SystemBarsFor(isDarkTheme(settings.themeMode))
            GlassTheme(settings.glassStyle) {
                PhoneUi(settings.animatedBackground, flatNav = settings.uiStyle == 1, startTab = settings.startTab)
            }
        }
    }
}

@Composable
private fun PhoneUi(animated: Boolean, flatNav: Boolean, startTab: Int) {
    var tab by rememberSaveable { mutableIntStateOf(startTab.coerceIn(TAB_KEYPAD, TAB_FAVORITES)) }
    var tabDrag by remember { mutableStateOf<Float?>(null) }
    val stack = remember { mutableStateListOf<Overlay>() }
    val holder = rememberSaveableStateHolder()
    var sheet by remember { mutableStateOf<SheetSpec?>(null) }
    var prompt by remember { mutableStateOf<PromptSpec?>(null) }
    var dialog by remember { mutableStateOf<DialogSpec?>(null) }
    var toast by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val reduced = LocalReducedMotion.current
    val conflict by Repo.countryConflict.collectAsStateWithLifecycle()
    val pendingDial by Repo.pendingDial.collectAsStateWithLifecycle()
    val openRecents by Repo.openRecents.collectAsStateWithLifecycle()
    val inCall by CallManager.ui.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext

    LaunchedEffect(pendingDial) {
        if (pendingDial != null) {
            stack.clear()
            tab = TAB_KEYPAD
        }
    }
    LaunchedEffect(openRecents) {
        if (openRecents > 0) {
            stack.clear()
            tab = TAB_RECENTS
        }
    }
    // Abrir recientes es ver las perdidas. Sin el rol, de eso se encarga el marcador que lo tenga.
    LaunchedEffect(tab) {
        if (tab == TAB_RECENTS && Repo.isDefaultDialer()) CallNotifications.missedSeen(appContext)
    }

    fun push(o: Overlay) {
        stack += o
    }
    fun pop() {
        if (stack.isEmpty()) return
        val key = "${stack.lastIndex}:${stack.last()}"
        stack.removeAt(stack.lastIndex)
        holder.removeState(key)
    }
    BackHandler(enabled = stack.isNotEmpty() && sheet == null && prompt == null && dialog == null) { pop() }
    BackHandler(enabled = stack.isEmpty() && tab != TAB_KEYPAD) { tab = TAB_KEYPAD }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val dir = LocalLayoutDirection.current
    val navBar = if (flatNav) FlatNavBarHeight else 70.dp + 12.dp
    val navHeight = navBar + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabPadding = PaddingValues(
        top = insets.calculateTopPadding(),
        bottom = navHeight + 16.dp,
        start = insets.calculateStartPadding(dir),
        end = insets.calculateEndPadding(dir),
    )
    val overlayPadding = PaddingValues(
        top = insets.calculateTopPadding(),
        bottom = insets.calculateBottomPadding() + 16.dp,
        start = insets.calculateStartPadding(dir),
        end = insets.calculateEndPadding(dir),
    )

    CompositionLocalProvider(
        LocalActionSheet provides { sheet = it },
        LocalPrompt provides { prompt = it },
        LocalDialog provides { dialog = it },
        LocalToast provides { toast = it to System.nanoTime() },
    ) {
        // Dos chips de países distintos: no se adivina el país por defecto.
        LaunchedEffect(conflict) {
            if (conflict.isNotEmpty()) {
                dialog = DialogSpec(str(R.string.country_conflict_title)) { dismiss ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(str(R.string.country_conflict_text), color = LocalGlass.current.content, fontSize = 14.sp)
                        conflict.forEach { iso ->
                            GlassButton(countryName(iso), { Repo.setCountry(iso); dismiss() }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        GlassRoot(
            animated = animated,
            floating = {
                AnimatedVisibility(
                    stack.isEmpty(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn() + if (reduced) fadeIn() else slideInVertically { it / 2 },
                    exit = fadeOut() + if (reduced) fadeOut() else slideOutVertically { it / 2 },
                ) {
                    val navItems = listOf(
                        NavItem(str(R.string.tab_keypad), Icons.Rounded.Dialpad),
                        NavItem(str(R.string.tab_recents), Icons.Rounded.History),
                        NavItem(str(R.string.tab_favorites), Icons.Rounded.Star),
                        NavItem(str(R.string.tab_contacts), Icons.Rounded.Contacts),
                    )
                    if (flatNav) {
                        FlatNavBar(navItems, selected = tab, onSelect = { tab = it })
                    } else {
                        GlassNavBar(
                            items = navItems,
                            selected = tab,
                            dragPosition = { tabDrag },
                            onSelect = { tab = it },
                            modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                // Volver a la llamada desde el teclado, por ejemplo al agregar otra.
                inCall.primary?.let { call ->
                    GlassButton(
                        str(R.string.call_return, call.title),
                        { appContext.startActivity(Intent(appContext, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(8.dp),
                        icon = Icons.Rounded.Call,
                        prominent = true,
                    )
                }
                ToastHost(toast) { toast = null }
                ActionSheetHost(sheet) { sheet = null }
                PromptHost(prompt) { prompt = null }
                DialogHost(dialog) { dialog = null }
            },
        ) {
            CallerHost {
                val top = stack.lastOrNull()
                val tabsAlpha by animateFloatAsState(
                    if (top == null) 1f else 0f,
                    tween(if (reduced) 0 else if (top == null) 220 else 120),
                    label = "tabs",
                )
                val onSettings = { push(Overlay.Settings()) }
                val onContact: (Long) -> Unit = { push(Overlay.Contact(it)) }
                val onCall: (CallGroup) -> Unit = { push(Overlay.Call(it)) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = tabsAlpha }
                        .drawWithContent { if (tabsAlpha > 0.01f) drawContent() }
                        .then(if (top != null) Modifier.clearAndSetSemantics { }.pointerInput(Unit) { swallowTouches() } else Modifier),
                ) {
                    SwipeTabs(tab, onScreen = top == null, onTab = { tab = it }, onDrag = { tabDrag = it }) { t ->
                        when (t) {
                            TAB_KEYPAD -> DialTab(tabPadding, onSettings, onContact)
                            TAB_RECENTS -> RecentsTab(tabPadding, onSettings, onCall)
                            TAB_FAVORITES -> FavoritesTab(tabPadding, onSettings, onContact)
                            else -> ContactsTab(tabPadding, onSettings, { push(Overlay.Settings(toAreaCode = true)) }, onContact)
                        }
                    }
                }

                val topKey = if (top == null) "none" else "${stack.lastIndex}:$top"
                AnimatedContent(
                    targetState = top to topKey,
                    transitionSpec = {
                        if (reduced) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            (fadeIn(tween(200)) + scaleIn(tween(220), initialScale = 0.97f)) togetherWith fadeOut(tween(120))
                        }
                    },
                    contentKey = { it.second },
                    label = "overlay",
                ) { (current, key) ->
                    if (current == null) {
                        Box(Modifier.fillMaxSize())
                        return@AnimatedContent
                    }
                    holder.SaveableStateProvider(key) {
                        when (current) {
                            is Overlay.Settings -> SettingsScreen(overlayPadding, ::pop, current.toAreaCode, onBlocked = { push(Overlay.Blocked) })
                            Overlay.Blocked -> BlockedScreen(overlayPadding, ::pop)
                            is Overlay.Contact -> ContactDetailScreen(current.contactId, overlayPadding, ::pop)
                            is Overlay.Call -> CallDetailScreen(current.group, overlayPadding, ::pop, onContact)
                        }
                    }
                }
            }
        }
    }
}

/** Se traga todos los toques (para las pestañas que quedan debajo de otra pantalla). */
private suspend fun PointerInputScope.swallowTouches() {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * Pestañas que se cambian deslizando, como en Liquid Calendar y Liquid Contacts. Todas siguen
 * creadas aunque no se vean y la burbuja de la barra sigue al dedo.
 */
@Composable
private fun SwipeTabs(
    tab: Int,
    onScreen: Boolean,
    onTab: (Int) -> Unit,
    onDrag: (Float?) -> Unit,
    content: @Composable (Int) -> Unit,
) {
    val pager = rememberPagerState(initialPage = tab) { TAB_COUNT }
    val select by rememberUpdatedState(onTab)
    val drag by rememberUpdatedState(onDrag)
    val reduced = LocalReducedMotion.current
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { select(it) }
    }
    LaunchedEffect(pager) {
        snapshotFlow { if (pager.isScrollInProgress) pager.currentPage + pager.currentPageOffsetFraction else null }
            .collect { drag(it) }
    }
    LaunchedEffect(tab) {
        if (pager.targetPage != tab) {
            if (reduced) pager.scrollToPage(tab) else pager.animateScrollToPage(tab)
        }
    }
    HorizontalPager(
        state = pager,
        flingBehavior = PagerDefaults.flingBehavior(pager, snapPositionalThreshold = 0.15f),
        beyondViewportPageCount = TAB_COUNT - 1,
        userScrollEnabled = onScreen,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        CompositionLocalProvider(LocalTabActive provides (page == pager.currentPage && onScreen)) { content(page) }
    }
}

/** Iconos de las barras del sistema según el tema (claros sobre fondo oscuro). */
@Composable
fun SystemBarsFor(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(dark) {
        val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
