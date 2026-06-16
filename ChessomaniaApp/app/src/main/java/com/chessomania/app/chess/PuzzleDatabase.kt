package com.chessomania.app.chess

// ─── Built-in Chess Puzzles ───────────────────────────────────────────────────
data class Puzzle(
    val id: Int,
    val fen: String,           // Position in FEN
    val solutionMoves: List<String>,  // Moves in UCI format (e.g., "e2e4")
    val theme: String,
    val description: String,
    val playerColor: Color
)

object PuzzleDatabase {

    // Helper: Parse FEN into board state
    fun loadPuzzleFen(game: ChessGame, fen: String) {
        val parts = fen.split(" ")
        val ranks = parts[0].split("/")
        for (r in 0..7) for (c in 0..7) game.board[r][c] = null
        for (r in 0..7) {
            var c = 0
            for (ch in ranks[r]) {
                if (ch.isDigit()) { c += ch.digitToInt() }
                else {
                    val color = if (ch.isUpperCase()) Color.WHITE else Color.BLACK
                    val type = when (ch.lowercaseChar()) {
                        'k' -> PieceType.KING; 'q' -> PieceType.QUEEN; 'r' -> PieceType.ROOK
                        'b' -> PieceType.BISHOP; 'n' -> PieceType.KNIGHT; else -> PieceType.PAWN
                    }
                    game.board[r][c] = Piece(type, color)
                    c++
                }
            }
        }
        game.currentTurn = if (parts.size > 1 && parts[1] == "b") Color.BLACK else Color.WHITE
        game.enPassantTarget = null
        if (parts.size > 3 && parts[3] != "-") {
            val epStr = parts[3]
            val epCol = epStr[0] - 'a'
            val epRow = 8 - epStr[1].digitToInt()
            game.enPassantTarget = Pos(epRow, epCol)
        }
        game.moveHistory.clear(); game.sanHistory.clear()
        game.whiteKingMoved = false; game.blackKingMoved = false
        game.whiteRookAMoved = false; game.whiteRookHMoved = false
        game.blackRookAMoved = false; game.blackRookHMoved = false
        if (parts.size > 2) {
            val castling = parts[2]
            if (!castling.contains('K')) game.whiteRookHMoved = true
            if (!castling.contains('Q')) game.whiteRookAMoved = true
            if (!castling.contains('k')) game.blackRookHMoved = true
            if (!castling.contains('q')) game.blackRookAMoved = true
            if (!castling.contains('K') && !castling.contains('Q')) game.whiteKingMoved = true
            if (!castling.contains('k') && !castling.contains('q')) game.blackKingMoved = true
        }
        game.gameStatus = ChessGame.GameStatus.PLAYING
        game.updateKingPositionsFromBoard()
    }

    fun uciToMove(uci: String, game: ChessGame): Move? {
        if (uci.length < 4) return null
        val cols = "abcdefgh"
        val fromCol = cols.indexOf(uci[0])
        val fromRow = 8 - uci[1].digitToInt()
        val toCol = cols.indexOf(uci[2])
        val toRow = 8 - uci[3].digitToInt()
        if (fromCol < 0 || toCol < 0) return null
        val from = Pos(fromRow, fromCol)
        val to = Pos(toRow, toCol)
        val promotion = if (uci.length >= 5) when(uci[4]) {
            'q' -> PieceType.QUEEN; 'r' -> PieceType.ROOK; 'b' -> PieceType.BISHOP; 'n' -> PieceType.KNIGHT; else -> null
        } else null
        val legal = game.getLegalMoves(from)
        return legal.find { it.to == to && it.promotion == promotion }
            ?: legal.find { it.to == to && promotion == null }
    }

    val puzzles = listOf(
        // 1. Fork
        Puzzle(1, "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
            listOf("f3g5", "d8e7", "g5f7"), "Fork", "Knight fork — attack king and rook!", Color.WHITE),
        // 2. Mate in 1
        Puzzle(2, "r1bqkbnr/pppp1ppp/8/4p3/2BnP3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 4",
            listOf("c4f7"), "Mate in 1", "Scholar's mate threat!", Color.WHITE),
        // 3. Pin and win
        Puzzle(3, "rnb1kbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 3",
            listOf("d1h5"), "Pin", "Attack the f7 weakness!", Color.WHITE),
        // 4. Skewer
        Puzzle(4, "4k3/8/8/8/8/8/8/4K2R w - - 0 1",
            listOf("h1h8"), "Checkmate", "Rook to 8th rank — checkmate!", Color.WHITE),
        // 5. Discovered check
        Puzzle(5, "r3k2r/ppp2ppp/2n1bn2/2b1p1q1/2B1P3/2NP1N2/PPP1QPPP/R1B1K2R w KQkq - 0 1",
            listOf("c3d5"), "Discovered Attack", "Knight move discovers attack!", Color.WHITE),
        // 6. Back rank mate
        Puzzle(6, "6k1/5ppp/8/8/8/8/8/3R2K1 w - - 0 1",
            listOf("d1d8"), "Back Rank", "Rook to 8th for checkmate!", Color.WHITE),
        // 7. Promotion
        Puzzle(7, "8/P7/8/8/8/8/8/K5k1 w - - 0 1",
            listOf("a7a8q"), "Promotion", "Promote the pawn to queen!", Color.WHITE),
        // 8. Double check
        Puzzle(8, "r1bqk2r/pppp1ppp/2n2n2/2b5/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 0 1",
            listOf("e4e5", "f6e4", "c3e4"), "Double Check", "Attack the center and fork!", Color.WHITE),
        // 9. Smothered mate
        Puzzle(9, "6k1/6pp/8/8/8/8/6PP/3R2K1 w - - 0 1",
            listOf("d1d8"), "Checkmate", "Rook delivers checkmate!", Color.WHITE),
        // 10. Zwischenzug
        Puzzle(10, "r1b1k2r/pppp1ppp/2n2n2/2b1p1q1/4P3/2NP1N2/PPP2PPP/R1BQKB1R w KQkq - 0 1",
            listOf("f3e5", "g5d2", "e1d2"), "Zwischenzug", "Intermediate move — take the knight!", Color.WHITE)
    )
}
