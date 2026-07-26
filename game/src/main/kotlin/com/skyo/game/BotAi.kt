package com.skyo.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

data class BotAiConfig(
    val hiddenExpectedValue: Double = 5.0,
    val lowCardBonus: Map<Int, Double> = mapOf(-2 to 36.0, -1 to 30.0, 0 to 25.0, 1 to 20.0, 2 to 16.0, 3 to 7.0),
    val ownExpectedScoreWeight: Double = -10.0,
    val immediateScoreDeltaWeight: Double = -6.0,
    val removedCardWeight: Double = 7.0,
    val removedValueWeight: Double = 5.0,
    val cascadeStepWeight: Double = 18.0,
    val verticalLineBonus: Double = 8.0,
    val nearLineWeight: Double = 16.0,
    val conditionalDiagonalWeight: Double = 22.0,
    val highCardExposureWeight: Double = -5.5,
    val opponentGiftWeight: Double = -14.0,
    val opponentDenialWeight: Double = 11.0,
    val opponentFinishRiskWeight: Double = -35.0,
    val dangerousFirstFinishPenalty: Double = -100_000.0,
    val safeFinishBonus: Double = 1_000.0,
    val revealHiddenPenaltyWhenBehind: Double = -700.0,
    val preserveNearLineWeight: Double = -400.0,
    val speculativeHighCardPenalty: Double = -90.0,
    val revealAdjacentVerticalBonus: Double = 70.0,
    val revealAdjacentHorizontalBonus: Double = 28.0,
    val revealNearLineBonus: Double = 45.0,
    val isolatedRevealPenalty: Double = -25.0,
    val earlyRevealedCardThreshold: Int = 3,
    val earlyMinimumRevealedSwapImprovement: Int = 4,
    val earlyMarginalRevealedSwapPenalty: Double = -260.0,
    val removalScoreDeltaWeight: Double = 10.0,
    val negativeRemovalPenaltyWeight: Double = -35.0,
    val immediateNegativeLinePenalty: Double = -1_500.0,
    val negativePairRiskPenalty: Double = -180.0,
    val negativeSeparationWeight: Double = 34.0,
    val lowScoreLaneImprovementWeight: Double = 22.0,
    val knownDiscardCertaintyBonus: Double = 22.0,
    val redundantSameValuePenalty: Double = -2_500.0,
    val fillPositivePatternBonus: Double = 520.0,
    val completePositiveLineBonus: Double = 1_800.0,
    val negativeSplitPlacementBonus: Double = 260.0,
    val negativeSameLanePlacementPenalty: Double = -420.0,
    val lowCardIntoNegativeLaneBonus: Double = 280.0,
    val rejectReturnOnlyDiscardChoicePenalty: Double = -8_000.0,
    val discardUsefulLowCardPenalty: Double = -650.0,
    val knownVeryLowDiscardBonus: Double = 650.0,
    val earlyHiddenRatioThreshold: Double = 0.58,
    val lateHiddenRatioThreshold: Double = 0.25,
    val mediocreCardEarlySwapPenalty: Double = -760.0,
    val explorationRevealWeight: Double = 3.2,
    val lateGuaranteedReductionWeight: Double = 4.0,
    val revealMatchingNeighborBonus: Double = 95.0,
    val revealBetweenMatchingBonus: Double = 180.0,
    val revealNearHighCardBonus: Double = 42.0,
    val revealPatternLaneBonus: Double = 58.0,
    val negativeCardIncreasePenalty: Double = -12_000.0,
    val compactionBridgeCascadeBonus: Double = 1_600.0,
    val compactionBridgeCascadeValueWeight: Double = 120.0,
    val compactionBridgeLowTargetBonus: Double = 1_250.0,
    val compactionBridgePositiveOverwritePenalty: Double = -900.0,
    val desirableHiddenPlacementBonus: Double = 1_150.0,
    val tinyRevealedLowLaneReplacementPenalty: Double = -1_400.0,
    val tinyRevealedImprovementThreshold: Int = 2,
    val beamWidth: Int = 6,
    val lookaheadDepth: Int = 1,
    val nearTieEpsilon: Double = 0.001,
    val debugLogging: Boolean = false,
)

data class BotDecision(
    val action: Action,
    val score: Double,
    val candidates: List<ActionEvaluation>,
)

data class ActionEvaluation(
    val action: Action,
    val score: Double,
    val immediateScoreDelta: Double,
    val removedLines: List<RemovalLine>,
    val cascadeCount: Int,
    val finishesRound: Boolean,
    val estimatedBotScore: Double,
    val estimatedOpponentScore: Double,
    val dangerousFinishPenalty: Double,
    val opponentGiftRisk: Double,
    val opponentDenialValue: Double,
    val activePlans: List<RemovalPlan>,
    val negativeCardsRemoved: Int = 0,
    val negativeRemovalScoreLoss: Double = 0.0,
    val negativeCardSeparationScore: Double = 0.0,
    val lowScoreLaneImprovement: Double = 0.0,
    val discardKnownValueUtility: Double = 0.0,
    val deckExpectedUtility: Double = 0.0,
    val isRedundantSameValueReplacement: Boolean = false,
    val isDominatedAction: Boolean = false,
    val actionPurpose: ActionPurpose = ActionPurpose.NORMAL,
    val rejectionReason: RejectionReason? = null,
)

enum class ActionPurpose {
    NORMAL,
    LINE_COMPLETION,
    DENIAL,
    TACTICAL_STALL,
}

enum class RejectionReason {
    REJECT_NEGATIVE_LINE_REMOVAL,
    REJECT_REDUNDANT_IDENTICAL_REPLACEMENT,
    REJECT_DOMINATED_BY_DISCARD_ACTION,
    REJECT_BREAKS_EXISTING_PATTERN,
}

data class RemovalLine(
    val kind: LineKind,
    val indices: List<Int>,
    val value: Int,
)

data class RemovalPlan(
    val kind: LineKind,
    val targetIndices: List<Int>,
    val value: Int,
    val missingIndices: List<Int>,
    val prerequisite: RemovalLine? = null,
    val expectedTurns: Int,
    val confidence: Double,
    val temporaryCost: Double,
)

enum class LineKind {
    ROW,
    COLUMN,
    DIAGONAL,
}

object BotDecisionEngine {
    fun chooseAction(
        state: GameState,
        random: Random = Random.Default,
        config: BotAiConfig = BotAiConfig(),
    ): BotDecision {
        require(state.currentPlayerIndex in state.players.indices) { "Current player index out of bounds" }
        val player = state.players[state.currentPlayerIndex]
        require(player.isBot) { "BotDecisionEngine can only choose actions for bot players" }

        val evaluatedCandidates = legalActions(state).mapNotNull { action ->
            runCatching { BotStateEvaluator.evaluateAction(state, action, config) }.getOrNull()
        }
        val candidates = applyDominance(state, evaluatedCandidates, config)
        val selectableCandidates = candidates.filterNot { it.isDominatedAction || it.rejectionReason != null }
            .ifEmpty { candidates }

        val bestScore = selectableCandidates.maxOfOrNull { it.score }
        val best = if (bestScore == null) {
            fallbackAction(state)
        } else {
            selectableCandidates
                .filter { abs(it.score - bestScore) <= config.nearTieEpsilon }
                .random(random)
                .action
        }

        if (config.debugLogging) {
            BotDecisionLogger.log(state, candidates, best)
        }

        return BotDecision(
            action = best,
            score = candidates.firstOrNull { it.action == best }?.score ?: Double.NEGATIVE_INFINITY,
            candidates = candidates.sortedByDescending { it.score },
        )
    }

    private fun applyDominance(
        state: GameState,
        candidates: List<ActionEvaluation>,
        config: BotAiConfig,
    ): List<ActionEvaluation> {
        val withSameValuePruned = pruneRedundantSameValueReplacements(state, candidates, config)
        return pruneDeckWhenDiscardClearlyDominates(withSameValuePruned)
    }

    private fun pruneRedundantSameValueReplacements(
        state: GameState,
        candidates: List<ActionEvaluation>,
        config: BotAiConfig,
    ): List<ActionEvaluation> {
        if (state.stage != TurnStage.CHOOSE_SWAP_OR_DISCARD) return candidates
        val drawn = state.drawnCard ?: return candidates
        val bot = state.players[state.currentPlayerIndex]
        val usefulNonSamePlacementExists = candidates.any { candidate ->
            val action = candidate.action as? Action.SwapWithGrid ?: return@any false
            val target = bot.grid[action.index]
            target.value != drawn.value &&
                candidate.rejectionReason == null &&
                (
                    candidate.removedLines.any { it.value > 0 } ||
                        candidate.immediateScoreDelta < -0.1 ||
                        candidate.activePlans.any { it.value == drawn.value && it.value > 0 }
                    )
        }
        if (!usefulNonSamePlacementExists) return candidates

        return candidates.map { candidate ->
            val action = candidate.action as? Action.SwapWithGrid ?: return@map candidate
            val target = bot.grid[action.index]
            if (
                target.isRevealed &&
                !target.isCleared &&
                target.value == drawn.value &&
                !candidate.finishesRound &&
                candidate.removedLines.isEmpty() &&
                candidate.actionPurpose != ActionPurpose.TACTICAL_STALL
            ) {
                candidate.copy(
                    score = candidate.score + config.redundantSameValuePenalty,
                    isDominatedAction = true,
                    rejectionReason = RejectionReason.REJECT_REDUNDANT_IDENTICAL_REPLACEMENT,
                )
            } else {
                candidate
            }
        }
    }

