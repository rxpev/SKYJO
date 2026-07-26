package com.skyo.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BotAiTest {
    @Test
    fun `low-card preference takes discard values from minus two through two`() {
        (-2..2).forEach { value ->
            val state = chooseDrawState(
                botGrid = gridOf(
                    12, 11, 10, 9,
                    8, 7, 6, 5,
                    4, 3, 2, 1,
                    revealed = true,
                ),
                discard = value,
                opponentGrid = gridOf(
                    12, 12, 12, 12,
                    12, 12, 12, 12,
                    12, 12, 12, 12,
                    revealed = true,
                ),
            )

            val decision = BotDecisionEngine.chooseAction(state)
            assertEquals(Action.DrawFromDiscard, decision.action, "discard value $value")
        }
    }

    @Test
    fun `value three is contextual and weaker than values zero through two`() {
        val neutralThree = chooseDrawState(
            botGrid = gridOf(
                -2, -1, 0, 1,
                2, 3, -2, -1,
                0, 1, 2, 3,
                revealed = true,
            ),
            discard = 3,
        )
        val usefulThree = chooseDrawState(
            botGrid = gridOf(
                3, 4, 5, 6,
                3, 7, 8, 9,
                12, 10, 11, 12,
            ).mapIndexed { index, card -> if (index == 0 || index == 4) card.copy(isRevealed = true) else card },
            discard = 3,
        )

        val neutralDiscardScore = BotDecisionEngine.chooseAction(neutralThree).candidates
            .first { it.action == Action.DrawFromDiscard }
            .discardKnownValueUtility
        val usefulDiscardScore = BotDecisionEngine.chooseAction(usefulThree).candidates
            .first { it.action == Action.DrawFromDiscard }
            .discardKnownValueUtility
        assertTrue(usefulDiscardScore > neutralDiscardScore)
        assertEquals(Action.DrawFromDiscard, BotDecisionEngine.chooseAction(usefulThree).action)
    }

    @Test
    fun `immediate high-card vertical removal can beat low-card replacement`() {
        val state = choosePlacementState(
            drawn = 10,
            botGrid = gridOf(
                10, 1, 2, 3,
                10, 4, 5, 6,
                12, 7, 8, 9,
            ).mapIndexed { index, card -> if (index == 0 || index == 4) card.copy(isRevealed = true) else card },
        )

        assertEquals(Action.SwapWithGrid(8), BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `speculative high card is rejected when opponent is close to finishing`() {
        val state = choosePlacementState(
            drawn = 10,
            botGrid = gridOf(
                10, 1, 2, 3,
                4, 10, 5, 6,
                7, 8, 9, 12,
                revealed = true,
            ),
            opponentGrid = gridOf(
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 1,
            ).mapIndexed { index, card -> if (index == 11) card else card.copy(isRevealed = true) },
        )

        assertEquals(Action.DiscardDrawnCard, BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `picture one cascade is simulated and selected`() {
        val state = choosePlacementState(
            drawn = 9,
            botGrid = gridOf(
                9, 1, 2, 3,
                9, 4, 4, 4,
                12, 5, 6, 7,
            ).mapIndexed { index, card ->
                if (index in setOf(0, 4, 5, 6, 7)) card.copy(isRevealed = true) else card
            },
        )

        val decision = BotDecisionEngine.chooseAction(state)
        assertEquals(Action.SwapWithGrid(8), decision.action)
        val after = GameStateSimulator.simulate(state, decision.action)
        assertTrue(setOf(0, 4, 8, 5, 6, 7).all { after.players[1].grid[it].isCleared })
    }

    @Test
    fun `preserve a planned line instead of replacing it with a lower mismatch`() {
        val state = choosePlacementState(
            drawn = 0,
            botGrid = gridOf(
                10, 6, 7, 8,
                10, 9, 10, 11,
                12, 4, 5, 6,
            ).mapIndexed { index, card -> if (index == 0 || index == 4) card.copy(isRevealed = true) else card },
        )

        val action = BotDecisionEngine.chooseAction(state).action
        assertNotEquals(Action.SwapWithGrid(0), action)
        assertNotEquals(Action.SwapWithGrid(4), action)
    }

    @Test
    fun `opponent card denial keeps a useful five when the cost is lower than the gift`() {
        val state = choosePlacementState(
            drawn = 5,
            botGrid = gridOf(
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                revealed = true,
            ),
            opponentGrid = gridOf(
                5, 1, 2, 3,
                5, 4, 6, 7,
                9, 8, 10, 11,
            ).mapIndexed { index, card -> if (index == 0 || index == 4) card.copy(isRevealed = true) else card },
        )

        assertIs<Action.SwapWithGrid>(BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `no irrational denial keeps harmful high card`() {
        val state = choosePlacementState(
            drawn = 11,
            botGrid = gridOf(
                -2, -1, 0, 1,
                2, 3, 4, 5,
                6, 7, 8, 9,
                revealed = true,
            ),
            opponentGrid = gridOf(
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                revealed = true,
            ),
        )

        assertEquals(Action.DiscardDrawnCard, BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `tactical game extension swaps a revealed high card instead of final hidden card when behind`() {
        val state = choosePlacementState(
            drawn = 0,
            botGrid = gridOf(
                12, 8, 8, 8,
                8, 8, 8, 8,
                8, 8, 8, 5,
            ).mapIndexed { index, card -> if (index == 11) card else card.copy(isRevealed = true) },
            opponentGrid = gridOf(
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                revealed = true,
            ),
        )

        assertEquals(Action.SwapWithGrid(0), BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `safe finish reveals final hidden card when clearly ahead`() {
        val state = choosePlacementState(
            drawn = -2,
            botGrid = gridOf(
                -2, -1, 0, 1,
                2, 3, 4, 5,
                6, 7, 8, 5,
            ).mapIndexed { index, card -> if (index == 11) card else card.copy(isRevealed = true) },
            opponentGrid = gridOf(
                12, 12, 12, 12,
                12, 12, 12, 12,
                12, 12, 12, 12,
                revealed = true,
            ),
        )

        assertEquals(Action.SwapWithGrid(11), BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `diagonal is invalid in a larger active grid`() {
        val grid = gridOf(
            4, 1, 2, 3,
            5, 4, 6, 7,
            8, 9, 4, 10,
            revealed = true,
        )

        assertTrue(BoardPatternEvaluator.completedLines(grid).none { it.kind == LineKind.DIAGONAL })
    }

    @Test
    fun `picture two diagonal cascade removes column then remapped diagonal`() {
        val state = GameState(
            players = listOf(
                PlayerState(id = 0, name = "You", isBot = false, grid = gridOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)),
                PlayerState(
                    id = 1,
                    name = "Bot 1",
                    isBot = true,
                    grid = gridOf(
                        4, 7, 2, 0,
                        5, 2, -2, 0,
                        2, 6, -2, 0,
                    ).mapIndexed { index, card ->
                        if (index in setOf(2, 3, 5, 6, 7, 8, 10)) card.copy(isRevealed = true) else card
                    },
                ),
            ),
            deck = emptyList(),
            discardPile = listOf(Card(3, isRevealed = true)),
            currentPlayerIndex = 1,
            stage = TurnStage.TURN_END,
        )

        val after = SkyoGame.reduce(state, Action.RevealGrid(11), kotlin.random.Random(0))
        val cleared = after.discardPile.drop(1)
        assertEquals(3, cleared.count { it.value == 0 })
        assertEquals(3, cleared.count { it.value == 2 })
        assertEquals(2, cleared.count { it.value == -2 })
        assertEquals(4, after.players[1].grid.count { !it.isCleared })
    }

    @Test
    fun `invalid diagonal preparation is not awarded when prerequisite does not create three by three`() {
        val grid = gridOf(
            2, 9, 4, 7,
            5, 2, 6, 8,
            9, 10, 2, 11,
        ).mapIndexed { index, card -> if (index in setOf(0, 5, 10)) card.copy(isRevealed = true) else card }

        assertTrue(BoardPatternEvaluator.conditionalDiagonalPlans(grid).isEmpty())
    }

    @Test
    fun `last-card safety avoids incomplete plan that finishes badly`() {
        val state = choosePlacementState(
            drawn = 9,
            botGrid = gridOf(
                9, 8, 8, 8,
                8, 9, 8, 8,
                8, 8, 5, 12,
            ).mapIndexed { index, card -> if (index == 10) card else card.copy(isRevealed = true) },
            opponentGrid = gridOf(
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                revealed = true,
            ),
        )

        assertNotEquals(Action.SwapWithGrid(10), BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `multiple cascading removals resolve until stable`() {
        val state = choosePlacementState(
            drawn = 7,
            botGrid = gridOf(
                7, 3, 3, 3,
                7, 4, 4, 4,
                12, 5, 5, 5,
            ).mapIndexed { index, card ->
                if (index in setOf(0, 4, 1, 2, 3, 5, 6, 7)) card.copy(isRevealed = true) else card
            },
        )

        val after = GameStateSimulator.simulate(state, Action.SwapWithGrid(8))
        assertEquals(3, after.discardPile.count { it.value == 7 })
        assertEquals(3, after.discardPile.count { it.value == 3 })
        assertEquals(3, after.discardPile.count { it.value == 4 })
    }

    @Test
    fun `hidden information does not change the current decision`() {
        val first = choosePlacementState(
            drawn = 0,
            botGrid = gridOf(
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
            ).mapIndexed { index, card -> if (index < 4) card.copy(isRevealed = true) else card },
        )
        val second = first.copy(
            players = first.players.map { player ->
                if (!player.isBot) player else player.copy(
                    grid = player.grid.mapIndexed { index, card ->
                        if (card.isRevealed) card else card.copy(value = 12 - index)
                    },
                )
            },
            deck = listOf(Card(-2), Card(-2), Card(-2)),
        )

        assertEquals(
            BotDecisionEngine.chooseAction(first, kotlin.random.Random(9)).action,
            BotDecisionEngine.chooseAction(second, kotlin.random.Random(9)).action,
        )
    }

    @Test
    fun `deterministic testing returns repeatable decision with fixed seed`() {
        val state = choosePlacementState(
            drawn = 1,
            botGrid = gridOf(
                5, 5, 5, 5,
                5, 5, 5, 5,
                5, 5, 5, 5,
                revealed = true,
            ),
        )

        val first = BotDecisionEngine.chooseAction(state, kotlin.random.Random(4)).action
        val second = BotDecisionEngine.chooseAction(state, kotlin.random.Random(4)).action
        assertEquals(first, second)
    }

    @Test
    fun `after discarding reveal near existing revealed cards and prefer vertical probes`() {
        val state = revealRequiredState(
            botGrid = gridOf(
                4, 4, 4, 4,
                4, 4, 4, 4,
                4, 4, 4, 4,
            ).mapIndexed { index, card ->
                if (index == 5) card.copy(isRevealed = true) else card
            },
        )

        val action = BotDecisionEngine.chooseAction(state, kotlin.random.Random(2)).action

        assertTrue(action == Action.RevealGrid(1) || action == Action.RevealGrid(9))
    }

    @Test
    fun `early marginal high swap is rejected when only three cards are revealed`() {
        val state = choosePlacementState(
            drawn = 7,
            botGrid = gridOf(
                9, 4, 2, 5,
                5, 5, 5, 5,
                5, 5, 5, 5,
            ).mapIndexed { index, card ->
                if (index in setOf(0, 1, 2)) card.copy(isRevealed = true) else card
            },
        )

        assertEquals(Action.DiscardDrawnCard, BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `early very low swap is still allowed with only three cards revealed`() {
        val state = choosePlacementState(
            drawn = 5,
            botGrid = gridOf(
                5, 4, 2, 5,
                5, 5, 5, 5,
                9, 5, 5, 5,
            ).mapIndexed { index, card ->
                if (index in setOf(0, 4, 1)) card.copy(isRevealed = true) else card
            },
        )

        assertEquals(Action.SwapWithGrid(8), BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `do not complete a minus two line`() {
        val state = choosePlacementState(
            drawn = -2,
            botGrid = gridOf(
                -2, 4, 9, 10,
                -2, 10, 11, 12,
                8, 7, 8, 9,
                revealed = true,
            ),
        )

        val decision = BotDecisionEngine.chooseAction(state)
        val negativeCompletion = BotStateEvaluator.evaluateAction(state, Action.SwapWithGrid(8), BotAiConfig())

        assertNotEquals(Action.SwapWithGrid(8), decision.action)
        assertTrue(negativeCompletion.negativeCardsRemoved >= 2)
        assertTrue(negativeCompletion.negativeRemovalScoreLoss >= 4.0)
        assertEquals(RejectionReason.REJECT_NEGATIVE_LINE_REMOVAL, negativeCompletion.rejectionReason)
    }

    @Test
    fun `do not complete a minus one line`() {
        val state = choosePlacementState(
            drawn = -1,
            botGrid = gridOf(
                -1, 4, 9, 10,
                -1, 10, 11, 12,
                6, 7, 8, 9,
                revealed = true,
            ),
        )

        assertNotEquals(Action.SwapWithGrid(8), BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `split negative duplicates across different rows and columns`() {
        val state = choosePlacementState(
            drawn = -2,
            botGrid = gridOf(
                -2, 8, 9, 10,
                11, 12, 9, 8,
                7, 6, 10, 11,
                revealed = true,
            ),
        )

        val action = BotDecisionEngine.chooseAction(state).action
        assertIs<Action.SwapWithGrid>(action)
        assertTrue(action.index / 4 != 0 && action.index % 4 != 0)
    }

    @Test
    fun `improve a lane containing a negative card and preserve the negative`() {
        val state = choosePlacementState(
            drawn = 1,
            botGrid = gridOf(
                -2, 9, 7, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                revealed = true,
            ),
        )

        val action = BotDecisionEngine.chooseAction(state).action
        assertIs<Action.SwapWithGrid>(action)
        assertTrue(action.index in setOf(1, 2, 4, 8))
    }

    @Test
    fun `cascade involving a negative line is evaluated by net score`() {
        val state = choosePlacementState(
            drawn = 9,
            botGrid = gridOf(
                9, 9, 9, 5,
                -2, 4, 5, 6,
                -2, 7, 8, 10,
                revealed = true,
            ),
        )

        val evaluation = BotStateEvaluator.evaluateAction(state, Action.SwapWithGrid(3), BotAiConfig())

        assertTrue(evaluation.removedLines.any { it.value == 9 })
        assertTrue(evaluation.negativeCardsRemoved >= 2)
        assertTrue(evaluation.negativeRemovalScoreLoss >= 4.0)
    }

    @Test
    fun `take useful discard three for an existing pattern`() {
        val state = chooseDrawState(
            botGrid = gridOf(
                3, 8, 10, 4,
                3, 6, 7, 8,
                8, 10, 11, 12,
            ).mapIndexed { index, card -> if (index in setOf(0, 4, 8)) card.copy(isRevealed = true) else card },
            discard = 3,
        )

        assertEquals(Action.DrawFromDiscard, BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `take discard three to replace a high card`() {
        val state = chooseDrawState(
            botGrid = gridOf(
                10, 4, 5, 6,
                7, 8, 9, 10,
                11, 12, 4, 5,
                revealed = true,
            ),
            discard = 3,
        )

        assertEquals(Action.DrawFromDiscard, BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `reject harmful discard three that causes dangerous first finish`() {
        val state = chooseDrawState(
            botGrid = gridOf(
                3, 2, 1, 0,
                2, 1, 0, 2,
                1, 0, 2, 5,
            ).mapIndexed { index, card -> if (index == 11) card else card.copy(isRevealed = true) },
            opponentGrid = gridOf(
                -2, -2, -2, -2,
                -2, -2, -2, -2,
                -2, -2, -2, -2,
                revealed = true,
            ),
            discard = 3,
        )

        assertEquals(Action.DrawFromDeck, BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `complete seven seven gap correctly`() {
        val state = choosePlacementState(
            drawn = 7,
            botGrid = gridOf(
                7, 5, 9, 4,
                7, 6, 8, 8,
                10, 10, 11, 12,
            ).mapIndexed { index, card -> if (index == 11) card else card.copy(isRevealed = true) },
        )

        val decision = BotDecisionEngine.chooseAction(state)
        assertEquals(Action.SwapWithGrid(8), decision.action)
        val after = GameStateSimulator.simulate(state, decision.action)
        assertTrue(listOf(0, 4, 8).all { after.players[1].grid[it].isCleared })
    }

    @Test
    fun `identical replacement is dominated when another placement improves the board`() {
        val state = choosePlacementState(
            drawn = 7,
            botGrid = gridOf(
                7, 5, 9, 4,
                7, 6, 8, 8,
                10, 10, 11, 12,
            ).mapIndexed { index, card -> if (index == 11) card else card.copy(isRevealed = true) },
        )

        val candidates = BotDecisionEngine.chooseAction(state).candidates
        val sameValueCandidates = candidates.filter { it.action == Action.SwapWithGrid(0) || it.action == Action.SwapWithGrid(4) }

        assertTrue(sameValueCandidates.isNotEmpty())
        assertTrue(sameValueCandidates.all { it.isDominatedAction || it.rejectionReason == RejectionReason.REJECT_REDUNDANT_IDENTICAL_REPLACEMENT })
    }

    @Test
    fun `identical replacement allowed for tactical stall`() {
        val state = choosePlacementState(
            drawn = 7,
            botGrid = gridOf(
                7, 6, 5, 4,
                3, 2, 1, 0,
                -1, -2, 2, 5,
            ).mapIndexed { index, card -> if (index == 11) card else card.copy(isRevealed = true) },
            opponentGrid = gridOf(
                -2, -2, -2, -2,
                -2, -2, -2, -2,
                -2, -2, -2, -2,
                revealed = true,
            ),
        )

        val decision = BotDecisionEngine.chooseAction(state)

        assertIs<Action.SwapWithGrid>(decision.action)
        assertEquals(ActionPurpose.TACTICAL_STALL, decision.candidates.first { it.action == decision.action }.actionPurpose)
    }

    @Test
    fun `do not stall when ahead`() {
        val state = choosePlacementState(
            drawn = 7,
            botGrid = gridOf(
                7, -2, -1, 0,
                1, 1, 1, 1,
                1, 1, 1, 5,
            ).mapIndexed { index, card -> if (index == 11) card else card.copy(isRevealed = true) },
            opponentGrid = gridOf(
                12, 12, 12, 12,
                12, 12, 12, 12,
                12, 12, 12, 12,
                revealed = true,
            ),
        )

        assertNotEquals(Action.SwapWithGrid(0), BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `same value candidates are eliminated before normal selection`() {
        val state = choosePlacementState(
            drawn = 7,
            botGrid = gridOf(
                7, 5, 9, 4,
                7, 6, 8, 8,
                10, 10, 11, 12,
            ).mapIndexed { index, card -> if (index == 11) card else card.copy(isRevealed = true) },
        )

        val candidates = BotDecisionEngine.chooseAction(state).candidates
        assertEquals(ActionPurpose.LINE_COMPLETION, candidates.first { it.action == Action.SwapWithGrid(8) }.actionPurpose)
        assertTrue(candidates.first { it.action == Action.SwapWithGrid(0) }.isDominatedAction)
        assertTrue(candidates.first { it.action == Action.SwapWithGrid(4) }.isDominatedAction)
    }

    @Test
    fun `known discard three beats unknown deck without seeing next deck card`() {
        val first = chooseDrawState(
            botGrid = gridOf(
                10, 4, 5, 6,
                7, 8, 9, 10,
                11, 12, 4, 5,
                revealed = true,
            ),
            discard = 3,
        )
        val second = first.copy(deck = listOf(Card(-2), Card(-2), Card(-2)))

        assertEquals(Action.DrawFromDiscard, BotDecisionEngine.chooseAction(first).action)
        assertEquals(
            BotDecisionEngine.chooseAction(first, kotlin.random.Random(3)).action,
            BotDecisionEngine.chooseAction(second, kotlin.random.Random(3)).action,
        )
    }

    @Test
    fun `early game discards mediocre five instead of replacing revealed nine`() {
        val state = choosePlacementState(
            drawn = 5,
            botGrid = gridOf(
                9, 8, 8, 4,
                8, 8, 8, 8,
                4, 8, 8, 8,
            ).mapIndexed { index, card ->
                if (index in setOf(0, 3, 8)) card.copy(isRevealed = true) else card
            },
            opponentGrid = gridOf(
                8, 8, 8, 8,
                8, 8, 8, 8,
                8, 8, 8, 8,
            ),
        )

        assertEquals(Action.DiscardDrawnCard, BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `late game may replace revealed nine with mediocre five`() {
        val state = choosePlacementState(
            drawn = 5,
            botGrid = gridOf(
                9, 2, 1, 0,
                3, 4, 2, 1,
                0, 1, 2, 5,
            ).mapIndexed { index, card ->
                if (index in setOf(10, 11)) card else card.copy(isRevealed = true)
            },
            opponentGrid = gridOf(
                4, 4, 4, 4,
                4, 4, 4, 4,
                4, 4, 4, 5,
            ).mapIndexed { index, card ->
                if (index == 11) card else card.copy(isRevealed = true)
            },
        )

        assertEquals(Action.SwapWithGrid(0), BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `mediocre five still completes beneficial line`() {
        val state = choosePlacementState(
            drawn = 5,
            botGrid = gridOf(
                5, 2, 9, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
            ).mapIndexed { index, card ->
                if (index in setOf(0, 4, 1)) card.copy(isRevealed = true) else card
            },
        )

        assertEquals(Action.SwapWithGrid(8), BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `hidden reveal prefers useful adjacency over isolated cell`() {
        val state = revealRequiredState(
            botGrid = gridOf(
                4, 4, 4, 4,
                4, 8, 4, 4,
                4, 4, 4, 4,
            ).mapIndexed { index, card ->
                if (index == 5) card.copy(isRevealed = true) else card
            },
        )

        assertEquals(Action.RevealGrid(1), BotDecisionEngine.chooseAction(state, kotlin.random.Random(1)).action)
    }

    @Test
    fun `hidden reveal decision ignores unseen card value`() {
        val first = revealRequiredState(
            botGrid = gridOf(
                4, 4, 4, 4,
                4, 8, 4, 4,
                4, 4, 4, 4,
            ).mapIndexed { index, card ->
                if (index == 5) card.copy(isRevealed = true) else card
            },
        )
        val second = first.copy(
            players = first.players.map { player ->
                if (!player.isBot) player else player.copy(
                    grid = player.grid.mapIndexed { index, card ->
                        if (card.isRevealed) card else card.copy(value = 12 - index)
                    },
                )
            },
        )

        assertEquals(
            BotDecisionEngine.chooseAction(first, kotlin.random.Random(6)).action,
            BotDecisionEngine.chooseAction(second, kotlin.random.Random(6)).action,
        )
    }

    @Test
    fun `negative cards are not replaced by higher cards`() {
        val state = choosePlacementState(
            drawn = 5,
            botGrid = gridOf(
                -2, 9, 8, 7,
                6, 5, 4, 3,
                2, 1, 0, 8,
                revealed = true,
            ),
        )

        assertNotEquals(Action.SwapWithGrid(0), BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `screenshot board aligns three across planned four row removal`() {
        val state = choosePlacementState(drawn = 3, botGrid = screenshotCascadeBoard())

        assertEquals(Action.SwapWithGrid(10), BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `screenshot board values replacing zero with two because twos meet after compaction`() {
        val state = choosePlacementState(drawn = 2, botGrid = screenshotCascadeBoard())

        assertEquals(Action.SwapWithGrid(11), BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `screenshot board evaluates compaction cascade before choosing placement`() {
        val state = choosePlacementState(drawn = 2, botGrid = screenshotCascadeBoard())
        val bridge = BotStateEvaluator.evaluateAction(state, Action.SwapWithGrid(11), BotAiConfig())
        val unrelated = BotStateEvaluator.evaluateAction(state, Action.SwapWithGrid(5), BotAiConfig())

        assertTrue(bridge.score > unrelated.score)
        assertEquals(Action.SwapWithGrid(11), BotDecisionEngine.chooseAction(state).action)
    }

    @Test
    fun `drawn zero replaces hidden card instead of revealed one with many hidden cells`() {
        val state = choosePlacementState(
            drawn = 0,
            botGrid = screenshotHiddenReplacementBoard(),
        )

        val decision = BotDecisionEngine.chooseAction(state).action

        assertIs<Action.SwapWithGrid>(decision)
        assertNotEquals(1, decision.index)
        assertFalse(state.players[1].grid[decision.index].isRevealed)
    }

    @Test
    fun `hidden replacement decision ignores unseen card value`() {
        val first = choosePlacementState(
            drawn = 0,
            botGrid = screenshotHiddenReplacementBoard(),
        )
        val second = first.copy(
            players = first.players.map { player ->
                if (!player.isBot) player else player.copy(
                    grid = player.grid.mapIndexed { index, card ->
                        if (card.isRevealed) card else card.copy(value = 12 - index)
                    },
                )
            },
        )

        assertEquals(
            BotDecisionEngine.chooseAction(first, kotlin.random.Random(10)).action,
            BotDecisionEngine.chooseAction(second, kotlin.random.Random(10)).action,
        )
    }

    private fun chooseDrawState(
        botGrid: List<Card>,
        discard: Int,
        opponentGrid: List<Card> = gridOf(6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6),
    ): GameState = GameState(
        players = listOf(
            PlayerState(id = 0, name = "You", isBot = false, grid = opponentGrid),
            PlayerState(id = 1, name = "Bot 1", isBot = true, grid = botGrid),
        ),
        deck = listOf(Card(5), Card(5), Card(5)),
        discardPile = listOf(Card(discard, isRevealed = true)),
        currentPlayerIndex = 1,
        stage = TurnStage.DRAW_OR_TAKE,
    )

    private fun choosePlacementState(
        drawn: Int,
        botGrid: List<Card>,
        opponentGrid: List<Card> = gridOf(6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6),
        cameFromDiscard: Boolean = false,
    ): GameState = GameState(
        players = listOf(
            PlayerState(id = 0, name = "You", isBot = false, grid = opponentGrid),
            PlayerState(id = 1, name = "Bot 1", isBot = true, grid = botGrid),
        ),
        deck = listOf(Card(5), Card(5), Card(5)),
        discardPile = listOf(Card(0, isRevealed = true)),
        currentPlayerIndex = 1,
        stage = TurnStage.CHOOSE_SWAP_OR_DISCARD,
        drawnCard = Card(drawn, isRevealed = true),
        drawnCardCameFromDiscard = cameFromDiscard,
    )

    private fun revealRequiredState(
        botGrid: List<Card>,
        opponentGrid: List<Card> = gridOf(6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6),
    ): GameState = GameState(
        players = listOf(
            PlayerState(id = 0, name = "You", isBot = false, grid = opponentGrid),
            PlayerState(id = 1, name = "Bot 1", isBot = true, grid = botGrid),
        ),
        deck = listOf(Card(5), Card(5), Card(5)),
        discardPile = listOf(Card(7, isRevealed = true)),
        currentPlayerIndex = 1,
        stage = TurnStage.TURN_END,
        revealRequiredBeforeEndTurn = true,
    )

    private fun screenshotCascadeBoard(): List<Card> = gridOf(
        5, 4, 3, 2,
        5, 3, 4, 4,
        5, 5, 2, 0,
    ).mapIndexed { index, card ->
        if (index in setOf(1, 2, 3, 5, 6, 7, 10, 11)) card.copy(isRevealed = true) else card
    }

    private fun screenshotHiddenReplacementBoard(): List<Card> = gridOf(
        8, -1, 8, 8,
        8, 2, 8, 8,
        8, 2, 8, 8,
    ).mapIndexed { index, card ->
        if (index in setOf(1, 5, 9)) card.copy(isRevealed = true) else card
    }

    private fun gridOf(vararg values: Int, revealed: Boolean = false): List<Card> =
        values.map { Card(value = it, isRevealed = revealed) }
}
