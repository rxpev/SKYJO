package com.skyo.game

data class Card(
    val value: Int,
    val isRevealed: Boolean = false,
)

data class PlayerState(
    val id: Int,
    val name: String,
    val isBot: Boolean,
    val grid: List<Card>,
    val score: Int = 0,
)

enum class TurnStage {
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
    val round: Int = 1,
    val roundEnded: Boolean = false,
)

sealed interface Action {
    data object DrawFromDeck : Action
    data object DrawFromDiscard : Action
    data class SwapWithGrid(val index: Int) : Action
    data object DiscardDrawnCard : Action
    data class RevealGrid(val index: Int) : Action
    data object EndTurn : Action
}