    private fun pruneDeckWhenDiscardClearlyDominates(candidates: List<ActionEvaluation>): List<ActionEvaluation> {
        val discard = candidates.firstOrNull { it.action == Action.DrawFromDiscard } ?: return candidates
        val deck = candidates.firstOrNull { it.action == Action.DrawFromDeck } ?: return candidates
        if (discard.score <= deck.score + 40.0) return candidates
        return candidates.map { candidate ->
            if (candidate.action == Action.DrawFromDeck) {
                candidate.copy(isDominatedAction = true, rejectionReason = RejectionReason.REJECT_DOMINATED_BY_DISCARD_ACTION)
            } else {
                candidate
            }
        }
    }

    private fun legalActions(state: GameState): List<Action> {
        val bot = state.players[state.currentPlayerIndex]
        return when (state.stage) {
            TurnStage.OPENING_REVEAL -> bot.grid
                .withIndex()
                .filter { !it.value.isCleared && !it.value.isRevealed }
                .map { Action.RevealOpeningBotGrid(bot.id, it.index) }
            TurnStage.DRAW_OR_TAKE -> buildList {
                if (state.deck.isNotEmpty()) add(Action.DrawFromDeck)
                if (state.discardPile.isNotEmpty()) add(Action.DrawFromDiscard)
            }
            TurnStage.CHOOSE_SWAP_OR_DISCARD -> buildList {
                bot.grid.withIndex()
                    .filterNot { it.value.isCleared }
                    .forEach { add(Action.SwapWithGrid(it.index)) }
                if (state.drawnCardCameFromDiscard) {
                    add(Action.ReturnDrawnDiscardCard)
                } else {
                    add(Action.DiscardDrawnCard)
                }
            }
            TurnStage.TURN_END -> if (state.revealRequiredBeforeEndTurn) {
                bot.grid.withIndex()
                    .filter { !it.value.isCleared && !it.value.isRevealed }
                    .map { Action.RevealGrid(it.index) }
            } else {
                listOf(Action.EndTurn)
            }
        }
    }

    private fun fallbackAction(state: GameState): Action = when (state.stage) {
        TurnStage.OPENING_REVEAL -> {
            val bot = state.players[state.currentPlayerIndex]
            val index = bot.grid.indexOfFirst { !it.isCleared && !it.isRevealed }.coerceAtLeast(0)
            Action.RevealOpeningBotGrid(bot.id, index)
        }
        TurnStage.DRAW_OR_TAKE -> if (state.discardPile.isNotEmpty()) Action.DrawFromDiscard else Action.DrawFromDeck
        TurnStage.CHOOSE_SWAP_OR_DISCARD -> {
            val index = state.players[state.currentPlayerIndex].grid.indexOfFirst { !it.isCleared }
            if (index >= 0) Action.SwapWithGrid(index) else Action.DiscardDrawnCard
        }
        TurnStage.TURN_END -> {
            val index = state.players[state.currentPlayerIndex].grid.indexOfFirst { !it.isCleared && !it.isRevealed }
            if (state.revealRequiredBeforeEndTurn && index >= 0) Action.RevealGrid(index) else Action.EndTurn
        }
    }
}

object GameStateSimulator {
    fun simulate(state: GameState, action: Action): GameState {
        val safeState = maskUnknownCards(state)
        return SkyoGame.reduce(safeState, action, Random(0))
    }

    private fun maskUnknownCards(state: GameState): GameState {
        fun Card.knownOrExpected(): Card = if (isRevealed || isCleared) this else copy(value = BotAiDefaults.HiddenCardValue)
        return state.copy(
            players = state.players.map { player ->
                if (player.id == state.players[state.currentPlayerIndex].id) {
                    player.copy(grid = player.grid.map { it.knownOrExpected() })
                } else {
                    player.copy(grid = player.grid.map { if (it.isRevealed || it.isCleared) it else it.copy(value = BotAiDefaults.HiddenCardValue) })
                }
            },
            deck = state.deck.map { Card(BotAiDefaults.HiddenCardValue, isRevealed = false) },
        )
    }
}

object BoardPatternEvaluator {
    fun completedLines(grid: List<Card>): List<RemovalLine> {
        return candidateLines(grid)
            .filter { line ->
                val cards = line.indices.map { grid[it] }
                cards.all { it.isRevealed && !it.isCleared } && cards.map { it.value }.distinct().size == 1
            }
            .map { it.copy(value = grid[it.indices.first()].value) }
    }

    fun nearLines(grid: List<Card>, value: Int? = null): List<RemovalPlan> {
        return candidateLines(grid)
            .mapNotNull { line ->
                val cards = line.indices.map { grid[it] }
                val activeCards = cards.filter { !it.isCleared }
                val revealedValues = activeCards.filter { it.isRevealed }.map { it.value }
                if (revealedValues.isEmpty()) return@mapNotNull null
                val target = value ?: revealedValues.groupingBy { it }.eachCount().maxBy { it.value }.key
                if (value != null && revealedValues.none { it == value }) return@mapNotNull null
                val blockers = activeCards.count { it.isRevealed && it.value != target }
                val matching = activeCards.count { it.isRevealed && it.value == target }
                val missing = line.indices.filter { index ->
                    val card = grid[index]
                    !card.isCleared && (!card.isRevealed || card.value != target)
                }
                if (blockers > 1 || matching < 2 || missing.isEmpty()) return@mapNotNull null
                RemovalPlan(
                    kind = line.kind,
                    targetIndices = line.indices,
                    value = target,
                    missingIndices = missing,
                    expectedTurns = missing.size,
                    confidence = if (blockers == 0) 0.8 else 0.45,
                    temporaryCost = max(0, target) * missing.size.toDouble(),
                )
            }
    }

    fun conditionalDiagonalPlans(grid: List<Card>): List<RemovalPlan> {
        val activeRows = activeRows(grid)
        val activeColumns = activeColumns(grid)
        if (activeRows.size != 3 || activeColumns.size != 4) return emptyList()

        return activeColumns.flatMap { removeColumn ->
            val remainingColumns = activeColumns.filterNot { it == removeColumn }
            diagonalIndexSets(activeRows, remainingColumns).mapNotNull { diagonal ->
                val values = diagonal.map { grid[it] }.filter { it.isRevealed && !it.isCleared }.map { it.value }
                if (values.size < 2) return@mapNotNull null
                val target = values.groupingBy { it }.eachCount().maxBy { it.value }.key
                if (values.count { it == target } < 2) return@mapNotNull null
                val columnIndices = activeRows.map { it * Grid.Columns + removeColumn }
                val columnCards = columnIndices.map { grid[it] }
                val revealedColumnValues = columnCards.filter { it.isRevealed && !it.isCleared }.map { it.value }
                val prereqValue = revealedColumnValues.firstOrNull() ?: return@mapNotNull null
                if (revealedColumnValues.count { it == prereqValue } < 2) return@mapNotNull null
                val prerequisite = RemovalLine(LineKind.COLUMN, columnIndices, prereqValue)
                RemovalPlan(
                    kind = LineKind.DIAGONAL,
                    targetIndices = diagonal,
                    value = target,
                    missingIndices = diagonal.filter { !grid[it].isRevealed || grid[it].value != target },
                    prerequisite = prerequisite,
                    expectedTurns = 2,
                    confidence = 0.5,
                    temporaryCost = max(0, target).toDouble(),
                )
            }
        }
    }

    fun lineMembership(grid: List<Card>, index: Int): List<RemovalPlan> =
        nearLines(grid).filter { index in it.targetIndices || index in it.missingIndices } +
            completedLines(grid).filter { index in it.indices }.map {
                RemovalPlan(it.kind, it.indices, it.value, emptyList(), expectedTurns = 0, confidence = 1.0, temporaryCost = 0.0)
            }

    private fun candidateLines(grid: List<Card>): List<RemovalLine> {
        val rows = activeRows(grid)
        val columns = activeColumns(grid)
        val rowLines = rows.map { row -> RemovalLine(LineKind.ROW, columns.map { row * Grid.Columns + it }, 0) }
        val columnLines = columns.map { column -> RemovalLine(LineKind.COLUMN, rows.map { it * Grid.Columns + column }, 0) }
        val diagonals = if (rows.size == 3 && columns.size == 3 && isFullActiveGrid(grid, rows, columns)) {
            diagonalIndexSets(rows, columns).map { RemovalLine(LineKind.DIAGONAL, it, 0) }
        } else {
            emptyList()
        }
        return (rowLines + columnLines + diagonals).filter { it.indices.size >= Grid.MinClearLineLength }
    }

