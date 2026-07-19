package com.skyo.game

data class Card(
    val value: Int,
    val isRevealed: Boolean = false,
    val isCleared: Boolean = false,
)

data class PlayerState(
    val id: Int,
    val name: String,
    val isBot: Boolean,
    val grid: List<Card>,
    val score: Int = 0,
    val hasLost: Boolean = false,
)

enum class TurnStage {
    OPENING_REVEAL,
    DRAW_OR_TAKE,
    CHOOSE_SWAP_OR_DISCARD,
    TURN_END,
}

data class GameState(
    val players: List<PlayerState>,
    val deck: List<Card>,
    val discardPile: List<Card>,
    val currentPlayerIndex: Int,
    val stage: TurnStage,
    val drawnCard: Card? = null,
    val drawnCardCameFromDiscard: Boolean = false,
    val revealRequiredBeforeEndTurn: Boolean = false,
    val round: Int = 1,
    val roundFinisherIndex: Int? = null,
    val finalTurnsRemaining: Int = 0,
    val openingRevealCount: Int = 2,
    val openingContenderIds: Set<Int> = emptySet(),
    val roundEnded: Boolean = false,
    val gameEnded: Boolean = false,
)

sealed interface Action {
    data object DrawFromDeck : Action
    data object DrawFromDiscard : Action
    data class SwapWithGrid(val index: Int) : Action
    data object DiscardDrawnCard : Action
    data object ReturnDrawnDiscardCard : Action
    data class RevealGrid(val index: Int) : Action
    data class RevealOpeningBotGrid(val playerId: Int, val index: Int) : Action
    data object EndTurn : Action
}
