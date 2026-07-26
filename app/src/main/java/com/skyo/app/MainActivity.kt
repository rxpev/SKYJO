package com.skyo.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.skyo.game.Action
import com.skyo.game.BotDecisionEngine
import com.skyo.game.Card
import com.skyo.game.GameState
import com.skyo.game.PlayerState
import com.skyo.game.SkyoGame
import com.skyo.game.TurnStage
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

private class ActivePileDrag(
    val card: Card,
    val sourceBounds: Rect,
) {
    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    fun moveBy(delta: Offset) {
        dragOffset += delta
    }
}

private data class RoundScoreLine(
    val playerName: String,
    val previousTotal: Int,
    val baseRoundScore: Int,
    val finalRoundScore: Int,
    val totalScore: Int,
    val doubled: Boolean,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(SPLASH_DURATION_MS)
                showSplash = false
            }

            SkyjoTheme {
                if (showSplash) {
                    SkyjoSplashScreen()
                } else {
                    SkyjoApp()
                }
            }
        }
    }
}

@Composable
private fun SkyjoTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFFC1D6),
            content = content,
        )
    }
}

@Composable
private fun SkyjoSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFC1D6)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.icon),
            contentDescription = "SKYJO",
            modifier = Modifier.size(width = 260.dp, height = 100.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun SkyjoApp() {
    val context = LocalContext.current
    val savedGames = remember(context) { SavedGameStore(context) }
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.MainMenu) }

    when (val currentScreen = screen) {
        AppScreen.MainMenu -> MainMenuScreen(
            savedGames = savedGames,
            onOpenGame = { gameState -> screen = AppScreen.Game(gameState) },
        )
        is AppScreen.Game -> SkyjoGameScreen(
            initialGameState = currentScreen.initialGameState,
            onGameStateChanged = { gameState -> savedGames.saveUnfinishedGame(gameState) },
            onReturnToMenu = { screen = AppScreen.MainMenu },
        )
    }
}

