package com.chessomania.app.ui

import android.app.Dialog
import android.view.Window
import android.graphics.drawable.ColorDrawable
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
import com.chessomania.app.MainActivity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.content.ClipboardManager
import android.content.ClipData
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

    // P2P Multiplayer components
    private lateinit var onlinePanel: LinearLayout
    private lateinit var p2pSetupLayout: LinearLayout
    private lateinit var btnP2pHost: Button
    private lateinit var editP2pCode: EditText
    private lateinit var btnP2pJoin: Button
    private lateinit var p2pHostLayout: LinearLayout
    private lateinit var textP2pRoomCode: TextView
    private lateinit var btnP2pCopyCode: Button
    private lateinit var textP2pHostStatus: TextView
    private lateinit var btnP2pCancelHost: Button
    private lateinit var p2pConnectedLayout: LinearLayout
    private lateinit var textP2pStatus: TextView
    private lateinit var textP2pTurnInfo: TextView
    private lateinit var btnP2pLeave: Button
    private lateinit var p2pWebView: WebView
    private var isWebViewInited = false

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

    private val moveAdapter = MoveHistoryAdapter()

    private val clearHintRunnable = Runnable {
        boardView.clearHintArrow()
    }

    private fun disableCheat() {
        boardView.isHostCheatActive = false
        boardView.onDoubleTapped = null
        handler.removeCallbacks(clearHintRunnable)
        boardView.clearHintArrow()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_play, container, false)
    }

    override fun onDestroyView() {
        disableCheat()
        super.onDestroyView()
    }

    fun isInActiveGame(): Boolean {
        return !isFriendMode || activeGameId != null
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

        // P2P UI bindings
        onlinePanel = view.findViewById(R.id.online_panel)
        p2pSetupLayout = view.findViewById(R.id.p2p_setup_layout)
        btnP2pHost = view.findViewById(R.id.btn_p2p_host)
        editP2pCode = view.findViewById(R.id.edit_p2p_code)
        btnP2pJoin = view.findViewById(R.id.btn_p2p_join)
        p2pHostLayout = view.findViewById(R.id.p2p_host_layout)
        textP2pRoomCode = view.findViewById(R.id.text_p2p_room_code)
        btnP2pCopyCode = view.findViewById(R.id.btn_p2p_copy_code)
        textP2pHostStatus = view.findViewById(R.id.text_p2p_host_status)
        btnP2pCancelHost = view.findViewById(R.id.btn_p2p_cancel_host)
        p2pConnectedLayout = view.findViewById(R.id.p2p_connected_layout)
        textP2pStatus = view.findViewById(R.id.text_p2p_status)
        textP2pTurnInfo = view.findViewById(R.id.text_p2p_turn_info)
        btnP2pLeave = view.findViewById(R.id.btn_p2p_leave)
        p2pWebView = view.findViewById(R.id.p2p_webview)

        btnP2pHost.setOnClickListener {
            initP2PWebView()
            val shortCode = generateShortRoomCode()
            val boardTheme = SettingsManager.getBoardTheme(requireContext())
            val pieceTheme = SettingsManager.getPieceTheme(requireContext())
            val soundTheme = SettingsManager.getSoundTheme(requireContext())
            p2pWebView.loadUrl("javascript:createRoom('$shortCode', '$boardTheme', '$pieceTheme', '$soundTheme')")
        }

        btnP2pLeave.setOnClickListener {
            val context = context ?: return@setOnClickListener
            val dialog = Dialog(context)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_game_over)
            dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

            val icon = dialog.findViewById<ImageView>(R.id.dialog_icon)
            val titleText = dialog.findViewById<TextView>(R.id.dialog_title)
            val subtitleText = dialog.findViewById<TextView>(R.id.dialog_subtitle)
            val btnPositive = dialog.findViewById<Button>(R.id.btn_positive)
            val btnNegative = dialog.findViewById<Button>(R.id.btn_negative)
            setupReviewButton(dialog, false)

            icon.setImageResource(R.drawable.ic_sword)
            icon.setColorFilter(ContextCompat.getColor(context, R.color.red))
            titleText.text = "Leave Game"
            subtitleText.text = "Are you sure you want to leave the game?"

            btnPositive.text = "Yes"
            btnPositive.backgroundTintList = ContextCompat.getColorStateList(context, R.color.red)
            btnNegative.text = "No"

            btnPositive.setOnClickListener {
                dialog.dismiss()
                activity?.runOnUiThread {
                    p2pWebView.loadUrl("javascript:leave()")
                }
                showLocalLeaveDialog()
            }
            btnNegative.setOnClickListener {
                dialog.dismiss()
            }
            dialog.show()
        }

        btnP2pJoin.setOnClickListener {
            val code = editP2pCode.text.toString().trim().uppercase()
            if (code.length == 6) {
                initP2PWebView()
                p2pWebView.loadUrl("javascript:joinRoom('$code')")
            } else {
                Toast.makeText(context, "Please enter a 6-character room code", Toast.LENGTH_SHORT).show()
            }
        }

        btnP2pCopyCode.setOnClickListener {
            val code = textP2pRoomCode.text.toString()
            if (code.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Room Code", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Room code copied!", Toast.LENGTH_SHORT).show()
            }
        }

        btnP2pCancelHost.setOnClickListener {
            hideP2P()
            showP2PSetup()
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
                showP2PSetup()
            } else {
                hideP2P()
            }
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
        handler.removeCallbacks(clearHintRunnable)
        boardView.clearHintArrow()
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
            if (status == ChessGame.GameStatus.CHECKMATE) {
                SettingsManager.performHapticFeedback(requireContext(), SettingsManager.HapticType.CHECKMATE)
            } else {
                SettingsManager.performHapticFeedback(requireContext(), SettingsManager.HapticType.STALEMATE)
            }
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
                SettingsManager.performHapticFeedback(requireContext(), SettingsManager.HapticType.CHECK)
            } else {
                if (isCapture) {
                    SettingsManager.performHapticFeedback(requireContext(), SettingsManager.HapticType.CAPTURE)
                } else {
                    SettingsManager.performHapticFeedback(requireContext(), SettingsManager.HapticType.MOVE)
                }
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
        val fromStr = "${(from.col + 'a'.code).toChar()}${8 - from.row}"
        val toStr = "${(to.col + 'a'.code).toChar()}${8 - to.row}"
        val promoStr = when (promotion) {
            PieceType.QUEEN -> "q"
            PieceType.ROOK -> "r"
            PieceType.BISHOP -> "b"
            PieceType.KNIGHT -> "n"
            else -> ""
        }
        activity?.runOnUiThread {
            p2pWebView.loadUrl("javascript:sendMove('$fromStr', '$toStr', '$promoStr')")
        }
        updateP2PTurnInfo()
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
                        if (st == ChessGame.GameStatus.CHECKMATE) {
                            SettingsManager.performHapticFeedback(requireContext(), SettingsManager.HapticType.CHECKMATE)
                        } else {
                            SettingsManager.performHapticFeedback(requireContext(), SettingsManager.HapticType.STALEMATE)
                        }
                        showGameOverDialog(st)
                    } else {
                        val isCapture = bestMove.capturedPiece != null || bestMove.isEnPassant
                        val soundName = if (isCapture) "Capture" else "Move"
                        SettingsManager.playSound(requireContext(), soundName)
                        if (st == ChessGame.GameStatus.CHECK) {
                            SettingsManager.playSound(requireContext(), "GenericNotify")
                            SettingsManager.performHapticFeedback(requireContext(), SettingsManager.HapticType.CHECK)
                        } else {
                            if (isCapture) {
                                SettingsManager.performHapticFeedback(requireContext(), SettingsManager.HapticType.CAPTURE)
                            } else {
                                SettingsManager.performHapticFeedback(requireContext(), SettingsManager.HapticType.MOVE)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showPromotionDialog(from: Pos, to: Pos) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_promotion)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.setCancelable(false)

        dialog.findViewById<View>(R.id.btn_promo_queen).setOnClickListener {
            dialog.dismiss()
            handleMove(from, to, PieceType.QUEEN)
        }
        dialog.findViewById<View>(R.id.btn_promo_rook).setOnClickListener {
            dialog.dismiss()
            handleMove(from, to, PieceType.ROOK)
        }
        dialog.findViewById<View>(R.id.btn_promo_bishop).setOnClickListener {
            dialog.dismiss()
            handleMove(from, to, PieceType.BISHOP)
        }
        dialog.findViewById<View>(R.id.btn_promo_knight).setOnClickListener {
            dialog.dismiss()
            handleMove(from, to, PieceType.KNIGHT)
        }
        dialog.show()
    }

    private fun showGameOverDialog(status: ChessGame.GameStatus) {
        if (!isAdded) return
        disableCheat()
        
        val bgMusic = com.chessomania.app.audio.BgMusicManager.getInstance(requireContext())
        if (status == ChessGame.GameStatus.CHECKMATE) {
            val userWon = if (isFriendMode) {
                game.currentTurn != myColor
            } else {
                game.currentTurn == Color.BLACK
            }
            if (userWon) {
                bgMusic.play(com.chessomania.app.audio.BgMusicManager.MusicTrack.VICTORY)
            } else {
                bgMusic.play(com.chessomania.app.audio.BgMusicManager.MusicTrack.DEFEAT)
            }
        } else {
            bgMusic.play(com.chessomania.app.audio.BgMusicManager.MusicTrack.MENU)
        }

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_game_over)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        val icon = dialog.findViewById<ImageView>(R.id.dialog_icon)
        val titleText = dialog.findViewById<TextView>(R.id.dialog_title)
        val subtitleText = dialog.findViewById<TextView>(R.id.dialog_subtitle)
        val btnPositive = dialog.findViewById<Button>(R.id.btn_positive)
        val btnNegative = dialog.findViewById<Button>(R.id.btn_negative)
        setupReviewButton(dialog, true)

        val title = when(status) {
            ChessGame.GameStatus.CHECKMATE -> "Checkmate!"
            ChessGame.GameStatus.STALEMATE -> "Stalemate!"
            ChessGame.GameStatus.DRAW -> "Draw!"
            else -> "Game Over"
        }
        val msg = when(status) {
            ChessGame.GameStatus.CHECKMATE -> if (game.currentTurn == Color.WHITE) "Black wins!" else "White wins!"
            ChessGame.GameStatus.STALEMATE -> "No legal moves available."
            ChessGame.GameStatus.DRAW -> "50-move rule reached."
            else -> ""
        }

        // Match UI/UX robot icon for AI, sword icon for other modes
        if (isAIMode) {
            icon.setImageResource(R.drawable.ic_robot)
        } else {
            icon.setImageResource(R.drawable.ic_sword)
        }

        titleText.text = title
        subtitleText.text = msg

        btnPositive.setOnClickListener {
            dialog.dismiss()
            newGame()
        }
        btnNegative.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
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
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_game_over)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        val icon = dialog.findViewById<ImageView>(R.id.dialog_icon)
        val titleText = dialog.findViewById<TextView>(R.id.dialog_title)
        val subtitleText = dialog.findViewById<TextView>(R.id.dialog_subtitle)
        val btnPositive = dialog.findViewById<Button>(R.id.btn_positive)
        val btnNegative = dialog.findViewById<Button>(R.id.btn_negative)
        setupReviewButton(dialog, false)

        icon.setImageResource(R.drawable.ic_sword)
        icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.red))
        titleText.text = "Resign?"
        subtitleText.text = "Are you sure you want to resign?"

        btnPositive.text = "Yes"
        btnPositive.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.red)
        btnNegative.text = "No"

        btnPositive.setOnClickListener {
            dialog.dismiss()
            if (isFriendMode) {
                activity?.runOnUiThread {
                    p2pWebView.loadUrl("javascript:resign()")
                }
                showLocalResignDialog()
            } else {
                showGameOverDialog(ChessGame.GameStatus.CHECKMATE)
            }
        }
        btnNegative.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showLocalResignDialog() {
        val context = context ?: return
        disableCheat()
        com.chessomania.app.audio.BgMusicManager.getInstance(context).play(com.chessomania.app.audio.BgMusicManager.MusicTrack.DEFEAT)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_game_over)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        val icon = dialog.findViewById<ImageView>(R.id.dialog_icon)
        val titleText = dialog.findViewById<TextView>(R.id.dialog_title)
        val subtitleText = dialog.findViewById<TextView>(R.id.dialog_subtitle)
        val btnPositive = dialog.findViewById<Button>(R.id.btn_positive)
        val btnNegative = dialog.findViewById<Button>(R.id.btn_negative)
        setupReviewButton(dialog, true)

        icon.setImageResource(R.drawable.ic_sword)
        icon.setColorFilter(ContextCompat.getColor(context, R.color.red))
        titleText.text = "Resigned"
        subtitleText.text = "You resigned. Game over!"
        btnPositive.text = "OK"
        btnNegative.visibility = View.GONE
        btnPositive.setOnClickListener { dialog.dismiss() }
        dialog.show()

        activeGameId = null
        updateUI()
    }

    private fun showLocalLeaveDialog() {
        val context = context ?: return
        disableCheat()
        com.chessomania.app.audio.BgMusicManager.getInstance(context).play(com.chessomania.app.audio.BgMusicManager.MusicTrack.MENU)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_game_over)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        val icon = dialog.findViewById<ImageView>(R.id.dialog_icon)
        val titleText = dialog.findViewById<TextView>(R.id.dialog_title)
        val subtitleText = dialog.findViewById<TextView>(R.id.dialog_subtitle)
        val btnPositive = dialog.findViewById<Button>(R.id.btn_positive)
        val btnNegative = dialog.findViewById<Button>(R.id.btn_negative)
        setupReviewButton(dialog, true)

        icon.setImageResource(R.drawable.ic_sword)
        icon.setColorFilter(ContextCompat.getColor(context, R.color.red))
        titleText.text = "Left Game"
        subtitleText.text = "You left the game."
        btnPositive.text = "OK"
        btnNegative.visibility = View.GONE
        btnPositive.setOnClickListener { dialog.dismiss() }
        dialog.show()

        hideP2P()
        showP2PSetup()
    }

    private fun newGame() {
        if (isFriendMode) {
            activity?.runOnUiThread {
                p2pWebView.loadUrl("javascript:rematch()")
            }
            Toast.makeText(context, "Requesting rematch...", Toast.LENGTH_SHORT).show()
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
        SettingsManager.performHapticFeedback(requireContext(), SettingsManager.HapticType.GAME_START)
        if (!isFriendMode) {
            com.chessomania.app.audio.BgMusicManager.getInstance(requireContext()).play(com.chessomania.app.audio.BgMusicManager.MusicTrack.GAMEPLAY)
        }
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
            (activity as? MainActivity)?.updateStatusBadge("P2P")
            initP2PWebView()
            if (activeGameId == "p2p_game") {
                p2pSetupLayout.visibility = View.GONE
                p2pHostLayout.visibility = View.GONE
                p2pConnectedLayout.visibility = View.VISIBLE
            } else if (p2pHostLayout.visibility == View.VISIBLE) {
                // Keep host waiting layout
            } else {
                p2pSetupLayout.visibility = View.VISIBLE
                p2pHostLayout.visibility = View.GONE
                p2pConnectedLayout.visibility = View.GONE
            }
        } else {
            (activity as? MainActivity)?.updateStatusBadge("OFFLINE")
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            boardView.refreshTheme()
            if (isFriendMode) {
                (activity as? MainActivity)?.updateStatusBadge("P2P")
                if (activeGameId == "p2p_game") {
                    p2pSetupLayout.visibility = View.GONE
                    p2pHostLayout.visibility = View.GONE
                    p2pConnectedLayout.visibility = View.VISIBLE
                } else if (p2pHostLayout.visibility == View.VISIBLE) {
                    // Keep host waiting layout
                } else {
                    p2pSetupLayout.visibility = View.VISIBLE
                    p2pHostLayout.visibility = View.GONE
                    p2pConnectedLayout.visibility = View.GONE
                }
            } else {
                (activity as? MainActivity)?.updateStatusBadge("OFFLINE")
            }
        }
    }

    private fun showP2PSetup() {
        p2pSetupLayout.visibility = View.VISIBLE
        p2pHostLayout.visibility = View.GONE
        p2pConnectedLayout.visibility = View.GONE
        (activity as? MainActivity)?.updateStatusBadge("P2P")
        initP2PWebView()
    }

    private fun hideP2P() {
        activity?.runOnUiThread {
            p2pWebView.loadUrl("javascript:disconnect()")
        }
        SettingsManager.clearOverrides()
        boardView.refreshTheme()
        p2pSetupLayout.visibility = View.GONE
        p2pHostLayout.visibility = View.GONE
        p2pConnectedLayout.visibility = View.GONE
        (activity as? MainActivity)?.updateStatusBadge("OFFLINE")
        
        activeGameId = null
        myColor = null
        myUsername = null
        opponentUsername = null
        
        nameBlack.text = if (isAIMode) "ChessOmania Engine" else "Player 2 (Black)"
        nameWhite.text = "You (White)"
        newGame()
    }

    private fun initP2PWebView() {
        if (isWebViewInited) return
        if (context == null) return
        p2pWebView.settings.javaScriptEnabled = true
        p2pWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isWebViewInited = true
            }
        }
        p2pWebView.addJavascriptInterface(ChessP2PInterface(this), "Android")
        p2pWebView.loadUrl("file:///android_asset/peer_bridge.html")
    }

    private fun generateShortRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = StringBuilder()
        for (i in 0 until 6) {
            code.append(chars[(chars.indices).random()])
        }
        return code.toString()
    }

    fun onRoomCreated(code: String) {
        textP2pRoomCode.text = code
        p2pSetupLayout.visibility = View.GONE
        p2pHostLayout.visibility = View.VISIBLE
        p2pConnectedLayout.visibility = View.GONE
    }

    fun onConnected(isHost: Boolean, boardTheme: String, pieceTheme: String, soundTheme: String) {
        val context = context ?: return
        
        if (boardTheme.isNotEmpty()) {
            SettingsManager.overrideBoardTheme = SettingsManager.mapBoardTheme(boardTheme)
        }
        if (pieceTheme.isNotEmpty()) {
            SettingsManager.overridePieceTheme = pieceTheme
        }
        if (soundTheme.isNotEmpty()) {
            SettingsManager.overrideSoundTheme = SettingsManager.mapSoundTheme(soundTheme)
        }
        boardView.refreshTheme()
        activeGameId = "p2p_game"
        myColor = if (isHost) Color.WHITE else Color.BLACK
        opponentUsername = if (isHost) "Friend (Black)" else "Friend (White)"
        myUsername = if (isHost) "You (White)" else "You (Black)"

        disableCheat()
        val hostHintEnabled = com.chessomania.app.net.SecurePrefs.getHostHintEnabled(context)
        if (hostHintEnabled) {
            com.chessomania.app.net.SecurePrefs.clearHostHintEnabled(context)
            if (isHost) {
                boardView.isHostCheatActive = true
                boardView.onDoubleTapped = {
                    if (boardView.isHostCheatActive && activeGameId == "p2p_game" && myColor == game.currentTurn) {
                        val gameCopy = game.clone()
                        val moveCountBefore = game.moveHistory.size
                        thread {
                            // Calculate at depth 5 for GM-level best move suggestions
                            val bestMove = ai.getBestMove(gameCopy, 5)
                            handler.post {
                                if (boardView.isHostCheatActive &&
                                    activeGameId == "p2p_game" &&
                                    game.moveHistory.size == moveCountBefore &&
                                    bestMove != null
                                ) {
                                    boardView.showHintArrow(bestMove.from, bestMove.to)
                                    handler.removeCallbacks(clearHintRunnable)
                                    handler.postDelayed(clearHintRunnable, 3000)
                                }
                            }
                        }
                    }
                }
            }
        }

        nameWhite.text = if (isHost) "You (White)" else "Friend (White)"
        nameBlack.text = if (isHost) "Friend (Black)" else "You (Black)"

        if (myColor == Color.BLACK && !isFlipped) {
            flipBoard()
        } else if (myColor == Color.WHITE && isFlipped) {
            flipBoard()
        }

        isAIThinking = false
        boardView.isInteractive = true
        engineProgress.visibility = View.GONE
        engineText.visibility = View.GONE
        game.setupInitialPosition()
        boardView.setLastMove(null, null, false)
        boardView.clearSelection()
        SettingsManager.performHapticFeedback(context, SettingsManager.HapticType.GAME_START)

        p2pSetupLayout.visibility = View.GONE
        p2pHostLayout.visibility = View.GONE
        p2pConnectedLayout.visibility = View.VISIBLE
        textP2pStatus.text = "✓ Connected! You play as " + (if (isHost) "White" else "Black")
        updateP2PTurnInfo()

        Toast.makeText(context, "Game started! You are ${if (isHost) "white" else "black"}", Toast.LENGTH_SHORT).show()
        renderBoard()
        updateUI()
        com.chessomania.app.audio.BgMusicManager.getInstance(context).play(com.chessomania.app.audio.BgMusicManager.MusicTrack.GAMEPLAY)
    }

    private fun updateP2PTurnInfo() {
        val turnColor = game.currentTurn
        val isMyTurn = (myColor == turnColor)
        textP2pTurnInfo.text = if (isMyTurn) "🟢 Your turn!" else "⌛ Opponent is thinking..."
        textP2pTurnInfo.setTextColor(if (isMyTurn) 0xFF34d399.toInt() else 0xFFaba399.toInt())
    }

    fun onMoveReceived(from: String, to: String, promotion: String) {
        handler.removeCallbacks(clearHintRunnable)
        boardView.clearHintArrow()
        val context = context ?: return
        if (activeGameId == "p2p_game") {
            val fromPos = Pos(8 - from[1].digitToInt(), from[0] - 'a')
            val toPos = Pos(8 - to[1].digitToInt(), to[0] - 'a')
            
            val legal = game.getLegalMoves(fromPos)
            val promoType = when(promotion) {
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
                updateP2PTurnInfo()
                
                val status = game.gameStatus
                val isCapture = move.capturedPiece != null || move.isEnPassant
                val soundName = if (isCapture) "Capture" else "Move"
                SettingsManager.playSound(context, soundName)
                if (status == ChessGame.GameStatus.CHECKMATE || status == ChessGame.GameStatus.STALEMATE || status == ChessGame.GameStatus.DRAW) {
                    if (status == ChessGame.GameStatus.CHECKMATE) {
                        SettingsManager.playSound(context, "GenericNotify")
                        SettingsManager.performHapticFeedback(context, SettingsManager.HapticType.CHECKMATE)
                    } else {
                        SettingsManager.performHapticFeedback(context, SettingsManager.HapticType.STALEMATE)
                    }
                    showGameOverDialog(status)
                } else {
                    if (status == ChessGame.GameStatus.CHECK) {
                        SettingsManager.playSound(context, "GenericNotify")
                        SettingsManager.performHapticFeedback(context, SettingsManager.HapticType.CHECK)
                    } else {
                        if (isCapture) {
                            SettingsManager.performHapticFeedback(context, SettingsManager.HapticType.CAPTURE)
                        } else {
                            SettingsManager.performHapticFeedback(context, SettingsManager.HapticType.MOVE)
                        }
                    }
                }
            }
        }
    }

    fun onResignReceived() {
        val context = context ?: return
        disableCheat()
        com.chessomania.app.audio.BgMusicManager.getInstance(context).play(com.chessomania.app.audio.BgMusicManager.MusicTrack.VICTORY)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_game_over)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        val icon = dialog.findViewById<ImageView>(R.id.dialog_icon)
        val titleText = dialog.findViewById<TextView>(R.id.dialog_title)
        val subtitleText = dialog.findViewById<TextView>(R.id.dialog_subtitle)
        val btnPositive = dialog.findViewById<Button>(R.id.btn_positive)
        val btnNegative = dialog.findViewById<Button>(R.id.btn_negative)
        setupReviewButton(dialog, true)

        icon.setImageResource(R.drawable.ic_sword)
        titleText.text = "Game Ended"
        subtitleText.text = "Opponent resigned. You win!"
        btnPositive.text = "OK"
        btnNegative.visibility = View.GONE
        btnPositive.setOnClickListener { dialog.dismiss() }
        dialog.show()

        activeGameId = null
        updateUI()
    }

    fun onRematchReceived() {
        val context = context ?: return
        disableCheat()
        myColor = if (myColor == Color.WHITE) Color.BLACK else Color.WHITE
        val isHost = (myColor == Color.WHITE)
        opponentUsername = if (isHost) "Friend (Black)" else "Friend (White)"
        myUsername = if (isHost) "You (White)" else "You (Black)"

        nameWhite.text = if (isHost) "You (White)" else "Friend (White)"
        nameBlack.text = if (isHost) "Friend (Black)" else "You (Black)"

        if (myColor == Color.BLACK && !isFlipped) {
            flipBoard()
        } else if (myColor == Color.WHITE && isFlipped) {
            flipBoard()
        }

        isAIThinking = false
        boardView.isInteractive = true
        game.setupInitialPosition()
        boardView.setLastMove(null, null, false)
        boardView.clearSelection()
        SettingsManager.performHapticFeedback(context, SettingsManager.HapticType.GAME_START)

        p2pSetupLayout.visibility = View.GONE
        p2pHostLayout.visibility = View.GONE
        p2pConnectedLayout.visibility = View.VISIBLE
        textP2pStatus.text = "✓ Rematch! You play as " + (if (isHost) "White" else "Black")
        updateP2PTurnInfo()

        Toast.makeText(context, "Rematch started!", Toast.LENGTH_SHORT).show()
        renderBoard()
        updateUI()
        com.chessomania.app.audio.BgMusicManager.getInstance(context).play(com.chessomania.app.audio.BgMusicManager.MusicTrack.GAMEPLAY)
    }

    fun onLeaveReceived() {
        if (activeGameId == null) return
        val context = context ?: return
        disableCheat()
        com.chessomania.app.audio.BgMusicManager.getInstance(context).play(com.chessomania.app.audio.BgMusicManager.MusicTrack.VICTORY)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_game_over)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        val icon = dialog.findViewById<ImageView>(R.id.dialog_icon)
        val titleText = dialog.findViewById<TextView>(R.id.dialog_title)
        val subtitleText = dialog.findViewById<TextView>(R.id.dialog_subtitle)
        val btnPositive = dialog.findViewById<Button>(R.id.btn_positive)
        val btnNegative = dialog.findViewById<Button>(R.id.btn_negative)
        setupReviewButton(dialog, true)

        icon.setImageResource(R.drawable.ic_sword)
        icon.setColorFilter(ContextCompat.getColor(context, R.color.red))
        titleText.text = "Opponent Left"
        subtitleText.text = "Your opponent has left the game."
        btnPositive.text = "OK"
        btnNegative.visibility = View.GONE
        btnPositive.setOnClickListener { dialog.dismiss() }
        dialog.show()

        hideP2P()
        showP2PSetup()
    }

    fun onDisconnected() {
        if (activeGameId == null) return
        val context = context ?: return
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_game_over)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        val icon = dialog.findViewById<ImageView>(R.id.dialog_icon)
        val titleText = dialog.findViewById<TextView>(R.id.dialog_title)
        val subtitleText = dialog.findViewById<TextView>(R.id.dialog_subtitle)
        val btnPositive = dialog.findViewById<Button>(R.id.btn_positive)
        val btnNegative = dialog.findViewById<Button>(R.id.btn_negative)
        setupReviewButton(dialog, true)

        icon.setImageResource(R.drawable.ic_sword)
        icon.setColorFilter(ContextCompat.getColor(context, R.color.red))
        titleText.text = "Disconnected"
        subtitleText.text = "Opponent disconnected."
        btnPositive.text = "OK"
        btnNegative.visibility = View.GONE
        btnPositive.setOnClickListener { dialog.dismiss() }
        dialog.show()

        hideP2P()
        showP2PSetup()
    }

    fun onError(errorMsg: String) {
        val context = context ?: return
        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        hideP2P()
        showP2PSetup()
    }

    private fun setupReviewButton(dialog: Dialog, show: Boolean) {
        val btnReview = dialog.findViewById<Button>(R.id.btn_review)
        btnReview.visibility = View.GONE
    }

    class ChessP2PInterface(private val fragment: PlayFragment) {
        @JavascriptInterface
        fun onRoomCreated(code: String) {
            fragment.activity?.runOnUiThread {
                fragment.onRoomCreated(code)
            }
        }

        @JavascriptInterface
        fun onConnected(isHost: Boolean, boardTheme: String?, pieceTheme: String?, soundTheme: String?) {
            fragment.activity?.runOnUiThread {
                fragment.onConnected(isHost, boardTheme ?: "", pieceTheme ?: "", soundTheme ?: "")
            }
        }

        @JavascriptInterface
        fun onMoveReceived(from: String, to: String, promotion: String) {
            fragment.activity?.runOnUiThread {
                fragment.onMoveReceived(from, to, promotion)
            }
        }

        @JavascriptInterface
        fun onResignReceived() {
            fragment.activity?.runOnUiThread {
                fragment.onResignReceived()
            }
        }

        @JavascriptInterface
        fun onLeaveReceived() {
            fragment.activity?.runOnUiThread {
                fragment.onLeaveReceived()
            }
        }

        @JavascriptInterface
        fun onRematchReceived() {
            fragment.activity?.runOnUiThread {
                fragment.onRematchReceived()
            }
        }

        @JavascriptInterface
        fun onDisconnected() {
            fragment.activity?.runOnUiThread {
                fragment.onDisconnected()
            }
        }

        @JavascriptInterface
        fun onError(errorMsg: String) {
            fragment.activity?.runOnUiThread {
                fragment.onError(errorMsg)
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
