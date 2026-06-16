package com.chessomania.app.chess

// ─── Minimax AI with Alpha-Beta Pruning ──────────────────────────────────────
class ChessAI {

    // Piece values (centipawns)
    private val pieceValue = mapOf(
        PieceType.PAWN to 100,
        PieceType.KNIGHT to 320,
        PieceType.BISHOP to 330,
        PieceType.ROOK to 500,
        PieceType.QUEEN to 900,
        PieceType.KING to 20000
    )

    // Piece-square tables (from white's perspective, row 0 = rank 8)
    private val pawnTable = arrayOf(
        intArrayOf(0,0,0,0,0,0,0,0),
        intArrayOf(50,50,50,50,50,50,50,50),
        intArrayOf(10,10,20,30,30,20,10,10),
        intArrayOf(5,5,10,25,25,10,5,5),
        intArrayOf(0,0,0,20,20,0,0,0),
        intArrayOf(5,-5,-10,0,0,-10,-5,5),
        intArrayOf(5,10,10,-20,-20,10,10,5),
        intArrayOf(0,0,0,0,0,0,0,0)
    )
    private val knightTable = arrayOf(
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50),
        intArrayOf(-40,-20,0,0,0,0,-20,-40),
        intArrayOf(-30,0,10,15,15,10,0,-30),
        intArrayOf(-30,5,15,20,20,15,5,-30),
        intArrayOf(-30,0,15,20,20,15,0,-30),
        intArrayOf(-30,5,10,15,15,10,5,-30),
        intArrayOf(-40,-20,0,5,5,0,-20,-40),
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50)
    )
    private val bishopTable = arrayOf(
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20),
        intArrayOf(-10,0,0,0,0,0,0,-10),
        intArrayOf(-10,0,5,10,10,5,0,-10),
        intArrayOf(-10,5,5,10,10,5,5,-10),
        intArrayOf(-10,0,10,10,10,10,0,-10),
        intArrayOf(-10,10,10,10,10,10,10,-10),
        intArrayOf(-10,5,0,0,0,0,5,-10),
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20)
    )
    private val rookTable = arrayOf(
        intArrayOf(0,0,0,0,0,0,0,0),
        intArrayOf(5,10,10,10,10,10,10,5),
        intArrayOf(-5,0,0,0,0,0,0,-5),
        intArrayOf(-5,0,0,0,0,0,0,-5),
        intArrayOf(-5,0,0,0,0,0,0,-5),
        intArrayOf(-5,0,0,0,0,0,0,-5),
        intArrayOf(-5,0,0,0,0,0,0,-5),
        intArrayOf(0,0,0,5,5,0,0,0)
    )
    private val queenTable = arrayOf(
        intArrayOf(-20,-10,-10,-5,-5,-10,-10,-20),
        intArrayOf(-10,0,0,0,0,0,0,-10),
        intArrayOf(-10,0,5,5,5,5,0,-10),
        intArrayOf(-5,0,5,5,5,5,0,-5),
        intArrayOf(0,0,5,5,5,5,0,-5),
        intArrayOf(-10,5,5,5,5,5,0,-10),
        intArrayOf(-10,0,5,0,0,0,0,-10),
        intArrayOf(-20,-10,-10,-5,-5,-10,-10,-20)
    )
    private val kingMidgameTable = arrayOf(
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-20,-30,-30,-40,-40,-30,-30,-20),
        intArrayOf(-10,-20,-20,-20,-20,-20,-20,-10),
        intArrayOf(20,20,0,0,0,0,20,20),
        intArrayOf(20,30,10,0,0,10,30,20)
    )

    fun evaluateBoard(game: ChessGame): Int {
        var score = 0
        for (r in 0..7) for (c in 0..7) {
            val piece = game.board[r][c] ?: continue
            val pv = pieceValue[piece.type] ?: 0
            val tableBonus = when (piece.type) {
                PieceType.PAWN -> pawnTable[if (piece.color == Color.WHITE) r else 7 - r][if (piece.color == Color.WHITE) c else 7 - c]
                PieceType.KNIGHT -> knightTable[if (piece.color == Color.WHITE) r else 7 - r][if (piece.color == Color.WHITE) c else 7 - c]
                PieceType.BISHOP -> bishopTable[if (piece.color == Color.WHITE) r else 7 - r][if (piece.color == Color.WHITE) c else 7 - c]
                PieceType.ROOK -> rookTable[if (piece.color == Color.WHITE) r else 7 - r][if (piece.color == Color.WHITE) c else 7 - c]
                PieceType.QUEEN -> queenTable[if (piece.color == Color.WHITE) r else 7 - r][if (piece.color == Color.WHITE) c else 7 - c]
                PieceType.KING -> kingMidgameTable[if (piece.color == Color.WHITE) r else 7 - r][if (piece.color == Color.WHITE) c else 7 - c]
            }
            val value = pv + tableBonus
            score += if (piece.color == Color.WHITE) value else -value
        }
        return score
    }

    fun minimax(game: ChessGame, depth: Int, alpha: Int, beta: Int, isMaximizing: Boolean): Int {
        if (depth == 0) return evaluateBoard(game)

        val color = if (isMaximizing) Color.WHITE else Color.BLACK
        val moves = game.getAllLegalMoves(color)

        if (moves.isEmpty()) {
            val inCheck = game.isInCheck(color)
            return if (inCheck) {
                // Checkmate
                if (isMaximizing) -100000 + (10 - depth) * 1000 else 100000 - (10 - depth) * 1000
            } else {
                // Stalemate
                0
            }
        }
        if (game.halfMoveClock >= 100) return 0

        val sortedMoves = orderMoves(moves, game)

        var alphaV = alpha
        var betaV = beta

        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE
            for (move in sortedMoves) {
                game.applyMove(move, false)
                val eval = minimax(game, depth - 1, alphaV, betaV, false)
                game.undoLastMove(false)
                if (eval > maxEval) maxEval = eval
                if (eval > alphaV) alphaV = eval
                if (betaV <= alphaV) break
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            for (move in sortedMoves) {
                game.applyMove(move, false)
                val eval = minimax(game, depth - 1, alphaV, betaV, true)
                game.undoLastMove(false)
                if (eval < minEval) minEval = eval
                if (eval < betaV) betaV = eval
                if (betaV <= alphaV) break
            }
            return minEval
        }
    }

    private fun orderMoves(moves: List<Move>, game: ChessGame): List<Move> {
        return moves.sortedByDescending { move ->
            var score = 0
            val captured = move.capturedPiece
            if (captured != null) score += 10 * (pieceValue[captured.type] ?: 0)
            if (move.promotion != null) score += pieceValue[move.promotion] ?: 0
            if (move.isCastle) score += 50
            score
        }
    }

    fun getBestMove(game: ChessGame, depth: Int): Move? {
        val color = game.currentTurn
        val moves = game.getAllLegalMoves(color)
        if (moves.isEmpty()) return null

        val isMaximizing = color == Color.WHITE
        var bestMove: Move? = null
        var bestScore = if (isMaximizing) Int.MIN_VALUE else Int.MAX_VALUE

        for (move in orderMoves(moves, game)) {
            game.applyMove(move, false)
            val score = minimax(game, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE, !isMaximizing)
            game.undoLastMove(false)
            if (isMaximizing && score > bestScore) { bestScore = score; bestMove = move }
            if (!isMaximizing && score < bestScore) { bestScore = score; bestMove = move }
        }
        return bestMove ?: moves.first()
    }
}
