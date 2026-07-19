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

        val deal = dealRound(players, random)

        return GameState(
            players = deal.players,
            deck = deal.deck,
            discardPile = deal.discardPile,
            currentPlayerIndex = 0,
            stage = TurnStage.OPENING_REVEAL,
        )
    }

    fun startNextRound(
        state: GameState,
        random: Random = Random.Default,
    ): GameState {
        require(state.roundEnded) { "Round must be over before starting the next round" }
        require(!state.gameEnded) { "Game is already over" }

        val deal = dealRound(
            players = state.players.map { player ->
                player.copy(grid = emptyList(), hasLost = false)
            },
            random = random,
        )

        return GameState(
            players = deal.players,
            deck = deal.deck,
            discardPile = deal.discardPile,
            currentPlayerIndex = 0,
            stage = TurnStage.OPENING_REVEAL,
            round = state.round + 1,
        )
    }

    fun reduce(state: GameState, action: Action): GameState = when (action) {
        Action.DrawFromDeck -> drawFromDeck(requireRoundInProgress(state))
        Action.DrawFromDiscard -> drawFromDiscard(requireRoundInProgress(state))
        is Action.SwapWithGrid -> swapWithGrid(requireRoundInProgress(state), action.index)
        Action.DiscardDrawnCard -> discardDrawnCard(requireRoundInProgress(state))
        Action.ReturnDrawnDiscardCard -> returnDrawnDiscardCard(requireRoundInProgress(state))
        is Action.RevealGrid -> revealGrid(requireRoundInProgress(state), action.index)
        is Action.RevealOpeningBotGrid -> revealOpeningBotGrid(requireRoundInProgress(state), action.playerId, action.index)
        Action.EndTurn -> endTurn(requireRoundInProgress(state))
    }

    fun chooseOpeningBotRevealIndices(
        player: PlayerState,
        targetRevealCount: Int,
        random: Random = Random.Default,
    ): List<Int> {
        require(player.isBot) { "Only bots use automatic opening reveal choices" }

        val revealedCount = player.grid.count { it.isRevealed }
        val needed = targetRevealCount - revealedCount
        if (needed <= 0) {
            return emptyList()
        }

        val hiddenIndices = player.grid
            .withIndex()
            .filter { !it.value.isCleared && !it.value.isRevealed }
            .map { it.index }

        if (hiddenIndices.size <= needed) {
            return hiddenIndices
        }

        return if (needed >= 2 && revealedCount == 0) {
            weightedOpeningPair(hiddenIndices, random).toList().shuffled(random).take(needed)
        } else {
            hiddenIndices.shuffled(random).take(needed)
        }
    }

    fun scoreGrid(grid: List<Card>): Int {
        require(grid.size == GRID_SIZE) { "Grid must contain $GRID_SIZE cards" }
        return grid.filterNot { it.isCleared }.sumOf { it.value }
    }

    private fun drawFromDeck(state: GameState): GameState {
        require(state.stage == TurnStage.DRAW_OR_TAKE) { "Must be in DRAW_OR_TAKE stage" }
        require(state.deck.isNotEmpty()) { "Deck is empty" }
        val drawn = state.deck.first().copy(isRevealed = true, isCleared = false)
        return state.copy(
            deck = state.deck.drop(1),
            drawnCard = drawn,
            drawnCardCameFromDiscard = false,
            revealRequiredBeforeEndTurn = false,
            stage = TurnStage.CHOOSE_SWAP_OR_DISCARD,
        )
    }

    private fun drawFromDiscard(state: GameState): GameState {
        require(state.stage == TurnStage.DRAW_OR_TAKE) { "Must be in DRAW_OR_TAKE stage" }
        require(state.discardPile.isNotEmpty()) { "Discard pile is empty" }
        val drawn = state.discardPile.last().copy(isRevealed = true, isCleared = false)
        return state.copy(
            discardPile = state.discardPile.dropLast(1),
            drawnCard = drawn,
            drawnCardCameFromDiscard = true,
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
        val newGrid = current.grid.toMutableList().also { it[index] = drawn.copy(isRevealed = true, isCleared = false) }
        val updatedPlayers = state.players.toMutableList().also {
            it[state.currentPlayerIndex] = current.copy(grid = newGrid)
        }

        return clearCompletedColumns(
            state.copy(
                players = updatedPlayers,
                discardPile = state.discardPile + swappedOut,
                drawnCard = null,
                drawnCardCameFromDiscard = false,
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
            discardPile = state.discardPile + drawn.copy(isRevealed = true, isCleared = false),
            drawnCard = null,
            drawnCardCameFromDiscard = false,
            revealRequiredBeforeEndTurn = hasHiddenGridCard,
            stage = TurnStage.TURN_END,
        )
    }

    private fun returnDrawnDiscardCard(state: GameState): GameState {
        require(state.stage == TurnStage.CHOOSE_SWAP_OR_DISCARD) { "Must choose swap or discard after drawing" }
        require(state.drawnCardCameFromDiscard) { "Only a card taken from discard can be returned" }
        val drawn = requireNotNull(state.drawnCard) { "No drawn card available" }

        return state.copy(
            discardPile = state.discardPile + drawn.copy(isRevealed = true, isCleared = false),
            drawnCard = null,
            drawnCardCameFromDiscard = false,
            revealRequiredBeforeEndTurn = false,
            stage = TurnStage.DRAW_OR_TAKE,
        )
    }

    private fun revealGrid(state: GameState, index: Int): GameState {
        if (state.stage == TurnStage.OPENING_REVEAL) {
            return revealHumanOpeningGrid(state, index)
        }

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

    private fun revealHumanOpeningGrid(state: GameState, index: Int): GameState {
        require(index in 0 until GRID_SIZE) { "Grid index out of bounds" }

        val humanIndex = state.players.indexOfFirst { !it.isBot }
        require(humanIndex >= 0) { "No human player found" }

        val human = state.players[humanIndex]
        val contenders = openingContenderIds(state)
        require(human.id in contenders) { "Opening tie-break does not need another card from you" }
        require(human.grid.count { it.isRevealed } < state.openingRevealCount) {
            "You already revealed enough cards for this tie-break"
        }
        require(!human.grid[index].isCleared) { "Cannot reveal a cleared card slot" }
        require(!human.grid[index].isRevealed) { "Card already revealed" }

        val updatedGrid = human.grid.toMutableList().also {
            it[index] = it[index].copy(isRevealed = true)
        }
        val updatedPlayers = state.players.toMutableList().also {
            it[humanIndex] = human.copy(grid = updatedGrid)
        }

        return advanceOpeningReveal(state.copy(players = updatedPlayers))
    }

    private fun revealOpeningBotGrid(state: GameState, playerId: Int, index: Int): GameState {
        require(state.stage == TurnStage.OPENING_REVEAL) { "Bot opening cards can only be revealed during opening reveal" }
        require(index in 0 until GRID_SIZE) { "Grid index out of bounds" }

        val playerIndex = state.players.indexOfFirst { it.id == playerId }
        require(playerIndex >= 0) { "Player not found" }

        val player = state.players[playerIndex]
        require(player.isBot) { "Only bots can use bot opening reveal" }
        require(player.id in openingContenderIds(state)) { "This bot is not in the opening tie-break" }
        require(player.grid.count { it.isRevealed } < state.openingRevealCount) {
            "${player.name} already revealed enough cards for this tie-break"
        }
        require(!player.grid[index].isCleared) { "Cannot reveal a cleared card slot" }
        require(!player.grid[index].isRevealed) { "Card already revealed" }

        val updatedGrid = player.grid.toMutableList().also {
            it[index] = it[index].copy(isRevealed = true)
        }
        val updatedPlayers = state.players.toMutableList().also {
            it[playerIndex] = player.copy(grid = updatedGrid)
        }

        return advanceOpeningReveal(state.copy(players = updatedPlayers))
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
                    clearedCards += grid[index].copy(isRevealed = true, isCleared = false)
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
            .all { (index, score) -> index == finishingPlayerIndex || finishingPlayerScore < score }

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
            drawnCardCameFromDiscard = false,
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

    private fun advanceOpeningReveal(state: GameState): GameState {
        val contenders = openingContenderIds(state)
        val contenderPlayers = state.players.filter { it.id in contenders }

        if (contenderPlayers.any { it.grid.count { card -> card.isRevealed } < state.openingRevealCount }) {
            return state
        }

        val ranked = contenderPlayers.map { player -> player to openingRank(player) }
        val bestRank = ranked.maxOf { it.second }
        val leaders = ranked.filter { it.second == bestRank }.map { it.first }

        if (leaders.size == 1) {
            val startingPlayerIndex = state.players.indexOfFirst { it.id == leaders.single().id }
            return state.copy(
                currentPlayerIndex = startingPlayerIndex,
                stage = TurnStage.DRAW_OR_TAKE,
                openingRevealCount = 2,
                openingContenderIds = emptySet(),
            )
        }

        if (leaders.all { leader -> leader.grid.none { !it.isCleared && !it.isRevealed } }) {
            val startingPlayerIndex = state.players.indexOfFirst { it.id == leaders.minBy { leader -> leader.id }.id }
            return state.copy(
                currentPlayerIndex = startingPlayerIndex,
                stage = TurnStage.DRAW_OR_TAKE,
                openingRevealCount = 2,
                openingContenderIds = emptySet(),
            )
        }

        return state.copy(
            openingRevealCount = state.openingRevealCount + 1,
            openingContenderIds = leaders.map { it.id }.toSet(),
        )
    }

    private fun openingContenderIds(state: GameState): Set<Int> =
        state.openingContenderIds.ifEmpty { state.players.map { it.id }.toSet() }

    private fun openingRank(player: PlayerState): OpeningRank {
        val revealedValues = player.grid
            .filter { it.isRevealed && !it.isCleared }
            .map { it.value }
            .sortedDescending()

        return OpeningRank(revealedValues.sum(), revealedValues)
    }

    private data class OpeningRank(
        val sum: Int,
        val sortedValuesDescending: List<Int>,
    ) : Comparable<OpeningRank> {
        override fun compareTo(other: OpeningRank): Int {
            sum.compareTo(other.sum).takeIf { it != 0 }?.let { return it }

            val maxSize = maxOf(sortedValuesDescending.size, other.sortedValuesDescending.size)
            for (index in 0 until maxSize) {
                val left = sortedValuesDescending.getOrElse(index) { Int.MIN_VALUE }
                val right = other.sortedValuesDescending.getOrElse(index) { Int.MIN_VALUE }
                left.compareTo(right).takeIf { it != 0 }?.let { return it }
            }

            return 0
        }
    }

    private fun weightedOpeningPair(indices: List<Int>, random: Random): Set<Int> {
        val weightedPairs = buildList {
            for (firstPosition in indices.indices) {
                for (secondPosition in firstPosition + 1 until indices.size) {
                    val first = indices[firstPosition]
                    val second = indices[secondPosition]
                    val weight = openingPairWeight(first, second)
                    repeat(weight) { add(setOf(first, second)) }
                }
            }
        }

        return weightedPairs.random(random)
    }

    private fun openingPairWeight(first: Int, second: Int): Int {
        val firstRow = first / GRID_COLUMNS
        val firstColumn = first % GRID_COLUMNS
        val secondRow = second / GRID_COLUMNS
        val secondColumn = second % GRID_COLUMNS

        return when {
            firstColumn == secondColumn && kotlin.math.abs(firstRow - secondRow) == 1 -> 8
            firstRow == secondRow && kotlin.math.abs(firstColumn - secondColumn) == 1 -> 3
            else -> 1
        }
    }

    private fun dealRound(players: List<PlayerState>, random: Random): RoundDeal {
        val shuffledDeck = defaultDeck().shuffled(random)
        var pointer = 0

        val initializedPlayers = players.map { player ->
            val dealt = shuffledDeck.subList(pointer, pointer + GRID_SIZE).map { it.copy(isRevealed = false) }
            pointer += GRID_SIZE

            player.copy(grid = dealt)
        }

        val discardStart = shuffledDeck[pointer].copy(isRevealed = true)
        pointer += 1

        return RoundDeal(
            players = initializedPlayers,
            deck = shuffledDeck.drop(pointer),
            discardPile = listOf(discardStart),
        )
    }

    private data class RoundDeal(
        val players: List<PlayerState>,
        val deck: List<Card>,
        val discardPile: List<Card>,
    )

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