    private fun diagonalIndexSets(rows: List<Int>, columns: List<Int>): List<List<Int>> = listOf(
        listOf(rows[0] * Grid.Columns + columns[0], rows[1] * Grid.Columns + columns[1], rows[2] * Grid.Columns + columns[2]),
        listOf(rows[0] * Grid.Columns + columns[2], rows[1] * Grid.Columns + columns[1], rows[2] * Grid.Columns + columns[0]),
    )

    private fun isFullActiveGrid(grid: List<Card>, rows: List<Int>, columns: List<Int>): Boolean =
        rows.all { row -> columns.all { column -> !grid[row * Grid.Columns + column].isCleared } }

    fun activeRows(grid: List<Card>): List<Int> = (0 until Grid.Rows).filter { row ->
        (0 until Grid.Columns).any { column -> !grid[row * Grid.Columns + column].isCleared }
    }

    fun activeColumns(grid: List<Card>): List<Int> = (0 until Grid.Columns).filter { column ->
        (0 until Grid.Rows).any { row -> !grid[row * Grid.Columns + column].isCleared }
    }
}

object OpponentThreatEvaluator {
    fun discardGiftRisk(state: GameState, cardValue: Int, config: BotAiConfig): Double {
        return opponents(state).sumOf { opponent ->
            val patternValue = BoardPatternEvaluator.nearLines(opponent.grid, cardValue).sumOf { plan ->
                val orientation = if (plan.kind == LineKind.COLUMN) 1.2 else 1.0
                (20.0 + plan.targetIndices.size * 8.0 + max(0, cardValue) * 2.0) *
                    plan.confidence *
                    orientation *
                    (config.nearLineWeight / 16.0)
            }
            val replacementValue = opponent.grid
                .filter { it.isRevealed && !it.isCleared }
                .maxOfOrNull { (it.value - cardValue).coerceAtLeast(0) }
                ?.toDouble()
                ?: 0.0
            patternValue + replacementValue
        } * finishUrgency(state)
    }

    fun denialValue(state: GameState, cardValue: Int, config: BotAiConfig): Double =
        discardGiftRisk(state, cardValue, config) * 0.85

    fun finishUrgency(state: GameState): Double {
        val currentId = state.players[state.currentPlayerIndex].id
        return state.players.filter { it.id != currentId }.maxOfOrNull { opponent ->
            val hidden = opponent.grid.count { !it.isCleared && !it.isRevealed }
            val visibleScore = opponent.grid.filter { it.isRevealed && !it.isCleared }.sumOf { it.value }
            val nearLines = BoardPatternEvaluator.nearLines(opponent.grid).size
            val hiddenFactor = when (hidden) {
                0 -> 1.0
                1 -> 0.9
                2 -> 0.65
                3 -> 0.42
                else -> 0.2
            }
            (hiddenFactor + nearLines * 0.08 + if (visibleScore <= 8) 0.12 else 0.0).coerceIn(0.0, 1.0)
        } ?: 0.0
    }

    fun estimatedOpponentScore(state: GameState, config: BotAiConfig): Double {
        return opponents(state).minOfOrNull { player ->
            expectedScore(player.grid, config)
        } ?: 0.0
    }

    private fun opponents(state: GameState): List<PlayerState> {
        val currentId = state.players[state.currentPlayerIndex].id
        return state.players.filter { it.id != currentId }
    }
}

object BotStateEvaluator {
    fun evaluateAction(state: GameState, action: Action, config: BotAiConfig): ActionEvaluation {
        if (state.stage == TurnStage.DRAW_OR_TAKE && (action == Action.DrawFromDiscard || action == Action.DrawFromDeck)) {
            return evaluateDrawChoice(state, action, config)
        }

        val beforePlayer = state.players[state.currentPlayerIndex]
        val beforeExpected = expectedScore(beforePlayer.grid, config)
        val beforeDiscardTop = state.discardPile.lastOrNull()?.value
        val after = simulateWithExpectedDraws(state, action, config)
        val afterPlayer = after.players[after.currentPlayerIndex.coerceIn(after.players.indices)]
            .takeIf { it.id == beforePlayer.id }
            ?: after.players.first { it.id == beforePlayer.id }

        val afterExpected = expectedScore(afterPlayer.grid, config)
        val removed = removedLines(beforePlayer.grid, afterPlayer.grid)
        val removedValueUtility = removed.sumOf { line -> line.value * line.indices.size }.toDouble()
        val negativeCardsRemoved = removed.filter { it.value < 0 }.sumOf { it.indices.size }
        val negativeRemovalScoreLoss = removed.filter { it.value < 0 }.sumOf { -it.value * it.indices.size }.toDouble()
        val cascadeCount = max(0, after.discardPile.size - state.discardPile.size - directDiscardCount(state, action))
        val finishes = wouldFinishRound(after, beforePlayer.id)
        val opponentScore = OpponentThreatEvaluator.estimatedOpponentScore(after.copy(currentPlayerIndex = state.currentPlayerIndex), config)
        val dangerousPenalty = if (finishes && afterExpected >= opponentScore) config.dangerousFirstFinishPenalty else 0.0
        val plans = BoardPatternEvaluator.nearLines(afterPlayer.grid) + BoardPatternEvaluator.conditionalDiagonalPlans(afterPlayer.grid)
        val drawnValue = state.drawnCard?.value ?: beforeDiscardTop
        val discardedValue = discardedCardValue(state, after, action)
        val giftRisk = discardedValue?.let { OpponentThreatEvaluator.discardGiftRisk(state, it, config) } ?: 0.0
        val denial = drawnValue?.let { value ->
            val usedCard = action is Action.SwapWithGrid
            if (usedCard) OpponentThreatEvaluator.denialValue(state, value, config) else 0.0
        } ?: 0.0
        val replacedPlanPenalty = replacedPlanPenalty(state, action, config)
        val terminalTimingPenalty = terminalTimingPenalty(state, action, afterExpected, opponentScore, config)
        val revealPositionValue = revealPositionValue(state, action, config)
        val earlyMarginalSwapPenalty = earlyMarginalSwapPenalty(state, action, removed, plans, config)
        val negativeCardSeparationScore = negativeCardSeparationScore(afterPlayer.grid)
        val lowScoreLaneImprovement = lowScoreLaneScore(afterPlayer.grid) - lowScoreLaneScore(beforePlayer.grid)
        val negativePairRiskPenalty = negativePairRiskPenalty(afterPlayer.grid, config)
        val redundantSameValueReplacement = isRedundantSameValueReplacement(state, action, removed, finishes)
        val tacticalStall = redundantSameValueReplacement && isTacticalStall(state, action, afterExpected, opponentScore)
        val placementProgressValue = placementProgressValue(state, action, removed, config)
        val negativePlacementValue = negativePlacementValue(state, action, config)
        val lowCardNegativeLaneValue = lowCardNegativeLaneValue(state, action, config)
        val discardUsefulLowCardPenalty = discardUsefulLowCardPenalty(state, action, config)
        val phaseAwareMediocreSwapPenalty = phaseAwareMediocreSwapPenalty(state, action, removed, plans, config)
        val discardExplorationValue = discardExplorationValue(state, action, config)
        val lateGuaranteedReductionValue = lateGuaranteedReductionValue(state, action, config)
        val negativeCardIncreasePenalty = negativeCardIncreasePenalty(state, action, afterExpected, opponentScore, config)
        val compactionBridgeCascadeValue = compactionBridgeCascadeValue(state, action, config)
        val hiddenPlacementPreference = hiddenPlacementPreference(state, action, removed, plans, config)
        val tinyRevealedLowLanePenalty = tinyRevealedLowLanePenalty(state, action, removed, plans, config)
        val lowCardBonus = drawnValue?.let { value ->
            val baseBonus = config.lowCardBonus[value] ?: 0.0
            when {
                value == 3 && action == Action.DrawFromDiscard -> {
                    if (BoardPatternEvaluator.nearLines(beforePlayer.grid, value).isNotEmpty()) baseBonus + 12.0 else 0.0
                }
                action == Action.DrawFromDiscard || action is Action.SwapWithGrid -> baseBonus
                else -> 0.0
            }
        } ?: 0.0
        val deckFlexibilityBonus = if (action == Action.DrawFromDeck) 2.0 else 0.0
        val highCardPenalty = speculativeHighCardPenalty(action, drawnValue, removed, plans, state, config)
        val lookahead = if (config.lookaheadDepth > 0 && after.stage == TurnStage.TURN_END) {
            plans.sortedByDescending { it.confidence }.take(config.beamWidth).sumOf { it.confidence * 3.0 }
        } else {
            0.0
        }

        val score =
            dangerousPenalty +
                terminalTimingPenalty +
                (afterExpected * config.ownExpectedScoreWeight) +
                ((afterExpected - beforeExpected) * config.immediateScoreDeltaWeight) +
                (removed.filter { it.value >= 0 }.sumOf { it.indices.size } * config.removedCardWeight) +
                (removedValueUtility * config.removalScoreDeltaWeight) +
                (negativeRemovalScoreLoss * config.negativeRemovalPenaltyWeight) +
                (if (negativeCardsRemoved > 0 && removedValueUtility <= 0.0) config.immediateNegativeLinePenalty else 0.0) +
                (cascadeCount * config.cascadeStepWeight) +
                (removed.count { it.kind == LineKind.COLUMN } * config.verticalLineBonus) +
                (plans.filter { it.value >= 0 }.sumOf { it.confidence * (if (it.kind == LineKind.COLUMN) 1.15 else 1.0) } * config.nearLineWeight) +
                (plans.count { it.kind == LineKind.DIAGONAL && it.prerequisite != null } * config.conditionalDiagonalWeight) +
                (highCardExposure(afterPlayer.grid) * config.highCardExposureWeight * (1.0 + OpponentThreatEvaluator.finishUrgency(state))) +
                (giftRisk * config.opponentGiftWeight) +
                (denial * config.opponentDenialWeight) +
                (OpponentThreatEvaluator.finishUrgency(state) * config.opponentFinishRiskWeight) +
                lowCardBonus +
                deckFlexibilityBonus +
                revealPositionValue +
                earlyMarginalSwapPenalty +
                placementProgressValue +
                negativePlacementValue +
                lowCardNegativeLaneValue +
                discardUsefulLowCardPenalty +
                phaseAwareMediocreSwapPenalty +
                discardExplorationValue +
                lateGuaranteedReductionValue +
                negativeCardIncreasePenalty +
                compactionBridgeCascadeValue +
                hiddenPlacementPreference +
                tinyRevealedLowLanePenalty +
                (negativeCardSeparationScore * config.negativeSeparationWeight) +
                (lowScoreLaneImprovement * config.lowScoreLaneImprovementWeight) +
                negativePairRiskPenalty +
                (if (redundantSameValueReplacement && !tacticalStall) config.redundantSameValuePenalty else 0.0) +
                replacedPlanPenalty +
                highCardPenalty +
                lookahead +
                if (finishes && afterExpected < opponentScore) config.safeFinishBonus else 0.0

        val actionPurpose = when {
            tacticalStall -> ActionPurpose.TACTICAL_STALL
            removed.any { it.value > 0 } -> ActionPurpose.LINE_COMPLETION
            denial > 0.0 -> ActionPurpose.DENIAL
            else -> ActionPurpose.NORMAL
        }

        return ActionEvaluation(
            action = action,
            score = score,
            immediateScoreDelta = afterExpected - beforeExpected,
            removedLines = removed,
            cascadeCount = cascadeCount,
            finishesRound = finishes,
            estimatedBotScore = afterExpected,
            estimatedOpponentScore = opponentScore,
            dangerousFinishPenalty = dangerousPenalty,
            opponentGiftRisk = giftRisk,
            opponentDenialValue = denial,
            activePlans = plans,
            negativeCardsRemoved = negativeCardsRemoved,
            negativeRemovalScoreLoss = negativeRemovalScoreLoss,
            negativeCardSeparationScore = negativeCardSeparationScore,
            lowScoreLaneImprovement = lowScoreLaneImprovement,
            isRedundantSameValueReplacement = redundantSameValueReplacement,
            actionPurpose = actionPurpose,
            rejectionReason = when {
                negativeCardsRemoved > 0 && removedValueUtility <= 0.0 && !tacticalStall -> RejectionReason.REJECT_NEGATIVE_LINE_REMOVAL
                redundantSameValueReplacement && !tacticalStall -> RejectionReason.REJECT_REDUNDANT_IDENTICAL_REPLACEMENT
                else -> null
            },
        )
    }

