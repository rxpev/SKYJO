package com.skyo.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkyoGameTest {
    @Test
    fun `new game deals 12 cards and reveals exactly 2 per player`() {
        val state = SkyoGame.newGame(humanPlayerName = "You", botCount = 2, random = Random(7))

        assertEquals(3, state.players.size)
        state.players.forEach { player ->
            assertEquals(12, player.grid.size)
            assertEquals(2, player.grid.count { it.isRevealed })
        }
        assertTrue(state.discardPile.single().isRevealed)
        assertEquals(TurnStage.DRAW_OR_TAKE, state.stage)
    }

    @Test
    fun `new game uses official card distribution`() {
        val state = SkyoGame.newGame(humanPlayerName = "You", botCount = 3, random = Random(7))
        val allCards = state.players.flatMap { it.grid } + state.deck + state.discardPile
        val counts = allCards.groupingBy { it.value }.eachCount()

        assertEquals(150, allCards.size)
        assertEquals(5, counts[-2])
        assertEquals(10, counts[-1])
        assertEquals(15, counts[0])
        (1..12).forEach { value ->
            assertEquals(10, counts[value])
        }
    }

    @Test
    fun `draw from deck then swap updates board and discard`() {
        val state = SkyoGame.newGame(humanPlayerName = "You", botCount = 1, random = Random(3))
        val afterDraw = SkyoGame.reduce(state, Action.DrawFromDeck)

        assertEquals(TurnStage.CHOOSE_SWAP_OR_DISCARD, afterDraw.stage)
        val drawn = assertNotNull(afterDraw.drawnCard)

        val swapIndex = 0
        val swappedOutBefore = afterDraw.players[afterDraw.currentPlayerIndex].grid[swapIndex]
        val afterSwap = SkyoGame.reduce(afterDraw, Action.SwapWithGrid(swapIndex))

        assertEquals(TurnStage.TURN_END, afterSwap.stage)
        assertEquals(drawn.value, afterSwap.players[0].grid[swapIndex].value)
        assertTrue(afterSwap.players[0].grid[swapIndex].isRevealed)
        assertEquals(swappedOutBefore.value, afterSwap.discardPile.last().value)
        assertTrue(afterSwap.discardPile.last().isRevealed)
    }

    @Test
    fun `discard drawn card allows reveal and end turn`() {
        val start = SkyoGame.newGame(humanPlayerName = "You", botCount = 1, random = Random(11))
        val afterDraw = SkyoGame.reduce(start, Action.DrawFromDeck)
        val afterDiscard = SkyoGame.reduce(afterDraw, Action.DiscardDrawnCard)

        assertEquals(TurnStage.TURN_END, afterDiscard.stage)
        assertTrue(afterDiscard.revealRequiredBeforeEndTurn)

        val hiddenIndex = afterDiscard.players[0].grid.indexOfFirst { !it.isRevealed }
        assertTrue(hiddenIndex >= 0)
        val afterReveal = SkyoGame.reduce(afterDiscard, Action.RevealGrid(hiddenIndex))
        assertTrue(afterReveal.players[0].grid[hiddenIndex].isRevealed)

        val afterEnd = SkyoGame.reduce(afterReveal, Action.EndTurn)
        assertEquals(1, afterEnd.currentPlayerIndex)
        assertEquals(TurnStage.DRAW_OR_TAKE, afterEnd.stage)
    }

    @Test
    fun `discard drawn card cannot end turn before revealing a hidden card`() {
        val start = SkyoGame.newGame(humanPlayerName = "You", botCount = 1, random = Random(11))
        val afterDraw = SkyoGame.reduce(start, Action.DrawFromDeck)
        val afterDiscard = SkyoGame.reduce(afterDraw, Action.DiscardDrawnCard)

        assertFailsWith<IllegalArgumentException> {
            SkyoGame.reduce(afterDiscard, Action.EndTurn)
        }
    }

    @Test
    fun `illegal action order throws`() {
        val state = SkyoGame.newGame(humanPlayerName = "You", botCount = 1, random = Random(1))

        assertFailsWith<IllegalArgumentException> {
            SkyoGame.reduce(state, Action.SwapWithGrid(0))
        }
    }

    @Test
    fun `matching revealed column is cleared and removed from score`() {
        val state = GameState(
            players = listOf(
                PlayerState(
                    id = 0,
                    name = "You",
                    isBot = false,
                    grid = gridOf(
                        5, 1, 2, 3,
                        5, 4, 6, 7,
                        5, 8, 10, 11,
                    ).mapIndexed { index, card ->
                        if (index == 0 || index == 4) card.copy(isRevealed = true) else card
                    },
                ),
                PlayerState(id = 1, name = "Bot 1", isBot = true, grid = gridOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)),
            ),
            deck = emptyList(),
            discardPile = listOf(Card(0, isRevealed = true)),
            currentPlayerIndex = 0,
            stage = TurnStage.TURN_END,
        )

        val afterReveal = SkyoGame.reduce(state, Action.RevealGrid(8))
        val playerGrid = afterReveal.players[0].grid

        assertTrue(playerGrid[0].isCleared)
        assertTrue(playerGrid[4].isCleared)
        assertTrue(playerGrid[8].isCleared)
        assertEquals(3, afterReveal.discardPile.takeLast(3).count { it.value == 5 && it.isCleared })
        assertEquals(52, SkyoGame.scoreGrid(playerGrid))
    }

    @Test
    fun `ending a round gives each other player one final turn before scoring`() {
        val state = GameState(
            players = listOf(
                PlayerState(
                    id = 0,
                    name = "You",
                    isBot = false,
                    grid = gridOf(
                        1, 1, 1, 1,
                        1, 1, 1, 1,
                        1, 1, 1, 9,
                        revealed = true,
                    ),
                ),
                PlayerState(
                    id = 1,
                    name = "Bot 1",
                    isBot = true,
                    grid = gridOf(
                        -2, -2, -2, -2,
                        -2, -2, -2, -2,
                        -2, -2, -2, -2,
                    ),
                ),
            ),
            deck = emptyList(),
            discardPile = listOf(Card(0, isRevealed = true)),
            currentPlayerIndex = 0,
            stage = TurnStage.TURN_END,
        )

        val finalTurnStarted = SkyoGame.reduce(state, Action.EndTurn)

        assertEquals(0, finalTurnStarted.roundFinisherIndex)
        assertEquals(1, finalTurnStarted.finalTurnsRemaining)
        assertEquals(1, finalTurnStarted.currentPlayerIndex)
        assertEquals(0, finalTurnStarted.players[0].score)
        assertEquals(0, finalTurnStarted.players[1].score)
        assertEquals(TurnStage.DRAW_OR_TAKE, finalTurnStarted.stage)

        val roundOver = SkyoGame.reduce(finalTurnStarted.copy(stage = TurnStage.TURN_END), Action.EndTurn)
        assertTrue(roundOver.roundEnded)
        assertTrue(roundOver.players.all { player -> player.grid.all { it.isRevealed } })
        assertEquals(40, roundOver.players[0].score)
        assertEquals(-24, roundOver.players[1].score)
    }

    @Test
    fun `players with at least 100 total points lose after round scoring`() {
        val state = GameState(
            players = listOf(
                PlayerState(
                    id = 0,
                    name = "You",
                    isBot = false,
                    grid = gridOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, revealed = true),
                    score = 95,
                ),
                PlayerState(
                    id = 1,
                    name = "Bot 1",
                    isBot = true,
                    grid = gridOf(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, revealed = true),
                    score = 20,
                ),
            ),
            deck = emptyList(),
            discardPile = listOf(Card(0, isRevealed = true)),
            currentPlayerIndex = 1,
            stage = TurnStage.TURN_END,
            roundFinisherIndex = 0,
            finalTurnsRemaining = 1,
        )

        val gameOver = SkyoGame.reduce(state, Action.EndTurn)

        assertTrue(gameOver.gameEnded)
        assertTrue(gameOver.players[0].hasLost)
        assertEquals(107, gameOver.players[0].score)
        assertEquals(44, gameOver.players[1].score)
    }

    @Test
    fun `no actions are allowed after round ends`() {
        val start = SkyoGame.newGame(humanPlayerName = "You", botCount = 1, random = Random(1))
        val roundOver = start.copy(roundEnded = true)

        assertFailsWith<IllegalArgumentException> {
            SkyoGame.reduce(roundOver, Action.DrawFromDeck)
        }
    }

    private fun gridOf(vararg values: Int, revealed: Boolean = false): List<Card> {
        return values.map { Card(value = it, isRevealed = revealed) }
    }
}
