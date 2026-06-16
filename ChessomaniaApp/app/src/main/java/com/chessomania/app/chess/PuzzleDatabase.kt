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
        Puzzle(1, "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4", listOf("f3g5", "d8e7", "g5f7"), "Fork", "Knight fork - attack king and rook!", Color.WHITE),
        Puzzle(2, "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", listOf("e4d5"), "Fork", "Pawn capture fork threat!", Color.WHITE),
        Puzzle(3, "2r3k1/5ppp/R1N5/8/8/8/5PPP/6K1 w - - 0 1", listOf("c6e7", "g8f8", "e7c8"), "Fork", "Knight fork - win the rook!", Color.WHITE),
        Puzzle(4, "r1bqk2r/ppp2ppp/2np1n2/4p3/2B1P3/P1NP4/1PP2PPP/R1BQK1NR b KQkq - 0 5", listOf("d6d5", "e4d5", "c6d5"), "Fork", "Pawn push forks bishop and knight!", Color.BLACK),
        Puzzle(5, "r2qk2r/ppp1bppp/2np1n2/4p3/4P1b1/2NP1N2/PPPBBPPP/R2QK2R w KQkq - 4 7", listOf("f3e5", "c6e5", "d2d4"), "Fork", "Knight fork trick to gain a pawn!", Color.WHITE),
        Puzzle(6, "r1bqk2r/ppp2ppp/2n5/1B1pp3/4n3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 7", listOf("d3e4", "d5e4", "d1d8"), "Fork", "Win the knight and fork the queen!", Color.WHITE),
        Puzzle(7, "r1bqkb1r/ppp2ppp/2np1n2/4p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 2 5", listOf("c4f7", "e8f7", "f3g5"), "Fork", "Bishop sacrifice leading to a knight fork!", Color.WHITE),
        Puzzle(8, "r2qk2r/ppp2ppp/2n1bn2/3pp3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 7", listOf("c4b5", "d5d4", "c3e2"), "Fork", "Pawn fork threat from knight!", Color.WHITE),
        Puzzle(9, "r2q1rk1/ppp2ppp/2n5/3pNb2/3P4/2n5/PPP2PPP/R1BQR1K1 w - - 0 11", listOf("e5c6", "b7c6", "b2c3"), "Fork", "Centralized knight fork on queen and rook!", Color.WHITE),
        Puzzle(10, "8/p1p3k1/8/2N5/8/8/6PP/6K1 w - - 0 1", listOf("c5e6", "g7f7", "e6c7"), "Fork", "Centralized knight forks pawns!", Color.WHITE),
        Puzzle(11, "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3", listOf("c6d4"), "Pin", "Pin the knight to the queen!", Color.BLACK),
        Puzzle(12, "rnb1kbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 3", listOf("d1h5"), "Pin", "Pin the f7 pawn and threaten mate!", Color.WHITE),
        Puzzle(13, "rnbqk2r/pppp1ppp/4pn2/8/1bPP4/2N5/PP2PPPP/R1BQKBNR w KQkq - 2 4", listOf("d1c2"), "Pin", "Defend the pinned knight!", Color.WHITE),
        Puzzle(14, "r1bqk2r/ppp1bppp/2np1n2/4p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 1 6", listOf("c1g5"), "Pin", "Pin the opponent knight to the queen!", Color.WHITE),
        Puzzle(15, "r1b1k2r/ppp1qppp/2np1n2/1B2p3/4P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 7", listOf("c3d5", "e7d8", "c1g5"), "Pin", "Exploit the pinned knight on c6!", Color.WHITE),
        Puzzle(16, "r1bqk2r/ppp1bppp/2n2n2/3pp3/4P3/2NP1N2/PPP1BPPP/R1BQK2R w KQkq - 0 6", listOf("c1g5", "d5d4", "c3b1"), "Pin", "Establish an active pin on f6!", Color.WHITE),
        Puzzle(17, "r3k2r/ppp2ppp/2np1n2/1B2p3/4P1b1/2NP1N2/PPPB1PPP/R3K2R b KQkq - 0 8", listOf("g4f3", "g2f3"), "Pin", "Exploit pinned pieces on the c-file!", Color.BLACK),
        Puzzle(18, "rnbqkb1r/pp2pppp/2p2n2/3p4/2PP4/2N5/PP2PPPP/R1BQKBNR w KQkq - 0 4", listOf("c1g5", "e7e6", "e2e3"), "Pin", "Pin the knight on f6!", Color.WHITE),
        Puzzle(19, "r1bqk2r/pppnbppp/3p1n2/4p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 1 6", listOf("f3g5", "d6d5", "c3d5"), "Pin", "Apply pressure to the pinned f7 pawn!", Color.WHITE),
        Puzzle(20, "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R b KQkq - 5 4", listOf("f6e4", "c3e4", "d7d5"), "Pin", "Break the pin and fork the bishop!", Color.BLACK),
        Puzzle(21, "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", listOf("a1a8"), "Back Rank", "Back rank checkmate - Rook to 8th rank!", Color.WHITE),
        Puzzle(22, "6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1", listOf("a1a8"), "Back Rank", "Back rank checkmate - no escape for the king!", Color.WHITE),
        Puzzle(23, "5rk1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", listOf("d1d8", "f8d8"), "Back Rank", "Forced rook trade on the back rank!", Color.WHITE),
        Puzzle(24, "5r1k/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", listOf("d1d8", "f8d8"), "Back Rank", "Skewer/Back rank combination!", Color.WHITE),
        Puzzle(25, "r5k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", listOf("d1d8", "r5d8"), "Back Rank", "Classic back rank rook checkmate!", Color.WHITE),
        Puzzle(26, "6k1/R4ppp/8/8/8/8/5PPP/6K1 w - - 0 1", listOf("a7a8"), "Back Rank", "Rook coordinates back rank checkmate!", Color.WHITE),
        Puzzle(27, "6k1/5ppp/R7/8/8/8/5PPP/6K1 w - - 0 1", listOf("a6a8"), "Back Rank", "Infiltrate the back rank for victory!", Color.WHITE),
        Puzzle(28, "r5k1/5ppp/8/8/8/8/1R3PPP/6K1 w - - 0 1", listOf("b2b8", "r5b8"), "Back Rank", "Rook sacrifice for back rank mate!", Color.WHITE),
        Puzzle(29, "6k1/2R2ppp/8/8/8/8/5PPP/6K1 w - - 0 1", listOf("c7c8"), "Back Rank", "Slam the back rank door shut!", Color.WHITE),
        Puzzle(30, "6k1/5rpp/8/8/8/8/R4PPP/6K1 w - - 0 1", listOf("a2a8", "f7f8", "a8f8"), "Back Rank", "Patience on the back rank!", Color.WHITE),
        Puzzle(31, "4k3/8/8/8/8/8/8/4K2R w - - 0 1", listOf("h1h8"), "Skewer", "Rook to 8th rank - check and win material!", Color.WHITE),
        Puzzle(32, "r3k2r/ppp2ppp/2np1n2/1B2p3/4P1b1/2NP1N2/PPPB1PPP/R3K2R w KQkq - 0 8", listOf("b5c6", "b7c6"), "Skewer", "Skewer the king and pawn structure!", Color.WHITE),
        Puzzle(33, "3k4/8/8/8/q7/8/8/4K2R w K - 0 1", listOf("h1h8", "d8e7", "h8a8"), "Skewer", "Skewer the king and win the queen!", Color.WHITE),
        Puzzle(34, "2k5/8/8/8/r7/8/8/4K2R b K - 0 1", listOf("a4a1", "e1d2", "a1h1"), "Skewer", "Black skewers the white king and rook!", Color.BLACK),
        Puzzle(35, "8/p1p3k1/8/2N5/8/8/6PP/6K1 w - - 0 1", listOf("c5e6", "g7f6", "e6c7"), "Skewer", "Skewer the pawn with knight!", Color.WHITE),
        Puzzle(36, "4k3/8/8/8/b7/8/8/4K2R w K - 0 1", listOf("h1h8", "d8e7", "h8a8"), "Skewer", "Skewer the king and capture the bishop!", Color.WHITE),
        Puzzle(37, "2k5/8/8/8/b7/8/8/4K2R b K - 0 1", listOf("a4c6"), "Skewer", "Skewer rook and king on the diagonal!", Color.BLACK),
        Puzzle(38, "2k5/8/8/8/b7/8/8/3K3R w - - 0 1", listOf("h1h8", "c8d7", "h8a8"), "Skewer", "Skewer the bishop on a8!", Color.WHITE),
        Puzzle(39, "4k3/8/8/8/q7/8/8/3K3R w - - 0 1", listOf("h1h8", "e8d7", "h8a8"), "Skewer", "Diagonal king-queen skewer!", Color.WHITE),
        Puzzle(40, "3k4/8/8/8/8/8/8/r3K2R b K - 0 1", listOf("a1h1"), "Skewer", "Skewer king and win the rook!", Color.BLACK),
        Puzzle(41, "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4", listOf("f6e4", "c4f7"), "Sacrifice", "Sacrifice to win material!", Color.BLACK),
        Puzzle(42, "r1bqkb1r/ppp2ppp/2n5/1B1pp3/4n3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("c3e4", "d5e4", "f3e5"), "Sacrifice", "Central knight sacrifice to win a pawn!", Color.WHITE),
        Puzzle(43, "r1bqkb1r/ppp2ppp/2n5/3pp1N1/2B1P3/8/PPPP1PPP/RNBQK2R w KQkq - 0 6", listOf("c4f7", "e8e7", "f7d5"), "Sacrifice", "Sacrifice bishop to expose the king!", Color.WHITE),
        Puzzle(44, "r2qk2r/ppp2ppp/2n2n2/2b1p3/2B1P1b1/2NP1N2/PPP3PP/R1BQK2R w KQkq - 3 8", listOf("c4f7", "e8f7", "f3g5"), "Sacrifice", "Sacrifice the bishop on f7 to disrupt the king!", Color.WHITE),
        Puzzle(45, "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", listOf("e4d5", "d8d5", "b1c3"), "Sacrifice", "Pawn sacrifice for rapid development!", Color.WHITE),
        Puzzle(46, "r1bqk2r/pppn1ppp/3p1n2/4p3/1bB1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 1 6", listOf("c4f7", "e8f7", "f3g5", "f7g8", "g5e6"), "Sacrifice", "Sacrifice the light-squared bishop to win a queen fork!", Color.WHITE),
        Puzzle(47, "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4", listOf("c4f7", "e8f7", "f3g5", "f7e8"), "Sacrifice", "Classic Fried Liver bishop sacrifice!", Color.WHITE),
        Puzzle(48, "r3k2r/ppp2ppp/2np1n2/1B2p3/4P1b1/2NP1N2/PPPB1PPP/R3K2R w KQkq - 0 8", listOf("b5c6", "b7c6", "f3e5", "d6e5"), "Sacrifice", "Pawn sacrifice to open the b-file!", Color.WHITE),
        Puzzle(49, "r1bqk2r/ppp2ppp/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("c4f7", "e8f7", "f3g5"), "Sacrifice", "Expose the black king with a bishop sacrifice!", Color.WHITE),
        Puzzle(50, "r1b1k2r/ppp2ppp/2np1n2/4p3/1bB1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 7", listOf("c4f7", "e8f7", "f3g5"), "Sacrifice", "Dismantle castling rights with a sacrifice!", Color.WHITE),
        Puzzle(51, "rnb1kbnr/pppp1ppp/8/4p1q1/2B1P3/3P4/PPP2PPP/RNBQK1NR w KQkq - 1 4", listOf("d3d4"), "Discovered Attack", "Discovered attack on the queen!", Color.WHITE),
        Puzzle(52, "r3k2r/ppp2ppp/2n1bn2/2b1p1q1/2B1P3/2NP1N2/PPP1QPPP/R1B1K2R w KQkq - 0 1", listOf("c3d5", "g5g2", "h1g1"), "Discovered Attack", "Knight move discovers attack on queen!", Color.WHITE),
        Puzzle(53, "r1bqk2r/ppp2ppp/2np1n2/4p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("c3d5", "f6d5", "c4d5"), "Discovered Attack", "Knight jump discovers bishop scope!", Color.WHITE),
        Puzzle(54, "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", listOf("e4d5", "d8d5", "b1c3"), "Discovered Attack", "Knight move attacks queen upon pawn capture!", Color.WHITE),
        Puzzle(55, "r2qk2r/ppp2ppp/2n2n2/2b1p3/2B1P1b1/2NP1N2/PPP3PP/R1BQK2R w KQkq - 3 8", listOf("c3d5", "f6d5", "c4d5"), "Discovered Attack", "Unveil bishop pressure on f7!", Color.WHITE),
        Puzzle(56, "r1bqk2r/pppn1ppp/3p1n2/4p3/1bB1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 1 6", listOf("c3e2", "c7c6", "c2c3"), "Discovered Attack", "Knight retreat discovers c3 pawn defense!", Color.WHITE),
        Puzzle(57, "rnbqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4", listOf("d2d4", "e5d4", "c3c3"), "Discovered Attack", "Central pawn push opens d-file discovered attacks!", Color.WHITE),
        Puzzle(58, "r3k2r/ppp2ppp/2np1n2/1B2p3/4P1b1/2NP1N2/PPPB1PPP/R3K2R w KQkq - 0 8", listOf("f3e5", "d6e5", "b5c6"), "Discovered Attack", "Knight capture reveals diagonal bishop pin!", Color.WHITE),
        Puzzle(59, "r1bqk2r/ppp2ppp/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("c3b5", "c5b6", "c2c3"), "Discovered Attack", "Knight hop reveals dynamic center attack!", Color.WHITE),
        Puzzle(60, "r1b1k2r/ppp2ppp/2np1n2/4p3/1bB1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 7", listOf("c3d5", "f6d5", "c4d5"), "Discovered Attack", "Centralized knight jump reveals bishop attack!", Color.WHITE),
        Puzzle(61, "r1bqkbnr/pppp1ppp/8/4p3/2BnP3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 4", listOf("c4f7"), "Mate in 1", "Scholar's mate threat - King check!", Color.WHITE),
        Puzzle(62, "rnbqkbnr/pp3ppp/2p5/3pp3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4", listOf("f3f7"), "Mate in 1", "Queen checkmates protected by bishop!", Color.WHITE),
        Puzzle(63, "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4", listOf("c4f7"), "Mate in 1", "Defend Scholar's mate with active checkmate!", Color.BLACK),
        Puzzle(64, "rnbqkbnr/ppp1p1pp/3p1p2/8/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4", listOf("f3h5"), "Mate in 1", "Fools mate variant - checkmate!", Color.WHITE),
        Puzzle(65, "rnb1kbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 3", listOf("d8h4"), "Mate in 1", "Fool's Mate - checkmate in one move!", Color.BLACK),
        Puzzle(66, "6k1/5ppp/8/8/8/8/8/5K1R w - - 0 1", listOf("h1h8"), "Mate in 1", "Rook checkmate on the back rank!", Color.WHITE),
        Puzzle(67, "6k1/R4ppp/8/8/8/8/8/6K1 w - - 0 1", listOf("a7a8"), "Mate in 1", "Checkmate the trapped king!", Color.WHITE),
        Puzzle(68, "6k1/5ppp/R7/8/8/8/8/6K1 w - - 0 1", listOf("a6a8"), "Mate in 1", "Rook coordinates back rank checkmate!", Color.WHITE),
        Puzzle(69, "6k1/2R2ppp/8/8/8/8/8/6K1 w - - 0 1", listOf("c7c8"), "Mate in 1", "Quick back rank mate!", Color.WHITE),
        Puzzle(70, "r5k1/5ppp/8/8/8/8/8/4K2R w - - 0 1", listOf("h1h8"), "Mate in 1", "Mate on the 8th rank!", Color.WHITE),
        Puzzle(71, "r1bqk2r/pppp1ppp/2n2n2/2b5/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 0 1", listOf("e4e5", "f6e4", "c3e4"), "Double Check", "Double check - attack the center and fork!", Color.WHITE),
        Puzzle(72, "r1bqk2r/ppp2ppp/2np1n2/4p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("c3d5", "c6d4", "c4f7"), "Double Check", "Deliver a double check pressure!", Color.WHITE),
        Puzzle(73, "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/2N2N2/PPPP1PPP/RNBQK2R w KQkq - 4 4", listOf("d2d4", "e5d4", "e4e5"), "Double Check", "Initiate center pawn storm check!", Color.WHITE),
        Puzzle(74, "r3k2r/ppp2ppp/2np1n2/1B2p3/4P1b1/2NP1N2/PPPB1PPP/R3K2R w KQkq - 0 8", listOf("c3d5", "g4f3", "g2f3"), "Double Check", "Generate overlapping pins!", Color.WHITE),
        Puzzle(75, "rnbqkbnr/pp3ppp/2p5/3pp3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4", listOf("e4d5", "c6d5", "c4d5"), "Double Check", "Double attack the central files!", Color.WHITE),
        Puzzle(76, "r1bqk2r/ppp2ppp/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("d2d4", "e5d4", "f3d4"), "Double Check", "Unleash double attack from bishop and knight!", Color.WHITE),
        Puzzle(77, "r1b1k2r/ppp2ppp/2np1n2/4p3/1bB1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 7", listOf("d2d4", "e5d4", "f3d4"), "Double Check", "Unveil double center threat!", Color.WHITE),
        Puzzle(78, "r1bqk2r/ppp1bppp/2np1n2/4p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 1 6", listOf("d2d4", "e5d4", "f3d4"), "Double Check", "Unleash central double threat!", Color.WHITE),
        Puzzle(79, "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", listOf("d2d4", "d5e4", "b1c3"), "Double Check", "Initiate double developmental gambit!", Color.WHITE),
        Puzzle(80, "r1bqk2r/pppn1ppp/3p1n2/4p3/1bB1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 1 6", listOf("d2d4", "e5d4", "f3d4"), "Double Check", "Initiate double check tactics!", Color.WHITE),
        Puzzle(81, "r1b1k2r/pppp1ppp/2n2n2/2b1p1q1/4P3/2NP1N2/PPP2PPP/R1BQKB1R w KQkq - 0 1", listOf("f3e5", "g5d2", "e1d2"), "Zwischenzug", "Intermediate move - take the knight first!", Color.WHITE),
        Puzzle(82, "r1bqk2r/ppp2ppp/2np1n2/4p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("c3d5", "c6d4", "c4f7", "e8f7"), "Zwischenzug", "Intermediate capture before executing plan!", Color.WHITE),
        Puzzle(83, "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4", listOf("d2d4", "e5d4", "e4e5", "d7d5"), "Zwischenzug", "Intermediate pawn challenge in the center!", Color.WHITE),
        Puzzle(84, "r3k2r/ppp2ppp/2np1n2/1B2p3/4P1b1/2NP1N2/PPPB1PPP/R3K2R w KQkq - 0 8", listOf("c3d5", "g4f3", "g2f3", "e8c8"), "Zwischenzug", "Intermediate defensive castling!", Color.WHITE),
        Puzzle(85, "rnbqkbnr/pp3ppp/2p5/3pp3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4", listOf("e4d5", "c6d5", "c4d5", "d8d5"), "Zwischenzug", "Intermediate queen trade threat!", Color.WHITE),
        Puzzle(86, "r1bqk2r/ppp2ppp/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6", listOf("d2d4", "e5d4", "f3d4", "c6d4"), "Zwischenzug", "Intermediate recapture check!", Color.WHITE),
        Puzzle(87, "r1b1k2r/ppp2ppp/2np1n2/4p3/1bB1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 7", listOf("d2d4", "e5d4", "f3d4", "b4c3"), "Zwischenzug", "Intermediate bishop trade!", Color.WHITE),
        Puzzle(88, "r1bqk2r/ppp1bppp/2np1n2/4p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 1 6", listOf("d2d4", "e5d4", "f3d4", "c6d4"), "Zwischenzug", "Intermediate knight exchange!", Color.WHITE),
        Puzzle(89, "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", listOf("d2d4", "d5e4", "b1c3", "g8f6"), "Zwischenzug", "Intermediate knight challenge!", Color.WHITE),
        Puzzle(90, "r1bqk2r/pppn1ppp/3p1n2/4p3/1bB1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 1 6", listOf("d2d4", "e5d4", "f3d4", "b4c3"), "Zwischenzug", "Intermediate counter-attack on c3!", Color.WHITE),
        Puzzle(91, "8/P7/8/8/8/8/8/K5k1 w - - 0 1", listOf("a7a8q"), "Promotion", "Promote the pawn to queen!", Color.WHITE),
        Puzzle(92, "8/1P6/8/8/8/8/8/K5k1 w - - 0 1", listOf("b7b8q"), "Promotion", "Advance pawn to queen!", Color.WHITE),
        Puzzle(93, "8/2P5/8/8/8/8/8/K5k1 w - - 0 1", listOf("c7c8q"), "Promotion", "Promote pawn on c-file!", Color.WHITE),
        Puzzle(94, "8/3P4/8/8/8/8/8/K5k1 w - - 0 1", listOf("d7d8q"), "Promotion", "Promote pawn on d-file!", Color.WHITE),
        Puzzle(95, "8/4P3/8/8/8/8/8/K5k1 w - - 0 1", listOf("e7e8q"), "Promotion", "Promote pawn on e-file!", Color.WHITE),
        Puzzle(96, "8/5P2/8/8/8/8/8/K5k1 w - - 0 1", listOf("f7f8q"), "Promotion", "Promote pawn on f-file!", Color.WHITE),
        Puzzle(97, "8/6P1/8/8/8/8/8/K5k1 w - - 0 1", listOf("g7g8q"), "Promotion", "Promote pawn on g-file!", Color.WHITE),
        Puzzle(98, "8/7P/8/8/8/8/8/K5k1 w - - 0 1", listOf("h7h8q"), "Promotion", "Promote pawn on h-file!", Color.WHITE),
        Puzzle(99, "8/8/8/8/8/8/p7/k5K1 b - - 0 1", listOf("a2a1q"), "Promotion", "Black promotes pawn to queen!", Color.BLACK),
        Puzzle(100, "8/8/8/8/8/8/1p6/k5K1 b - - 0 1", listOf("b2b1q"), "Promotion", "Black advances pawn to queen!", Color.BLACK)
    )
}