    private fun evaluateDrawChoice(state: GameState, action: Action, config: BotAiConfig): ActionEvaluation {
        val drawnState = simulateWithExpectedDraws(state, action, config)
        val followUps = followUpActions(drawnState).mapNotNull { followUp ->
            runCatching { evaluateAction(drawnState, followUp, config) }.getOrNull()
        }
        val discardValue = state.discardPile.lastOrNull()?.value
        val selectableFollowUps = followUps
            .filter { it.rejectionReason == null && !it.isDominatedAction }
            .ifEmpty { followUps }
        val bestFollowUpPool = if (action == Action.DrawFromDiscard && discardValue != null && discardValue <= 3) {
            selectableFollowUps.filterNot { it.action == Action.ReturnDrawnDiscardCard }.ifEmpty { selectableFollowUps }
        } else {
            selectableFollowUps
        }
        val bestFollowUp = bestFollowUpPool
            .maxByOrNull { it.score }

        val knownUtility = if (action == Action.DrawFromDiscard && bestFollowUp != null && bestFollowUp.action != Action.ReturnDrawnDiscardCard) {
            val baseLowBonus = discardValue?.let { value ->
                when {
                    value <= 2 -> (config.lowCardBonus[value] ?: 0.0) + config.knownVeryLowDiscardBonus
                    value == 3 && discardThreeHasContext(bestFollowUp) -> config.lowCardBonus[value] ?: 0.0
                    else -> 0.0
                }
            } ?: 0.0
            val certainty = if (discardValue != null && (discardValue <= 2 || discardThreeHasContext(bestFollowUp))) {
                config.knownDiscardCertaintyBonus
            } else {
                0.0
            }
            baseLowBonus + certainty
        } else {
            0.0
        }
        val deckUtility = if (action == Action.DrawFromDeck && bestFollowUp != null) bestFollowUp.score else 0.0
        val returnOnlyPenalty = if (action == Action.DrawFromDiscard && bestFollowUp?.action == Action.ReturnDrawnDiscardCard) {
            config.rejectReturnOnlyDiscardChoicePenalty
        } else {
            0.0
        }
        val contextlessDangerousThreePenalty = if (
            action == Action.DrawFromDiscard &&
            discardValue == 3 &&
            bestFollowUp != null &&
            !discardThreeHasImmediatePositiveRemoval(bestFollowUp) &&
            discardThreeHasFinishTimingRisk(state, config)
        ) {
            config.rejectReturnOnlyDiscardChoicePenalty
        } else {
            0.0
        }
        val score = (bestFollowUp?.score ?: Double.NEGATIVE_INFINITY) + knownUtility + returnOnlyPenalty + contextlessDangerousThreePenalty

        return ActionEvaluation(
            action = action,
            score = score,
            immediateScoreDelta = bestFollowUp?.immediateScoreDelta ?: 0.0,
            removedLines = bestFollowUp?.removedLines ?: emptyList(),
            cascadeCount = bestFollowUp?.cascadeCount ?: 0,
            finishesRound = bestFollowUp?.finishesRound ?: false,
            estimatedBotScore = bestFollowUp?.estimatedBotScore ?: expectedScore(state.players[state.currentPlayerIndex].grid, config),
            estimatedOpponentScore = bestFollowUp?.estimatedOpponentScore ?: OpponentThreatEvaluator.estimatedOpponentScore(state, config),
            dangerousFinishPenalty = bestFollowUp?.dangerousFinishPenalty ?: 0.0,
            opponentGiftRisk = bestFollowUp?.opponentGiftRisk ?: 0.0,
            opponentDenialValue = bestFollowUp?.opponentDenialValue ?: 0.0,
            activePlans = bestFollowUp?.activePlans ?: emptyList(),
            negativeCardsRemoved = bestFollowUp?.negativeCardsRemoved ?: 0,
            negativeRemovalScoreLoss = bestFollowUp?.negativeRemovalScoreLoss ?: 0.0,
            negativeCardSeparationScore = bestFollowUp?.negativeCardSeparationScore ?: 0.0,
            lowScoreLaneImprovement = bestFollowUp?.lowScoreLaneImprovement ?: 0.0,
            discardKnownValueUtility = knownUtility,
            deckExpectedUtility = deckUtility,
            actionPurpose = bestFollowUp?.actionPurpose ?: ActionPurpose.NORMAL,
            rejectionReason = bestFollowUp?.rejectionReason ?: if (returnOnlyPenalty != 0.0 || contextlessDangerousThreePenalty != 0.0) {
                RejectionReason.REJECT_DOMINATED_BY_DISCARD_ACTION
            } else {
                null
            },
        )
    }

    private fun discardThreeHasContext(bestFollowUp: ActionEvaluation): Boolean =
        bestFollowUp.immediateScoreDelta < -0.1 ||
            bestFollowUp.removedLines.any { it.value > 0 } ||
            bestFollowUp.activePlans.any { it.value == 3 && it.confidence >= 0.45 }

