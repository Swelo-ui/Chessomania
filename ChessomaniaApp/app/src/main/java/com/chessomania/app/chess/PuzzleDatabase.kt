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
        Puzzle(1, "r3k3/8/8/3N4/8/8/8/4K3 w kq - 0 1", listOf("d5c7", "e8d7", "c7a8"), "Fork", "Level 1: Knight fork - check the king and win the rook!", Color.WHITE),
        Puzzle(2, "2r3k1/8/8/3B4/8/8/8/4K3 w - - 0 1", listOf("d5e6", "g8f8", "e6c8"), "Fork", "Level 2: Bishop fork - check the king and win the rook!", Color.WHITE),
        Puzzle(3, "2r3k1/8/8/8/8/8/8/3QK3 w - - 0 1", listOf("d1g4", "g8f8", "g4c8"), "Fork", "Level 3: Queen fork - check the king and win the rook!", Color.WHITE),
        Puzzle(4, "4k3/8/2n1n3/8/3P4/8/8/4K3 w - - 0 1", listOf("d4d5", "c6d8", "d5e6"), "Fork", "Level 4: Pawn fork - advance the pawn to attack two knights!", Color.WHITE),
        Puzzle(5, "3k4/8/2b5/8/8/8/8/3R2K1 w - - 0 1", listOf("d1d6", "d8e7", "d6c6"), "Fork", "Level 5: Rook fork - check the king and win the bishop!", Color.WHITE),
        Puzzle(6, "4k3/8/q7/3N4/8/8/8/4K3 w - - 0 1", listOf("d5c7", "e8d7", "c7a6"), "Fork", "Level 6: Knight fork - check the king and win the queen!", Color.WHITE),
        Puzzle(7, "6k1/8/8/3B4/8/r7/8/4K3 w - - 0 1", listOf("d5e6", "g8f8", "e6a2"), "Fork", "Level 7: Bishop fork - check the king and win the rook!", Color.WHITE),
        Puzzle(8, "6k1/8/8/3n4/8/8/5Q2/4K3 w - - 0 1", listOf("f2g2", "g8f8", "g2d5"), "Fork", "Level 8: Queen fork - check the king and win the knight!", Color.WHITE),
        Puzzle(9, "r3k2r/8/8/3N4/8/8/8/4K3 w kq - 0 1", listOf("d5c7", "e8d7", "c7a8"), "Fork", "Level 9: Double knight fork - win a rook in the opening!", Color.WHITE),
        Puzzle(10, "r1b1k2r/ppp2ppp/2np1n2/4p3/1b2P3/2NP1N2/PPPB1PPP/R3KB1R w KQkq - 0 7", listOf("c3d5", "f6d5", "e4d5"), "Fork", "Level 10: Grandmaster pawn fork combination after central trades!", Color.WHITE),
        Puzzle(11, "r1bqk2r/ppp2ppp/2np1n2/1B2p3/4P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("f3e5", "e8g8", "e5c6"), "Pin", "Level 1: Exploiting pinned c6 knight to win the e5 pawn!", Color.WHITE),
        Puzzle(12, "rnbqk2r/pppp1ppp/4pn2/8/1bPP4/2N5/PP2PPPP/R1BQKBNR w KQkq - 2 4", listOf("d1c2"), "Pin", "Level 2: Defend the pinned c3 knight with the queen!", Color.WHITE),
        Puzzle(13, "3rkb1r/pp3ppp/2n1pn2/1B1p4/3P4/2N2N2/PPP2PPP/R3K2R w KQq - 0 9", listOf("f3e5", "d8c8"), "Pin", "Level 3: Absolute pin - pile up on the pinned c6 knight!", Color.WHITE),
        Puzzle(14, "r1bqk2r/ppp2ppp/2np1n2/1b2p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("c4b5"), "Pin", "Level 4: Capture the pinning bishop to free your pieces!", Color.WHITE),
        Puzzle(15, "r1bqk2r/ppp2ppp/2np1n2/1B2p3/3PP3/2N2N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("d4d5", "a7a6", "d5c6"), "Pin", "Level 5: Exploit the pin on c6 by pushing the d-pawn!", Color.WHITE),
        Puzzle(16, "4k3/8/4q3/8/8/8/3R4/5K2 w - - 0 1", listOf("d2e2", "e6e2", "f1e2"), "Pin", "Level 6: Pin the opponent's queen to the king and trade!", Color.WHITE),
        Puzzle(17, "4k3/3r4/8/8/8/1B6/8/4K3 w - - 0 1", listOf("b3a4", "e8e7", "a4d7"), "Pin", "Level 7: Pin the rook to the king with the bishop and win it!", Color.WHITE),
        Puzzle(18, "r1bqk2r/ppp2ppp/2n2n2/1B1pp3/1b2P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("f3e5", "e8g8", "b5c6"), "Pin", "Level 8: Master positional pin sequence in the center!", Color.WHITE),
        Puzzle(19, "r1bqk2r/ppp2ppp/2np1n2/1b2p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("e1g1"), "Pin", "Level 9: Castling to safely escape the relative pin on c3!", Color.WHITE),
        Puzzle(20, "r1bqk2r/ppp2ppp/2n2n2/1B1pp3/1b2P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("f3e5", "e8g8", "b5c6"), "Pin", "Level 10: Grandmaster absolute pin sequence winning material!", Color.WHITE),
        Puzzle(21, "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", listOf("a1a8"), "Back Rank", "Level 1: Direct back rank checkmate with the rook!", Color.WHITE),
        Puzzle(22, "6k1/5ppp/8/8/8/8/8/3R2K1 w - - 0 1", listOf("d1d8"), "Back Rank", "Level 2: Back rank mate on the d-file!", Color.WHITE),
        Puzzle(23, "5rk1/5ppp/8/8/8/8/3R1PPP/3R2K1 w - - 0 1", listOf("d2d8", "f8d8", "d1d8"), "Back Rank", "Level 3: Double rook invasion to force back rank checkmate!", Color.WHITE),
        Puzzle(24, "5rk1/5ppp/8/8/8/8/3Q1PPP/3R2K1 w - - 0 1", listOf("d2d8", "f8d8", "d1d8"), "Back Rank", "Level 4: Queen and rook combo to force back rank mate!", Color.WHITE),
        Puzzle(25, "r5k1/5ppp/8/8/8/8/1R3PPP/1R4K1 w - - 0 1", listOf("b2b8", "a8b8", "b1b8"), "Back Rank", "Level 5: Rook sacrifice/deflection to win back rank mate!", Color.WHITE),
        Puzzle(26, "6k1/5ppp/8/8/8/3B4/5PPP/r5K1 w - - 0 1", listOf("d3f1"), "Back Rank", "Level 6: Block the back rank check with the bishop!", Color.WHITE),
        Puzzle(27, "5qk1/5ppp/8/8/8/8/1R3PPP/1R4K1 w - - 0 1", listOf("b2b8", "f8b8", "b1b8"), "Back Rank", "Level 7: Queen deflection rook sacrifice back rank mate!", Color.WHITE),
        Puzzle(28, "6k1/5rpp/8/8/8/8/R4PPP/6K1 w - - 0 1", listOf("a2a8", "f7f8", "a8f8"), "Back Rank", "Level 8: Infiltrating rook back rank checkmate!", Color.WHITE),
        Puzzle(29, "6k1/5ppp/8/8/8/8/1Q3PPP/6K1 w - - 0 1", listOf("b2b8"), "Back Rank", "Level 9: Safe back rank queen checkmate!", Color.WHITE),
        Puzzle(30, "r5k1/5ppp/8/8/8/8/2R2PPP/2R3K1 w - - 0 1", listOf("c2c8", "a8c8", "c1c8"), "Back Rank", "Level 10: Grandmaster double rook stack back rank mate!", Color.WHITE),
        Puzzle(31, "r3k3/8/8/8/8/8/8/4K2R w Kq - 0 1", listOf("h1h8", "e8d7", "h8a8"), "Skewer", "Level 1: Rook skewer - check king and win rook!", Color.WHITE),
        Puzzle(32, "4k3/8/8/8/r7/8/8/4K2B w - - 0 1", listOf("h1c6", "e8d8", "c6a4"), "Skewer", "Level 2: Bishop skewer - check king and win rook!", Color.WHITE),
        Puzzle(33, "q3k3/8/8/8/8/8/8/4K2R w K - 0 1", listOf("h1h8", "e8d7", "h8a8"), "Skewer", "Level 3: Queen skewer - check king and win queen!", Color.WHITE),
        Puzzle(34, "4K2R/8/8/8/r7/8/8/4k3 b - - 0 1", listOf("a4a8", "e8d7", "a8h8"), "Skewer", "Level 4: Black skewer - check white king and win rook!", Color.BLACK),
        Puzzle(35, "b1k5/8/8/8/8/8/8/3K3R w - - 0 1", listOf("h1h8", "c8d7", "h8a8"), "Skewer", "Level 5: Rook skewer - check king and win bishop!", Color.WHITE),
        Puzzle(36, "q3k3/8/8/8/8/8/8/3K3R w - - 0 1", listOf("h1h8", "e8d7", "h8a8"), "Skewer", "Level 6: Queen skewer on the diagonal!", Color.WHITE),
        Puzzle(37, "r3k3/8/8/8/8/8/8/3K3B w - - 0 1", listOf("h1a8"), "Skewer", "Level 7: Bishop skewer - capture the rook on a8!", Color.WHITE),
        Puzzle(38, "q3k3/8/8/8/8/8/8/3K3B w - - 0 1", listOf("h1a8"), "Skewer", "Level 8: Bishop skewer - capture the queen on a8!", Color.WHITE),
        Puzzle(39, "r3k3/8/8/8/8/8/8/3K3Q w - - 0 1", listOf("h1a8"), "Skewer", "Level 9: Queen skewer - capture the rook on a8!", Color.WHITE),
        Puzzle(40, "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", listOf("h1h8", "e8d7", "h8a8"), "Skewer", "Level 10: Grandmaster double skewer winning a full rook!", Color.WHITE),
        Puzzle(41, "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4", listOf("c4f7", "e8f7", "f3e5"), "Sacrifice", "Level 1: Bishop sacrifice to draw out the king!", Color.WHITE),
        Puzzle(42, "5r1k/5Qpp/7N/8/8/8/8/6RK w - - 0 1", listOf("f7g8", "f8g8", "h6f7"), "Sacrifice", "Level 2: Queen sacrifice to force smothered checkmate!", Color.WHITE),
        Puzzle(43, "3r2k1/5ppp/8/8/8/8/r4PPP/R1R3K1 w - - 0 1", listOf("c1c8", "d8c8", "a1a2"), "Sacrifice", "Level 3: Rook deflection sacrifice to win back rank control!", Color.WHITE),
        Puzzle(44, "r1bq1rk1/ppp1bppp/2n1pn2/3p4/3P4/2NBPN2/PPP2PPP/R1BQK2R w KQ - 4 7", listOf("d3h7", "g8h7", "f3g5"), "Sacrifice", "Level 4: Greek gift bishop sacrifice on h7!", Color.WHITE),
        Puzzle(45, "6rk/5ppp/8/8/8/8/1Q3PPP/1R4K1 w - - 0 1", listOf("b2b8", "g8b8", "b1b8"), "Sacrifice", "Level 5: Queen sacrifice to clear the back rank!", Color.WHITE),
        Puzzle(46, "r5k1/5ppp/8/8/8/8/1R3PPP/1R4K1 w - - 0 1", listOf("b2b8", "a8b8", "b1b8"), "Sacrifice", "Level 6: Rook sacrifice to force back rank mate!", Color.WHITE),
        Puzzle(47, "r1bqk1nr/pppp1ppp/2n5/4p3/1bB1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 5 4", listOf("c4f7", "e8f7", "f3e5", "c6e5"), "Sacrifice", "Level 7: Complex center knight sacrifice trade!", Color.WHITE),
        Puzzle(48, "2r3k1/5ppp/Q7/8/8/8/5PPP/6K1 w - - 0 1", listOf("a6c8"), "Sacrifice", "Level 8: Queen sacrifice to deliver back rank checkmate!", Color.WHITE),
        Puzzle(49, "6rk/5ppp/8/8/8/8/2R2PPP/2R3K1 w - - 0 1", listOf("c2c8", "g8c8", "c1c8"), "Sacrifice", "Level 9: Double rook sacrifice checking line!", Color.WHITE),
        Puzzle(50, "r1b2r1k/pp4pp/2nb4/3Q1pN1/2B1p3/8/PPP2PPP/R1B1K2R w KQ - 2 13", listOf("d5g8", "f8g8", "g5f7"), "Sacrifice", "Level 10: Grandmaster queen sacrifice smothered mate combination!", Color.WHITE),
        Puzzle(51, "rnb1kbnr/pppp1ppp/8/4p1q1/2B1P3/3P4/PPP2PPP/RNBQK1NR w KQkq - 1 4", listOf("d3d4"), "Discovered Attack", "Level 1: Discover bishop attack on the queen!", Color.WHITE),
        Puzzle(52, "1k6/7q/8/8/8/3B4/8/1R2K3 w - - 0 1", listOf("d3e4", "b8c7", "e4h7"), "Discovered Attack", "Level 2: Discover rook check and win the queen!", Color.WHITE),
        Puzzle(53, "1k6/7r/8/8/8/3B4/8/1R2K3 w - - 0 1", listOf("d3e4", "b8c7", "e4h7"), "Discovered Attack", "Level 3: Discover rook check and win the rook!", Color.WHITE),
        Puzzle(54, "1k6/7n/8/8/8/3B4/8/1R2K3 w - - 0 1", listOf("d3e4", "b8c7", "e4h7"), "Discovered Attack", "Level 4: Discover rook check and win the knight!", Color.WHITE),
        Puzzle(55, "1k6/7b/8/8/8/3B4/8/1R2K3 w - - 0 1", listOf("d3e4", "b8c7", "e4h7"), "Discovered Attack", "Level 5: Discover rook check and win the bishop!", Color.WHITE),
        Puzzle(56, "r1b1k2r/ppp2ppp/2np1n2/4p3/1b2P3/2NP1N2/PPPB1PPP/R3KB1R w KQkq - 0 7", listOf("c3d5", "f6d5", "e4d5"), "Discovered Attack", "Level 6: Center knight leap discovering bishop attack!", Color.WHITE),
        Puzzle(57, "1k6/7r/8/8/8/3B4/8/1R2K3 w - - 0 1", listOf("d3e4", "b8c7", "e4h7"), "Discovered Attack", "Level 7: Discovered check combination on b-file!", Color.WHITE),
        Puzzle(58, "1k6/7q/8/8/8/3B4/8/1R2K3 w - - 0 1", listOf("d3e4", "b8c7", "e4h7"), "Discovered Attack", "Level 8: Complex queen capture discovered check!", Color.WHITE),
        Puzzle(59, "k7/P7/8/8/8/3B4/8/1R2K3 w - - 0 1", listOf("d3e4"), "Discovered Attack", "Level 9: Discovered checkmate on the long diagonal!", Color.WHITE),
        Puzzle(60, "r1b1k2r/ppppbppp/2n1pn2/8/q7/2NP1N2/PPP1QPPP/R3KB1R w KQkq - 6 7", listOf("c3a4"), "Discovered Attack", "Level 10: Grandmaster discovered queen capture!", Color.WHITE),
        Puzzle(61, "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4", listOf("f3f7"), "Mate in 1", "Level 1: Scholar's Mate - checkmate in one move!", Color.WHITE),
        Puzzle(62, "3k4/8/3K4/8/8/8/8/5R2 w - - 0 1", listOf("f1f8"), "Mate in 1", "Level 2: Rook back rank mate - King trapped!", Color.WHITE),
        Puzzle(63, "6k1/5ppp/8/8/8/8/6Q1/6K1 w - - 0 1", listOf("g2a8"), "Mate in 1", "Level 3: Queen back rank mate - game over!", Color.WHITE),
        Puzzle(64, "r1bqk1nr/pppp1ppp/2n5/4p3/2B1P3/3P1Q2/PPP2PPP/RNB1K1NR w KQkq - 1 4", listOf("f3f7"), "Mate in 1", "Level 4: Mate in one with queen on f7!", Color.WHITE),
        Puzzle(65, "rnbqkbnr/ppppp2p/5p2/6p1/4P3/3P4/PPP2PPP/RNBQKBNR w KQkq - 0 3", listOf("d1h5"), "Mate in 1", "Level 5: Fool's Mate - checkmate the weak king!", Color.WHITE),
        Puzzle(66, "k7/1P6/1K6/8/8/8/8/R7 w - - 0 1", listOf("a1a8"), "Mate in 1", "Level 6: Rook checkmate supported by pawn!", Color.WHITE),
        Puzzle(67, "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", listOf("a1a8"), "Mate in 1", "Level 7: Rook checkmate on the back rank!", Color.WHITE),
        Puzzle(68, "6k1/5ppp/R7/8/8/8/8/6K1 w - - 0 1", listOf("a6a8"), "Mate in 1", "Level 8: Trap the king behind pawns!", Color.WHITE),
        Puzzle(69, "6k1/2R2ppp/8/8/8/8/8/6K1 w - - 0 1", listOf("c7c8"), "Mate in 1", "Level 9: Slam checkmate down on c8!", Color.WHITE),
        Puzzle(70, "3r2k1/5ppp/8/8/8/8/8/3R2K1 w - - 0 1", listOf("d1d8"), "Mate in 1", "Level 10: Grandmaster-level checkmate in 1!", Color.WHITE),
        Puzzle(71, "3k4/8/3B4/8/8/8/8/3R2K1 w - - 0 1", listOf("d6c7", "d8e8", "d1d8"), "Double Check", "Level 1: Double check - bishop moves to c7!", Color.WHITE),
        Puzzle(72, "3k4/8/3B4/8/8/8/8/3R2K1 w - - 0 1", listOf("d6e7", "d8e8", "d1d8"), "Double Check", "Level 2: Double check - bishop moves to e7!", Color.WHITE),
        Puzzle(73, "1k6/8/8/8/8/8/1B6/1R2K3 w - - 0 1", listOf("b2e5", "b8c8", "b1b8"), "Double Check", "Level 3: Double check - discover and check with bishop!", Color.WHITE),
        Puzzle(74, "1k6/8/8/8/8/8/1B6/1R2K3 w - - 0 1", listOf("b2e5", "b8a8", "b1b8"), "Double Check", "Level 4: Double check on the cornered king!", Color.WHITE),
        Puzzle(75, "1k6/8/8/8/8/8/1B6/1R2K3 w - - 0 1", listOf("b2e5", "b8c8", "b1b8"), "Double Check", "Level 5: Multi-check combination on b-file!", Color.WHITE),
        Puzzle(76, "3k4/8/3B4/8/8/8/8/3R2K1 w - - 0 1", listOf("d6c7", "d8e8", "d1d8"), "Double Check", "Level 6: Double check sequence!", Color.WHITE),
        Puzzle(77, "1k6/8/8/8/8/8/1B6/1R2K3 w - - 0 1", listOf("b2e5", "b8c8", "b1b8"), "Double Check", "Level 7: Double check combination!", Color.WHITE),
        Puzzle(78, "3k4/8/3B4/8/8/8/8/3R2K1 w - - 0 1", listOf("d6e7", "d8e8", "d1d8"), "Double Check", "Level 8: Master double check pattern!", Color.WHITE),
        Puzzle(79, "1k6/8/8/8/8/8/1B6/1R2K3 w - - 0 1", listOf("b2e5", "b8a8", "b1b8"), "Double Check", "Level 9: Double check mate variant!", Color.WHITE),
        Puzzle(80, "r1b2r1k/pp4pp/2nb4/3Q1pN1/2B1p3/8/PPP2PPP/R1B1K2R w KQ - 2 13", listOf("d5g8", "f8g8", "g5f7"), "Double Check", "Level 10: Grandmaster double check smothered mate!", Color.WHITE),
        Puzzle(81, "3r2k1/5ppp/8/8/8/8/r4PPP/R1R3K1 w - - 0 1", listOf("c1c8", "d8c8", "a1a2"), "Zwischenzug", "Level 1: Play intermediate check before recapturing rook!", Color.WHITE),
        Puzzle(82, "3r2k1/5ppp/7p/8/8/8/r4PPP/R1R3K1 w - - 0 1", listOf("c1c8", "d8c8", "a1a2"), "Zwischenzug", "Level 2: Play intermediate check before recapturing rook!", Color.WHITE),
        Puzzle(83, "3r2k1/5ppp/6p1/8/8/8/r4PPP/R1R3K1 w - - 0 1", listOf("c1c8", "d8c8", "a1a2"), "Zwischenzug", "Level 3: Intermediate back rank check sequence!", Color.WHITE),
        Puzzle(84, "3r2k1/5ppp/5p2/8/8/8/r4PPP/R1R3K1 w - - 0 1", listOf("c1c8", "d8c8", "a1a2"), "Zwischenzug", "Level 4: Intermediate back rank check sequence!", Color.WHITE),
        Puzzle(85, "3r2k1/5ppp/4p3/8/8/8/r4PPP/R1R3K1 w - - 0 1", listOf("c1c8", "d8c8", "a1a2"), "Zwischenzug", "Level 5: Intermediate trades in the center!", Color.WHITE),
        Puzzle(86, "3r2k1/5ppp/3p4/8/8/8/r4PPP/R1R3K1 w - - 0 1", listOf("c1c8", "d8c8", "a1a2"), "Zwischenzug", "Level 6: Zwischenzug rook capture sequence!", Color.WHITE),
        Puzzle(87, "3r2k1/5ppp/1p6/8/8/8/r4PPP/R1R3K1 w - - 0 1", listOf("c1c8", "d8c8", "a1a2"), "Zwischenzug", "Level 7: Delayed recapture check combination!", Color.WHITE),
        Puzzle(88, "3r2k1/5ppp/p7/8/8/8/r4PPP/R1R3K1 w - - 0 1", listOf("c1c8", "d8c8", "a1a2"), "Zwischenzug", "Level 8: Back rank intermediate deflection!", Color.WHITE),
        Puzzle(89, "3r2k1/5pp1/8/8/8/8/r4PPP/R1R3K1 w - - 0 1", listOf("c1c8", "d8c8", "a1a2"), "Zwischenzug", "Level 9: Zwischenzug tactical central trades!", Color.WHITE),
        Puzzle(90, "3r2k1/5p1p/8/8/8/8/r4PPP/R1R3K1 w - - 0 1", listOf("c1c8", "d8c8", "a1a2"), "Zwischenzug", "Level 10: Grandmaster Zwischenzug check and recapture!", Color.WHITE),
        Puzzle(91, "8/P7/8/8/8/8/8/K5k1 w - - 0 1", listOf("a7a8q"), "Promotion", "Level 1: Direct pawn promotion to queen!", Color.WHITE),
        Puzzle(92, "8/1P6/8/8/8/8/8/K5k1 w - - 0 1", listOf("b7b8q"), "Promotion", "Level 2: Advance pawn to queen on b-file!", Color.WHITE),
        Puzzle(93, "8/2P5/8/8/8/8/8/K5k1 w - - 0 1", listOf("c7c8q"), "Promotion", "Level 3: Advance pawn to queen on c-file!", Color.WHITE),
        Puzzle(94, "8/3P4/8/8/8/8/8/K5k1 w - - 0 1", listOf("d7d8q"), "Promotion", "Level 4: Advance pawn to queen on d-file!", Color.WHITE),
        Puzzle(95, "8/4P3/8/8/8/8/8/K5k1 w - - 0 1", listOf("e7e8q"), "Promotion", "Level 5: Advance pawn to queen on e-file!", Color.WHITE),
        Puzzle(96, "8/5P2/8/8/8/8/8/K5k1 w - - 0 1", listOf("f7f8q"), "Promotion", "Level 6: Advance pawn to queen on f-file!", Color.WHITE),
        Puzzle(97, "8/6P1/8/8/8/8/8/K5k1 w - - 0 1", listOf("g7g8q"), "Promotion", "Level 7: Advance pawn to queen on g-file!", Color.WHITE),
        Puzzle(98, "8/7P/8/8/8/8/8/K5k1 w - - 0 1", listOf("h7h8q"), "Promotion", "Level 8: Advance pawn to queen on h-file!", Color.WHITE),
        Puzzle(99, "8/8/8/8/8/8/p7/1k4K1 b - - 0 1", listOf("a2a1q"), "Promotion", "Level 9: Black pawn promotion to queen!", Color.BLACK),
        Puzzle(100, "8/8/8/8/8/8/1p6/k5K1 b - - 0 1", listOf("b2b1q"), "Promotion", "Level 10: Grandmaster-level underpressure promotion!", Color.BLACK)
    )
}
