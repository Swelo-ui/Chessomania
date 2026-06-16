package com.chessomania.app.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chessomania.app.R
import com.chessomania.app.chess.*
import com.chessomania.app.net.*
import com.chessomania.app.SettingsManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

class PlayFragment : Fragment() {

    private lateinit var boardView: ChessBoardView
    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var engineProgress: ProgressBar
    private lateinit var engineText: TextView
    private lateinit var nameBlack: TextView
    private lateinit var nameWhite: TextView
    private lateinit var capturesBlack: TextView
    private lateinit var capturesWhite: TextView
    private lateinit var playerBlackBox: View
    private lateinit var playerWhiteBox: View
    private lateinit var moveHistoryRecycler: RecyclerView
    private lateinit var modeGroup: RadioGroup
    private lateinit var modeAI: RadioButton
    private lateinit var modeHuman: RadioButton
    private lateinit var modeFriend: RadioButton
    private lateinit var diffContainer: LinearLayout
    private lateinit var diffLabel: TextView

    // Multiplayer components
    private lateinit var onlinePanel: LinearLayout
    private lateinit var layoutLoggedOut: LinearLayout
    private lateinit var layoutLoggedIn: LinearLayout
    private lateinit var editUsername: EditText
    private lateinit var editPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var textLoggedInAs: TextView
    private lateinit var btnFriendsList: Button
    private lateinit var btnLogout: Button

    private val game = ChessGame()
    private val ai = ChessAI()
    private val handler = Handler(Looper.getMainLooper())
    private var aiDepth = 3
    private var isAIMode = true
    private var isFriendMode = false
    private var isFlipped = false
    private var isAIThinking = false

    private var activeGameId: String? = null
    private var myColor: Color? = null
    private var myUsername: String? = null
    private var opponentUsername: String? = null
    private val incomingChallenges = mutableListOf<SseEvent>()
    private var sseCollectionJob: kotlinx.coroutines.Job? = null

    private val moveAdapter = MoveHistoryAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_play, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        boardView = view.findViewById(R.id.chess_board)
        statusText = view.findViewById(R.id.status_text)
        statusDot = view.findViewById(R.id.status_dot)
        engineProgress = view.findViewById(R.id.engine_progress)
        engineText = view.findViewById(R.id.engine_text)
        nameBlack = view.findViewById(R.id.name_black)
        nameWhite = view.findViewById(R.id.name_white)
        capturesBlack = view.findViewById(R.id.captures_black)
        capturesWhite = view.findViewById(R.id.captures_white)
        playerBlackBox = view.findViewById(R.id.player_black_box)
        playerWhiteBox = view.findViewById(R.id.player_white_box)
        moveHistoryRecycler = view.findViewById(R.id.move_history)
        modeGroup = view.findViewById(R.id.mode_group)
        modeAI = view.findViewById(R.id.mode_ai)
        modeHuman = view.findViewById(R.id.mode_human)
        modeFriend = view.findViewById(R.id.mode_friend)
        diffContainer = view.findViewById(R.id.diff_container)
        diffLabel = view.findViewById(R.id.diff_label)

        // Multiplayer UI bindings
        onlinePanel = view.findViewById(R.id.online_panel)
        layoutLoggedOut = view.findViewById(R.id.layout_logged_out)
        layoutLoggedIn = view.findViewById(R.id.layout_logged_in)
        editUsername = view.findViewById(R.id.edit_username)
        editPassword = view.findViewById(R.id.edit_password)
        btnLogin = view.findViewById(R.id.btn_login)
        btnRegister = view.findViewById(R.id.btn_register)
        textLoggedInAs = view.findViewById(R.id.text_logged_in_as)
        btnFriendsList = view.findViewById(R.id.btn_friends_list)
        btnLogout = view.findViewById(R.id.btn_logout)
        val editServerUrl = view.findViewById<EditText>(R.id.edit_server_url)
        val btnSaveServer = view.findViewById<Button>(R.id.btn_save_server)
        
