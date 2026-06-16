package com.chessomania.app.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.chessomania.app.R
import com.chessomania.app.chess.ChessGame
import com.chessomania.app.chess.Color
import com.chessomania.app.chess.Pos

class CoordinatesFragment : Fragment() {

    private lateinit var boardView: ChessBoardView
    private lateinit var targetText: TextView
    private lateinit var messageText: TextView
    private lateinit var correctText: TextView
    private lateinit var wrongText: TextView
    private lateinit var streakText: TextView
    private lateinit var startButton: Button

    private val game = ChessGame()
    private val files = "abcdefgh"
    private val ranks = "12345678"
    private var targetCoord = ""
    private var correctCount = 0
    private var wrongCount = 0
    private var streak = 0
    private var isRunning = false
    private var isFlipped = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_coordinates, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        boardView = view.findViewById(R.id.coord_board)
        targetText = view.findViewById(R.id.coord_target)
        messageText = view.findViewById(R.id.coord_message)
        correctText = view.findViewById(R.id.score_correct)
        wrongText = view.findViewById(R.id.score_wrong)
        streakText = view.findViewById(R.id.score_streak)
        startButton = view.findViewById(R.id.btn_coord_start)

        // Setup empty board (no game pieces, just coordinates)
        boardView.game = game
        boardView.isInteractive = false

        // Custom touch for coordinates
        boardView.setOnTouchListener { _, event ->
            if (isRunning && event.action == android.view.MotionEvent.ACTION_UP) {
                val sq = boardView.width.toFloat() / 8f
                val col = (event.x / sq).toInt().coerceIn(0, 7)
                val row = (event.y / sq).toInt().coerceIn(0, 7)
                val logicalRow = if (isFlipped) 7 - row else row
                val logicalCol = if (isFlipped) 7 - col else col
                checkCoordinate(logicalRow, logicalCol)
            }
            true
        }

        startButton.setOnClickListener { startTraining() }

        view.findViewById<Button>(R.id.btn_coord_flip).setOnClickListener {
            isFlipped = !isFlipped
            boardView.isFlipped = isFlipped
            boardView.invalidate()
        }

        newTarget()
    }

    private fun startTraining() {
        correctCount = 0; wrongCount = 0; streak = 0
        correctText.text = "0"; wrongText.text = "0"; streakText.text = "0"
        isRunning = true
        startButton.text = "Training..."
        startButton.isEnabled = false
        newTarget()

        // Auto-stop after 60 seconds
        object : CountDownTimer(60000, 1000) {
            override fun onTick(ms: Long) {
                if (!isAdded) cancel()
                startButton.text = "Running: ${ms/1000}s"
            }
            override fun onFinish() {
                if (!isAdded) return
                isRunning = false
                startButton.text = "▶ Start Training"
                startButton.isEnabled = true
                messageText.text = "Done! Correct: $correctCount | Wrong: $wrongCount | Best Streak: $streak"
                messageText.setTextColor(0xFFb58863.toInt())
                targetText.text = "—"
            }
        }.start()
    }

    private fun newTarget() {
        val file = files.random()
        val rank = ranks.random()
        targetCoord = "$file$rank"
        targetText.text = targetCoord.uppercase()
    }

    private fun checkCoordinate(row: Int, col: Int) {
        val fileChar = files[col]
        val rankChar = ranks[7 - row]
        val tapped = "$fileChar$rankChar"

        // Flash the tapped square
        val tappedPos = Pos(row, col)
        boardView.highlightedPos = tappedPos
        boardView.invalidate()

        if (tapped == targetCoord) {
            correctCount++
            streak++
            correctText.text = "$correctCount"
            streakText.text = "$streak"
            messageText.text = "✓ Correct!"
            messageText.setTextColor(0xFF34d399.toInt())
            com.chessomania.app.SettingsManager.playSound(requireContext(), "Move")
        } else {
            wrongCount++
            streak = 0
            wrongText.text = "$wrongCount"
            streakText.text = "0"
            messageText.text = "✗ Wrong! That was $tapped"
            messageText.setTextColor(0xFFf87171.toInt())
            com.chessomania.app.SettingsManager.playSound(requireContext(), "GenericNotify")
        }

        boardView.postDelayed({
            if (isAdded) {
                boardView.highlightedPos = null
                boardView.invalidate()
                if (isRunning) newTarget()
            }
        }, 400)
    }

    override fun onResume() {
        super.onResume()
        boardView.refreshTheme()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            boardView.refreshTheme()
        }
    }
}