@Composable
private fun MainMenuScreen(
    savedGames: SavedGameStore,
    onOpenGame: (GameState) -> Unit,
) {
    var unfinishedGame by remember { mutableStateOf<GameState?>(null) }
    var showNewGameConfirmation by remember { mutableStateOf(false) }
    var restoreNewGameFocus by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val newGameFocusRequester = remember { FocusRequester() }

    fun refreshSavedGameAvailability() {
        unfinishedGame = savedGames.loadUnfinishedGame()
    }

    fun createAndOpenNewGame() {
        val newGame = SkyoGame.newGame(humanPlayerName = "You", botCount = 1)
        if (savedGames.replaceUnfinishedGame(newGame)) {
            onOpenGame(newGame)
        } else {
            message = "Could not save the new game. Please try again."
            refreshSavedGameAvailability()
        }
    }

    LaunchedEffect(Unit) {
        refreshSavedGameAvailability()
    }

    LaunchedEffect(showNewGameConfirmation, restoreNewGameFocus) {
        if (!showNewGameConfirmation && restoreNewGameFocus) {
            newGameFocusRequester.requestFocus()
            restoreNewGameFocus = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFC1D6)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(top = 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.icon),
                contentDescription = "SKYJO",
                modifier = Modifier.size(width = 260.dp, height = 100.dp),
                contentScale = ContentScale.Fit,
            )

            Spacer(modifier = Modifier.height(44.dp))

            Column(
                modifier = Modifier.widthIn(max = 260.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                unfinishedGame?.let { savedGame ->
                    Button(
                        onClick = {
                            val latestSavedGame = savedGames.loadUnfinishedGame()
                            if (latestSavedGame == null) {
                                refreshSavedGameAvailability()
                            } else {
                                unfinishedGame = latestSavedGame
                                onOpenGame(latestSavedGame)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Continue Game\n${savedGame.continueGameScoreText()}",
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Button(
                    onClick = {
                        message = null
                        val latestSavedGame = savedGames.loadUnfinishedGame()
                        if (latestSavedGame == null) {
                            unfinishedGame = null
                            createAndOpenNewGame()
                        } else {
                            unfinishedGame = latestSavedGame
                            showNewGameConfirmation = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(newGameFocusRequester),
                ) {
                    Text("New Game")
                }

                message?.let {
                    Text(
                        text = it,
                        color = Color(0xFF41534F),
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            color = Color(0x9941534F),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }

    if (showNewGameConfirmation) {
        NewGameConfirmationDialog(
            onConfirm = {
                showNewGameConfirmation = false
                restoreNewGameFocus = false
                createAndOpenNewGame()
            },
            onDismiss = {
                showNewGameConfirmation = false
                restoreNewGameFocus = true
                refreshSavedGameAvailability()
            },
        )
    }
}

private fun GameState.continueGameScoreText(): String {
    val humanScore = players.firstOrNull { !it.isBot }?.score ?: 0
    val botScore = players.filter { it.isBot }.sumOf { it.score }
    return "($humanScore-$botScore)"
}

@Composable
private fun NewGameConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        dialogFocusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        title = { Text("Start a new game?") },
        text = {
            Text("Another game is currently in progress. Starting a new game will replace it. Are you sure you want to continue?")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .focusRequester(dialogFocusRequester)
                    .focusable(),
            ) {
                Text("Create New Game")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Go Back")
            }
        },
    )
}

@Composable
private fun SkyjoGameScreen(
    initialGameState: GameState,
    onGameStateChanged: (GameState) -> Unit,
    onReturnToMenu: () -> Unit,
) {
    var gameState by remember(initialGameState) { mutableStateOf(initialGameState) }
    var message by remember(initialGameState) { mutableStateOf(messageFor(initialGameState)) }
    var deckBounds by remember { mutableStateOf(Rect.Zero) }
    var discardBounds by remember { mutableStateOf(Rect.Zero) }
    var drawnCardBounds by remember { mutableStateOf(Rect.Zero) }
    var activePileDrag by remember { mutableStateOf<ActivePileDrag?>(null) }
    var humanHeldCardCameFromDeck by remember { mutableStateOf(false) }
    var botDropTarget by remember { mutableStateOf<Rect?>(null) }
    var showRoundEndDialog by remember(initialGameState) { mutableStateOf(false) }
    val gridBounds = remember { mutableStateMapOf<Int, Rect>() }

    fun setGameState(nextState: GameState) {
        gridBounds.clear()
        gameState = nextState
        onGameStateChanged(nextState)
    }

    BackHandler(onBack = onReturnToMenu)
    GameplayFullscreenEffect()

    fun dispatch(action: Action) {
        runCatching { SkyoGame.reduce(gameState, action) }
            .onSuccess { nextState ->
                setGameState(nextState)
                message = messageFor(nextState)
                if (nextState.stage != TurnStage.CHOOSE_SWAP_OR_DISCARD) {
                    humanHeldCardCameFromDeck = false
                }
            }
            .onFailure { error ->
                message = error.message ?: "That move is not allowed."
            }
    }

    fun handleCardDrop(center: Offset) {
        when {
            discardBounds.contains(center) -> {
                val action = if (gameState.drawnCardCameFromDiscard) {
                    Action.ReturnDrawnDiscardCard
                } else {
                    Action.DiscardDrawnCard
                }
                dispatch(action)
            }
            else -> gridBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(center) }
                ?.let { (index, _) -> dispatch(Action.SwapWithGrid(index)) }
                ?: run { message = "Drop it on the discard pile or one of your cards." }
        }
    }

    fun beginPileDrag(action: Action, sourceBounds: Rect) {
        if (sourceBounds == Rect.Zero) return

        runCatching { SkyoGame.reduce(gameState, action) }
            .onSuccess { nextState ->
                val drawn = nextState.drawnCard ?: return@onSuccess
                setGameState(nextState)
                activePileDrag = ActivePileDrag(
                    card = drawn,
                    sourceBounds = sourceBounds,
                )
                humanHeldCardCameFromDeck = action == Action.DrawFromDeck
                message = "${gameState.players[gameState.currentPlayerIndex].name} picked up ${drawn.value}."
            }
            .onFailure { error ->
                message = error.message ?: "That move is not allowed."
            }
    }

    fun startNextRound() {
        runCatching { SkyoGame.startNextRound(gameState) }
            .onSuccess { nextState ->
                activePileDrag = null
                humanHeldCardCameFromDeck = false
                botDropTarget = null
                gridBounds.clear()
                setGameState(nextState)
                message = messageFor(nextState)
            }
            .onFailure { error ->
                message = error.message ?: "Could not start the next round."
            }
    }

    val player = gameState.players[gameState.currentPlayerIndex]
    val isOpeningReveal = gameState.stage == TurnStage.OPENING_REVEAL
    val isBotTurn = player.isBot
    val humanPlayer = gameState.players.first { !it.isBot }
    val opponent = gameState.players.firstOrNull { it.isBot }
    val humanOpeningRevealKey = humanPlayer.grid.count { it.isRevealed }

    LaunchedEffect(gameState.stage, gameState.openingRevealCount, gameState.openingContenderIds, humanOpeningRevealKey) {
        if (!isOpeningReveal || gameState.roundEnded || gameState.gameEnded) {
            return@LaunchedEffect
        }

        var nextState = gameState
        val contenderIds = nextState.openingContenderIds.ifEmpty { nextState.players.map { it.id }.toSet() }
        val botsToReveal = nextState.players.filter { bot ->
            bot.isBot &&
                bot.id in contenderIds &&
                bot.grid.count { it.isRevealed } < nextState.openingRevealCount
        }

        for (bot in botsToReveal) {
            val currentBot = nextState.players.firstOrNull { it.id == bot.id } ?: continue
            val revealIndices = SkyoGame.chooseOpeningBotRevealIndices(
                player = currentBot,
                targetRevealCount = nextState.openingRevealCount,
                random = Random.Default,
            )

            for (revealIndex in revealIndices) {
                delay(Random.nextLong(OPENING_BOT_REVEAL_DELAY_MIN_MS, OPENING_BOT_REVEAL_DELAY_MAX_MS))
                val revealResult = runCatching {
                    SkyoGame.reduce(nextState, Action.RevealOpeningBotGrid(bot.id, revealIndex))
                }

                nextState = revealResult.getOrElse { return@LaunchedEffect }
                setGameState(nextState)
                message = if (nextState.stage == TurnStage.OPENING_REVEAL) {
                    "${bot.name} revealed slot ${revealIndex + 1}."
                } else {
                    messageFor(nextState)
                }

                if (nextState.stage != TurnStage.OPENING_REVEAL) {
                    return@LaunchedEffect
                }
            }
        }
    }

    LaunchedEffect(gameState.currentPlayerIndex, gameState.stage, gameState.roundEnded, gameState.gameEnded) {
        if (isBotTurn && !isOpeningReveal && !gameState.roundEnded && !gameState.gameEnded) {
            var nextState = gameState

            if (nextState.stage == TurnStage.DRAW_OR_TAKE) {
                val drawAction = withContext(Dispatchers.Default) {
                    BotDecisionEngine.chooseAction(nextState).action
                }
                message = "${player.name} is choosing a pile..."
                delay(BOT_DECISION_DELAY_MS)
                nextState = SkyoGame.reduce(nextState, drawAction)
                setGameState(nextState)
                humanHeldCardCameFromDeck = false
                message = if (drawAction == Action.DrawFromDiscard) {
                    "${player.name} took the discard card."
                } else {
                    "${player.name} drew from the deck."
                }
            }

            if (nextState.stage == TurnStage.CHOOSE_SWAP_OR_DISCARD) {
                delay(BOT_CARD_REVIEW_DELAY_MS)
                val drawn = nextState.drawnCard
                val action = withContext(Dispatchers.Default) {
                    BotDecisionEngine.chooseAction(nextState).action
                }
                val swapIndex = (action as? Action.SwapWithGrid)?.index
                if (drawn != null && swapIndex != null) {
                    gridBounds[swapIndex]?.takeIf { drawnCardBounds != Rect.Zero }?.let { target ->
                        message = "${player.name} is moving ${drawn.value} into slot ${swapIndex + 1}..."
                        botDropTarget = target
                        delay(BOT_CARD_DRAG_DURATION_MS + BOT_AFTER_CARD_DRAG_DELAY_MS)
                    }
                    nextState = SkyoGame.reduce(nextState, Action.SwapWithGrid(swapIndex))
                    setGameState(nextState)
                    humanHeldCardCameFromDeck = false
                    botDropTarget = null
                    message = "${player.name} swapped ${drawn.value} into slot ${swapIndex + 1}."
                } else {
                    discardBounds.takeIf { it != Rect.Zero && drawnCardBounds != Rect.Zero }?.let { target ->
                        message = "${player.name} is moving the drawn card to the discard pile..."
                        botDropTarget = target
                        delay(BOT_CARD_DRAG_DURATION_MS + BOT_AFTER_CARD_DRAG_DELAY_MS)
                    }
                    nextState = SkyoGame.reduce(nextState, action)
                    setGameState(nextState)
                    humanHeldCardCameFromDeck = false
                    botDropTarget = null
                    message = if (action == Action.ReturnDrawnDiscardCard) {
                        "${player.name} returned the discard card."
                    } else {
                        "${player.name} discarded the drawn card."
                    }
                }
            }

            if (nextState.stage == TurnStage.TURN_END && nextState.revealRequiredBeforeEndTurn) {
                val revealAction = withContext(Dispatchers.Default) {
                    BotDecisionEngine.chooseAction(nextState).action
                }
                val revealIndex = (revealAction as? Action.RevealGrid)?.index
                if (revealIndex == null) {
                    message = messageFor(nextState)
                    return@LaunchedEffect
                } else {
                    delay(BOT_REVEAL_DELAY_MS)
                    nextState = SkyoGame.reduce(nextState, Action.RevealGrid(revealIndex))
                    setGameState(nextState)
                    message = "${player.name} revealed slot ${revealIndex + 1}."
                }
            }

            if (nextState.stage == TurnStage.TURN_END && !nextState.revealRequiredBeforeEndTurn) {
                delay(BOT_END_TURN_DELAY_MS)
                nextState = SkyoGame.reduce(nextState, Action.EndTurn)
                setGameState(nextState)
                message = messageFor(nextState)
            }
        }
    }

    LaunchedEffect(
        gameState.currentPlayerIndex,
        gameState.stage,
        gameState.revealRequiredBeforeEndTurn,
        gameState.roundEnded,
        gameState.gameEnded,
    ) {
        if (
            !isBotTurn &&
            !isOpeningReveal &&
            !gameState.roundEnded &&
            !gameState.gameEnded &&
            gameState.stage == TurnStage.TURN_END &&
            !gameState.revealRequiredBeforeEndTurn
        ) {
            delay(HUMAN_AUTO_END_TURN_DELAY_MS)
            dispatch(Action.EndTurn)
        }
    }

    LaunchedEffect(gameState.round, gameState.roundEnded, gameState.gameEnded) {
        showRoundEndDialog = false
        if (gameState.roundEnded) {
            delay(ROUND_END_REVEAL_REVIEW_DELAY_MS)
            showRoundEndDialog = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val boardLayout = remember(maxWidth, maxHeight) {
            GameBoardLayout.calculate(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
            )
        }
        val discard = gameState.discardPile.lastOrNull()
        val canUsePiles = !gameState.roundEnded && !gameState.gameEnded && !isBotTurn && !isOpeningReveal
        val canDragPile = canUsePiles && gameState.stage == TurnStage.DRAW_OR_TAKE
        val canDragHeldCard = !isBotTurn &&
            !isOpeningReveal &&
            humanHeldCardCameFromDeck &&
            gameState.stage == TurnStage.CHOOSE_SWAP_OR_DISCARD
        val onDrawFromDeck: () -> Unit = {
            runCatching { SkyoGame.reduce(gameState, Action.DrawFromDeck) }
                .onSuccess { nextState ->
                    setGameState(nextState)
                    humanHeldCardCameFromDeck = true
                    message = messageFor(nextState)
                }
                .onFailure { error ->
                    humanHeldCardCameFromDeck = false
                    message = error.message ?: "That move is not allowed."
                }
        }
        val onHumanCardClick: (Int) -> Unit = { index ->
            when (gameState.stage) {
                TurnStage.OPENING_REVEAL -> dispatch(Action.RevealGrid(index))
                TurnStage.CHOOSE_SWAP_OR_DISCARD -> dispatch(Action.SwapWithGrid(index))
                TurnStage.TURN_END -> dispatch(Action.RevealGrid(index))
                TurnStage.DRAW_OR_TAKE -> message = "Draw or take the discard card first."
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = boardLayout.horizontalPadding,
                    vertical = boardLayout.verticalPadding,
                ),
        ) {
            if (boardLayout.landscape) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(boardLayout.sectionSpacing),
                ) {
                    GameHeader(
                        player = player,
                        roundText = if (gameState.gameEnded) "Game over" else "Round ${gameState.round}",
                        compact = true,
                        scale = boardLayout.uiScale,
                        onReturnToMenu = onReturnToMenu,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        horizontalArrangement = Arrangement.spacedBy(boardLayout.sectionSpacing, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        opponent?.let {
                            PlayerBoard(
                                player = it,
                                title = "${it.name} | Score ${it.score}",
                                enabled = false,
                                layout = boardLayout.botBoard,
                                modifier = Modifier.weight(1f, fill = false),
                                onCardPositioned = { index, bounds ->
                                    if (player.id == it.id) {
                                        gridBounds[index] = bounds
                                    }
                                },
                                onCardClick = {},
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(boardLayout.sectionSpacing),
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF41534F),
                                fontSize = (12f * boardLayout.uiScale).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            PileRow(
                                layout = boardLayout,
                                drawnCard = gameState.drawnCard.takeIf { activePileDrag == null },
                                discard = discard,
                                deckSize = gameState.deck.size,
                                canUsePiles = canUsePiles,
                                canDragPile = canDragPile,
                                canDragHeldCard = canDragHeldCard,
                                botDropTarget = botDropTarget,
                                humanHeldCardCameFromDeck = humanHeldCardCameFromDeck,
                                onDrawnCardPositioned = { drawnCardBounds = it },
                                onHeldDragStart = {
                                    val drawn = gameState.drawnCard
                                    if (drawn != null && drawnCardBounds != Rect.Zero) {
                                        activePileDrag = ActivePileDrag(drawn, drawnCardBounds)
                                    }
                                },
                                onDrag = { dragAmount -> activePileDrag?.moveBy(dragAmount) },
                                onDragEnd = {
                                    activePileDrag?.let { drag ->
                                        handleCardDrop(drag.sourceBounds.center + drag.dragOffset)
                                    }
                                    activePileDrag = null
                                },
                                onDropped = ::handleCardDrop,
                                onDeckPositioned = { deckBounds = it },
                                onDiscardPositioned = { discardBounds = it },
                                onDeckDragStart = { beginPileDrag(Action.DrawFromDeck, deckBounds) },
                                onDiscardDragStart = { beginPileDrag(Action.DrawFromDiscard, discardBounds) },
                                onDrawFromDeck = onDrawFromDeck,
                                onDrawFromDiscard = {
                                    humanHeldCardCameFromDeck = false
                                    dispatch(Action.DrawFromDiscard)
                                },
                            )
                        }

                        PlayerBoard(
                            player = humanPlayer,
                            title = "${humanPlayer.name} | Score ${humanPlayer.score}",
                            enabled = !gameState.roundEnded && !gameState.gameEnded && (!isBotTurn || isOpeningReveal),
                            layout = boardLayout.humanBoard,
                            modifier = Modifier.weight(1f, fill = false),
                            onCardPositioned = { index, bounds ->
                                if (player.id == humanPlayer.id) {
                                    gridBounds[index] = bounds
                                }
                            },
                            onCardClick = onHumanCardClick,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(boardLayout.sectionSpacing),
                ) {
                    GameHeader(
                        player = player,
                        roundText = if (gameState.gameEnded) "Game over" else "Round ${gameState.round}",
                        compact = false,
                        scale = boardLayout.uiScale,
                        onReturnToMenu = onReturnToMenu,
                    )

                    if (opponent != null) {
                        PlayerBoard(
                            player = opponent,
                            title = "${opponent.name} | Score ${opponent.score}",
                            enabled = false,
                            layout = boardLayout.botBoard,
                            onCardPositioned = { index, bounds ->
                                if (player.id == opponent.id) {
                                    gridBounds[index] = bounds
                                }
                            },
                            onCardClick = {},
                        )
                    }

                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF41534F),
                        fontSize = (13f * boardLayout.uiScale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    PileRow(
                        layout = boardLayout,
                        drawnCard = gameState.drawnCard.takeIf { activePileDrag == null },
                        discard = discard,
                        deckSize = gameState.deck.size,
                        canUsePiles = canUsePiles,
                        canDragPile = canDragPile,
                        canDragHeldCard = canDragHeldCard,
                        botDropTarget = botDropTarget,
                        humanHeldCardCameFromDeck = humanHeldCardCameFromDeck,
                        onDrawnCardPositioned = { drawnCardBounds = it },
                        onHeldDragStart = {
                            val drawn = gameState.drawnCard
                            if (drawn != null && drawnCardBounds != Rect.Zero) {
                                activePileDrag = ActivePileDrag(drawn, drawnCardBounds)
                            }
                        },
                        onDrag = { dragAmount -> activePileDrag?.moveBy(dragAmount) },
                        onDragEnd = {
                            activePileDrag?.let { drag ->
                                handleCardDrop(drag.sourceBounds.center + drag.dragOffset)
                            }
                            activePileDrag = null
                        },
                        onDropped = ::handleCardDrop,
                        onDeckPositioned = { deckBounds = it },
                        onDiscardPositioned = { discardBounds = it },
                        onDeckDragStart = { beginPileDrag(Action.DrawFromDeck, deckBounds) },
                        onDiscardDragStart = { beginPileDrag(Action.DrawFromDiscard, discardBounds) },
                        onDrawFromDeck = onDrawFromDeck,
                        onDrawFromDiscard = {
                            humanHeldCardCameFromDeck = false
                            dispatch(Action.DrawFromDiscard)
                        },
                    )

                    PlayerBoard(
                        player = humanPlayer,
                        title = "${humanPlayer.name} | Score ${humanPlayer.score}",
                        enabled = !gameState.roundEnded && !gameState.gameEnded && (!isBotTurn || isOpeningReveal),
                        layout = boardLayout.humanBoard,
                        onCardPositioned = { index, bounds ->
                            if (player.id == humanPlayer.id) {
                                gridBounds[index] = bounds
                            }
                        },
                        onCardClick = onHumanCardClick,
                    )
                }
            }

            activePileDrag?.let { drag ->
                FloatingDraggedCard(drag)
            }
        }
    }

    if (showRoundEndDialog) {
        RoundEndDialog(
            state = gameState,
            onStartNextRound = ::startNextRound,
            onReturnToMenu = onReturnToMenu,
        )
    }
}

@Composable
private fun RoundEndDialog(
    state: GameState,
    onStartNextRound: () -> Unit,
    onReturnToMenu: () -> Unit,
) {
    val dialogFocusRequester = remember { FocusRequester() }
    val scoreLines = remember(state) { state.roundScoreLines() }

    LaunchedEffect(Unit) {
        dialogFocusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        containerColor = Color(0xFFFFC1D6),
        title = {
            Text(
                text = if (state.gameEnded) "Game over" else "Round ${state.round} scores",
                color = Color(0xFF143D35),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                scoreLines.forEach { score ->
                    RoundScoreRow(score)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = if (state.gameEnded) onReturnToMenu else onStartNextRound,
                modifier = Modifier
                    .focusRequester(dialogFocusRequester)
                    .focusable(),
            ) {
                Text(if (state.gameEnded) "Back to Menu" else "Start Next Round")
            }
        },
    )
}

@Composable
private fun RoundScoreRow(score: RoundScoreLine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFA3B7), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = score.playerName,
                color = Color(0xFF143D35),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Text(
                text = "${score.previousTotal} + ${score.finalRoundScore} = ${score.totalScore}",
                color = Color(0xFF36524A),
                fontSize = 13.sp,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (score.doubled) {
                DoublePointsBadge()
            }
            Text(
                text = if (score.doubled) "${score.baseRoundScore} -> ${score.finalRoundScore}" else "${score.finalRoundScore}",
                color = Color(0xFF143D35),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DoublePointsBadge() {
    val badgeScale = remember { Animatable(0.75f) }

    LaunchedEffect(Unit) {
        badgeScale.animateTo(
            targetValue = 1.25f,
            animationSpec = tween(
                durationMillis = DOUBLE_POINTS_BADGE_ANIMATION_MS.toInt(),
                easing = FastOutSlowInEasing,
            ),
        )
        badgeScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = DOUBLE_POINTS_BADGE_SETTLE_MS.toInt(),
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Box(
        modifier = Modifier
            .scale(badgeScale.value)
            .background(Color(0xFFFFE45C), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "2x",
            color = Color(0xFF6147A8),
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun BoardCard(
    card: Card,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onPositioned: (Rect) -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            .clickable(enabled = enabled && !card.isCleared, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            card.isCleared -> ClearedSlot()
            card.isRevealed -> Image(
                painter = painterResource(cardImageRes(card.value)),
                contentDescription = "Card ${card.value}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            else -> CardBack()
        }
    }
}

@Composable
private fun CardBack() {
    Image(
        painter = painterResource(R.drawable.card_back),
        contentDescription = "Hidden card",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
private fun ClearedSlot() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE1DED6))
            .border(1.dp, Color(0xFFCCC6BA), RoundedCornerShape(6.dp)),
    )
}

@Composable
private fun GameHeader(
    player: PlayerState,
    roundText: String,
    compact: Boolean,
    scale: Float,
    onReturnToMenu: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compact) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GameLogo(compact = true, scale = scale)
                GameTurnText(player = player, compact = true, scale = scale)
            }
        } else {
            Column {
                GameLogo(compact = false, scale = scale)
                GameTurnText(player = player, compact = false, scale = scale)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = roundText,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF143D35),
                fontSize = (14f * scale).sp,
                maxLines = 1,
            )
            IconButton(
                onClick = onReturnToMenu,
                modifier = Modifier
                    .size((if (compact) 36f else 40f).dp * scale)
                    .clip(CircleShape)
                    .background(Color(0xFF6147A8)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.home),
                    contentDescription = "Home",
                    tint = Color.White,
                    modifier = Modifier.size((if (compact) 20f else 22f).dp * scale),
                )
            }
        }
    }
}

@Composable
private fun GameLogo(
    compact: Boolean,
    scale: Float,
) {
    Image(
        painter = painterResource(R.drawable.icon),
        contentDescription = "SKYJO",
        modifier = Modifier.size(
            width = (if (compact) 96f else 118f).dp * scale,
            height = (if (compact) 36f else 45f).dp * scale,
        ),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun GameTurnText(
    player: PlayerState,
    compact: Boolean,
    scale: Float,
) {
    Text(
        text = "${player.name}'s turn | Score ${player.score}",
        fontSize = ((if (compact) 12f else 15f) * scale).sp,
        color = Color(0xFF36524A),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PileRow(
    layout: GameBoardLayout,
    drawnCard: Card?,
    discard: Card?,
    deckSize: Int,
    canUsePiles: Boolean,
    canDragPile: Boolean,
    canDragHeldCard: Boolean,
    botDropTarget: Rect?,
    humanHeldCardCameFromDeck: Boolean,
    onDrawnCardPositioned: (Rect) -> Unit,
    onHeldDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDropped: (Offset) -> Unit,
    onDeckPositioned: (Rect) -> Unit,
    onDiscardPositioned: (Rect) -> Unit,
    onDeckDragStart: () -> Unit,
    onDiscardDragStart: () -> Unit,
    onDrawFromDeck: () -> Unit,
    onDrawFromDiscard: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(layout.pileSpacing),
            verticalAlignment = Alignment.Top,
        ) {
            HeldCardSlot(
                card = drawnCard,
                cardWidth = layout.pileCardWidth,
                cardHeight = layout.pileCardHeight,
                slotHeight = layout.pileSlotHeight,
                draggable = canDragHeldCard && humanHeldCardCameFromDeck,
                animatedDropTarget = botDropTarget,
                onPositioned = onDrawnCardPositioned,
                onDragStart = onHeldDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDropped = onDropped,
            )
            PileCard(
                label = "Deck",
                value = deckSize.toString(),
                cardWidth = layout.pileCardWidth,
                cardHeight = layout.pileCardHeight,
                labelGap = layout.pileLabelGap,
                labelFontSize = layout.pileLabelFontSize,
                enabled = canUsePiles,
                draggable = canDragPile,
                onPositioned = onDeckPositioned,
                onDragStart = onDeckDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onClick = onDrawFromDeck,
            )
            PileCard(
                label = "Discard",
                value = discard?.value?.toString() ?: "-",
                imageRes = discard?.let { cardImageRes(it.value) },
                cardWidth = layout.pileCardWidth,
                cardHeight = layout.pileCardHeight,
                labelGap = layout.pileLabelGap,
                labelFontSize = layout.pileLabelFontSize,
                enabled = canUsePiles,
                draggable = canDragPile,
                onPositioned = onDiscardPositioned,
                onDragStart = onDiscardDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onClick = onDrawFromDiscard,
            )
        }
    }
}

@Composable
private fun PileCard(
    label: String,
    value: String,
    imageRes: Int? = null,
    cardWidth: Dp,
    cardHeight: Dp,
    labelGap: Dp,
    labelFontSize: androidx.compose.ui.unit.TextUnit,
    enabled: Boolean = true,
    draggable: Boolean = false,
    onPositioned: (Rect) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onClick: () -> Unit,
) {
    val currentDraggable by rememberUpdatedState(draggable)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = cardWidth, height = cardHeight)
                .clip(RoundedCornerShape(6.dp))
                .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
                .pointerInput(Unit) {
                    var isActivePileDrag = false
                    detectDragGestures(
                        onDragStart = {
                            isActivePileDrag = currentDraggable
                            if (isActivePileDrag) {
                                currentOnDragStart()
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (isActivePileDrag) {
                                change.consume()
                                currentOnDrag(dragAmount)
                            }
                        },
                        onDragEnd = {
                            if (isActivePileDrag) {
                                currentOnDragEnd()
                            }
                            isActivePileDrag = false
                        },
                        onDragCancel = {
                            if (isActivePileDrag) {
                                currentOnDragEnd()
                            }
                            isActivePileDrag = false
                        },
                    )
                }
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (imageRes == null) {
                CardBack()
            } else {
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = "$label $value",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(modifier = Modifier.height(labelGap))
        Text(
            text = if (label == "Deck") "$label ($value)" else label,
            fontSize = labelFontSize,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF143D35),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeldCardSlot(
    modifier: Modifier = Modifier,
    card: Card?,
    cardWidth: Dp,
    cardHeight: Dp,
    slotHeight: Dp,
    draggable: Boolean,
    animatedDropTarget: Rect?,
    onPositioned: (Rect) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDropped: (Offset) -> Unit,
) {
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    val botDragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val currentDraggable by rememberUpdatedState(draggable)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    LaunchedEffect(animatedDropTarget, cardBounds) {
        val target = animatedDropTarget ?: run {
            botDragOffset.snapTo(Offset.Zero)
            return@LaunchedEffect
        }

        if (cardBounds != Rect.Zero) {
            botDragOffset.snapTo(Offset.Zero)
            botDragOffset.animateTo(
                targetValue = target.center - cardBounds.center,
                animationSpec = tween(
                    durationMillis = BOT_CARD_DRAG_DURATION_MS.toInt(),
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    Box(
        modifier = modifier
            .zIndex(1f)
            .size(width = cardWidth, height = slotHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .size(width = cardWidth, height = cardHeight)
                .offset {
                    IntOffset(
                        botDragOffset.value.x.roundToInt(),
                        botDragOffset.value.y.roundToInt(),
                    )
                }
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x1AFF335D))
                .border(
                    width = if (card == null) 2.dp else 3.dp,
                    color = if (card == null) Color(0xFFFF335D) else Color(0xFFFFE45C),
                    shape = RoundedCornerShape(6.dp),
                )
                .onGloballyPositioned {
                    if (botDragOffset.value == Offset.Zero) {
                        cardBounds = it.boundsInRoot()
                        onPositioned(cardBounds)
                    }
                }
                .pointerInput(Unit) {
                    var isActiveDrag = false
                    detectDragGestures(
                        onDragStart = {
                            isActiveDrag = currentDraggable
                            if (isActiveDrag) {
                                currentOnDragStart()
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (isActiveDrag) {
                                change.consume()
                                currentOnDrag(dragAmount)
                            }
                        },
                        onDragEnd = {
                            if (isActiveDrag) {
                                currentOnDragEnd()
                            } else if (draggable) {
                                onDropped(cardBounds.center)
                            }
                            isActiveDrag = false
                        },
                        onDragCancel = {
                            if (isActiveDrag) {
                                currentOnDragEnd()
                            }
                            isActiveDrag = false
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (card != null) {
                Image(
                    painter = painterResource(cardImageRes(card.value)),
                    contentDescription = "Held card ${card.value}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun FloatingDraggedCard(drag: ActivePileDrag) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .zIndex(2f)
            .offset {
                IntOffset(
                    (drag.sourceBounds.left + drag.dragOffset.x).roundToInt(),
                    (drag.sourceBounds.top + drag.dragOffset.y).roundToInt(),
                )
            }
            .size(
                width = with(density) { drag.sourceBounds.width.toDp() },
                height = with(density) { drag.sourceBounds.height.toDp() },
            )
            .clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(cardImageRes(drag.card.value)),
            contentDescription = "Dragged card ${drag.card.value}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

private data class BoardDimensions(
    val cardWidth: Dp,
    val cardHeight: Dp,
    val spacing: Dp,
    val padding: Dp,
    val maxWidth: Dp,
    val backgroundColor: Color,
    val titleFontSize: androidx.compose.ui.unit.TextUnit,
)

private data class GameBoardLayout(
    val landscape: Boolean,
    val uiScale: Float,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val sectionSpacing: Dp,
    val pileSpacing: Dp,
    val pileCardWidth: Dp,
    val pileCardHeight: Dp,
    val pileSlotHeight: Dp,
    val pileLabelGap: Dp,
    val pileLabelFontSize: androidx.compose.ui.unit.TextUnit,
    val botBoard: BoardDimensions,
    val humanBoard: BoardDimensions,
) {
    companion object {
        fun calculate(
            maxWidth: Dp,
            maxHeight: Dp,
        ): GameBoardLayout {
            val width = maxWidth.value
            val height = maxHeight.value
            val landscape = width > height
            val horizontalPadding = if (width < 360f || landscape) 10f else 16f
            val verticalPadding = if (height < 620f || landscape) 8f else 16f
            val sectionSpacing = if (height < 620f || landscape) 5f else 8f
            val humanRows = BOARD_GRID_ROWS
            val humanColumns = BOARD_GRID_COLUMNS
            val botRows = BOARD_GRID_ROWS
            val botColumns = BOARD_GRID_COLUMNS

            val humanGridBaseWidth = humanColumns * HUMAN_CARD_WIDTH_DP + max(0, humanColumns - 1) * HUMAN_GRID_SPACING_DP
            val botGridBaseWidth = botColumns * BOT_CARD_WIDTH_DP + max(0, botColumns - 1) * BOT_GRID_SPACING_DP
            val pileRowBaseWidth = 3f * PILE_CARD_WIDTH_DP + 2f * PILE_SPACING_DP

            val scaleByWidth = if (landscape) {
                val totalBaseWidth = humanGridBaseWidth + botGridBaseWidth + pileRowBaseWidth + 2f * sectionSpacing + 2f * HUMAN_BOARD_PADDING_DP
                ((width - 2f * horizontalPadding) / totalBaseWidth).coerceAtMost(1.05f)
            } else {
                ((width - 2f * horizontalPadding - 2f * HUMAN_BOARD_PADDING_DP) / humanGridBaseWidth).coerceAtMost(1.05f)
            }

            val humanGridBaseHeight = humanRows * HUMAN_CARD_HEIGHT_DP + max(0, humanRows - 1) * HUMAN_GRID_SPACING_DP
            val botGridBaseHeight = botRows * BOT_CARD_HEIGHT_DP + max(0, botRows - 1) * BOT_GRID_SPACING_DP
            val scaleByHeight = if (landscape) {
                val baseHeight = 2f * verticalPadding +
                    LANDSCAPE_HEADER_HEIGHT_DP +
                    sectionSpacing +
                    BOARD_TITLE_HEIGHT_DP +
                    2f * HUMAN_BOARD_PADDING_DP +
                    max(humanGridBaseHeight, botGridBaseHeight) +
                    LAYOUT_HEIGHT_RESERVE_DP
                (height / baseHeight).coerceAtMost(1.05f)
            } else {
                val baseHeight = 2f * verticalPadding +
                    PORTRAIT_HEADER_HEIGHT_DP +
                    MESSAGE_HEIGHT_DP +
                    4f * sectionSpacing +
                    2f * BOARD_TITLE_HEIGHT_DP +
                    PILE_LABEL_GAP_DP +
                    PILE_LABEL_HEIGHT_DP +
                    2f * HUMAN_BOARD_PADDING_DP +
                    humanGridBaseHeight +
                    botGridBaseHeight +
                    PILE_CARD_HEIGHT_DP +
                    LAYOUT_HEIGHT_RESERVE_DP
                (height / baseHeight).coerceAtMost(1.05f)
            }
            val scale = min(scaleByWidth, scaleByHeight).coerceAtLeast(0.34f)

            fun scaled(value: Float): Dp = (value * scale).dp

            val humanCardWidth = scaled(HUMAN_CARD_WIDTH_DP)
            val humanCardHeight = humanCardWidth / CARD_ASPECT_RATIO
            val botCardWidth = scaled(BOT_CARD_WIDTH_DP)
            val botCardHeight = botCardWidth / CARD_ASPECT_RATIO
            val pileCardWidth = scaled(PILE_CARD_WIDTH_DP)
            val pileCardHeight = pileCardWidth / CARD_ASPECT_RATIO

            return GameBoardLayout(
                landscape = landscape,
                uiScale = scale,
                horizontalPadding = scaled(horizontalPadding),
                verticalPadding = scaled(verticalPadding),
                sectionSpacing = scaled(sectionSpacing),
                pileSpacing = scaled(PILE_SPACING_DP),
                pileCardWidth = pileCardWidth,
                pileCardHeight = pileCardHeight,
                pileSlotHeight = pileCardHeight + scaled(20f),
                pileLabelGap = scaled(PILE_LABEL_GAP_DP),
                pileLabelFontSize = (12f * scale).sp,
                botBoard = BoardDimensions(
                    cardWidth = botCardWidth,
                    cardHeight = botCardHeight,
                    spacing = scaled(BOT_GRID_SPACING_DP),
                    padding = 0.dp,
                    maxWidth = scaled(botGridBaseWidth),
                    backgroundColor = Color.Transparent,
                    titleFontSize = (11f * scale).sp,
                ),
                humanBoard = BoardDimensions(
                    cardWidth = humanCardWidth,
                    cardHeight = humanCardHeight,
                    spacing = scaled(HUMAN_GRID_SPACING_DP),
                    padding = scaled(HUMAN_BOARD_PADDING_DP),
                    maxWidth = scaled(humanGridBaseWidth + 2f * HUMAN_BOARD_PADDING_DP),
                    backgroundColor = Color(0xFFFFA3B7),
                    titleFontSize = (14f * scale).sp,
                ),
            )
        }
    }
}

@Composable
private fun PlayerBoard(
    player: PlayerState,
    title: String,
    enabled: Boolean,
    layout: BoardDimensions,
    modifier: Modifier = Modifier,
    onCardPositioned: (Int, Rect) -> Unit,
    onCardClick: (Int) -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(max = layout.maxWidth)
            .fillMaxWidth()
            .background(
                color = layout.backgroundColor,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(layout.padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(layout.spacing),
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            fontSize = layout.titleFontSize,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF143D35),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BoardGrid(
            cards = player.grid,
            enabled = enabled,
            layout = layout,
            onCardPositioned = onCardPositioned,
            onCardClick = onCardClick,
        )
    }
}

@Composable
private fun BoardGrid(
    cards: List<Card>,
    enabled: Boolean,
    layout: BoardDimensions,
    onCardPositioned: (Int, Rect) -> Unit,
    onCardClick: (Int) -> Unit,
) {
    val visibleRows = (0 until BOARD_GRID_ROWS).filter { row ->
        (0 until BOARD_GRID_COLUMNS).any { column ->
            !cards[row * BOARD_GRID_COLUMNS + column].isCleared
        }
    }
    val visibleColumns = (0 until BOARD_GRID_COLUMNS).filter { column ->
        (0 until BOARD_GRID_ROWS).any { row ->
            !cards[row * BOARD_GRID_COLUMNS + column].isCleared
        }
    }
    val rowsToMeasure = visibleRows.size.coerceAtLeast(1)
    val gridHeight = layout.cardHeight * rowsToMeasure + layout.spacing * (rowsToMeasure - 1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(layout.spacing, Alignment.CenterVertically),
    ) {
        visibleRows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(layout.spacing),
            ) {
                visibleColumns.forEach { column ->
                    val index = row * BOARD_GRID_COLUMNS + column
                    val card = cards[index]
                    if (!card.isCleared) {
                        BoardCard(
                            card = card,
                            enabled = enabled,
                            modifier = Modifier.size(width = layout.cardWidth, height = layout.cardHeight),
                            onPositioned = { bounds -> onCardPositioned(index, bounds) },
                            onClick = { onCardClick(index) },
                        )
                    } else {
                        Spacer(
                            modifier = Modifier.size(
                                width = layout.cardWidth,
                                height = layout.cardHeight,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameplayFullscreenEffect() {
    val view = LocalView.current

    DisposableEffect(view) {
        val window = (view.context as? ComponentActivity)?.window
        if (window == null) {
            onDispose {}
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }

            onDispose {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private const val BOT_DECISION_DELAY_MS = 1400L
private const val BOT_CARD_REVIEW_DELAY_MS = 1300L
private const val BOT_CARD_DRAG_DURATION_MS = 1100L
private const val BOT_AFTER_CARD_DRAG_DELAY_MS = 450L
private const val BOT_REVEAL_DELAY_MS = 1000L
private const val BOT_END_TURN_DELAY_MS = 1200L
private const val HUMAN_AUTO_END_TURN_DELAY_MS = 650L
private const val OPENING_BOT_REVEAL_DELAY_MIN_MS = 450L
private const val OPENING_BOT_REVEAL_DELAY_MAX_MS = 1200L
private const val SPLASH_DURATION_MS = 1200L
private const val ROUND_END_REVEAL_REVIEW_DELAY_MS = 3000L
private const val DOUBLE_POINTS_BADGE_ANIMATION_MS = 420L
private const val DOUBLE_POINTS_BADGE_SETTLE_MS = 220L
private const val BOARD_GRID_COLUMNS = 4
private const val BOARD_GRID_ROWS = 3
private const val CARD_ASPECT_RATIO = 1024f / 1600f
private const val HUMAN_CARD_WIDTH_DP = 72f
private const val HUMAN_CARD_HEIGHT_DP = HUMAN_CARD_WIDTH_DP / CARD_ASPECT_RATIO
private const val HUMAN_GRID_SPACING_DP = 8f
private const val HUMAN_BOARD_PADDING_DP = 10f
private const val BOT_CARD_WIDTH_DP = 46f
private const val BOT_CARD_HEIGHT_DP = BOT_CARD_WIDTH_DP / CARD_ASPECT_RATIO
private const val BOT_GRID_SPACING_DP = 4f
private const val PILE_CARD_WIDTH_DP = 62f
private const val PILE_CARD_HEIGHT_DP = PILE_CARD_WIDTH_DP / CARD_ASPECT_RATIO
private const val PILE_SPACING_DP = 12f
private const val PILE_LABEL_GAP_DP = 4f
private const val PILE_LABEL_HEIGHT_DP = 22f
private const val BOARD_TITLE_HEIGHT_DP = 18f
private const val MESSAGE_HEIGHT_DP = 20f
private const val PORTRAIT_HEADER_HEIGHT_DP = 64f
private const val LANDSCAPE_HEADER_HEIGHT_DP = 38f
private const val LAYOUT_HEIGHT_RESERVE_DP = 56f

private fun GameState.roundScoreLines(): List<RoundScoreLine> {
    val baseScores = players.map { player -> SkyoGame.scoreGrid(player.grid) }
    val finishingIndex = roundFinisherIndex
    val finishingPlayerHasLowestScore = finishingIndex == null ||
        baseScores.withIndex().all { (index, score) ->
            index == finishingIndex || baseScores[finishingIndex] < score
        }

    return players.mapIndexed { index, player ->
        val doubled = index == finishingIndex && !finishingPlayerHasLowestScore
        val baseRoundScore = baseScores[index]
        val finalRoundScore = if (doubled) baseRoundScore * 2 else baseRoundScore

        RoundScoreLine(
            playerName = player.name,
            previousTotal = player.score - finalRoundScore,
            baseRoundScore = baseRoundScore,
            finalRoundScore = finalRoundScore,
            totalScore = player.score,
            doubled = doubled,
        )
    }
}

private fun messageFor(state: GameState): String {
    if (state.gameEnded) {
        val losers = state.players.filter { it.hasLost }.joinToString { it.name }
        return "$losers reached 100 points."
    }

    if (state.roundEnded) {
        return "Round over."
    }

    if (state.roundFinisherIndex != null) {
        return "${state.finalTurnsRemaining} final turn(s) remaining."
    }

    return when (state.stage) {
        TurnStage.OPENING_REVEAL -> {
            val human = state.players.first { !it.isBot }
            val revealed = human.grid.count { it.isRevealed }
            val remaining = (state.openingRevealCount - revealed).coerceAtLeast(0)
            if (remaining > 1) {
                "Reveal $remaining cards to decide who starts."
            } else {
                "Reveal 1 card to decide who starts."
            }
        }
        TurnStage.DRAW_OR_TAKE -> "Draw from the deck or take the discard card."
        TurnStage.CHOOSE_SWAP_OR_DISCARD -> "Tap a grid card to swap, or discard and reveal one hidden card."
        TurnStage.TURN_END -> if (state.revealRequiredBeforeEndTurn) {
            "Reveal one hidden card before ending your turn."
        } else {
            "End your turn."
        }
    }
}

private fun cardImageRes(value: Int): Int = when (value) {
    -2 -> R.drawable.card_neg2
    -1 -> R.drawable.card_neg1
    0 -> R.drawable.card_00
    1 -> R.drawable.card_01
    2 -> R.drawable.card_02
    3 -> R.drawable.card_03
    4 -> R.drawable.card_04
    5 -> R.drawable.card_05
    6 -> R.drawable.card_06
    7 -> R.drawable.card_07
    8 -> R.drawable.card_08
    9 -> R.drawable.card_09
    10 -> R.drawable.card_10
    11 -> R.drawable.card_11
    12 -> R.drawable.card_12
    else -> error("Unsupported card value: $value")
}

private sealed interface AppScreen {
    data object MainMenu : AppScreen
    data class Game(val initialGameState: GameState) : AppScreen
}

private class SavedGameStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadUnfinishedGame(): GameState? {
        val encoded = preferences.getString(KEY_ACTIVE_GAME, null) ?: return null
        return runCatching {
            val root = JSONObject(encoded)
            if (root.optInt("version") != SAVE_VERSION) return null

            val state = root.getJSONObject("state").toGameState()
            if (state.gameEnded || !state.isRestorable()) {
                clear()
                null
            } else {
                state
            }
        }.getOrElse {
            clear()
            null
        }
    }

    fun saveUnfinishedGame(state: GameState): Boolean {
        if (state.gameEnded) {
            return clear()
        }

        return replaceUnfinishedGame(state)
    }

    fun replaceUnfinishedGame(state: GameState): Boolean {
        val encoded = JSONObject()
            .put("version", SAVE_VERSION)
            .put("state", state.toJson())
            .toString()

        return preferences.edit()
            .putString(KEY_ACTIVE_GAME, encoded)
            .commit()
    }

    private fun clear(): Boolean = preferences.edit()
        .remove(KEY_ACTIVE_GAME)
        .commit()

    private fun GameState.isRestorable(): Boolean {
        if (players.isEmpty()) return false
        if (currentPlayerIndex !in players.indices) return false
        if (round < 1) return false
        if (finalTurnsRemaining < 0) return false
        if (openingRevealCount < 2) return false
        if (openingContenderIds.any { contenderId -> players.none { it.id == contenderId } }) return false
        if (players.any { it.grid.size != GRID_SIZE }) return false
        if (stage == TurnStage.CHOOSE_SWAP_OR_DISCARD && drawnCard == null) return false

        val allCards = players.flatMap { it.grid } + deck + discardPile + listOfNotNull(drawnCard)
        return allCards.all { it.value in VALID_CARD_VALUES }
    }

    private fun GameState.toJson(): JSONObject = JSONObject()
        .put("players", players.toJsonArray { it.toJson() })
        .put("deck", deck.toJsonArray { it.toJson() })
        .put("discardPile", discardPile.toJsonArray { it.toJson() })
        .put("currentPlayerIndex", currentPlayerIndex)
        .put("stage", stage.name)
        .put("drawnCard", drawnCard?.toJson() ?: JSONObject.NULL)
        .put("drawnCardCameFromDiscard", drawnCardCameFromDiscard)
        .put("revealRequiredBeforeEndTurn", revealRequiredBeforeEndTurn)
        .put("round", round)
        .put("roundFinisherIndex", roundFinisherIndex ?: JSONObject.NULL)
        .put("finalTurnsRemaining", finalTurnsRemaining)
        .put("openingRevealCount", openingRevealCount)
        .put("openingContenderIds", JSONArray().also { array ->
            openingContenderIds.forEach { array.put(it) }
        })
        .put("roundEnded", roundEnded)
        .put("gameEnded", gameEnded)

    private fun JSONObject.toGameState(): GameState = GameState(
        players = getJSONArray("players").toList { getJSONObject(it).toPlayerState() },
        deck = getJSONArray("deck").toList { getJSONObject(it).toCard() },
        discardPile = getJSONArray("discardPile").toList { getJSONObject(it).toCard() },
        currentPlayerIndex = getInt("currentPlayerIndex"),
        stage = TurnStage.valueOf(getString("stage")),
        drawnCard = if (isNull("drawnCard")) null else getJSONObject("drawnCard").toCard(),
        drawnCardCameFromDiscard = optBoolean("drawnCardCameFromDiscard", false),
        revealRequiredBeforeEndTurn = getBoolean("revealRequiredBeforeEndTurn"),
        round = getInt("round"),
        roundFinisherIndex = if (isNull("roundFinisherIndex")) null else getInt("roundFinisherIndex"),
        finalTurnsRemaining = getInt("finalTurnsRemaining"),
        openingRevealCount = optInt("openingRevealCount", 2),
        openingContenderIds = optJSONArray("openingContenderIds")
            ?.toList { getInt(it) }
            ?.toSet()
            ?: emptySet(),
        roundEnded = getBoolean("roundEnded"),
        gameEnded = getBoolean("gameEnded"),
    )

    private fun PlayerState.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("isBot", isBot)
        .put("grid", grid.toJsonArray { it.toJson() })
        .put("score", score)
        .put("hasLost", hasLost)

    private fun JSONObject.toPlayerState(): PlayerState = PlayerState(
        id = getInt("id"),
        name = getString("name"),
        isBot = getBoolean("isBot"),
        grid = getJSONArray("grid").toList { getJSONObject(it).toCard() },
        score = getInt("score"),
        hasLost = getBoolean("hasLost"),
    )

    private fun Card.toJson(): JSONObject = JSONObject()
        .put("value", value)
        .put("isRevealed", isRevealed)
        .put("isCleared", isCleared)

    private fun JSONObject.toCard(): Card = Card(
        value = getInt("value"),
        isRevealed = getBoolean("isRevealed"),
        isCleared = getBoolean("isCleared"),
    )

    private fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        forEach { array.put(transform(it)) }
        return array
    }

    private fun <T> JSONArray.toList(transform: JSONArray.(Int) -> T): List<T> =
        List(length()) { index -> transform(index) }

    private companion object {
        private const val PREFERENCES_NAME = "skyjo_saved_games"
        private const val KEY_ACTIVE_GAME = "active_game"
        private const val SAVE_VERSION = 1
        private const val GRID_SIZE = 12
        private val VALID_CARD_VALUES = -2..12
    }
}
