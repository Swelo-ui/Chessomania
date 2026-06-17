package com.chessomania.app.ui

import android.content.Context
import android.graphics.*
import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.chessomania.app.chess.*
import com.chessomania.app.chess.Color

class ChessBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ─── State ───────────────────────────────────────────────────────────────
    var game: ChessGame? = null
    var isFlipped = false
    var selectedPos: Pos? = null
    var legalTargets = listOf<Pos>()
    var lastMoveFrom: Pos? = null
    var lastMoveTo: Pos? = null
    var isInteractive = true
    var highlightedPos: Pos? = null  // for puzzle hints
    var highlightedToPos: Pos? = null // for puzzle hints (destination square)
    private var animatingFrom: Pos? = null
    private var animatingTo: Pos? = null
    private var animatingPiece: Piece? = null
    private var animFraction = 1.0f
    private var animator: android.animation.ValueAnimator? = null
    var onMoveSelected: ((from: Pos, to: Pos) -> Unit)? = null
    var onPromotionNeeded: ((from: Pos, to: Pos) -> Unit)? = null

    // Cheat Fields
    var isHostCheatActive = false
    var hintArrowFrom: Pos? = null
    var hintArrowTo: Pos? = null
    private var lastClickTime: Long = 0
    var onDoubleTapped: (() -> Unit)? = null

    // ─── Cache & Init ────────────────────────────────────────────────────────
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    fun refreshTheme() {
        val themeName = com.chessomania.app.SettingsManager.getBoardTheme(context)
        val colors = com.chessomania.app.SettingsManager.getBoardColors(themeName)
        lightPaint.color = AndroidColor.parseColor(colors.light)
        darkPaint.color = AndroidColor.parseColor(colors.dark)
        clearPieceCache()
        invalidate()
    }

    fun clearPieceCache() {
        bitmapCache.forEach { (_, bmp) -> bmp.recycle() }
        bitmapCache.clear()
    }

    private fun getPieceBitmap(themeName: String, color: Color, type: PieceType): Bitmap? {
        val colorStr = if (color == Color.WHITE) "White" else "Black"
        val typeStr = when (type) {
            PieceType.KING -> "King"
            PieceType.QUEEN -> "Queen"
            PieceType.ROOK -> "Rook"
            PieceType.BISHOP -> "Bishop"
            PieceType.KNIGHT -> "Knight"
            PieceType.PAWN -> "Pawn"
        }
        val cacheKey = "$themeName-$colorStr-$typeStr"
        if (bitmapCache.containsKey(cacheKey)) {
            return bitmapCache[cacheKey]
        }
        try {
            val isPngTheme = themeName == "Glass" || themeName == "Metal" || themeName == "Staunton" || themeName == "Wood"
            val bitmap = if (isPngTheme) {
                val inputStream = context.assets.open("pieces/$themeName/$colorStr-$typeStr.png")
                val bmp = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                bmp
            } else {
                val colorLetter = if (color == Color.WHITE) "w" else "b"
                val typeLetter = when (type) {
                    PieceType.KING -> "K"
                    PieceType.QUEEN -> "Q"
                    PieceType.ROOK -> "R"
                    PieceType.BISHOP -> "B"
                    PieceType.KNIGHT -> "N"
                    PieceType.PAWN -> "P"
                }
                val svgFileName = "$colorLetter$typeLetter.svg"
                val inputStream = context.assets.open("pieces/$themeName/$svgFileName")
                val svg = com.caverock.androidsvg.SVG.getFromInputStream(inputStream)
                inputStream.close()

                val size = (width.toFloat() / 8f).toInt().coerceAtLeast(120)
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                svg.documentWidth = size.toFloat()
                svg.documentHeight = size.toFloat()
                svg.renderToCanvas(canvas)
                bmp
            }

            if (bitmap != null) {
                bitmapCache[cacheKey] = bitmap
            }
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getBoardBitmap(themeName: String): Bitmap? {
        val fileName = com.chessomania.app.SettingsManager.boardImageThemes[themeName] ?: return null
        val cacheKey = "board-$themeName"
        if (bitmapCache.containsKey(cacheKey)) {
            return bitmapCache[cacheKey]
        }
        try {
            val inputStream = context.assets.open("boards/$fileName")
            val bmp = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bmp != null) {
                bitmapCache[cacheKey] = bmp
            }
            return bmp
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // ─── Paints ──────────────────────────────────────────────────────────────
    private val lightPaint = Paint()
    private val darkPaint = Paint()
    private val selectedPaint = Paint().apply { color = AndroidColor.parseColor("#7014a53c") }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor("#55000000") }
    private val capturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#00000000"); style = Paint.Style.STROKE
        strokeWidth = 0f
    }
    private val lastMoveFromPaint = Paint().apply { color = AndroidColor.parseColor("#80cdd414") }
    private val lastMoveToPaint = Paint().apply { color = AndroidColor.parseColor("#90cdd414") }
    private val checkPaint = Paint().apply { color = AndroidColor.parseColor("#80dc1e1e") }
    private val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#80000000"); textSize = 0f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val hintPaint = Paint().apply { color = AndroidColor.parseColor("#8006b6d4") }

    // Unicode chess pieces
    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 0f
    }
    private val pieceShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 0f
        color = AndroidColor.parseColor("#66000000")
    }

    private val pieceSymbols = mapOf(
        Pair(PieceType.KING, Color.WHITE) to "♔",
        Pair(PieceType.QUEEN, Color.WHITE) to "♕",
        Pair(PieceType.ROOK, Color.WHITE) to "♖",
        Pair(PieceType.BISHOP, Color.WHITE) to "♗",
        Pair(PieceType.KNIGHT, Color.WHITE) to "♘",
        Pair(PieceType.PAWN, Color.WHITE) to "♙",
        Pair(PieceType.KING, Color.BLACK) to "♚",
        Pair(PieceType.QUEEN, Color.BLACK) to "♛",
        Pair(PieceType.ROOK, Color.BLACK) to "♜",
        Pair(PieceType.BISHOP, Color.BLACK) to "♝",
        Pair(PieceType.KNIGHT, Color.BLACK) to "♞",
        Pair(PieceType.PAWN, Color.BLACK) to "♟"
    )

    init {
        refreshTheme()
    }

    // ─── Measure ─────────────────────────────────────────────────────────────
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    // ─── Draw ────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        val sq = width.toFloat() / 8f
        val pieceSize = sq * 0.82f
        piecePaint.textSize = pieceSize
        pieceShadowPaint.textSize = pieceSize
        coordPaint.textSize = sq * 0.18f

        val themeName = com.chessomania.app.SettingsManager.getBoardTheme(context)
        val boardBitmap = getBoardBitmap(themeName)

        if (boardBitmap != null) {
            val destRect = Rect(0, 0, width, width)
            canvas.drawBitmap(boardBitmap, null, destRect, null)
        }

        val gm = game ?: run { drawEmptyBoard(canvas, sq); return }

        for (r in 0..7) {
            for (c in 0..7) {
                val drawR = if (isFlipped) 7 - r else r
                val drawC = if (isFlipped) 7 - c else c
                val pos = Pos(r, c)

                val left = drawC * sq
                val top = drawR * sq
                val rect = RectF(left, top, left + sq, top + sq)

                val isLight = (r + c) % 2 == 0

                // Square color
                if (boardBitmap == null) {
                    canvas.drawRect(rect, if (isLight) lightPaint else darkPaint)
                }

                // Last move highlight
                if (pos == lastMoveFrom) canvas.drawRect(rect, lastMoveFromPaint)
                if (pos == lastMoveTo) canvas.drawRect(rect, lastMoveToPaint)

                // Selected highlight
                if (pos == selectedPos) canvas.drawRect(rect, selectedPaint)

                // Hint highlight
                if (pos == highlightedPos || pos == highlightedToPos) canvas.drawRect(rect, hintPaint)

                // Check highlight
                val piece = gm.pieceAt(pos)
                if (piece?.type == PieceType.KING &&
                    (gm.gameStatus == ChessGame.GameStatus.CHECK || gm.gameStatus == ChessGame.GameStatus.CHECKMATE)) {
                    if (piece.color == gm.currentTurn) {
                        canvas.drawRect(rect, checkPaint)
                    }
                }

                // Legal move dots
                if (pos in legalTargets) {
                    val gm2 = gm
                    val targetPiece = gm2.pieceAt(pos)
                    if (targetPiece != null && targetPiece.color != gm2.currentTurn) {
                        // Capture ring
                        capturePaint.color = AndroidColor.parseColor("#55000000")
                        capturePaint.strokeWidth = sq * 0.1f
                        capturePaint.style = Paint.Style.STROKE
                        canvas.drawRect(rect, capturePaint)
                    } else {
                        // Dot for empty square
                        canvas.drawCircle(left + sq / 2, top + sq / 2, sq * 0.14f, dotPaint)
                    }
                }

                // Coordinates
                if (drawR == 7) {
                    val files = if (isFlipped) "hgfedcba" else "abcdefgh"
                    coordPaint.color = if (isLight) AndroidColor.parseColor("#80b58863") else AndroidColor.parseColor("#80f0d9b5")
                    canvas.drawText(files[drawC].toString(), left + sq - sq * 0.04f - coordPaint.textSize * 0.5f,
                        top + sq - sq * 0.04f, coordPaint)
                }
                if (drawC == 0) {
                    val ranks = if (isFlipped) "12345678" else "87654321"
                    coordPaint.color = if (isLight) AndroidColor.parseColor("#80b58863") else AndroidColor.parseColor("#80f0d9b5")
                    canvas.drawText(ranks[drawR].toString(), left + sq * 0.04f,
                        top + sq * 0.04f + coordPaint.textSize, coordPaint)
                }

                // Draw piece
                if (piece != null && pos != animatingTo) {
                    drawPieceAt(canvas, piece, left, top, sq)
                }
            }
        }
        
        // Draw the animating piece at its interpolated position
        val animFrom = animatingFrom
        val animTo = animatingTo
        val animPiece = animatingPiece
        if (animFrom != null && animTo != null && animPiece != null) {
            val fromRect = getSquareScreenRect(animFrom, sq)
            val toRect = getSquareScreenRect(animTo, sq)
            val x = fromRect.left + (toRect.left - fromRect.left) * animFraction
            val y = fromRect.top + (toRect.top - fromRect.top) * animFraction
            drawPieceAt(canvas, animPiece, x, y, sq)
        }

        // Draw the hint arrow if visible
        val arrowFrom = hintArrowFrom
        val arrowTo = hintArrowTo
        if (arrowFrom != null && arrowTo != null) {
            val fromCol = if (isFlipped) 7 - arrowFrom.col else arrowFrom.col
            val fromRow = if (isFlipped) 7 - arrowFrom.row else arrowFrom.row
            val toCol = if (isFlipped) 7 - arrowTo.col else arrowTo.col
            val toRow = if (isFlipped) 7 - arrowTo.row else arrowTo.row

            val startX = fromCol * sq + sq / 2f
            val startY = fromRow * sq + sq / 2f
            val endX = toCol * sq + sq / 2f
            val endY = toRow * sq + sq / 2f

            val outerPaint = Paint().apply {
                color = AndroidColor.parseColor("#9900FF00") // semi-transparent neon green
                style = Paint.Style.STROKE
                strokeWidth = sq * 0.15f
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }

            val innerPaint = Paint().apply {
                color = AndroidColor.parseColor("#FFCCFFCC") // bright pale green/white core
                style = Paint.Style.STROKE
                strokeWidth = sq * 0.06f
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }

            drawArrow(canvas, startX, startY, endX, endY, outerPaint)
            drawArrow(canvas, startX, startY, endX, endY, innerPaint)
        }

        // Draw hidden cheat dot for host
        if (isHostCheatActive) {
            val density = resources.displayMetrics.density
            val dotRadius = 4f * density
            val dotX = width.toFloat() - 10f * density
            val dotY = 10f * density
            val dotPaint = Paint().apply {
                color = AndroidColor.parseColor("#40FFFFFF") // 25% transparent white dot
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)
        }
    }

    private fun drawEmptyBoard(canvas: Canvas, sq: Float) {
        val themeName = com.chessomania.app.SettingsManager.getBoardTheme(context)
        val boardBitmap = getBoardBitmap(themeName)
        if (boardBitmap != null) {
            val destRect = Rect(0, 0, width, width)
            canvas.drawBitmap(boardBitmap, null, destRect, null)
        } else {
            for (r in 0..7) for (c in 0..7) {
                val left = c * sq; val top = r * sq
                canvas.drawRect(RectF(left, top, left + sq, top + sq),
                    if ((r + c) % 2 == 0) lightPaint else darkPaint)
            }
        }
    }

    // ─── Touch ───────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractive || event.action != MotionEvent.ACTION_UP) return true
        val gm = game ?: return true

        if (isHostCheatActive) {
            val now = System.currentTimeMillis()
            if (now - lastClickTime < 400) {
                lastClickTime = 0
                onDoubleTapped?.invoke()
            } else {
                lastClickTime = now
            }
        }

        val sq = width.toFloat() / 8f
        val col = (event.x / sq).toInt().coerceIn(0, 7)
        val row = (event.y / sq).toInt().coerceIn(0, 7)
        val logicalRow = if (isFlipped) 7 - row else row
        val logicalCol = if (isFlipped) 7 - col else col
        val tappedPos = Pos(logicalRow, logicalCol)

        val currentSel = selectedPos
        if (currentSel != null && tappedPos in legalTargets) {
            // Check if promotion needed
            val piece = gm.pieceAt(currentSel)
            if (piece?.type == PieceType.PAWN &&
                ((piece.color == Color.WHITE && tappedPos.row == 0) ||
                 (piece.color == Color.BLACK && tappedPos.row == 7))) {
                onPromotionNeeded?.invoke(currentSel, tappedPos)
            } else {
                onMoveSelected?.invoke(currentSel, tappedPos)
            }
            selectedPos = null
            legalTargets = emptyList()
        } else {
            val piece = gm.pieceAt(tappedPos)
            if (piece != null && piece.color == gm.currentTurn) {
                selectedPos = tappedPos
                legalTargets = gm.getLegalMoves(tappedPos).map { it.to }
            } else {
                selectedPos = null
                legalTargets = emptyList()
            }
        }
        invalidate()
        return true
    }

    fun clearSelection() {
        selectedPos = null
        legalTargets = emptyList()
        invalidate()
    }

    fun setLastMove(from: Pos?, to: Pos?, animate: Boolean = true) {
        lastMoveFrom = from
        lastMoveTo = to
        
        if (animate && from != null && to != null) {
            val piece = game?.pieceAt(to)
            if (piece != null) {
                animatingFrom = from
                animatingTo = to
                animatingPiece = piece
                animFraction = 0.0f
                
                animator?.cancel()
                animator = android.animation.ValueAnimator.ofFloat(0.0f, 1.0f).apply {
                    duration = 200
                    interpolator = android.view.animation.DecelerateInterpolator()
                    addUpdateListener { anim ->
                        animFraction = anim.animatedValue as Float
                        invalidate()
                    }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            animatingFrom = null
                            animatingTo = null
                            animatingPiece = null
                            animFraction = 1.0f
                            invalidate()
                        }
                    })
                    start()
                }
            }
        } else {
            animator?.cancel()
            animatingFrom = null
            animatingTo = null
            animatingPiece = null
            animFraction = 1.0f
        }
        invalidate()
    }

    private fun getSquareScreenRect(pos: Pos, sq: Float): RectF {
        val drawR = if (isFlipped) 7 - pos.row else pos.row
        val drawC = if (isFlipped) 7 - pos.col else pos.col
        val left = drawC * sq
        val top = drawR * sq
        return RectF(left, top, left + sq, top + sq)
    }

    private fun drawPieceAt(canvas: Canvas, piece: Piece, left: Float, top: Float, sq: Float) {
        val pieceSize = sq * 0.82f
        piecePaint.textSize = pieceSize
        pieceShadowPaint.textSize = pieceSize
        val pTheme = com.chessomania.app.SettingsManager.getPieceTheme(context)
        if (pTheme == "Unicode") {
            val sym = pieceSymbols[Pair(piece.type, piece.color)] ?: return
            val cx = left + sq / 2
            val cy = top + sq / 2 + pieceSize * 0.35f
            canvas.drawText(sym, cx + sq * 0.02f, cy + sq * 0.03f, pieceShadowPaint)
            piecePaint.color = if (piece.color == Color.WHITE) AndroidColor.WHITE else AndroidColor.parseColor("#1a1a1a")
            canvas.drawText(sym, cx, cy, piecePaint)
        } else {
            val bmp = getPieceBitmap(pTheme, piece.color, piece.type)
            if (bmp != null) {
                val margin = sq * 0.08f
                val pRect = RectF(left + margin, top + margin, left + sq - margin, top + sq - margin)
                canvas.drawBitmap(bmp, null, pRect, null)
            }
        }
    }

    fun showHintArrow(from: Pos, to: Pos) {
        hintArrowFrom = from
        hintArrowTo = to
        invalidate()
    }

    fun clearHintArrow() {
        hintArrowFrom = null
        hintArrowTo = null
        invalidate()
    }

    private fun drawArrow(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float, paint: Paint) {
        val dx = endX - startX
        val dy = endY - startY
        val distance = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance < 1) return

        val sq = width.toFloat() / 8f
        val shortenLen = sq * 0.2f
        val ratio = (distance - shortenLen) / distance
        val arrowEndX = startX + dx * ratio
        val arrowEndY = startY + dy * ratio

        canvas.drawLine(startX, startY, arrowEndX, arrowEndY, paint)

        val angle = Math.atan2(dy.toDouble(), dx.toDouble())
        val arrowHeadSize = sq * 0.25f

        val arrowHeadPath = android.graphics.Path()
        val apexX = endX - sq * 0.15f * Math.cos(angle).toFloat()
        val apexY = endY - sq * 0.15f * Math.sin(angle).toFloat()
        arrowHeadPath.moveTo(apexX, apexY)
        
        val leftWingX = (arrowEndX - arrowHeadSize * Math.cos(angle - Math.PI / 6).toFloat())
        val leftWingY = (arrowEndY - arrowHeadSize * Math.sin(angle - Math.PI / 6).toFloat())
        val rightWingX = (arrowEndX - arrowHeadSize * Math.cos(angle + Math.PI / 6).toFloat())
        val rightWingY = (arrowEndY - arrowHeadSize * Math.sin(angle + Math.PI / 6).toFloat())

        arrowHeadPath.lineTo(leftWingX, leftWingY)
        arrowHeadPath.lineTo(rightWingX, rightWingY)
        arrowHeadPath.close()

        val fillPaint = Paint(paint).apply {
            style = Paint.Style.FILL_AND_STROKE
        }
        canvas.drawPath(arrowHeadPath, fillPaint)
    }
}
