package com.skyo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.skyo.game.Action
import com.skyo.game.Card
import com.skyo.game.GameState
import com.skyo.game.SkyoGame
import com.skyo.game.TurnStage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SkyjoTheme {
                SkyjoGameScreen()
            }
        }
    }
}

@Composable
private fun SkyjoTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF4F1EA),
            content = content,
        )
    }
}

@Composable
private fun SkyjoGameScreen() {
    var gameState by remember { mutableStateOf(SkyoGame.newGame(humanPlayerName = "You", botCount = 1)) }
    var message by remember { mutableStateOf("Draw from the deck or take the discard card.") }
    var showOpponents by remember { mutableStateOf(false) }
    var discardBounds by remember { mutableStateOf(Rect.Zero) }
    var drawnCardBounds by remember { mutableStateOf(Rect.Zero) }
    var botDropTarget by remember { mutableStateOf<Rect?>(null) }
    val gridBounds = remember { mutableStateMapOf<Int, Rect>() }

    fun dispatch(action: Action) {
        runCatching { SkyoGame.reduce(gameState, action) }
            .onSuccess { nextState ->
                gameState = nextState
                message = messageFor(nextState)
            }
            .onFailure { error ->
                message = error.message ?: "That move is not allowed."
            }
    }

    val player = gameState.players[gameState.currentPlayerIndex]
    val isBotTurn = player.isBot

    LaunchedEffect(gameState.currentPlayerIndex, gameState.stage, gameState.roundEnded, gameState.gameEnded) {
        if (!isBotTurn) {
            showOpponents = false
        }

        if (isBotTurn && !gameState.roundEnded && !gameState.gameEnded) {
            showOpponents = true
            var nextState = gameState

            if (nextState.stage == TurnStage.DRAW_OR_TAKE) {
                val drawAction = chooseBotDrawAction(nextState)
                message = "${player.name} is choosing a pile..."
                delay(BOT_DECISION_DELAY_MS)
                nextState = SkyoGame.reduce(nextState, drawAction)
                gameState = nextState
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "SKYJO",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF143D35),
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

        Text(
            text = message,
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF41534F),
            fontSize = 14.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PileCard(
                label = "Deck",
                value = gameState.deck.size.toString(),
                enabled = !isBotTurn,
                onClick = { dispatch(Action.DrawFromDeck) },
            )
            val discard = gameState.discardPile.lastOrNull()
            PileCard(
                label = "Discard",
                value = discard?.value?.toString() ?: "-",
                imageRes = discard?.let { cardImageRes(it.value) },
                enabled = !isBotTurn,
                onPositioned = { discardBounds = it },
                onClick = { dispatch(Action.DrawFromDiscard) },
            )
        }

        gameState.drawnCard?.let { drawnCard ->
            DrawnCard(
                card = drawnCard,
                stage = gameState.stage,
                draggable = !isBotTurn && gameState.stage == TurnStage.CHOOSE_SWAP_OR_DISCARD,
                animatedDropTarget = botDropTarget,
                onPositioned = { drawnCardBounds = it },
                onDropped = { center ->
                    when {
                        discardBounds.contains(center) -> dispatch(Action.DiscardDrawnCard)
                        else -> gridBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(center) }
                            ?.let { (index, _) -> dispatch(Action.SwapWithGrid(index)) }
                            ?: run { message = "Drop it on the discard pile or one of your cards." }
                    }
                },
            )
        }

        OpponentSection(
            gameState = gameState,
            expanded = showOpponents,
            onToggle = { showOpponents = !showOpponents },
        )

        if (!showOpponents) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false,
            ) {
                itemsIndexed(player.grid) { index, card ->
                    BoardCard(
                        card = card,
                        enabled = !isBotTurn,
                        onPositioned = { bounds -> gridBounds[index] = bounds },
                        onClick = {
                            when (gameState.stage) {
                                TurnStage.CHOOSE_SWAP_OR_DISCARD -> dispatch(Action.SwapWithGrid(index))
                                TurnStage.TURN_END -> dispatch(Action.RevealGrid(index))
                                TurnStage.DRAW_OR_TAKE -> message = "Draw or take the discard card first."
                            }
                        },
                    )
                }
            }
        }

        ActionButtons(
            gameState = gameState,
            enabled = !isBotTurn,
            onDiscard = { dispatch(Action.DiscardDrawnCard) },
            onEndTurn = { dispatch(Action.EndTurn) },
            onNewGame = {
                gameState = SkyoGame.newGame(humanPlayerName = "You", botCount = 1)
                message = "Draw from the deck or take the discard card."
                showOpponents = false
            },
        )
    }
}