    private fun discardThreeHasImmediatePositiveRemoval(bestFollowUp: ActionEvaluation): Boolean =
        bestFollowUp.removedLines.any { it.value > 0 }

    private fun discardThreeHasFinishTimingRisk(state: GameState, config: BotAiConfig): Boolean {
        val bot = state.players[state.currentPlayerIndex]
        val hiddenCount = bot.grid.count { !it.isCleared && !it.isRevealed }
        return hiddenCount == 1 && expectedScore(bot.grid, config) >= OpponentThreatEvaluator.estimatedOpponentScore(state, config)
    }

    private fun followUpActions(state: GameState): List<Action> {
        val bot = state.players[state.currentPlayerIndex]
        return when (state.stage) {
            TurnStage.CHOOSE_SWAP_OR_DISCARD -> buildList {
                bot.grid.withIndex().filterNot { it.value.isCleared }.forEach { add(Action.SwapWithGrid(it.index)) }
                if (state.drawnCardCameFromDiscard) add(Action.ReturnDrawnDiscardCard) else add(Action.DiscardDrawnCard)
            }
            else -> emptyList()
        }
    }

    private fun simulateWithExpectedDraws(state: GameState, action: Action, config: BotAiConfig): GameState {
        val simulationState = if (action == Action.DrawFromDeck && state.deck.isNotEmpty()) {
            state.copy(deck = listOf(Card(config.hiddenExpectedValue.toInt(), isRevealed = false)) + state.deck.drop(1))
        } else {
            state
        }
        return GameStateSimulator.simulate(simulationState, action)
    }

    private fun removedLines(before: List<Card>, after: List<Card>): List<RemovalLine> {
        val newlyCleared = before.indices.filter { !before[it].isCleared && after[it].isCleared }
        if (newlyCleared.isEmpty()) return emptyList()
        return newlyCleared
            .groupBy { after[it].value }
            .map { (value, indices) ->
                val rows = indices.map { it / Grid.Columns }.distinct()
                val columns = indices.map { it % Grid.Columns }.distinct()
                val kind = when {
                    columns.size == 1 -> LineKind.COLUMN
                    rows.size == 1 -> LineKind.ROW
                    else -> LineKind.DIAGONAL
                }
                RemovalLine(kind, indices, value)
            }
    }

    private fun directDiscardCount(state: GameState, action: Action): Int = when {
        action == Action.DiscardDrawnCard -> 1
        action is Action.SwapWithGrid -> 1
        state.drawnCardCameFromDiscard && action == Action.ReturnDrawnDiscardCard -> 1
        else -> 0
    }

    private fun discardedCardValue(state: GameState, after: GameState, action: Action): Int? = when (action) {
        Action.DiscardDrawnCard, Action.ReturnDrawnDiscardCard -> state.drawnCard?.value
        is Action.SwapWithGrid -> after.discardPile.lastOrNull()?.value
        else -> null
    }

    private fun wouldFinishRound(after: GameState, botId: Int): Boolean {
        val player = after.players.first { it.id == botId }
        return after.roundFinisherIndex != null || player.grid.filterNot { it.isCleared }.all { it.isRevealed }
    }

    private fun replacedPlanPenalty(state: GameState, action: Action, config: BotAiConfig): Double {
        if (action !is Action.SwapWithGrid) return 0.0
        val grid = state.players[state.currentPlayerIndex].grid
        val protectedPlans = BoardPatternEvaluator.lineMembership(grid, action.index)
        if (protectedPlans.none { it.expectedTurns <= 1 || it.confidence >= 0.75 }) return 0.0
        val drawn = state.drawnCard ?: return 0.0
        return if (protectedPlans.any { plan -> drawn.value != plan.value }) config.preserveNearLineWeight else 0.0
    }

    private fun terminalTimingPenalty(
        state: GameState,
        action: Action,
        afterExpected: Double,
        opponentScore: Double,
        config: BotAiConfig,
    ): Double {
        val bot = state.players[state.currentPlayerIndex]
        val hiddenCount = bot.grid.count { !it.isCleared && !it.isRevealed }
        if (hiddenCount != 1) return 0.0
        val revealsLastCard = action is Action.RevealGrid || (action is Action.SwapWithGrid && !bot.grid[action.index].isRevealed)
        return if (revealsLastCard && afterExpected >= opponentScore) config.revealHiddenPenaltyWhenBehind else 0.0
    }

    private fun placementProgressValue(
        state: GameState,
        action: Action,
        removed: List<RemovalLine>,
        config: BotAiConfig,
    ): Double {
        if (action !is Action.SwapWithGrid) return 0.0
        val drawn = state.drawnCard ?: return 0.0
        if (drawn.value <= 0) return 0.0
        val grid = state.players[state.currentPlayerIndex].grid
        val target = grid.getOrNull(action.index) ?: return 0.0
        if (target.value == drawn.value && target.isRevealed) return 0.0

        val fillsKnownPattern = BoardPatternEvaluator.nearLines(grid, drawn.value)
            .any { action.index in it.missingIndices && it.value > 0 }
        val completesPositiveLine = removed.any { it.value == drawn.value && it.value > 0 }

        return (if (fillsKnownPattern) config.fillPositivePatternBonus else 0.0) +
            (if (completesPositiveLine) config.completePositiveLineBonus else 0.0)
    }

    private fun negativePlacementValue(state: GameState, action: Action, config: BotAiConfig): Double {
        if (action !is Action.SwapWithGrid) return 0.0
        val drawn = state.drawnCard ?: return 0.0
        if (drawn.value >= 0) return 0.0
        val grid = state.players[state.currentPlayerIndex].grid
        val target = grid.getOrNull(action.index) ?: return 0.0
        if (target.isRevealed && target.value == drawn.value) return config.negativeSameLanePlacementPenalty

        val sameNegativeIndices = grid.withIndex()
            .filter { it.value.isRevealed && !it.value.isCleared && it.value.value == drawn.value }
            .map { it.index }
        if (sameNegativeIndices.isEmpty()) return 0.0

        val row = action.index / Grid.Columns
        val column = action.index % Grid.Columns
        val sharesLane = sameNegativeIndices.any { it / Grid.Columns == row || it % Grid.Columns == column }
        val replacementValue = if (target.isRevealed) target.value else BotAiDefaults.HiddenCardValue
        return if (sharesLane) {
            config.negativeSameLanePlacementPenalty
        } else {
            config.negativeSplitPlacementBonus + max(0, replacementValue).toDouble()
        }
    }

    private fun lowCardNegativeLaneValue(state: GameState, action: Action, config: BotAiConfig): Double {
        if (action !is Action.SwapWithGrid) return 0.0
        val drawn = state.drawnCard ?: return 0.0
        if (drawn.value !in 0..3) return 0.0
        val grid = state.players[state.currentPlayerIndex].grid
        val target = grid.getOrNull(action.index) ?: return 0.0
        if (!target.isRevealed || target.value <= drawn.value) return 0.0

        val row = action.index / Grid.Columns
        val column = action.index % Grid.Columns
        val negativeInLane =
            (0 until Grid.Columns).any { probeColumn ->
                val card = grid[row * Grid.Columns + probeColumn]
                card.isRevealed && !card.isCleared && card.value < 0
            } ||
                (0 until Grid.Rows).any { probeRow ->
                    val card = grid[probeRow * Grid.Columns + column]
                    card.isRevealed && !card.isCleared && card.value < 0
                }
        return if (negativeInLane) config.lowCardIntoNegativeLaneBonus else 0.0
    }

    private fun discardUsefulLowCardPenalty(state: GameState, action: Action, config: BotAiConfig): Double {
        if (action != Action.DiscardDrawnCard) return 0.0
        val drawn = state.drawnCard ?: return 0.0
        val bot = state.players[state.currentPlayerIndex]
        val hiddenCount = bot.grid.count { !it.isCleared && !it.isRevealed }
        val opponentScore = OpponentThreatEvaluator.estimatedOpponentScore(state, config)
        val ownScore = expectedScore(bot.grid, config)
        val finalRevealRisk = hiddenCount == 1 && ownScore >= opponentScore
        val lowCardPenalty = if (drawn.value <= 2) config.discardUsefulLowCardPenalty else 0.0
        val finalRevealPenalty = if (finalRevealRisk) config.revealHiddenPenaltyWhenBehind else 0.0
        return lowCardPenalty + finalRevealPenalty
    }

