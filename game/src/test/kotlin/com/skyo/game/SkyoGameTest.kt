package com.skyo.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkyoGameTest {
    @Test
    fun `new game deals 12 hidden cards per player and waits for opening reveal`() {
        val state = SkyoGame.newGame(humanPlayerName = "You", botCount = 2, random = Random(7))

        assertEquals(3, state.players.size)
        state.players.forEach { player ->
            assertEquals(12, player.grid.size)
            assertEquals(0, player.grid.count { it.isRevealed })
        }
        assertTrue(state.discardPile.single().isRevealed)
        assertEquals(TurnStage.OPENING_REVEAL, state.stage)
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
        val state = playableNewGame(random = Random(3))
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
        val start = playableNewGame(random = Random(11))
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
        val start = playableNewGame(random = Random(11))
        val afterDraw = SkyoGame.reduce(start, Action.DrawFromDeck)
        val afterDiscard = SkyoGame.reduce(afterDraw, Action.DiscardDrawnCard)

        assertFailsWith<IllegalArgumentException> {
            SkyoGame.reduce(afterDiscard, Action.EndTurn)
        }
    }

    @Test
    fun `return card taken from discard goes back to draw choice without requiring reveal`() {
        val start = playableNewGame(random = Random(11))
        val discard = start.discardPile.last()
        val afterTake = SkyoGame.reduce(start, Action.DrawFromDiscard)

        assertEquals(discard.value, assertNotNull(afterTake.drawnCard).value)
        assertTrue(afterTake.drawnCardCameFromDiscard)
        assertEquals(start.discardPile.dropLast(1), afterTake.discardPile)

        val afterReturn = SkyoGame.reduce(afterTake, Action.ReturnDrawnDiscardCard)

        assertEquals(TurnStage.DRAW_OR_TAKE, afterReturn.stage)
        assertEquals(discard.value, afterReturn.discardPile.last().value)
        assertEquals(null, afterReturn.drawnCard)
        assertFalse(afterReturn.drawnCardCameFromDiscard)
        assertFalse(afterReturn.revealRequiredBeforeEndTurn)
    }

    @Test
    fun `deck draw cannot be returned to discard choice`() {
        val start = playableNewGame(random = Random(11))
        val afterDraw = SkyoGame.reduce(start, Action.DrawFromDeck)

        assertFailsWith<IllegalArgumentException> {
            SkyoGame.reduce(afterDraw, Action.ReturnDrawnDiscardCard)
        }
    }

    @Test
    fun `illegal action order throws`() {
        val state = playableNewGame(random = Random(1))

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
        assertEquals(3, afterReveal.discardPile.takeLast(3).count { it.value == 5 && !it.isCleared })
        assertEquals(52, SkyoGame.scoreGrid(playerGrid))
    }

    @Test
    fun `cleared column cards drawn from discard do not clear the new grid slot`() {
        val state = GameState(
            players = listOf(
                PlayerState(
                    id = 0,
                    name = "You",
                    isBot = false,
                    grid = gridOf(
                        0, 1, 2, 3,
                        0, 4, 6, 7,
                        0, 8, 10, 11,
                    ).mapIndexed { index, card ->
                        if (index == 0 || index == 4) card.copy(isRevealed = true) else card
                    },
                ),
                PlayerState(
                    id = 1,
                    name = "Bot 1",
                    isBot = true,
                    grid = gridOf(
                        4, 4, -1, 3,
                        8, 8, -1, 5,
                        9, 9, 10, 11,
                        revealed = true,
                    ),
                ),
            ),
            deck = emptyList(),
            discardPile = listOf(Card(7, isRevealed = true)),
            currentPlayerIndex = 0,
            stage = TurnStage.TURN_END,
        )

        val afterClear = SkyoGame.reduce(state, Action.RevealGrid(8))
        assertTrue(afterClear.players[0].grid[0].isCleared)
        assertFalse(afterClear.discardPile.last().isCleared)

        val botTurn = afterClear.copy(currentPlayerIndex = 1, stage = TurnStage.DRAW_OR_TAKE)
        val afterDraw = SkyoGame.reduce(botTurn, Action.DrawFromDiscard)
        assertFalse(assertNotNull(afterDraw.drawnCard).isCleared)

        val afterSwap = SkyoGame.reduce(afterDraw, Action.SwapWithGrid(0))
        assertEquals(0, afterSwap.players[1].grid[0].value)
        assertTrue(afterSwap.players[1].grid[0].isRevealed)
        assertFalse(afterSwap.players[1].grid[0].isCleared)
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
    fun `next round preserves totals and redeals hidden cards`() {
        val roundOver = GameState(
            players = listOf(
                PlayerState(
                    id = 0,
                    name = "You",
                    isBot = false,
                    grid = gridOf(5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, revealed = true),
                    score = 60,
                ),
                PlayerState(
                    id = 1,
                    name = "Bot 1",
                    isBot = true,
                    grid = gridOf(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, revealed = true),
                    score = 24,
                ),
            ),
            deck = emptyList(),
            discardPile = listOf(Card(0, isRevealed = true)),
            currentPlayerIndex = 0,
            stage = TurnStage.TURN_END,
            round = 2,
            roundFinisherIndex = 0,
            roundEnded = true,
        )

        val nextRound = SkyoGame.startNextRound(roundOver, random = Random(14))

        assertEquals(3, nextRound.round)
        assertEquals(TurnStage.OPENING_REVEAL, nextRound.stage)
        assertFalse(nextRound.roundEnded)
        assertEquals(60, nextRound.players[0].score)
        assertEquals(24, nextRound.players[1].score)
        nextRound.players.forEach { player ->
            assertEquals(12, player.grid.size)
            assertEquals(0, player.grid.count { it.isRevealed })
        }
        assertTrue(nextRound.discardPile.single().isRevealed)
        assertEquals(null, nextRound.roundFinisherIndex)
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
        val start = SkyoGame.newGame(humanPlayerName = "You", botCount = 1)
        val roundOver = start.copy(roundEnded = true)

        assertFailsWith<IllegalArgumentException> {
            SkyoGame.reduce(roundOver, Action.DrawFromDeck)
        }
    }

    @Test
    fun `human opening reveal resolves starting player by total revealed value`() {
        val state = GameState(
            players = listOf(
                PlayerState(
                    id = 0,
                    name = "You",
                    isBot = false,
                    grid = gridOf(12, 1, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4),
                ),
                PlayerState(
                    id = 1,
                    name = "Bot 1",
                    isBot = true,
                    grid = gridOf(5, 5, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4)
                        .mapIndexed { index, card ->
                            if (index == 0 || index == 1) card.copy(isRevealed = true) else card
                        },
                ),
            ),
            deck = emptyList(),
            discardPile = listOf(Card(0, isRevealed = true)),
            currentPlayerIndex = 0,
            stage = TurnStage.OPENING_REVEAL,
        )

        val afterFirstReveal = SkyoGame.reduce(state, Action.RevealGrid(0))
        assertEquals(TurnStage.OPENING_REVEAL, afterFirstReveal.stage)

        val afterSecondReveal = SkyoGame.reduce(afterFirstReveal, Action.RevealGrid(1))
        assertEquals(TurnStage.DRAW_OR_TAKE, afterSecondReveal.stage)
        assertEquals(0, afterSecondReveal.currentPlayerIndex)
    }

    @Test
    fun `opening reveal uses highest card when totals match`() {
        val state = GameState(
            players = listOf(
                PlayerState(
                    id = 0,
                    name = "You",
                    isBot = false,
                    grid = gridOf(6, 6, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4),
                ),
                PlayerState(
                    id = 1,
                    name = "Bot 1",
                    isBot = true,
                    grid = gridOf(11, 1, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4)
                        .mapIndexed { index, card ->
                            if (index == 0 || index == 1) card.copy(isRevealed = true) else card
                        },
                ),
            ),
            deck = emptyList(),
            discardPile = listOf(Card(0, isRevealed = true)),
            currentPlayerIndex = 0,
            stage = TurnStage.OPENING_REVEAL,
        )

        val afterFirstReveal = SkyoGame.reduce(state, Action.RevealGrid(0))
        val afterSecondReveal = SkyoGame.reduce(afterFirstReveal, Action.RevealGrid(1))

        assertEquals(TurnStage.DRAW_OR_TAKE, afterSecondReveal.stage)
        assertEquals(1, afterSecondReveal.currentPlayerIndex)
    }

    @Test
    fun `exact opening tie requires another reveal from tied players`() {
        val state = GameState(
            players = listOf(
                PlayerState(
                    id = 0,
                    name = "You",
                    isBot = false,
                    grid = gridOf(6, 6, 12, 4, 4, 4, 4, 4, 4, 4, 4, 4),
                ),
                PlayerState(
                    id = 1,
                    name = "Bot 1",
                    isBot = true,
                    grid = gridOf(6, 6, -2, 4, 4, 4, 4, 4, 4, 4, 4, 4)
                        .mapIndexed { index, card ->
                            if (index == 0 || index == 1) card.copy(isRevealed = true) else card
                        },
                ),
            ),
            deck = emptyList(),
            discardPile = listOf(Card(0, isRevealed = true)),
            currentPlayerIndex = 0,
            stage = TurnStage.OPENING_REVEAL,
        )

        val afterFirstReveal = SkyoGame.reduce(state, Action.RevealGrid(0))
        val tied = SkyoGame.reduce(afterFirstReveal, Action.RevealGrid(1))

        assertEquals(TurnStage.OPENING_REVEAL, tied.stage)
        assertEquals(3, tied.openingRevealCount)
        assertEquals(setOf(0, 1), tied.openingContenderIds)
        assertEquals(2, tied.players[1].grid.count { it.isRevealed })

        val botRevealed = SkyoGame.reduce(tied, Action.RevealOpeningBotGrid(playerId = 1, index = 2))
        assertEquals(3, botRevealed.players[1].grid.count { it.isRevealed })

        val resolved = SkyoGame.reduce(botRevealed, Action.RevealGrid(2))
        assertEquals(TurnStage.DRAW_OR_TAKE, resolved.stage)
        assertEquals(0, resolved.currentPlayerIndex)
    }

    @Test
    fun `bot opening reveal action flips one bot card without resolving early`() {
        val state = SkyoGame.newGame(humanPlayerName = "You", botCount = 1, random = Random(4))
        val bot = state.players.single { it.isBot }
        val revealIndex = SkyoGame.chooseOpeningBotRevealIndices(bot, state.openingRevealCount, Random(4)).first()

        val afterReveal = SkyoGame.reduce(state, Action.RevealOpeningBotGrid(bot.id, revealIndex))

        assertTrue(afterReveal.players.single { it.isBot }.grid[revealIndex].isRevealed)
        assertEquals(TurnStage.OPENING_REVEAL, afterReveal.stage)
        assertEquals(1, afterReveal.players.single { it.isBot }.grid.count { it.isRevealed })
    }

    private fun gridOf(vararg values: Int, revealed: Boolean = false): List<Card> {
        return values.map { Card(value = it, isRevealed = revealed) }
    }

    private fun playableNewGame(random: Random): GameState {
        val state = SkyoGame.newGame(humanPlayerName = "You", botCount = 1, random = random)
        val players = state.players.map { player ->
            if (player.isBot) {
                player
            } else {
                player.copy(
                    grid = player.grid.mapIndexed { index, card ->
                        if (index < 2) card.copy(isRevealed = true) else card
                    },
                )
            }
        }

        return state.copy(
            players = players,
            currentPlayerIndex = 0,
            stage = TurnStage.DRAW_OR_TAKE,
            openingRevealCount = 2,
            openingContenderIds = emptySet(),
        )
    }
}
