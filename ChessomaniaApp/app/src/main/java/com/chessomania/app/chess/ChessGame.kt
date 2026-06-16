package com.chessomania.app.chess

// ─── Piece Types & Colors ────────────────────────────────────────────────────
enum class PieceType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }
enum class Color { WHITE, BLACK;
    fun opposite() = if (this == WHITE) BLACK else WHITE
}

data class Piece(val type: PieceType, val color: Color)

// ─── Position & Move ─────────────────────────────────────────────────────────
data class Pos(val row: Int, val col: Int) {
    fun isValid() = row in 0..7 && col in 0..7
    operator fun plus(other: Pos) = Pos(row + other.row, col + other.col)
}

data class Move(
    val from: Pos,
    val to: Pos,
    val promotion: PieceType? = null,
    val isCastle: Boolean = false,
    val isEnPassant: Boolean = false,
    val capturedPiece: Piece? = null
)

// ─── Board State ─────────────────────────────────────────────────────────────
class ChessGame {
    val board = Array(8) { arrayOfNulls<Piece>(8) }
    var currentTurn = Color.WHITE
    var enPassantTarget: Pos? = null
    var whiteKingMoved = false
    var blackKingMoved = false
    var whiteRookAMoved = false  // a-file rook (col 0)
    var whiteRookHMoved = false  // h-file rook (col 7)
    var blackRookAMoved = false
    var blackRookHMoved = false
    var halfMoveClock = 0
    var fullMoveNumber = 1
    val moveHistory = mutableListOf<Move>()
    val sanHistory = mutableListOf<String>()
    var gameStatus = GameStatus.PLAYING
    var whiteKingPos = Pos(7, 4)
    var blackKingPos = Pos(0, 4)

    enum class GameStatus { PLAYING, CHECK, CHECKMATE, STALEMATE, DRAW }

    init { setupInitialPosition() }

    fun setupInitialPosition() {
        // Clear board
        for (r in 0..7) for (c in 0..7) board[r][c] = null

        // Black pieces (row 0)
        val backRank = listOf(PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
            PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK)
        for (c in 0..7) {
            board[0][c] = Piece(backRank[c], Color.BLACK)
            board[1][c] = Piece(PieceType.PAWN, Color.BLACK)
            board[6][c] = Piece(PieceType.PAWN, Color.WHITE)
            board[7][c] = Piece(backRank[c], Color.WHITE)
        }

        currentTurn = Color.WHITE
        enPassantTarget = null
        whiteKingMoved = false; blackKingMoved = false
        whiteRookAMoved = false; whiteRookHMoved = false
        blackRookAMoved = false; blackRookHMoved = false
        moveHistory.clear(); sanHistory.clear()
        gameStatus = GameStatus.PLAYING
        halfMoveClock = 0; fullMoveNumber = 1
        whiteKingPos = Pos(7, 4)
        blackKingPos = Pos(0, 4)
    }

    fun pieceAt(pos: Pos): Piece? = if (pos.isValid()) board[pos.row][pos.col] else null