    private fun phaseAwareMediocreSwapPenalty(
        state: GameState,
        action: Action,
        removed: List<RemovalLine>,
        plans: List<RemovalPlan>,
        config: BotAiConfig,
    ): Double {
        if (action !is Action.SwapWithGrid) return 0.0
        val drawn = state.drawnCard ?: return 0.0
        if (drawn.value !in 4..6) return 0.0
        val bot = state.players[state.currentPlayerIndex]
        val target = bot.grid.getOrNull(action.index) ?: return 0.0
        if (!target.isRevealed || target.isCleared || target.value <= drawn.value) return 0.0
        if (removed.any { it.value > 0 }) return 0.0
        if (plans.any { it.value == drawn.value && it.expectedTurns <= 1 && it.confidence >= 0.7 }) return 0.0

        val hiddenRatio = hiddenRatio(bot.grid)
        val opponentUrgency = OpponentThreatEvaluator.finishUrgency(state)
        val earlyExplorationPressure = ((hiddenRatio - config.earlyHiddenRatioThreshold) / (1.0 - config.earlyHiddenRatioThreshold))
            .coerceIn(0.0, 1.0)
        val urgencyDiscount = (1.0 - opponentUrgency).coerceIn(0.0, 1.0)

        return config.mediocreCardEarlySwapPenalty * earlyExplorationPressure * urgencyDiscount
    }

    private fun discardExplorationValue(state: GameState, action: Action, config: BotAiConfig): Double {
        if (action != Action.DiscardDrawnCard) return 0.0
        val bot = state.players[state.currentPlayerIndex]
        val hiddenRatio = hiddenRatio(bot.grid)
        val opponentUrgency = OpponentThreatEvaluator.finishUrgency(state)
        val explorationPhase = ((hiddenRatio - config.lateHiddenRatioThreshold) / (1.0 - config.lateHiddenRatioThreshold))
            .coerceIn(0.0, 1.0)
        val urgencyDiscount = (1.0 - opponentUrgency).coerceIn(0.0, 1.0)
        val bestReveal = bot.grid.indices
            .filter { !bot.grid[it].isCleared && !bot.grid[it].isRevealed }
            .maxOfOrNull { revealPositionValue(bot.grid, it, config) }
            ?: 0.0

        return bestReveal * config.explorationRevealWeight * explorationPhase * urgencyDiscount
    }

    private fun lateGuaranteedReductionValue(state: GameState, action: Action, config: BotAiConfig): Double {
        if (action !is Action.SwapWithGrid) return 0.0
        val drawn = state.drawnCard ?: return 0.0
        val bot = state.players[state.currentPlayerIndex]
        val target = bot.grid.getOrNull(action.index) ?: return 0.0
        if (!target.isRevealed || target.isCleared || target.value <= drawn.value) return 0.0

        val hiddenRatio = hiddenRatio(bot.grid)
        val latePhase = ((config.earlyHiddenRatioThreshold - hiddenRatio) / config.earlyHiddenRatioThreshold)
            .coerceIn(0.0, 1.0)
        val urgency = OpponentThreatEvaluator.finishUrgency(state)
        val reduction = (target.value - drawn.value).toDouble()

        return reduction * config.lateGuaranteedReductionWeight * (latePhase + urgency)
    }

    private fun negativeCardIncreasePenalty(
        state: GameState,
        action: Action,
        afterExpected: Double,
        opponentScore: Double,
        config: BotAiConfig,
    ): Double {
        if (action !is Action.SwapWithGrid) return 0.0
        val drawn = state.drawnCard ?: return 0.0
        val bot = state.players[state.currentPlayerIndex]
        val target = bot.grid.getOrNull(action.index) ?: return 0.0
        if (!target.isRevealed || target.isCleared || target.value >= 0 || drawn.value <= target.value) return 0.0

        val hiddenCount = bot.grid.count { !it.isCleared && !it.isRevealed }
        val preventsDangerousFinish = hiddenCount == 1 && afterExpected < opponentScore
        return if (preventsDangerousFinish) {
            config.negativeCardIncreasePenalty * 0.2
        } else {
            config.negativeCardIncreasePenalty * (drawn.value - target.value)
        }
    }

    private fun compactionBridgeCascadeValue(state: GameState, action: Action, config: BotAiConfig): Double {
        if (action !is Action.SwapWithGrid) return 0.0
        val drawn = state.drawnCard ?: return 0.0
        if (drawn.value <= 0) return 0.0
        val opponentUrgency = OpponentThreatEvaluator.finishUrgency(state)
        if (opponentUrgency > 0.65) return 0.0

        val grid = state.players[state.currentPlayerIndex].grid.toMutableList()
        val target = grid.getOrNull(action.index) ?: return 0.0
        if (target.isCleared) return 0.0
        if (target.isRevealed && target.value == drawn.value) return 0.0
        grid[action.index] = drawn.copy(isRevealed = true, isCleared = false)

        val rowBridge = rowCompactionBridgeValue(grid, action.index, drawn.value, config)
        val columnBridge = columnCompactionBridgeValue(grid, action.index, drawn.value, config)
        val bridgeValue = rowBridge + columnBridge
        if (bridgeValue <= 0.0) return 0.0
        val targetQuality = when {
            target.isRevealed && target.value <= 0 -> config.compactionBridgeLowTargetBonus
            target.isRevealed && target.value > drawn.value -> config.compactionBridgePositiveOverwritePenalty
            else -> 0.0
        }

        return (bridgeValue + targetQuality) * (1.0 - opponentUrgency)
    }

    private fun hiddenPlacementPreference(
        state: GameState,
        action: Action,
        removed: List<RemovalLine>,
        plans: List<RemovalPlan>,
        config: BotAiConfig,
    ): Double {
        if (action !is Action.SwapWithGrid) return 0.0
        val drawn = state.drawnCard ?: return 0.0
        if (drawn.value !in -2..2) return 0.0
        val bot = state.players[state.currentPlayerIndex]
        val target = bot.grid.getOrNull(action.index) ?: return 0.0
        if (target.isCleared || target.isRevealed) return 0.0
        if (removed.any { it.value > 0 }) return 0.0
        val hiddenCount = bot.grid.count { !it.isCleared && !it.isRevealed }
        val wouldFinish = hiddenCount == 1
        if (wouldFinish) return 0.0

        val hiddenRatio = hiddenRatio(bot.grid)
        val explorationPhase = ((hiddenRatio - config.lateHiddenRatioThreshold) / (1.0 - config.lateHiddenRatioThreshold))
            .coerceIn(0.0, 1.0)
        val planContext = if (plans.any { it.value == drawn.value && action.index in it.targetIndices }) 0.35 else 0.0
        return config.desirableHiddenPlacementBonus * (explorationPhase + planContext)
    }

    private fun tinyRevealedLowLanePenalty(
        state: GameState,
        action: Action,
        removed: List<RemovalLine>,
        plans: List<RemovalPlan>,
        config: BotAiConfig,
    ): Double {
        if (action !is Action.SwapWithGrid) return 0.0
        val drawn = state.drawnCard ?: return 0.0
        if (drawn.value !in -2..2) return 0.0
        val bot = state.players[state.currentPlayerIndex]
        val target = bot.grid.getOrNull(action.index) ?: return 0.0
        if (!target.isRevealed || target.isCleared || target.value <= drawn.value) return 0.0
        val improvement = target.value - drawn.value
        if (improvement > config.tinyRevealedImprovementThreshold) return 0.0
        if (removed.any { it.value > 0 }) return 0.0
        if (plans.any { it.value == drawn.value && it.expectedTurns <= 1 && it.confidence >= 0.7 }) return 0.0

        val hiddenRatioValue = hiddenRatio(bot.grid)
        val opponentUrgency = OpponentThreatEvaluator.finishUrgency(state)
        val hiddenAlternatives = bot.grid.any { !it.isCleared && !it.isRevealed }
        val latePhase = ((config.earlyHiddenRatioThreshold - hiddenRatioValue) / config.earlyHiddenRatioThreshold)
            .coerceIn(0.0, 1.0)
        val earlyPressure = ((hiddenRatioValue - config.lateHiddenRatioThreshold) / (1.0 - config.lateHiddenRatioThreshold))
            .coerceIn(0.0, 1.0)
        val preserveLowLane = lowLanePreservationPressure(bot.grid, action.index)
        val canPreferHidden = hiddenAlternatives && opponentUrgency < 0.65 && latePhase < 0.55
        if (!canPreferHidden && preserveLowLane <= 0.0) return 0.0

        return config.tinyRevealedLowLaneReplacementPenalty * (earlyPressure + preserveLowLane)
    }

    private fun lowLanePreservationPressure(grid: List<Card>, index: Int): Double {
        val row = index / Grid.Columns
        val column = index % Grid.Columns
        val laneValues = buildList {
            (0 until Grid.Columns).forEach { probeColumn ->
                val card = grid[row * Grid.Columns + probeColumn]
                if (card.isRevealed && !card.isCleared) add(card.value)
            }
            (0 until Grid.Rows).forEach { probeRow ->
                val card = grid[probeRow * Grid.Columns + column]
                if (card.isRevealed && !card.isCleared) add(card.value)
            }
        }
        val hasNegative = laneValues.any { it < 0 }
        val allLow = laneValues.filter { it != grid[index].value }.all { it <= 2 }
        return if (hasNegative && allLow) 0.75 else 0.0
    }

