package com.skyo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.skyo.game.Action
import com.skyo.game.Card
import com.skyo.game.GameState
import com.skyo.game.PlayerState
import com.skyo.game.SkyoGame
import com.skyo.game.TurnStage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class ActivePileDrag(
    val card: Card,
    val sourceBounds: Rect,
    val dragOffset: Offset,
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
                    SkyjoGameScreen()
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
private fun SkyjoGameScreen() {
    var gameState by remember { mutableStateOf(SkyoGame.newGame(humanPlayerName = "You", botCount = 1)) }
    var message by remember { mutableStateOf("Draw from the deck or take the discard card.") }
    var deckBounds by remember { mutableStateOf(Rect.Zero) }
    var discardBounds by remember { mutableStateOf(Rect.Zero) }
    var drawnCardBounds by remember { mutableStateOf(Rect.Zero) }
    var activePileDrag by remember { mutableStateOf<ActivePileDrag?>(null) }
    var humanHeldCardCameFromDeck by remember { mutableStateOf(false) }
    var botDropTarget by remember { mutableStateOf<Rect?>(null) }
    val gridBounds = remember { mutableStateMapOf<Int, Rect>() }

    fun dispatch(action: Action) {
        runCatching { SkyoGame.reduce(gameState, action) }
            .onSuccess { nextState ->
                gameState = nextState
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
            discardBounds.contains(center) -> dispatch(Action.DiscardDrawnCard)
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
                gameState = nextState
                activePileDrag = ActivePileDrag(
                    card = drawn,
                    sourceBounds = sourceBounds,
                    dragOffset = Offset.Zero,
                )
                humanHeldCardCameFromDeck = action == Action.DrawFromDeck
                message = "${gameState.players[gameState.currentPlayerIndex].name} picked up ${drawn.value}."
            }
            .onFailure { error ->
                message = error.message ?: "That move is not allowed."
            }
    }

    val player = gameState.players[gameState.currentPlayerIndex]
    val isBotTurn = player.isBot
    val humanPlayer = gameState.players.first { !it.isBot }
    val opponent = gameState.players.firstOrNull { it.isBot }

    LaunchedEffect(gameState.currentPlayerIndex, gameState.stage, gameState.roundEnded, gameState.gameEnded) {
        if (isBotTurn && !gameState.roundEnded && !gameState.gameEnded) {
            var nextState = gameState

            if (nextState.stage == TurnStage.DRAW_OR_TAKE) {
                val drawAction = chooseBotDrawAction(nextState)
                message = "${player.name} is choosing a pile..."
                delay(BOT_DECISION_DELAY_MS)
                nextState = SkyoGame.reduce(nextState, drawAction)
                gameState = nextState
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
                val swapIndex = chooseBotSwapIndex(nextState)
                if (drawn != null && swapIndex != null) {
                    gridBounds[swapIndex]?.takeIf { drawnCardBounds != Rect.Zero }?.let { target ->
                        message = "${player.name} is moving ${drawn.value} into slot ${swapIndex + 1}..."
                        botDropTarget = target
                        delay(BOT_CARD_DRAG_DURATION_MS + BOT_AFTER_CARD_DRAG_DELAY_MS)
                    }
                    nextState = SkyoGame.reduce(nextState, Action.SwapWithGrid(swapIndex))
                    gameState = nextState
                    humanHeldCardCameFromDeck = false
                    botDropTarget = null
                    message = "${player.name} swapped ${drawn.value} into slot ${swapIndex + 1}."
                } else {
                    discardBounds.takeIf { it != Rect.Zero && drawnCardBounds != Rect.Zero }?.let { target ->
                        message = "${player.name} is moving the drawn card to the discard pile..."
                        botDropTarget = target
                        delay(BOT_CARD_DRAG_DURATION_MS + BOT_AFTER_CARD_DRAG_DELAY_MS)
                    }
                    nextState = SkyoGame.reduce(nextState, Action.DiscardDrawnCard)
                    gameState = nextState
                    humanHeldCardCameFromDeck = false
                    botDropTarget = null
                    message = "${player.name} discarded the drawn card."
                    delay(BOT_REVEAL_DELAY_MS)
                    chooseBotRevealIndex(nextState)?.let { revealIndex ->
                        nextState = SkyoGame.reduce(nextState, Action.RevealGrid(revealIndex))
                        gameState = nextState
                        message = "${player.name} revealed slot ${revealIndex + 1}."
                    }
                }
            }

            if (nextState.stage == TurnStage.TURN_END) {
                delay(BOT_END_TURN_DELAY_MS)
                nextState = SkyoGame.reduce(nextState, Action.EndTurn)
                gameState = nextState
                message = messageFor(nextState)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Image(
                        painter = painterResource(R.drawable.icon),
                        contentDescription = "SKYJO",
                        modifier = Modifier.size(width = 118.dp, height = 45.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        text = "${player.name}'s turn | Score ${player.score}",
                        fontSize = 15.sp,
                        color = Color(0xFF36524A),
                    )
                }

                Text(
                    text = if (gameState.gameEnded) "Game over" else "Round ${gameState.round}",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF143D35),
                )
            }

            if (opponent != null) {
                PlayerBoard(
                    player = opponent,
                    title = "${opponent.name} | Score ${opponent.score}",
                    enabled = false,
                    compact = true,
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
                fontSize = 13.sp,
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    HeldCardSlot(
                        card = gameState.drawnCard.takeIf { activePileDrag == null },
                        draggable = !isBotTurn &&
                            humanHeldCardCameFromDeck &&
                            gameState.stage == TurnStage.CHOOSE_SWAP_OR_DISCARD,
                        animatedDropTarget = botDropTarget,
                        onPositioned = { bounds ->
                            drawnCardBounds = bounds
                        },
                        onDragStart = {
                            val drawn = gameState.drawnCard
                            if (drawn != null && drawnCardBounds != Rect.Zero) {
                                activePileDrag = ActivePileDrag(
                                    card = drawn,
                                    sourceBounds = drawnCardBounds,
                                    dragOffset = Offset.Zero,
                                )
                            }
                        },
                        onDrag = { dragAmount ->
                            activePileDrag = activePileDrag?.let {
                                it.copy(dragOffset = it.dragOffset + dragAmount)
                            }
                        },
                        onDragEnd = {
                            activePileDrag?.let { drag ->
                                handleCardDrop(drag.sourceBounds.center + drag.dragOffset)
                            }
                            activePileDrag = null
                        },
                        onDropped = ::handleCardDrop,
                    )
                    PileCard(
                        label = "Deck",
                        value = gameState.deck.size.toString(),
                        compact = true,
                        enabled = !isBotTurn,
                        draggable = !isBotTurn && gameState.stage == TurnStage.DRAW_OR_TAKE,
                        onPositioned = { deckBounds = it },
                        onDragStart = { beginPileDrag(Action.DrawFromDeck, deckBounds) },
                        onDrag = { dragAmount ->
                            activePileDrag = activePileDrag?.let {
                                it.copy(dragOffset = it.dragOffset + dragAmount)
                            }
                        },
                        onDragEnd = {
                            activePileDrag?.let { drag ->
                                handleCardDrop(drag.sourceBounds.center + drag.dragOffset)
                            }
                            activePileDrag = null
                        },
                        onClick = {
                            runCatching { SkyoGame.reduce(gameState, Action.DrawFromDeck) }
                                .onSuccess { nextState ->
                                    gameState = nextState
                                    humanHeldCardCameFromDeck = true
                                    message = messageFor(nextState)
                                }
                                .onFailure { error ->
                                    humanHeldCardCameFromDeck = false
                                    message = error.message ?: "That move is not allowed."
                                }
                        },
                    )
                    val discard = gameState.discardPile.lastOrNull()
                    PileCard(
                        label = "Discard",
                        value = discard?.value?.toString() ?: "-",
                        imageRes = discard?.let { cardImageRes(it.value) },
                        compact = true,
                        enabled = !isBotTurn,
                        draggable = !isBotTurn && gameState.stage == TurnStage.DRAW_OR_TAKE,
                        onPositioned = { discardBounds = it },
                        onDragStart = { beginPileDrag(Action.DrawFromDiscard, discardBounds) },
                        onDrag = { dragAmount ->
                            activePileDrag = activePileDrag?.let {
                                it.copy(dragOffset = it.dragOffset + dragAmount)
                            }
                        },
                        onDragEnd = {
                            activePileDrag?.let { drag ->
                                handleCardDrop(drag.sourceBounds.center + drag.dragOffset)
                            }
                            activePileDrag = null
                        },
                        onClick = {
                            humanHeldCardCameFromDeck = false
                            dispatch(Action.DrawFromDiscard)
                        },
                    )
                }
            }

            PlayerBoard(
                player = humanPlayer,
                title = "${humanPlayer.name} | Score ${humanPlayer.score}",
                enabled = !isBotTurn,
                compact = false,
                modifier = Modifier.weight(1f, fill = false),
                onCardPositioned = { index, bounds ->
                    if (player.id == humanPlayer.id) {
                        gridBounds[index] = bounds
                    }
                },
                onCardClick = { index ->
                    when (gameState.stage) {
                        TurnStage.CHOOSE_SWAP_OR_DISCARD -> dispatch(Action.SwapWithGrid(index))
                        TurnStage.TURN_END -> dispatch(Action.RevealGrid(index))
                        TurnStage.DRAW_OR_TAKE -> message = "Draw or take the discard card first."
                    }
                },
            )

            ActionButtons(
                gameState = gameState,
                enabled = !isBotTurn,
                onDiscard = { dispatch(Action.DiscardDrawnCard) },
                onEndTurn = { dispatch(Action.EndTurn) },
                onNewGame = {
                    gameState = SkyoGame.newGame(humanPlayerName = "You", botCount = 1)
                    message = "Draw from the deck or take the discard card."
                    gridBounds.clear()
                    activePileDrag = null
                    humanHeldCardCameFromDeck = false
                    botDropTarget = null
                },
            )
        }

        activePileDrag?.let { drag ->
            FloatingDraggedCard(drag)
        }
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
                contentScale = ContentScale.Fit,
            )
            else -> CardBack()
        }
    }
}