@Composable
private fun BoardCard(
    card: Card,
    enabled: Boolean,
    onPositioned: (Rect) -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
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
    enabled: Boolean = true,
    onPositioned: (Rect) -> Unit = {},
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 82.dp, height = 118.dp)
                .clip(RoundedCornerShape(6.dp))
                .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
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
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF143D35),
        )
    }
}

@Composable
private fun DrawnCard(
    card: Card,
    stage: TurnStage,
    draggable: Boolean,
    animatedDropTarget: Rect?,
    onPositioned: (Rect) -> Unit,
    onDropped: (Offset) -> Unit,
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    val botDragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

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

    Row(
        modifier = Modifier
            .zIndex(1f)
            .fillMaxWidth()
            .background(Color(0xFFE7F0E9), RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 66.dp, height = 96.dp)
                .offset {
                    val combinedOffset = dragOffset + botDragOffset.value
                    IntOffset(
                        combinedOffset.x.roundToInt(),
                        combinedOffset.y.roundToInt(),
                    )
                }
                .clip(RoundedCornerShape(6.dp))
                .onGloballyPositioned {
                    if (botDragOffset.value == Offset.Zero) {
                        cardBounds = it.boundsInRoot()
                        onPositioned(cardBounds)
                    }
                }
                .pointerInput(draggable) {
                    if (draggable) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                            },
                            onDragEnd = {
                                onDropped(cardBounds.center)
                                dragOffset = Offset.Zero
                            },
                            onDragCancel = {
                                dragOffset = Offset.Zero
                            },
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(cardImageRes(card.value)),
                contentDescription = "Drawn card ${card.value}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        SpacerWidth()
        Column {
            Text(
                text = "Picked up: ${card.value}",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF143D35),
            )
            Text(
                text = if (stage == TurnStage.CHOOSE_SWAP_OR_DISCARD) {
                    "Drag it onto your grid or the discard pile."
                } else {
                    "Card in hand"
                },
                color = Color(0xFF41534F),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun OpponentSection(
    gameState: GameState,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val currentPlayer = gameState.players[gameState.currentPlayerIndex]
    val opponents = if (currentPlayer.isBot) {
        listOf(currentPlayer)
    } else {
        gameState.players.filter { it.isBot }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFECE7DC), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (expanded) {
            opponents.forEach { opponent ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${opponent.name} | Score ${opponent.score}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF143D35),
                    )
                    MiniGrid(cards = opponent.grid)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onToggle,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C4FB3)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                EyeIcon()
            }
        }
    }
}

@Composable
private fun EyeIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        drawOval(
            color = Color.White,
            style = Stroke(width = 2.5f),
        )
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.18f,
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}

@Composable
private fun MiniGrid(cards: List<Card>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cards.chunked(4).forEach { rowCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rowCards.forEach { card ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.68f)
                            .clip(RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            card.isCleared -> ClearedSlot()
                            card.isRevealed -> Image(
                                painter = painterResource(cardImageRes(card.value)),
                                contentDescription = "Opponent card ${card.value}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                            else -> CardBack()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpacerWidth() {
    Box(modifier = Modifier.width(12.dp))
}

private const val BOT_DECISION_DELAY_MS = 1400L
private const val BOT_CARD_REVIEW_DELAY_MS = 1300L
private const val BOT_CARD_DRAG_DURATION_MS = 1100L
private const val BOT_AFTER_CARD_DRAG_DELAY_MS = 450L
private const val BOT_REVEAL_DELAY_MS = 1000L
private const val BOT_END_TURN_DELAY_MS = 1200L

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
            enabled = enabled && gameState.stage == TurnStage.TURN_END,
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
        TurnStage.TURN_END -> "Reveal one card if you discarded, then end your turn."
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
