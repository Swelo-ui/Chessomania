package com.chessomania.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.chessomania.app.R
import com.chessomania.app.chess.*

class PuzzleFragment : Fragment() {

    private lateinit var boardView: ChessBoardView
    private lateinit var messageText: TextView
    private lateinit var solvedText: TextView
    private lateinit var failedText: TextView

    private val game = ChessGame()
    private val handler = Handler(Looper.getMainLooper())
    private val puzzles = PuzzleDatabase.puzzles.shuffled()
    private var puzzleIndex = 0
    private var solutionStep = 0
    private var solvedCount = 0
    private var failedCount = 0
    private var isFlipped = false
    private var awaitingOpponent = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_puzzle, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        boardView = view.findViewById(R.id.puzzle_board)
        messageText = view.findViewById(R.id.puz_message)
        solvedText = view.findViewById(R.id.puz_solved)
        failedText = view.findViewById(R.id.puz_failed)

        boardView.game = game
        boardView.onMoveSelected = { from, to -> checkPuzzleMove(from, to) }

        view.findViewById<Button>(R.id.btn_next_puzzle).setOnClickListener { nextPuzzle() }
        view.findViewById<Button>(R.id.btn_puz_hint).setOnClickListener { showHint() }
        view.findViewById<Button>(R.id.btn_puz_flip).setOnClickListener {
            isFlipped = !isFlipped
            boardView.isFlipped = isFlipped
            boardView.invalidate()
        }
        view.findViewById<Button>(R.id.btn_puz_next).setOnClickListener { nextPuzzle() }

        loadPuzzle(0)
    }

    private fun loadPuzzle(index: Int) {
        val puzzle = puzzles[index % puzzles.size]
        solutionStep = 0
        awaitingOpponent = false
        boardView.isInteractive = true
        boardView.highlightedPos = null
        boardView.highlightedToPos = null

        PuzzleDatabase.loadPuzzleFen(game, puzzle.fen)
        boardView.game = game
        boardView.setLastMove(null, null)
        boardView.clearSelection()
        boardView.isFlipped = puzzle.playerColor == Color.BLACK
        isFlipped = boardView.isFlipped

        val side = if (puzzle.playerColor == Color.WHITE) "White" else "Black"
        messageText.text = "Find the best move for $side! (${puzzle.theme})"
        messageText.setBackgroundResource(R.drawable.card_bg)
        messageText.setTextColor(0xFFd9d4cd.toInt())
        boardView.invalidate()
    }

    private fun checkPuzzleMove(from: Pos, to: Pos) {
        if (awaitingOpponent) return
        val puzzle = puzzles[puzzleIndex % puzzles.size]
        if (solutionStep >= puzzle.solutionMoves.size) return

        val expectedUci = puzzle.solutionMoves[solutionStep]
        val legal = game.getLegalMoves(from)
        val move = legal.find { it.to == to } ?: run {
            boardView.clearSelection(); return
        }

        val cols = "abcdefgh"
        val rows = "87654321"
        val actualUci = "${cols[from.col]}${rows[from.row]}${cols[to.col]}${rows[to.row]}"

        if (actualUci == expectedUci || (move.promotion != null && expectedUci.startsWith(actualUci))) {
            val isCapture = move.capturedPiece != null || move.isEnPassant
            com.chessomania.app.SettingsManager.playSound(requireContext(), if (isCapture) "Capture" else "Move")

            game.applyMove(move)
            boardView.setLastMove(from, to)
            boardView.clearSelection()
            boardView.invalidate()
            solutionStep++

            if (solutionStep >= puzzle.solutionMoves.size) {
                // Puzzle solved!
                solvedCount++
                solvedText.text = "$solvedCount Solved"
                messageText.text = "✓ Excellent! Puzzle solved!"
                messageText.setTextColor(0xFF34d399.toInt())
                boardView.isInteractive = false
                com.chessomania.app.SettingsManager.playSound(requireContext(), "GenericNotify")
                handler.postDelayed({ nextPuzzle() }, 2000)
            } else {
                // Play opponent move
                awaitingOpponent = true
                boardView.isInteractive = false
                handler.postDelayed({
                    if (!isAdded) return@postDelayed
                    val oppUci = puzzle.solutionMoves[solutionStep]
                    val oppMove = PuzzleDatabase.uciToMove(oppUci, game)
                    if (oppMove != null) {
                        val isOppCapture = oppMove.capturedPiece != null || oppMove.isEnPassant
                        com.chessomania.app.SettingsManager.playSound(requireContext(), if (isOppCapture) "Capture" else "Move")

                        game.applyMove(oppMove)
                        boardView.setLastMove(oppMove.from, oppMove.to)
                        boardView.invalidate()
                        solutionStep++
                    }
                    awaitingOpponent = false
                    boardView.isInteractive = true
                    messageText.text = "Good! Keep going..."
                    messageText.setTextColor(0xFFb58863.toInt())
                }, 800)
            }
        } else {
            // Wrong move
            failedCount++
            failedText.text = "$failedCount Failed"
            messageText.text = "✗ Wrong move! Try again."
            messageText.setTextColor(0xFFf87171.toInt())
            com.chessomania.app.SettingsManager.playSound(requireContext(), "GenericNotify")
            boardView.clearSelection()
        }
    }

    private fun showHint() {
        val puzzle = puzzles[puzzleIndex % puzzles.size]
        if (solutionStep >= puzzle.solutionMoves.size) return
        val uci = puzzle.solutionMoves[solutionStep]
        if (uci.length < 4) return
        val cols = "abcdefgh"
        val fromCol = cols.indexOf(uci[0])
        val fromRow = 8 - uci[1].digitToInt()
        val toCol = cols.indexOf(uci[2])
        val toRow = 8 - uci[3].digitToInt()
        if (fromCol >= 0 && toCol >= 0) {
            boardView.highlightedPos = Pos(fromRow, fromCol)
            boardView.highlightedToPos = Pos(toRow, toCol)
            boardView.invalidate()
            handler.postDelayed({ 
                boardView.highlightedPos = null
                boardView.highlightedToPos = null
                boardView.invalidate() 
            }, 1500)
        }
    }

    private fun nextPuzzle() {
        puzzleIndex++
        loadPuzzle(puzzleIndex)
    }

    override fun onResume() {
        super.onResume()
        boardView.refreshTheme()
    }
}
