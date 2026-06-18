package com.chessomania.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.chessomania.app.R
import com.chessomania.app.chess.*

class PuzzleFragment : Fragment() {

    private lateinit var boardView: ChessBoardView
    private lateinit var messageText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var solvedText: TextView
    private lateinit var failedText: TextView

    private val game = ChessGame()
    private val handler = Handler(Looper.getMainLooper())
    private val allPuzzles = PuzzleDatabase.puzzles
    private var currentThemeName = "All Themes"
    private var currentLoadedPuzzle: Puzzle? = null
    private var solutionStep = 0
    private var solvedCount = 0
    private var failedCount = 0
    private var isFlipped = false
    private var awaitingOpponent = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_puzzle, container, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // BGM is managed contextually by MainActivity tab switching
        boardView = view.findViewById(R.id.puzzle_board)
        messageText = view.findViewById(R.id.puz_message)
        subtitleText = view.findViewById(R.id.puz_subtitle)
        solvedText = view.findViewById(R.id.puz_solved)
        failedText = view.findViewById(R.id.puz_failed)

        boardView.game = game
        boardView.onMoveSelected = { from, to -> checkPuzzleMove(from, to) }

        view.findViewById<Button>(R.id.btn_puz_hint).setOnClickListener { showHint() }
        view.findViewById<Button>(R.id.btn_puz_flip).setOnClickListener {
            isFlipped = !isFlipped
            boardView.isFlipped = isFlipped
            boardView.invalidate()
        }
        view.findViewById<Button>(R.id.btn_puz_next).setOnClickListener { nextPuzzle() }

        // Initialize Puzzle Theme Spinner
        val context = requireContext()
        val themes = listOf("All Themes") + allPuzzles.map { it.theme }.distinct().sorted()
        val themeSpinner = view.findViewById<Spinner>(R.id.spinner_puzzle_theme)
        
        val themeIcons = mapOf(
            "All Themes" to R.drawable.ic_theme_all,
            "Fork" to R.drawable.ic_theme_fork,
            "Pin" to R.drawable.ic_theme_pin,
            "Back Rank" to R.drawable.ic_theme_backrank,
            "Skewer" to R.drawable.ic_theme_skewer,
            "Sacrifice" to R.drawable.ic_theme_sacrifice,
            "Discovered Attack" to R.drawable.ic_theme_discovered,
            "Mate in 1" to R.drawable.ic_theme_mate,
            "Double Check" to R.drawable.ic_theme_doublecheck,
            "Zwischenzug" to R.drawable.ic_theme_zwischenzug,
            "Promotion" to R.drawable.ic_theme_promotion
        )

        val themeAdapter = PuzzleThemeAdapter(context, themes, themeIcons)
        themeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        themeSpinner.adapter = themeAdapter

        themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = themes[position]
                loadCurrentPuzzleForSelectedTheme(selected)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun getThemeLevel(theme: String): Int {
        val prefs = requireContext().getSharedPreferences("chessomania_puz_progress", android.content.Context.MODE_PRIVATE)
        return prefs.getInt("level_$theme", 0) // Default to 0 (Level 1)
    }

