package com.skyo.game

import kotlin.random.Random

object SkyoGame {
    private const val GRID_SIZE = 12
    private const val GRID_COLUMNS = 4
    private const val GRID_ROWS = 3

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
        Action.DrawFromDeck -> drawFromDeck(requireRoundInProgress(state))
        Action.DrawFromDiscard -> drawFromDiscard(requireRoundInProgress(state))
        is Action.SwapWithGrid -> swapWithGrid(requireRoundInProgress(state), action.index)
        Action.DiscardDrawnCard -> discardDrawnCard(requireRoundInProgress(state))
        is Action.RevealGrid -> revealGrid(requireRoundInProgress(state), action.index)
        Action.EndTurn -> endTurn(requireRoundInProgress(state))
    }

    fun scoreGrid(grid: List<Card>): Int {
        require(grid.size == GRID_SIZE) { "Grid must contain $GRID_SIZE cards" }
        return grid.filterNot { it.isCleared }.sumOf { it.value }
    }

    private fun drawFromDeck(state: GameState): GameState {
        require(state.stage == TurnStage.DRAW_OR_TAKE) { "Must be in DRAW_OR_TAKE stage" }
        require(state.deck.isNotEmpty()) { "Deck is empty" }
        val drawn = state.deck.first().copy(isRevealed = true)
        return state.copy(
            deck = state.deck.drop(1),
            drawnCard = drawn,
            revealRequiredBeforeEndTurn = false,
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
            revealRequiredBeforeEndTurn = false,
            stage = TurnStage.CHOOSE_SWAP_OR_DISCARD,
        )
    }

    private fun swapWithGrid(state: GameState, index: Int): GameState {
        require(state.stage == TurnStage.CHOOSE_SWAP_OR_DISCARD) { "Must choose swap or discard after drawing" }
        require(index in 0 until GRID_SIZE) { "Grid index out of bounds" }
        val drawn = requireNotNull(state.drawnCard) { "No drawn card available" }

        val current = state.players[state.currentPlayerIndex]
        require(!current.grid[index].isCleared) { "Cannot swap with a cleared card slot" }
        val swappedOut = current.grid[index].copy(isRevealed = true)
        val newGrid = current.grid.toMutableList().also { it[index] = drawn.copy(isRevealed = true) }
        val updatedPlayers = state.players.toMutableList().also {
            it[state.currentPlayerIndex] = current.copy(grid = newGrid)
        }

        return clearCompletedColumns(
            state.copy(
                players = updatedPlayers,
                discardPile = state.discardPile + swappedOut,
                drawnCard = null,
                revealRequiredBeforeEndTurn = false,
                stage = TurnStage.TURN_END,
            )
        )
    }

    private fun discardDrawnCard(state: GameState): GameState {
        require(state.stage == TurnStage.CHOOSE_SWAP_OR_DISCARD) { "Must choose swap or discard after drawing" }
        val drawn = requireNotNull(state.drawnCard) { "No drawn card available" }
        val current = state.players[state.currentPlayerIndex]
        val hasHiddenGridCard = current.grid.any { !it.isCleared && !it.isRevealed }
        return state.copy(
            discardPile = state.discardPile + drawn.copy(isRevealed = true),
            drawnCard = null,
            revealRequiredBeforeEndTurn = hasHiddenGridCard,
            stage = TurnStage.TURN_END,
        )
    }

    private fun revealGrid(state: GameState, index: Int): GameState {
        require(state.stage == TurnStage.TURN_END) { "Can only reveal a grid card at turn end" }
        require(index in 0 until GRID_SIZE) { "Grid index out of bounds" }

        val current = state.players[state.currentPlayerIndex]
        require(!current.grid[index].isCleared) { "Cannot reveal a cleared card slot" }
        require(!current.grid[index].isRevealed) { "Card already revealed" }

        val updatedGrid = current.grid.toMutableList().also {
            it[index] = it[index].copy(isRevealed = true)
        }
        val updatedPlayers = state.players.toMutableList().also {
            it[state.currentPlayerIndex] = current.copy(grid = updatedGrid)
        }

        return clearCompletedColumns(
            state.copy(
                players = updatedPlayers,
                revealRequiredBeforeEndTurn = false,
            )
        )
    }

    private fun endTurn(state: GameState): GameState {
        require(state.stage == TurnStage.TURN_END) { "Turn can only end from TURN_END stage" }
        require(!state.revealRequiredBeforeEndTurn) { "Reveal one hidden card before ending your turn" }

        val currentPlayerFinished = state.players[state.currentPlayerIndex].grid
            .filterNot { it.isCleared }
            .all { it.isRevealed }

        if (state.roundFinisherIndex == null && currentPlayerFinished) {
            return state.copy(
                currentPlayerIndex = nextPlayerIndex(state),
                stage = TurnStage.DRAW_OR_TAKE,
                revealRequiredBeforeEndTurn = false,
                roundFinisherIndex = state.currentPlayerIndex,
                finalTurnsRemaining = state.players.size - 1,
            )
        }

        if (state.roundFinisherIndex != null) {
            val finalTurnsRemaining = state.finalTurnsRemaining - 1
            if (finalTurnsRemaining == 0) {
                return scoreRound(state, state.roundFinisherIndex)
            }

            return state.copy(
                currentPlayerIndex = nextPlayerIndex(state),
                stage = TurnStage.DRAW_OR_TAKE,
                revealRequiredBeforeEndTurn = false,
                finalTurnsRemaining = finalTurnsRemaining,
            )
        }

        return state.copy(
            currentPlayerIndex = nextPlayerIndex(state),
            stage = TurnStage.DRAW_OR_TAKE,
            revealRequiredBeforeEndTurn = false,
        )
    }

    private fun clearCompletedColumns(state: GameState): GameState {
        val current = state.players[state.currentPlayerIndex]
        val grid = current.grid.toMutableList()
        val clearedCards = mutableListOf<Card>()

        for (column in 0 until GRID_COLUMNS) {
            val indices = (0 until GRID_ROWS).map { row -> row * GRID_COLUMNS + column }
            val columnCards = indices.map { grid[it] }
            val shouldClear = columnCards.all { it.isRevealed && !it.isCleared } &&
                columnCards.map { it.value }.distinct().size == 1

            if (shouldClear) {
                indices.forEach { index ->
                    clearedCards += grid[index].copy(isRevealed = true, isCleared = true)
                    grid[index] = grid[index].copy(isRevealed = true, isCleared = true)
                }
            }
        }

        if (clearedCards.isEmpty()) {
            return state
        }

        val updatedPlayers = state.players.toMutableList().also {
            it[state.currentPlayerIndex] = current.copy(grid = grid)
        }

        return state.copy(
            players = updatedPlayers,
            discardPile = state.discardPile + clearedCards,
        )
    }

    private fun scoreRound(state: GameState, finishingPlayerIndex: Int): GameState {
        val revealedPlayers = state.players.map { player ->
            player.copy(
                grid = player.grid.map { card ->
                    if (card.isCleared) card else card.copy(isRevealed = true)
                },
            )
        }

        val roundScores = revealedPlayers.map { scoreGrid(it.grid) }
        val finishingPlayerScore = roundScores[finishingPlayerIndex]
        val finishingPlayerHasLowestScore = roundScores.withIndex()
            .all { (index, score) -> index == finishingPlayerIndex || finishingPlayerScore <= score }

        val scoredPlayers = revealedPlayers.mapIndexed { index, player ->
            val roundScore = if (index == finishingPlayerIndex && !finishingPlayerHasLowestScore) {
                roundScores[index] * 2
            } else {
                roundScores[index]
            }

            player.copy(score = player.score + roundScore)
        }
        val gameEnded = scoredPlayers.any { it.score >= MAX_SCORE }

        return state.copy(
            players = scoredPlayers.map { it.copy(hasLost = gameEnded && it.score >= MAX_SCORE) },
            drawnCard = null,
            revealRequiredBeforeEndTurn = false,
            currentPlayerIndex = finishingPlayerIndex,
            stage = TurnStage.TURN_END,
            finalTurnsRemaining = 0,
            roundEnded = true,
            gameEnded = gameEnded,
        )
    }

    private fun requireRoundInProgress(state: GameState): GameState {
        require(!state.roundEnded) { "Round already ended" }
        require(!state.gameEnded) { "Game already ended" }
        return state
    }

    private fun nextPlayerIndex(state: GameState): Int = (state.currentPlayerIndex + 1) % state.players.size

    private fun defaultDeck(): List<Card> {
        val values = listOf(
            List(5) { -2 },
            List(10) { -1 },
            List(15) { 0 },
            (1..12).flatMap { value -> List(10) { value } },
        ).flatten()
        return values.map { Card(value = it, isRevealed = false) }
    }

    private const val MAX_SCORE = 100
}