        // Display and manage server info
        editServerUrl.setText(SettingsManager.getServerUrl(requireContext()))
        btnSaveServer.setOnClickListener {
            val url = editServerUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                SettingsManager.setServerUrl(requireContext(), url)
                Toast.makeText(context, "Server IP updated", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup RecyclerView
        moveHistoryRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        moveHistoryRecycler.adapter = moveAdapter

        // Setup board
        boardView.game = game
        boardView.onMoveSelected = { from, to -> handleMove(from, to, null) }
        boardView.onPromotionNeeded = { from, to -> showPromotionDialog(from, to) }

        // Mode buttons
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            isAIMode = checkedId == R.id.mode_ai
            isFriendMode = checkedId == R.id.mode_friend
            
            diffContainer.visibility = if (isAIMode) View.VISIBLE else View.GONE
            diffLabel.visibility = if (isAIMode) View.VISIBLE else View.GONE
            onlinePanel.visibility = if (isFriendMode) View.VISIBLE else View.GONE
            
            if (isFriendMode) {
                updateLoginState()
            } else {
                // If switching away from Friend mode, stop SSE service
                val intent = Intent(requireContext(), SSEService::class.java).apply {
                    action = SSEService.ACTION_DISCONNECT
                }
                requireContext().startService(intent)
                sseCollectionJob?.cancel()
                sseCollectionJob = null
                
                activeGameId = null
                myColor = null
                myUsername = null
                opponentUsername = null
                
                nameBlack.text = if (isAIMode) "ChessOmania Engine" else "Player 2 (Black)"
                nameWhite.text = "You (White)"
                newGame()
            }
        }

        // Multiplayer Authentication Listeners
        btnLogin.setOnClickListener {
            val username = editUsername.text.toString().trim()
            val password = editPassword.text.toString().trim()
            if (username.isNotEmpty() && password.isNotEmpty()) {
                lifecycleScope.launch {
                    val result = NetworkClient.post(requireContext(), "/api/login", mapOf("username" to username, "password" to password))
                    if (result is ApiResult.Success) {
                        val token = result.data["token"] as? String ?: ""
                        val actualUser = result.data["username"] as? String ?: username
                        SecurePrefs.saveSession(requireContext(), token, actualUser)
                        Toast.makeText(context, "Logged in as $actualUser", Toast.LENGTH_SHORT).show()
                        editPassword.text.clear()
                        updateLoginState()
                    } else if (result is ApiResult.Error) {
                        Toast.makeText(context, "Login failed: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(context, "Please enter username and password", Toast.LENGTH_SHORT).show()
            }
        }

        btnRegister.setOnClickListener {
            val username = editUsername.text.toString().trim()
            val password = editPassword.text.toString().trim()
            if (username.isNotEmpty() && password.isNotEmpty()) {
                lifecycleScope.launch {
                    val result = NetworkClient.post(requireContext(), "/api/register", mapOf("username" to username, "password" to password))
                    if (result is ApiResult.Success) {
                        val token = result.data["token"] as? String ?: ""
                        val actualUser = result.data["username"] as? String ?: username
                        SecurePrefs.saveSession(requireContext(), token, actualUser)
                        Toast.makeText(context, "Registered successfully as $actualUser", Toast.LENGTH_SHORT).show()
                        editPassword.text.clear()
                        updateLoginState()
                    } else if (result is ApiResult.Error) {
                        Toast.makeText(context, "Registration failed: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(context, "Please enter username and password", Toast.LENGTH_SHORT).show()
            }
        }

        btnLogout.setOnClickListener {
            lifecycleScope.launch {
                NetworkClient.post(requireContext(), "/api/logout", emptyMap())
                SecurePrefs.clearSession(requireContext())
                updateLoginState()
            }
        }

        btnFriendsList.setOnClickListener {
            val sheet = FriendsBottomSheet()
            sheet.show(childFragmentManager, "FriendsBottomSheet")
        }

        // Difficulty buttons
        setupDiffButtons(view)

        // Action buttons
        view.findViewById<Button>(R.id.btn_flip).setOnClickListener { flipBoard() }
        view.findViewById<Button>(R.id.btn_undo).setOnClickListener { undoMove() }
        view.findViewById<Button>(R.id.btn_resign).setOnClickListener { resign() }
        view.findViewById<Button>(R.id.btn_new_game).setOnClickListener { newGame() }
        
        view.findViewById<View>(R.id.btn_copy_history).setOnClickListener {
            copyMoveHistory()
        }

        renderBoard()
        updateUI()
    }

    private fun setupDiffButtons(view: View) {
        val diffEasy = view.findViewById<TextView>(R.id.diff_easy)
        val diffMid = view.findViewById<TextView>(R.id.diff_mid)
        val diffHard = view.findViewById<TextView>(R.id.diff_hard)
        val diffMax = view.findViewById<TextView>(R.id.diff_max)
        val allBtns = listOf(diffEasy, diffMid, diffHard, diffMax)
        val depths = listOf(2, 3, 4, 5)

        fun select(idx: Int) {
            aiDepth = depths[idx]
            allBtns.forEachIndexed { i, btn ->
                btn.background = if (i == idx)
                    ContextCompat.getDrawable(requireContext(), R.drawable.diff_btn_selected)
                else
                    ContextCompat.getDrawable(requireContext(), R.drawable.diff_btn_normal)
                btn.setTextColor(if (i == idx) 0xFFFFFFFF.toInt() else 0xFFd9d4cd.toInt())
            }
        }

        diffEasy.setOnClickListener { select(0) }
        diffMid.setOnClickListener { select(1) }
        diffHard.setOnClickListener { select(2) }
        diffMax.setOnClickListener { select(3) }
        select(0)
    }

    private fun handleMove(from: Pos, to: Pos, promotion: PieceType?) {
        if (isAIThinking) return
        if (game.gameStatus == ChessGame.GameStatus.CHECKMATE ||
            game.gameStatus == ChessGame.GameStatus.STALEMATE ||
            game.gameStatus == ChessGame.GameStatus.DRAW) return

        if (isFriendMode) {
            // Block local move if it's not our turn in online match
            if (myColor != game.currentTurn) {
                Toast.makeText(context, "It is not your turn", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val legal = game.getLegalMoves(from)
        val move = legal.find { it.to == to && (promotion == null || it.promotion == promotion) }
            ?: legal.find { it.to == to && promotion == null }
            ?: return

        game.applyMove(move)
        boardView.setLastMove(from, to)
        boardView.clearSelection()
        renderBoard()
        updateUI()

        val status = game.gameStatus
        if (status == ChessGame.GameStatus.CHECKMATE || status == ChessGame.GameStatus.STALEMATE
            || status == ChessGame.GameStatus.DRAW) {
            SettingsManager.playSound(requireContext(), "GenericNotify")
            showGameOverDialog(status)
            if (isFriendMode) {
                sendMoveToServer(from, to, promotion)
            }
            return
        } else {
            val isCapture = move.capturedPiece != null || move.isEnPassant
            val soundName = if (isCapture) "Capture" else "Move"
            SettingsManager.playSound(requireContext(), soundName)
            if (status == ChessGame.GameStatus.CHECK) {
                SettingsManager.playSound(requireContext(), "GenericNotify")
            }
        }

        if (isFriendMode) {
            sendMoveToServer(from, to, promotion)
        }

        // AI move
        if (isAIMode && game.currentTurn == Color.BLACK) {
            triggerAI()
        }
    }

    private fun sendMoveToServer(from: Pos, to: Pos, promotion: PieceType?) {
        val gId = activeGameId ?: return
        val fromStr = "${(from.col + 'a'.code).toChar()}${8 - from.row}"
        val toStr = "${(to.col + 'a'.code).toChar()}${8 - to.row}"
        val promoStr = when (promotion) {
            PieceType.QUEEN -> "q"
            PieceType.ROOK -> "r"
            PieceType.BISHOP -> "b"
            PieceType.KNIGHT -> "n"
            else -> null
        }
        
        lifecycleScope.launch {
            val result = NetworkClient.post(
                requireContext(),
                "/api/game/move",
                mapOf(
                    "gameId" to gId,
                    "from" to fromStr,
                    "to" to toStr,
                    "promotion" to promoStr
                )
            )
            if (result is ApiResult.Error) {
                Toast.makeText(context, "Move sync failed: ${result.message}", Toast.LENGTH_SHORT).show()
                // Revert move locally
                game.undoLastMove()
                boardView.setLastMove(
                    game.moveHistory.lastOrNull()?.from,
                    game.moveHistory.lastOrNull()?.to,
                    false
                )
                boardView.clearSelection()
                renderBoard()
                updateUI()
            }
        }
    }

    private fun triggerAI() {
        val moveCountBefore = game.moveHistory.size
        isAIThinking = true
        boardView.isInteractive = false
        engineProgress.visibility = View.VISIBLE
        engineText.visibility = View.VISIBLE

        val gameCopy = game.clone()
        thread {
            val startTime = System.currentTimeMillis()
            val bestMove = ai.getBestMove(gameCopy, aiDepth)
            
            // Add a delay to let the player's move animation finish and feel natural
            val elapsed = System.currentTimeMillis() - startTime
            val delay = 350L - elapsed
            if (delay > 0) {
                try {
                    Thread.sleep(delay)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            
            handler.post {
                engineProgress.visibility = View.GONE
                engineText.visibility = View.GONE
                isAIThinking = false
                boardView.isInteractive = true
                if (bestMove != null && isAdded && game.moveHistory.size == moveCountBefore) {
                    game.applyMove(bestMove)
                    boardView.setLastMove(bestMove.from, bestMove.to)
                    boardView.clearSelection()
                    renderBoard()
                    updateUI()
                    val st = game.gameStatus
                    if (st == ChessGame.GameStatus.CHECKMATE || st == ChessGame.GameStatus.STALEMATE
                        || st == ChessGame.GameStatus.DRAW) {
                        SettingsManager.playSound(requireContext(), "GenericNotify")
                        showGameOverDialog(st)
                    } else {
                        val isCapture = bestMove.capturedPiece != null || bestMove.isEnPassant
                        val soundName = if (isCapture) "Capture" else "Move"
                        SettingsManager.playSound(requireContext(), soundName)
                        if (st == ChessGame.GameStatus.CHECK) {
                            SettingsManager.playSound(requireContext(), "GenericNotify")
                        }
                    }
                }
            }
        }
    }

    private fun showPromotionDialog(from: Pos, to: Pos) {
        val items = arrayOf("♛ Queen", "♜ Rook", "♝ Bishop", "♞ Knight")
        val types = arrayOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
        AlertDialog.Builder(requireContext())
            .setTitle("Choose Promotion")
            .setItems(items) { _, which -> handleMove(from, to, types[which]) }
            .setCancelable(false)
            .show()
    }

    private fun showGameOverDialog(status: ChessGame.GameStatus) {
        if (!isAdded) return
        val title = when(status) {
            ChessGame.GameStatus.CHECKMATE -> "Checkmate!"
            ChessGame.GameStatus.STALEMATE -> "Stalemate!"
            ChessGame.GameStatus.DRAW -> "Draw!"
            else -> "Game Over"
        }
        val msg = when(status) {
            ChessGame.GameStatus.CHECKMATE -> if (game.currentTurn == Color.WHITE) "⚫ Black wins!" else "⚪ White wins!"
            ChessGame.GameStatus.STALEMATE -> "No legal moves available."
            ChessGame.GameStatus.DRAW -> "50-move rule reached."
            else -> ""
        }
        AlertDialog.Builder(requireContext())
            .setTitle("♟ $title")
            .setMessage(msg)
            .setPositiveButton("Play Again") { _, _ -> newGame() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun flipBoard() {
        isFlipped = !isFlipped
        boardView.isFlipped = isFlipped
        boardView.invalidate()
    }

    private fun undoMove() {
        if (isFriendMode) {
            Toast.makeText(context, "Cannot undo moves in online matches", Toast.LENGTH_SHORT).show()
            return
        }
        if (isAIThinking) return
        game.undoLastMove()
        if (isAIMode && game.moveHistory.isNotEmpty()) game.undoLastMove()
        boardView.setLastMove(
            game.moveHistory.lastOrNull()?.from,
            game.moveHistory.lastOrNull()?.to,
            false
        )
        boardView.clearSelection()
        renderBoard()
        updateUI()
    }

    private fun resign() {
        AlertDialog.Builder(requireContext())
            .setTitle("Resign?")
            .setMessage("Are you sure you want to resign?")
            .setPositiveButton("Yes") { _, _ ->
                if (isFriendMode) {
                    val gId = activeGameId
                    if (gId != null) {
                        lifecycleScope.launch {
                            NetworkClient.post(requireContext(), "/api/game/resign", mapOf("gameId" to gId))
                        }
                    }
                } else {
                    showGameOverDialog(ChessGame.GameStatus.CHECKMATE)
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun newGame() {
        if (isFriendMode) {
            Toast.makeText(context, "Challenge a friend to start a new match", Toast.LENGTH_SHORT).show()
            return
        }
        isAIThinking = false
        boardView.isInteractive = true
        engineProgress.visibility = View.GONE
        engineText.visibility = View.GONE
        game.setupInitialPosition()
        boardView.setLastMove(null, null)
        boardView.clearSelection()
        renderBoard()
        updateUI()
    }

    private fun copyMoveHistory() {
        if (game.sanHistory.isEmpty()) {
            Toast.makeText(context, "No moves to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val historyStr = buildString {
            for (i in game.sanHistory.indices step 2) {
                val moveNum = (i / 2) + 1
                val whiteMove = game.sanHistory[i]
                val blackMove = if (i + 1 < game.sanHistory.size) game.sanHistory[i + 1] else ""
                append("$moveNum. $whiteMove")
                if (blackMove.isNotEmpty()) {
                    append(" $blackMove")
                }
                append("\n")
            }
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Move History", historyStr.trim())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Move history copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun renderBoard() {
        boardView.game = game
        boardView.invalidate()
    }

    private fun updateUI() {
        val turn = game.currentTurn
        val status = game.gameStatus

        if (isFriendMode) {
            // Online Turn/Status formatting
            if (activeGameId != null) {
                statusText.text = when(status) {
                    ChessGame.GameStatus.CHECK -> if (turn == myColor) "You are in Check!" else "Opponent is in Check!"
                    ChessGame.GameStatus.CHECKMATE -> "Checkmate!"
                    ChessGame.GameStatus.STALEMATE -> "Stalemate!"
                    ChessGame.GameStatus.DRAW -> "Draw!"
                    else -> if (turn == myColor) "Your turn" else "Waiting for opponent (${opponentUsername ?: "Opponent"})"
                }
                
                // Flip names based on colors played
                if (myColor == Color.WHITE) {
                    nameWhite.text = "You ($myUsername)"
                    nameBlack.text = opponentUsername ?: "Opponent"
                } else {
                    nameWhite.text = opponentUsername ?: "Opponent"
                    nameBlack.text = "You ($myUsername)"
                }
            } else {
                statusText.text = "Waiting for challenge..."
                nameWhite.text = "You"
                nameBlack.text = "Opponent"
            }
            
            // Set interactiveness
            boardView.isInteractive = activeGameId != null && turn == myColor
        } else {
            // Offline local/AI turn status formatting
            statusText.text = when(status) {
                ChessGame.GameStatus.CHECK -> if (turn == Color.WHITE) "White is in Check!" else "Black is in Check!"
                ChessGame.GameStatus.CHECKMATE -> "Checkmate!"
                ChessGame.GameStatus.STALEMATE -> "Stalemate!"
                ChessGame.GameStatus.DRAW -> "Draw!"
                else -> if (turn == Color.WHITE) "White to move" else "Black to move"
            }
            nameWhite.text = "You (White)"
            nameBlack.text = if (isAIMode) "ChessOmania Engine" else "Player 2 (Black)"
            boardView.isInteractive = !isAIThinking
        }

        // Dot color
        val dotColor = when(status) {
            ChessGame.GameStatus.CHECK, ChessGame.GameStatus.CHECKMATE -> 0xFFc94f36.toInt()
            ChessGame.GameStatus.STALEMATE, ChessGame.GameStatus.DRAW -> 0xFFb58863.toInt()
            else -> if (isFriendMode && activeGameId == null) 0xFF8a8178.toInt()
                     else if (turn == Color.WHITE) 0xFF629924.toInt() else 0xFF8a8178.toInt()
        }
        statusDot.background.setTint(dotColor)

        // Player boxes highlight
        playerBlackBox.setBackgroundResource(if (turn == Color.BLACK) R.drawable.player_box_active_bg else R.drawable.player_box_bg)
        playerWhiteBox.setBackgroundResource(if (turn == Color.WHITE) R.drawable.player_box_active_bg else R.drawable.player_box_bg)

        // Captures
        capturesBlack.text = game.getCapturedPieces(Color.BLACK)
        capturesWhite.text = game.getCapturedPieces(Color.WHITE)

        // Move history
        moveAdapter.moves = game.sanHistory.toList()
        moveAdapter.notifyDataSetChanged()
        if (game.sanHistory.isNotEmpty()) {
            moveHistoryRecycler.scrollToPosition(game.sanHistory.size - 1)
        }
    }

    override fun onResume() {
        super.onResume()
        boardView.refreshTheme()
        if (isFriendMode) {
            updateLoginState()
        }
    }

    fun getIncomingChallenges(): List<SseEvent> = incomingChallenges
    fun removeChallenge(challengeId: String) {
        incomingChallenges.removeAll { it.challengeId == challengeId }
    }

    private fun updateLoginState() {
        val context = context ?: return
        val loggedIn = SecurePrefs.isLoggedIn(context)
        if (loggedIn) {
            layoutLoggedOut.visibility = View.GONE
            layoutLoggedIn.visibility = View.VISIBLE
            val username = SecurePrefs.getUsername(context) ?: "User"
            textLoggedInAs.text = "Logged in as: $username"
            
            // Connect to real-time events service
            val intent = Intent(context, SSEService::class.java).apply {
                action = SSEService.ACTION_CONNECT
            }
            context.startService(intent)
            
            startEventCollection()
            
            // Auto rejoin stored active game session
            val activeId = SettingsManager.getActiveGameId(context)
            if (activeId != null) {
                rejoinGame(activeId)
            }
        } else {
            layoutLoggedOut.visibility = View.VISIBLE
            layoutLoggedIn.visibility = View.GONE
            
            // Disconnect from events service
            val intent = Intent(context, SSEService::class.java).apply {
                action = SSEService.ACTION_DISCONNECT
            }
            context.startService(intent)
            
            sseCollectionJob?.cancel()
            sseCollectionJob = null
            
            activeGameId = null
            myColor = null
            myUsername = null
            opponentUsername = null
            renderBoard()
            updateUI()
        }
    }

    private fun rejoinGame(gameId: String) {
        val context = context ?: return
        lifecycleScope.launch {
            val result = NetworkClient.get(context, "/api/game/state/$gameId")
            if (result is ApiResult.Success) {
                val data = result.data
                activeGameId = gameId
                val white = data["white"] as? String ?: ""
                val black = data["black"] as? String ?: ""
                val fen = data["fen"] as? String ?: ""
                val statusStr = data["status"] as? String ?: ""
                
                myUsername = SecurePrefs.getUsername(context)
                myColor = if (white.equals(myUsername, ignoreCase = true)) Color.WHITE else Color.BLACK
                opponentUsername = if (myColor == Color.WHITE) black else white
                
                // Automatically flip board if we are Black
                if (myColor == Color.BLACK && !isFlipped) {
                    flipBoard()
                } else if (myColor == Color.WHITE && isFlipped) {
                    flipBoard()
                }
                
                // Load FEN
                PuzzleDatabase.loadPuzzleFen(game, fen)
                
                // Reconstruct move history
                val moves = data["moves"] as? List<Map<String, Any?>> ?: emptyList()
                game.sanHistory.clear()
                moves.forEach { m ->
                    val san = m["san"] as? String
                    if (san != null) game.sanHistory.add(san)
                }
                
                // Set last move indicators
                if (moves.isNotEmpty()) {
                    val lastM = moves.last()
                    val from = lastM["from"] as? String ?: ""
                    val to = lastM["to"] as? String ?: ""
                    if (from.length >= 2 && to.length >= 2) {
                        val fromPos = Pos(8 - from[1].digitToInt(), from[0] - 'a')
                        val toPos = Pos(8 - to[1].digitToInt(), to[0] - 'a')
                        boardView.setLastMove(fromPos, toPos, false)
                    }
                } else {
                    boardView.setLastMove(null, null, false)
                }
                
                renderBoard()
                updateUI()
                
                if (statusStr != "active") {
                    SettingsManager.setActiveGameId(context, null)
                    activeGameId = null
                }
            } else {
                SettingsManager.setActiveGameId(context, null)
                activeGameId = null
            }
        }
    }

    private fun startEventCollection() {
        sseCollectionJob?.cancel()
        sseCollectionJob = viewLifecycleOwner.lifecycleScope.launch {
            SSEService.eventBus.collect { event ->
                handleSseEvent(event)
            }
        }
    }

    private fun handleSseEvent(event: SseEvent) {
        val context = context ?: return
        when (event.type) {
            "connected" -> {
                Toast.makeText(context, "Connected to online lobby!", Toast.LENGTH_SHORT).show()
            }
            "friend_request" -> {
                Toast.makeText(context, "New friend request from ${event.from}!", Toast.LENGTH_LONG).show()
            }
            "friend_accepted" -> {
                Toast.makeText(context, "${event.by} accepted your friend request!", Toast.LENGTH_LONG).show()
            }
            "challenge_incoming" -> {
                incomingChallenges.removeAll { it.challengeId == event.challengeId }
                incomingChallenges.add(event)
                Toast.makeText(context, "Challenge received from ${event.from}!", Toast.LENGTH_LONG).show()
            }
            "challenge_declined" -> {
                Toast.makeText(context, "${event.by} declined your challenge.", Toast.LENGTH_LONG).show()
            }
            "challenge_expired" -> {
                incomingChallenges.removeAll { it.challengeId == event.challengeId }
                Toast.makeText(context, "Challenge expired.", Toast.LENGTH_SHORT).show()
            }
            "game_start" -> {
                val white = event.white ?: ""
                val black = event.black ?: ""
                val gId = event.gameId ?: ""
                
                activeGameId = gId
                SettingsManager.setActiveGameId(context, gId)
                myUsername = SecurePrefs.getUsername(context)
                myColor = if (white.equals(myUsername, ignoreCase = true)) Color.WHITE else Color.BLACK
                opponentUsername = if (myColor == Color.WHITE) black else white
                
                // Automatically flip board if we are Black
                if (myColor == Color.BLACK && !isFlipped) {
                    flipBoard()
                } else if (myColor == Color.WHITE && isFlipped) {
                    flipBoard()
                }
                
                // Initialize board
                isAIThinking = false
                boardView.isInteractive = true
                engineProgress.visibility = View.GONE
                engineText.visibility = View.GONE
                game.setupInitialPosition()
                boardView.setLastMove(null, null, false)
                boardView.clearSelection()
                
                // Dismiss open BottomSheet
                val sheet = childFragmentManager.findFragmentByTag("FriendsBottomSheet") as? BottomSheetDialogFragment
                sheet?.dismiss()
                
                Toast.makeText(context, "Game started! You are ${myColor.toString().lowercase()}", Toast.LENGTH_LONG).show()
                renderBoard()
                updateUI()
            }
            "game_move" -> {
                if (event.gameId == activeGameId) {
                    val from = event.from ?: return
                    val to = event.to ?: return
                    val fromPos = Pos(8 - from[1].digitToInt(), from[0] - 'a')
                    val toPos = Pos(8 - to[1].digitToInt(), to[0] - 'a')
                    
                    val legal = game.getLegalMoves(fromPos)
                    val promoType = when(event.promotion) {
                        "q" -> PieceType.QUEEN
                        "r" -> PieceType.ROOK
                        "b" -> PieceType.BISHOP
                        "n" -> PieceType.KNIGHT
                        else -> null
                    }
                    val move = legal.find { it.to == toPos && (promoType == null || it.promotion == promoType) }
                        ?: legal.find { it.to == toPos }

                    if (move != null) {
                        game.applyMove(move)
                        boardView.setLastMove(fromPos, toPos)
                        boardView.clearSelection()
                        renderBoard()
                        updateUI()
                        
                        // Play sound and check for states
                        val status = game.gameStatus
                        val isCapture = move.capturedPiece != null || move.isEnPassant
                        val soundName = if (isCapture) "Capture" else "Move"
                        SettingsManager.playSound(context, soundName)
                        if (status == ChessGame.GameStatus.CHECK || status == ChessGame.GameStatus.CHECKMATE) {
                            SettingsManager.playSound(context, "GenericNotify")
                        }
                        if (status == ChessGame.GameStatus.CHECKMATE || status == ChessGame.GameStatus.STALEMATE || status == ChessGame.GameStatus.DRAW) {
                            showGameOverDialog(status)
                        }
                    } else {
                        // Desync fallback loading FEN
                        event.fen?.let { fen ->
                            PuzzleDatabase.loadPuzzleFen(game, fen)
                            boardView.setLastMove(fromPos, toPos)
                            boardView.clearSelection()
                            renderBoard()
                            updateUI()
                        }
                    }
                }
            }
            "game_ended" -> {
                if (event.gameId == activeGameId) {
                    val reasonText = when (event.status) {
                        "resigned" -> "${event.loser} resigned. Winner: ${event.winner}"
                        "abandoned" -> "${event.loser} abandoned. Winner: ${event.winner}"
                        "draw" -> "Match drawn."
                        else -> "${event.winner} wins by checkmate!"
                    }
                    
                    AlertDialog.Builder(context)
                        .setTitle("♟ Game Ended")
                        .setMessage(reasonText)
                        .setPositiveButton("OK", null)
                        .show()
                        
                    activeGameId = null
                    SettingsManager.setActiveGameId(context, null)
                    updateUI()
                }
            }
            "opponent_disconnected" -> {
                if (event.gameId == activeGameId) {
                    Toast.makeText(context, "Opponent disconnected. Claiming victory in 5 mins if offline.", Toast.LENGTH_LONG).show()
                }
            }
            "opponent_reconnected" -> {
                if (event.gameId == activeGameId) {
                    Toast.makeText(context, "Opponent reconnected.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─── Move History Adapter ─────────────────────────────────────────────
    class MoveHistoryAdapter : RecyclerView.Adapter<MoveHistoryAdapter.VH>() {
        var moves = listOf<String>()

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val numText: TextView = itemView.findViewById(R.id.move_num)
            val moveText: TextView = itemView.findViewById(R.id.move_san)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_move, parent, false)
            return VH(v)
        }

        override fun getItemCount() = moves.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val moveNum = position / 2 + 1
            val isWhiteMove = position % 2 == 0
            holder.numText.text = if (isWhiteMove) "$moveNum." else ""
            holder.moveText.text = moves[position]
            holder.moveText.setTextColor(
                if (position == moves.size - 1) 0xFFb58863.toInt() else 0xFFd9d4cd.toInt()
            )
        }
    }
}