    private fun saveThemeLevel(theme: String, level: Int) {
        val prefs = requireContext().getSharedPreferences("chessomania_puz_progress", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("level_$theme", level).apply()
    }

    private fun loadCurrentPuzzleForSelectedTheme(selectedTheme: String) {
        currentThemeName = selectedTheme
        solutionStep = 0
        awaitingOpponent = false
        boardView.isInteractive = true
        boardView.highlightedPos = null
        boardView.highlightedToPos = null

        val rawPuzzle = getPuzzleForTheme(selectedTheme)
        if (rawPuzzle == null) {
            messageText.text = "All puzzles in this theme solved! Next set loaded."
            if (selectedTheme != "All Themes") {
                saveThemeLevel(selectedTheme, 0)
            } else {
                val themesList = allPuzzles.map { it.theme }.distinct()
                themesList.forEach { saveThemeLevel(it, 0) }
            }
            loadCurrentPuzzleForSelectedTheme(selectedTheme)
            return
        }

        val isProcedural = rawPuzzle.id == -1
        var puzzle = rawPuzzle
        if (!isProcedural && Math.random() < 0.5) {
            val mirroredFen = PuzzleDatabase.mirrorFen(rawPuzzle.fen)
            val mirroredMoves = rawPuzzle.solutionMoves.map { PuzzleDatabase.mirrorMove(it) }
            puzzle = Puzzle(
                rawPuzzle.id,
                mirroredFen,
                mirroredMoves,
                rawPuzzle.theme,
                rawPuzzle.description,
                rawPuzzle.playerColor
            )
        }
        currentLoadedPuzzle = puzzle

        PuzzleDatabase.loadPuzzleFen(game, puzzle.fen)
        boardView.game = game
        boardView.setLastMove(null, null)
        boardView.clearSelection()
        boardView.isFlipped = puzzle.playerColor == Color.BLACK
        isFlipped = boardView.isFlipped

        val side = if (puzzle.playerColor == Color.WHITE) "White" else "Black"
        val level = getThemeLevel(puzzle.theme) + 1
        val cleanDesc = puzzle.description.replace(Regex("^Level\\s+\\d+:\\s*", RegexOption.IGNORE_CASE), "")

        messageText.text = "Find the best move for $side! (${puzzle.theme})"
        subtitleText.text = "Lvl $level: $cleanDesc"
        messageText.setBackgroundResource(R.drawable.card_bg)
        messageText.setTextColor(0xFFd9d4cd.toInt())
        boardView.invalidate()
    }

    private fun getPuzzleForTheme(theme: String): Puzzle? {
        if (theme == "All Themes") {
            val themesList = allPuzzles.map { it.theme }.distinct()
            val unsolvedThemes = themesList.filter { getThemeLevel(it) < 150 }
            val chosenTheme = if (unsolvedThemes.isNotEmpty()) {
                unsolvedThemes.random()
            } else {
                themesList.random()
            }
            val level = getThemeLevel(chosenTheme)
            val themePuzzles = allPuzzles.filter { it.theme == chosenTheme }.sortedBy { it.id }
            return if (level >= themePuzzles.size) {
                PuzzleDatabase.generateProceduralPuzzle(chosenTheme, level + 1)
            } else if (themePuzzles.isNotEmpty()) {
                themePuzzles[level]
            } else {
                null
            }
        } else {
            val level = getThemeLevel(theme)
            val themePuzzles = allPuzzles.filter { it.theme == theme }.sortedBy { it.id }
            if (level >= themePuzzles.size) {
                return PuzzleDatabase.generateProceduralPuzzle(theme, level + 1)
            }
            if (themePuzzles.isEmpty()) return null
            return themePuzzles[level]
        }
    }

    private fun checkPuzzleMove(from: Pos, to: Pos) {
        if (awaitingOpponent) return
        val puzzle = currentLoadedPuzzle ?: return
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
            if (isCapture) {
                com.chessomania.app.SettingsManager.performHapticFeedback(requireContext(), com.chessomania.app.SettingsManager.HapticType.CAPTURE)
            } else {
                com.chessomania.app.SettingsManager.performHapticFeedback(requireContext(), com.chessomania.app.SettingsManager.HapticType.MOVE)
            }

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
                com.chessomania.app.SettingsManager.performHapticFeedback(requireContext(), com.chessomania.app.SettingsManager.HapticType.CHECKMATE)
                
                // Solve progress!
                val solvedTheme = puzzle.theme
                val curLevel = getThemeLevel(solvedTheme)
                saveThemeLevel(solvedTheme, curLevel + 1)

                handler.postDelayed({ 
                    loadCurrentPuzzleForSelectedTheme(currentThemeName) 
                }, 2000)
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
                        if (isOppCapture) {
                            com.chessomania.app.SettingsManager.performHapticFeedback(requireContext(), com.chessomania.app.SettingsManager.HapticType.CAPTURE)
                        } else {
                            com.chessomania.app.SettingsManager.performHapticFeedback(requireContext(), com.chessomania.app.SettingsManager.HapticType.MOVE)
                        }

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
            com.chessomania.app.SettingsManager.performHapticFeedback(requireContext(), com.chessomania.app.SettingsManager.HapticType.ERROR)
            boardView.clearSelection()
        }
    }

    private fun showHint() {
        val puzzle = currentLoadedPuzzle ?: return
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
        if (currentThemeName == "All Themes") {
            loadCurrentPuzzleForSelectedTheme("All Themes")
        } else {
            val curLevel = getThemeLevel(currentThemeName)
            saveThemeLevel(currentThemeName, curLevel + 1)
            loadCurrentPuzzleForSelectedTheme(currentThemeName)
        }
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

    private class PuzzleThemeAdapter(
        context: android.content.Context,
        private val items: List<String>,
        private val icons: Map<String, Int>
    ) : ArrayAdapter<String>(context, R.layout.spinner_item, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as TextView
            setupView(view, position)
            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getDropDownView(position, convertView, parent) as TextView
            setupView(view, position)
            return view
        }

        private fun setupView(textView: TextView, position: Int) {
            val theme = items[position]
            val iconRes = icons[theme] ?: 0
            if (iconRes != 0) {
                val drawable = ContextCompat.getDrawable(context, iconRes)
                if (drawable != null) {
                    val size = dpToPx(18)
                    drawable.setBounds(0, 0, size, size)
                    textView.setCompoundDrawables(drawable, null, null, null)
                    textView.compoundDrawablePadding = dpToPx(8)
                } else {
                    textView.setCompoundDrawables(null, null, null, null)
                }
            } else {
                textView.setCompoundDrawables(null, null, null, null)
            }
            textView.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            textView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }

        private fun dpToPx(dp: Int): Int {
            val scale = context.resources.displayMetrics.density
            return (dp * scale + 0.5f).toInt()
        }
    }
}