    private fun rowCompactionBridgeValue(grid: List<Card>, placedIndex: Int, value: Int, config: BotAiConfig): Double {
        val placedRow = placedIndex / Grid.Columns
        val column = placedIndex % Grid.Columns
        return (0 until Grid.Rows).sumOf { otherRow ->
            if (otherRow == placedRow) return@sumOf 0.0
            val otherIndex = otherRow * Grid.Columns + column
            val other = grid[otherIndex]
            if (!other.isRevealed || other.isCleared || other.value != value) return@sumOf 0.0
            val prerequisiteRows = rowsBetween(placedRow, otherRow)
            prerequisiteRows.sumOf { prereqRow ->
                val prerequisite = prerequisiteRowScore(grid, prereqRow)
                if (prerequisite.confidence <= 0.0) {
                    0.0
                } else {
                    config.compactionBridgeCascadeBonus * prerequisite.confidence +
                        config.compactionBridgeCascadeValueWeight * value +
                        prerequisite.positiveValue * 12.0
                }
            }
        }
    }

    private fun columnCompactionBridgeValue(grid: List<Card>, placedIndex: Int, value: Int, config: BotAiConfig): Double {
        val row = placedIndex / Grid.Columns
        val placedColumn = placedIndex % Grid.Columns
        return (0 until Grid.Columns).sumOf { otherColumn ->
            if (otherColumn == placedColumn) return@sumOf 0.0
            val otherIndex = row * Grid.Columns + otherColumn
            val other = grid[otherIndex]
            if (!other.isRevealed || other.isCleared || other.value != value) return@sumOf 0.0
            val prerequisiteColumns = columnsBetween(placedColumn, otherColumn)
            prerequisiteColumns.sumOf { prereqColumn ->
                val prerequisite = prerequisiteColumnScore(grid, prereqColumn)
                if (prerequisite.confidence <= 0.0) {
                    0.0
                } else {
                    config.compactionBridgeCascadeBonus * prerequisite.confidence * 0.85 +
                        config.compactionBridgeCascadeValueWeight * value * 0.85 +
                        prerequisite.positiveValue * 10.0
                }
            }
        }
    }

    private fun rowsBetween(first: Int, second: Int): IntRange =
        (minOf(first, second) + 1) until maxOf(first, second)

    private fun columnsBetween(first: Int, second: Int): IntRange =
        (minOf(first, second) + 1) until maxOf(first, second)

    private fun prerequisiteRowScore(grid: List<Card>, row: Int): PrerequisiteScore {
        val activeColumns = BoardPatternEvaluator.activeColumns(grid)
        val cards = activeColumns.map { column -> grid[row * Grid.Columns + column] }.filterNot { it.isCleared }
        return prerequisiteScore(cards)
    }

    private fun prerequisiteColumnScore(grid: List<Card>, column: Int): PrerequisiteScore {
        val activeRows = BoardPatternEvaluator.activeRows(grid)
        val cards = activeRows.map { row -> grid[row * Grid.Columns + column] }.filterNot { it.isCleared }
        return prerequisiteScore(cards)
    }

    private fun prerequisiteScore(cards: List<Card>): PrerequisiteScore {
        val revealedPositive = cards.filter { it.isRevealed && it.value > 0 }
        if (revealedPositive.size < 2) return PrerequisiteScore.None
        val best = revealedPositive.groupingBy { it.value }.eachCount().maxBy { it.value }
        val blockers = cards.count { it.isRevealed && it.value != best.key }
        if (blockers > 1) return PrerequisiteScore.None
        val confidence = when (blockers) {
            0 -> if (best.value == cards.size) 1.0 else 0.72
            else -> 0.42
        }
        return PrerequisiteScore(confidence = confidence, positiveValue = best.key * best.value.toDouble())
    }

    private data class PrerequisiteScore(
        val confidence: Double,
        val positiveValue: Double,
    ) {
        companion object {
            val None = PrerequisiteScore(0.0, 0.0)
        }
    }

    private fun negativeCardSeparationScore(grid: List<Card>): Double {
        val negativeIndices = grid.withIndex()
            .filter { it.value.isRevealed && !it.value.isCleared && it.value.value < 0 }
            .map { it.index to it.value.value }
        if (negativeIndices.size <= 1) return 0.0

        return negativeIndices.sumOf { (index, value) ->
            val row = index / Grid.Columns
            val column = index % Grid.Columns
            val matchingInRowOrColumn = negativeIndices.count { (otherIndex, otherValue) ->
                otherIndex != index &&
                    otherValue == value &&
                    (otherIndex / Grid.Columns == row || otherIndex % Grid.Columns == column)
            }
            if (matchingInRowOrColumn == 0) 1.0 else -matchingInRowOrColumn.toDouble()
        }
    }

    private fun lowScoreLaneScore(grid: List<Card>): Double {
        val negativeIndices = grid.withIndex()
            .filter { it.value.isRevealed && !it.value.isCleared && it.value.value < 0 }
            .map { it.index }

        return negativeIndices.sumOf { index ->
            val row = index / Grid.Columns
            val column = index % Grid.Columns
            val rowScore = (0 until Grid.Columns).sumOf { probeColumn ->
                val card = grid[row * Grid.Columns + probeColumn]
                if (!card.isCleared) visibleOrExpectedLaneValue(card) else 0.0
            }
            val columnScore = (0 until Grid.Rows).sumOf { probeRow ->
                val card = grid[probeRow * Grid.Columns + column]
                if (!card.isCleared) visibleOrExpectedLaneValue(card) else 0.0
            }
            -(rowScore + columnScore)
        }
    }

    private fun visibleOrExpectedLaneValue(card: Card): Double =
        if (card.isRevealed) card.value.toDouble() else BotAiDefaults.HiddenCardValue.toDouble()

    private fun negativePairRiskPenalty(grid: List<Card>, config: BotAiConfig): Double {
        return BoardPatternEvaluator.nearLines(grid)
            .filter { it.value < 0 }
            .sumOf { plan ->
                config.negativePairRiskPenalty * plan.confidence * (1 + plan.missingIndices.size)
            }
    }

    private fun isRedundantSameValueReplacement(
        state: GameState,
        action: Action,
        removed: List<RemovalLine>,
        finishes: Boolean,
    ): Boolean {
        if (action !is Action.SwapWithGrid) return false
        val drawn = state.drawnCard ?: return false
        val target = state.players[state.currentPlayerIndex].grid.getOrNull(action.index) ?: return false
        return target.isRevealed &&
            !target.isCleared &&
            target.value == drawn.value &&
            removed.isEmpty() &&
            !finishes
    }

    private fun isTacticalStall(
        state: GameState,
        action: Action,
        afterExpected: Double,
        opponentScore: Double,
    ): Boolean {
        if (action !is Action.SwapWithGrid) return false
        val bot = state.players[state.currentPlayerIndex]
        val hiddenCount = bot.grid.count { !it.isCleared && !it.isRevealed }
        val target = bot.grid.getOrNull(action.index) ?: return false
        val drawn = state.drawnCard ?: return false
        val hasUsefulNonTerminalPlacement = bot.grid.withIndex().any { (index, card) ->
            index != action.index &&
                !card.isCleared &&
                (
                    (card.isRevealed && card.value > drawn.value) ||
                        BoardPatternEvaluator.nearLines(bot.grid, drawn.value).any { index in it.missingIndices && it.value > 0 }
                    )
        }
        return hiddenCount == 1 &&
            target.isRevealed &&
            target.value == drawn.value &&
            afterExpected >= opponentScore &&
            !hasUsefulNonTerminalPlacement
    }

    private fun revealPositionValue(state: GameState, action: Action, config: BotAiConfig): Double {
        if (action !is Action.RevealGrid) return 0.0
        val grid = state.players[state.currentPlayerIndex].grid
        val index = action.index
        if (index !in grid.indices || grid[index].isCleared || grid[index].isRevealed) return 0.0
        return revealPositionValue(grid, index, config)
    }

    private fun revealPositionValue(grid: List<Card>, index: Int, config: BotAiConfig): Double {
        val row = index / Grid.Columns
        val column = index % Grid.Columns
        val adjacentVertical = listOf(row - 1, row + 1).count { adjacentRow ->
            adjacentRow in 0 until Grid.Rows &&
                grid[adjacentRow * Grid.Columns + column].let { it.isRevealed && !it.isCleared }
        }
        val adjacentHorizontal = listOf(column - 1, column + 1).count { adjacentColumn ->
            adjacentColumn in 0 until Grid.Columns &&
                grid[row * Grid.Columns + adjacentColumn].let { it.isRevealed && !it.isCleared }
        }
        val lineProbeBonus = revealLineProbeBonus(grid, index, config)
        val matchingNeighborBonus = matchingNeighborRevealBonus(grid, index, config)
        val betweenMatchingBonus = betweenMatchingRevealBonus(grid, index, config)
        val nearHighBonus = nearHighCardRevealBonus(grid, index, config)
        val patternLaneBonus = patternLaneRevealBonus(grid, index, config)
        val isolatedPenalty = if (adjacentVertical == 0 && adjacentHorizontal == 0 && lineProbeBonus == 0.0) {
            config.isolatedRevealPenalty
        } else {
            0.0
        }

        return adjacentVertical * config.revealAdjacentVerticalBonus +
            adjacentHorizontal * config.revealAdjacentHorizontalBonus +
            lineProbeBonus +
            matchingNeighborBonus +
            betweenMatchingBonus +
            nearHighBonus +
            patternLaneBonus +
            isolatedPenalty
    }