    fun isSquareAttacked(pos: Pos, byColor: Color): Boolean {
        // Check pawn attacks
        val pawnDir = if (byColor == Color.WHITE) 1 else -1
        val pawnAttacks = listOf(Pos(pos.row + pawnDir, pos.col - 1), Pos(pos.row + pawnDir, pos.col + 1))
        for (pa in pawnAttacks) {
            if (pa.isValid()) {
                val p = pieceAt(pa)
                if (p?.type == PieceType.PAWN && p.color == byColor) return true
            }
        }
        // Knight attacks
        val knightMoves = listOf(Pos(-2,-1),Pos(-2,1),Pos(-1,-2),Pos(-1,2),
            Pos(1,-2),Pos(1,2),Pos(2,-1),Pos(2,1))
        for (km in knightMoves) {
            val np = Pos(pos.row + km.row, pos.col + km.col)
            if (np.isValid()) {
                val p = pieceAt(np)
                if (p?.type == PieceType.KNIGHT && p.color == byColor) return true
            }
        }
        // King attacks
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val np = Pos(pos.row + dr, pos.col + dc)
            if (np.isValid()) {
                val p = pieceAt(np)
                if (p?.type == PieceType.KING && p.color == byColor) return true
            }
        }
        // Sliding pieces (rook directions)
        val rookDirs = listOf(Pos(0,1),Pos(0,-1),Pos(1,0),Pos(-1,0))
        for (dir in rookDirs) {
            var np = Pos(pos.row + dir.row, pos.col + dir.col)
            while (np.isValid()) {
                val p = pieceAt(np)
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) return true
                    break
                }
                np = Pos(np.row + dir.row, np.col + dir.col)
            }
        }
        // Sliding pieces (bishop directions)
        val bishopDirs = listOf(Pos(1,1),Pos(1,-1),Pos(-1,1),Pos(-1,-1))
        for (dir in bishopDirs) {
            var np = Pos(pos.row + dir.row, pos.col + dir.col)
            while (np.isValid()) {
                val p = pieceAt(np)
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) return true
                    break
                }
                np = Pos(np.row + dir.row, np.col + dir.col)
            }
        }
        return false
    }

    fun findKing(color: Color): Pos? {
        return if (color == Color.WHITE) whiteKingPos else blackKingPos
    }

    fun updateKingPositionsFromBoard() {
        for (r in 0..7) for (c in 0..7) {
            val p = board[r][c]
            if (p?.type == PieceType.KING) {
                if (p.color == Color.WHITE) whiteKingPos = Pos(r, c)
                else blackKingPos = Pos(r, c)
            }
        }
    }

    fun isInCheck(color: Color): Boolean {
        val kingPos = findKing(color) ?: return false
        return isSquareAttacked(kingPos, color.opposite())
    }

    fun getPseudoLegalMoves(pos: Pos): List<Move> {
        val piece = pieceAt(pos) ?: return emptyList()
        val moves = mutableListOf<Move>()
        val color = piece.color

        fun addMove(to: Pos, promotion: PieceType? = null, isCastle: Boolean = false, isEP: Boolean = false) {
            val captured = if (isEP) {
                val epCaptureRow = if (color == Color.WHITE) to.row + 1 else to.row - 1
                pieceAt(Pos(epCaptureRow, to.col))
            } else pieceAt(to)
            moves.add(Move(pos, to, promotion, isCastle, isEP, captured))
        }

        fun slide(dirs: List<Pos>) {
            for (dir in dirs) {
                var np = pos + dir
                while (np.isValid()) {
                    val target = pieceAt(np)
                    if (target == null) { addMove(np) }
                    else { if (target.color != color) addMove(np); break }
                    np = np + dir
                }
            }
        }

        fun jump(offsets: List<Pos>) {
            for (off in offsets) {
                val np = pos + off
                if (np.isValid()) {
                    val target = pieceAt(np)
                    if (target == null || target.color != color) addMove(np)
                }
            }
        }

        when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (color == Color.WHITE) -1 else 1
                val startRow = if (color == Color.WHITE) 6 else 1
                val promoRow = if (color == Color.WHITE) 0 else 7
                val fwd = Pos(pos.row + dir, pos.col)
                if (fwd.isValid() && pieceAt(fwd) == null) {
                    if (fwd.row == promoRow) {
                        for (pt in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT))
                            addMove(fwd, pt)
                    } else {
                        addMove(fwd)
                        val fwd2 = Pos(pos.row + 2*dir, pos.col)
                        if (pos.row == startRow && pieceAt(fwd2) == null) addMove(fwd2)
                    }
                }
                // Captures
                for (dc in listOf(-1, 1)) {
                    val cap = Pos(pos.row + dir, pos.col + dc)
                    if (cap.isValid()) {
                        val target = pieceAt(cap)
                        if (target != null && target.color != color) {
                            if (cap.row == promoRow) {
                                for (pt in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT))
                                    addMove(cap, pt)
                            } else addMove(cap)
                        }
                        // En passant
                        if (enPassantTarget == cap) addMove(cap, null, false, true)
                    }
                }
            }
            PieceType.KNIGHT -> jump(listOf(Pos(-2,-1),Pos(-2,1),Pos(-1,-2),Pos(-1,2),
                Pos(1,-2),Pos(1,2),Pos(2,-1),Pos(2,1)))
            PieceType.BISHOP -> slide(listOf(Pos(1,1),Pos(1,-1),Pos(-1,1),Pos(-1,-1)))
            PieceType.ROOK -> slide(listOf(Pos(0,1),Pos(0,-1),Pos(1,0),Pos(-1,0)))
            PieceType.QUEEN -> slide(listOf(Pos(0,1),Pos(0,-1),Pos(1,0),Pos(-1,0),
                Pos(1,1),Pos(1,-1),Pos(-1,1),Pos(-1,-1)))
            PieceType.KING -> {
                jump(listOf(Pos(-1,-1),Pos(-1,0),Pos(-1,1),Pos(0,-1),
                    Pos(0,1),Pos(1,-1),Pos(1,0),Pos(1,1)))
                // Castling
                val row = if (color == Color.WHITE) 7 else 0
                val kingMoved = if (color == Color.WHITE) whiteKingMoved else blackKingMoved
                val rookAMoved = if (color == Color.WHITE) whiteRookAMoved else blackRookAMoved
                val rookHMoved = if (color == Color.WHITE) whiteRookHMoved else blackRookHMoved
                if (!kingMoved && !isInCheck(color)) {
                    // Kingside castling
                    if (!rookHMoved &&
                        board[row][5] == null && board[row][6] == null &&
                        !isSquareAttacked(Pos(row,5), color.opposite()) &&
                        !isSquareAttacked(Pos(row,6), color.opposite())) {
                        moves.add(Move(pos, Pos(row,6), null, true))
                    }
                    // Queenside castling
                    if (!rookAMoved &&
                        board[row][1] == null && board[row][2] == null && board[row][3] == null &&
                        !isSquareAttacked(Pos(row,3), color.opposite()) &&
                        !isSquareAttacked(Pos(row,2), color.opposite())) {
                        moves.add(Move(pos, Pos(row,2), null, true))
                    }
                }
            }
        }
        return moves
    }

    fun getLegalMoves(pos: Pos): List<Move> {
        return getPseudoLegalMoves(pos).filter { isLegal(it) }
    }

    fun getAllLegalMoves(color: Color): List<Move> {
        val result = mutableListOf<Move>()
        for (r in 0..7) for (c in 0..7) {
            val p = board[r][c]
            if (p?.color == color) result.addAll(getLegalMoves(Pos(r, c)))
        }
        return result
    }

    private fun isLegal(move: Move): Boolean {
        val movingColor = board[move.from.row][move.from.col]?.color ?: return false
        val saved = applyMoveInternal(move)
        val inCheck = isInCheck(movingColor)
        undoMoveInternal(move, saved)
        return !inCheck
    }

    // Internal move application for legal check (returns save state)
    private fun applyMoveInternal(move: Move): SavedState {
        val piece = board[move.from.row][move.from.col]!!
        val saved = SavedState(
            board[move.to.row][move.to.col],
            enPassantTarget, whiteKingMoved, blackKingMoved,
            whiteRookAMoved, whiteRookHMoved, blackRookAMoved, blackRookHMoved
        )
        board[move.to.row][move.to.col] = if (move.promotion != null) Piece(move.promotion, piece.color) else piece
        board[move.from.row][move.from.col] = null
        // En passant capture
        if (move.isEnPassant) {
            val epRow = if (piece.color == Color.WHITE) move.to.row + 1 else move.to.row - 1
            board[epRow][move.to.col] = null
        }
        // Castling
        if (move.isCastle) {
            val row = move.from.row
            if (move.to.col == 6) { // kingside
                board[row][5] = board[row][7]; board[row][7] = null
            } else { // queenside
                board[row][3] = board[row][0]; board[row][0] = null
            }
        }
        enPassantTarget = null
        if (piece.type == PieceType.PAWN && Math.abs(move.to.row - move.from.row) == 2)
            enPassantTarget = Pos((move.from.row + move.to.row) / 2, move.from.col)
        if (piece.type == PieceType.KING) {
            if (piece.color == Color.WHITE) whiteKingPos = move.to else blackKingPos = move.to
        }
        return saved
    }

    private fun undoMoveInternal(move: Move, saved: SavedState) {
        val piece = board[move.to.row][move.to.col]!!
        board[move.from.row][move.from.col] = if (move.promotion != null) Piece(PieceType.PAWN, piece.color) else piece
        board[move.to.row][move.to.col] = saved.capturedPiece
        if (move.isEnPassant) {
            val epRow = if (piece.color == Color.WHITE) move.to.row + 1 else move.to.row - 1
            board[epRow][move.to.col] = Piece(PieceType.PAWN, piece.color.opposite())
        }
        if (move.isCastle) {
            val row = move.from.row
            if (move.to.col == 6) { board[row][7] = board[row][5]; board[row][5] = null }
            else { board[row][0] = board[row][3]; board[row][3] = null }
        }
        if (piece.type == PieceType.KING) {
            if (piece.color == Color.WHITE) whiteKingPos = move.from else blackKingPos = move.from
        }
        enPassantTarget = saved.enPassantTarget
        whiteKingMoved = saved.whiteKingMoved; blackKingMoved = saved.blackKingMoved
        whiteRookAMoved = saved.whiteRookAMoved; whiteRookHMoved = saved.whiteRookHMoved
        blackRookAMoved = saved.blackRookAMoved; blackRookHMoved = saved.blackRookHMoved
    }

    data class SavedState(
        val capturedPiece: Piece?,
        val enPassantTarget: Pos?,
        val whiteKingMoved: Boolean, val blackKingMoved: Boolean,
        val whiteRookAMoved: Boolean, val whiteRookHMoved: Boolean,
        val blackRookAMoved: Boolean, val blackRookHMoved: Boolean
    )

    fun applyMove(move: Move, updateStatus: Boolean = true) {
        val piece = board[move.from.row][move.from.col] ?: return
        val san = moveToSan(move)
        applyMoveInternal(move)
        // Update moved flags
        if (piece.type == PieceType.KING) {
            if (piece.color == Color.WHITE) whiteKingMoved = true else blackKingMoved = true
        }
        if (piece.type == PieceType.ROOK) {
            if (piece.color == Color.WHITE) {
                if (move.from.col == 0) whiteRookAMoved = true
                if (move.from.col == 7) whiteRookHMoved = true
            } else {
                if (move.from.col == 0) blackRookAMoved = true
                if (move.from.col == 7) blackRookHMoved = true
            }
        }
        if (piece.type == PieceType.PAWN || move.capturedPiece != null) halfMoveClock = 0 else halfMoveClock++
        if (currentTurn == Color.BLACK) fullMoveNumber++
        currentTurn = currentTurn.opposite()
        moveHistory.add(move)
        sanHistory.add(san)
        if (updateStatus) updateGameStatus()
    }

    fun undoLastMove(updateStatus: Boolean = true) {
        if (moveHistory.isEmpty()) return
        val lastMove = moveHistory.removeLast()
        sanHistory.removeLastOrNull()
        currentTurn = currentTurn.opposite()
        if (currentTurn == Color.WHITE) fullMoveNumber--
        // Rebuild from scratch is safest for undo
        val piece = board[lastMove.to.row][lastMove.to.col]!!
        board[lastMove.from.row][lastMove.from.col] = if (lastMove.promotion != null) Piece(PieceType.PAWN, piece.color) else piece
        board[lastMove.to.row][lastMove.to.col] = lastMove.capturedPiece
        if (lastMove.isEnPassant) {
            val epRow = if (piece.color == Color.WHITE) lastMove.to.row + 1 else lastMove.to.row - 1
            board[epRow][lastMove.to.col] = Piece(PieceType.PAWN, piece.color.opposite())
        }
        if (lastMove.isCastle) {
            val row = lastMove.from.row
            if (lastMove.to.col == 6) { board[row][7] = board[row][5]; board[row][5] = null }
            else { board[row][0] = board[row][3]; board[row][3] = null }
        }
        if (piece.type == PieceType.KING) {
            if (piece.color == Color.WHITE) whiteKingPos = lastMove.from else blackKingPos = lastMove.from
        }
        // Restore en passant from previous move
        enPassantTarget = if (moveHistory.isNotEmpty()) {
            val prev = moveHistory.last()
            val prevPiece = board[prev.to.row][prev.to.col]
            if (prevPiece?.type == PieceType.PAWN && Math.abs(prev.to.row - prev.from.row) == 2)
                Pos((prev.from.row + prev.to.row) / 2, prev.from.col) else null
        } else null
        gameStatus = GameStatus.PLAYING
        if (updateStatus) updateGameStatus()
    }

    fun clone(): ChessGame {
        val newGame = ChessGame()
        for (r in 0..7) {
            for (c in 0..7) {
                newGame.board[r][c] = this.board[r][c]
            }
        }
        newGame.currentTurn = this.currentTurn
        newGame.enPassantTarget = this.enPassantTarget
        newGame.whiteKingMoved = this.whiteKingMoved
        newGame.blackKingMoved = this.blackKingMoved
        newGame.whiteRookAMoved = this.whiteRookAMoved
        newGame.whiteRookHMoved = this.whiteRookHMoved
        newGame.blackRookAMoved = this.blackRookAMoved
        newGame.blackRookHMoved = this.blackRookHMoved
        newGame.halfMoveClock = this.halfMoveClock
        newGame.fullMoveNumber = this.fullMoveNumber
        newGame.moveHistory.clear()
        newGame.moveHistory.addAll(this.moveHistory)
        newGame.sanHistory.clear()
        newGame.sanHistory.addAll(this.sanHistory)
        newGame.gameStatus = this.gameStatus
        newGame.whiteKingPos = this.whiteKingPos
        newGame.blackKingPos = this.blackKingPos
        return newGame
    }

    private fun updateGameStatus() {
        val legalMoves = getAllLegalMoves(currentTurn)
        val inCheck = isInCheck(currentTurn)
        gameStatus = when {
            legalMoves.isEmpty() && inCheck -> GameStatus.CHECKMATE
            legalMoves.isEmpty() -> GameStatus.STALEMATE
            halfMoveClock >= 100 -> GameStatus.DRAW
            inCheck -> GameStatus.CHECK
            else -> GameStatus.PLAYING
        }
    }

    private fun moveToSan(move: Move): String {
        val piece = board[move.from.row][move.from.col] ?: return ""
        val cols = "abcdefgh"
        val rows = "87654321"
        val toStr = "${cols[move.to.col]}${rows[move.to.row]}"
        val pieceChar = when(piece.type) {
            PieceType.KING -> "K"; PieceType.QUEEN -> "Q"; PieceType.ROOK -> "R"
            PieceType.BISHOP -> "B"; PieceType.KNIGHT -> "N"; PieceType.PAWN -> ""
        }
        val captureStr = if (move.capturedPiece != null || move.isEnPassant) {
            if (piece.type == PieceType.PAWN) "${cols[move.from.col]}x" else "x"
        } else ""
        val promoStr = if (move.promotion != null) "=${when(move.promotion){
            PieceType.QUEEN->"Q"; PieceType.ROOK->"R"; PieceType.BISHOP->"B"; else->"N"
        }}" else ""
        return if (move.isCastle) {
            if (move.to.col == 6) "O-O" else "O-O-O"
        } else "$pieceChar$captureStr$toStr$promoStr"
    }

    fun getCapturedPieces(color: Color): String {
        val initialCounts = mapOf(
            PieceType.PAWN to 8, PieceType.KNIGHT to 2, PieceType.BISHOP to 2,
            PieceType.ROOK to 2, PieceType.QUEEN to 1, PieceType.KING to 1
        )
        val currentCounts = mutableMapOf<PieceType, Int>()
        for (r in 0..7) for (c in 0..7) {
            val p = board[r][c]
            if (p?.color == color) currentCounts[p.type] = (currentCounts[p.type] ?: 0) + 1
        }
        val symbols = mapOf(PieceType.PAWN to "♟", PieceType.KNIGHT to "♞",
            PieceType.BISHOP to "♝", PieceType.ROOK to "♜", PieceType.QUEEN to "♛")
        return buildString {
            for ((type, sym) in symbols) {
                val captured = (initialCounts[type] ?: 0) - (currentCounts[type] ?: 0)
                if (captured > 0) repeat(captured) { append(sym) }
            }
        }
    }
}
