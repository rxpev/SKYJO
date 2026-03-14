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

        val hiddenIndex = afterDiscard.players[0].grid.indexOfFirst { !it.isRevealed }
        assertTrue(hiddenIndex >= 0)
        val afterReveal = SkyoGame.reduce(afterDiscard, Action.RevealGrid(hiddenIndex))
        assertTrue(afterReveal.players[0].grid[hiddenIndex].isRevealed)

        val afterEnd = SkyoGame.reduce(afterReveal, Action.EndTurn)
        assertEquals(1, afterEnd.currentPlayerIndex)
        assertEquals(TurnStage.DRAW_OR_TAKE, afterEnd.stage)
    }

    @Test
    fun `illegal action order throws`() {
        val state = SkyoGame.newGame(humanPlayerName = "You", botCount = 1, random = Random(1))

        assertFailsWith<IllegalArgumentException> {
            SkyoGame.reduce(state, Action.SwapWithGrid(0))
        }
    }
}