@Composable
private fun CardBack() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF173B35))
            .border(1.dp, Color(0xFF214E46), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.card_back),
            contentDescription = "Hidden card",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
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
private fun PileCard(
    label: String,
    value: String,
    imageRes: Int? = null,
    compact: Boolean = false,
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
                .size(
                    width = if (compact) 62.dp else 82.dp,
                    height = if (compact) 90.dp else 118.dp,
                )
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (label == "Deck") "$label ($value)" else label,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF143D35),
        )
    }
}

@Composable
private fun HeldCardSlot(
    modifier: Modifier = Modifier,
    card: Card?,
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
            .size(width = 62.dp, height = 110.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .size(width = 62.dp, height = 90.dp)
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

@Composable
private fun PlayerBoard(
    player: PlayerState,
    title: String,
    enabled: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onCardPositioned: (Int, Rect) -> Unit,
    onCardClick: (Int) -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(max = if (compact) 188.dp else 318.dp)
            .fillMaxWidth()
            .background(
                color = if (compact) Color.Transparent else Color(0xFFFFA3B7),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(if (compact) 0.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 8.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            fontSize = if (compact) 11.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF143D35),
        )
        BoardGrid(
            cards = player.grid,
            enabled = enabled,
            compact = compact,
            spacing = if (compact) 3.dp else 8.dp,
            onCardPositioned = onCardPositioned,
            onCardClick = onCardClick,
        )
    }
}

@Composable
private fun BoardGrid(
    cards: List<Card>,
    enabled: Boolean,
    compact: Boolean,
    spacing: Dp,
    onCardPositioned: (Int, Rect) -> Unit,
    onCardClick: (Int) -> Unit,
) {
    val cardWidth = if (compact) 40.dp else 58.dp
    val cardHeight = if (compact) 59.dp else 85.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        cards.chunked(4).forEachIndexed { rowIndex, rowCards ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                rowCards.forEachIndexed { columnIndex, card ->
                    val index = rowIndex * 4 + columnIndex
                    BoardCard(
                        card = card,
                        enabled = enabled,
                        modifier = Modifier.size(width = cardWidth, height = cardHeight),
                        onPositioned = { bounds -> onCardPositioned(index, bounds) },
                        onClick = { onCardClick(index) },
                    )
                }
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
private const val SPLASH_DURATION_MS = 1200L

@Composable
private fun ActionButtons(
    gameState: GameState,
    enabled: Boolean,
    onDiscard: () -> Unit,
    onEndTurn: () -> Unit,
    onNewGame: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onDiscard,
            enabled = enabled && gameState.stage == TurnStage.CHOOSE_SWAP_OR_DISCARD,
            modifier = Modifier.weight(1f),
        ) {
            Text("Discard")
        }
        Button(
            onClick = onEndTurn,
            enabled = enabled &&
                gameState.stage == TurnStage.TURN_END &&
                !gameState.revealRequiredBeforeEndTurn,
            modifier = Modifier.weight(1f),
        ) {
            Text("End Turn")
        }
        Button(
            onClick = onNewGame,
            modifier = Modifier.weight(1f),
        ) {
            Text("New")
        }
    }
}

private fun chooseBotDrawAction(state: GameState): Action {
    val bot = state.players[state.currentPlayerIndex]
    val discard = state.discardPile.lastOrNull()
    val worstCard = bot.grid
        .withIndex()
        .filterNot { it.value.isCleared }
        .maxByOrNull { it.value.value }
        ?.value

    return if (discard != null && worstCard != null && discard.value < worstCard.value) {
        Action.DrawFromDiscard
    } else {
        Action.DrawFromDeck
    }
}

private fun chooseBotSwapIndex(state: GameState): Int? {
    val drawn = state.drawnCard ?: return null
    val bot = state.players[state.currentPlayerIndex]
    val worstSlot = bot.grid
        .withIndex()
        .filterNot { it.value.isCleared }
        .maxByOrNull { it.value.value }
        ?: return null

    return if (drawn.value < worstSlot.value.value) worstSlot.index else null
}

private fun chooseBotRevealIndex(state: GameState): Int? {
    val bot = state.players[state.currentPlayerIndex]
    return bot.grid
        .withIndex()
        .filter { !it.value.isCleared && !it.value.isRevealed }
        .minByOrNull { it.value.value }
        ?.index
}

private fun messageFor(state: GameState): String {
    if (state.gameEnded) {
        val losers = state.players.filter { it.hasLost }.joinToString { it.name }
        return "$losers reached 100 points."
    }

    if (state.roundEnded) {
        return "Round over. Start a new game for now."
    }

    if (state.roundFinisherIndex != null) {
        return "${state.finalTurnsRemaining} final turn(s) remaining."
    }

    return when (state.stage) {
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
    -2 -> R.drawable.card_neg_2
    -1 -> R.drawable.card_neg_1
    0 -> R.drawable.card_0
    1 -> R.drawable.card_1
    2 -> R.drawable.card_2
    3 -> R.drawable.card_3
    4 -> R.drawable.card_4
    5 -> R.drawable.card_5
    6 -> R.drawable.card_6
    7 -> R.drawable.card_7
    8 -> R.drawable.card_8
    9 -> R.drawable.card_9
    10 -> R.drawable.card_10
    11 -> R.drawable.card_11
    12 -> R.drawable.card_12
    else -> error("Unsupported card value: $value")
}
