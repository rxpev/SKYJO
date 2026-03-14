package com.skyo.game

import kotlin.random.Random

object SkyoGame {
    private const val GRID_SIZE = 12

    fun newGame(
        humanPlayerName: String,
        botCount: Int,
        random: Random = Random.Default,
    ): GameState {
        require(botCount in 1..3) { "botCount must be between 1 and 3" }

        val players = buildList {
            add(PlayerState(id = 0, name = humanPlayerName, isBot = false, grid = emptyList()))
            repeat(botCount) { botIndex ->
                add(PlayerState(id = botIndex + 1, name = "Bot ${botIndex + 1}", isBot = true, grid = emptyList()))
            }
        }

        val shuffledDeck = defaultDeck().shuffled(random)
        var pointer = 0

        val initializedPlayers = players.map { player ->
            val dealt = shuffledDeck.subList(pointer, pointer + GRID_SIZE).map { it.copy(isRevealed = false) }
            pointer += GRID_SIZE

            val revealIndices = (0 until GRID_SIZE).shuffled(random).take(2).toSet()
            val grid = dealt.mapIndexed { index, card ->
                if (index in revealIndices) card.copy(isRevealed = true) else card
            }

            player.copy(grid = grid)
        }

        val discardStart = shuffledDeck[pointer].copy(isRevealed = true)
        pointer += 1

        return GameState(
            players = initializedPlayers,
            deck = shuffledDeck.drop(pointer),
            discardPile = listOf(discardStart),
            currentPlayerIndex = 0,
            stage = TurnStage.DRAW_OR_TAKE,
        )
    }

    fun reduce(state: GameState, action: Action): GameState = when (action) {
        Action.DrawFromDeck -> drawFromDeck(state)
        Action.DrawFromDiscard -> drawFromDiscard(state)
        is Action.SwapWithGrid -> swapWithGrid(state, action.index)
        Action.DiscardDrawnCard -> discardDrawnCard(state)
        is Action.RevealGrid -> revealGrid(state, action.index)
        Action.EndTurn -> endTurn(state)
    }

    private fun drawFromDeck(state: GameState): GameState {
        require(state.stage == TurnStage.DRAW_OR_TAKE) { "Must be in DRAW_OR_TAKE stage" }
        require(state.deck.isNotEmpty()) { "Deck is empty" }
        val drawn = state.deck.first().copy(isRevealed = true)
        return state.copy(
            deck = state.deck.drop(1),
            drawnCard = drawn,
            stage = TurnStage.CHOOSE_SWAP_OR_DISCARD,
        )
    }

    private fun drawFromDiscard(state: GameState): GameState {
        require(state.stage == TurnStage.DRAW_OR_TAKE) { "Must be in DRAW_OR_TAKE stage" }
        require(state.discardPile.isNotEmpty()) { "Discard pile is empty" }
        val drawn = state.discardPile.last().copy(isRevealed = true)
        return state.copy(
            discardPile = state.discardPile.dropLast(1),
            drawnCard = drawn,
            stage = TurnStage.CHOOSE_SWAP_OR_DISCARD,
        )
    }

    private fun swapWithGrid(state: GameState, index: Int): GameState {
        require(state.stage == TurnStage.CHOOSE_SWAP_OR_DISCARD) { "Must choose swap or discard after drawing" }
        require(index in 0 until GRID_SIZE) { "Grid index out of bounds" }
        val drawn = requireNotNull(state.drawnCard) { "No drawn card available" }

        val current = state.players[state.currentPlayerIndex]
        val swappedOut = current.grid[index].copy(isRevealed = true)
        val newGrid = current.grid.toMutableList().also { it[index] = drawn.copy(isRevealed = true) }
        val updatedPlayers = state.players.toMutableList().also {
            it[state.currentPlayerIndex] = current.copy(grid = newGrid)
        }

        return state.copy(
            players = updatedPlayers,
            discardPile = state.discardPile + swappedOut,
            drawnCard = null,
            stage = TurnStage.TURN_END,
        )
    }

    private fun discardDrawnCard(state: GameState): GameState {
        require(state.stage == TurnStage.CHOOSE_SWAP_OR_DISCARD) { "Must choose swap or discard after drawing" }
        val drawn = requireNotNull(state.drawnCard) { "No drawn card available" }
        return state.copy(
            discardPile = state.discardPile + drawn.copy(isRevealed = true),
            drawnCard = null,
            stage = TurnStage.TURN_END,
        )
    }

    private fun revealGrid(state: GameState, index: Int): GameState {
        require(state.stage == TurnStage.TURN_END) { "Can only reveal a grid card at turn end" }
        require(index in 0 until GRID_SIZE) { "Grid index out of bounds" }

        val current = state.players[state.currentPlayerIndex]
        require(!current.grid[index].isRevealed) { "Card already revealed" }

        val updatedGrid = current.grid.toMutableList().also {
            it[index] = it[index].copy(isRevealed = true)
        }
        val updatedPlayers = state.players.toMutableList().also {
            it[state.currentPlayerIndex] = current.copy(grid = updatedGrid)
        }

        return state.copy(players = updatedPlayers)
    }

    private fun endTurn(state: GameState): GameState {
        require(state.stage == TurnStage.TURN_END) { "Turn can only end from TURN_END stage" }

        val roundEnded = state.players[state.currentPlayerIndex].grid.all { it.isRevealed }
        val nextPlayer = (state.currentPlayerIndex + 1) % state.players.size

        return state.copy(
            currentPlayerIndex = nextPlayer,
            stage = TurnStage.DRAW_OR_TAKE,
            roundEnded = roundEnded,
        )
    }

    private fun defaultDeck(): List<Card> {
        val values = (-2..12).flatMap { value -> List(10) { value } }
        return values.map { Card(value = it, isRevealed = false) }
    }
}