    private fun matchingNeighborRevealBonus(grid: List<Card>, index: Int, config: BotAiConfig): Double {
        val row = index / Grid.Columns
        val column = index % Grid.Columns
        val neighborValues = adjacentIndices(row, column)
            .map { grid[it] }
            .filter { it.isRevealed && !it.isCleared }
            .map { it.value }
        return neighborValues.groupingBy { it }.eachCount().values.maxOrNull()?.let {
            if (it >= 1) config.revealMatchingNeighborBonus * it else 0.0
        } ?: 0.0
    }

    private fun betweenMatchingRevealBonus(grid: List<Card>, index: Int, config: BotAiConfig): Double {
        val row = index / Grid.Columns
        val column = index % Grid.Columns
        val horizontal = if (column in 1 until Grid.Columns - 1) {
            val left = grid[row * Grid.Columns + column - 1]
            val right = grid[row * Grid.Columns + column + 1]
            left.isRevealed && !left.isCleared && right.isRevealed && !right.isCleared && left.value == right.value
        } else {
            false
        }
        val vertical = if (row == 1) {
            val above = grid[column]
            val below = grid[2 * Grid.Columns + column]
            above.isRevealed && !above.isCleared && below.isRevealed && !below.isCleared && above.value == below.value
        } else {
            false
        }
        return when {
            vertical -> config.revealBetweenMatchingBonus * 1.25
            horizontal -> config.revealBetweenMatchingBonus
            else -> 0.0
        }
    }

    private fun nearHighCardRevealBonus(grid: List<Card>, index: Int, config: BotAiConfig): Double {
        val row = index / Grid.Columns
        val column = index % Grid.Columns
        return adjacentIndices(row, column)
            .map { grid[it] }
            .filter { it.isRevealed && !it.isCleared && it.value >= 8 }
            .sumOf { (it.value - 7).toDouble() * config.revealNearHighCardBonus }
    }

    private fun patternLaneRevealBonus(grid: List<Card>, index: Int, config: BotAiConfig): Double {
        val plans = BoardPatternEvaluator.nearLines(grid)
        return plans
            .filter { index in it.missingIndices && it.value >= 0 }
            .sumOf { it.confidence * config.revealPatternLaneBonus * (if (it.kind == LineKind.COLUMN) 1.15 else 1.0) }
    }

    private fun adjacentIndices(row: Int, column: Int): List<Int> = buildList {
        if (row > 0) add((row - 1) * Grid.Columns + column)
        if (row < Grid.Rows - 1) add((row + 1) * Grid.Columns + column)
        if (column > 0) add(row * Grid.Columns + column - 1)
        if (column < Grid.Columns - 1) add(row * Grid.Columns + column + 1)
    }

    private fun revealLineProbeBonus(grid: List<Card>, index: Int, config: BotAiConfig): Double {
        val row = index / Grid.Columns
        val column = index % Grid.Columns
        val rowRevealed = (0 until Grid.Columns).count { probeColumn ->
            val card = grid[row * Grid.Columns + probeColumn]
            !card.isCleared && card.isRevealed
        }
        val columnRevealed = (0 until Grid.Rows).count { probeRow ->
            val card = grid[probeRow * Grid.Columns + column]
            !card.isCleared && card.isRevealed
        }
        return when {
            columnRevealed > 0 -> config.revealNearLineBonus * columnRevealed
            rowRevealed > 0 -> config.revealNearLineBonus * 0.55 * rowRevealed
            else -> 0.0
        }
    }

    private fun earlyMarginalSwapPenalty(
        state: GameState,
        action: Action,
        removed: List<RemovalLine>,
        plans: List<RemovalPlan>,
        config: BotAiConfig,
    ): Double {
        if (action !is Action.SwapWithGrid) return 0.0
        val drawn = state.drawnCard ?: return 0.0
        val grid = state.players[state.currentPlayerIndex].grid
        val target = grid.getOrNull(action.index) ?: return 0.0
        if (!target.isRevealed || target.isCleared) return 0.0
        val revealedCount = grid.count { it.isRevealed && !it.isCleared }
        if (revealedCount > config.earlyRevealedCardThreshold) return 0.0
        if (removed.isNotEmpty()) return 0.0
        if (plans.any { drawn.value == it.value && it.expectedTurns <= 1 && it.confidence >= 0.7 }) return 0.0
        if (drawn.value <= 3) return 0.0

        val improvement = target.value - drawn.value
        return if (improvement in 1 until config.earlyMinimumRevealedSwapImprovement) {
            config.earlyMarginalRevealedSwapPenalty
        } else {
            0.0
        }
    }

    private fun speculativeHighCardPenalty(
        action: Action,
        value: Int?,
        removed: List<RemovalLine>,
        plans: List<RemovalPlan>,
        state: GameState,
        config: BotAiConfig,
    ): Double {
        if (action !is Action.SwapWithGrid || value == null || value <= 3) return 0.0
        if (removed.any { it.value == value }) return 0.0
        val strongPlan = plans.any { it.value == value && it.expectedTurns <= 1 && it.confidence >= 0.7 }
        return if (!strongPlan && OpponentThreatEvaluator.finishUrgency(state) > 0.55) config.speculativeHighCardPenalty else 0.0
    }

}

private object BotDecisionLogger {
    fun log(state: GameState, candidates: List<ActionEvaluation>, best: Action) {
        val player = state.players[state.currentPlayerIndex]
        println(
            buildString {
                append("BotDecision player=${player.name} stage=${state.stage} best=$best")
                candidates.sortedByDescending { it.score }.forEach { candidate ->
                    appendLine()
                    append(
                        "  action=${candidate.action} score=${"%.2f".format(candidate.score)} " +
                            "delta=${"%.2f".format(candidate.immediateScoreDelta)} " +
                            "removed=${candidate.removedLines} cascades=${candidate.cascadeCount} " +
                            "finish=${candidate.finishesRound} bot=${"%.2f".format(candidate.estimatedBotScore)} " +
                            "opp=${"%.2f".format(candidate.estimatedOpponentScore)} " +
                            "danger=${"%.2f".format(candidate.dangerousFinishPenalty)} " +
                            "gift=${"%.2f".format(candidate.opponentGiftRisk)} " +
                            "denial=${"%.2f".format(candidate.opponentDenialValue)} " +
                            "negativeCardsRemoved=${candidate.negativeCardsRemoved} " +
                            "negativeRemovalScoreLoss=${"%.2f".format(candidate.negativeRemovalScoreLoss)} " +
                            "negativeCardSeparationScore=${"%.2f".format(candidate.negativeCardSeparationScore)} " +
                            "lowScoreLaneImprovement=${"%.2f".format(candidate.lowScoreLaneImprovement)} " +
                            "discardKnownValueUtility=${"%.2f".format(candidate.discardKnownValueUtility)} " +
                            "deckExpectedUtility=${"%.2f".format(candidate.deckExpectedUtility)} " +
                            "isRedundantSameValueReplacement=${candidate.isRedundantSameValueReplacement} " +
                            "isDominatedAction=${candidate.isDominatedAction} " +
                            "actionPurpose=${candidate.actionPurpose} " +
                            "rejectionReason=${candidate.rejectionReason} " +
                            "plans=${candidate.activePlans}",
                    )
                }
            },
        )
    }
}

fun expectedScore(grid: List<Card>, config: BotAiConfig): Double =
    grid.filterNot { it.isCleared }.sumOf { card ->
        if (card.isRevealed) card.value.toDouble() else config.hiddenExpectedValue
    }

private fun highCardExposure(grid: List<Card>): Double =
    grid.filter { it.isRevealed && !it.isCleared && it.value > 3 }.sumOf { (it.value - 3).toDouble() }

private fun hiddenRatio(grid: List<Card>): Double {
    val active = grid.count { !it.isCleared }
    if (active == 0) return 0.0
    return grid.count { !it.isCleared && !it.isRevealed }.toDouble() / active
}

private object BotAiDefaults {
    const val HiddenCardValue = 5
}

private object Grid {
    const val Rows = 3
    const val Columns = 4
    const val MinClearLineLength = 2
}
