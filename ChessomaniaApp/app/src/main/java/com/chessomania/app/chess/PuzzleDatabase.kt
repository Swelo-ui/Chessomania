package com.chessomania.app.chess

// ─── Curated Lichess Chess Puzzles ───────────────────────────────────────────
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

    class SeededRandom(var seed: Long) {
        fun nextInt(max: Int): Int {
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            return (seed % max).toInt()
        }
        fun nextBoolean(): Boolean {
            return nextInt(2) == 0
        }
    }

    fun boardToFen(board: Array<Array<String?>>, turn: String): String {
        val ranks = ArrayList<String>()
        for (r in 0..7) {
            var empty = 0
            var rankStr = ""
            for (c in 0..7) {
                val p = board[r][c]
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        rankStr += empty
                        empty = 0
                    }
                    rankStr += p
                }
            }
            if (empty > 0) {
                rankStr += empty
            }
            ranks.add(rankStr)
        }
        return ranks.joinToString("/") + " " + turn + " - - 0 1"
    }

    fun generateProceduralPuzzle(themeName: String, level: Int): Puzzle {
        var themeHash = 0L
        for (i in 0 until themeName.length) {
            themeHash += themeName[i].code.toLong() * (i + 1)
        }
        val seed = (themeHash * 1000 + level) and 0x7fffffff
        val rng = SeededRandom(seed)
        val board = Array(8) { Array<String?>(8) { null } }
        var fen = ""
        var moves = listOf<String>()
        var sideColor = Color.WHITE
        var tip = "Procedural Level $level: Find the best moves!"

        val cols = "abcdefgh"
        val rows = "87654321"

        if (themeName == "Fork") {
            val fork_r = rng.nextInt(4) + 2
            val fork_c = rng.nextInt(4) + 2
            val offsets = arrayOf(
                intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, -2), intArrayOf(-1, 2),
                intArrayOf(1, -2), intArrayOf(1, 2), intArrayOf(2, -1), intArrayOf(2, 1)
            )
            val pool = arrayListOf(0, 1, 2, 3, 4, 5, 6, 7)
            fun popRandom(): Int {
                val idx = rng.nextInt(pool.size)
                return pool.removeAt(idx)
            }
            val idx1 = popRandom()
            val idx2 = popRandom()
            val idx3 = popRandom()

            val k_r = fork_r + offsets[idx1][0]
            val k_c = fork_c + offsets[idx1][1]
            val t_r = fork_r + offsets[idx2][0]
            val t_c = fork_c + offsets[idx2][1]
            val n_r = fork_r + offsets[idx3][0]
            val n_c = fork_c + offsets[idx3][1]

            val target_piece = if (rng.nextBoolean()) "q" else "r"
            var wk_r = 7
            var wk_c = 7

            for (r in 0..7) {
                var found = false
                for (c in 0..7) {
                    if (Math.abs(r - k_r) <= 1 && Math.abs(c - k_c) <= 1) continue
                    if (r == t_r || c == t_c) {
                        if (target_piece == "r" || target_piece == "q") continue
                    }
                    if (Math.abs(r - t_r) == Math.abs(c - t_c)) {
                        if (target_piece == "q") continue
                    }
                    if (r == n_r && c == n_c) continue
                    if (r == fork_r && c == fork_c) continue
                    wk_r = r
                    wk_c = c
                    found = true
                    break
                }
                if (found) break
            }

            board[wk_r][wk_c] = "K"
            board[k_r][k_c] = "k"
            board[t_r][t_c] = target_piece
            board[n_r][n_c] = "N"

            val move1 = "${cols[n_c]}${rows[n_r]}${cols[fork_c]}${rows[fork_r]}"

            val king_moves = ArrayList<IntArray>()
            for (dr in intArrayOf(-1, 0, 1)) {
                for (dc in intArrayOf(-1, 0, 1)) {
                    if (dr == 0 && dc == 0) continue
                    val nkr = k_r + dr
                    val nkc = k_c + dc
                    if (nkr in 0..7 && nkc in 0..7) {
                        var isAttacked = false
                        for (off in offsets) {
                            if (fork_r + off[0] == nkr && fork_c + off[1] == nkc) {
                                isAttacked = true
                                break
                            }
                        }
                        if (Math.abs(nkr - wk_r) <= 1 && Math.abs(nkc - wk_c) <= 1) {
                            isAttacked = true
                        }
                        if (!isAttacked && (board[nkr][nkc] == null || (nkr == n_r && nkc == n_c))) {
                            king_moves.add(intArrayOf(nkr, nkc))
                        }
                    }
                }
            }

            val k_next_r: Int
            val k_next_c: Int
            if (king_moves.isNotEmpty()) {
                val k_mv = king_moves[rng.nextInt(king_moves.size)]
                k_next_r = k_mv[0]
                k_next_c = k_mv[1]
            } else {
                k_next_r = if (k_r < 7) k_r + 1 else k_r - 1
                k_next_c = k_c
            }

            val move2 = "${cols[k_c]}${rows[k_r]}${cols[k_next_c]}${rows[k_next_r]}"
            val move3 = "${cols[fork_c]}${rows[fork_r]}${cols[t_c]}${rows[t_r]}"

            fen = boardToFen(board, "w")
            moves = listOf(move1, move2, move3)
            sideColor = Color.WHITE
            val targetName = if (target_piece == "q") "queen" else "rook"
            tip = "Level $level: Play Knight fork to check the king and win the $targetName!"

        } else if (themeName == "Pin") {
            val pin_col = rng.nextInt(4) + 2
            board[7][7] = "K"
            board[0][pin_col] = "k"
            val pinned_type = if (rng.nextBoolean()) "n" else "b"
            board[3][pin_col] = pinned_type
            board[7][pin_col] = "R"
            val pawn_offset = if (rng.nextBoolean()) -1 else 1
            val pawn_col = pin_col + pawn_offset
            board[5][pawn_col] = "P"

            val move1 = "${cols[pawn_col]}${rows[5]}${cols[pawn_col]}${rows[4]}"
            val move2 = "${cols[pin_col]}${rows[0]}${cols[pin_col-1]}${rows[0]}"
            val move3 = "${cols[pawn_col]}${rows[4]}${cols[pin_col]}${rows[3]}"

            fen = boardToFen(board, "w")
            moves = listOf(move1, move2, move3)
            sideColor = Color.WHITE
            tip = "Level $level: Leverage the absolute pin in the center to win material!"

        } else if (themeName == "Back Rank") {
            val king_side = rng.nextBoolean()
            val king_col = if (king_side) 6 else 1
            val pawn_cols = if (king_side) intArrayOf(5, 6, 7) else intArrayOf(0, 1, 2)
            board[7][6] = "K"
            board[0][king_col] = "k"
            for (c in pawn_cols) board[1][c] = "p"
            val file_col = if (king_side) 3 else 4
            board[7][file_col] = "R"
            board[6][file_col] = "R"
            val black_rook_col = if (file_col > 0) file_col - 1 else file_col + 1
            board[0][black_rook_col] = "r"

            val move1 = "${cols[file_col]}${rows[6]}${cols[file_col]}${rows[0]}"
            val move2 = "${cols[black_rook_col]}${rows[0]}${cols[file_col]}${rows[0]}"
            val move3 = "${cols[file_col]}${rows[7]}${cols[file_col]}${rows[0]}"

            fen = boardToFen(board, "w")
            moves = listOf(move1, move2, move3)
            sideColor = Color.WHITE
            tip = "Level $level: Infiltrate the back rank and force checkmate!"

        } else if (themeName == "Skewer") {
            board[7][6] = "K"
            board[0][0] = "q"
            board[2][2] = "k"
            board[5][5] = "B"
            fen = boardToFen(board, "w")
            moves = listOf("f3d5", "c6b6", "d5a8")
            sideColor = Color.WHITE
            tip = "Level $level: Skewer the king to win the queen!"

        } else if (themeName == "Sacrifice") {
            board[7][0] = "K"
            board[0][7] = "k"
            board[0][5] = "r"
            board[1][6] = "p"
            board[1][7] = "p"
            board[3][6] = "N"
            board[4][2] = "Q"
            fen = boardToFen(board, "w")
            moves = listOf("c4g8", "f8g8", "g5f7")
            sideColor = Color.WHITE
            tip = "Level $level: Sac the queen to deliver a brilliant smothered checkmate!"

        } else if (themeName == "Discovered Attack") {
            board[1][7] = "K"
            board[7][1] = "R"
            board[3][1] = "B"
            board[0][1] = "k"
            board[6][6] = "q"
            fen = boardToFen(board, "w")
            moves = listOf("b5c6", "b8c8", "c6g2")
            sideColor = Color.WHITE
            tip = "Level $level: Unleash a discovered check and capture the hanging queen!"

        } else if (themeName == "Mate in 1") {
            board[7][7] = "K"
            board[0][4] = "k"
            board[1][3] = "p"
            board[1][4] = "p"
            board[1][5] = "p"
            board[5][5] = "Q"
            board[4][2] = "B"
            fen = boardToFen(board, "w")
            moves = listOf("f3f7")
            sideColor = Color.WHITE
            tip = "Level $level: Deliver a direct checkmate in one single move!"

        } else if (themeName == "Double Check") {
            board[7][7] = "K"
            board[7][4] = "R"
            board[3][4] = "N"
            board[0][4] = "k"
            fen = boardToFen(board, "w")
            moves = listOf("e5d7", "e8d8", "e1e8")
            sideColor = Color.WHITE
            tip = "Level $level: Fire a double check forcing the king to run, then checkmate!"

        } else if (themeName == "Zwischenzug") {
            board[7][6] = "K"
            board[7][0] = "R"
            board[0][0] = "r"
            board[7][5] = "R"
            board[0][6] = "k"
            board[1][6] = "p"
            board[1][7] = "p"
            fen = boardToFen(board, "w")
            moves = listOf("f1f8", "g8f8", "a1a8")
            sideColor = Color.WHITE
            tip = "Level $level: Execute an intermediate check (zwischenzug) before recapturing!"

        } else if (themeName == "Promotion") {
            board[7][7] = "K"
            board[1][0] = "P"
            board[5][6] = "k"
            fen = boardToFen(board, "w")
            moves = listOf("a7a8q")
            sideColor = Color.WHITE
            tip = "Level $level: Push the pawn to the end of the board to promote!"
        }

        return Puzzle(-1, fen, moves, themeName, tip, sideColor)
    }

    fun mirrorFen(fen: String): String {
        val parts = fen.split(" ").toMutableList()
        val ranks = parts[0].split("/")
        val mirroredRanks = ranks.map { rank ->
            val squares = ArrayList<String?>()
            for (ch in rank) {
                if (ch.isDigit()) {
                    val count = ch.digitToInt()
                    for (i in 0 until count) squares.add(null)
                } else {
                    squares.add(ch.toString())
                }
            }
            squares.reverse()
            var rankStr = ""
            var empty = 0
            for (sq in squares) {
                if (sq == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        rankStr += empty
                        empty = 0
                    }
                    rankStr += sq
                }
            }
            if (empty > 0) rankStr += empty
            rankStr
        }
        parts[0] = mirroredRanks.joinToString("/")
        if (parts.size > 2 && parts[2] != "-") {
            val castling = parts[2]
            var newCastling = ""
            if (castling.contains('Q')) newCastling += 'K'
            if (castling.contains('K')) newCastling += 'Q'
            if (castling.contains('q')) newCastling += 'k'
            if (castling.contains('k')) newCastling += 'q'
            parts[2] = if (newCastling.isNotEmpty()) newCastling.toCharArray().sorted().joinToString("") else "-"
        }
        if (parts.size > 3 && parts[3] != "-") {
            val ep = parts[3]
            val file = ep[0]
            val rank = ep[1]
            val newFile = ('h'.code - (file.code - 'a'.code)).toChar()
            parts[3] = "$newFile$rank"
        }
        return parts.joinToString(" ")
    }

    fun mirrorMove(move: String): String {
        val files = "abcdefgh"
        var mirrored = ""
        for (ch in move) {
            val idx = files.indexOf(ch)
            mirrored += if (idx != -1) files[7 - idx] else ch
        }
        return mirrored
    }

    private fun getChunk0(): List<Puzzle> {
        return listOf(
            Puzzle(1, "1n4k1/5ppp/8/p7/P2r4/7P/5PP1/2R3K1 w - - 1 27", listOf("c1c8", "d4d8", "c8d8"), "Back Rank", "Rating 400 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(2, "2r1k3/5p2/p3pp1r/3p3P/1p1R2P1/5N2/PP2BP2/K1R5 b - - 0 23", listOf("c8c1"), "Back Rank", "Rating 399 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(3, "1r4k1/5ppp/4p3/p7/P3P2P/1qpQ1P2/6PK/3R4 w - - 2 31", listOf("d3d8", "b8d8", "d1d8"), "Back Rank", "Rating 399 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(4, "5r1k/3q2pp/3P2p1/3B4/1p6/6P1/1P1B3P/5R1K w - - 0 39", listOf("f1f8"), "Back Rank", "Rating 421 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(5, "r5k1/1p3ppp/p2p1q2/2pP4/2PbQ3/1P6/P4PPP/4RBK1 w - - 1 22", listOf("e4e8", "a8e8", "e1e8"), "Back Rank", "Rating 669 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(6, "4r2k/6pp/p7/6q1/3P4/P4RP1/1PP3P1/5RK1 w - - 0 28", listOf("f3f8", "e8f8", "f1f8"), "Back Rank", "Rating 442 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(7, "8/1p5k/pR6/7p/5N2/1B6/Pr3PPP/6K1 b - - 0 31", listOf("b2b1", "b3d1", "b1d1"), "Back Rank", "Rating 458 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(8, "r4rk1/5p1p/5np1/6N1/3P3Q/8/5PPP/R5K1 b - - 0 30", listOf("a8a1"), "Back Rank", "Rating 474 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(9, "rnb2q1k/pp4pp/8/4b3/2Bp4/2P5/P5PP/R4RK1 w - - 0 19", listOf("f1f8"), "Back Rank", "Rating 487 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(10, "r4k2/p3npp1/3Qp3/8/4Pp1q/5K2/PP2BP2/3R4 w - - 4 28", listOf("d6d8", "a8d8", "d1d8"), "Back Rank", "Rating 931 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(11, "1k3b2/ppp5/3p1q2/7p/8/1P5P/P1P2PP1/4R1K1 w - - 0 26", listOf("e1e8", "f6d8", "e8d8"), "Back Rank", "Rating 500 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(12, "3r2k1/2Q1bpp1/4pn1p/1p6/7B/1P3N2/1P3PPP/3R2K1 b - - 0 23", listOf("d8d1", "f3e1", "d1e1"), "Back Rank", "Rating 512 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(13, "r5k1/1p3ppp/p1np4/3Q4/8/P3r1qP/1PP3P1/4RRK1 w - - 0 19", listOf("d5f7", "g8h8", "f7f8", "a8f8", "f1f8"), "Back Rank", "Rating 978 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(14, "4rn2/ppQ3pk/2p2n1p/8/3P3B/8/PP3PPP/RN2R1K1 b - - 0 18", listOf("e8e1"), "Back Rank", "Rating 523 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(15, "5r1k/pp1b2p1/n1p1pqBp/4P2Q/3P4/6R1/PPP3PP/R5K1 b - - 0 21", listOf("f6f2", "g1h1", "f2f1", "a1f1", "f8f1"), "Back Rank", "Rating 993 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(16, "2k1r1r1/Qpp1q3/3p3p/3P2p1/2PB4/2Nb4/PP3PPP/2R3K1 b - - 2 23", listOf("e7e1", "c1e1", "e8e1"), "Back Rank", "Rating 533 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(17, "4r1k1/ppp2ppp/4rn2/8/2P5/3P1B2/P1P2PPP/Q3R1K1 b - - 0 17", listOf("e6e1", "a1e1", "e8e1"), "Back Rank", "Rating 544 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(18, "r1b2r1k/ppp3pp/3pP3/6B1/2n1P3/2P5/P1P3PP/R4RK1 w - - 0 18", listOf("f1f8"), "Back Rank", "Rating 555 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(19, "r5k1/5ppp/2pQ4/pp6/8/P1P2q2/1P3P1P/3R2K1 w - - 0 30", listOf("d6d8", "a8d8", "d1d8"), "Back Rank", "Rating 565 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(20, "3r2k1/p5pp/1p6/2p5/2P1q3/PP1p3P/5QP1/5RK1 w - - 0 35", listOf("f2f7", "g8h8", "f7f8", "d8f8", "f1f8"), "Back Rank", "Rating 1071 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(21, "r1b1r1k1/pp3ppp/2n5/5n2/5P2/5BP1/PPPN3P/2K1R2R w - - 0 19", listOf("e1e8"), "Back Rank", "Rating 575 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(22, "r5k1/ppp2ppp/2n2q2/3p4/P2P4/R1P2b1P/4QPP1/4R1K1 w - - 0 18", listOf("e2e8", "a8e8", "e1e8"), "Back Rank", "Rating 585 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(23, "5r1k/6p1/p4qQp/1p6/3PB3/P7/1P4PP/3R2K1 b - - 3 29", listOf("f6f2", "g1h1", "f2f1", "d1f1", "f8f1"), "Back Rank", "Rating 1098 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(24, "8/R3N1pk/4bp1p/8/4P3/1r1R4/5PPP/6K1 b - - 0 27", listOf("b3b1", "d3d1", "b1d1"), "Back Rank", "Rating 596 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(25, "2k4r/ppp5/2n1PN2/1R3p1p/6p1/P1P5/5PPP/3rR1K1 b - - 4 26", listOf("d1e1"), "Back Rank", "Rating 606 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(26, "4rk2/pppbqpp1/8/8/4B3/N1P2PP1/PP1r2P1/R4K1R w - - 0 21", listOf("h1h8"), "Back Rank", "Rating 615 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(27, "8/5rkp/6p1/Q1p5/8/2P5/P2q2PP/1R4K1 b - - 4 29", listOf("d2f2", "g1h1", "f2f1", "b1f1", "f7f1"), "Back Rank", "Rating 1136 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(28, "2r3k1/1Q3ppp/4p3/2Bpqb2/1P6/P1r5/5PPP/R4RK1 w - - 0 25", listOf("b7c8"), "Back Rank", "Rating 625 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(29, "1k6/pp2r1pp/8/3rq3/8/Q7/P4PPP/1R1R2K1 b - - 1 25", listOf("d5d1", "b1d1", "e5e1", "d1e1", "e7e1"), "Back Rank", "Rating 1147 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(30, "6k1/pR3ppp/8/8/2N5/1Pn4r/P2r3P/5K2 w - - 0 33", listOf("b7b8", "d2d8", "b8d8"), "Back Rank", "Rating 635 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(31, "5r1k/pp3qpp/4p3/8/1P1P4/P6Q/1B4PP/2R3K1 b - - 0 28", listOf("f7f2", "g1h1", "f2f1", "c1f1", "f8f1"), "Back Rank", "Rating 1157 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(32, "5rk1/p4ppp/8/3p4/3N1Q2/P2q1P2/4p1PP/2R4K b - - 1 28", listOf("d3d1", "c1d1", "e2d1q"), "Back Rank", "Rating 1046 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(33, "8/r4kpp/2n1pp2/2NP4/3P4/1R6/5PPP/6K1 b - - 0 29", listOf("a7a1", "b3b1", "a1b1"), "Back Rank", "Rating 646 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(34, "7k/p5pp/1p3p2/6P1/1PQr4/3p1B1P/5K2/q2Nr3 w - - 0 28", listOf("c4c8", "d4d8", "c8d8", "e1e8", "d8e8"), "Back Rank", "Rating 657 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(35, "5r1k/6pp/pq1p4/8/1p2R2P/8/1P4P1/5R1K w - - 0 37", listOf("f1f8"), "Back Rank", "Rating 671 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(36, "2k4r/ppp3pp/1q3p2/2b3n1/4r3/NB1Q3P/PP3PP1/R2R2K1 w - - 10 20", listOf("d3d7", "c8b8", "d7d8", "h8d8", "d1d8"), "Back Rank", "Rating 1186 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(37, "2r2rk1/1R1Q2pp/p3p3/4p3/3bB3/8/P5PP/5R1K b - - 0 27", listOf("f8f1"), "Back Rank", "Rating 682 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(38, "r1b2k1r/1pN1ppb1/p5p1/4P2p/8/2N1n3/PPP1B1PP/3R2K1 w - - 0 18", listOf("d1d8"), "Back Rank", "Rating 694 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(39, "1r5k/p3Q1pp/5p2/8/8/5q2/P7/4R1K1 w - - 4 31", listOf("e7e8", "b8e8", "e1e8"), "Back Rank", "Rating 706 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(40, "2r4k/2P3pp/q4p2/3Qp3/3p4/rp3N2/1R3PPP/2n3K1 w - - 0 29", listOf("d5d8", "c8d8", "c7d8q"), "Back Rank", "Rating 1106 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(41, "1k6/1p6/2R4p/B5pQ/3p4/P4r2/1P3qPP/R6K b - - 0 34", listOf("f2f1", "a1f1", "f3f1"), "Back Rank", "Rating 717 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(42, "6k1/p5pp/4pr1n/3p4/P2P4/1PQ1P2B/4q1PP/4R1K1 b - - 2 28", listOf("e2f2", "g1h1", "f2f1", "e1f1", "f6f1"), "Back Rank", "Rating 1237 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(43, "2r4k/pp4p1/6bp/3p3P/2qP4/5Q2/PP1R1PP1/K3R3 b - - 3 28", listOf("c4c1", "e1c1", "c8c1"), "Back Rank", "Rating 727 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(44, "2r4r/3N1ppk/p6p/3R1p2/1p6/1Q3P2/PPq4P/K3R3 b - - 3 27", listOf("c2c1", "e1c1", "c8c1"), "Back Rank", "Rating 738 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(45, "3r3k/4Nppp/4bn2/4p3/4P3/Pq3P1P/1P4P1/2KR3R w - - 0 22", listOf("d1d8", "f6e8", "d8e8"), "Back Rank", "Rating 748 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(46, "r6k/pppb2p1/3p1q1p/2b1N2Q/8/1B6/PP3PPP/R1B1R1K1 b - - 0 18", listOf("f6f2", "g1h1", "f2e1"), "Back Rank", "Rating 1050 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(47, "rn2r1k1/pp3ppp/8/3q1b2/3P4/3B1Pn1/PP4PN/R1BQR1K1 w - - 1 15", listOf("e1e8"), "Back Rank", "Rating 759 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(48, "2R5/Q4pkp/3p1qp1/P7/8/1b2P3/5PPP/6K1 b - - 0 27", listOf("f6a1", "c8c1", "a1c1"), "Back Rank", "Rating 768 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(49, "1k3r2/ppp5/3p4/4n3/4P2p/2Q4P/PP3qP1/2R4K w - - 0 28", listOf("c3c7", "b8a8", "c7c8", "f8c8", "c1c8"), "Back Rank", "Rating 777 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(50, "4r1k1/p4ppp/5b2/2pQN3/8/1P5P/PBP2qP1/7K w - - 1 24", listOf("d5f7", "g8h8", "f7e8"), "Back Rank", "Rating 786 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(51, "bb5k/6pp/2p5/p1q2p2/2B5/P4N1P/1PPR1P2/6K1 w - - 0 31", listOf("d2d8", "c5f8", "d8f8"), "Back Rank", "Rating 795 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(52, "r1r3k1/p1q1pp1p/8/5p2/3R4/Q4PP1/PPP4P/1K5R b - - 0 21", listOf("c7c2", "b1a1", "c2c1", "h1c1", "c8c1"), "Back Rank", "Rating 803 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(53, "r3k2r/p2b1pp1/7p/3Q4/4n2q/B1P5/P4PPP/RN2R1K1 b kq - 0 19", listOf("h4f2", "g1h1", "f2e1"), "Back Rank", "Rating 810 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(54, "k7/6R1/3Np1pp/4P3/2Pn1r2/8/6PP/6K1 b - - 0 34", listOf("d4e2", "g1h1", "f4f1"), "Back Rank", "Rating 818 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(55, "5r1k/pb5p/1p4p1/3P1q2/8/1Q6/PP3PPP/3R1BK1 b - - 0 26", listOf("f5f2", "g1h1", "f2f1", "d1f1", "f8f1"), "Back Rank", "Rating 826 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(56, "4r1k1/5ppp/p4b2/1p1Q4/3p4/P2P3P/Bq3PP1/R5K1 w - - 0 23", listOf("d5f7", "g8h8", "f7e8"), "Back Rank", "Rating 833 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(57, "r1b1r1k1/p1qN1ppp/2p5/2pp2b1/8/2PP4/PP1N1PPP/R2QR1K1 w - - 0 15", listOf("e1e8"), "Back Rank", "Rating 840 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(58, "6k1/p4ppp/4p3/8/P7/5Q1P/3qnPPK/1r6 w - - 0 24", listOf("f3a8", "b1b8", "a8b8", "d2d8", "b8d8"), "Back Rank", "Rating 847 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(59, "r5k1/4Qrpp/1p1p4/p1pP4/8/4P3/Pq4PP/5RK1 w - - 0 24", listOf("e7f7", "g8h8", "f7f8", "a8f8", "f1f8"), "Back Rank", "Rating 854 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(60, "3R4/6kp/1p2rp2/p5p1/3Nn3/1P6/P4PPP/5K2 b - - 9 37", listOf("e4d2", "f1g1", "e6e1"), "Back Rank", "Rating 860 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(61, "r1k1r3/1pp1R2Q/p2p2p1/8/8/2N3N1/PPPq2PP/4R2K b - - 0 25", listOf("d2e1", "e7e1", "e8e1", "g3f1", "e1f1"), "Back Rank", "Rating 865 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(62, "7k/ppq3pp/7r/1Bnr4/8/7P/PPP2PP1/3RR1K1 w - - 0 19", listOf("e1e8"), "Back Rank", "Rating 871 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(63, "3r2k1/1pQ2ppp/8/8/1p1P2Pb/1b5P/3q1P2/4R1K1 w - - 0 27", listOf("c7d8", "h4d8", "e1e8"), "Back Rank", "Rating 877 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(64, "1R1r2k1/4nppp/B2r4/3p4/Q3pP2/P1q5/6PP/1R4K1 w - - 0 26", listOf("a4e8", "d8e8", "b8e8"), "Back Rank", "Rating 1442 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(65, "5rkr/p3R1p1/2p5/1p2N2p/3P1n2/2P5/PP4PP/5RK1 b - - 1 22", listOf("f4e2", "g1h1", "f8f1"), "Back Rank", "Rating 883 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(66, "6k1/1N3p1p/r1R3p1/3n4/1P1P4/4P3/5PPP/R5K1 b - - 0 24", listOf("a6a1", "c6c1", "a1c1"), "Back Rank", "Rating 889 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(67, "6k1/5p1p/1p2pnp1/8/3P4/1N1RP1Q1/Pq3PPP/6K1 b - - 0 32", listOf("b2b1", "b3c1", "b1c1", "d3d1", "c1d1"), "Back Rank", "Rating 894 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(68, "6k1/rR3ppp/1np5/2Np4/3r2P1/8/8/7K w - - 0 30", listOf("b7b8", "b6c8", "b8c8"), "Back Rank", "Rating 899 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(69, "1k3r2/ppp2Bpp/8/2q5/7Q/8/3R1PPP/6K1 b - - 0 27", listOf("c5c1", "d2d1", "c1d1"), "Back Rank", "Rating 904 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(70, "3r4/5Rpp/p7/1p2p1k1/3nP3/1B6/PPP3PP/2K5 b - - 0 22", listOf("d4e2", "c1b1", "d8d1"), "Back Rank", "Rating 909 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(71, "4rk2/p4p2/2R4p/3p2p1/3Pn3/P7/1B3PPP/4RK2 b - - 0 28", listOf("e4d2", "f1g1", "e8e1"), "Back Rank", "Rating 915 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(72, "r5k1/1p1b1rpp/3p4/1p1p4/1P6/q1P2Q2/6PP/5R1K w - - 0 22", listOf("f3f7", "g8h8", "f7f8", "a8f8", "f1f8"), "Back Rank", "Rating 920 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(73, "5rk1/p5pp/1p6/3R4/1P2P3/P1Q5/q5PP/2R3K1 b - - 0 30", listOf("a2f2", "g1h1", "f2f1", "c1f1", "f8f1"), "Back Rank", "Rating 924 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(74, "6k1/p4ppp/1q1p1r2/2pQ1P2/8/1P3P2/P5PP/3R2K1 w - - 3 28", listOf("d5a8", "b6b8", "a8b8"), "Back Rank", "Rating 930 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(75, "4r2k/p3rQpp/1pp3p1/2b5/6P1/2P4P/PP6/5R1K w - - 1 31", listOf("f7f8", "e8f8", "f1f8"), "Back Rank", "Rating 935 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk1(): List<Puzzle> {
        return listOf(
            Puzzle(76, "R4bk1/4rp1p/6p1/3B4/2n5/2N5/1PP2PPP/5K2 b - - 4 25", listOf("c4d2", "f1g1", "e7e1"), "Back Rank", "Rating 940 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(77, "5r1k/p1B3pp/1p6/2p5/3PQ3/P1P1P3/1P2qRPP/1R4K1 b - - 0 28", listOf("e2f2", "g1h1", "f2f1", "b1f1", "f8f1"), "Back Rank", "Rating 945 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(78, "r5k1/1p3pp1/p1p2Q1p/3p4/1b1P1B2/2N5/PPP1rPPP/4R1K1 b - - 0 19", listOf("e2e1"), "Back Rank", "Rating 950 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(79, "r4rk1/6pp/p5b1/2p1P1N1/3p3Q/1PnP4/P1P3PP/R1B3K1 b - - 0 21", listOf("c3e2", "g1h1", "f8f1"), "Back Rank", "Rating 954 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(80, "r1k5/3nR1pp/1P6/2B2p2/3P4/8/5PPP/6K1 b - - 2 39", listOf("a8a1", "e7e1", "a1e1"), "Back Rank", "Rating 959 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(81, "rnR2bk1/pp3ppp/8/5N2/6P1/r1P4K/7P/8 w - - 2 25", listOf("f5e7", "g8h8", "c8f8"), "Back Rank", "Rating 963 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(82, "r3r1k1/pp3ppp/2p5/3p1Q2/1Pn2R2/P6P/2P1q3/1K3R2 w - - 0 30", listOf("f5f7", "g8h8", "f7f8", "e8f8", "f4f8", "a8f8", "f1f8"), "Back Rank", "Rating 967 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(83, "3R1r2/1pn3kp/1p4p1/p1q1N3/P7/1QP5/1P3PPP/3R2K1 b - - 0 28", listOf("c5f2", "g1h1", "f2f1", "d1f1", "f8f1"), "Back Rank", "Rating 971 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(84, "3r2k1/Q4ppp/p2rp1n1/1p2q3/2p1P3/P1P4P/5RP1/1B3RK1 w - - 3 27", listOf("a7f7", "g8h8", "f7f8", "g6f8", "f2f8", "d8f8", "f1f8"), "Back Rank", "Rating 975 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(85, "2r2bk1/pp3pnp/1q3Q2/4p3/3r2P1/5RN1/PP3P1P/5RK1 w - - 0 28", listOf("f6f7", "g8h8", "f7f8", "c8f8", "f3f8"), "Back Rank", "Rating 978 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(86, "5rk1/1pp2qp1/p2p3p/2P1p1B1/1P2P1Q1/P2P4/6PP/R5K1 b - - 0 21", listOf("f7f2", "g1h1", "f2f1", "a1f1", "f8f1"), "Back Rank", "Rating 982 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(87, "2k5/ppp2B2/2p5/4P3/4b3/BP5K/P4br1/3R4 w - - 10 35", listOf("f7e6", "c8b8", "d1d8"), "Back Rank", "Rating 985 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(88, "Q1q3k1/p4ppp/1p2p3/2r5/8/P7/2P2PPP/3R2K1 w - - 4 29", listOf("d1d8", "c8d8", "a8d8"), "Back Rank", "Rating 988 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(89, "5rk1/p5pr/1p6/2pNn2p/4P2P/3P2P1/PP1K4/5R2 w - - 0 31", listOf("d5e7", "g8h8", "f1f8"), "Back Rank", "Rating 991 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(90, "2r2rk1/p2R4/bp2p1p1/2q1N1Q1/2P5/P7/5PPP/2R3K1 b - - 3 25", listOf("c5f2", "g1h1", "f2f1", "c1f1", "f8f1"), "Back Rank", "Rating 994 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(91, "r4r1k/2ppnqpB/pp6/8/4Q3/PbB1R3/1P3PPP/R5K1 b - - 4 21", listOf("f7f2", "g1h1", "f2f1", "a1f1", "f8f1"), "Back Rank", "Rating 998 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(92, "2r3k1/5ppp/4b3/1q6/4P2P/1PQ5/1KP5/3R4 w - - 1 30", listOf("c3c8", "e6c8", "d1d8", "b5e8", "d8e8"), "Back Rank", "Rating 1003 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(93, "2k2r2/ppp2p2/2p3p1/4q2p/4P3/3Q2PP/PPP3K1/3R4 w - - 0 23", listOf("d3d7", "c8b8", "d7d8", "f8d8", "d1d8"), "Back Rank", "Rating 1008 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(94, "1r5r/p1p2p1p/3k2p1/3pR3/Q2R4/8/P1P2PPP/6K1 b - - 0 24", listOf("b8b1", "d4d1", "b1d1", "e5e1", "d1e1"), "Back Rank", "Rating 1014 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(95, "k4b1r/ppR3pp/4q3/8/4PB2/2P3P1/PP5P/3R2K1 w - - 0 21", listOf("d1d8", "e6c8", "c7c8"), "Back Rank", "Rating 1019 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(96, "R4nk1/5ppp/3p4/3Np1P1/7q/5Q2/3r1PK1/2r5 w - - 0 32", listOf("d5e7", "g8h8", "a8f8"), "Back Rank", "Rating 1026 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(97, "4r1k1/Bp1R1p1p/6p1/2p5/1P1b1P2/P7/5RPP/7K b - - 0 26", listOf("e8e1", "f2f1", "e1f1"), "Back Rank", "Rating 1031 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(98, "3R1rk1/pp4pp/8/8/4p1Q1/2b5/Pq3PPP/1N4K1 w - - 2 25", listOf("g4e6", "g8h8", "d8f8"), "Back Rank", "Rating 1037 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(99, "1k3r2/ppp5/6nQ/5p2/8/2PB4/P1P2q1P/4R2K w - - 0 28", listOf("h6f8", "g6f8", "e1e8"), "Back Rank", "Rating 1043 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(100, "3r2k1/p4p1p/4p1p1/1p6/2n5/1PR3PP/P3PPB1/5K2 b - - 0 25", listOf("d8d1"), "Back Rank", "Rating 1048 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(101, "4r2k/ppp1q1pp/5p2/5Q2/3P1P2/3P4/PP3PPP/R4K2 b - - 0 21", listOf("e7e2", "f1g1", "e2e1", "a1e1", "e8e1"), "Back Rank", "Rating 1053 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(102, "2B2rk1/1p4pp/1NpP4/p7/2Pn4/P7/1P4PP/5RK1 b - - 0 29", listOf("d4e2", "g1h1", "f8f1"), "Back Rank", "Rating 1059 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(103, "2k4r/ppp2Q2/2p3p1/4b1Bp/P3q3/4n2P/2P2PP1/3R2K1 w - - 0 25", listOf("f7d7", "c8b8", "d7d8", "h8d8", "d1d8"), "Back Rank", "Rating 1723 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(104, "4r1k1/5ppp/p7/1p1Qq3/8/8/PPP3PP/5RK1 w - - 0 22", listOf("d5f7", "g8h8", "f7f8", "e8f8", "f1f8"), "Back Rank", "Rating 1064 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(105, "r1b3k1/p4ppp/8/1pqpQ3/2p2B2/6PP/PP4K1/4R3 w - - 0 25", listOf("e5e8", "c5f8", "e8f8", "g8f8", "f4d6", "f8g8", "e1e8"), "Back Rank", "Rating 1069 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(106, "5rk1/R5pp/4pq2/3pN2b/3Qp3/4P3/6PP/1R4K1 b - - 3 30", listOf("f6f2", "g1h1", "f2f1", "b1f1", "f8f1"), "Back Rank", "Rating 1073 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(107, "4r1k1/6pp/p1p5/1p2q3/1P1p4/P2P1Q2/6PP/5RK1 w - - 0 34", listOf("f3f7", "g8h8", "f7f8", "e8f8", "f1f8"), "Back Rank", "Rating 1077 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(108, "3q2k1/pQ3rpp/8/8/8/8/P1r3PP/5RK1 w - - 0 22", listOf("b7f7", "g8h8", "f7f8", "d8f8", "f1f8"), "Back Rank", "Rating 1082 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(109, "8/p2Q1pbk/6pp/1P2p3/8/4qP2/P2p2PP/3R3K b - - 0 38", listOf("e3e1", "d1e1", "d2e1q"), "Back Rank", "Rating 1085 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(110, "5r1k/1pp3p1/p2p1q1p/8/3PQ3/P2P4/BPP3PP/R5K1 b - - 0 21", listOf("f6f2", "g1h1", "f2f1", "a1f1", "f8f1"), "Back Rank", "Rating 1089 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(111, "6k1/p1r2ppp/1p6/4P3/8/3R1q2/P5PP/6K1 w - - 0 33", listOf("d3d8"), "Back Rank", "Rating 1093 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(112, "2k3r1/ppp2p2/6qp/3Q4/8/P7/2P2PPP/3R2K1 w - - 3 23", listOf("d5d7", "c8b8", "d7d8", "g8d8", "d1d8"), "Back Rank", "Rating 1098 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(113, "5rk1/2p3pp/Q3R3/2q2r2/8/PP6/2P2PPP/4R1K1 b - - 4 27", listOf("c5f2", "g1h1", "f2f1", "e1f1", "f5f1", "a6f1", "f8f1"), "Back Rank", "Rating 1102 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(114, "5rk1/pb2R1p1/np1n3B/3pNq2/3P4/1Q6/PP4PP/R5K1 b - - 0 29", listOf("f5f2", "g1h1", "f2f1", "a1f1", "f8f1"), "Back Rank", "Rating 1107 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(115, "5rk1/pp2R1p1/6Qp/8/8/8/Pq4PP/4R1K1 b - - 9 30", listOf("b2f2", "g1h1", "f2f1", "e1f1", "f8f1"), "Back Rank", "Rating 1111 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(116, "3r2k1/1p3p1p/pQBqp1p1/8/8/2P5/PP3PPP/R5K1 b - - 0 23", listOf("d6d1", "a1d1", "d8d1"), "Back Rank", "Rating 1116 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(117, "r4k2/5ppp/7q/pp1r4/2pN4/P1P4P/1P2Q1P1/4R1K1 w - - 0 35", listOf("e2e7", "f8g8", "e7e8", "a8e8", "e1e8"), "Back Rank", "Rating 1121 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(118, "2r3k1/pp3ppp/1n6/8/3PQb2/2q2N1P/P4PP1/4R1K1 w - - 0 22", listOf("e4e8", "c8e8", "e1e8"), "Back Rank", "Rating 1125 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(119, "3rr1k1/2p3pp/p7/1pQ1ppN1/8/1P1qP3/PB1PNPPP/2R1K2R b K - 1 17", listOf("d3d2", "e1f1", "d2d1", "c1d1", "d8d1"), "Back Rank", "Rating 1130 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(120, "7B/1kn2Q2/b6p/p2n1P2/4q3/1P6/P5PP/2R3K1 b - - 0 28", listOf("e4e3", "g1h1", "e3c1"), "Back Rank", "Rating 1134 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(121, "2k4r/ppp2ppp/4p3/8/8/b1PQ3P/q4PP1/3R2K1 w - - 0 19", listOf("d3d7", "c8b8", "d7d8", "h8d8", "d1d8"), "Back Rank", "Rating 1139 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(122, "4r1k1/2p3pp/5p2/pPP1q3/P2Q4/1P6/5PPP/3R1K2 b - - 0 28", listOf("e5e2", "f1g1", "e2e1", "d1e1", "e8e1"), "Back Rank", "Rating 1143 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(123, "r5k1/6pp/p6q/1pNp4/2pPp3/P1P1r2b/1P3QPK/3R1R2 w - - 0 29", listOf("f2f7", "g8h8", "f7f8", "a8f8", "f1f8"), "Back Rank", "Rating 1147 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(124, "3r4/p5pk/1pp5/6BP/4n2Q/8/PPP3P1/1K1R4 b - - 0 27", listOf("d8d1", "g5c1", "e4d2", "b1a1", "d1c1"), "Back Rank", "Rating 1152 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(125, "B4r1k/3R2p1/p3Q2p/1p6/2p5/P7/2q2PPP/3R2K1 b - - 0 25", listOf("c2f2", "g1h1", "f2f1", "d1f1", "f8f1"), "Back Rank", "Rating 1156 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(126, "4k1r1/pQ3p1p/3pp3/7P/4n3/1N6/PPP2P2/1K4R1 b - - 0 25", listOf("g8g1", "b3c1", "e4d2", "b1a1", "g1c1"), "Back Rank", "Rating 1161 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(127, "8/2p2r1k/6p1/1p5p/1P2P2P/6Q1/5qNP/4R2K b - - 1 38", listOf("f2f1", "e1f1", "f7f1"), "Back Rank", "Rating 1165 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(128, "2q3k1/pp4pp/2r1p3/3n2B1/3P4/P4Q2/1P4PP/5RK1 w - - 2 27", listOf("f3f7", "g8h8", "f7f8", "c8f8", "f1f8"), "Back Rank", "Rating 1170 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(129, "5rk1/p1R3pp/2N1pn2/4N3/1p2P3/4KP2/r5PP/8 w - - 0 25", listOf("c6e7", "g8h8", "e5f7", "f8f7", "c7c8", "f6e8", "c8e8", "f7f8", "e8f8"), "Back Rank", "Rating 1941 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(130, "5r1k/6p1/pq6/3Q4/P3p3/1PR5/5RPP/6K1 b - - 0 28", listOf("b6f2", "g1h1", "f2e1"), "Back Rank", "Rating 1175 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(131, "4r1k1/6pp/2pQ1p2/5b2/P1q5/8/P2B1PPP/2R3K1 b - - 1 26", listOf("c4c1", "d2c1", "e8e1"), "Back Rank", "Rating 1180 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(132, "4r1k1/p1p3pp/8/2p1q2Q/2P5/1P2p3/P5PP/5R1K w - - 2 33", listOf("h5f7", "g8h8", "f7f8", "e8f8", "f1f8"), "Back Rank", "Rating 1184 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(133, "4r1k1/p4ppp/2pp4/6q1/4r3/5Q2/PPP1N1B1/5RK1 w - - 0 24", listOf("f3f7", "g8h8", "f7f8", "e8f8", "f1f8"), "Back Rank", "Rating 1190 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(134, "5rk1/p5pp/4p3/2P2r2/3PQq2/8/P5PP/3RR1K1 b - - 0 26", listOf("f4f2", "g1h1", "f2f1", "e1f1", "f5f1", "d1f1", "f8f1"), "Back Rank", "Rating 1195 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(135, "7k/1p2q1pp/2p1B3/3pQ3/4p2P/4P3/r2r1PP1/1R4K1 w - - 1 28", listOf("e5b8", "e7d8", "b8d8"), "Back Rank", "Rating 1202 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(136, "r1r3k1/pp2bpp1/6n1/3pqp1Q/8/4B2R/PP4PP/5RK1 w - - 0 27", listOf("h5h7", "g8f8", "h7h8", "g6h8", "h3h8"), "Back Rank", "Rating 1211 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(137, "4r1k1/ppp2ppp/2n5/8/5P2/2q1R2Q/P5PP/4R2K b - - 0 22", listOf("c3e1", "e3e1", "e8e1"), "Back Rank", "Rating 1223 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(138, "5r1k/p3Q1pp/1p6/8/3Pp3/4P3/PP1q2PP/R5K1 b - - 1 23", listOf("d2f2", "g1h1", "f2f1", "a1f1", "f8f1"), "Back Rank", "Rating 1234 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(139, "5k2/1Q3ppp/4p3/8/8/4Bb1P/qb3PP1/6K1 w - - 0 34", listOf("e3c5", "f8g8", "b7b8"), "Back Rank", "Rating 1246 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(140, "4r1k1/p4ppp/bp6/3Q4/3P4/2q1B3/P4PPP/2R3K1 b - - 1 25", listOf("c3c1", "e3c1", "e8e1"), "Back Rank", "Rating 1258 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(141, "2r2k2/5ppp/p7/5q2/2p5/2P5/P3QPPP/4R1K1 w - - 2 25", listOf("e2e7", "f8g8", "e7e8", "c8e8", "e1e8"), "Back Rank", "Rating 1269 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(142, "r1b5/1pkp3R/1nn1pQB1/1p6/3RN3/8/5PPP/6K1 b - - 0 27", listOf("a8a1", "d4d1", "a1d1"), "Back Rank", "Rating 1280 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(143, "2Rr3k/1p4pp/4Qp2/4p3/p7/3q1P2/P2r2PP/5R1K w - - 6 31", listOf("e6e8", "d8e8", "c8e8"), "Back Rank", "Rating 1292 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(144, "r3k1nr/pp2npp1/2p4p/4q2P/8/2N1B3/PPPQb1P1/3R2KR w kq - 0 16", listOf("d2d7", "e8f8", "d7d8", "a8d8", "d1d8"), "Back Rank", "Rating 1307 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(145, "4r1k1/p1Q2ppp/1p1P4/2n5/2P5/P1q1B3/5PPP/2R3K1 b - - 2 29", listOf("c3c1", "e3c1", "e8e1"), "Back Rank", "Rating 1318 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(146, "4r1k1/p4ppp/1b3B2/8/1P3N2/P3qP2/3R2PP/3Q3K b - - 0 33", listOf("e3e1", "d1e1", "e8e1"), "Back Rank", "Rating 1332 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(147, "6k1/pRr1rppp/3p4/2p5/2b1n2P/4RPP1/P1P3K1/8 w - - 0 34", listOf("b7b8", "c7c8", "b8c8", "e7e8", "c8e8"), "Back Rank", "Rating 1345 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(148, "3r1rk1/1p3ppp/p1B5/2p5/2b1P3/P1N3Q1/1q3PPP/1R4KR b - - 1 18", listOf("b2b1", "c3b1", "d8d1"), "Back Rank", "Rating 1359 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(149, "3r1rk1/1p3p1p/5Qp1/8/8/2N1R3/PPq2PPP/1R4K1 b - - 2 24", listOf("c2b1", "c3b1", "d8d1", "e3e1", "d1e1"), "Back Rank", "Rating 1374 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(150, "4rrk1/2p4p/p3bqp1/1p1pN3/8/2PQ4/PPB3PP/R1B2NK1 b - - 0 18", listOf("f6f2", "g1h1", "f2f1", "d3f1", "f8f1"), "Back Rank", "Rating 1391 - Find the best sequence of moves!", Color.BLACK)
        )
    }

    private fun getChunk2(): List<Puzzle> {
        return listOf(
            Puzzle(151, "1k1r4/ppp2Q2/7p/2PR4/q1P3p1/P5B1/5PPP/6K1 b - - 0 27", listOf("a4d1", "d5d1", "d8d1"), "Back Rank", "Rating 1408 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(152, "2r3k1/p4rnp/1p2n1pB/qP2P3/8/8/5QPP/5R1K w - - 0 32", listOf("f2f7", "g8h8", "h6g7", "e6g7", "f7f8", "c8f8", "f1f8"), "Back Rank", "Rating 1425 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(153, "2r3k1/P4ppp/8/4P3/8/1r6/2RB1PPP/6K1 b - - 0 31", listOf("b3b1", "d2c1", "b1c1", "c2c1", "c8c1"), "Back Rank", "Rating 1445 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(154, "r5k1/pp1b2np/2n1p3/3pP1q1/2pP4/2P3B1/PP3Q1P/3B1RK1 w - - 0 24", listOf("f2f7", "g8h8", "f7f8", "a8f8", "f1f8"), "Back Rank", "Rating 1463 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(155, "6k1/1p1Q1ppp/p5n1/8/P2Pq3/1P5P/1Br3P1/5R1K w - - 2 26", listOf("d7f7", "g8h8", "f7f8", "g6f8", "f1f8"), "Back Rank", "Rating 1481 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(156, "4r1k1/2p2pp1/p1p2n1p/3PR3/8/1Pq2Q2/P1P2PPP/4R1K1 b - - 0 22", listOf("c3e1", "e5e1", "e8e1"), "Back Rank", "Rating 1502 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(157, "2r3k1/1p3ppp/p3bq2/2Q5/2N5/1P6/2r2PPP/3RR1K1 w - - 10 26", listOf("c5c8", "e6c8", "e1e8"), "Back Rank", "Rating 1524 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(158, "7k/pp1r1rpp/2p5/4nPQ1/4P3/7P/Pq4P1/3R1RK1 w - - 0 24", listOf("g5d8", "d7d8", "d1d8", "f7f8", "d8f8"), "Back Rank", "Rating 1546 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(159, "r1b2k2/pp3ppp/8/3p1q2/n1pp3P/5Q2/PPP2PP1/4R1K1 w - - 0 20", listOf("f3a3", "a4c5", "a3c5", "f8g8", "e1e8"), "Back Rank", "Rating 1568 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(160, "7k/1r4pp/2p1Q1b1/2qp4/6P1/P7/7K/5R2 w - - 0 36", listOf("e6c8", "g6e8", "c8e8", "c5f8", "f1f8"), "Back Rank", "Rating 1592 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(161, "r2r2k1/2p2p2/p1p1bn1p/4N3/2P1Pp2/2N2Q2/P1q1RPPP/1R4K1 b - - 1 18", listOf("c2b1", "c3b1", "d8d1", "e2e1", "d1e1"), "Back Rank", "Rating 1620 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(162, "6k1/1p3rpp/p7/3R2b1/8/3Q4/Pq1r1PPP/4R1K1 w - - 0 24", listOf("d5d8", "g5d8", "e1e8", "f7f8", "d3c4", "d2d5", "c4d5", "g8h8", "e8f8"), "Back Rank", "Rating 2323 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(163, "6k1/q2r1ppp/p3pn2/Q1r5/4P3/5P2/6PP/2RR3K w - - 0 28", listOf("a5d8", "d7d8", "d1d8", "f6e8", "d8e8"), "Back Rank", "Rating 1649 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(164, "3r2k1/p4ppp/8/Q2rq3/2p1P1n1/2N5/PP3PPP/3R2K1 w - - 0 23", listOf("a5d8", "d5d8", "d1d8", "e5e8", "d8e8"), "Back Rank", "Rating 1685 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(165, "k7/pp3R1p/2b2p2/1q3r2/3p2r1/1N3N2/PP3PPP/2R3K1 w - - 0 24", listOf("f7f8", "c6e8", "c1c8"), "Back Rank", "Rating 1729 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(166, "6k1/1b3ppp/p3p3/1p4Q1/1P1r1B2/P1q2P2/6PP/3R2K1 w - - 0 25", listOf("g5d8", "d4d8", "d1d8"), "Back Rank", "Rating 1778 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(167, "6k1/p4p1p/4p1pb/4P3/PN1P4/5N2/r4RPP/6K1 b - - 0 27", listOf("a2a1", "f3e1", "a1e1", "f2f1", "h6e3", "g1h1", "e1f1"), "Back Rank", "Rating 1843 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(168, "3r1bk1/1R5R/1p3p2/6p1/3Nq3/6B1/3Q1PPP/6K1 b - - 0 27", listOf("e4b1", "d2c1", "b1c1"), "Back Rank", "Rating 1930 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(169, "2kr4/ppp3pp/2n4n/6b1/5B2/5QP1/PPP4P/2KNqB1R b - - 2 16", listOf("g5f4", "g3f4", "e1d2", "c1b1", "d2d1", "f3d1", "d8d1"), "Back Rank", "Rating 2040 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(170, "5r2/ppb3k1/6p1/3pq3/2p1P1Q1/2P4R/PP1B2PP/6K1 b - - 0 30", listOf("e5h2", "h3h2", "c7b6", "d2e3", "b6e3", "g1h1", "f8f1"), "Back Rank", "Rating 2576 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(171, "5rk1/p5p1/4p2p/8/3P4/2P3qP/P4RP1/4B2K w - - 0 38", listOf("f2f8", "g8f8", "e1g3"), "Discovered Attack", "Rating 483 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(172, "4rrk1/1pp3p1/p7/4n1B1/5P1P/2P5/1P6/2KRR3 b - - 0 32", listOf("e5d3", "d1d3", "e8e1"), "Discovered Attack", "Rating 760 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(173, "r1b1r1k1/p1p2pp1/7p/8/2pq4/3B1P2/P1P3PP/R2Q1R1K w - - 0 16", listOf("d3h7", "g8h7", "d1d4"), "Discovered Attack", "Rating 812 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(174, "8/5p1k/6p1/5p2/KP2nP2/7r/P3R1N1/8 b - - 0 45", listOf("e4c3", "a4b3", "c3e2"), "Discovered Attack", "Rating 846 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(175, "4r1k1/4qppp/3p4/2pPn3/5P2/2N5/1PP3PP/3QR1K1 b - - 0 21", listOf("e5f3", "g2f3", "e7e1", "d1e1", "e8e1"), "Discovered Attack", "Rating 875 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(176, "8/6k1/4p2p/8/8/2PB2Pn/r7/1R5K b - - 3 30", listOf("h3f2", "h1g2", "f2d3"), "Discovered Attack", "Rating 897 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(177, "2r4k/p4b1p/8/3pP1p1/5n2/1P5P/PB3RPK/8 w - - 0 41", listOf("e5e6", "h8g8", "e6f7"), "Discovered Attack", "Rating 912 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(178, "r1b2rk1/pp3ppp/2nq1n2/8/2p2P2/3B1N1P/PP4P1/R1BQ1RK1 w - - 0 15", listOf("d3h7", "f6h7", "d1d6"), "Discovered Attack", "Rating 927 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(179, "4r3/pp6/4k2p/2Kp2p1/4P3/2P5/P6P/4R3 w - - 0 28", listOf("e4d5", "e6d7", "e1e8"), "Discovered Attack", "Rating 940 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(180, "5rk1/pp3Npp/3p2r1/8/bnN5/8/5RP1/5RK1 w - - 0 27", listOf("f7h6", "g7h6", "f2f8", "g8g7", "f1f7"), "Discovered Attack", "Rating 953 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(181, "r2q1kr1/ppp2p2/2nb4/3Q2P1/8/2PP4/PP2N1P1/RNB1K2R b KQ - 0 20", listOf("d6g3", "e2g3", "d8d5"), "Discovered Attack", "Rating 964 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(182, "4r3/3NPkpp/2r5/3p1p2/P7/6PP/8/4R1K1 w - - 5 44", listOf("d7e5", "f7e7", "e5c6", "e7d6", "e1e8"), "Discovered Attack", "Rating 1011 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(183, "2kr3r/pbq2pp1/1p6/3pn1p1/3Q4/P1P1N3/1P2BPPP/3R1RK1 b - - 1 20", listOf("e5f3", "g2f3", "c7h2"), "Discovered Attack", "Rating 1055 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(184, "r4k1r/pB3pp1/3p4/2q1n1Q1/8/7P/PPP2PP1/2KRR3 b - - 0 18", listOf("e5d3", "d1d3", "c5g5"), "Discovered Attack", "Rating 973 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(185, "8/Q5pk/7p/1R1p2r1/4p1q1/4P3/3K4/2R5 b - - 1 45", listOf("g4g2", "d2c3", "d5d4", "a7d4", "g5b5"), "Discovered Attack", "Rating 982 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(186, "r1b2rk1/p2p1ppp/4p3/3p4/3q1P2/3B4/P1PQ2PP/R4R1K w - - 3 16", listOf("d3h7", "g8h7", "d2d4"), "Discovered Attack", "Rating 990 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(187, "3r4/p1q2p1k/4p1pp/3r4/3QN3/1P3R1P/P4PP1/6K1 w - - 0 32", listOf("e4f6", "h7h8", "f6d5"), "Discovered Attack", "Rating 999 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(188, "1k3r2/ppp5/2n5/4N3/6B1/2P4P/1P3PP1/2Kb4 w - - 0 24", listOf("e5d7", "b8c8", "d7f8", "d1g4", "h3g4"), "Discovered Attack", "Rating 1010 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(189, "6r1/p4p2/2Q3n1/4R2p/2P1Pk2/1P3p1P/P4P2/5BK1 b - - 0 31", listOf("g6e5", "g1h2", "e5c6"), "Discovered Attack", "Rating 1022 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(190, "8/5pp1/2bp2kp/2b5/1PP5/1P3nP1/1B5P/R6K b - - 0 30", listOf("f3e5"), "Discovered Attack", "Rating 1033 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(191, "8/1R4pk/ppr3pp/3bB3/8/7P/PP3PP1/6K1 b - - 1 34", listOf("c6c1", "g1h2", "d5b7"), "Discovered Attack", "Rating 1043 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(192, "rn3rk1/ppp1q1pp/5n2/3pb3/8/5N2/PPP1QPPP/RNB2RK1 b - - 1 12", listOf("e5h2", "f3h2", "e7e2"), "Discovered Attack", "Rating 1053 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(193, "r2q1rk1/1b2bpp1/p2p1n1p/2p1p3/4N3/1PN1PQ2/P2B1PPP/1R1R2K1 w - - 2 20", listOf("e4f6", "e7f6", "f3b7"), "Discovered Attack", "Rating 1063 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(194, "8/2k5/1p1b2p1/1K6/4R2P/1P5B/2r5/8 b - - 5 52", listOf("c2c5", "b5b4", "c5e5", "b4c3", "e5e4"), "Discovered Attack", "Rating 1071 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(195, "3r1b1r/1p1kp3/p1p2pp1/3P3p/2P1p2P/2P1P1B1/1P3PP1/3RK2R w K - 2 19", listOf("d5c6", "d7c6", "d1d8"), "Discovered Attack", "Rating 979 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(196, "r4r2/2kqb1p1/2p4p/3bP3/p4B2/P3Q2P/1PR2PP1/3R2K1 w - - 2 25", listOf("e5e6", "f8f4", "e6d7"), "Discovered Attack", "Rating 1080 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(197, "8/3b1pkp/2p1p1p1/1p2P1q1/1n1P1N2/5P2/3Q1RPP/r2B2K1 w - - 5 28", listOf("f4e6", "d7e6", "d2g5"), "Discovered Attack", "Rating 1088 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(198, "2kr4/pp3p1p/8/2p3p1/3B4/qP3P2/P1P5/1K1R1B2 w - - 0 23", listOf("f1h3", "c8c7", "d4e5", "c7b6", "d1d8"), "Discovered Attack", "Rating 1095 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(199, "5rk1/5p1p/p3pp2/1p2q3/2r1B3/Q1P3P1/PP3P1P/4R1K1 w - - 1 24", listOf("e4h7", "g8h7", "e1e5"), "Discovered Attack", "Rating 1103 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(200, "R7/2p2r1k/3p1bq1/3P3p/4PQ2/2P2N2/1P4P1/6K1 b - - 0 44", listOf("f6d4", "f3d4", "f7f4"), "Discovered Attack", "Rating 1111 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(201, "1r3rk1/2qbbppp/p2p1n2/4p1N1/2p1N3/1P1Q2P1/P3PPBP/1R3RK1 w - - 0 17", listOf("e4f6", "g7f6", "d3h7"), "Discovered Attack", "Rating 1118 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(202, "r2qrk2/pbp3b1/1p4pp/3p1p1n/3P1P1N/2P1P1RQ/PP4PP/R1B3K1 w - - 0 18", listOf("h4g6", "f8g8", "h3h5"), "Discovered Attack", "Rating 1125 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(203, "5rkr/4QNpp/8/3qp3/1P2n3/P7/2P4P/5RK1 w - - 1 30", listOf("f7h6", "g7h6", "f1f8"), "Discovered Attack", "Rating 1133 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(204, "r2q2k1/pp3ppp/6r1/2pNQ3/5P1b/P2P4/BPP4K/R1B2R2 b - - 2 20", listOf("h4g3", "h2g2", "g3f4"), "Discovered Attack", "Rating 1140 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(205, "4r3/5pk1/3p3p/3Qn3/P2BP3/2pq4/7P/R1K5 w - - 0 33", listOf("d4e5", "e8e5", "d5d3"), "Discovered Attack", "Rating 1146 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(206, "r1bq2k1/2npb1pp/2p5/pp2N2P/4PpQ1/P1NP4/1PP2P2/R4K1R b - - 3 19", listOf("d7d6", "e5c6", "c8g4"), "Discovered Attack", "Rating 1152 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(207, "2q1k2r/3rB1pp/p3Qn2/1p6/8/2N4P/PP3PP1/2KR3R b k - 0 19", listOf("d7d1", "c1d1", "c8e6"), "Discovered Attack", "Rating 1158 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(208, "3r1r2/2b2bpk/p2p1p1p/1pp2P2/P3q1NQ/BP1R4/6PP/3R2K1 w - - 0 28", listOf("g4f6", "g7f6", "h4e4"), "Discovered Attack", "Rating 1164 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(209, "r4r2/ppp1qpk1/1n1p1Npp/4P3/7Q/8/PP3PPP/3RK2R w K - 2 20", listOf("f6h5", "g6h5", "h4e7"), "Discovered Attack", "Rating 1170 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(210, "5rk1/5p2/p7/1p2pP2/3pP2q/P2P1p2/1P4K1/2Q3R1 w - - 0 32", listOf("g2f3", "g8h7", "g1h1", "h4h1", "c1h1"), "Discovered Attack", "Rating 1292 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(211, "8/K7/p4Bp1/1p1k2P1/1P6/2P5/8/r7 w - - 10 45", listOf("c3c4", "d5c4", "f6a1"), "Discovered Attack", "Rating 1175 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(212, "6k1/pp4p1/2p5/2Pb1rPp/1P2p3/4RP1P/P3R1K1/8 b - - 0 44", listOf("e4f3", "g2f2", "f3e2"), "Discovered Attack", "Rating 1103 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(213, "7r/pp1qb1p1/2p3k1/4pp1N/8/1QPP1P1R/PP4K1/8 w - - 5 28", listOf("h5f4", "e5f4", "h3h8"), "Discovered Attack", "Rating 1181 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(214, "8/8/5k2/3B1p2/4Pp2/5b1K/5P2/8 w - - 2 51", listOf("e4e5", "f6e5", "d5f3"), "Discovered Attack", "Rating 1187 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(215, "4rbRN/1p1r4/pB5p/2P1kp2/1P2p3/P7/7P/4R1K1 b - - 3 40", listOf("f8c5", "b4c5", "e8g8"), "Discovered Attack", "Rating 1192 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(216, "1nk4r/1p2bppp/pn6/1Np1P3/5B2/5N2/PP4PP/3R2K1 w - - 0 18", listOf("b5a7", "c8c7", "e5e6"), "Discovered Attack", "Rating 1198 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(217, "rnbqkb1r/pp5p/3p1nP1/2pP3Q/2P5/8/PP3PPP/RNB1K1NR w KQkq - 1 10", listOf("g6g7", "f6h5", "g7h8q"), "Discovered Attack", "Rating 1206 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(218, "r2q3k/pbppb1pB/1pn1pr2/7Q/2PP4/2N5/PP3P1P/R3K1NR w KQ - 6 17", listOf("h7g6", "h8g8", "h5h7", "g8f8", "h7h8"), "Discovered Attack", "Rating 1216 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(219, "r1b2rk1/1p3pb1/pq1p3p/2pP3n/P3PPP1/3B4/1PQ1N1PB/1R3RK1 b - - 0 21", listOf("c5c4", "g1h1", "c4d3"), "Discovered Attack", "Rating 1226 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(220, "2kr1b1r/ppp2q1p/2np2p1/3Np3/8/1Q3N2/PP3PPP/R3R1K1 w - - 2 16", listOf("d5b6", "a7b6", "b3f7"), "Discovered Attack", "Rating 1235 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(221, "5rk1/p4p2/1p2pbp1/5q1p/6rP/1QP1P1PB/PP3P2/1R1R2K1 b - - 2 30", listOf("g4g3", "f2g3", "f5h3"), "Discovered Attack", "Rating 1244 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(222, "7r/p4kpp/2Qbqp2/3N4/4P3/8/PP3PPP/4R1K1 b - - 2 28", listOf("d6h2", "g1h2", "e6c6"), "Discovered Attack", "Rating 1253 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(223, "rnb1k2r/pp2bpp1/4p2p/3pP3/2pP4/2P2N2/PqB1QPPP/RN3RK1 w kq - 0 12", listOf("c2a4", "b7b5", "e2b2"), "Discovered Attack", "Rating 1261 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(224, "3r1rk1/2p2p1p/5p2/Q3nq2/8/6B1/PPP2PPP/R3R1K1 b - - 3 19", listOf("e5f3", "g2f3", "f5a5"), "Discovered Attack", "Rating 1269 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(225, "r4r2/pp2qpkB/2pn1n1R/3p4/3P4/8/PPQ2pP1/2K4R w - - 2 24", listOf("h6g6", "f7g6", "c2g6", "g7h8", "h7g8", "f6h5", "h1h5", "e7h7", "h5h7"), "Discovered Attack", "Rating 1467 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk3(): List<Puzzle> {
        return listOf(
            Puzzle(226, "8/7r/5p2/2pQnNkq/Pp1pP3/3P2P1/1P3KB1/7R b - - 8 32", listOf("e5d3", "f2g1", "h5d1", "g2f1", "h7h1", "g1h1", "d1f1"), "Discovered Attack", "Rating 1278 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(227, "r3k2r/pp1n1ppp/2pbp3/q5P1/2BP1n1P/P1N5/1PbBQP1N/R4RK1 w kq - 5 16", listOf("d2f4", "d6f4", "e2c2", "f4h2", "g1h2"), "Discovered Attack", "Rating 1286 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(228, "1r3b1r/pp3Q1p/1kpp4/1B1P2p1/5p2/5P2/PP4KP/R1Bq4 w - - 4 25", listOf("c1e3", "f4e3", "a1d1"), "Discovered Attack", "Rating 1295 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(229, "5rk1/3b2b1/1q4pp/1P2pn2/2P1B3/Q7/6PP/2R1NR1K b - - 0 33", listOf("f5g3", "h2g3", "f8f1", "h1h2", "b6g1"), "Discovered Attack", "Rating 1303 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(230, "rn2kb1r/pp3ppp/4b3/5q2/2pPN3/3B1N2/PP3PPP/R2QK2R w KQkq - 0 11", listOf("e4d6", "f8d6", "d3f5"), "Discovered Attack", "Rating 1311 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(231, "4r3/p4p2/1pr1pkp1/3P3p/3RKP2/1P4PP/P4P2/4R3 b - - 0 29", listOf("e6d5", "e4f3", "e8e1"), "Discovered Attack", "Rating 1450 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(232, "5rk1/1R4p1/8/1P3n1p/2P2Qq1/5NP1/4PPK1/8 b - - 1 27", listOf("f5h4", "f3h4", "f8f4"), "Discovered Attack", "Rating 1319 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(233, "4r3/1k2r3/2p3pp/2ppb3/6P1/1P2R1BP/P1P5/2K1R3 b - - 1 30", listOf("e5b2", "c1b2", "e7e3"), "Discovered Attack", "Rating 1327 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(234, "2r1r1k1/3b1p1p/4pQpB/q2pP1P1/8/1pP4P/1P6/1BKR3R b - - 4 30", listOf("c8c3", "c1d2", "c3f3", "d2e2", "f3f6"), "Discovered Attack", "Rating 1335 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(235, "r7/p7/4p3/K1pb4/2kP1Q2/7R/P7/3q4 w - - 2 42", listOf("d4c5", "d5e4", "f4e4"), "Discovered Attack", "Rating 1342 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(236, "r1bqr1k1/pppn1pbp/5np1/4B3/2P5/2N2NP1/PP2PPBP/R2Q1RK1 b - - 0 10", listOf("d7e5", "f3e5", "d8d1", "a1d1", "e8e5"), "Discovered Attack", "Rating 1350 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(237, "1k1r4/1pp2q2/p4p2/4rQp1/7p/P1N2R1P/1P4PK/8 w - - 4 33", listOf("f5e5", "f6e5", "f3f7"), "Discovered Attack", "Rating 1359 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(238, "r4r1k/1p5B/p3bq1Q/3p4/7b/2P2P2/PP4P1/RN4K1 w - - 1 29", listOf("h7g6", "h8g8", "h6h7"), "Discovered Attack", "Rating 1367 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(239, "r4rk1/2p1qpb1/p2p1np1/1p3bBp/3pN3/3P1P2/PPP3PP/R2KQB1R w - - 0 17", listOf("e4f6", "g7f6", "e1e7", "f6e7", "g5e7"), "Discovered Attack", "Rating 1375 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(240, "r1bqk2r/pppnbppp/5n2/3N2B1/3P4/5Q2/PPP2PPP/R3KBNR b KQkq - 0 7", listOf("f6d5", "f3d5", "e7g5"), "Discovered Attack", "Rating 1383 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(241, "r1b2rk1/1p5p/p4p2/8/5Q2/1Pp3K1/6PP/3q1B1R w - - 0 29", listOf("f1c4", "g8h8", "h1d1"), "Discovered Attack", "Rating 1391 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(242, "5rk1/1p3p2/p2p3Q/3P2r1/2P5/1P2q3/P4R2/6RK b - - 5 39", listOf("g5g1", "h1g1", "e3h6"), "Discovered Attack", "Rating 1399 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(243, "2kr1b1r/ppp1ppp1/2n1b3/2N4p/3qNPP1/2P5/PP1P2P1/R1BQ1R1K b - - 0 17", listOf("h5g4"), "Discovered Attack", "Rating 1406 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(244, "rn2k1nr/ppp3p1/7p/4qNQ1/3p4/3P4/bPP1PPPP/R3KB1R w KQkq - 0 12", listOf("f5g7", "e8f8", "g5e5"), "Discovered Attack", "Rating 1415 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(245, "4r1k1/pp3pp1/2p1P2p/7b/2B5/2P2P2/P2r2PP/4R1K1 w - - 0 22", listOf("e6f7", "h5f7", "e1e8", "g8h7", "c4f7"), "Discovered Attack", "Rating 1333 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(246, "r2q1r2/pp3ppk/8/3Pp1b1/1n4bP/2N1P3/PP4P1/R2QK2R w KQ - 0 16", listOf("h4g5", "h7g6", "d1g4"), "Discovered Attack", "Rating 1423 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(247, "5rk1/3q1ppp/2n1p3/1R1P4/2pP4/2P2B1P/2Q2PP1/6K1 b - - 0 20", listOf("c6d4", "c3d4", "d7b5"), "Discovered Attack", "Rating 1432 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(248, "5rk1/pppq2pp/3p4/5NQ1/4n1P1/2P4P/PPP5/2K2R2 w - - 1 21", listOf("f5h6", "g8h8", "f1f8"), "Discovered Attack", "Rating 1555 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(249, "3qrbk1/1b3ppp/1p3n2/3p1P2/1PrQ4/2N2B2/1BP3PP/3R1R1K w - - 3 19", listOf("d4c4", "d5c4", "d1d8", "e8d8", "f3b7"), "Discovered Attack", "Rating 1440 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(250, "r1b1kb1r/ppp2ppp/1q6/3P4/3n2n1/2N4P/PP1B1PP1/1R1QKBNR b Kkq - 0 11", listOf("d4c2", "d1c2", "b6f2", "e1d1", "f2f1"), "Discovered Attack", "Rating 1619 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(251, "1r4k1/3bp3/p5p1/1ppPq3/8/1P1Pp2P/P3QrB1/1R2R2K w - - 2 32", listOf("e2f2", "e3f2", "e1e5"), "Discovered Attack", "Rating 1448 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(252, "5qk1/1p5p/p1p2pp1/P3p3/1P2Pn2/2P2PP1/3Q3K/7R b - - 0 32", listOf("f8h6", "h2g1", "f4h3", "h1h3", "h6d2"), "Discovered Attack", "Rating 1456 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(253, "1r5r/2p3k1/3pn1p1/4p1q1/1p2P3/1P2QPN1/2P5/2KR2R1 w - - 0 29", listOf("g3f5", "g7f6", "g1g5"), "Discovered Attack", "Rating 1464 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(254, "r6r/p2nkp1p/3bp1p1/8/3PQ1nq/1B2B2P/PPP5/RN1K2NR b - - 0 15", listOf("g4f2", "e3f2", "h4e4"), "Discovered Attack", "Rating 1385 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(255, "6rk/6qp/p1Np1rnb/Pp1P1p2/1Q3P2/6PR/1P3KB1/7R b - - 2 30", listOf("g6f4", "g3f4", "g7g2"), "Discovered Attack", "Rating 1472 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(256, "3r3k/6p1/4Q2p/2q5/3p4/2P2P2/1P1KR1P1/2R5 b - - 0 33", listOf("d4c3", "d2c2", "c3b2", "c2b2", "d8b8", "e6b3", "b8b3"), "Discovered Attack", "Rating 1628 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(257, "r1b3k1/1pp3pp/p1pN4/5qN1/3P1n2/2P2QP1/PP3P1P/4R1K1 b - - 0 19", listOf("f4h3", "g5h3", "f5f3"), "Discovered Attack", "Rating 1480 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(258, "6r1/7p/R3p3/1p1bBp2/2k5/2P3PP/1r6/3R2K1 w - - 8 37", listOf("d1d4", "c4b3", "d4b4", "b3c2", "b4b2", "c2b2", "c3c4"), "Discovered Attack", "Rating 1489 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(259, "2R2r1k/1p4pp/4Qp1n/1B2n3/4q1P1/4P2P/PP3P2/5RK1 b - - 0 23", listOf("e5f3", "g1h1", "e4e6", "c8f8", "h6g8"), "Discovered Attack", "Rating 1498 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(260, "6k1/1p3r1p/2p3p1/p1P5/2Q2Br1/P3P3/1q5P/1N3R1K w - - 2 27", listOf("c4f7", "g8f7", "f4e5", "f7e6", "e5b2"), "Discovered Attack", "Rating 1506 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(261, "r4rk1/p4ppp/2p1bn2/3p4/8/2NB1Q1P/PqP2PP1/3R1RK1 w - - 0 16", listOf("d1b1", "b2c3", "d3h7", "f6h7", "f3c3"), "Discovered Attack", "Rating 1676 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(262, "8/4p1k1/b2p1ppp/1q1P4/1pQ1PPP1/1P6/6P1/R5K1 b - - 0 29", listOf("b5b6", "g1h2", "a6c4"), "Discovered Attack", "Rating 1515 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(263, "5r2/2p1p2k/p1q2bp1/7p/2p1R2P/5QP1/PP3P2/2B3K1 w - - 1 25", listOf("e4e7", "f6e7", "f3c6"), "Discovered Attack", "Rating 1523 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(264, "8/6R1/3p4/2kNp3/p1P5/3K4/1b6/8 b - - 1 38", listOf("e5e4", "d3c2", "b2g7"), "Discovered Attack", "Rating 1532 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(265, "8/7k/8/4N1R1/5p2/5P1P/6P1/1r3nK1 b - - 0 49", listOf("f1g3", "g1f2", "b1f1"), "Discovered Attack", "Rating 1540 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(266, "2r5/1p1Q1pbk/p5pp/4pq2/7P/1P3N2/1P4P1/5R1K w - - 2 28", listOf("f3g5", "h6g5", "f1f5", "c8c1", "h1h2"), "Discovered Attack", "Rating 1547 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(267, "r1b1kb1r/p1p1p1pp/2N5/3n1p2/3Pq3/4B3/PPP1QP1P/RN2KR2 b Qkq - 0 11", listOf("d5e3", "e2e3", "e4c6"), "Discovered Attack", "Rating 1555 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(268, "2r1r1k1/3Q1ppp/8/2b2N2/8/8/P3qPPP/2R2RK1 b - - 3 29", listOf("c5f2", "g1h1", "c8c1"), "Discovered Attack", "Rating 1563 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(269, "3r2k1/p4ppp/2p5/2N5/2Pq4/1P1n1P2/P3Q1PP/3R3K b - - 5 28", listOf("d3f2", "e2f2", "d4d1"), "Discovered Attack", "Rating 1571 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(270, "8/pp4bp/3kp1p1/8/8/Pb1N4/1P4rP/1K1RR3 w - - 0 37", listOf("d3f4", "b3d1", "e1d1", "d6e5", "f4g2"), "Discovered Attack", "Rating 1579 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(271, "r4r2/7k/3p1p1P/3p3P/b4P2/1NQ1B3/PP5q/1K6 w - - 0 32", listOf("c3c7", "h7h6", "f4f5", "h6h5", "c7h7", "h5g4", "h7h2"), "Discovered Attack", "Rating 1747 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(272, "5R2/6k1/4p3/3bP3/PP1B2Bp/2P3rP/6PK/8 b - - 0 40", listOf("g3g2", "h2h1", "g2g4", "h1h2", "g4g2"), "Discovered Attack", "Rating 1587 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(273, "2rqk3/pp1nbpp1/1n2p1p1/2ppP1Nr/3P1B2/2P2PQ1/PP1N2PP/R4RK1 w - - 2 16", listOf("g5e6", "f7e6", "g3g6", "e8f8", "g6h5"), "Discovered Attack", "Rating 1595 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(274, "r3r3/p3b2p/1np1B2k/1p5P/3q1P2/8/PPP1Q3/2K3RR w - - 0 24", listOf("g1g6", "h7g6", "h5g6", "h6g7", "h1h7"), "Discovered Attack", "Rating 1763 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(275, "r3k1nr/ppp5/3pp2p/4n1q1/4PR2/2N1Q3/PPP3P1/5RK1 w kq - 0 17", listOf("f4f8", "e8d7", "e3g5", "h6g5", "f8a8"), "Discovered Attack", "Rating 1602 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(276, "6r1/4q1pk/4p1Rp/p2nP2P/2pP1rQ1/6R1/P7/2K5 w - - 2 35", listOf("g6h6", "g7h6", "g4g8"), "Discovered Attack", "Rating 1610 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(277, "r3r1k1/bp3pp1/p1p4p/3pq3/1P6/P1Q1B2P/5PP1/2R1R1K1 w - - 0 26", listOf("e3a7", "e5c3", "e1e8", "a8e8", "c1c3"), "Discovered Attack", "Rating 1752 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(278, "r1b1k1nr/ppp2p1p/3p3b/8/2BNPppq/2NP4/PPPK2PP/R1BQ3R b kq - 0 10", listOf("f4f3"), "Discovered Attack", "Rating 1617 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(279, "r5k1/p1rnqppp/1p1np3/1B1p4/Q2P4/PPN1P1P1/5PP1/2RR2K1 w - - 1 21", listOf("c3d5", "e6d5", "c1c7", "d6b5", "a4b5"), "Discovered Attack", "Rating 1625 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(280, "3rB3/1p1Pk3/2p5/2P5/1Pb2R2/5P1p/6rP/3RK3 b - - 3 38", listOf("g2e2", "e1f1", "e2e4", "f1f2", "e4f4"), "Discovered Attack", "Rating 1633 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(281, "r1b2Q2/p1pp1N1p/1pk5/8/3q4/2N2P2/PP1P2PP/n1B1RK2 b - - 1 20", listOf("c8a6", "e1e2", "a8f8"), "Discovered Attack", "Rating 1744 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(282, "r1b2rk1/2qn1pbp/p2p1np1/1p6/2B1N3/5N2/PBQ2PPP/R3R1K1 w - - 0 15", listOf("c4f7", "f8f7", "c2c7"), "Discovered Attack", "Rating 1642 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(283, "2b3k1/2q2rp1/p6p/1p1p1R2/3P2Q1/6PB/PPr4P/5RK1 w - - 0 25", listOf("f5f7", "c7f7", "g4c8", "c2c8", "f1f7"), "Discovered Attack", "Rating 1650 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(284, "r6r/ppR3pk/5pp1/3p3n/3P4/2N1BqPK/PP2QP2/6R1 b - - 4 26", listOf("h5f4", "e3f4", "h7g8", "f4h6", "h8h6"), "Discovered Attack", "Rating 1763 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(285, "1kr5/pp3p1p/4pP2/2N2n2/3r4/4p3/P6P/1RR4K w - - 8 27", listOf("c5a6", "b8a8", "c1c8"), "Discovered Attack", "Rating 1658 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(286, "2kr3r/1pq2pp1/p2bpnp1/P1P5/8/2P1BNN1/5PPP/R2Q1RK1 b - - 0 16", listOf("d6g3", "h2g3", "d8d1"), "Discovered Attack", "Rating 1667 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(287, "r1b1nrk1/8/pp1p2pb/2pP1p2/1PP1PpP1/P1N2BP1/3Q1P1K/R4R2 b - - 0 22", listOf("f4g3", "f2g3", "h6d2"), "Discovered Attack", "Rating 1676 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(288, "2rr2k1/1p5p/p1n1qR2/3p2p1/1P1B4/P3P1P1/6PP/2RQ2K1 b - - 0 25", listOf("c6d4", "f6e6", "c8c1", "d1c1", "d4e2", "g1f2", "e2c1"), "Discovered Attack", "Rating 1792 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(289, "4rrk1/p4pp1/1p2p1q1/3pRn2/3P2N1/2P2PP1/PPQ3K1/4R3 b - - 6 27", listOf("f5h4", "g3h4", "g6c2"), "Discovered Attack", "Rating 1684 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(290, "4r1k1/1pp4p/3p1Qp1/3Pq3/2P1r1bP/4NpP1/P4P2/4RRK1 w - - 4 32", listOf("e3g4", "e4g4", "e1e5"), "Discovered Attack", "Rating 1694 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(291, "3r1b1r/2pkn3/R1n2p2/1q6/2NP4/5N2/4QPKB/8 w - - 0 35", listOf("c4e5", "f6e5", "e2b5"), "Discovered Attack", "Rating 1703 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(292, "r4rk1/1pp2p2/1pn1b1n1/4p1N1/2B5/2PP4/PP1K1P1P/R5R1 w - - 1 19", listOf("g5e6", "f7e6", "g1g6"), "Discovered Attack", "Rating 1713 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(293, "5rk1/5ppp/4p3/8/Q7/1N5P/2p3P1/3q1RK1 b - - 0 34", listOf("c2c1q", "b3c1", "d1a4"), "Discovered Attack", "Rating 1723 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(294, "5nk1/qR5p/b1Bbp1pP/Q2p1p2/3P4/4BP2/3NnKP1/8 b - - 8 39", listOf("d6g3", "f2f1", "e2d4"), "Discovered Attack", "Rating 1734 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(295, "3r1rk1/pp1bR1bp/1q1p1np1/3P2B1/6P1/7P/PPBQN2K/5R2 b - - 2 26", listOf("f6g4", "h3g4", "f8f1", "e7g7", "g8g7"), "Discovered Attack", "Rating 1743 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(296, "6k1/p3q2p/6p1/5p2/2P1n3/4QPP1/P2r3P/4R1K1 w - - 3 33", listOf("e3d2", "e4d2", "e1e7"), "Discovered Attack", "Rating 1754 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(297, "3r4/ppkr1p1p/2n3p1/2b5/3Np3/2P1B3/P2R1PPP/3R2K1 w - - 0 20", listOf("d4b5", "c7c8", "d2d7", "d8d7", "d1d7", "c8d7", "e3c5"), "Discovered Attack", "Rating 1765 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(298, "3r2k1/1p4pp/2p5/p2n1P2/P1N2P2/2PK2P1/1P5P/5R2 b - - 0 30", listOf("d5b6", "d3e2", "b6c4"), "Discovered Attack", "Rating 1776 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(299, "r3kb1r/pppb1ppp/2np1n2/8/3q4/2NBN3/PPP2PPP/R1BQK2R w KQkq - 2 9", listOf("c3b5", "d4b6", "e3c4", "b6b5", "c4d6", "f8d6", "d3b5"), "Discovered Attack", "Rating 1786 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(300, "r3k2r/p2pbppp/b3pn2/1N6/q3P3/2P2N2/2nPQPPP/1RBK1B1R w kq - 0 14", listOf("b5c7", "e8f8", "e2a6", "c2e3", "d1e2", "a4a6", "c7a6"), "Discovered Attack", "Rating 1798 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk4(): List<Puzzle> {
        return listOf(
            Puzzle(301, "2kr1r2/2p4p/1pn1p1p1/3qN3/Q7/P7/KP1N2PP/3R4 w - - 2 27", listOf("d2c4", "c6b8", "d1d5"), "Discovered Attack", "Rating 1809 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(302, "8/2R2N2/2n1p1r1/3k1p2/3P1K2/2r1P3/6P1/4R3 b - - 5 35", listOf("g6g4", "f4f3", "c6d4", "f3f2", "c3c7"), "Discovered Attack", "Rating 1921 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(303, "r2q2k1/1p4Pp/2b3p1/p2pP3/8/P1B5/1PP4P/R4QK1 b - - 0 20", listOf("d8g5", "f1g2", "g5g2", "g1g2", "d5d4", "g2f2", "d4c3"), "Discovered Attack", "Rating 1818 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(304, "r2r2k1/1q2bppp/p3p3/1p2P3/3PN3/1P2n3/PB4PP/R2Q1R1K w - - 3 20", listOf("d1f3", "e3f1", "e4f6", "g7f6", "f3b7"), "Discovered Attack", "Rating 1976 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(305, "rb3rk1/pbq2ppp/1p3n2/3pn3/3N4/PPN1PB2/1B2QPPP/R4RK1 b - - 8 16", listOf("e5f3", "d4f3", "d5d4", "c3b5", "b7f3", "b5c7", "f3e2"), "Discovered Attack", "Rating 1828 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(306, "8/5pk1/8/3b2p1/1R1P2Pp/5r1P/6B1/6K1 b - - 7 56", listOf("f3g3", "b4b2", "d5g2", "b2g2", "g3h3"), "Discovered Attack", "Rating 1841 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(307, "1r3r2/p5p1/4pnk1/3b4/3q1P1Q/4R2R/P1P3PP/6K1 w - - 0 27", listOf("f4f5", "e6f5", "h4d4", "b8b1", "g1f2"), "Discovered Attack", "Rating 1854 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(308, "r7/p7/kq6/1p3Q2/1K1P4/2P2N2/PP6/8 b - - 4 41", listOf("b6a5", "b4c5", "b5b4", "c5c4", "a5f5"), "Discovered Attack", "Rating 1760 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(309, "r5k1/1b1nq1p1/pp3r1p/2ppP3/7N/P1PBP3/1P2Q1PP/R4RK1 b - - 0 20", listOf("f6f1", "e2f1", "e7h4", "f1f5", "d7f8"), "Discovered Attack", "Rating 1867 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(310, "r3r1k1/1p3p1p/1q4pQ/2p1n3/1PPnN3/6PP/5PB1/1R3RK1 b - - 2 24", listOf("e5f3", "g1h1", "e8e4"), "Discovered Attack", "Rating 1880 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(311, "4r3/4ppk1/3p2p1/p2P2QP/Pp1q4/1P4R1/4rPP1/5RK1 w - - 3 33", listOf("h5g6", "e2e5", "g6f7", "e5g5", "f7e8q"), "Discovered Attack", "Rating 1960 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(312, "r4rk1/4q1pp/pp1p4/3bp3/3N4/8/PPP2PPP/R2Q1RK1 w - - 0 17", listOf("d4f5", "e7f7", "d1d5", "f7d5", "f5e7", "g8f7", "e7d5"), "Discovered Attack", "Rating 1893 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(313, "r3r1k1/1ppq3p/1n1p2p1/p2Pb3/P3B2Q/2N5/1PP5/2K1R2R w - - 4 27", listOf("e4g6", "h7g6", "e1e5"), "Discovered Attack", "Rating 1905 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(314, "5r1k/pp4b1/2p5/7p/8/2P2q1P/PP2QP1K/3R1B2 b - - 4 35", listOf("f3e2", "f1e2", "f8f2"), "Discovered Attack", "Rating 1998 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(315, "4r2r/p2n2bk/1pqpQ1pp/8/1P2N3/2P5/P5PP/4RR1K w - - 3 28", listOf("e4g5", "h6g5", "e6h3", "h7g8", "e1e8"), "Discovered Attack", "Rating 1915 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(316, "8/8/8/ppr1pR2/5P2/1PPK3k/8/8 b - - 0 51", listOf("e5e4", "d3e4", "c5f5", "e4f5", "a5a4", "b3a4", "b5a4", "f5g6", "a4a3", "f4f5", "a3a2", "f5f6", "a2a1q"), "Discovered Attack", "Rating 1992 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(317, "2kr4/pp1q1pp1/2pPp1p1/2P5/1P1Q1n1r/6N1/P4PPP/R3R1K1 b - - 4 18", listOf("f4h3", "g2h3", "h4d4"), "Discovered Attack", "Rating 1926 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(318, "5rk1/pp4p1/1bp4q/5B1p/8/PbNQr1P1/1P5P/2R2R1K w - - 10 28", listOf("f5h7", "h6h7", "f1f8", "g8f8", "d3h7"), "Discovered Attack", "Rating 1937 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(319, "2r5/3k4/2p1ppp1/1rRp4/pP1P1KpP/P1R1P3/5P2/8 w - - 0 40", listOf("c5b5", "c6b5", "c3c8", "d7c8", "f4g4", "c8c7", "h4h5"), "Discovered Attack", "Rating 1948 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(320, "r1b2rk1/pp1p1p2/2n1p3/6pN/1P1bP3/P7/2PQ1P1q/2KR1B2 w - - 0 23", listOf("d2g5", "g8h7", "d1d4", "h2e5", "h5f6", "e5f6", "g5f6"), "Discovered Attack", "Rating 1959 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(321, "r3k2r/1bq3pp/p3N3/1p2n3/1bp3n1/2N5/PPB2PPP/R1BQR1K1 b kq - 0 17", listOf("e5f3", "d1f3", "c7h2", "g1f1", "b7f3"), "Discovered Attack", "Rating 1971 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(322, "r2qr1k1/ppp1bppp/8/6PQ/3nB3/4B2P/PPP2P2/R3K2R b KQ - 1 14", listOf("e7b4", "c2c3", "e8e4"), "Discovered Attack", "Rating 2068 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(323, "4r1k1/3r1ppp/p7/2P2Rq1/2Nn4/P2QN2P/6P1/6KR b - - 6 27", listOf("d4f3", "g1f2", "d7d3", "f5g5", "f3g5"), "Discovered Attack", "Rating 1983 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(324, "5r1k/1pQ3pp/6b1/PP2p3/8/4P3/3r2PP/4R1K1 b - - 0 29", listOf("g6e4", "c7e5", "d2g2", "g1h1", "g2g4", "e5e4", "g4e4"), "Discovered Attack", "Rating 1994 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(325, "r2q1knr/ppp3pp/1b1p2n1/4PbNQ/5P2/2Pp4/PP4PP/RNB2R1K w - - 2 13", listOf("g5h7", "h8h7", "h5f5"), "Discovered Attack", "Rating 2007 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(326, "5rk1/p5p1/1p1pqr1p/2p1nR2/2P4P/2PBP1P1/P1Q3P1/5RK1 b - - 0 25", listOf("e5d3", "f5f6", "e6e3", "g1h2", "f8f6", "f1f6", "g7f6"), "Discovered Attack", "Rating 2022 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(327, "3rkbr1/p3p2p/2p1Np2/5Q2/P7/N7/1P3KPP/R1B4q w - - 2 22", listOf("e6c7", "e8f7", "f5e6", "f7g7", "c1h6"), "Discovered Attack", "Rating 2036 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(328, "r2q1rk1/pp2b1p1/2ppp2p/7Q/4n3/2P3RP/PP4P1/R1B2NK1 w - - 0 16", listOf("g3g7", "g8g7", "c1h6", "g7h8", "h6f8"), "Discovered Attack", "Rating 1969 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(329, "1k1r3r/p2b1ppp/1p2p3/q2pn3/3N4/8/PQ2B1PP/1RR4K w - - 0 25", listOf("d4b3", "a5a4", "b2e5"), "Discovered Attack", "Rating 2051 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(330, "5rk1/Q1p2ppp/1r6/2bBp2b/4N1q1/2PP1P2/PP4PP/R1B1K2R b KQ - 0 18", listOf("g4g2", "h1f1", "b6b2", "c1b2", "c5a7"), "Discovered Attack", "Rating 2066 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(331, "8/pp4k1/2q3pp/2p1R1r1/3P3Q/3P1r1P/PP4P1/4R1K1 b - - 0 27", listOf("f3f1", "e1f1", "c6g2"), "Discovered Attack", "Rating 2080 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(332, "1k2rr2/1p1q4/p1p1bb1p/6p1/1Q1pB3/3P1N1P/PPP3P1/4RRK1 w - - 0 24", listOf("f3e5", "f6e5", "f1f8", "e8f8", "b4f8"), "Discovered Attack", "Rating 2093 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(333, "r5k1/1p1Prppp/p1n5/q1p2bB1/8/2PBP3/PPQ2PPP/R3K2R b KQ - 0 17", listOf("f5d3", "c2d3", "c5c4", "d3c4", "a5g5"), "Discovered Attack", "Rating 2110 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(334, "r2q2kr/1p1bb1pp/p1np1n2/6N1/4PB2/8/PP2QPPP/2RR2K1 w - - 2 16", listOf("e2c4", "d6d5", "d1d5", "d8e8", "d5d7"), "Discovered Attack", "Rating 2129 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(335, "2r1rbk1/p5p1/6p1/8/2pBp2q/2Q2P1P/PPP2P2/3RR1K1 b - - 0 27", listOf("e4f3", "c3f3", "e8e1", "d1e1", "h4d4"), "Discovered Attack", "Rating 2148 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(336, "4r3/pp2r3/2n1qkBQ/3p4/3P4/5P2/PP5P/7K w - - 0 33", listOf("g6h5", "f6f5", "h5g4"), "Discovered Attack", "Rating 2167 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(337, "r3r1k1/1p4pb/p2bNp2/7q/3p1p1B/1P1P1R1P/1PPQ4/6RK w - - 4 29", listOf("g1g7", "g8h8", "h4f6", "e8e6", "g7g5", "e6f6", "g5h5"), "Discovered Attack", "Rating 2186 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(338, "r6r/p2kp1Qp/2pp1n2/1p6/8/2N5/Pq3PPP/4R1K1 w - - 0 19", listOf("e1e7", "d7d8", "g7f6", "b2a1", "e7e1"), "Discovered Attack", "Rating 2252 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(339, "6k1/5ppp/p3q3/3Nn3/2r2P2/4Q1P1/P6P/2R3K1 b - - 0 25", listOf("e5f3", "g1f2", "e6e3", "f2e3", "c4c1"), "Discovered Attack", "Rating 2206 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(340, "2r1r1k1/RR4pp/8/8/8/3K1BPP/3p2P1/2b5 b - - 4 46", listOf("e8e3", "d3e3", "d2d1q"), "Discovered Attack", "Rating 2228 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(341, "3r3r/2kb4/p3p3/1p2q3/1P1RB3/7P/P2Q2P1/1K5R b - - 6 37", listOf("d7c6", "d2c3", "d8d4", "c3c6", "c7d8"), "Discovered Attack", "Rating 2253 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(342, "6k1/p4p1p/1p2p2Q/2p1P1Pp/2P2R2/2r5/P2q2PK/8 w - - 2 36", listOf("f4f7", "g8f7", "g5g6", "h7g6", "h6d2"), "Discovered Attack", "Rating 2279 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(343, "r3kr2/1pqb4/p1np4/2p1p1b1/P1B2N1p/3PQ2P/1PP1NR2/RK6 w q - 2 23", listOf("f4d5", "e8c8", "e3g5", "f8f2", "d5c7"), "Discovered Attack", "Rating 2307 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(344, "1b6/p7/3r2k1/P1p3rp/4R3/1PBP4/2P4K/4R3 b - - 7 40", listOf("d6d3", "c3e5", "g5e5", "e4e5", "d3d5", "h2h1", "d5e5"), "Discovered Attack", "Rating 2405 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(345, "8/1R2P3/5qbk/3Q1pp1/8/P7/P4P2/1K6 b - - 0 39", listOf("f5f4", "b1c1", "f6c3", "c1d1", "g6h5", "f2f3", "h5f3", "d5f3", "c3f3"), "Discovered Attack", "Rating 2336 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(346, "4Q3/1p4pp/4p2r/3b4/5Pk1/1P4Pq/P3PR1P/2R3K1 b - - 2 36", listOf("h3g3", "g1f1", "h6h2", "f2h2", "g3f4", "f1e1", "f4c1"), "Discovered Attack", "Rating 2335 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(347, "5rk1/1b5p/1q4p1/pp1pn3/2p4P/1PP3P1/P1B1RPK1/R2Q4 b - - 1 24", listOf("d5d4", "c2e4", "d4d3", "e4b7", "b6b7"), "Discovered Attack", "Rating 2381 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(348, "6k1/2p2q2/3p3p/2PPnPpP/1p1Q4/1B6/1KP5/8 w - - 1 37", listOf("d4e5", "d6e5", "d5d6", "c7d6", "b3f7", "g8f7", "c5c6"), "Discovered Attack", "Rating 2442 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(349, "8/8/8/1P1k4/P3p3/7r/1R1K4/8 b - - 2 51", listOf("e4e3", "d2d3", "e3e2", "d3d2", "h3h1", "d2e2", "h1h2", "e2d3", "h2b2"), "Discovered Attack", "Rating 2533 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(350, "rn1k2nr/p4ppp/8/4Q3/6Pq/2P4B/P1P1PP2/R3K1Nb w Q - 1 14", listOf("a1d1", "b8d7", "g4g5", "h1c6", "h3d7", "c6d7", "e5d5"), "Discovered Attack", "Rating 2945 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(351, "rn1k2nr/p4ppp/8/4Q3/6Pq/2P4B/P1P1PP2/R3K1Nb w Q - 1 14", listOf("a1d1", "b8d7", "g4g5", "h1c6", "h3d7", "c6d7", "e5d5"), "Discovered Attack", "Rating 2945 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(352, "5rr1/p3k3/8/2N1pn2/1PP4p/3PRK2/P2R2PP/8 b - - 0 28", listOf("f5g3"), "Discovered Attack", "Rating 2432 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(353, "3r3k/3q4/3r1P1p/4Q3/1pp5/6P1/7P/3R2K1 w - - 0 40", listOf("f6f7", "h8h7", "d1d6", "d7a7", "g1g2", "a7a8", "g2h3", "d8d6", "e5e7", "a8c8", "g3g4"), "Discovered Attack", "Rating 2888 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(354, "r1bq2k1/pp4pp/1bn2p2/3R4/1PB4B/P4N2/4rPPP/R5K1 w - - 0 17", listOf("d5d8"), "Double Check", "Rating 536 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(355, "8/1b3p1p/p2r1k2/6p1/4P3/P1PpRPP1/7P/4K3 w - - 4 34", listOf("e4e5", "f6e7", "e5d6"), "Double Check", "Rating 776 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(356, "rnbqkbnr/p3pppp/8/1N6/Q1pp4/8/PP2PPPP/R1B1KBNR w KQkq - 0 6", listOf("b5c7"), "Double Check", "Rating 817 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(357, "r1b3k1/pp4bp/5qp1/n1BR4/8/1Q6/PP3PPP/5RK1 w - - 1 24", listOf("d5d8"), "Double Check", "Rating 854 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(358, "rnbk1b1r/1pp2p1p/p1q1p1p1/4P3/8/5N2/3B1PPP/3RKB1R w K - 0 15", listOf("d2g5", "d8e8", "d1d8"), "Double Check", "Rating 886 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(359, "2k1r1nN/1pp1b1p1/p7/8/6b1/8/PPB2PPP/R1B1K2R b KQ - 0 16", listOf("e7b4", "e1f1", "e8e1"), "Double Check", "Rating 915 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(360, "3qkb1r/1rpNnppp/4p3/pB6/P2PP3/2P5/5PPP/R1B1K2R w KQk - 0 14", listOf("d7f6"), "Double Check", "Rating 936 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(361, "r2qr1k1/pb3Rpp/2p5/2p1p3/4P3/1BNP2nP/PPP3P1/5RK1 w - - 0 18", listOf("f7f8"), "Double Check", "Rating 959 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(362, "r3k2r/ppp2p2/2p5/2b3B1/4P1P1/2NP4/PPP4p/R2Q2RK b kq - 0 18", listOf("h2g1q"), "Double Check", "Rating 977 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(363, "8/1p2k3/1K6/3PN3/1P5r/2n4p/8/4R3 w - - 6 48", listOf("e5g6", "e7d6", "g6h4"), "Double Check", "Rating 992 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(364, "r3n1k1/p4Nbp/1p1P1qp1/3Q1b2/1Pn5/B6P/5PP1/2R3K1 w - - 1 31", listOf("f7h6", "g8h8", "d5g8"), "Double Check", "Rating 1036 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(365, "2r2b1r/kp2pppp/1n1p4/3Pq3/P3P3/4Q2P/1P3PP1/R4RK1 w - - 0 20", listOf("a4a5", "c8c5", "a5b6"), "Double Check", "Rating 1058 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(366, "4nk2/p4Pp1/2pN2bp/8/2P4q/1P6/P3B1P1/5RK1 w - - 0 37", listOf("f7e8q"), "Double Check", "Rating 1065 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(367, "r2qkb1r/3p1ppp/p3Pn2/8/Np6/1B3b2/PP3PPP/R1B1R1K1 w kq - 0 16", listOf("e6f7"), "Double Check", "Rating 1074 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(368, "rn2k2r/ppp3p1/2q1p3/5p1p/6P1/2N3QK/PPPP4/R1B2R2 b kq - 0 18", listOf("h5g4"), "Double Check", "Rating 1084 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(369, "2kr1bnr/ppp3pp/8/8/6b1/1QN1PnPP/PP1PKP2/R1B4R b - - 0 14", listOf("f3d4", "e2e1", "d4b3"), "Double Check", "Rating 1094 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(370, "r1bRN1k1/pp3rp1/4p2p/qB3pQ1/8/8/PPP2nPP/2K4R w - - 0 18", listOf("e8f6"), "Double Check", "Rating 1105 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(371, "3q3k/rpr2pb1/3p1P1P/p1pPp3/P1n5/1P1R2P1/2P2PK1/7R w - - 0 42", listOf("h6g7", "h8g8", "h1h8"), "Double Check", "Rating 1116 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(372, "3r1b2/4Np1k/p2nb1p1/2r1p1PP/Pp2P1p1/1P3P2/4B3/3R1K1R w - - 2 31", listOf("h5g6", "h7g7", "h1h7"), "Double Check", "Rating 1124 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(373, "r3k1nr/ppp1qppp/8/2b1n3/2PP4/P4N1P/1P3PP1/R1BQKB1R b KQkq - 0 10", listOf("e5f3"), "Double Check", "Rating 1135 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(374, "r2q3r/1p1Nkpp1/p3pbbp/1B1p4/3P4/PQ2PPP1/1P5P/R3K2R w KQ - 0 17", listOf("b3b4", "e7e8", "d7f6"), "Double Check", "Rating 1144 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(375, "5rk1/5p2/4p1pQ/1p6/4P3/P2N1PP1/1q2n1KP/R4R2 b - - 1 32", listOf("e2f4", "g2g1", "b2g2"), "Double Check", "Rating 1153 - Find the best sequence of moves!", Color.BLACK)
        )
    }

    private fun getChunk5(): List<Puzzle> {
        return listOf(
            Puzzle(376, "r2q3k/1p6/p2p3p/3Np3/1PBnP3/P4r2/1P5Q/2K5 w - - 0 30", listOf("h2h6", "h8g8", "d5e7"), "Double Check", "Rating 1163 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(377, "5rk1/5Nnp/6p1/3Q1p2/7P/8/KP6/6q1 w - - 8 41", listOf("f7h6", "g8h8", "d5g8", "f8g8", "h6f7"), "Double Check", "Rating 1172 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(378, "6n1/p2q1p1k/1p1p1Pp1/3Pr1bB/2P4Q/1P4N1/P7/5R1K w - - 0 43", listOf("h5g6", "h7g6", "h4h5"), "Double Check", "Rating 1180 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(379, "k5r1/P6p/1R1B1b2/2p2r2/1p1p1P2/3P1qP1/1P5P/R5K1 w - - 7 36", listOf("b6b8", "g8b8", "a7b8q"), "Double Check", "Rating 1187 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(380, "rn1qkbnr/pbpp4/1p2pp2/4N1p1/3P4/4P1p1/PPPNBPPP/R2QK2R w KQkq - 0 9", listOf("e2h5", "e8e7", "e5g6", "e7f7", "g6h8"), "Double Check", "Rating 1193 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(381, "1r4rk/p6P/3p1Np1/q7/8/P1p5/1PP2PP1/1K5R w - - 0 29", listOf("h7g8q"), "Double Check", "Rating 1200 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(382, "r1b1r1k1/ppp2p1p/2n3p1/3Nb1B1/2P5/8/PP3PPP/3RKB1R b K - 2 13", listOf("e5c3"), "Double Check", "Rating 1213 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(383, "2krRb2/1q3P1Q/8/p2p4/3P4/Ppp5/2P5/1K2R3 b - - 1 38", listOf("b3c2", "b1c2", "b7b2", "c2d3", "b2d2"), "Double Check", "Rating 1226 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(384, "r4rk1/pppq2pp/2n5/2PN1p2/3Pn3/PQ6/1P2bPPP/2R2RK1 w - - 0 16", listOf("d5f6", "g8h8", "f6d7"), "Double Check", "Rating 1238 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(385, "r1bq1b1r/pp4pp/1kp5/1N1np3/PPB3Q1/2P5/3P1PPP/n1B2K1R w - - 0 15", listOf("a4a5", "b6a6", "b5c7"), "Double Check", "Rating 1248 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(386, "2r1r3/1b3p1k/5PR1/p2pP2p/P2P4/1p1Bq2P/1P5K/6R1 w - - 0 35", listOf("g6g7", "h7h8", "g7h7"), "Double Check", "Rating 1261 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(387, "2r5/1p6/1Pkb1p1p/3R4/p1K2P2/5BP1/7P/8 w - - 3 40", listOf("d5c5", "c6b6", "c5c8"), "Double Check", "Rating 1272 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(388, "2kr2nr/pp1q1ppp/4p3/4P3/2Nn4/8/P4PPP/2RQK2R w K - 2 17", listOf("c4b6", "c8b8", "b6d7"), "Double Check", "Rating 1282 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(389, "2r2rk1/pp3ppp/1q2p3/3p1B2/8/2P5/PP2RnPN/RQB3K1 b - - 0 17", listOf("f2h3", "g1f1", "b6g1"), "Double Check", "Rating 1293 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(390, "8/5p1k/r6p/5Np1/8/Pp2PQ2/1PqR2PP/K2R4 b - - 2 37", listOf("a6a3", "b2a3", "b3b2", "a1a2", "b2b1q"), "Double Check", "Rating 1303 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(391, "1kr5/p3q3/Pp3p1p/4pQ2/3r4/6P1/1PB2P1P/2R2K2 w - - 5 30", listOf("f5c8", "b8c8", "c2f5", "c8d8", "c1c8"), "Double Check", "Rating 1312 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(392, "2k5/pb1RR2p/6p1/8/2p5/5r2/P1P4P/7K b - - 0 30", listOf("f3f1"), "Double Check", "Rating 1320 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(393, "r1k5/pppb2pQ/2n1p3/8/2Bq4/8/PPP2nPP/R1B2RK1 b - - 3 17", listOf("f2h3", "g1h1", "d4g1", "f1g1", "h3f2"), "Double Check", "Rating 1327 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(394, "7R/Q4pq1/2pk4/2p1pP2/3pP3/P2P2bp/2P1BP1N/5RK1 b - - 0 26", listOf("g3h2", "g1h1", "g7g2"), "Double Check", "Rating 1335 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(395, "3r4/p7/2k3pp/3p4/1Pp1pP2/P3KbP1/2R2P1P/2R5 b - - 1 38", listOf("d5d4", "e3d2", "c4c3", "c2c3", "d4c3"), "Double Check", "Rating 1344 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(396, "r6k/6p1/3p3p/4pP2/4P3/Pp6/1Pq1Q1P1/K3R3 b - - 2 33", listOf("a8a3", "b2a3", "b3b2", "a1a2", "b2b1q"), "Double Check", "Rating 1355 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(397, "5r1k/ppp3p1/6r1/P1PPQNq1/4P3/6p1/5P1P/R4RK1 b - - 0 28", listOf("g3h2", "g1h1", "g5g2"), "Double Check", "Rating 1364 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(398, "3rkb1r/1q2pppp/p1p1bn2/6N1/P2Q4/4P3/1P1B1PPP/RN1R2K1 w k - 7 14", listOf("d4d8", "e8d8", "d2a5", "d8e8", "d1d8"), "Double Check", "Rating 1372 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(399, "2k4r/ppp2pp1/1bp1b1q1/8/3PP1nB/2P2NpP/PP2BP2/R2Q1RK1 b - - 1 16", listOf("g3f2", "g1g2", "g4e3", "g2f2", "e3d1"), "Double Check", "Rating 1383 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(400, "r1br1nk1/p3RR1p/1p2B1p1/3P4/8/8/PP4PP/6K1 w - - 3 28", listOf("f7g7", "g8h8", "g7g8"), "Double Check", "Rating 1391 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(401, "2kr3r/p1pp2q1/1P5n/5p2/6pp/1QPP1N1P/1P3PP1/R4RK1 b - - 0 22", listOf("g4f3", "g2g3", "h4g3", "b6a7", "g3f2"), "Double Check", "Rating 1401 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(402, "r4rk1/2q3pp/p1p1R3/3p4/3P4/1P4nP/PBPN2PK/R2Q4 b - - 0 20", listOf("g3f1", "h2g1", "c7h2"), "Double Check", "Rating 1410 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(403, "r1b5/p1pkqp1p/2pb1Q2/3p4/8/1P2p1P1/PBPP3P/RN2K1NR b KQ - 0 12", listOf("e3d2", "e1d2", "d6b4"), "Double Check", "Rating 1421 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(404, "rn1q3r/1b1pnk2/p3p1pp/1pp1P2Q/3P1P2/P2B4/P1P3PP/R1B2RK1 w - - 0 13", listOf("d3g6", "e7g6", "f4f5", "d8g8", "f5g6"), "Double Check", "Rating 1431 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(405, "5k2/2pR1P2/5Np1/b5Pp/8/2P5/p6r/K7 w - - 2 44", listOf("f6h7", "f8g7", "f7f8q"), "Double Check", "Rating 1441 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(406, "2r2k1r/1Q6/p2pb1p1/5P1p/3qP3/8/PP1N1nPP/R1B2RK1 b - - 4 23", listOf("f2h3", "g1h1", "d4g1", "f1g1", "h3f2"), "Double Check", "Rating 1460 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(407, "5qk1/1p1b2p1/1n2p3/p2pP1pP/2nP1NP1/2PB2K1/8/1Q6 w - - 0 28", listOf("d3h7", "g8h8", "f4g6", "h8h7", "g6f8"), "Double Check", "Rating 1470 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(408, "rn2kb1r/pp3ppp/2p3n1/4P3/2BQ2b1/2N2Npq/PPP4P/R1B2R1K w kq - 0 15", listOf("c4f7", "e8f7", "f3g5", "f7e8", "g5h3"), "Double Check", "Rating 1481 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(409, "8/2R2pkp/3p2p1/1NpPq3/2P1P3/5QnP/2r3PK/4R3 b - - 0 28", listOf("g3f1", "h2g1", "e5h2", "g1f1", "h2h1"), "Double Check", "Rating 1491 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(410, "r5k1/p4ppp/1qp5/3pR3/1P6/2P5/P3QnPP/RN4K1 b - - 2 19", listOf("f2h3", "g1f1", "b6g1"), "Double Check", "Rating 1499 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(411, "Q1b1kb1r/2ppqpp1/p6p/1p2n3/8/2N4P/PPP2PP1/R1B1KB1R b KQk - 0 12", listOf("e5f3", "e1d1", "e7e1"), "Double Check", "Rating 1509 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(412, "8/1p2b2p/3k1r2/p1pPp1KP/P1P1Bp2/3P4/1P3R2/8 b - - 0 43", listOf("f6g6", "g5f5", "g6g5"), "Double Check", "Rating 1519 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(413, "4r1k1/pp3pb1/1n4pp/2p1P3/4N1Qq/1P1r3n/PB1N2R1/1R3B1K b - - 17 31", listOf("h3f2", "h1g1", "f2g4"), "Double Check", "Rating 1528 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(414, "3q2k1/5pp1/p3b2p/8/P1p1N3/1r2n2P/6P1/1B1R2K1 w - - 0 34", listOf("d1d8", "g8h7", "e4f6"), "Double Check", "Rating 1537 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(415, "r1b2rk1/1q2p1b1/p1np2p1/1p1Nn1pQ/4P3/NB6/PPP2PP1/2KR3R w - - 0 18", listOf("d5f6"), "Double Check", "Rating 1547 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(416, "4rbk1/p1N5/3p4/3P2q1/3N3p/2P1p1Pb/PPB1Qn1P/4R1K1 b - - 0 32", listOf("h4g3", "e2f3", "g3h2"), "Double Check", "Rating 1556 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(417, "2r1r3/1p1n2bk/2n1b1Np/p2pP1q1/P1pP4/2P3B1/1P3RPP/1Q3RK1 w - - 1 32", listOf("g6f8", "h7h8", "b1h7"), "Double Check", "Rating 1565 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(418, "r7/4Q1pk/p2p4/r3p3/7P/Pp2BP2/1PqR4/K6R b - - 2 26", listOf("a5a3", "b2a3", "b3b2", "a1a2", "b2b1q"), "Double Check", "Rating 1574 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(419, "2k3rr/1pp1b3/p1P1p3/2P1Pp2/3P4/P1BB1Nq1/4QR1p/3R3K b - - 0 27", listOf("g3g1", "f3g1", "h2g1q"), "Double Check", "Rating 1582 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(420, "r1b4r/pp2pk1p/4n1pb/1BPN4/8/5Np1/PPP3P1/2KR1R2 w - - 4 18", listOf("f3g5", "f7g8", "d5e7", "g8g7", "f1f7"), "Double Check", "Rating 1590 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(421, "4r2k/3n2bp/3N2p1/qP1Q1p2/2P5/1P6/5PPP/6K1 w - - 4 32", listOf("d6f7", "h8g8", "f7h6", "g8h8", "d5g8", "e8g8", "h6f7"), "Double Check", "Rating 1599 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(422, "3rk2r/1pq2p2/p1nbp3/3p2Pp/P2Pn3/2N1BQ1B/1PP3PP/R4RK1 b k - 2 16", listOf("d6h2", "g1h1", "e4g3", "h1h2", "g3f1", "h2g1", "f1e3"), "Double Check", "Rating 1606 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(423, "4r1k1/pp3pbp/6p1/3Pp3/3qP3/2Q5/PP2RnPP/R1B3K1 b - - 2 23", listOf("f2h3", "g1f1", "d4g1"), "Double Check", "Rating 1614 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(424, "2kr1b1r/pp3ppp/8/q7/1n2N3/5N2/PP3PPP/R1Q1KB1R b KQ - 0 16", listOf("b4c2", "e1e2", "a5b5"), "Double Check", "Rating 1622 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(425, "r3k1r1/pb1p1p1N/1pq3p1/2b1PpB1/2Nn4/5P2/PP4PP/R2Q1RK1 b q - 0 18", listOf("d4f3", "g1h1", "f3g5"), "Double Check", "Rating 1634 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(426, "3k1b1r/2Nbpppp/8/Q2p4/8/P3P3/4KPPP/2q5 w - - 4 26", listOf("c7e6", "d8e8", "a5d8"), "Double Check", "Rating 1643 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(427, "8/8/3B3p/8/R2b2k1/5pN1/PP3r1P/6K1 b - - 1 34", listOf("f2g2", "g1f1", "g2g1"), "Double Check", "Rating 1650 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(428, "rn2kbnr/ppq2ppp/8/1p6/4Np2/8/PPPP2PP/R1BQR1K1 w kq - 2 11", listOf("e4f6", "e8d8", "e1e8"), "Double Check", "Rating 1658 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(429, "r5k1/1b3Rbp/p5p1/1pB1p3/4P1nq/1BNr3P/PPP3P1/3R2K1 w - - 0 19", listOf("f7f8"), "Double Check", "Rating 1665 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(430, "5k2/1p4pp/1b6/p7/P7/7P/4prB1/R5KR b - - 0 30", listOf("f2f1", "g1h2", "b6c7"), "Double Check", "Rating 1674 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(431, "r3k3/ppp2pp1/2p5/6Qq/4P3/2PP2p1/PPK1b2r/RNB1R3 b q - 1 19", listOf("e2d1"), "Double Check", "Rating 1682 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(432, "1b4k1/5p2/6p1/Rq6/1PrB4/4PP2/5P2/Q4K2 b - - 1 36", listOf("c4c1", "f1g2", "b5f1"), "Double Check", "Rating 1689 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(433, "r7/pp2n3/7k/3p2RP/8/2P5/PP1Q4/2KBqr2 w - - 5 29", listOf("g5g6", "h6h7", "d2h6"), "Double Check", "Rating 1697 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(434, "r2q1r2/ppp1ppk1/5n2/5pNp/3nP2P/6Q1/PPP3P1/R3K2R w KQ - 0 19", listOf("g5e6", "g7h8", "g3g7"), "Double Check", "Rating 1706 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(435, "6k1/4bppp/1qR1p3/3pPP2/6P1/4rN2/3Q2BP/6K1 b - - 0 26", listOf("e3e1"), "Double Check", "Rating 1714 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(436, "r2q2k1/ppp2r2/2npQn2/4p1N1/4P3/2PP3P/PP1N1KP1/R6R b - - 0 15", listOf("f6e4", "f2g1", "e4g5"), "Double Check", "Rating 1724 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(437, "r2q1rk1/ppp2pp1/5n1p/2b1P3/3n4/2NP1N2/PPP3PP/R1BQ1RK1 b - - 0 14", listOf("d4f3", "g1h1", "f3e5"), "Double Check", "Rating 1732 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(438, "r1b1k1nr/p1p2ppp/2pp4/8/2B1Nb2/8/P1P2PPP/4R1K1 w kq - 0 15", listOf("e4f6", "e8f8", "e1e8"), "Double Check", "Rating 1740 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(439, "r1b2r1k/4N1b1/1q1N3p/1p2P1pQ/p3np2/8/PPBB2PP/R3R2K b - - 10 30", listOf("e4f2", "h1g1", "f2h3", "g1h1", "b6g1", "e1g1", "h3f2"), "Double Check", "Rating 1748 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(440, "b6r/2p1npk1/2P1p3/1P1PR3/2n3pq/6N1/PQ3PP1/5KN1 w - - 1 33", listOf("e5g5", "g7f8", "b2g7"), "Double Check", "Rating 1756 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(441, "Q6R/4k3/5pP1/5p2/1p6/4BP2/Kpq5/7R b - - 0 36", listOf("b2b1q"), "Double Check", "Rating 1764 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(442, "1k1r1b2/2ppq3/1P2pp2/4P3/2PP2P1/3B1b1r/3Q1P2/1R2R1K1 w - - 0 24", listOf("b6c7", "b8c8", "b1b8", "c8c7", "d2a5", "c7b8", "e1b1"), "Double Check", "Rating 1781 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(443, "rr4k1/5p2/3pp3/1q2b2P/1Pb1P3/p1P1BNN1/3Q2P1/K2R3R b - - 1 32", listOf("b5b4", "f3e5", "b4b2", "d2b2", "a3b2"), "Double Check", "Rating 1771 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(444, "5k1r/ppp3pp/5q2/2b5/3nPP2/8/PPP3PP/R1BQ1RK1 b - - 0 16", listOf("d4e2", "g1h1", "e2g3", "h2g3", "f6h6", "d1h5", "h6h5"), "Double Check", "Rating 1780 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(445, "8/6p1/2bN2kp/3nB3/3P1PP1/2P1p3/1P2RbKP/8 b - - 4 32", listOf("d5f4", "g2f1", "c6g2"), "Double Check", "Rating 1789 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(446, "4Q3/p6p/5p1k/2b1NR2/3n4/6P1/PPr4P/R5K1 b - - 0 23", listOf("d4f3", "g1f1", "c2f2"), "Double Check", "Rating 1797 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(447, "8/p4pk1/3p2p1/2b1p3/2B2qQ1/2PP2nP/PP3PP1/3RR1K1 b - - 6 24", listOf("c5f2", "g1h2", "g3f1", "h2h1", "f4h2"), "Double Check", "Rating 1804 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(448, "4r1k1/7p/3p2pP/4p1B1/p6Q/PpP5/2qR1P2/K6R b - - 2 33", listOf("b3b2", "a1a2", "b2b1q"), "Double Check", "Rating 1813 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(449, "4rrk1/p2q1pbp/6pQ/3p4/1p1NnP2/1P1R4/PBP3PP/5R1K w - - 3 23", listOf("h6g7", "g8g7", "d4f5", "g7g8", "f5h6"), "Double Check", "Rating 1831 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(450, "2r2rk1/1b3p2/p2qp1B1/1p5N/1P1P2Qp/P3nP2/6PP/2R3K1 w - - 0 26", listOf("g6h7", "g8h8", "g4g7"), "Double Check", "Rating 1821 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk6(): List<Puzzle> {
        return listOf(
            Puzzle(451, "5b2/p4p2/1p3Pk1/3prN1p/2pp2p1/PP1B2PP/2P2RK1/4r3 w - - 0 36", listOf("f5e7", "g6h6", "e7g8", "h6g5", "h3h4"), "Double Check", "Rating 1829 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(452, "r5r1/1pk1qRb1/p6B/3Q4/8/2NP4/PPP4P/6K1 b - - 8 27", listOf("g7d4", "g1f1", "g8g1"), "Double Check", "Rating 1839 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(453, "r1b1r1k1/pp1p2b1/7p/q1pP2p1/1n2B3/PQ4B1/1P1N1PPP/R3K2R b KQ - 4 17", listOf("e8e4", "d2e4", "b4c2"), "Double Check", "Rating 1850 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(454, "1r4k1/p2n2p1/4NpQp/q2pP3/1prP2PP/2P5/PK3P2/2R4R b - - 0 26", listOf("b4c3", "b2a1", "a5a2", "a1a2", "c4a4"), "Double Check", "Rating 1862 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(455, "b3rbk1/6pp/3p4/PP5r/2P1PR2/1B3PpP/R4qP1/3Q3K b - - 4 33", listOf("h5h3", "g2h3", "g3g2", "h1h2", "g2g1q"), "Double Check", "Rating 1871 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(456, "2kr3r/pppb1p2/2pb4/8/4P1pp/3P2Pq/PPP2P1P/R2QNBRK b - - 1 16", listOf("h3h2", "h1h2", "h4g3", "h2g2", "h8h2"), "Double Check", "Rating 1882 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(457, "r3kb1r/ppp2ppp/2n5/7q/3PN3/6P1/PP3PBP/R1BbR1K1 w kq - 0 13", listOf("e4f6", "e8d8", "e1e8"), "Double Check", "Rating 1892 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(458, "r6r/pp1bkpbp/1qnNpn2/8/8/Q7/PPP2PPP/2KR1BNR w - - 6 12", listOf("d6c8", "e7e8", "c8b6"), "Double Check", "Rating 1901 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(459, "1r1k1b1r/2Np1qpp/2nP4/Q1p1P3/4p3/P7/1PPR1PPP/2K4R w - - 3 27", listOf("c7e6", "d8e8", "a5d8", "c6d8", "e6c7"), "Double Check", "Rating 1909 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(460, "r5k1/p4ppp/7r/2Rp3b/3P4/4PPBq/PP3P2/2R2QK1 b - - 10 23", listOf("h3h1", "g1h1", "h5f3", "h1g1", "h6h1"), "Double Check", "Rating 1917 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(461, "r1b1k3/ppp3pp/5n2/4N3/3P1q1b/2P3nP/PP4PK/RNBQ3R b q - 0 16", listOf("g3f1", "h2g1", "f4f2"), "Double Check", "Rating 1924 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(462, "2b2r1k/1pp1p1b1/1p2B1pp/3P1nN1/4R3/1P5Q/P1P3PP/5K2 b - - 0 23", listOf("f5g3", "f1e1", "g7c3", "e1d1", "f8f1", "e4e1", "f1e1"), "Double Check", "Rating 1932 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(463, "r2qrb2/pp3kpb/1np1Rn1p/7P/2QP1B2/5NN1/PPP2PP1/4R1K1 w - - 9 18", listOf("e6e7"), "Double Check", "Rating 1940 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(464, "2r2r2/6Bk/p1q3pp/1pPn1p2/1P6/P3p1P1/2N1PPKP/2RQR3 b - - 0 26", listOf("d5f4", "g2f1", "c6h1"), "Double Check", "Rating 1948 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(465, "r2q1r2/pb1nbppk/1p2p3/8/3pNP1P/2PB4/PP2Q1P1/R3K2R w KQ - 0 15", listOf("e4f6", "h7h8", "e2h5"), "Double Check", "Rating 1956 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(466, "6r1/1b2b2k/p6p/4B2P/3PRpp1/2P4R/1PQ2pK1/8 b - - 1 42", listOf("g4h3", "g2f1", "g8g1", "f1f2", "g1g2", "f2f3", "g2c2"), "Double Check", "Rating 1930 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(467, "4r1k1/5p2/1p6/1PpQb1P1/2Pp2q1/4rNP1/5RK1/2R5 w - - 0 39", listOf("d5f7", "g8f7", "f3e5", "f7g7", "e5g4"), "Double Check", "Rating 1965 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(468, "rnbq1rk1/2p1p1bp/p2p1pp1/3N2Q1/1p1P4/1B3N2/PPP2PPP/R3K2R w KQ - 0 12", listOf("d5e7", "g8h8", "e7g6", "h7g6", "g5h4", "g7h6", "h4h6"), "Double Check", "Rating 1973 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(469, "3rk3/5p2/p7/6p1/4N1P1/3q4/P3Q2K/8 w - - 0 33", listOf("e4d6", "e8d7", "e2d3"), "Double Check", "Rating 1955 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(470, "r6k/pp4bp/2p1b2r/3pN1q1/3Pp1B1/2P3Q1/PP3RPP/5RK1 w - - 10 26", listOf("g4e6", "g5g3", "e5f7", "h8g8", "f7h6"), "Double Check", "Rating 1981 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(471, "5rk1/p1p2ppp/8/3q4/8/2Q2nP1/PP3PKP/3R1R2 b - - 5 18", listOf("f3e1", "g2g1", "d5g2"), "Double Check", "Rating 1990 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(472, "r3k2r/pb1p4/4pp2/pP4p1/2P5/P2B1n2/5P1P/R2QRK2 b kq - 3 28", listOf("f3h2", "f1g1", "h2f3", "g1g2", "f3e1"), "Double Check", "Rating 1997 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(473, "5rr1/p4Qbk/2p1pRpp/3p4/2qB4/2P5/P5PP/6K1 w - - 5 26", listOf("f7g6", "h7h8", "g6h6", "g7h6", "f6h6"), "Double Check", "Rating 2005 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(474, "rn2kb1r/pp2pppp/2p5/4P3/3Q4/5b2/P1qB2PP/3RKB1R w Kkq - 0 15", listOf("d4d8", "e8d8", "d2a5", "d8e8", "d1d8"), "Double Check", "Rating 2016 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(475, "r4rk1/pp2qp1p/5Qn1/3p4/3N4/1P2P3/PBP3PP/R5K1 w - - 1 19", listOf("f6g7", "g8g7", "d4f5", "g7g8", "f5h6"), "Double Check", "Rating 2045 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(476, "rnbk1b1r/pp3ppp/2p5/4q3/4n3/8/PPPB1PPP/2KR1BNR w - - 0 10", listOf("d2g5", "d8c7", "g5d8"), "Double Check", "Rating 2025 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(477, "rn2k1nr/pp3ppp/3p4/2b5/4N3/3P2Pb/PPP1Q2P/R1B1KBq1 w Qkq - 0 10", listOf("e4f6", "e8d8", "e2e8", "d8c7", "f6d5"), "Double Check", "Rating 2034 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(478, "r2q1rk1/pb2bpp1/1p2p2p/8/3n2Q1/P1PBP1B1/5P1P/R3K1R1 w Q - 0 17", listOf("g4g7", "g8g7", "g3e5"), "Double Check", "Rating 2043 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(479, "3r2k1/qb1n1Npp/p4b2/1p6/1P1pP3/P3Q1BP/5PP1/R5K1 w - - 0 27", listOf("e3b3", "b7d5", "b3d5", "d7b6", "f7h6"), "Double Check", "Rating 2053 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(480, "r1bq1rk1/pp3R1p/2p3p1/7n/N3P2b/1BBn4/PPp3PP/R6K w - - 0 19", listOf("f7g7", "g8h8", "g7g8"), "Double Check", "Rating 2061 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(481, "r5q1/ppp5/3p3Q/3k3n/3P1n2/2P3PP/PP1N1P2/R3R1K1 b - - 0 21", listOf("h5g3", "h6f4", "g3e2", "g1f1", "e2f4"), "Double Check", "Rating 2070 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(482, "r4N1k/pp1n2pp/2p3q1/8/2BP4/4P1b1/PP2Q2P/R3R1K1 b - - 0 20", listOf("g3f2", "g1f2", "a8f8"), "Double Check", "Rating 2079 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(483, "r5k1/p4ppp/1p4q1/3p4/3Nr3/2PKR2P/PP1Q1PP1/R7 b - - 2 24", listOf("e4e3", "d3e3", "g6e4"), "Double Check", "Rating 2087 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(484, "1r2nr1k/1p1P3B/p7/3Pp3/1P4pp/6P1/P1QN1q2/2R1R2K b - - 1 32", listOf("h4g3", "d2e4", "g3g2", "h1h2", "g2g1q"), "Double Check", "Rating 2097 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(485, "1r3r1k/p1p2pRp/3p4/3p4/4P3/2B2P2/PP1q1P1P/R6K w - - 1 21", listOf("g7g8", "h8g8", "a1g1", "d2g5", "g1g5"), "Double Check", "Rating 2108 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(486, "rn1k4/pp2R2p/2p3p1/6B1/3b2b1/1BN3P1/PPP2r1P/R6K b - - 4 20", listOf("g4f3", "h1g1", "f2g2", "g1f1", "g2g1"), "Double Check", "Rating 2121 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(487, "6r1/2p2prk/2p4p/3p1qpQ/3Pp2P/2P5/7R/6RK w - - 5 44", listOf("h5h6", "h7h6", "h4g5", "h6g6", "h2h6"), "Double Check", "Rating 2168 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(488, "1r4k1/5ppp/b1Q5/6B1/B1nP4/2P2N2/2P2PPP/5K2 b - - 0 26", listOf("c4e3", "f1e1", "b8b1", "e1d2", "e3f1"), "Double Check", "Rating 2134 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(489, "1b3r1k/1Q4pp/p2p1r2/P4p2/1P3q2/6N1/2P1KPRP/R7 w - - 0 29", listOf("b7g7", "h8g7", "g3h5", "g7h8", "h5f4"), "Double Check", "Rating 2147 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(490, "r4rk1/pp3Nb1/3p2Pp/8/2B2B2/1Pp5/P1n2KP1/R7 w - - 0 22", listOf("f7h6", "g8h8", "h6f7", "f8f7", "a1h1"), "Double Check", "Rating 2159 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(491, "rn2r1k1/pp5p/2p2pp1/3p4/3P2N1/5QP1/PPP1q1P1/2K4R w - - 0 19", listOf("g4f6", "g8f8", "f6h7", "f8e7", "f3e2"), "Double Check", "Rating 2171 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(492, "7r/pkp2ppp/2Nrp3/2R5/P3QK2/q7/6PP/8 w - - 0 28", listOf("c6a5", "b7c8", "e4b7"), "Double Check", "Rating 2185 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(493, "rn2k2r/1pp2ppp/p1b1p3/3nP3/3P4/1QPB2Pq/P3NP1P/R1B2RK1 b kq - 2 13", listOf("h3g2", "g1g2", "d5f4", "g2g1", "f4h3"), "Double Check", "Rating 2199 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(494, "1n2r1k1/3q1p2/1bppb1pp/1p3N1n/3pP1QN/2P4P/2BB1PP1/4R1K1 w - - 0 24", listOf("h4g6", "e6f5", "g6e7"), "Double Check", "Rating 2212 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(495, "6k1/Q4pp1/p3p2p/3q4/7n/4B1NP/PR3PbK/8 b - - 0 31", listOf("h4f3", "h2g2", "f3h4", "g2f1", "d5d1"), "Double Check", "Rating 2227 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(496, "1n1k2r1/1Bp4q/p2bp3/4n3/NP6/P7/4QRP1/3R2K1 b - - 2 30", listOf("h7h2", "g1h2", "e5f3", "h2h3", "g8g3"), "Double Check", "Rating 2286 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(497, "rnb1r1k1/pp4p1/7p/3pnB2/3N3P/8/P1PN1PP1/R1B1K2R b KQ - 0 15", listOf("e5d3", "e1d1", "d3f2"), "Double Check", "Rating 2244 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(498, "2r3k1/1p3pp1/pq4np/3Q1N2/8/1Pr1R1P1/P6P/5RK1 w - - 0 27", listOf("d5f7", "g8f7", "f5h6"), "Double Check", "Rating 2261 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(499, "Nnb3Q1/pp2k2p/2p2p2/8/3pP3/2q1b1K1/2P3PP/3R1BNR b - - 5 20", listOf("e3f2", "g3f2", "c3e3"), "Double Check", "Rating 2279 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(500, "6RQ/3r3p/4pP1k/3q2p1/p7/7P/5rP1/6RK w - - 1 45", listOf("g8g7", "d5d3", "h8f8", "d7d8", "g7h7"), "Double Check", "Rating 2301 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(501, "4r3/1p2b3/2p1p1kp/p3Bp2/PnP3P1/6Q1/1P1q1PP1/5BK1 w - - 1 33", listOf("g4f5", "g6f5", "e5c3", "d2f4", "f1d3", "b4d3", "g3d3", "f5g5", "c3d2", "f4d2", "d3d2"), "Double Check", "Rating 2323 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(502, "6rr/pp6/5pk1/8/3P4/2P2QnN/PP3qPK/4R1R1 b - - 1 32", listOf("h8h3", "h2h3", "g8h8", "h3g4", "h8h4", "g4h4", "g3f5", "h4g4", "f2h4"), "Double Check", "Rating 2345 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(503, "6k1/1p2p2p/p1qpp1p1/8/1P1b4/P4rB1/2R4P/1N1QR2K b - - 0 30", listOf("f3f1"), "Double Check", "Rating 1663 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(504, "r3k1r1/1bp1qp1p/p3p3/2p1B3/8/1P1P4/P1PN1PPP/R2Q1RK1 b q - 0 16", listOf("g8g2", "g1h1", "g2h2", "h1h2", "e7h4", "h2g1", "h4h1"), "Double Check", "Rating 2380 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(505, "r1b4r/1pq2pk1/p3p3/3pn1N1/6Q1/1P6/P4PPP/R4RK1 w - - 0 28", listOf("g5e6", "g7f6", "g4g7", "f6e6", "g7h8"), "Double Check", "Rating 2421 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(506, "r6k/ppp2q2/2npb2B/7Q/2P4P/P1PBPr2/5P2/R3K3 w Q - 2 20", listOf("d3g6", "e6g4", "h6g7", "h8g7", "h5h7", "g7f8", "g6f7", "f3f7", "h7h8", "f8e7", "h8a8", "f7f8", "a8b7"), "Double Check", "Rating 2408 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(507, "3R4/1pp2pk1/1p5p/4P1p1/4bP1n/P5QP/1Pq2B1K/4R3 b - - 3 36", listOf("h4f3", "h2g2", "f3e1", "g2f1", "e1f3"), "Double Check", "Rating 2476 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(508, "3b1B2/3b1pR1/7k/p2pp3/2pP1PK1/2r3NP/8/8 w - - 0 46", listOf("g3f5", "d7f5", "g4f5", "d8e7", "g7g6", "h6h7", "g6h6", "h7g8", "f8e7"), "Double Check", "Rating 2579 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(509, "4R3/1r5k/6p1/6q1/2PP1p1p/3QP2P/5PP1/5RK1 b - - 0 29", listOf("f4f3", "g2g4", "h4g3", "e3e4", "g3f2", "g1f2", "g5h4", "f2f3", "h4h3", "f3e2", "b7b2"), "Double Check", "Rating 2880 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(510, "5r2/1pp3p1/p1k1p3/2b3Np/4P1nP/PP3P2/1BP3P1/3R3K b - - 2 27", listOf("g4f2", "h1h2", "f2d1"), "Fork", "Rating 692 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(511, "8/4kP2/6p1/7p/2B2Pn1/6PP/6K1/8 b - - 0 49", listOf("g4e3", "g2f3", "e3c4", "f7f8q", "e7f8"), "Fork", "Rating 742 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(512, "8/1bb4p/p3kp2/8/2P4p/1P1NB3/P4P2/6K1 w - - 0 44", listOf("d3c5", "e6f5", "c5b7"), "Fork", "Rating 778 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(513, "2b3k1/1p3ppp/4pB2/2n5/4P3/6P1/3r1PBP/R5K1 w - - 0 20", listOf("a1a8", "g7f6", "a8c8", "g8g7", "c8c5"), "Fork", "Rating 928 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(514, "2r5/2P4p/p3kpp1/8/3n1PPP/4N3/8/2R3K1 b - - 0 36", listOf("d4e2", "g1f2", "e2c1"), "Fork", "Rating 800 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(515, "8/2p2pk1/7N/p1p5/P1P5/5pr1/3KR3/8 w - - 1 40", listOf("h6f5", "g7g6", "f5g3"), "Fork", "Rating 816 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(516, "4k3/5R2/1p3B2/pP1p4/3Pq1p1/6P1/P6P/6K1 w - - 2 51", listOf("f7e7", "e4e7", "f6e7"), "Fork", "Rating 831 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(517, "7R/2pk3p/7b/8/1P2p2P/3b4/6P1/6K1 w - - 0 41", listOf("h8h7", "d7e6", "h7h6"), "Fork", "Rating 846 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(518, "4r2k/2p4p/3pq1p1/Q7/5P2/4R1P1/P1P4P/3R2K1 b - - 0 26", listOf("e6e3", "g1g2", "e3e2", "g2h3", "e2d1"), "Fork", "Rating 859 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(519, "5rk1/3q1pp1/3b3p/B1p1p3/P3Pn2/1B3PQP/6P1/R4RK1 b - - 0 24", listOf("f4e2", "g1h2", "e2g3"), "Fork", "Rating 871 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(520, "6r1/3k2r1/p1pqp3/3p4/2pP4/2N2QPP/PPP2P2/4R1K1 b - - 0 25", listOf("g7g3", "f2g3", "g8g3", "g1f2", "g3f3"), "Fork", "Rating 1013 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(521, "r7/P4k2/1R2pp2/6p1/5b1p/5P1P/6P1/R5K1 b - - 4 40", listOf("f4e3", "g1f1", "e3b6"), "Fork", "Rating 883 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(522, "8/8/6K1/7p/2p3kP/8/2R1b3/8 b - - 3 60", listOf("e2d3", "g6f6", "d3c2"), "Fork", "Rating 895 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(523, "r2qk3/4bp2/p2pb2Q/3Pp3/1p6/1N3P2/PPP1B1P1/2KR4 b q - 0 20", listOf("e7g5", "c1b1", "g5h6"), "Fork", "Rating 905 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(524, "5r1k/p2b2p1/8/8/2Q1R2p/5Pr1/P1P2RPq/5K2 w - - 4 35", listOf("e4h4", "h2h4", "c4h4"), "Fork", "Rating 915 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(525, "8/8/2b4p/5k2/5p2/5N1P/5KP1/8 w - - 4 52", listOf("f3d4", "f5e4", "d4c6"), "Fork", "Rating 924 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk7(): List<Puzzle> {
        return listOf(
            Puzzle(526, "r5r1/1ppk4/p1nb4/3b3q/3PN3/1P2P3/PBQ5/2R1KR2 w - - 0 23", listOf("e4f6", "d7c8", "f6h5"), "Fork", "Rating 933 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(527, "r7/8/P7/1BP5/6p1/4Pk1p/7K/8 w - - 0 45", listOf("b5c6", "f3f2", "c6a8", "g4g3", "h2h3"), "Fork", "Rating 942 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(528, "2r4r/5kpp/p4b2/3ppP2/1pq1N2P/1Q6/PP6/K2R2R1 w - - 2 26", listOf("e4d6", "f7e7", "d6c4"), "Fork", "Rating 950 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(529, "4q1k1/p1p3p1/1p1p3p/5P2/2P3P1/2Bn4/P5QP/6K1 w - - 0 29", listOf("g2d5", "g8f8", "d5d3"), "Fork", "Rating 958 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(530, "rnb1k2r/pp3ppp/4p1n1/3p2Q1/q2N4/P1P5/1P3PPP/1R2KB1R w Kkq - 0 15", listOf("f1b5", "c8d7", "b5a4"), "Fork", "Rating 965 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(531, "3k4/ppq5/1Pp2pN1/8/2QP4/6p1/1P4P1/R4R1K b - - 0 29", listOf("c7h7", "g6h4", "h7h4", "h1g1", "h4h2"), "Fork", "Rating 972 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(532, "1b4k1/pp1b2pp/5p2/5R2/5PPP/1P6/P7/6K1 w - - 1 35", listOf("f5d5", "d7g4", "d5d8", "g8f7", "d8b8"), "Fork", "Rating 979 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(533, "r1q2rk1/1p3ppp/1pppb3/5N2/2P5/4NQ2/Pb3PPP/R3R1K1 w - - 0 19", listOf("f5e7", "g8h8", "e7c8"), "Fork", "Rating 985 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(534, "4R3/pp1r2p1/6kp/8/2P5/1P3N2/P6P/2n3K1 w - - 3 35", listOf("f3e5", "g6f5", "e5d7"), "Fork", "Rating 991 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(535, "r1b1k2r/1p1qbpp1/p3pn1p/1Np1B3/P7/3P4/1PP2PPP/R2QKB1R w KQkq - 0 13", listOf("b5c7", "d7c7", "e5c7"), "Fork", "Rating 997 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(536, "r7/3krp2/pb4pB/2pN3n/2P5/3P1K1P/P7/1R6 w - - 2 36", listOf("d5b6", "d7c6", "b6a8"), "Fork", "Rating 1005 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(537, "r2qk2r/ppp2pp1/5pp1/3p4/1bPP4/4P2P/PP1N1PP1/R2QK2R w KQkq - 1 13", listOf("d1a4", "d8d7", "a4b4"), "Fork", "Rating 1014 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(538, "r4b1r/pp4pp/1np1Nnk1/3q4/3P4/8/PPP1QPPP/R1B1K2R w KQ - 1 13", listOf("e6f4", "g6f7", "f4d5"), "Fork", "Rating 1023 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(539, "5R2/pp2q1pk/7p/3pN3/3P4/P1P5/1P4P1/6K1 w - - 2 32", listOf("f8h8", "h7h8", "e5g6", "h8g8", "g6e7"), "Fork", "Rating 1251 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(540, "r1bq1rk1/ppp2ppp/3p4/2b1p1N1/4P1n1/1P1P2QP/1PP2PP1/RNB1K2R b KQ - 0 10", listOf("c5f2", "g3f2", "g4f2"), "Fork", "Rating 1033 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(541, "4rbk1/R6p/4n1p1/4P3/2p5/2B2N1P/P5P1/6K1 b - - 0 38", listOf("f8c5", "g1f1", "c5a7"), "Fork", "Rating 1042 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(542, "8/pb2Qr1k/1p4pp/3q4/4p1N1/7P/6P1/4R2K w - - 1 35", listOf("g4f6", "h7g7", "f6d5", "f7e7", "d5e7"), "Fork", "Rating 1058 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(543, "1R6/1P3ppk/7p/p7/5r2/2b2P1P/5P2/6K1 w - - 0 38", listOf("b8h8", "h7h8", "b7b8q", "h8h7", "b8f4"), "Fork", "Rating 1067 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(544, "1r3rk1/R1bbq1p1/2p1p2p/2QpP3/1P3P2/2N3P1/6BP/2R3K1 b - - 0 25", listOf("c7b6", "c5b6", "b8b6"), "Fork", "Rating 1255 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(545, "r5k1/p3p1b1/1p1p1rpp/Q4p2/2p2P2/2P1qN2/PP4PP/R4R1K w - - 0 22", listOf("a5d5", "e7e6", "d5a8"), "Fork", "Rating 1074 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(546, "2kr4/r2n1q2/1p1R1p1p/p7/P2N4/1PP3Q1/5PP1/3R3K b - - 6 32", listOf("f7h5", "g3h3", "h5d1"), "Fork", "Rating 1082 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(547, "5rk1/6pp/p1p1p3/1pN5/3r4/8/PPP3PP/2K2R2 w - - 0 24", listOf("f1f8", "g8f8", "c5e6", "f8e7", "e6d4"), "Fork", "Rating 1387 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(548, "1r2k3/pb1p4/1pn1ppqB/7p/3P1Q1N/P1n5/2P2PPP/R4RK1 b - - 4 18", listOf("c3e2", "g1h1", "e2f4", "h4g6", "f4g6"), "Fork", "Rating 1089 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(549, "8/3n1pk1/8/3B3P/6K1/8/8/8 b - - 1 65", listOf("d7f6", "g4f3", "f6d5"), "Fork", "Rating 1096 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(550, "6k1/r4pb1/4p1p1/3p4/2p2QPK/5R1P/6r1/8 w - - 8 53", listOf("f4b8", "g8h7", "b8a7"), "Fork", "Rating 1110 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(551, "1r2kb1r/p2q1p1p/1nQ4p/8/3PN1b1/4P3/PP3P1P/R3K1NR w KQk - 1 15", listOf("e4f6", "e8d8", "f6d7"), "Fork", "Rating 1117 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(552, "r4rk1/ppp3pp/3bp1q1/3N3n/2P2B2/5Q1P/PP3PP1/R3R1K1 w - - 6 17", listOf("f4d6", "f8f3", "d5e7", "g8f7", "e7g6"), "Fork", "Rating 1380 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(553, "7r/p7/3Q4/3Pp1k1/2P3p1/8/1P2K1P1/q7 w - - 1 35", listOf("d6e5", "g5g6", "e5h8"), "Fork", "Rating 1125 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(554, "rnb1k2r/pp2npp1/2pp2qp/3N4/3P3b/5B2/PPP2PPP/R1BQR1K1 w kq - 0 13", listOf("d5c7", "e8d8", "c7a8"), "Fork", "Rating 1131 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(555, "r3qrk1/3R1ppp/p2Q4/8/2p5/2p1P1P1/PP3P1P/1R2B2K b - - 0 26", listOf("e8e4", "h1g1", "e4b1"), "Fork", "Rating 1334 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(556, "7r/pq1kn2p/2bpNbp1/2p2p2/P1Q2P2/2N5/1PPB2PP/4R1K1 w - - 5 25", listOf("e6c5", "d6c5", "c4e6", "d7e8", "e6f6"), "Fork", "Rating 1399 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(557, "5r2/p4k1p/6p1/3N1p1q/2R1nP2/1P2P1P1/PQ5P/6K1 b - - 0 27", listOf("h5d1", "g1g2", "d1d5"), "Fork", "Rating 1137 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(558, "8/1p2k3/2p5/p4p1p/2P1r1rp/P3R3/1P3PP1/4R1K1 w - - 0 30", listOf("f2f3", "e4e3", "e1e3"), "Fork", "Rating 1342 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(559, "8/R3n1pk/2b2q1p/1p3P2/2pP2QP/2P1p1P1/1P2K3/8 w - - 1 38", listOf("a7e7", "f6e7", "g4g6", "h7h8", "g6c6"), "Fork", "Rating 1407 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(560, "6R1/p4r1p/1p6/4n2k/4N3/4P3/PP2K3/8 w - - 7 36", listOf("g8g5", "h5h6", "g5e5"), "Fork", "Rating 1143 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(561, "3r4/1p4pp/p3k3/4n3/8/1PN1K1PP/P2p1P2/3R4 b - - 0 30", listOf("d8d3", "e3e4", "d3c3"), "Fork", "Rating 1149 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(562, "r2qkbnr/pp3ppp/2n5/1B1p4/3Q4/4PN2/PP3PPP/R1B1K2R b KQkq - 3 9", listOf("d8a5", "c1d2", "a5b5"), "Fork", "Rating 1155 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(563, "5rk1/2N5/2p3p1/Pp1p2q1/1P1b4/8/6BP/5Q1K w - - 1 38", listOf("f1f8", "g8f8", "c7e6", "f8e7", "e6g5"), "Fork", "Rating 1433 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(564, "R7/2P3pp/1p2b3/1k6/7P/1P4B1/2r2PK1/8 b - - 0 33", listOf("e6d5", "g2h3", "d5a8"), "Fork", "Rating 1161 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(565, "r4r2/1pq1bp1k/2p4p/p1n1N2p/P1P1P3/6Q1/2P3PP/1R3R1K w - - 0 23", listOf("f1f7", "f8f7", "g3g6", "h7h8", "e5f7"), "Fork", "Rating 1475 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(566, "6k1/pp5p/4p1p1/5pN1/1P1q4/P5bP/2Q2P2/6K1 w - - 0 28", listOf("c2c8", "g8g7", "g5e6", "g7f7", "e6d4"), "Fork", "Rating 1167 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(567, "4n3/1p1n4/p1p5/P2P1kp1/B1P5/3K4/5P1B/8 b - - 0 37", listOf("d7c5", "d3c3", "c5a4"), "Fork", "Rating 1172 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(568, "r4rk1/ppp2ppp/3q4/8/3n1BQ1/3P4/PPP2PPP/R4RK1 b - - 3 14", listOf("d6f4", "g4f4", "d4e2", "g1h1", "e2f4"), "Fork", "Rating 1459 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(569, "6rk/5p2/4pPq1/3pP2p/P7/2P2N1P/1P3Qr1/5R1K w - - 0 42", listOf("f3h4", "g2f2", "h4g6", "g8g6", "f1f2"), "Fork", "Rating 1488 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(570, "6k1/1p1r1p1p/pq3p2/3bp3/P3Q3/2P2B1P/1P3PP1/5RK1 w - - 0 25", listOf("e4g4", "g8f8", "g4d7"), "Fork", "Rating 1178 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(571, "4r2r/p4p1p/7p/8/1k3n2/8/P3B1PP/2R1R1K1 w - - 2 26", listOf("c1c4", "b4a3", "c4f4"), "Fork", "Rating 1184 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(572, "6r1/p7/PpN2k2/2pP1b1p/2P1p2p/2P1K3/6P1/5R2 w - - 6 35", listOf("f1f5", "f6f5", "c6e7", "f5f6", "e7g8"), "Fork", "Rating 1190 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(573, "1r3rk1/6pp/8/3qpN2/1n4P1/pQ1P4/P6P/K3R3 w - - 0 34", listOf("f5e7", "g8h8", "e7d5", "b4d5", "b3d5"), "Fork", "Rating 1195 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(574, "8/p3p2p/6p1/8/1kP1n1P1/1P2p2P/P2bK3/3RB3 b - - 2 37", listOf("e4c3", "e2d3", "c3d1"), "Fork", "Rating 1203 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(575, "2r2k2/5pp1/pp2pq2/3rN3/4Q3/P4P2/1PP3PP/4RK2 w - - 3 30", listOf("e4d5", "e6d5", "e5d7", "f8g8", "d7f6"), "Fork", "Rating 1498 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(576, "8/8/p3p3/2p1k3/7r/1KPR3P/P1P5/8 b - - 7 35", listOf("c5c4", "b3b2", "c4d3"), "Fork", "Rating 1213 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(577, "r3k2b/1p2p3/p1p3p1/7q/3Pp2Q/4P3/PPP2P2/2K3R1 w q - 2 25", listOf("h4h5", "g6h5", "g1g8", "e8d7", "g8a8"), "Fork", "Rating 1196 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(578, "2b2r1k/2Q3p1/1p1p2qp/p2Pp3/1P3p2/P3P1PP/1B5K/2R3R1 b - - 0 29", listOf("f4g3", "g1g3", "f8f2", "h2h1", "g6g3", "c7c8", "h8h7"), "Fork", "Rating 1223 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(579, "3q2k1/3r2pp/4N3/p4p2/8/P1Q4P/1B4P1/6K1 b - - 2 33", listOf("d7d1", "g1h2", "d8d6", "c3e5", "d6e5"), "Fork", "Rating 1233 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(580, "2r5/5kpp/4bn2/p1q5/P2R4/2n5/1B1Q1PPP/R5K1 b - - 1 28", listOf("c5d4", "d2d4", "c3e2", "g1f1", "e2d4"), "Fork", "Rating 1522 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(581, "r2q4/ppp2pbk/3p2pp/8/2PB2b1/5N2/PP1Q1PPP/4R1K1 w - - 1 18", listOf("d4g7", "h7g7", "d2d4", "d8f6", "d4g4"), "Fork", "Rating 1243 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(582, "8/k1p5/2P5/q7/4N3/4K3/pR3P2/R7 b - - 1 47", listOf("a5a3", "e3f4", "a3b2"), "Fork", "Rating 1253 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(583, "5rk1/1R3ppp/p2p4/2pb1N2/P7/8/PB1b2PP/7K w - - 2 30", listOf("f5e7", "g8h8", "e7d5"), "Fork", "Rating 1262 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(584, "2rqk1nr/p2nb1pp/4Q3/1N6/3P1B2/5P2/1P5P/R3K1Nb w Qk - 1 15", listOf("b5d6", "e8f8", "e6f7"), "Fork", "Rating 1271 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(585, "rn1qkb1r/pb1p1p1p/1p4p1/2p4Q/2B1P3/8/PPP2PPP/R1B1K1NR w KQkq - 0 10", listOf("h5e5", "d8e7", "e5h8"), "Fork", "Rating 1281 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(586, "2k3n1/ppp3p1/3p4/2qN1b2/2Pr1P2/1K1B4/PP6/R1BQR3 b - - 2 24", listOf("d4d3", "d1d3", "f5d3"), "Fork", "Rating 1290 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(587, "1r5r/pp1k1pp1/2nb4/3p4/3P1P2/1P3Bpq/PBP3Q1/RN3K2 w - - 5 22", listOf("g2h3", "h8h3", "f3g4", "d7c7", "g4h3"), "Fork", "Rating 1298 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(588, "2R5/7p/p5nk/3p1Q2/1b1P2P1/5BKP/3q1P2/8 b - - 3 34", listOf("b4d6", "g3g2", "g6h4", "g2f1", "h4f5"), "Fork", "Rating 1307 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(589, "r4rk1/p1pb1p1p/1p4pB/3NPq1P/3P4/5QKP/P7/8 w - - 2 22", listOf("d5e7", "g8h8", "e7f5"), "Fork", "Rating 1316 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(590, "2b2rk1/1p3ppp/8/1P1p2q1/3PPnn1/2N1B2P/2P2P2/Q3KBR1 b - - 0 20", listOf("g4e3", "g1g5", "e3c2", "e1d2", "c2a1"), "Fork", "Rating 1586 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(591, "3r1rk1/p3q1pp/1pn5/4pp2/1b6/4P1P1/PB2QPNP/R2R2K1 w - - 0 19", listOf("e2c4", "g8h8", "c4c6"), "Fork", "Rating 1325 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(592, "r5k1/1ppq1p2/3p2pb/p2P2Np/2P1r3/1P4P1/P2Q1PK1/3R3R w - - 2 28", listOf("g5e4", "h6d2", "e4f6", "g8g7", "f6d7"), "Fork", "Rating 1593 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(593, "1r2q1k1/5p1p/1p1p2p1/pPpB1n2/2Pb1P2/3P2PP/P1QB2K1/4R3 b - - 1 23", listOf("e8e1", "d2e1", "f5e3", "g2f3", "e3c2"), "Fork", "Rating 1599 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(594, "2b1k3/p4p2/1q2pQp1/3pP3/P1pP4/NrP1P3/6P1/R4K2 b - - 5 24", listOf("b3a3", "a1a3", "b6b1", "f1f2", "b1b2", "f2g3", "b2a3"), "Fork", "Rating 1341 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(595, "8/p5p1/6k1/2Pn2N1/1r3PK1/7P/8/5R2 b - - 7 41", listOf("d5e3", "g4f3", "e3f1"), "Fork", "Rating 1350 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(596, "3rr1k1/4Rp1p/p4p2/1p6/6P1/1B5P/PPP2P2/2K5 w - - 3 22", listOf("b3f7", "g8f8", "e7e8", "d8e8", "f7e8"), "Fork", "Rating 1359 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(597, "1k1r4/ppprp3/7p/3P1Qp1/2P5/1P3PBP/P2q2PN/R5K1 b - - 0 24", listOf("d2d4", "g3f2", "d4a1"), "Fork", "Rating 1368 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(598, "1r5r/pQ1Rnkp1/4q1p1/4P1P1/5P1p/P6P/1P6/1K5R w - - 3 30", listOf("d7e7", "e6e7", "e5e6", "f7f8", "b7b8"), "Fork", "Rating 1626 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(599, "r5r1/p1p3kp/4p3/1pb5/2p2R1B/2n5/P2N1PPP/R5K1 w - - 0 21", listOf("h4f6", "g7g6", "f6c3"), "Fork", "Rating 1377 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(600, "5rk1/3Q1ppp/p1N5/1p1n4/3Pq3/PP6/6PP/5R1K w - - 7 28", listOf("d7d5", "e4d5", "c6e7", "g8h8", "e7d5"), "Fork", "Rating 1632 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk8(): List<Puzzle> {
        return listOf(
            Puzzle(601, "7k/R6p/6p1/3Nn3/2PK4/1P6/P4P2/4r3 b - - 12 36", listOf("e5c6", "d4c5", "c6a7"), "Fork", "Rating 1394 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(602, "5rk1/ppq1n2p/3p1Qb1/7p/3NP3/P5RP/5PP1/6K1 w - - 7 29", listOf("f6f8", "g8f8", "d4e6", "f8f7", "e6c7"), "Fork", "Rating 1647 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(603, "3rr1k1/ppp2p1p/6p1/6n1/8/P1B1PP2/1P3PK1/2RR4 w - - 2 21", listOf("d1d8", "e8d8", "c3f6", "d8d7", "f6g5"), "Fork", "Rating 1402 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(604, "rn2kb1r/pp2pppp/2p2n2/6N1/3qp3/7Q/PPP2P1P/RNB1K2R w KQkq - 0 10", listOf("h3c8", "d4d8", "c8d8", "e8d8", "g5f7", "d8e8", "f7h8"), "Fork", "Rating 1410 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(605, "r2qr1k1/ppp3pp/2n2n2/2b2bB1/3p4/2N2N2/PPP1Q1PP/2KR1B1R w - - 0 12", listOf("e2c4", "g8h8", "c4c5"), "Fork", "Rating 1419 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(606, "8/4k1p1/Bpb4p/1N1p4/1P1P4/1n3KP1/5P1P/8 b - - 8 36", listOf("c6b5", "a6b5", "b3d4", "f3e3", "d4b5"), "Fork", "Rating 1427 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(607, "7r/2q1p1k1/2p2pp1/p1Pn3r/3PR3/1PN3Pp/4QP1P/3R2K1 w - - 6 42", listOf("c3d5", "h5d5", "e4e7", "c7e7", "e2e7"), "Fork", "Rating 1436 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(608, "8/p6p/1p2k3/2p5/2Prp3/1P1r4/P2R1RPP/4K3 b - - 1 34", listOf("e4e3", "d2d3", "e3f2", "e1f2", "d4d3"), "Fork", "Rating 1675 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(609, "2k5/2p3pp/4P3/p2R1P2/8/6KP/rp4P1/8 w - - 0 36", listOf("e6e7", "b2b1q", "e7e8q", "c8b7", "d5b5", "b1b5", "e8b5"), "Fork", "Rating 1565 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(610, "2r5/1b3pk1/p1r3pp/1p1pPp2/1P1P1P2/P1R1nN1P/4NKP1/2R5 b - - 3 31", listOf("c6c3", "e2c3", "c8c3", "c1c3", "e3d1", "f2e1", "d1c3"), "Fork", "Rating 1445 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(611, "rnb1k2r/1p3ppp/p2p1n2/q7/3QP3/2PN4/P1P2PPP/R1B1KB1R b KQkq - 1 10", listOf("b8c6", "d4e3", "a5c3", "c1d2", "c3a1"), "Fork", "Rating 1454 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(612, "r1b1r1k1/ppp2ppp/8/3n4/1n6/N3BNPP/P4PB1/R3K2R b KQ - 1 20", listOf("d5e3", "f2e3", "e8e3", "e1f2", "e3a3"), "Fork", "Rating 1462 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(613, "r2q1r2/ppp1npkp/3p2p1/3Np1Q1/4B2P/1P1P1bP1/P1P1PP2/4K2R w K - 0 15", listOf("g5f6", "g7g8", "d5e7", "d8e7", "f6e7"), "Fork", "Rating 1471 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(614, "1r1q4/1p3pkp/p4bp1/3B1b2/P1Pn1N2/3P2P1/3Q1P2/1R2R1K1 b - - 9 22", listOf("d8d5", "c4d5", "d4f3", "g1g2", "f3d2"), "Fork", "Rating 1478 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(615, "2r2rk1/1p2bppp/pq6/3pPp2/1PnP4/P1N1B2P/4Q1PN/2R3K1 w - - 0 22", listOf("c3d5", "b6e6", "d5e7", "e6e7", "c1c4", "c8c4", "e2c4"), "Fork", "Rating 1487 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(616, "6r1/1p5Q/3kpNP1/p1pp1n2/3q1P2/3P4/1P4K1/2R3R1 b - - 7 43", listOf("g8g6", "h7g6", "f5h4", "g2h1", "h4g6"), "Fork", "Rating 1731 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(617, "3b4/1p3k1p/2p3p1/p1P1p3/2N1n2P/1P1B2P1/P2K1Bb1/8 w - - 0 33", listOf("d3e4", "g2e4", "c4d6", "f7e6", "d6e4"), "Fork", "Rating 1496 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(618, "5rk1/2R2ppb/5q1p/5P2/4P3/P6P/6P1/4QRK1 b - - 0 29", listOf("f6b6", "g1h2", "b6c7"), "Fork", "Rating 1506 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(619, "r3kr2/ppp4p/2n1p1p1/1B2Q3/5P1q/8/PPPP2KP/RNB4R b q - 0 14", listOf("h4g4", "g2f2", "f8f4", "f2e1", "f4e4", "e5e4", "g4e4"), "Fork", "Rating 1729 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(620, "r4r2/pp1b1pkp/1q2pnp1/3pN3/8/PP1B1Q2/2P2PPP/R3R1K1 w - - 2 17", listOf("f3f6", "g7f6", "e5d7", "f6e7", "d7b6"), "Fork", "Rating 1515 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(621, "8/p4nkp/1p2rrp1/8/8/1PQ1R2K/P6P/8 b - - 0 40", listOf("e6e3", "c3e3", "f6f3", "e3f3", "f7g5", "h3g4", "g5f3"), "Fork", "Rating 1755 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(622, "8/6pp/pr1k1p2/R2p4/P1P5/8/5KPP/8 w - - 0 29", listOf("c4c5", "d6c6", "c5b6"), "Fork", "Rating 1523 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(623, "8/6p1/6k1/1p6/3P1pp1/1q6/4Q1K1/4R3 b - - 1 50", listOf("f4f3", "g2f1", "f3e2"), "Fork", "Rating 1532 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(624, "2kr3r/ppp2p1p/2n2p2/8/1b1Pq3/1Q2PN2/PP2KPPP/2R2B1R b - - 3 12", listOf("c6d4", "f3d4", "d8d4", "b3c2", "d4d2", "c2d2", "b4d2"), "Fork", "Rating 1540 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(625, "8/pp3ppk/8/3RP3/4nP2/P4N2/4K1PP/2r5 b - - 2 29", listOf("e4c3", "e2d2", "c1d1", "d2c3", "d1d5"), "Fork", "Rating 1548 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(626, "3Q1r1k/7p/1p2r2b/1Np1pn2/P1P1P1R1/1P3P1P/2K5/5R2 b - - 0 41", listOf("f5e3", "c2b1", "f8d8"), "Fork", "Rating 1768 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(627, "5bk1/7p/1Q4p1/5p2/P1P3q1/1PB5/5P2/5K2 b - - 2 40", listOf("g4h3", "f1g1", "h3c3"), "Fork", "Rating 1557 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(628, "2r4k/1p5p/p3Q1p1/P2p1pPP/1P1q1P2/5R2/2R5/7K b - - 0 40", listOf("d4d1", "h1g2", "c8c2", "f3f2", "d1g4"), "Fork", "Rating 1776 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(629, "r6r/2pk2pp/Q1nq1p1n/1B1p1b2/3P3N/2P4P/PP3PP1/3KR2R w - - 5 21", listOf("b5c6", "d6c6", "e1e7", "d7e7", "a6c6"), "Fork", "Rating 1565 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(630, "1r1r1n2/2q2bkp/p4pp1/P1N2n2/2ppN3/6P1/2Q1PPBP/R4RK1 w - - 0 24", listOf("c5a6", "c7a7", "a6b8"), "Fork", "Rating 1573 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(631, "1k1r1r2/pppqn1p1/1b5p/P2pP3/8/3B1Q2/1B3PPP/R1R3K1 w - - 1 20", listOf("a5b6", "f8f3", "b6c7", "d7c7", "c1c7"), "Fork", "Rating 1815 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(632, "1r3rk1/p3qppp/Q3p3/8/P7/2N1K3/1P1R2PP/7R b - - 6 22", listOf("e7g5", "e3e2", "g5g2", "e2e1", "g2h1"), "Fork", "Rating 1581 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(633, "4R3/3N2pp/p3p1k1/8/4qp2/8/P2R4/3K4 w - - 0 48", listOf("e8e6", "e4e6", "d7f8", "g6f7", "f8e6"), "Fork", "Rating 1822 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(634, "r1b1r1kb/p3Bp2/2pp1Ppp/6Q1/q3P1n1/3B4/P1P3PP/R4R1K w - - 0 20", listOf("g5g6", "f7g6", "f6f7", "g8h7", "f7e8q"), "Fork", "Rating 1723 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(635, "7r/1Q2b1k1/4Ppp1/p1p3p1/2Pp1q2/P4P2/1P2R3/4KR2 b - - 1 29", listOf("f4c1", "e1f2", "h8h2", "f2g1", "h2h1", "g1h1", "c1f1", "h1h2", "f1e2"), "Fork", "Rating 1589 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(636, "r4rk1/3n1p2/p4P1p/4q2n/1P6/1B3R1P/1QP3PK/5R2 w - - 3 28", listOf("b2e5", "d7e5", "f3f5", "h5g3", "h2g3"), "Fork", "Rating 1597 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(637, "6k1/5ppp/p3p3/2b5/2P2Nn1/1P3RPK/PB1r3P/R7 b - - 6 28", listOf("d2h2", "h3g4", "f7f5", "g4g5", "c5e7", "b2f6", "g7f6"), "Fork", "Rating 1841 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(638, "3r2r1/b5p1/p1pNkpQ1/1p6/5q2/1P5P/P4PP1/3R2K1 w - - 0 37", listOf("g6f7", "e6e5", "f7e7"), "Fork", "Rating 1606 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(639, "rn1qk2r/p1p1bppp/bp2pn2/8/3P4/2P3N1/PP3PPP/RNBQKB1R w KQkq - 1 8", listOf("f1a6", "b8a6", "d1a4", "d8d7", "a4a6"), "Fork", "Rating 1614 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(640, "2r2qk1/R5p1/1p6/5p2/7Q/B1pP4/P7/6K1 b - - 0 40", listOf("f8a3", "a7a3", "c3c2", "h4f4", "c2c1q", "f4c1", "c8c1"), "Fork", "Rating 1766 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(641, "3r2k1/2R2pp1/b3pq1p/p7/2PrB3/P3Q2P/1P3PP1/2R3K1 b - - 4 26", listOf("d4d1", "c1d1", "d8d1", "g1h2", "f6e5", "e3g3", "e5e4"), "Fork", "Rating 1622 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(642, "8/8/4K1R1/2kN4/4P1P1/r4n2/8/8 b - - 8 61", listOf("a3a6", "e6f5", "f3h4", "f5g5", "h4g6"), "Fork", "Rating 1630 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(643, "7k/pp2qppp/3b4/3Pp3/5BQ1/6P1/PPr2P1P/2R3K1 b - - 1 23", listOf("e5f4", "c1c2", "e7e1", "g1g2", "e1e4", "g2h3", "e4c2"), "Fork", "Rating 1881 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(644, "5r2/5Rbk/p5pp/1p6/5B2/8/Pr3PPP/4R1K1 w - - 1 26", listOf("f7g7", "h7g7", "f4e5", "g7g8", "e5b2"), "Fork", "Rating 1639 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(645, "8/1p6/2kp4/p4p1p/P1K2P1P/1P2R1P1/8/3r4 b - - 0 38", listOf("d6d5", "c4c3", "d5d4", "c3c2", "d4e3"), "Fork", "Rating 1648 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(646, "6k1/7p/p1pP2p1/7R/bP2pP2/2QpP3/q7/2K1N2R b - - 0 44", listOf("d3d2", "c3d2", "a2a1"), "Fork", "Rating 1657 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(647, "4rk2/p4q2/1pp2Q2/2N5/3P4/7P/PP5K/8 w - - 1 42", listOf("c5d7", "f8g8", "f6g5", "g8h8", "g5h6", "f7h7", "h6h7", "h8h7", "d7f6", "h7g6", "f6e8"), "Fork", "Rating 1850 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(648, "r5k1/2r5/3R1Pp1/p3p3/R1b1P1B1/8/7P/6K1 w - - 0 31", listOf("a4c4", "c7c4", "g4e6", "g8h8", "e6c4"), "Fork", "Rating 1667 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(649, "4r1k1/4Q1pp/2q2p2/3p4/1Pb5/6N1/5PPP/2R3K1 w - - 2 30", listOf("g3f5", "e8e7", "f5e7", "g8f7", "e7c6"), "Fork", "Rating 1916 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(650, "r2qkbnr/4pppp/p1n5/1NpP1b2/B7/5N2/PPP2PPP/R1BQK2R b KQkq - 0 9", listOf("a6b5", "a4b5", "d8a5", "c2c3", "a5b5"), "Fork", "Rating 1677 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(651, "3r3k/1b4p1/p1q4p/5r2/3n4/P4BQP/1PP2PPN/1R3RK1 b - - 3 24", listOf("f5f3", "h2f3", "d4e2", "g1h2", "e2g3"), "Fork", "Rating 1925 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(652, "r4rk1/ppp1bbpp/3q4/2Q1p3/3PPp2/P1N2P1R/1P3BP1/3R1KN1 b - - 3 26", listOf("d6a6", "c5b5", "f7c4", "b5c4", "a6c4"), "Fork", "Rating 1686 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(653, "6k1/1p2qppp/4p3/3r4/1PN1Q3/P3P2P/4nPP1/R4K2 b - - 7 27", listOf("e2g3", "f2g3", "e7f6", "f1e2", "f6a1"), "Fork", "Rating 1933 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(654, "8/pp2Qpkp/3p4/2nNp1pP/4q3/2P3P1/1P6/2K2R1R b - - 1 43", listOf("c5b3", "c1d1", "e4d3", "d1e1", "d3d2"), "Fork", "Rating 1697 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(655, "8/2r3kp/5Rp1/3pPpPP/3P1N2/4q3/2r3PK/5R2 w - - 5 44", listOf("f4e6", "g7h8", "f6f8"), "Fork", "Rating 1707 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(656, "r4rk1/4bppp/pqpBbn2/8/Q3P3/2N5/PP2BPPP/R2R2K1 b - - 0 14", listOf("e7d6", "d1d6", "b6b2", "a1d1", "b2c3"), "Fork", "Rating 1719 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(657, "rn1qkb1r/p2ppppp/b4n2/1NpP4/2B1P3/8/PPP2PPP/R1BQK1NR b KQkq - 2 8", listOf("a6b5", "c4b5", "d8a5", "c2c3", "a5b5"), "Fork", "Rating 1730 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(658, "6k1/5pbp/4b1p1/8/nP1N4/2PpB3/r2N1PPP/2R3K1 b - - 3 26", listOf("a4c3", "d4e6", "c3e2", "g1f1", "e2c1"), "Fork", "Rating 1968 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(659, "2qr2k1/p3rpp1/1p5p/3pRQ1n/3N4/P1P4P/1P4P1/1B2R2K b - - 0 32", listOf("h5g3", "h1g1", "g3f5"), "Fork", "Rating 1741 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(660, "2r3k1/8/4b3/pp1p2pQ/3q4/P7/1P5P/5R1K w - - 2 31", listOf("h5g6", "d4g7", "g6e6"), "Fork", "Rating 1752 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(661, "r1b1k2r/1p3pp1/p2bpq1p/3pN2Q/3n1P2/2NB2P1/PP3P1P/R4RK1 w kq - 1 14", listOf("e5f7", "f6f7", "d3g6", "e8g8", "g6f7"), "Fork", "Rating 1764 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(662, "5rk1/1R3pp1/4b2p/1Q2N3/3pn3/1PqN3P/2r2PP1/5RK1 b - - 4 25", listOf("e4d6", "b5b4", "d6b7"), "Fork", "Rating 1777 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(663, "7k/5rp1/b1p4p/p3n2Q/4qN2/1P4PB/P4B1P/6K1 w - - 0 33", listOf("h5e5", "e4e5", "f4g6", "h8h7", "g6e5"), "Fork", "Rating 1789 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(664, "6rr/pBp2k1p/3pbn2/6Bq/1b5P/6P1/PPP1PP2/R2Q1RK1 w - - 1 17", listOf("g5f6", "f7f6", "d1d4", "h5e5", "d4b4"), "Fork", "Rating 1802 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(665, "7k/p1p2p2/1p3qp1/4n2p/4PP2/1QP1N1NP/PP1r2P1/4R1K1 b - - 0 27", listOf("f6f4", "g3f1", "f4f2", "g1h1", "f2e1"), "Fork", "Rating 1978 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(666, "2R2bk1/p5p1/4N3/1pN5/1P1n1P2/P3B1Kp/2q4P/5R2 b - - 2 33", listOf("c2g2", "g3h4", "d4f5", "h4h5", "g7g6"), "Fork", "Rating 1814 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(667, "4k2r/pp3pp1/r3p2p/8/b1Pp2P1/2qP3P/1R1Q1P2/1K3BNR b k - 7 26", listOf("a4c2", "d2c2", "c3e1", "c2c1", "a6a1", "b1a1", "e1c1", "a1a2", "c1f1"), "Fork", "Rating 2035 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(668, "6k1/p4p2/1p3p1p/2p3n1/5P2/2P5/PPB4K/4Q3 b - - 0 35", listOf("g5f3", "h2g3", "f3e1", "c2f5", "c5c4"), "Fork", "Rating 1824 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(669, "3b4/5pp1/7p/8/1N1k2P1/5P1P/8/6K1 w - - 3 35", listOf("b4c6", "d4e3", "c6d8", "e3f3", "g1h2"), "Fork", "Rating 1838 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(670, "2r2rk1/4p2p/1q1pp1p1/1p1B1b2/p7/2P1RP2/P2Q2PP/4R2K w - - 0 24", listOf("e3e6", "f5e6", "d5e6", "g8g7", "e6c8"), "Fork", "Rating 1854 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(671, "3r3k/1pN3p1/p1n4p/6qP/2B1P1b1/5Q2/1B3P2/R3K1R1 b Q - 0 26", listOf("g5d2", "e1f1", "g4f3", "b2g7", "h8h7", "c7d5", "d8d5"), "Fork", "Rating 2032 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(672, "3r4/p2r2pp/2Qbkp2/4p3/8/2N1q2P/PPP5/RK1R4 w - - 7 27", listOf("c6c4", "e6e7", "c3d5", "e7f8", "d5e3"), "Fork", "Rating 1869 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(673, "3R2r1/B1p1kp1p/Bpp1b3/8/6q1/P1Q3P1/2P2P1P/4K2R b K - 0 19", listOf("g4e4", "e1d2", "g8d8", "a6d3", "e4h1"), "Fork", "Rating 1885 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(674, "6k1/pp1q1p2/2p2Bn1/6RQ/4r2P/8/PPP3P1/7K b - - 1 27", listOf("e4e1", "h1h2", "d7d6", "f6e5", "e1e5"), "Fork", "Rating 1901 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(675, "3q1bk1/1b1B3p/4p1p1/p2pN3/3Pn3/5Q1P/6PK/8 w - - 4 33", listOf("d7e6", "g8h8", "e5f7", "h8g7", "f7d8"), "Fork", "Rating 1913 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk9(): List<Puzzle> {
        return listOf(
            Puzzle(676, "4r2k/pp5p/1qnp1ppB/3B4/2Pb4/6P1/PP1Q1P1P/4R1K1 b - - 3 20", listOf("d4f2", "d2f2", "e8e1", "g1g2", "b6f2"), "Fork", "Rating 1925 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(677, "5qk1/1p5p/p2p2pb/3N4/2P5/1P1p1rP1/PQ3PKP/4R3 b - - 3 29", listOf("d3d2", "e1f1", "f3f2", "f1f2", "f8f2", "g2f2", "d2d1n", "f2f3", "d1b2"), "Fork", "Rating 2078 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(678, "r3r1k1/1ppb1pq1/6p1/1P2b2p/2P4B/3B3P/P3RQP1/5RK1 w - - 3 26", listOf("e2e5", "e8e5", "h4f6", "e5f5", "d3f5", "g7f6", "f5d7"), "Fork", "Rating 1938 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(679, "r2q1b1r/1ppn4/p3kppp/3N4/3P1B2/5Q1P/PP1N1PP1/n5K1 w - - 0 20", listOf("d5c7", "e6f7", "f3d5", "f7g7", "c7e6"), "Fork", "Rating 1952 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(680, "5r2/1pp3k1/6p1/1PQ3n1/2n2qB1/2P3NP/6PK/3R4 b - - 2 30", listOf("f4g3", "h2g3", "g5e4", "g3h2", "e4c5"), "Fork", "Rating 1965 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(681, "r1bq1rk1/pp3ppp/2n1pn2/3P4/3N4/P1B3P1/1PQ1PP1P/R3KB1R b KQ - 0 11", listOf("c6d4", "c3d4", "d8d5", "d4f6", "d5h1"), "Fork", "Rating 2121 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(682, "2k1r2r/1p1b4/2p2q2/p1Np1p2/PbpP1Q2/5BPp/1P3P1K/R4R2 w - - 4 26", listOf("f4b8", "c8b8", "c5d7", "b8c8", "d7f6"), "Fork", "Rating 1980 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(683, "rn5r/p1pp1k1n/1p2p1pp/q3P2B/2PP1P2/2P3P1/P1Q4P/R3K2b w Q - 0 17", listOf("c2g6", "f7e7", "g6g7", "e7d8", "g7h8", "h7f8", "h8f8"), "Fork", "Rating 1994 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(684, "r7/pp4k1/2p2qb1/6p1/6P1/1Q6/PPP5/2K4R w - - 2 29", listOf("b3b7", "f6f7", "b7a8", "f7f4", "c1b1", "g6c2", "b1a1"), "Fork", "Rating 2011 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(685, "4rbk1/pp1n1ppp/1qp5/5N2/3Pp3/2P2Q1P/PP3PP1/R1B2K2 w - - 0 18", listOf("f5h6", "g7h6", "f3g4", "g8h8", "g4d7"), "Fork", "Rating 2193 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(686, "3r4/4r1kp/ppP2q2/2nBb1p1/2P2p2/7P/P3RPP1/1Q2R1K1 w - - 6 38", listOf("b1b6", "d8d5", "c4d5"), "Fork", "Rating 2029 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(687, "3rkb2/pp1qpn2/8/2pN3Q/8/3B4/PPP2P2/2K5 w - - 1 26", listOf("d3b5", "f8h6", "c1b1", "d7b5", "d5c7", "e8f8", "c7b5"), "Fork", "Rating 2048 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(688, "r2qr3/1p3kpp/p1n5/2b5/8/1P2p2P/PBP2PP1/R2QR1K1 w - - 0 20", listOf("d1h5", "f7g8", "h5c5", "e3f2", "c5f2"), "Fork", "Rating 2065 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(689, "r3k2r/p4ppp/2p1pb2/3p4/Q1Pq4/1PN5/P1K2PPP/5B1R w kq - 2 18", listOf("a4c6", "e8e7", "c6b7", "e7d8", "b7a8"), "Fork", "Rating 2082 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(690, "r3k2r/pp3ppp/1qp1b3/2bpP3/8/2N1B3/PPP2PPP/R2QR1K1 b kq - 7 12", listOf("d5d4", "c3a4", "d4e3"), "Fork", "Rating 2099 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(691, "2rQ1qk1/r1P2pp1/8/8/7p/n5B1/P4PPP/3R2K1 w - - 2 32", listOf("d8c8", "f8c8", "d1d8", "g8h7", "d8c8", "h4g3", "c8h8", "h7h8", "c7c8q"), "Fork", "Rating 2246 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(692, "r4rk1/p4ppp/4q3/3pP1b1/3P1p1n/2NQ4/PP1B1PPP/R4RK1 b - - 5 20", listOf("e6g4", "d3h3", "g4h3", "g2h3", "h4f3", "g1g2", "f3d2"), "Fork", "Rating 2124 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(693, "6k1/pp2b1pp/1q2b3/4N2Q/PP1P4/4r3/6PP/R5K1 w - - 0 27", listOf("h5e8", "e7f8", "a1f1", "b6d6", "f1f8", "d6f8", "e8e6"), "Fork", "Rating 2150 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(694, "r3r1k1/4qpb1/3p3p/p1pP3p/1p2NP2/3Q1P2/PPPB4/2K3R1 w - - 2 24", listOf("f4f5", "g8h8", "g1g7", "h8g7", "f5f6", "e7f6", "e4f6"), "Fork", "Rating 2176 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(695, "5k2/2pr2b1/p5QP/1p2p3/8/2P1BK2/Pq3P2/8 w - - 1 36", listOf("e3c5", "f8g8", "g6e8", "g8h7", "e8d7", "b2c3", "c5e3"), "Fork", "Rating 2296 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(696, "r5k1/ppbn2pp/2p3n1/2Pp2q1/1P1Nr3/P3PQ2/1B4PP/R4RK1 w - - 4 21", listOf("f3f7", "g8h8", "d4e6", "g6e5", "e6g5"), "Fork", "Rating 2202 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(697, "r2q4/pp2n1k1/1bp1Q3/3p4/4n1P1/5P1P/PP2B1K1/R3R3 b - - 0 25", listOf("e7g6", "f3e4", "g6f4", "g2h1", "f4e6"), "Fork", "Rating 2342 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(698, "6r1/p4Q2/1p1q2rk/5R2/3Pp3/4P1P1/P6P/7K w - - 2 35", listOf("g3g4", "g6g5", "f5f6", "d6f6", "f7f6"), "Fork", "Rating 2230 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(699, "r4rk1/5pp1/p1n1p2p/3qN3/1p1PNP1Q/2P5/PP4PP/R5K1 w - - 1 20", listOf("e4f6", "g7f6", "e5g4", "f6f5", "g4f6", "g8g7", "f6d5"), "Fork", "Rating 2369 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(700, "2r3k1/5pp1/2r1p1p1/1p2N3/p2P1Q2/P3P2P/1P3PPK/3q4 w - - 0 30", listOf("f4f7", "g8h8", "e5c6", "c8c6", "f7e8", "h8h7", "e8c6"), "Fork", "Rating 2325 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(701, "6k1/ppp1r2p/3p1qp1/5b2/8/1PP1N3/P2Q1PPP/R1n3K1 w - - 0 20", listOf("e3d5", "c1e2", "g1f1", "f6e6", "d5e7"), "Fork", "Rating 2262 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(702, "r2q1r2/p1p1k3/3nP1Q1/2N5/7b/8/PPP2PPP/RNB2K2 b - - 0 19", listOf("f8f2", "f1g1", "f2f1", "g1f1", "d8f8", "f1e2", "f8f2", "e2d3", "f2f1"), "Fork", "Rating 2400 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(703, "r5k1/p6p/2p1n2Q/3pnbp1/8/6P1/P4PBP/6K1 w - - 2 28", listOf("h6f6", "e6g7", "f6e5", "a8e8", "e5c3"), "Fork", "Rating 2296 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(704, "r2k1b1r/ppp1n2Q/3qb1n1/3p3B/3P4/8/PPP3PP/RNB2RK1 w - - 1 17", listOf("h7h8", "g6h8", "f1f8", "d8d7", "f8a8"), "Fork", "Rating 2298 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(705, "2k2r1r/1pq3p1/p3p3/6Bp/4P1n1/P2B3P/1P2RPPb/R2Q1K2 b - - 3 20", listOf("g4f2", "e2f2", "f8f2", "f1f2", "c7g3", "f2e2", "g3g2"), "Fork", "Rating 2483 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(706, "4rr1k/6pp/2p5/pp1q2B1/3Pp2n/1PP5/P1QN1PPP/4RRK1 b - - 0 24", listOf("d5g5", "g2g3", "g5d2", "c2d2", "h4f3", "g1g2", "f3d2"), "Fork", "Rating 2395 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(707, "3r2k1/2p3pp/1p6/2b2p1P/P4P2/2NrPQ2/q2P1K2/1RB4R b - - 1 30", listOf("d3c3", "b1b2", "a2b2", "c1b2", "d8d2", "f2g3", "c3e3", "f3e3", "c5e3"), "Fork", "Rating 2513 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(708, "kn6/2QP2pp/1p2Pb2/RP3p2/7q/5r2/6PP/6KR b - - 0 32", listOf("b6a5", "g2f3", "h4e1", "g1g2", "e1e2", "g2g3", "f6e5", "c7e5", "e2e5"), "Fork", "Rating 2496 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(709, "6r1/1pq4k/p1p4r/2PnQp2/1P3Pbp/P7/1B1R3K/4R3 w - - 2 41", listOf("e5c7", "d5c7", "e1e7", "h7g6", "d2d8", "h6h8", "d8d6", "g6h5", "b2h8"), "Fork", "Rating 2967 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(710, "2Qr1k1q/p3p1r1/1p3bBB/7P/8/2P5/PPK3P1/8 w - - 2 31", listOf("c8d8"), "Mate in 1", "Rating 477 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(711, "1R6/5k2/1p3N2/3pPp2/1P1P1r2/2P1n3/7P/7K b - - 4 49", listOf("f4f1"), "Mate in 1", "Rating 399 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(712, "1Qbr2k1/1pp2ppp/8/4p3/1P6/8/1Pp1NPPP/2BNK2R b K - 0 16", listOf("c2d1q"), "Mate in 1", "Rating 400 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(713, "r1b3k1/ppp1nrpp/2p5/8/4q3/1BP1P3/PP4PP/RN1Q2K1 w - - 0 14", listOf("d1d8"), "Mate in 1", "Rating 851 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(714, "2kr3r/p2q4/5p2/2pb2pp/3p4/P2P1NQ1/2P3PP/1R3RK1 w - - 2 31", listOf("g3b8"), "Mate in 1", "Rating 463 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(715, "r1b2rk1/pp3pp1/1npq3p/3p4/3P2n1/2NBP3/PPQ1NPPP/4RRK1 b - - 1 14", listOf("d6h2"), "Mate in 1", "Rating 493 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(716, "2r2rk1/pbq1bp1p/1p4p1/2pp1PB1/6nP/2P1P3/PP1NQ1P1/1B2RRK1 b - - 2 18", listOf("c7h2"), "Mate in 1", "Rating 515 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(717, "rn3r1k/1p4pB/2pp4/1p2P1N1/6b1/2P1P3/P1P3PP/5RK1 w - - 0 18", listOf("f1f8"), "Mate in 1", "Rating 533 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(718, "r4rk1/1b1nqppp/1pp1p3/p2p4/2PP4/2P1PPP1/P1QN1KP1/2R2B1R w - - 0 17", listOf("c2h7"), "Mate in 1", "Rating 549 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(719, "1Q5b/8/4p1pk/4P2p/7P/5qP1/5r2/5RK1 w - - 0 48", listOf("b8h8"), "Mate in 1", "Rating 564 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(720, "r4rk1/1b3ppp/pb1pp3/1p2n3/1Pq1P3/2P3B1/P2Q1PPP/RB1N1R1K b - - 6 20", listOf("c4f1"), "Mate in 1", "Rating 576 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(721, "rn1q1rk1/pp3pp1/2p1P2p/8/1P1b3P/3Q2N1/PP4P1/R1B2R1K b - - 1 17", listOf("d8h4"), "Mate in 1", "Rating 588 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(722, "5k2/1R4pK/r7/7P/6P1/8/8/8 b - - 16 86", listOf("a6h6"), "Mate in 1", "Rating 599 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(723, "7k/1p3r1p/5q2/p1p1p2P/3pPnQ1/P1PP2R1/1P4P1/6K1 w - - 1 34", listOf("g4g8"), "Mate in 1", "Rating 609 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(724, "r1bq1rkn/pp2nppp/2p5/3pPP2/1bBPN1Q1/8/PPP3PP/R1B2RK1 w - d6 0 13", listOf("e4f6"), "Mate in 1", "Rating 1051 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(725, "2kr3r/ppp2p1p/3q1B2/3N1P2/4P1b1/3B1n2/PPP3P1/R2Q1R1K b - - 1 15", listOf("d6h2"), "Mate in 1", "Rating 619 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(726, "r1bq1rBk/3n1p2/1pp1p2p/p2pP3/2PP4/1PN3P1/PBQ2P2/R3K3 w Q - 0 17", listOf("c2h7"), "Mate in 1", "Rating 629 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(727, "2kr1bnr/pp3p2/2pp4/4qN2/4P1Q1/2N5/PPP2PP1/3R1RK1 b - - 0 17", listOf("e5h2"), "Mate in 1", "Rating 639 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(728, "Qnbk1r2/p2p2Bp/1p1qp3/8/6n1/3BP3/PPP2PPP/RN3RK1 b - - 0 17", listOf("d6h2"), "Mate in 1", "Rating 648 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(729, "r2qrbk1/pbpn1p1n/1p2p2Q/6N1/3P4/2N5/PPP3PP/R4R1K w - - 0 16", listOf("h6h7"), "Mate in 1", "Rating 658 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(730, "r3r1k1/pp3pp1/2p2Bqp/3p4/3PnP2/P2B1N1b/1P3PP1/2RQR1K1 b - - 0 20", listOf("g6g2"), "Mate in 1", "Rating 669 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(731, "2r3k1/p4ppp/Qp2p1q1/3p2r1/8/2P2R1P/PP4P1/R5K1 w - - 2 30", listOf("a6c8"), "Mate in 1", "Rating 679 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(732, "3r1k2/p1P1Rpp1/1p2b2p/4Pp2/8/1P5P/PB3PPK/4q3 w - - 0 28", listOf("c7d8q"), "Mate in 1", "Rating 1020 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(733, "rn2k2r/ppp2ppp/8/4p1B1/4n3/1BP5/P1P2PPP/3RK2R w Kkq - 0 13", listOf("d1d8"), "Mate in 1", "Rating 688 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(734, "5k2/p4p2/1p1p2pp/2pP4/2P2P2/1P2Q1Pb/P2R1K1P/q7 b - - 7 32", listOf("a1f1"), "Mate in 1", "Rating 698 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(735, "r2qkb1r/p2npppp/3p1n2/1Np5/8/5N2/PPPP1PPP/R1BQR1K1 w kq - 2 9", listOf("b5d6"), "Mate in 1", "Rating 1149 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(736, "5r1r/pp1k2p1/3qp1p1/2pp2P1/6Q1/3P4/PPP1NPP1/R4RK1 b - - 0 22", listOf("d6h2"), "Mate in 1", "Rating 707 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(737, "5rk1/ppq3p1/2p1N2p/8/2P3Q1/1P5P/P5P1/5R1K b - - 0 36", listOf("f8f1"), "Mate in 1", "Rating 715 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(738, "2k5/p1r3p1/1p2q1P1/8/Q2P4/2P5/R5PP/6K1 b - - 0 42", listOf("e6e1"), "Mate in 1", "Rating 724 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(739, "r2qkb1r/pp1npppp/2p2n2/1N3b2/2BP4/5P2/PPPBQ1PP/R3K1NR w KQkq - 2 10", listOf("b5d6"), "Mate in 1", "Rating 1172 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(740, "r4rk1/2qn1ppp/ppp1p3/5N2/2PP2n1/2N5/PP1B1PPb/R2QRB1K b - - 0 16", listOf("g4f2"), "Mate in 1", "Rating 732 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(741, "r2q1rk1/2p3pp/4P3/1p1n4/4pN2/pP2B1P1/PbP1Q2P/1K1R3R b - - 6 25", listOf("d5c3"), "Mate in 1", "Rating 740 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(742, "8/1p1R4/1p2rk1p/2p1N3/4nP2/P1P4b/1P5P/6K1 w - - 4 41", listOf("d7f7"), "Mate in 1", "Rating 748 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(743, "rnb3k1/ppp2rpp/8/1Pp5/6n1/1QP1Pq2/PB3P1P/2KR1R2 w - - 0 17", listOf("d1d8"), "Mate in 1", "Rating 755 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(744, "r4k2/1q1p1rp1/p1n1p3/1p2PpQ1/8/2P4R/P4PPP/R5K1 w - - 5 21", listOf("h3h8"), "Mate in 1", "Rating 763 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(745, "r5k1/ppQ2R2/6p1/4p1Pp/q3PPb1/2P5/PP5R/2K5 b - - 0 23", listOf("a4d1"), "Mate in 1", "Rating 770 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(746, "6rr/1n3k2/2ppp3/1pb1p3/p3P3/P1PP1PB1/BP3P2/R4RK1 b - - 0 25", listOf("g8g3"), "Mate in 1", "Rating 1224 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(747, "2br2kb/3rp2p/p1p1p1pB/1p5n/8/1BN2P2/PPP3PP/R3R1K1 w - - 0 18", listOf("b3e6"), "Mate in 1", "Rating 776 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(748, "r5rk/1bp3bp/p1p2ppN/8/3PpP2/1P2P3/P1P4P/R1B2RK1 w - - 0 22", listOf("h6f7"), "Mate in 1", "Rating 783 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(749, "3r1rk1/1p2Rpp1/p1pP3p/P2q4/6N1/1PP3Bb/4QP2/4R1K1 b - - 0 27", listOf("d5g2"), "Mate in 1", "Rating 789 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(750, "r4r1k/5p1p/pq2bp2/1pp1pN2/4P1Q1/P1PP4/1P4PP/R4RK1 w - - 2 20", listOf("g4g7"), "Mate in 1", "Rating 795 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk10(): List<Puzzle> {
        return listOf(
            Puzzle(751, "5r1k/1pr4p/pq1p3Q/4bp2/2B1p3/1P6/2P2PPP/3R1RK1 w - - 6 27", listOf("h6f8"), "Mate in 1", "Rating 801 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(752, "8/8/8/P4k1K/1R3P2/5r2/8/8 b - - 0 43", listOf("f3h3"), "Mate in 1", "Rating 807 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(753, "8/2B5/2p5/4p3/2k3b1/2P1K3/r4P2/2R5 b - - 5 38", listOf("a2e2"), "Mate in 1", "Rating 812 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(754, "2k4r/ppp2ppp/8/8/2n5/qP2P3/P1P1NPPP/1K1Q3R b - - 2 18", listOf("a3b2"), "Mate in 1", "Rating 818 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(755, "2k1r1B1/1pp2pP1/p4B2/2q5/3n4/8/bPPQ1PPP/2KR2NR b - - 0 18", listOf("d4b3"), "Mate in 1", "Rating 1301 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(756, "r3k2r/1b2bppp/p3p3/1p1qP3/5B2/1N6/PP3PPP/R2Q1RK1 b kq - 2 17", listOf("d5g2"), "Mate in 1", "Rating 823 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(757, "r1b1k1nr/pp3ppp/2p5/3q4/1Q1nN3/2N5/PP2PPPP/R1B1KB1R b KQkq - 0 9", listOf("d4c2"), "Mate in 1", "Rating 829 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(758, "8/p5bk/8/1ppq1p1p/3p1QP1/1P1Pr1P1/P1PN1R1P/5K2 b - - 0 31", listOf("d5h1"), "Mate in 1", "Rating 834 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(759, "6Q1/8/1p4pk/p5b1/P5P1/1P4q1/4K3/8 w - - 2 72", listOf("g8h8"), "Mate in 1", "Rating 839 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(760, "1r5k/4bp1p/1q2p2P/4Pb2/8/5BQ1/6P1/6RK w - - 1 49", listOf("g3g7"), "Mate in 1", "Rating 843 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(761, "r6r/1p3pk1/p3p3/3pP2q/4n1N1/2P1QPP1/PP6/R3R1K1 b - - 0 27", listOf("h5h1"), "Mate in 1", "Rating 848 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(762, "2rq1rk1/pp2nppp/8/3P1b2/8/1PQ2P2/P1P1BP1P/2KR2R1 w - - 1 19", listOf("c3g7"), "Mate in 1", "Rating 853 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(763, "R7/6pk/5pqp/8/2Qp4/1P1P4/2P1r2P/7K w - - 1 37", listOf("c4g8"), "Mate in 1", "Rating 858 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(764, "1k1r1b1r/n1pq1ppp/Qp2p3/8/8/4nB2/PPP2PPP/R4RK1 w - - 0 16", listOf("a6b7"), "Mate in 1", "Rating 862 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(765, "rn2r1k1/ppp2ppp/8/8/6nq/1P3P1P/PBPPB3/RN1Q1K1R b - - 2 13", listOf("h4f2"), "Mate in 1", "Rating 867 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(766, "r3k1nr/ppp3p1/2nb3p/3p1p2/3P2Pq/2P2P1P/PP4K1/R1BQ1BNR b kq - 0 11", listOf("h4g3"), "Mate in 1", "Rating 871 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(767, "r1bqk1nr/ppp3pp/2np4/2bQ2N1/2P5/4p1P1/PP2PPBP/RNB1K2R w KQkq - 0 9", listOf("d5f7"), "Mate in 1", "Rating 875 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(768, "1r2k3/R4ppp/8/P3PP2/2r3P1/8/K2R3P/8 b - - 0 41", listOf("c4a4"), "Mate in 1", "Rating 880 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(769, "4rk2/1p3n2/p1r2R2/3p1N2/5p2/1P6/PKPP4/7R w - - 1 32", listOf("h1h8"), "Mate in 1", "Rating 884 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(770, "r4rk1/1pp3pp/pb1p2q1/4pN2/PP2Pn2/5N2/1B3PPP/R2Q1RK1 b - - 3 20", listOf("g6g2"), "Mate in 1", "Rating 888 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(771, "2kr3r/ppp2ppp/8/2b1pP2/4Q3/2N2P2/PPBq3P/R4KNR b - - 2 16", listOf("d2f2"), "Mate in 1", "Rating 892 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(772, "4r1k1/pQ5p/5Pp1/5qBn/3P1P2/8/PPr2P2/3R2RK b - - 0 31", listOf("f5h3"), "Mate in 1", "Rating 896 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(773, "r1b1k1nr/ppp2ppp/2n5/2b3N1/2BqpP2/1PP5/P2P2PP/RNBQK2R b KQkq - 0 8", listOf("d4f2"), "Mate in 1", "Rating 901 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(774, "r2q1rk1/pp3ppb/7p/2pp1P1N/6Q1/1P1P4/1PP3PP/R1b2RK1 w - - 0 18", listOf("g4g7"), "Mate in 1", "Rating 905 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(775, "2r2rk1/1b1p1ppp/pb2p3/1p2P1q1/1n1P1B2/1B6/PP2NPPP/1QR2RK1 b - - 12 20", listOf("g5g2"), "Mate in 1", "Rating 909 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(776, "3rkbnr/pp3p2/2n1P3/4q1pQ/4Pp2/8/PPP3PP/R1B1KB1R w KQk - 0 12", listOf("h5f7"), "Mate in 1", "Rating 913 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(777, "6k1/p5b1/8/2q4p/2P1Q2P/5Np1/P5P1/5K2 b - - 2 33", listOf("c5f2"), "Mate in 1", "Rating 917 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(778, "rn1qkb1r/pp2pp1p/2p2np1/3b4/3PN3/3B1N2/PPP1QPPP/R1B1K2R w KQkq - 0 8", listOf("e4f6"), "Mate in 1", "Rating 921 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(779, "r4n1k/pb5p/1p4p1/2ppPB2/Q2P3q/P1P2P2/6PP/R1B1R1K1 b - - 0 22", listOf("h4e1"), "Mate in 1", "Rating 924 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(780, "r2q1r2/pp3p1k/3p1P1n/3pp1Q1/2B5/3P4/PPP5/R4RK1 w - - 1 23", listOf("g5g7"), "Mate in 1", "Rating 928 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(781, "3r4/p1pn2QR/1pp1krq1/8/4P1b1/2NP1pP1/PPP2P2/1K1R4 w - - 7 23", listOf("g7e7"), "Mate in 1", "Rating 932 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(782, "q3r1k1/p4pp1/3p3p/8/2PQ2n1/2P2PPb/PP5P/R1B1R1K1 b - - 0 20", listOf("e8e1"), "Mate in 1", "Rating 936 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(783, "7k/1pp2rpp/3N4/p7/2P3Q1/1P1n2PP/PB3PK1/4r3 b - - 0 26", listOf("f7f2"), "Mate in 1", "Rating 940 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(784, "1nkr3r/1pp2ppp/5B2/8/b3p3/2P5/P3NPPP/3RKB1R b K - 0 15", listOf("d8d1"), "Mate in 1", "Rating 944 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(785, "r7/6pp/7k/4R3/4R3/2P2P2/6rP/2K5 b - - 3 32", listOf("a8a1"), "Mate in 1", "Rating 948 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(786, "rn1qk2r/pp3pbp/2p1p1p1/4P3/2BP4/2N1nQ2/PPP3PP/R4RK1 w kq - 0 12", listOf("f3f7"), "Mate in 1", "Rating 952 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(787, "r2qkb1r/pb3p1p/3p1np1/2pPn3/1pP1P3/1P4P1/PB1NN2P/R2QKB1R b KQkq - 0 11", listOf("e5d3"), "Mate in 1", "Rating 956 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(788, "r3r1k1/p6p/2pp2p1/3b1P2/5Q2/2PB4/PP3K2/R1B2R1q b - - 4 22", listOf("h1g2"), "Mate in 1", "Rating 960 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(789, "r1bq1rk1/1ppnn1p1/pb2pp2/3pP1NQ/1P1P4/P1N5/2PB1PPP/R4RK1 w - - 0 15", listOf("h5h7"), "Mate in 1", "Rating 963 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(790, "2b3rk/p4Q2/3K1bB1/1p1pP3/3P4/4Pq2/P7/8 w - - 0 46", listOf("f7h7"), "Mate in 1", "Rating 967 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(791, "4k3/P7/4PK2/6P1/3B4/8/8/3q4 w - - 0 62", listOf("a7a8q"), "Mate in 1", "Rating 971 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(792, "6r1/p7/3p1k1p/2pP4/1pP2P2/3QN2R/7K/2q5 b - - 2 46", listOf("c1g1"), "Mate in 1", "Rating 974 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(793, "4r1nB/pQ5p/2Pk1p2/5p2/8/7n/P5PP/4b1NK b - - 0 29", listOf("h3f2"), "Mate in 1", "Rating 978 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(794, "7r/p5k1/2p3p1/2b3PN/5B2/6P1/PPr3bK/R1R5 b - - 11 29", listOf("h8h5"), "Mate in 1", "Rating 982 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(795, "r4bkr/p3p1pp/2p2n2/1p6/3q1Pn1/2N5/PRPBQ2P/5R1K w - - 2 19", listOf("e2e6"), "Mate in 1", "Rating 985 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(796, "r4rk1/pp2bppp/8/3p1q2/1P2bN2/P1Q5/1B3PPP/2RR2K1 w - - 5 25", listOf("c3g7"), "Mate in 1", "Rating 989 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(797, "7r/5pk1/5bp1/pp1q3p/2p2Q1P/2P3P1/PPB1RP2/5K2 b - - 0 30", listOf("d5h1"), "Mate in 1", "Rating 992 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(798, "r1q1r1k1/1p4pp/3b1p2/2p2N2/p1Pp2Q1/P2P2P1/1P3P1P/1n3RK1 w - - 2 25", listOf("g4g7"), "Mate in 1", "Rating 996 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(799, "r1b1k2r/ppp2pp1/1b1p3p/3N4/1P1nP2q/P2P2PP/2PB4/R2QKB1R b KQkq - 0 13", listOf("h4g3"), "Mate in 1", "Rating 1000 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(800, "r4r1k/p1q3pP/2p1p3/1p1nNpp1/3P4/2PQ2P1/P1P5/1R2K2R w K - 1 22", listOf("e5g6"), "Mate in 1", "Rating 1005 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(801, "r2qkbnr/pp3ppp/2n5/4p3/3pb3/1B1P1Q2/PPP2PPP/R1B1K1NR w KQkq - 0 9", listOf("f3f7"), "Mate in 1", "Rating 1011 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(802, "r2q1r2/ppp2pkp/3pbb2/4n2Q/8/2NB1N2/PP3PPP/R3K2R w KQ - 0 13", listOf("h5h7"), "Mate in 1", "Rating 1017 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(803, "1r4k1/p4p1p/5PpP/4R3/P2pP3/K7/1r3P2/3R4 b - - 0 32", listOf("b8b3"), "Mate in 1", "Rating 1022 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(804, "8/5Q2/3q1n1k/3p1P2/3P3K/7P/P7/8 w - - 1 48", listOf("f7g6"), "Mate in 1", "Rating 1028 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(805, "r5k1/pp3ppp/2p1b3/4r2q/3Q4/8/PPP2NBP/R4KR1 b - - 3 19", listOf("h5e2"), "Mate in 1", "Rating 1034 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(806, "8/pp1R4/1kp5/8/5P2/1P4P1/Pr3r1P/2KR4 b - - 3 31", listOf("f2c2"), "Mate in 1", "Rating 1040 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(807, "5KQ1/3k3q/5R2/8/8/8/8/8 b - - 0 68", listOf("h7e7"), "Mate in 1", "Rating 1046 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(808, "3rk1r1/qp3N2/p3P1p1/4PnQ1/5P2/Pn6/1P4PP/2B4K w - - 0 25", listOf("g5d8"), "Mate in 1", "Rating 1051 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(809, "1kr5/1pq3p1/3r1p2/2Qb4/4P2p/2R5/6P1/R6K w - - 0 38", listOf("c5a7"), "Mate in 1", "Rating 1057 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(810, "2b4k/pr5B/5rQ1/q2pp3/8/1P2P3/P4PPP/5K1R w - - 0 23", listOf("g6g8"), "Mate in 1", "Rating 1063 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(811, "rn3rk1/pp1b2pp/2pb4/3p3n/B6q/2N2P2/PPP1N1PP/R1B1QR1K b - - 10 14", listOf("h4h2"), "Mate in 1", "Rating 1068 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(812, "8/5pk1/1Q4p1/p1p5/4q2P/P3P2K/1P3P1P/8 b - - 0 34", listOf("e4f3"), "Mate in 1", "Rating 1074 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(813, "r5nr/ppp3pp/8/8/2K1PQ2/7k/PPnP3P/RNB4q w - - 8 20", listOf("f4g3"), "Mate in 1", "Rating 1080 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(814, "3Q4/p4ppk/2Q4p/Pp6/8/B3P3/4q1pP/6K1 b - - 0 37", listOf("e2f1"), "Mate in 1", "Rating 1086 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(815, "8/2R4p/1p2pkp1/5p2/b4KP1/5P2/rb5P/3R4 w - - 3 31", listOf("g4g5"), "Mate in 1", "Rating 1091 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(816, "k2n2r1/p2R2p1/1pN5/1P5p/4p3/4P3/1PP2pP1/2K5 w - - 0 30", listOf("d7a7"), "Mate in 1", "Rating 1097 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(817, "6k1/pp4pp/b3pp2/2rp3Q/8/qN2P3/P5PP/1K1R4 w - - 0 26", listOf("h5e8"), "Mate in 1", "Rating 1102 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(818, "1r3k2/ppp3pp/3q4/6N1/3PR3/PB5P/1P4P1/6K1 w - - 7 32", listOf("g5h7"), "Mate in 1", "Rating 1108 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(819, "1R2n1k1/6q1/5p2/3p1N2/b2P3Q/3B1P1P/3r2P1/6K1 b - - 3 45", listOf("g7g2"), "Mate in 1", "Rating 1113 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(820, "6Q1/p1p5/3p1q2/2p1p2p/7k/1P1P1P2/P1P1KP2/8 w - - 28 45", listOf("g8g3"), "Mate in 1", "Rating 1119 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(821, "k1r5/p2QR2p/2q3p1/1p6/3P4/6PP/5P1K/8 w - - 1 41", listOf("d7a7"), "Mate in 1", "Rating 1124 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(822, "1k1r3r/ppp2ppp/6bb/8/Q1BPq1P1/2P4P/PP1N1P2/2KRR3 b - - 4 17", listOf("e4b1"), "Mate in 1", "Rating 1827 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(823, "r3k2r/ppq2ppp/5n2/3p4/1B6/3bPN1P/PPn2KB1/2RQ2NR b kq - 0 17", listOf("f6e4"), "Mate in 1", "Rating 1129 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(824, "N1b4r/pp1pkppp/8/4n3/4q3/8/PPP1BP1P/R2QKR2 b Q - 1 15", listOf("e5f3"), "Mate in 1", "Rating 1134 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(825, "4Q3/p1q1b1pk/1p2p3/3r2P1/7P/2p3p1/PP6/R1B3K1 w - - 0 35", listOf("g5g6"), "Mate in 1", "Rating 1139 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk11(): List<Puzzle> {
        return listOf(
            Puzzle(826, "rbbq1rk1/pp1n1ppp/2p1p3/3p4/2PP4/4PNP1/PPQNBPP1/R3K2R w KQ - 3 12", listOf("c2h7"), "Mate in 1", "Rating 1145 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(827, "r1bq1rk1/pp2ppbp/3p1np1/2pPn3/2P1P3/1PN3PP/P3NP2/R1BQKB1R b KQ - 0 9", listOf("e5f3"), "Mate in 1", "Rating 1149 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(828, "3rkb1r/Q3p1p1/2p5/3qB2p/7P/2P2PN1/PP4P1/R3KR2 b Qk - 0 19", listOf("d5d2"), "Mate in 1", "Rating 1154 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(829, "8/5Q1p/6pq/4b3/4k3/2P5/6K1/8 w - - 21 77", listOf("f7f3"), "Mate in 1", "Rating 1160 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(830, "8/p5bQ/2p2k2/2Pp4/5Pp1/P3pqP1/5N2/4RK2 b - - 0 44", listOf("f3f2"), "Mate in 1", "Rating 1165 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(831, "r3k3/1b3pp1/p3p1p1/8/1pPN4/1Q1R4/PP3rRP/7K b q - 0 30", listOf("f2f1"), "Mate in 1", "Rating 1170 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(832, "r1b2k1r/p2p2Rp/1p2Qp1n/1B2b3/3pP3/3P4/PPP1KP1P/q7 w - - 6 18", listOf("e6e7"), "Mate in 1", "Rating 1175 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(833, "7k/1p3p1p/5p2/p2QbN1P/2P3P1/1P1q4/P4P2/4K3 b - - 6 36", listOf("e5c3"), "Mate in 1", "Rating 1180 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(834, "Qnkr3r/2pq1p2/1p2pn1p/2bP1bp1/8/4B2P/PP1NBPP1/R3K2R w KQ - 0 17", listOf("e2a6"), "Mate in 1", "Rating 1186 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(835, "2r1k3/7P/5R2/p3Bp2/P2P2p1/4Pb2/1R1K1P2/2r5 b - - 0 61", listOf("c1d1"), "Mate in 1", "Rating 1192 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(836, "8/5Rp1/3kp3/6P1/2P5/8/2r4r/4RK2 b - - 0 42", listOf("h2h1"), "Mate in 1", "Rating 1199 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(837, "8/3Qn1p1/5kp1/3p1q2/1KP5/5r2/PP2R3/8 w - - 0 33", listOf("d7e7"), "Mate in 1", "Rating 1208 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(838, "2Q5/p4Bpk/1p3b1p/2p4K/6P1/P2P4/2P1q2P/8 b - - 0 33", listOf("e2h2"), "Mate in 1", "Rating 1218 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(839, "7r/pp1b1kp1/n1pBp1p1/3pPp2/3q4/3B1PK1/PPP1Q1P1/R4R2 b - - 0 21", listOf("d4h4"), "Mate in 1", "Rating 1229 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(840, "r4k1r/pp2nB1p/1bp2p2/4N3/3Pq3/8/PP3PPP/R1B2RK1 w - - 0 18", listOf("c1h6"), "Mate in 1", "Rating 1239 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(841, "2kr1b1r/pp3p2/n1P1pn1p/1B4p1/4p1b1/P1N1K1BP/1PP2PP1/R5NR b - - 0 15", listOf("f8c5"), "Mate in 1", "Rating 1250 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(842, "2k4r/ppp1R3/6p1/8/1P1q3r/3P2Qp/P1P3PK/4R3 w - - 0 33", listOf("g3c7"), "Mate in 1", "Rating 1261 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(843, "r7/ppp5/6k1/3N2r1/8/7K/PP6/R4R2 b - - 0 27", listOf("a8h8"), "Mate in 1", "Rating 1272 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(844, "rnb2r1k/1pp3bp/p1np1qp1/3Q2N1/2P1P1P1/2N5/PP3P2/R1B1KB1R w KQ - 3 13", listOf("h1h7"), "Mate in 1", "Rating 1283 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(845, "3r4/7p/6p1/q2N2Pk/3Q4/7P/8/7K w - - 1 42", listOf("d4g4"), "Mate in 1", "Rating 1295 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(846, "r1bb2nr/pp1k2pp/n1pp4/3N4/5B2/6P1/PPP2P1P/2K1RBNR w - - 0 12", listOf("f1h3"), "Mate in 1", "Rating 1307 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(847, "8/6pk/7p/p2Q2r1/P2P4/5B2/7q/1R1R1K2 b - - 0 50", listOf("g5g1"), "Mate in 1", "Rating 1320 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(848, "2kr3r/ppp2p2/8/4n1p1/3q2P1/2NP2Bp/PPPQ3K/R4R1B b - - 0 17", listOf("e5g4"), "Mate in 1", "Rating 1333 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(849, "rn1qk2r/pQ3pbp/4pnp1/1bp1p1N1/8/3P4/PPP2PPP/RNB1K2R w KQkq - 0 10", listOf("b7f7"), "Mate in 1", "Rating 1346 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(850, "2rq1r2/6p1/1p1b1pk1/p2B4/Q7/2p3P1/PP3PP1/2K4R w - - 2 23", listOf("a4g4"), "Mate in 1", "Rating 1360 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(851, "2r2b1r/1bN1kpp1/pB3n1p/P3p3/1p2P1q1/8/1PP2PPP/R2QR1K1 w - - 0 19", listOf("b6c5"), "Mate in 1", "Rating 1373 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(852, "7k/5p1p/2pQ1p2/5P2/1pb1r3/1P2q2P/1KPN2P1/r2R3R b - - 2 27", listOf("e3c3"), "Mate in 1", "Rating 1388 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(853, "5rk1/nr4bp/1pp1p1p1/p3N1q1/3PQn2/PP3N2/1B3PPP/R4RK1 b - - 1 20", listOf("g5g2"), "Mate in 1", "Rating 1404 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(854, "r1b1qr2/ppppn1pk/2n1N3/4Pp2/3b1B2/2N3Q1/PPP3PP/2KR3R w - - 2 15", listOf("g3g7"), "Mate in 1", "Rating 1419 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(855, "3r3k/pp3Bp1/2p3Pq/3n4/4NR1Q/3r3P/PP4K1/8 w - - 6 30", listOf("h4d8"), "Mate in 1", "Rating 1436 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(856, "8/1p5k/p4Q1p/2pB1pp1/P1P1P3/7K/1P3q2/R7 b - - 0 38", listOf("g5g4"), "Mate in 1", "Rating 1452 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(857, "r3k2r/pp3ppp/2n5/3qpb2/1b2P3/1P3N2/PB2KPPP/R2Q1B1R b kq - 0 11", listOf("d5e4"), "Mate in 1", "Rating 1468 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(858, "7k/1b2N2p/3P4/p3b1B1/1p4Q1/1P4PP/P1p4K/5r2 b - - 0 44", listOf("f1h1"), "Mate in 1", "Rating 1485 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(859, "3r2k1/5pp1/3q3p/3N4/2nQ1P2/P7/1P3K2/6R1 w - - 0 39", listOf("d4g7"), "Mate in 1", "Rating 1498 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(860, "8/2b5/8/1b1B2pp/1B1kP2P/P4P1K/8/8 b - - 2 42", listOf("b5f1"), "Mate in 1", "Rating 1517 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(861, "4k3/ppR3p1/2n2b2/1KP1p3/1P5r/3B4/P7/4R3 b - - 2 34", listOf("h4b4"), "Mate in 1", "Rating 1541 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(862, "1r1qkb1r/R4ppp/3p1n2/1ppQ4/4PB2/8/1PP2PPP/1N2K2R w Kk - 2 14", listOf("d5f7"), "Mate in 1", "Rating 1570 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(863, "1r2n1k1/4Q3/1n2p1qr/2p1P3/2Pp1RP1/3P4/1P3P2/6K1 w - - 0 39", listOf("f4f8"), "Mate in 1", "Rating 1598 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(864, "2R5/5ppk/8/2P5/1P1Pb2P/6NP/4n2K/5r2 b - - 4 36", listOf("f1f2"), "Mate in 1", "Rating 1629 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(865, "8/p2r1p1p/3p1p2/2rkb3/3N2N1/1PP2K2/8/R7 w - - 8 32", listOf("g4e3"), "Mate in 1", "Rating 1704 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(866, "4n3/2nN4/2K4p/3p1pp1/3Bk3/4P2P/5PP1/8 w - - 8 45", listOf("d7c5"), "Mate in 1", "Rating 1759 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(867, "5rk1/pb3p1p/1p2N1pQ/2p5/2P1P1P1/2q5/P4RK1/8 w - - 7 31", listOf("h6f8"), "Mate in 1", "Rating 1840 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(868, "8/3R2pk/5p1p/7P/1n2N3/3p2P1/5PK1/1r6 w - - 3 52", listOf("e4f6", "h7h8", "d7d8"), "Pin", "Rating 898 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(869, "1B6/Pk6/8/r4p1p/4p3/1B2K1PP/8/8 b - - 5 51", listOf("a5a3", "e3d2", "a3b3"), "Pin", "Rating 951 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(870, "6k1/q4p2/4p1pp/Pp1bP3/2R2P2/P2Q2P1/1B5P/5K2 b - - 0 33", listOf("d5c4", "d3c4", "b5c4"), "Pin", "Rating 969 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(871, "2kr3r/pb6/1p1pq2p/4n3/2P2p1N/P1NB4/1P1Q1PPP/4R1K1 w - - 1 20", listOf("d3f5", "e6f5", "h4f5"), "Pin", "Rating 985 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(872, "4r1k1/6p1/p4p2/3Nn1R1/1PppP3/P7/2K5/8 w - - 0 35", listOf("d5f6", "g8f8", "f6e8"), "Pin", "Rating 999 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(873, "r7/pp2p1kp/8/3p4/3P4/1P4b1/PBK1P3/5R2 w - - 1 32", listOf("f1g1", "h7h5", "g1g3"), "Pin", "Rating 1010 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(874, "8/p3k3/5p2/2q3p1/3pb3/P5P1/3B1Q1K/8 w - - 0 43", listOf("d2b4", "c5b4", "a3b4"), "Pin", "Rating 1024 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(875, "r4rk1/pp3ppp/2n2n2/2Pb4/3P3q/2P3BP/P2Q2BK/RN2R3 b - - 3 18", listOf("f6g4", "h2g1", "h4g3"), "Pin", "Rating 1037 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(876, "8/1kp4r/1p3N2/p3p3/6p1/6P1/5KP1/2R5 b - - 0 41", listOf("h7f7", "f2e3", "f7f6"), "Pin", "Rating 1064 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(877, "r5k1/bp1q1ppp/p7/5r2/3pNnQ1/7P/PPP2PP1/3R1RK1 w - - 2 22", listOf("e4f6", "f5f6", "g4d7"), "Pin", "Rating 1076 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(878, "2rq1rk1/1p2bppp/p4n2/3P4/P1BQ1P2/1PN5/1P4PP/3R1RK1 b - - 0 17", listOf("e7c5", "d4c5", "c8c5"), "Pin", "Rating 1087 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(879, "r1b2rk1/p3b1pp/1qp1p3/3nBp2/2BP4/5N2/PP2QPPP/1K1RR3 b - - 4 16", listOf("d5c3", "b1a1", "c3e2"), "Pin", "Rating 1098 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(880, "r7/pp2bkp1/2n3q1/2p5/3pPp2/2PP1B1Q/PP3K2/7R w - - 0 27", listOf("f3h5", "g6h5", "h3h5"), "Pin", "Rating 1109 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(881, "rRn3k1/P4p2/3rp1p1/3p3p/P1p2P2/8/3P2PP/6K1 w - - 2 37", listOf("b8a8", "d6d8", "a8b8", "c8a7", "b8d8"), "Pin", "Rating 1120 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(882, "6k1/pb4r1/1pn2Q2/2p1p3/2P4P/3PP3/PP5P/5K2 b - - 0 37", listOf("g7f7", "f6f7", "g8f7"), "Pin", "Rating 1131 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(883, "4r1k1/bpp5/p2p3P/3P1P1b/2P1p1q1/P7/1P1Q4/4RR1K w - - 1 30", listOf("f1g1", "a7g1", "e1g1"), "Pin", "Rating 1140 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(884, "rnb2r1k/pp4pp/2pq4/8/4Q3/1BP1B3/PPK3PP/R5NR b - - 0 18", listOf("c8f5", "e4f5", "f8f5"), "Pin", "Rating 1157 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(885, "6k1/p5b1/1p6/4pr2/2n3Q1/BPP3P1/P2r1PK1/5R2 b - - 2 34", listOf("c4e3", "g2h3", "e3g4"), "Pin", "Rating 1165 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(886, "1Rr1rk2/2P2pp1/7p/pR6/8/5P1P/5P2/6K1 w - - 1 34", listOf("b8c8", "e8c8", "b5b8", "f8e8", "b8c8"), "Pin", "Rating 1180 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(887, "3rb1k1/4bppp/B3p1n1/qPp1p3/P2rP3/2N2P2/1Q4PP/2RRB1K1 b - - 2 23", listOf("d4d1", "c1d1", "d8d1"), "Pin", "Rating 1187 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(888, "2r3k1/1b4bp/4p1p1/p3P3/P1B3P1/7q/KP2Q2P/R3R3 b - - 4 29", listOf("c8c4", "e2c4", "b7d5", "c4d5", "e6d5"), "Pin", "Rating 1193 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(889, "r7/2k5/2p5/1p6/3P4/B7/KPq5/2R2Q2 b - - 1 42", listOf("a8a3", "a2a3", "c2a4"), "Pin", "Rating 1201 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(890, "2r5/ppq1kpQ1/4p2p/1n1p4/1P6/P1P3N1/6PP/4R1K1 w - - 0 28", listOf("g3f5", "e7e8", "g7g8", "e8d7", "g8f7"), "Pin", "Rating 1212 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(891, "5r2/5p1k/1p1p1b1p/1PpP3P/2Ppn3/5R2/4B1P1/2B3K1 w - - 0 35", listOf("e2d3", "h7g7", "d3e4"), "Pin", "Rating 1235 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(892, "r1b2rk1/ppQ4p/3p2p1/4p2n/8/1P3PR1/PBPq4/1K5R w - - 3 23", listOf("h1h5", "c8d7", "c7d7"), "Pin", "Rating 1245 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(893, "3rr1k1/p1p2ppp/1p4q1/3PnN2/8/8/PP1Q1PPP/2R1RBK1 b - - 0 22", listOf("e5f3", "g1h1", "f3d2", "f5e7", "e8e7"), "Pin", "Rating 1265 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(894, "3rr1k1/pp6/6p1/1P1q4/3p3n/8/PB1Q1PPP/2RB2K1 w - - 7 29", listOf("d1b3", "d5b3", "a2b3"), "Pin", "Rating 1274 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(895, "8/2r1k1pB/4p3/5p2/p2P3P/5N2/1r1B1PP1/2R2K2 b - - 1 27", listOf("c7c1", "d2c1", "b2b1", "f1e2", "b1c1"), "Pin", "Rating 1283 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(896, "6rk/pp4pp/6n1/4Qp2/2p4q/P7/1BP3PP/4R2K w - - 1 28", listOf("e5g7", "g8g7", "e1e8", "g6f8", "e8f8"), "Pin", "Rating 1352 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(897, "3kr3/p5pp/1q1b1p2/Q5B1/8/5P2/PP2rP1P/3R2K1 w - - 0 31", listOf("d1d6", "d8e7", "d6b6"), "Pin", "Rating 1309 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(898, "4k3/ppR4p/3pp3/6bq/7P/P7/1P2r2P/4RQ1K b - - 1 32", listOf("e2e1", "f1e1", "h5f3", "h1g1", "g5e3", "e1e3", "f3e3"), "Pin", "Rating 1317 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(899, "1r4rb/2k3q1/p1np1p1R/1pp1pP1Q/4P3/1N1P4/P1P3B1/1R4K1 w - - 14 34", listOf("h6h7", "g7h7", "h5h7"), "Pin", "Rating 1326 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(900, "3Q1Rqk/7p/pp6/2ppp3/4K3/P1P1B3/1P4r1/3r4 w - - 0 43", listOf("e4e5", "d1f1", "f8g8"), "Pin", "Rating 1350 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk12(): List<Puzzle> {
        return listOf(
            Puzzle(901, "4r3/pQ2B1bk/3pp1p1/2p1q2p/8/P2P3P/2P3P1/5R1K w - - 1 27", listOf("e7f6", "e5f6", "f1f6"), "Pin", "Rating 1358 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(902, "r1b4r/p1p2kpp/2nb1n2/1B1qp3/8/8/PPPPQPPP/RNB1K2R w KQ - 0 9", listOf("b5c4", "c6d4", "c4d5"), "Pin", "Rating 1366 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(903, "8/5pkp/5p2/4nP2/7r/7R/7K/4R3 b - - 5 35", listOf("e5f3", "h2g3", "h4h3", "g3h3", "f3e1"), "Pin", "Rating 1374 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(904, "8/kbp5/8/1P1P4/6P1/1P4rr/P5R1/3R1K2 b - - 1 42", listOf("h3h1", "f1f2", "g3g2", "f2g2", "h1d1"), "Pin", "Rating 1382 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(905, "r2qr1k1/ppp1bpp1/3p1n1p/4nP1b/4P2B/2P3QP/PP1NB1PN/R4RK1 w - - 3 16", listOf("h4f6", "e7f6", "e2h5"), "Pin", "Rating 1390 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(906, "2q3r1/pp2nk2/2p1bR2/1PPp3p/N2PP2P/P5Q1/4B3/R5K1 b - - 0 29", listOf("f7f6", "e4e5", "f6f7", "e2h5", "f7f8"), "Pin", "Rating 1398 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(907, "3r4/2R2pk1/p2np1p1/4R1Np/P1p2P1P/8/2r3P1/6K1 w - - 0 38", listOf("g5e6", "g7g8", "e6d8"), "Pin", "Rating 1405 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(908, "2r3k1/pp3p2/1q4p1/7p/2p5/4Q2P/Pr1B2P1/2RR2K1 b - - 3 30", listOf("b2d2", "e3b6", "d2d1", "c1d1", "a7b6"), "Pin", "Rating 1413 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(909, "2r1k3/1br2p2/p2pp2p/1p4bN/3PP1P1/1P1KPR2/1P1N4/5R2 w - - 3 30", listOf("h5g7", "e8f8", "g7e6"), "Pin", "Rating 1421 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(910, "r3r1k1/pp4pp/3nqp2/1P1pB3/P2Q1PP1/1P6/6B1/R5K1 w - - 0 28", listOf("g2d5", "d6f7", "d5e6"), "Pin", "Rating 1429 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(911, "1B6/p1R1r1kp/1p3p2/n3q1p1/8/5PQP/6PK/8 w - - 6 36", listOf("g3e5", "f6e5", "c7e7"), "Pin", "Rating 1437 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(912, "2rq1bk1/p4pBp/6r1/8/2p3Q1/PP4P1/5PbP/R2R2K1 w - - 1 23", listOf("d1d8", "g6g4", "d8c8"), "Pin", "Rating 1445 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(913, "6k1/p1p2bpp/2p5/4q3/4Q3/2P2P2/P5PP/K4B1R b - - 0 26", listOf("e5c3", "a1b1", "f7g6", "f1d3", "g6e4"), "Pin", "Rating 1453 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(914, "6rk/bppq1p2/p4P1p/P2P4/1P6/2N3P1/4QP2/R4RK1 b - - 0 24", listOf("g8g3", "g1h2", "d7h3"), "Pin", "Rating 1461 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(915, "r1r3k1/p4pp1/qp2pn1p/8/8/1P1R1Q2/P4PPP/1B1R2K1 w - - 1 24", listOf("f3a8", "c8a8", "d3d8", "f6e8", "d8a8"), "Pin", "Rating 1529 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(916, "2rb2k1/1q3pp1/p2p3p/1prNp3/4P3/P1P2P1P/1P1R1QP1/3R2K1 b - - 1 32", listOf("c5d5", "d2d5", "d8b6", "f2b6", "b7b6"), "Pin", "Rating 1469 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(917, "8/1pk5/2p5/p7/2QP4/bPN5/P4q2/1K6 w - - 7 37", listOf("c3b5", "c7b8", "b5a3"), "Pin", "Rating 1475 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(918, "rnb2rk1/ppp2ppp/8/3Q4/2B5/2q2NK1/P5PP/4R3 w - - 0 16", listOf("d5f7", "f8f7", "e1e8"), "Pin", "Rating 1484 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(919, "Rr4k1/2q1p2p/3p1pp1/2pP4/2P1P3/2Q2P1P/1r4PK/R7 w - - 0 33", listOf("c3b2", "b8a8", "a1a8"), "Pin", "Rating 1492 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(920, "2rr2k1/5ppp/p3p3/1p1b4/5q2/P1NQ4/1PB2PPP/4R1K1 w - - 0 25", listOf("d3h7", "g8f8", "h7h8", "f8e7", "c3d5", "d8d5", "h8c8"), "Pin", "Rating 1500 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(921, "2rk3r/pp1nq3/5bB1/3Q3p/3P2p1/2P2R2/P2B2PP/6K1 w - - 0 26", listOf("f3f6", "e7f6", "d2g5"), "Pin", "Rating 1565 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(922, "q2k3r/p2n1pp1/6p1/2b5/5pP1/2N4P/PPP1QP1R/R3K3 b Q - 3 16", listOf("h8e8", "e1c1", "e8e2"), "Pin", "Rating 1508 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(923, "3r2k1/pp5p/2p2p2/5Bq1/2R3P1/P3PPQP/1P6/3r1RK1 b - - 2 28", listOf("g5e3", "g3f2", "d1f1", "g1f1", "d8d1", "f1g2", "d1d2", "f2d2", "e3d2"), "Pin", "Rating 1517 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(924, "5k2/R5r1/4p3/5bB1/5P2/5K1p/8/8 w - - 12 47", listOf("g5h6", "h3h2", "h6g7"), "Pin", "Rating 1524 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(925, "8/1p4kp/p7/2p1p3/3pP1qN/1P1P1R1b/PBP4P/7K w - - 3 28", listOf("f3g3", "g4g3", "h2g3"), "Pin", "Rating 1532 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(926, "6RR/p4p2/1p2b2p/7k/4PK2/2r2P1r/8/8 w - - 8 35", listOf("g8g5", "h5h4", "h8h6"), "Pin", "Rating 1539 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(927, "5k2/ppq3pp/2p1p3/4r1Q1/4pB2/2P5/PP4PP/R5K1 b - - 1 22", listOf("c7b6", "f4e3", "e5g5", "e3b6", "a7b6"), "Pin", "Rating 1547 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(928, "5rk1/4Qppp/p7/1p6/8/1B3bPq/PP3P1P/4R1K1 w - - 3 27", listOf("e7f7", "f8f7", "e1e8"), "Pin", "Rating 1613 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(929, "6k1/5pp1/N1P1p2p/3p4/3Pn2n/P3P2P/1r4PK/R5R1 b - - 2 37", listOf("h4f3", "h2h1", "e4f2"), "Pin", "Rating 1562 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(930, "6k1/1b3rR1/p3pq2/1p6/2pP2QB/2P1p3/PP5P/4R1K1 b - - 0 32", listOf("f7g7", "h4f6", "g7g4", "g1f1", "g4f4"), "Pin", "Rating 1569 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(931, "q4k2/1p3r1p/2b1B1r1/P3Q1P1/5P2/P2P4/2R5/4KR2 b - - 0 39", listOf("g6e6", "e5e6", "f7e7", "e6e7", "f8e7"), "Pin", "Rating 1576 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(932, "3r3r/1k3pbp/pn3pp1/1pQ5/3q4/2N5/PP3PPP/R3R1K1 w - - 0 26", listOf("e1e7", "d8d7", "c5d4"), "Pin", "Rating 1583 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(933, "2rr2k1/4q3/4p1pP/pp1n4/1bpPQB2/6P1/1P3P2/R2R2K1 w - - 0 25", listOf("e4g6", "g8f8", "h6h7", "e7g7", "f4h6", "g7h6", "g6h6"), "Pin", "Rating 1590 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(934, "4r3/1b4k1/p1p3p1/3p3p/4B1n1/1PN3RP/P1P3P1/6K1 w - - 0 26", listOf("h3g4", "d5e4", "g4h5"), "Pin", "Rating 1597 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(935, "r5k1/6pp/4b3/1R2Pp2/pp2pP2/1P2P1P1/PB3K1P/8 b - - 1 27", listOf("a4b3", "a2b3", "a8a2", "f2f1", "a2b2"), "Pin", "Rating 1604 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(936, "8/8/1p6/p1p1p1bp/P5k1/1P1B2q1/6Q1/1K6 w - - 6 51", listOf("d3f5", "g4f5", "g2g3"), "Pin", "Rating 1611 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(937, "7r/pp3k1B/2p2P1K/4pN2/2P3r1/3PR1P1/PP6/R7 b - - 0 35", listOf("g4g6", "h6h5", "h8h7", "f5h6", "h7h6"), "Pin", "Rating 1617 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(938, "2r4k/4qb1P/1p3b1Q/p2np3/P7/2PBN3/2KB4/6R1 b - - 1 42", listOf("d5b4", "c2b1", "b4d3"), "Pin", "Rating 1624 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(939, "6rk/7p/4P3/Q4n2/7P/1P4P1/P2R1PK1/2q5 b - - 0 41", listOf("f5h4", "g2h2", "h4f3"), "Pin", "Rating 1632 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(940, "r4rk1/pp1q2b1/2n3bp/2Pp1pp1/3N4/P2B1PnP/1P3BPN/2RQR1K1 b - - 1 21", listOf("g7d4", "f2d4", "c6d4"), "Pin", "Rating 1639 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(941, "N1k1r3/pp3Q1p/2b5/8/8/6P1/PPPq2NP/6RK b - - 0 26", listOf("d2g2", "g1g2", "e8e1", "f7f1", "e1f1"), "Pin", "Rating 1699 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(942, "r1r4R/p3kp2/4p3/4N1pn/7B/8/2q2PP1/3R2K1 w - - 0 31", listOf("h4g5", "h5f6", "d1d7"), "Pin", "Rating 1646 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(943, "2k5/2r3p1/4P1p1/1PR2pP1/8/6P1/1r6/5BK1 w - - 1 52", listOf("e6e7", "c7c5", "e7e8q"), "Pin", "Rating 1591 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(944, "2r2r2/p1Rn1pbk/1p1P3p/1q2p2P/6P1/1P2BP2/P2Q1K2/7R w - - 4 23", listOf("d2c2", "e5e4", "c7c8", "f8c8", "c2c8"), "Pin", "Rating 1653 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(945, "5rk1/6p1/3R3p/1p1Pp3/1P1nB1q1/P3Q3/5PP1/6K1 b - - 0 27", listOf("d4e2", "g1f1", "e2g3"), "Pin", "Rating 1661 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(946, "r2q2k1/1bp1b1p1/1pn1p2p/p7/2QP3N/P1P5/1PBB2PP/R4rK1 w - - 0 19", listOf("a1f1", "c6d4", "c3d4"), "Pin", "Rating 1668 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(947, "2r2rk1/1b1nq1p1/3p1bQp/p1pP1P2/1pP1p3/7R/P2NB1PP/5R1K w - - 1 28", listOf("h3h6", "e7e8", "g6h7", "g8f7", "e2h5", "f7e7", "h5e8"), "Pin", "Rating 1467 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(948, "4r1k1/pp2rp1p/3p2p1/1P1Pn1q1/2P1RP2/3B3P/2Q3P1/4R1K1 b - - 0 27", listOf("e5f3", "g1f2", "f3e1", "f4g5", "e1c2", "e4e7", "e8e7"), "Pin", "Rating 1676 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(949, "r1bq2k1/1p4b1/p3p1Q1/3pn1N1/5rP1/2N5/PPP2P2/2K4R w - - 0 22", listOf("h1h8", "g8h8", "g6h7"), "Pin", "Rating 1683 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(950, "5rk1/p1R3p1/1pb2rBp/4p1qP/8/2P2P2/PP2Q1P1/5RK1 b - - 3 25", listOf("c6f3", "e2c4", "g8h8", "f1f3", "f6f3"), "Pin", "Rating 1691 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(951, "1rb2rk1/1p3ppp/p2p4/2pB2q1/8/5Q1P/PPP2PP1/R3R1K1 w - - 4 21", listOf("f3f7", "f8f7", "e1e8"), "Pin", "Rating 1700 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(952, "r4r2/1b3Qpk/p1p2bRp/4pP2/4p2P/3B4/PPP1NqP1/2KR4 w - - 2 22", listOf("g6h6", "h7h6", "f7g6"), "Pin", "Rating 1709 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(953, "3r1rk1/pp3pbp/8/2p2pB1/8/2P3Q1/Pq2N1PP/R6K w - - 0 22", listOf("g5f6", "b2a1", "e2g1", "a1g1", "h1g1", "d8d1", "g1f2"), "Pin", "Rating 1718 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(954, "r4rk1/p4p1p/1p2p1q1/4P3/2p2B2/b1P1P3/P2R1P1Q/2K4R w - - 2 22", listOf("c1d1", "a8d8", "h1g1"), "Pin", "Rating 1735 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(955, "rr4k1/3P1pbp/n2p2p1/q1n1p3/p1P1P1PP/P1Q1B3/1P1NBP2/1K1R3R b - - 0 19", listOf("a5c3", "d7d8q", "g7f8", "d8b8", "a8b8"), "Pin", "Rating 1753 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(956, "3b1k2/5Pp1/3p4/3B4/1Q6/1N3P2/PR5q/K7 b - - 0 35", listOf("h2g1", "b2b1", "d8f6", "b3d4", "f6d4", "b4b2", "d4b2"), "Pin", "Rating 1772 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(957, "4r1k1/5pp1/4q1b1/3rB2R/p1pPpQ1P/1pP3R1/1P3PPK/8 w - - 0 34", listOf("h5h8", "g8h8", "f4h6", "h8g8", "h6g7"), "Pin", "Rating 1782 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(958, "r1b3k1/1pp2rp1/p6p/8/2B4q/4P2P/PPP1Q1P1/R5K1 w - - 0 19", listOf("a1f1", "c8e6", "c4e6"), "Pin", "Rating 1801 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(959, "2r2r2/p5kp/1p1b1n2/8/4n3/2B1P3/PPQ2P2/R3K3 w Q - 0 22", listOf("c2e4", "c8c3", "e4g2"), "Pin", "Rating 1811 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(960, "5rk1/1p2b1pp/p3p3/4p3/3rPn2/4Q3/PPP1N1PP/R5K1 b - - 3 22", listOf("e7c5", "e2d4", "c5d4"), "Pin", "Rating 1871 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(961, "5rk1/1p2b1pp/p3p3/4p3/3rPn2/4Q3/PPP1N1PP/R5K1 b - - 3 22", listOf("e7c5", "e2d4", "c5d4"), "Pin", "Rating 1871 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(962, "r1b2rk1/pp3ppp/2p5/6q1/2B5/5Q2/P4PPP/4R1K1 w - - 0 22", listOf("f3f7", "f8f7", "e1e8"), "Pin", "Rating 1819 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(963, "6r1/2R3pk/1n1np2p/1P1pN2N/3P4/2b5/5PPP/6K1 w - - 2 36", listOf("h5f6", "h7h8", "e5g6"), "Pin", "Rating 1838 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(964, "1b1kb3/8/RP3p2/3ppq2/6p1/2P1B1P1/3KBP1r/1N3R2 w - - 1 35", listOf("a6a8", "f5c8", "e2a6"), "Pin", "Rating 1861 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(965, "8/8/3N2P1/1p6/5K2/pkP5/4b3/8 w - - 0 57", listOf("g6g7", "b3b2", "g7g8q", "a3a2", "g8g2", "a2a1q", "g2e2"), "Pin", "Rating 1830 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(966, "1k1r3r/2p5/1pBp3p/3Pbpp1/4p3/P3Q2P/1PP2P2/2KR4 b - - 2 35", listOf("e5f4", "e3f4", "g5f4", "d1d4", "b6b5"), "Pin", "Rating 1883 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(967, "1r4k1/1b1p1rpp/p4p1Q/1p2p3/P6P/1q3PR1/4NP1K/2R5 w - - 2 33", listOf("c1g1", "g8f8", "g3g7"), "Pin", "Rating 1894 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(968, "2rr2k1/p4qb1/npp1pnQ1/3p2p1/PPPP4/2N1P3/4BPP1/1R2K2R w K - 1 23", listOf("h1h8", "g8h8", "g6f7"), "Pin", "Rating 1904 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(969, "5r2/pp4k1/2p1q2p/3p1r1Q/3P1b1P/P4Rp1/1PP3B1/5R1K w - - 2 30", listOf("h5g4", "e6g6", "f3f4", "f5f4", "f1f4", "f8f4", "g4f4"), "Pin", "Rating 1912 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(970, "4qrk1/6p1/2n4p/4p1p1/4Q3/7P/P1BN2P1/K7 w - - 11 35", listOf("e4h7", "g8f7", "c2g6", "f7e7", "g6e8"), "Pin", "Rating 1724 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(971, "4k3/1b4r1/p4p2/1p1P1P2/4Pq1p/P3QBr1/1P3KP1/2R3R1 b - - 1 43", listOf("g3g2", "g1g2", "g7g2", "f2g2", "f4e3"), "Pin", "Rating 1940 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(972, "rn4k1/ppq1rppp/2p2p2/5b1Q/2BP4/2P5/PP1K2PP/4R2R w - - 0 16", listOf("h5f7", "e7f7", "e1e8"), "Pin", "Rating 1950 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(973, "r3n3/2pBkpqp/3p4/3Pp2p/3n4/P2P2P1/5Q1K/4RR2 w - - 5 33", listOf("d7e8", "e7e8", "f2d4"), "Pin", "Rating 1959 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(974, "4k1r1/4qp2/p3pR2/1b1p2Bp/1P1P3Q/8/1Pr3PP/4R1K1 b - - 4 31", listOf("c2g2", "g1g2", "e7f6"), "Pin", "Rating 1969 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(975, "r5k1/pp4b1/3p2Q1/q1pP4/7R/2PB2P1/PP3rP1/1K1R4 b - - 0 20", listOf("f2b2", "b1c1", "a5c3", "d3c2", "b2b1", "c1b1", "c3b2"), "Pin", "Rating 1988 - Find the best sequence of moves!", Color.BLACK)
        )
    }

    private fun getChunk13(): List<Puzzle> {
        return listOf(
            Puzzle(976, "6k1/1R1rr2p/3q2p1/p1pbpp2/P1Bb4/7P/4QPP1/1R3N1K w - - 6 35", listOf("b1b6", "d7b7", "b6d6"), "Pin", "Rating 2009 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(977, "r1b1kr2/pp3pQ1/3p3p/2p1p3/2P2N1q/1P5P/P2K2B1/5R2 w q - 0 21", listOf("g7f8", "e8f8", "f4g6", "f8e8", "g6h4"), "Pin", "Rating 2055 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(978, "3r2k1/4bpp1/pq5p/1bp1P3/1p4Q1/4B1N1/PPP3PP/R5K1 w - - 7 25", listOf("g3f5", "e7f8", "f5h6", "b6h6", "e3h6"), "Pin", "Rating 2021 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(979, "r3kr2/ppp3pp/2np1n2/8/4q3/4Q3/PPP2PPP/RNB2RK1 w q - 0 12", listOf("e3e4", "f6e4", "f1e1", "d6d5", "f2f3", "c6d4", "b1a3", "d4f3", "g2f3"), "Pin", "Rating 2033 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(980, "8/2B3pk/2Q4p/3p4/1N3Pq1/1P1P4/P1P3P1/3b2K1 b - - 2 30", listOf("d1f3", "g1f2", "g4g2", "f2e3", "d5d4"), "Pin", "Rating 2044 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(981, "r1b1kb1r/pp2pp1p/6p1/3N4/3nQ3/5PB1/P1n2KPP/1q3BNR w kq - 0 14", listOf("d5f6", "e8d8", "e4d5", "c8d7", "d5d7"), "Pin", "Rating 2056 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(982, "8/6RR/5pp1/6k1/4p1P1/r4r1P/5PK1/8 w - - 0 45", listOf("h7h5", "g5f4", "g7g6", "f3f2", "g2f2"), "Pin", "Rating 2079 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(983, "r1b4r/4ppk1/2n3pp/2p5/p4P1P/P2B4/qPPQ2PN/2KR3R w - - 1 18", listOf("d2c3", "e7e5", "d3c4", "a2a1", "c1d2", "h8d8", "d2e2"), "Pin", "Rating 2089 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(984, "4rbk1/pp6/2p4p/3q1P2/3pR3/5Q2/PPP4P/7K w - - 1 26", listOf("f3g2", "g8h8", "e4e8"), "Pin", "Rating 2100 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(985, "r3r1k1/pp3p2/3b1ppp/8/3N1P2/2P1P2q/PPQ5/1BR2RK1 b - - 0 21", listOf("e8e3", "c2h2", "h3g4", "h2g2", "e3g3"), "Pin", "Rating 2115 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(986, "8/8/1R3p2/p1r3k1/2P5/5K2/P7/8 w - - 1 37", listOf("b6b5", "c5e5", "b5e5", "f6e5", "f3e4"), "Pin", "Rating 2130 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(987, "5r2/p4k2/4p3/8/3bB3/5PK1/P4r2/1R5R w - - 4 37", listOf("b1b7", "f7f6", "h1h6", "f6e5", "b7b5", "e5d6", "b5d5"), "Pin", "Rating 2145 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(988, "r3k2r/2q2pp1/p1p5/3pb2p/5Bb1/6P1/PP1N1P1P/2RQ1RK1 w kq - 2 17", listOf("f4e5", "c7e5", "f1e1", "e8g8", "e1e5", "g4d1", "c1d1"), "Pin", "Rating 2160 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(989, "q4r2/3r1pkp/p3p1p1/1p2P2P/1npP1NQ1/6P1/P4P2/3R1RK1 w - - 3 27", listOf("h5g6", "h7g6", "f4h5", "g7h7", "h5f6"), "Pin", "Rating 2175 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(990, "8/6bk/p3Q1pp/2p5/2PqNr2/7R/6PK/8 w - - 0 39", listOf("e4g5", "h7h8", "e6g6"), "Pin", "Rating 2191 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(991, "r4rk1/ppp1q1pp/2n3p1/8/1P4P1/3P1N1P/P4K2/R1BQR3 b - - 1 17", listOf("e7h4", "f2g2", "f8f3", "d1f3", "h4e1"), "Pin", "Rating 2205 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(992, "3r2k1/1bnr1ppp/p1p5/Pp3R2/1P1qP3/3Q4/B4PPP/3R2K1 w - - 0 26", listOf("f5f7", "d7f7", "d3d4"), "Pin", "Rating 2221 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(993, "r3kbnr/pp2pppp/8/1P1q4/3n2b1/2N2N2/2PP1PPP/R1BQKB1R b KQkq - 2 8", listOf("d5e6", "c3e2", "g4f3"), "Pin", "Rating 2238 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(994, "5b1k/q1p5/pp3P1p/3p1Q2/8/P1P2K1P/1P6/8 w - - 0 40", listOf("f5c8", "h8g8", "c8e6"), "Pin", "Rating 2256 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(995, "4r1k1/ppp1n2R/5q2/6NQ/6P1/2P4K/P1P2P2/8 b - - 4 28", listOf("e7g6", "h7h8", "f6h8"), "Pin", "Rating 2277 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(996, "rn2k1nr/pp1q4/2pbbp1p/6p1/3P4/2N2N1P/PPP2PP1/R1BQR1K1 w kq - 2 13", listOf("d4d5", "c6d5", "d1d5", "d6e5", "f3e5", "d7d5", "c3d5"), "Pin", "Rating 2316 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(997, "5k2/5qp1/2r1p2p/pp3p2/1n3r2/1PQR4/6PP/1B2R2K w - - 0 41", listOf("d3d8", "f8e7", "c3d2", "b4d5", "d8d5"), "Pin", "Rating 2339 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(998, "5rk1/1pp3pp/1p2qn2/3pp1Q1/4N3/2PP3P/1r4P1/R4RK1 w - - 0 21", listOf("e4f6", "f8f6", "a1a8", "g8f7", "g5h5", "f7e7", "a8e8", "e7d7", "e8e6", "f6f1", "g1f1"), "Pin", "Rating 2230 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(999, "r1b2rk1/5qp1/p3p3/1pPpB3/6Q1/PP4P1/6KP/2R4R b - - 2 25", listOf("f7f2", "g2h3", "f8f6", "e5f6", "e6e5", "g4c8", "a8c8"), "Pin", "Rating 2367 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1000, "3r1rk1/1p3pp1/2q1p2p/p7/P2P2R1/2n2QNP/1P3PP1/6K1 w - - 0 26", listOf("f3f6", "g7g6", "g3h5", "c3e2", "g1h2"), "Pin", "Rating 2452 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1001, "r3kb1r/pp2ppp1/8/n2p2N1/2pP1P2/1PP1P1p1/P1BBK1Pq/R2Q1R2 b kq - 1 16", listOf("h2g2", "e2e1", "h8h1", "d1f3", "g2f1", "f3f1", "g3g2"), "Pin", "Rating 2541 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1002, "4k3/pQR3pp/3qp1b1/8/1P3P2/P6P/6PK/4q3 w - - 8 43", listOf("c7c8", "d6d8", "b7b5", "e8f7", "c8d8"), "Pin", "Rating 2602 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1003, "3rr1k1/pp4pp/8/5Pq1/7P/b1PB1Q2/P1R2P2/3K3R b - - 0 26", listOf("g5f6", "h1e1", "d8d3", "f3d3", "e8d8", "d3d8", "f6d8"), "Pin", "Rating 2624 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1004, "8/8/4p1Pp/4P1K1/4b3/8/3k4/8 w - - 0 55", listOf("g5h6", "d2d3", "g6g7", "d3d4", "g7g8q"), "Promotion", "Rating 637 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1005, "8/8/p7/4N3/kPK5/5p2/8/8 b - - 30 68", listOf("f3f2", "e5d3", "f2f1q"), "Promotion", "Rating 712 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1006, "8/p2R4/1p6/8/1P6/K7/2kp4/8 b - - 2 45", listOf("d2d1q", "d7d1", "c2d1"), "Promotion", "Rating 760 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1007, "6B1/8/4p1P1/p3P3/3P4/P1K1kp2/8/8 b - - 0 51", listOf("f3f2", "g6g7", "f2f1q"), "Promotion", "Rating 804 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1008, "2r3k1/5pPp/Q7/5b2/8/8/PP1p2PP/K2R4 b - - 1 32", listOf("c8c1", "d1c1", "d2c1q"), "Promotion", "Rating 833 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1009, "3r4/1P6/8/8/1R6/5pk1/8/5K2 w - - 7 53", listOf("b7b8q", "d8b8", "b4b8"), "Promotion", "Rating 859 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1010, "8/6pk/5p1p/P1P1rP2/8/7P/2PQp1PK/8 b - - 2 36", listOf("e2e1q", "d2e1", "e5e1"), "Promotion", "Rating 883 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1011, "3r2k1/3P1ppp/p7/1p1b1p2/8/P1rBR3/2P3PP/5R1K w - - 1 24", listOf("e3e8", "d8e8", "d7e8q"), "Promotion", "Rating 905 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1012, "8/8/7p/1K3pp1/p1P3k1/P7/8/8 w - - 0 49", listOf("c4c5", "g4g3", "c5c6", "f5f4", "c6c7", "f4f3", "c7c8q"), "Promotion", "Rating 922 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1013, "8/k2N1pp1/P3p3/KP1pP3/5P2/6n1/8/7q w - - 0 44", listOf("b5b6", "a7a8", "b6b7", "a8a7", "b7b8q"), "Promotion", "Rating 937 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1014, "8/8/3PN3/6K1/6bp/6k1/8/8 w - - 0 55", listOf("d6d7", "g4e6", "d7d8q"), "Promotion", "Rating 950 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1015, "8/p3K2k/8/5P2/1p6/7P/6P1/8 b - - 1 54", listOf("b4b3", "f5f6", "b3b2", "f6f7", "b2b1q"), "Promotion", "Rating 963 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1016, "k7/2K3p1/1P5p/4p2P/8/7P/8/5q2 w - - 0 63", listOf("b6b7", "a8a7", "b7b8q", "a7a6", "b8b6"), "Promotion", "Rating 975 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1017, "8/6k1/R5P1/p2r4/2Kp4/P7/1P6/8 b - - 6 45", listOf("d4d3", "c4d5", "d3d2", "d5c6", "d2d1q"), "Promotion", "Rating 984 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1018, "8/R4pk1/6p1/8/p7/1p3P2/4K2P/8 b - - 1 57", listOf("b3b2", "a7b7", "a4a3", "e2d3", "a3a2", "b7b2", "a2a1q"), "Promotion", "Rating 995 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1019, "2r2k2/2P2pp1/1p2b3/5n2/8/2q3Pp/P6P/R2Q1RK1 w - - 0 31", listOf("d1d8", "c8d8", "c7d8q"), "Promotion", "Rating 1006 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1020, "8/R4P2/2k3K1/2p5/2P5/8/8/q4r2 w - - 0 74", listOf("a7a1", "f1a1", "f7f8q"), "Promotion", "Rating 1034 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1021, "2q5/2P3k1/2K2b2/8/1P4p1/3Q4/8/8 w - - 0 66", listOf("d3d7", "c8d7", "c6d7", "f6e5", "c7c8q"), "Promotion", "Rating 1059 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1022, "8/8/5q2/1KP5/8/k7/6Qp/8 b - - 3 57", listOf("f6b2", "g2b2", "a3b2", "c5c6", "h2h1q"), "Promotion", "Rating 1070 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1023, "5r1k/6pp/2BB4/1P1N4/p7/P2r4/4p1PP/R5K1 b - - 0 37", listOf("d3d1", "a1d1", "e2d1q"), "Promotion", "Rating 1080 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1024, "5k2/8/4PK2/8/p1N5/1r6/8/8 w - - 0 54", listOf("e6e7", "f8e8", "c4d6", "e8d7", "e7e8q"), "Promotion", "Rating 1089 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1025, "8/8/k1p5/P1R2K2/7P/1p6/8/8 b - - 0 48", listOf("b3b2", "c5c6", "a6a5", "c6e6", "b2b1q"), "Promotion", "Rating 1097 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1026, "1r4rk/5R2/pp2p3/4P3/1P1PN3/P6n/1B3p1P/5R1K b - - 0 33", listOf("g8g1", "f1g1", "f2g1q"), "Promotion", "Rating 1116 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1027, "1kr5/3R3R/1p2p3/8/3n1P2/4p3/PP6/3K4 b - - 4 33", listOf("e3e2", "d1d2", "c8c2", "d2d3", "e2e1q"), "Promotion", "Rating 1126 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1028, "8/8/6p1/2K2p2/1P2k2P/8/8/8 b - - 1 45", listOf("f5f4", "b4b5", "f4f3", "b5b6", "f3f2", "b6b7", "f2f1q", "b7b8q", "f1c1"), "Promotion", "Rating 1135 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1029, "8/7k/8/P7/1P2nB2/8/3p2RK/3r4 b - - 0 42", listOf("d1h1", "h2h1", "d2d1q"), "Promotion", "Rating 1142 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1030, "6k1/5p2/6p1/7p/1P1Nqb2/1QP4P/3p1PP1/R5K1 b - - 0 39", listOf("e4e1", "a1e1", "d2e1q"), "Promotion", "Rating 1150 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1031, "k1r5/P7/2qBp3/4Pp2/1Q1P3p/8/5KP1/8 w - - 0 44", listOf("b4b8", "c8b8", "a7b8q"), "Promotion", "Rating 1156 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1032, "7K/8/3Pk1r1/4B3/5P2/8/8/8 w - - 42 101", listOf("f4f5", "e6f5", "d6d7", "f5e5", "d7d8q"), "Promotion", "Rating 1163 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1033, "8/1r6/8/pkp3P1/8/2R5/6PK/8 w - - 0 41", listOf("c3b3", "b5a6", "b3b7", "a6b7", "g5g6", "c5c4", "g6g7", "c4c3", "g7g8q"), "Promotion", "Rating 1169 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1034, "5k2/5P1p/1p4p1/1K1B4/3b2P1/r7/8/q3R3 w - - 0 53", listOf("e1e8", "f8g7", "f7f8q"), "Promotion", "Rating 1176 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1035, "1Q6/p1p3bk/4b2p/2P3p1/2pR4/8/P3p1PP/5RK1 b - - 0 24", listOf("g7d4", "g1h1", "e2f1q"), "Promotion", "Rating 1182 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1036, "8/1R6/8/2p4P/2k3P1/p7/6K1/7r b - - 5 56", listOf("a3a2", "g2h1", "a2a1q"), "Promotion", "Rating 1188 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1037, "8/4P3/8/p4R2/1k3K2/1p6/5P2/4r3 w - - 2 45", listOf("f5e5", "e1e5", "f4e5", "a5a4", "e7e8q"), "Promotion", "Rating 1193 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1038, "k6r/p2Prqb1/Qp3npp/3p1p2/3P1B1P/5N2/PPP3P1/2KR4 w - - 1 23", listOf("a6c8", "h8c8", "d7c8q"), "Promotion", "Rating 1199 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1039, "4r1k1/5pp1/p4Q1p/1p6/2p5/P1P3R1/1P1p1PPP/1B2qRK1 b - - 0 27", listOf("e1f1", "g1f1", "d2d1q"), "Promotion", "Rating 1209 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1040, "5r2/1P3R2/8/8/P6p/4p3/2P2k2/3K4 b - - 3 39", listOf("f8f7", "b7b8q", "e3e2", "d1c1", "e2e1q"), "Promotion", "Rating 1221 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1041, "8/5R1p/2n3pk/8/2B1PPP1/7P/p5K1/r7 b - - 0 39", listOf("a1g1", "g2g1", "a2a1q"), "Promotion", "Rating 1231 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1042, "5r1k/p5pp/4Q3/8/8/2N5/PPqp4/K2R4 b - - 1 36", listOf("c2c1", "d1c1", "d2c1q"), "Promotion", "Rating 1241 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1043, "8/8/8/5PKp/1p5P/8/2k5/8 w - - 1 53", listOf("f5f6", "b4b3", "f6f7", "b3b2", "f7f8q", "b2b1q", "f8f5"), "Promotion", "Rating 1251 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1044, "8/8/5K2/p6p/4P2P/8/1k6/8 w - - 1 65", listOf("e4e5", "a5a4", "e5e6", "a4a3", "e6e7", "a3a2", "e7e8q", "a2a1q", "e8e5", "b2a2", "e5a1"), "Promotion", "Rating 1261 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1045, "8/7p/1Q3Pk1/6b1/2q5/8/3p1PPP/5RK1 b - - 0 33", listOf("c4f1", "g1f1", "d2d1q"), "Promotion", "Rating 1270 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1046, "8/pp5p/4pKpP/7k/8/8/P7/8 w - - 2 39", listOf("f6g7", "e6e5", "g7h7", "e5e4", "h7g7", "e4e3", "h6h7", "e3e2", "h7h8q"), "Promotion", "Rating 1278 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1047, "8/5K2/pk4P1/1p6/1P6/P1P4p/8/8 b - - 1 66", listOf("h3h2", "g6g7", "h2h1q", "g7g8q", "h1d5"), "Promotion", "Rating 1287 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1048, "8/4k3/K3p2p/1Pr1P1p1/6P1/7P/8/5B2 w - - 6 44", listOf("b5b6", "c5c1", "b6b7", "c1f1", "b7b8q"), "Promotion", "Rating 1295 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1049, "8/8/4k3/6p1/4r1P1/PR2pK2/8/8 b - - 3 44", listOf("e3e2", "f3e4", "e2e1q"), "Promotion", "Rating 1304 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1050, "5Q2/8/b2N4/8/8/p7/Kpk5/8 b - - 0 55", listOf("b2b1q", "a2a3", "b1b3"), "Promotion", "Rating 1313 - Find the best sequence of moves!", Color.BLACK)
        )
    }

    private fun getChunk14(): List<Puzzle> {
        return listOf(
            Puzzle(1051, "R7/P7/8/1k4K1/8/r6p/8/8 w - - 0 53", listOf("a8b8", "b5c6", "a7a8q", "a3a8", "b8a8"), "Promotion", "Rating 1322 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1052, "8/5p1p/7P/p2p1kP1/1PpP1P2/2P2K2/8/8 b - - 0 35", listOf("a5a4", "b4b5", "a4a3", "b5b6", "a3a2", "b6b7", "a2a1q"), "Promotion", "Rating 1330 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1053, "8/3P4/4B1p1/4p1k1/4P1P1/3r3p/7K/8 w - - 2 55", listOf("e6d5", "g5g4", "d7d8q"), "Promotion", "Rating 1339 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1054, "1r3r2/4RP1R/p4PP1/2k5/8/2pp4/P7/2K5 b - - 2 39", listOf("d3d2", "c1c2", "b8b2", "c2c3", "d2d1q"), "Promotion", "Rating 1348 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1055, "4k3/5p1p/6p1/2b5/5P2/1p2N2P/pB2K1P1/8 b - - 1 39", listOf("c5a3", "b2a3", "a2a1q"), "Promotion", "Rating 1357 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1056, "5r2/3K4/4P3/3k4/6Pp/7P/8/8 w - - 1 64", listOf("e6e7", "f8f7", "d7d8", "d5e5", "e7e8q"), "Promotion", "Rating 1366 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1057, "5rk1/6p1/p1p4p/3p4/3Q4/2P2p1b/PP4RP/4R2K b - - 0 28", listOf("f3g2", "h1g1", "f8f1", "e1f1", "g2f1q"), "Promotion", "Rating 1374 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1058, "5r1k/2P3p1/pR6/8/PP6/3b4/1K5p/8 w - - 0 45", listOf("b6b8", "h2h1q", "b8f8", "h8h7", "c7c8q"), "Promotion", "Rating 1383 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1059, "1n6/5B1k/7P/1p4PK/2pP4/2P5/8/q7 w - - 0 57", listOf("g5g6", "h7h8", "g6g7", "h8h7", "g7g8q"), "Promotion", "Rating 1391 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1060, "8/PR6/5p2/r3kp1p/7P/5KP1/8/8 w - - 3 47", listOf("b7b5", "a5b5", "a7a8q"), "Promotion", "Rating 1400 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1061, "R7/8/3P1k2/p1p1p3/P3bb2/1P6/8/6K1 w - - 0 50", listOf("d6d7", "e4a8", "d7d8q"), "Promotion", "Rating 1408 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1062, "7r/Ppkr2p1/5p2/5Q2/8/2P4P/P3q3/RK6 w - - 1 36", listOf("f5c5", "c7d8", "a7a8q"), "Promotion", "Rating 1417 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1063, "r2b2k1/3P2pp/p2B1n2/1p1q4/3p4/3P4/PPP3PP/4QRK1 w - - 2 24", listOf("e1e8", "f6e8", "d7e8q"), "Promotion", "Rating 1426 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1064, "8/6p1/PR3p1p/8/8/k1r5/1p3PKP/8 w - - 0 42", listOf("a6a7", "c3c8", "b6b8", "c8b8", "a7b8q"), "Promotion", "Rating 1434 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1065, "8/2k5/8/5BP1/6P1/1Pp1r3/2Kp4/5R2 b - - 1 49", listOf("e3e1", "c2c3", "d2d1q", "f1e1", "d1e1"), "Promotion", "Rating 1442 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1066, "3R4/1PB5/4k3/8/2P1n2p/6pp/8/6K1 b - - 0 48", listOf("h3h2", "g1g2", "h4h3", "g2f3", "h2h1q"), "Promotion", "Rating 1452 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1067, "3r4/P7/6k1/2Rn2p1/6Kp/1P6/1P6/8 w - - 3 48", listOf("c5d5", "d8d5", "a7a8q"), "Promotion", "Rating 1461 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1068, "R7/P5kp/2p5/3p4/3P4/2P4r/2K3p1/8 w - - 0 36", listOf("a8g8", "g7g8", "a7a8q", "g8f7", "a8b7"), "Promotion", "Rating 1470 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1069, "8/5PK1/7R/2k3p1/6P1/1pp2r2/8/8 w - - 1 54", listOf("h6f6", "f3f6", "g7f6", "c5d4", "f7f8q"), "Promotion", "Rating 1477 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1070, "r3q3/3rP2k/p5pp/1pb5/4Q2P/8/PP3PP1/4RRK1 w - - 7 31", listOf("e4a8", "e8a8", "e7e8q", "a8e8", "e1e8"), "Promotion", "Rating 1486 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1071, "r7/5K1p/3P2p1/2p5/1k3P2/p5P1/6BP/8 w - - 0 42", listOf("g2a8", "a3a2", "d6d7", "a2a1q", "d7d8q"), "Promotion", "Rating 1495 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1072, "1r6/2r2p1P/3kp3/P1p5/6R1/3B1P2/5P1K/8 w - - 1 43", listOf("g4g8", "c7c8", "h7h8q"), "Promotion", "Rating 1504 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1073, "3k4/1Qp1r3/1n6/1P6/3P4/p1P2P2/6P1/6K1 b - - 4 39", listOf("a3a2", "b7a7", "e7e1", "g1f2", "a2a1q", "a7a1", "e1a1"), "Promotion", "Rating 1513 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1074, "8/6R1/8/1P3P1k/2K5/8/5rp1/8 b - - 1 50", listOf("f2f4", "c4d3", "f4g4", "g7g4", "h5g4", "f5f6", "g2g1q"), "Promotion", "Rating 1521 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1075, "8/P4R2/4p1pp/8/6P1/r3k2P/2p3K1/8 w - - 0 51", listOf("f7f3", "e3d4", "f3a3", "c2c1q", "a7a8q"), "Promotion", "Rating 1381 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1076, "8/8/8/1PkBr3/2P5/3K2p1/2N5/8 b - - 1 37", listOf("e5d5", "c4d5", "g3g2", "c2d4", "g2g1q"), "Promotion", "Rating 1531 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1077, "8/8/8/8/1p4p1/1Pn2kP1/p2K3P/4R3 b - - 15 61", listOf("c3b1", "d2d3", "a2a1q"), "Promotion", "Rating 1539 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1078, "3k4/p7/1pKP4/8/2P5/r2N4/8/8 w - - 1 51", listOf("d3e5", "a3h3", "e5f7", "d8e8", "d6d7", "e8f7", "d7d8q"), "Promotion", "Rating 1548 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1079, "8/8/7P/5kP1/5r2/6K1/8/8 w - - 0 75", listOf("h6h7", "f4g4", "g3h3", "g4g1", "h3h2", "g1g5", "h7h8q"), "Promotion", "Rating 1556 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1080, "8/6b1/4R3/k4P2/2r5/3K3B/1p1N1P2/8 b - - 1 46", listOf("c4d4", "d3e3", "d4d2", "e3d2", "b2b1q"), "Promotion", "Rating 1574 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1081, "8/p7/5K2/7p/k7/7P/6P1/8 w - - 0 42", listOf("g2g4", "h5g4", "h3g4", "a7a5", "g4g5", "a4a3", "g5g6", "a3b2", "g6g7", "a5a4", "g7g8q"), "Promotion", "Rating 1582 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1082, "8/8/1Kp5/2P4p/3B2k1/1P4p1/8/8 b - - 2 50", listOf("h5h4", "b6c6", "h4h3", "c6d6", "h3h2", "c5c6", "h2h1q"), "Promotion", "Rating 1600 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1083, "6rk/pp4qp/4P3/Q1b5/2P1P3/P4B2/4R2p/2R4K b - - 0 30", listOf("g7g1", "c1g1", "h2g1q"), "Promotion", "Rating 1608 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1084, "8/PR4K1/4kp2/r6p/8/6P1/8/8 w - - 0 57", listOf("b7b6", "e6d5", "b6b5", "a5b5", "a7a8q"), "Promotion", "Rating 1617 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1085, "1k1r3r/ppp2ppp/5n2/5Q2/2P5/BPN1p1P1/P2P1PBP/q2R2K1 b - - 0 15", listOf("a1d1", "c3d1", "e3e2", "d1e3", "e2e1q"), "Promotion", "Rating 1739 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1086, "8/8/2R3P1/7k/5P2/7p/2p4K/5r2 w - - 1 57", listOf("g6g7", "c2c1r", "c6c1", "f1c1", "g7g8q"), "Promotion", "Rating 1626 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1087, "6b1/8/2p3P1/4KP2/pk6/8/8/8 w - - 1 82", listOf("f5f6", "a4a3", "f6f7", "a3a2", "f7f8q"), "Promotion", "Rating 1635 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1088, "5Q2/3P3p/6p1/2p2pk1/2Pb1q2/7P/3p2P1/7K w - - 2 40", listOf("d7d8q", "g5h5", "g2g4", "f4g4", "h3g4"), "Promotion", "Rating 1644 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1089, "8/1k1K2p1/1P6/2P5/8/7p/8/8 b - - 1 48", listOf("h3h2", "c5c6", "b7b6", "c6c7", "h2h1q", "c7c8q", "h1h3"), "Promotion", "Rating 1654 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1090, "8/6p1/p2p1kp1/K2P3p/1r6/P7/6P1/8 w - - 0 38", listOf("a3b4", "g6g5", "a5a6", "h5h4", "b4b5", "g5g4", "b5b6", "h4h3", "g2h3", "g4h3", "b6b7", "h3h2", "b7b8q", "h2h1q", "b8d6"), "Promotion", "Rating 1664 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1091, "2r1r2k/p1P3pp/8/8/1PQp4/P3p3/3q2PP/2RR3K b - - 2 30", listOf("e3e2", "d1d2", "e2e1q", "c4f1", "e1d2"), "Promotion", "Rating 1673 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1092, "8/8/3P1k2/2K5/p7/8/8/8 w - - 1 61", listOf("c5c6", "a4a3", "d6d7", "a3a2", "d7d8q"), "Promotion", "Rating 1682 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1093, "r7/P1N5/8/7p/2K3p1/2P3Pk/4p2P/R7 b - - 1 44", listOf("a8a7", "a1a7", "e2e1q"), "Promotion", "Rating 1692 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1094, "8/p6p/1p1P2k1/8/2PB4/3p3P/P5K1/4r3 w - - 0 34", listOf("d6d7", "d3d2", "d7d8q", "d2d1q", "d8f6"), "Promotion", "Rating 1701 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1095, "r3k2r/pp2Ppp1/4b2p/2q1p3/5P2/7P/PPPQ2P1/2KRR3 w kq - 0 21", listOf("d2d8", "a8d8", "e7d8q"), "Promotion", "Rating 1712 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1096, "8/3Q2pk/1p3bpp/1PP5/p4P2/1b4P1/6KP/8 b - - 0 46", listOf("a4a3", "d7d3", "a3a2", "d3b3", "a2a1q"), "Promotion", "Rating 1734 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1097, "8/1kp3R1/7p/8/1p1P4/P1N1p3/P5PP/6K1 b - - 0 35", listOf("b4c3", "g7f7", "e3e2", "g1f2", "c3c2", "f2e2", "c2c1q"), "Promotion", "Rating 1743 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1098, "8/5k2/5P2/p2PP1K1/P5p1/8/8/8 b - - 2 44", listOf("g4g3", "e5e6", "f7f8", "d5d6", "g3g2", "g5f5", "g2g1q"), "Promotion", "Rating 1754 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1099, "7k/2R4p/1P4p1/8/P7/6P1/2p2PKP/r7 b - - 1 48", listOf("c2c1q", "c7c1", "a1c1", "a4a5", "c1b1"), "Promotion", "Rating 1778 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1100, "8/p5kp/1p1P2p1/2p5/P2n4/4n1P1/3R4/6K1 w - - 1 42", listOf("d2d4", "c5d4", "d6d7", "g7f6", "d7d8q"), "Promotion", "Rating 1789 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1101, "8/1pp5/1p4KP/4R3/6kr/P1P2p2/1P6/8 w - - 0 45", listOf("e5e4", "g4g3", "e4h4", "g3h4", "h6h7", "h4g3", "h7h8q"), "Promotion", "Rating 1800 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1102, "8/8/R3bk2/3B4/P4Kp1/1r6/5P1p/8 b - - 3 47", listOf("b3f3", "d5f3", "g4f3", "a6d6", "h2h1q"), "Promotion", "Rating 1811 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1103, "1k5r/ppp3pp/8/1P1Q4/P2q4/3pN3/7P/4R1K1 b - - 5 30", listOf("d3d2", "d5d4", "d2e1q"), "Promotion", "Rating 1821 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1104, "2Q1N3/8/8/6p1/6Bp/5p1P/5kPK/2r5 b - - 0 59", listOf("c1h1", "h2h1", "f3g2", "h1h2", "g2g1q"), "Promotion", "Rating 1844 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1105, "8/P7/1K2p1kp/6p1/2R5/8/1r6/8 w - - 5 57", listOf("b6a5", "b2a2", "c4a4", "a2f2", "a7a8q"), "Promotion", "Rating 1856 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1106, "8/5bp1/p1n5/1p1p1Qk1/3PPb2/P1N5/1PB2qp1/R1BK4 b - - 2 31", listOf("g5h4", "f5f4", "f2f4", "c1f4", "g2g1q"), "Promotion", "Rating 1869 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1107, "8/2Q5/5kp1/7p/8/p4P1P/6P1/q4NK1 b - - 4 53", listOf("a1d4", "g1h2", "a3a2", "f1g3", "a2a1q"), "Promotion", "Rating 1882 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1108, "2bq1rk1/1pp3pp/1n3p2/2B5/3R4/6P1/p3PPBP/1NQ3K1 b - - 1 17", listOf("a2a1q", "d4d8", "f8d8"), "Promotion", "Rating 1896 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1109, "8/1K6/2P5/8/1B2R3/5pk1/1r6/8 b - - 2 79", listOf("f3f2", "c6c7", "f2f1q"), "Promotion", "Rating 1907 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1110, "8/8/5k2/5R2/5Kp1/8/7p/8 b - - 0 56", listOf("f6g6", "f5g5", "g6h6", "g5g8", "h6h7", "g8g4", "h2h1q"), "Promotion", "Rating 1917 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1111, "8/P5k1/7p/5p1K/5P1P/8/p7/8 b - - 0 54", listOf("a2a1q", "a7a8q", "a1d1", "a8f3", "d1f3"), "Promotion", "Rating 1927 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1112, "6R1/7p/2k1P3/8/Pp2KN2/2b4P/2rp4/8 w - - 0 42", listOf("e6e7", "d2d1q", "e7e8q", "d1d7", "e8a8"), "Promotion", "Rating 1937 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1113, "1k6/4P1R1/2p5/2P5/3Pp3/4P3/8/4K2q w - - 1 60", listOf("e1d2", "h1h5", "g7g8", "b8a7", "e7e8q", "h5e8", "g8e8"), "Promotion", "Rating 1948 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1114, "1k6/8/4p3/2p1P1P1/p1Pp4/8/6Kp/8 w - - 0 48", listOf("g5g6", "b8b7", "g6g7", "a4a3", "g7g8q", "a3a2", "g8f7", "b7b6", "f7f1"), "Promotion", "Rating 1970 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1115, "8/3Q4/p3p3/kpp3P1/1P6/2P3b1/P3p1P1/2K5 b - - 0 38", listOf("c5b4", "c1c2", "e2e1q"), "Promotion", "Rating 1982 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1116, "8/b6k/4K2P/6P1/4p3/3pB3/8/8 w - - 2 52", listOf("e6f7", "a7e3", "g5g6", "h7h6", "g6g7", "h6g5", "g7g8q"), "Promotion", "Rating 2003 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1117, "8/6R1/3K4/4p3/4P3/8/4Bkp1/2r5 b - - 0 59", listOf("g2g1q", "g7g1", "c1g1", "e2b5", "g1g5"), "Promotion", "Rating 2016 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1118, "3rkr2/ppp2p1p/4B3/4Q3/4P3/2q2N2/P1p1KPPP/R6R b - - 3 18", listOf("c3d3", "e2e1", "d3d1", "a1d1", "c2d1q"), "Promotion", "Rating 2030 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1119, "8/8/2K1k3/4P1pp/1P6/7P/8/8 b - - 1 54", listOf("g5g4", "h3g4", "h5h4", "b4b5", "h4h3", "b5b6", "h3h2", "b6b7", "h2h1q"), "Promotion", "Rating 2042 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1120, "8/7p/2qk4/6PP/p3Q3/8/7K/8 w - - 1 43", listOf("e4c6", "d6c6", "g5g6", "h7g6", "h5h6", "a4a3", "h6h7", "a3a2", "h7h8q"), "Promotion", "Rating 2055 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1121, "5k2/8/2p1PP1p/1pP2K1p/1P5P/6b1/8/8 w - - 0 44", listOf("f5g6", "g3h4", "e6e7", "f8e8", "f6f7", "e8e7", "g6g7", "h4f6", "g7g8", "e7e6", "f7f8q"), "Promotion", "Rating 2066 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1122, "8/3kP3/8/4K1P1/p7/8/8/8 b - - 0 52", listOf("a4a3", "e5f6", "d7e8", "g5g6", "a3a2", "g6g7", "a2a1q"), "Promotion", "Rating 2089 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1123, "2K1k3/2P4R/8/8/8/8/7p/1r6 b - - 4 44", listOf("h2h1q", "h7h8", "e8e7", "h8h1", "b1h1", "c8b7", "h1b1", "b7c6", "b1c1"), "Promotion", "Rating 2101 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1124, "8/4Ppkp/6p1/2p1N2n/2Q5/2P4P/r4r2/6K1 w - - 8 31", listOf("c4a2", "f2a2", "e7e8q"), "Promotion", "Rating 2117 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1125, "8/8/5RP1/p2p3p/5pkP/Ppp5/1P1r4/4K3 w - - 0 46", listOf("g6g7", "g4f3", "g7g8q", "d2e2", "e1f1"), "Promotion", "Rating 2133 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk15(): List<Puzzle> {
        return listOf(
            Puzzle(1126, "8/8/2PR4/8/pP2p3/P1K1k3/3p2r1/8 b - - 3 55", listOf("g2g6", "d6g6", "d2d1q"), "Promotion", "Rating 2149 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1127, "8/7k/1p2NK1p/3pP3/p2Pp3/4P2R/6PP/5q2 w - - 4 34", listOf("e6f4", "a4a3", "e5e6", "a3a2", "e6e7", "a2a1q", "e7e8q"), "Promotion", "Rating 2164 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1128, "4k3/p1Q2p2/4p3/4P3/3P3p/P1P1KP2/1PqpR3/8 b - - 1 40", listOf("d2d1n", "e3f4", "c2f5"), "Promotion", "Rating 2180 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1129, "6k1/rn3pp1/P1P4p/8/8/6P1/2R1PPKP/3r4 w - - 0 36", listOf("a6b7", "d1b1", "c6c7", "b1b7", "c7c8q"), "Promotion", "Rating 2196 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1130, "1r4k1/8/4NPp1/p5P1/6K1/1p4B1/8/8 w - - 0 44", listOf("g3b8", "b3b2", "e6d8", "b2b1q", "f6f7", "g8h7", "f7f8q"), "Promotion", "Rating 2212 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1131, "3BB3/7P/2p2b1k/8/1P6/pK3p2/P7/8 b - - 0 49", listOf("f3f2", "d8f6", "h6h7", "e8c6", "f2f1q"), "Promotion", "Rating 2252 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1132, "r3kb1r/ppp1pppb/2n3P1/3q1P1Q/4p3/2P5/PP2B2P/R1B1K2R w KQkq - 1 14", listOf("h5h7", "h8h7", "g6h7", "d5f5", "h7h8q"), "Promotion", "Rating 2228 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1133, "2r5/3K4/8/p1P3p1/P6p/5k2/P1R2P2/8 b - - 1 41", listOf("c8h8", "c5c6", "h4h3", "c6c7", "h3h2", "c7c8q", "h8c8", "c2c8", "h2h1q"), "Promotion", "Rating 2264 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1134, "2k5/1p1n1p2/pnpQ1N2/P5P1/8/2P4q/1P3p2/2KR4 b - - 0 31", listOf("f2f1q", "a5b6", "h3e3", "c1b1", "f1f5"), "Promotion", "Rating 2284 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1135, "8/1p6/p7/8/PP3pk1/2P5/5K2/8 b - - 3 47", listOf("b7b5", "a4b5", "a6b5", "c3c4", "b5c4", "b4b5", "c4c3", "b5b6", "c3c2", "b6b7", "c2c1q"), "Promotion", "Rating 2305 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1136, "8/8/3Kp3/p3P3/5Pp1/5kP1/8/8 b - - 2 48", listOf("a5a4", "f4f5", "e6f5", "e5e6", "a4a3", "e6e7", "a3a2", "e7e8q", "a2a1q"), "Promotion", "Rating 2326 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1137, "2Rr2k1/3P4/p4P1p/1p1b2p1/6P1/5NKP/8/5q2 w - - 2 43", listOf("c8d8", "g8f7", "f3e5", "f7e6", "d8e8", "e6d6", "d7d8q"), "Promotion", "Rating 2351 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1138, "8/3k4/4p3/2PpKn1P/3P1P2/8/p1N5/8 b - - 0 56", listOf("f5e3", "c5c6", "d7e7", "c2e3", "a2a1q"), "Promotion", "Rating 2384 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1139, "8/6bk/6pp/3Q4/8/p1qR4/5PP1/4R1K1 b - - 0 29", listOf("c3e1", "g1h2", "e1e5", "d5e5", "g7e5", "f2f4", "a3a2", "f4e5", "a2a1q"), "Promotion", "Rating 2425 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1140, "3R4/6p1/2K4p/8/4p3/3p1k2/5P1P/8 b - - 3 52", listOf("f3f2", "c6c5", "e4e3", "d8d3", "e3e2", "d3d7", "e2e1q"), "Promotion", "Rating 2474 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1141, "8/8/1R6/5P2/8/5K2/1pr5/2k5 b - - 0 60", listOf("c2c4", "f5f6", "b2b1q", "b6b1", "c1b1"), "Promotion", "Rating 2539 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1142, "6k1/1R3p2/6PP/5P2/p5K1/1r6/1p6/8 w - - 3 53", listOf("g6f7", "g8f8", "h6h7", "b3b7", "h7h8q", "f8f7", "h8h7"), "Promotion", "Rating 2623 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1143, "8/8/5p1p/8/8/3K1kPP/5P2/8 b - - 4 47", listOf("h6h5", "h3h4", "f3f2", "g3g4", "h5g4", "h4h5", "g4g3", "h5h6", "g3g2", "h6h7", "g2g1q"), "Promotion", "Rating 2934 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1144, "r1b3k1/pp3ppp/2p2b2/1q6/4rB2/1BP2P2/PP1Q2PP/2KR4 w - - 0 18", listOf("d2d8", "f6d8", "d1d8", "e4e8", "d8e8"), "Sacrifice", "Rating 774 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1145, "4k1r1/1b3p1p/pnq1p3/8/5P2/PP3N2/2PQBP1P/1R3R1K b - - 0 23", listOf("c6f3", "e2f3", "b7f3"), "Sacrifice", "Rating 844 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1146, "2r3k1/1bq2pp1/pp2Rn1p/8/2Pr4/1P6/P1B1QPPP/4R1K1 w - - 0 23", listOf("e6e8", "f6e8", "e2e8", "c8e8", "e1e8"), "Sacrifice", "Rating 895 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1147, "r6r/1p6/5k1p/5b1P/2B4P/1P6/PP3R2/K3R3 b - - 7 32", listOf("a8a2", "a1a2", "h8a8", "c4a6", "a8a6"), "Sacrifice", "Rating 958 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1148, "6rk/6p1/1R5p/1p1n4/5QP1/5P2/1q4PK/8 w - - 4 41", listOf("b6h6", "g7h6", "f4h6"), "Sacrifice", "Rating 1035 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1149, "8/pp2R2p/2p3pk/8/2P3qP/1PQ2pP1/P4P1K/3r4 b - - 1 27", listOf("d1h1", "h2h1", "g4h3", "h1g1", "h3g2"), "Sacrifice", "Rating 1085 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1150, "8/8/5pRp/1rp1p3/1k2P1PP/5PK1/8/8 b - - 1 38", listOf("c5c4", "g6f6", "c4c3", "f6h6", "c3c2"), "Sacrifice", "Rating 1111 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1151, "5r1k/ppp3pp/3p3r/4p3/4PnRq/2P2P1P/PP1Q1P1K/5BR1 b - - 9 27", listOf("h4h3", "f1h3", "h6h3"), "Sacrifice", "Rating 1125 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1152, "2r3k1/2q3pp/1n3p2/p1P5/1pR5/5PPP/P1QB2K1/8 w - - 4 38", listOf("c5b6", "c7c4", "c2c4", "c8c4", "b6b7"), "Sacrifice", "Rating 1167 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1153, "r5kr/1qBQ1pp1/p2Rp1p1/1p6/1n6/1P3P2/P1P1KP2/4R3 w - - 9 28", listOf("d7d8", "a8d8", "d6d8", "g8h7", "e1h1"), "Sacrifice", "Rating 1177 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1154, "r3r1k1/p3q1p1/2p5/2p1b1NR/2P2pb1/1P3N2/P4PK1/5Q2 w - - 2 32", listOf("h5h8", "g8h8", "f1h1", "g4h3", "h1h3", "h8g8", "h3h7", "g8f8", "h7h8"), "Sacrifice", "Rating 1194 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1155, "r4rk1/1p1bppbp/pq4p1/3PP3/2B5/2Q2N2/1PP2nPP/R1B2R1K w - - 3 18", listOf("f1f2", "b6f2", "c1e3", "f2e3", "c3e3"), "Sacrifice", "Rating 1207 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1156, "5r1k/4Npp1/QR5p/3p4/5K2/1P2P2P/3n2q1/8 w - - 2 37", listOf("b6h6", "g7h6", "a6h6"), "Sacrifice", "Rating 1222 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1157, "rn3r1k/ppp3pp/7R/8/2Bq4/8/P1PB2PP/3Q3K w - - 4 20", listOf("h6h7", "h8h7", "d1h5"), "Sacrifice", "Rating 1264 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1158, "r5r1/p2bk2p/Qp1p3p/3Pp1qB/4P3/2N5/PP4PP/5RK1 w - - 7 23", listOf("f1f7", "e7d8", "f7d7", "d8d7", "a6b7", "d7d8", "b7a8"), "Sacrifice", "Rating 1277 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1159, "6rk/p4prb/5p1p/3p4/3N3q/1P1pPP2/PQ1R1RPP/6K1 b - - 6 34", listOf("g7g2", "f2g2", "h4e1"), "Sacrifice", "Rating 1289 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1160, "5r2/7k/5Bpp/p2r1p2/1p6/5R1Q/PPq2P2/5K2 w - - 0 42", listOf("h3h6", "h7h6", "f3h3"), "Sacrifice", "Rating 1301 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1161, "6k1/1R4p1/1p2p2p/1P1pP2r/2pP1p2/2P2Pq1/2Q1K1P1/5R2 b - - 0 37", listOf("g3g2", "f1f2", "g2f2", "e2f2", "h5h2", "f2e1", "h2c2"), "Sacrifice", "Rating 1312 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1162, "5N1k/p6p/b1p3rq/2NnP3/P2p1p2/1P1P4/2PBQ1PP/R5RK b - - 0 23", listOf("h6h2", "h1h2", "g6h6", "e2h5", "h6h5"), "Sacrifice", "Rating 1322 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1163, "1q4rk/1b3p1p/p4Pp1/1p2r1P1/4pR1Q/P2B3P/1PP4K/5R2 w - - 0 34", listOf("h4h7", "h8h7", "f4h4"), "Sacrifice", "Rating 1332 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1164, "5r1k/6pp/p2Q4/1p3q2/8/2P5/PP1RrPPP/5RK1 b - - 2 26", listOf("f5f2", "f1f2", "e2e1", "f2f1", "e1f1"), "Sacrifice", "Rating 1342 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1165, "r7/2pq1kb1/1p5r/pNpPp3/2P1PpR1/P6P/1P3P1K/R5Q1 b - - 2 32", listOf("h6h3", "h2h3", "a8h8", "h3g2", "d7g4"), "Sacrifice", "Rating 1361 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1166, "r6r/1p6/1q3k2/p3nNp1/P4pb1/1BQP4/1PP3PP/R3R2K b - - 4 26", listOf("h8h2", "h1h2", "a8h8", "f5h4", "h8h4"), "Sacrifice", "Rating 1371 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1167, "3r1r1k/ppp3pp/1b6/1q2P3/2B3b1/2Q5/PBPN4/R1K2R2 b - - 4 21", listOf("f8f1", "c4f1", "b5f1", "d2f1", "d8d1"), "Sacrifice", "Rating 1390 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1168, "5rk1/1pp2ppp/5n2/p1R5/2P5/PPNq1BPb/5P2/R2QB1K1 b - - 0 18", listOf("d3f1", "g1h2", "f6g4", "f3g4", "f1g2"), "Sacrifice", "Rating 1416 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1169, "r1b2rk1/ppp2ppp/8/2bBpq2/4N3/3PBnP1/PPP1NPKP/R2Q1R2 b - - 6 12", listOf("f5h3", "g2f3", "c8g4"), "Sacrifice", "Rating 1425 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1170, "r7/ppR1ppk1/5N2/3p3r/B2b4/6P1/PP4PP/5R1K b - - 0 31", listOf("h5h2", "h1h2", "a8h8", "f6h7", "h8h7"), "Sacrifice", "Rating 1450 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1171, "r5rk/5p1p/p3pPpQ/1pp1P3/2P1q3/5R2/P2B2PP/n5K1 w - - 6 24", listOf("h6h7", "h8h7", "f3h3", "e4h4", "h3h4"), "Sacrifice", "Rating 1474 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1172, "6rk/ppp2pbn/8/5qN1/7Q/5P2/PPP2P1P/1K1R4 w - - 13 27", listOf("h4h7", "f5h7", "g5f7"), "Sacrifice", "Rating 1482 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1173, "1k1rr3/ppp2p2/3b2p1/5b1p/QP1P3P/2q2NP1/PR4B1/R1B3K1 b - - 3 19", listOf("e8e1", "f3e1", "c3e1"), "Sacrifice", "Rating 1490 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1174, "r2qr1k1/p3bp1p/2p3pB/np1pNb2/3P4/2PQR3/P1B2PPP/R5K1 w - - 4 18", listOf("d3f5", "g6f5", "e3g3"), "Sacrifice", "Rating 1506 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1175, "r5k1/pp5p/6p1/3p2q1/3Q3R/2P4b/PP1N1PrP/R6K b - - 3 21", listOf("g2h2", "h1h2", "g5g2"), "Sacrifice", "Rating 1514 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1176, "6rk/pQ4pp/5p2/8/3rP3/1P4RP/4q1P1/2R3K1 w - - 1 33", listOf("b7g7", "g8g7", "c1c8", "d4d8", "c8d8", "g7g8", "g3g8"), "Sacrifice", "Rating 1537 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1177, "r4rk1/2p3bR/p4q2/5p1Q/2pp4/4p1P1/PPP3P1/2K4R w - - 0 27", listOf("h7h8", "g7h8", "h5h7"), "Sacrifice", "Rating 1552 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1178, "5rrk/1p1q2bp/pn6/3p1R2/3P1B2/2N4Q/PP5P/6RK w - - 1 29", listOf("h3h7", "h8h7", "f5h5", "g7h6", "h5h6"), "Sacrifice", "Rating 1558 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1179, "3R4/6pk/p1p5/7p/5q2/4rBR1/PPP2K2/3N4 b - - 3 33", listOf("e3f3", "g3f3", "f4h4"), "Sacrifice", "Rating 1572 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1180, "r1b2r1k/5pR1/2q2n1p/ppp4P/4p1Q1/1P1PP3/2P2P2/1K4R1 w - - 2 28", listOf("g7h7", "f6h7", "g4g7"), "Sacrifice", "Rating 1579 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1181, "2kr3r/ppp2pp1/2P3p1/1P1p4/3P3b/P1N1BQP1/5K2/R3R2q b - - 0 23", listOf("h4g3", "f3g3", "h8h2", "g3h2", "h1h2"), "Sacrifice", "Rating 1640 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1182, "1r3r2/4qp2/4p1pk/1pPpQ1Rp/pP2nP2/P3P2P/5P1K/6R1 w - - 4 27", listOf("g5h5", "g6h5", "e5g7"), "Sacrifice", "Rating 1654 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1183, "r3r2k/pp6/2b2p1p/5p1q/8/PB1P2Q1/1PP2PPP/R3R1K1 b - - 1 26", listOf("e8g8", "b3g8", "a8g8", "e1e3", "g8g3"), "Sacrifice", "Rating 1661 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1184, "6k1/1b1p2r1/ppq1pQR1/4PR2/1PP5/PB5r/6P1/6K1 b - - 0 31", listOf("c6g2", "g6g2", "g7g2", "g1f1", "h3h1"), "Sacrifice", "Rating 1668 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1185, "2q3k1/4r1p1/r3PnQp/2p2B2/1pP5/1P3N2/4R1PP/6K1 w - - 3 36", listOf("g6f7", "e7f7", "e6f7"), "Sacrifice", "Rating 1683 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1186, "2r2rk1/1b4p1/3p4/1p1qppN1/2p5/2P4R/1P3PPP/R2Q2K1 w - - 0 26", listOf("h3h8", "g8h8", "d1h5", "h8g8", "h5h7"), "Sacrifice", "Rating 1691 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1187, "r3k1nr/1p2ppbp/p2p2p1/3N4/1q1nPPb1/3BQN2/PPPB2PP/2KR3R b kq - 7 13", listOf("d4e2", "d3e2", "b4b2"), "Sacrifice", "Rating 1707 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1188, "2r3q1/pQp1n2k/3pP2b/4p2R/4Pp2/1PP2Br1/P2B1KP1/R7 b - - 7 29", listOf("g3f3", "g2f3", "g8g3", "f2e2", "g3g2"), "Sacrifice", "Rating 1715 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1189, "4r1k1/1p4bR/2pq2p1/p2pN3/3Pn3/2P3P1/PP3r2/2Q3KR w - - 2 29", listOf("h7g7", "g8g7", "c1h6"), "Sacrifice", "Rating 1772 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1190, "6B1/1R5p/6pk/2p1b3/2P4q/3P4/5rQP/7K b - - 2 30", listOf("f2f1", "g2f1", "h4h2"), "Sacrifice", "Rating 1789 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1191, "8/3Q3R/p4kp1/5q2/1Pnp4/8/Pr3P2/KN4R1 b - - 0 36", listOf("b2a2", "a1a2", "f5c2", "a2a1", "c2b2"), "Sacrifice", "Rating 1798 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1192, "1k4rr/ppp2p2/5Q2/8/6q1/3P2P1/PP2P1KP/2R2R2 b - - 0 23", listOf("h8h2", "g2h2", "g4g3", "h2h1", "g3g2"), "Sacrifice", "Rating 1806 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1193, "2b2r1k/pp6/2p1pn1r/4QP2/3P2R1/2Pq4/PP4RP/7K w - - 0 32", listOf("e5f6", "h6f6", "g4h4", "f6h6", "h4h6"), "Sacrifice", "Rating 1851 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1194, "8/2qn2k1/2p2rp1/1p3pp1/p2Pp3/1BP3P1/PP3P2/5QKR w - - 0 33", listOf("f1h3", "a4b3", "h3h8", "g7f7", "h1h7", "f7e6", "h8e8"), "Sacrifice", "Rating 1861 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1195, "r2r2k1/pp3p1p/2p3p1/6P1/4q3/P1Q4R/1P1p1P2/3K3R w - - 0 29", listOf("c3h8", "g8h8", "h3h7", "h8g8", "h7h8", "g8g7", "h1h7"), "Sacrifice", "Rating 1892 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1196, "7k/p4p1p/5pr1/3bqQ2/8/1P4P1/P6P/1B3RK1 b - - 3 29", listOf("g6g3", "h2g3", "e5g3"), "Sacrifice", "Rating 1901 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1197, "2r1r1k1/1q1n1pbp/p3p1pB/1p6/3P4/2P2R1Q/P4PP1/1B1R2K1 w - - 2 28", listOf("h6g7", "g8g7", "f3f7", "g7f7", "h3h7"), "Sacrifice", "Rating 1909 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1198, "rnbq1b1r/ppp1k3/3p1p1p/4p1BQ/3PP3/8/PPP2PPP/R3K1NR w KQ - 2 10", listOf("g5f6", "e7f6", "h5h4", "f6g7", "h4d8"), "Sacrifice", "Rating 1653 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1199, "2kr2r1/pp3p2/1b2pp1p/1Q6/2B5/2q2N1P/2P2PPK/R4R2 b - - 2 19", listOf("g8g2", "h2g2", "d8g8", "f3g5", "g8g5"), "Sacrifice", "Rating 1950 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1200, "rn1q1bnr/pp5p/5k2/4NppB/3N4/8/PP3PPP/R3R1K1 w - - 0 18", listOf("e5g4", "f5g4", "e1e6", "f6g7", "d4f5"), "Sacrifice", "Rating 1959 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk16(): List<Puzzle> {
        return listOf(
            Puzzle(1201, "r3r1k1/2p2p1p/P1n1bQp1/2Bp4/P7/2N3Pq/5P1P/R2B1RK1 b - - 7 27", listOf("h3f1", "g1f1", "e6h3", "f1g1", "e8e1"), "Sacrifice", "Rating 1985 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1202, "3q2k1/pp1nb1np/2p1r2Q/3p1p2/3P1BpN/2PB4/PP5P/5RK1 w - - 2 29", listOf("h4f5", "e6h6", "f5h6"), "Sacrifice", "Rating 1994 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1203, "1r5k/1R4pp/1P1p4/1Q1Npr2/2B2b2/2P4q/5P1P/1R3NK1 b - - 0 29", listOf("f4h2", "f1h2", "f5g5", "h2g4", "g5g4"), "Sacrifice", "Rating 2003 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1204, "r4k2/2q2p2/4pQ2/4N3/2PP3P/1p3P2/1P3P2/1K6 b - - 3 32", listOf("a8a1", "b1a1", "c7a5", "a1b1", "a5e1"), "Sacrifice", "Rating 2013 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1205, "5b1k/p1q3pp/5P1r/2pBr1N1/1pp5/4Q1P1/PP5P/3R3K w - - 0 31", listOf("e3e5", "c7e5", "g5f7"), "Sacrifice", "Rating 2024 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1206, "3r1rk1/pp2npp1/2p4p/3qP3/3PNQ2/P4RP1/1P1R3P/6K1 w - - 8 28", listOf("e4f6", "g7f6", "e5f6", "d5f3", "f4f3"), "Sacrifice", "Rating 2065 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1207, "r2qkb1r/pbppp1p1/1p3p1p/4n1N1/8/1BP5/PPP1QPPP/R1B2RK1 w kq - 0 10", listOf("e2h5", "g7g6", "h5g6", "e5g6", "b3f7"), "Sacrifice", "Rating 2075 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1208, "r3nbkn/NpNb2r1/1P1p4/P2Pp3/4Pp1p/5Ppq/4B1PP/2RQ1RBK b - - 1 30", listOf("h3g2", "h1g2", "h4h3", "g2h1", "g3g2"), "Sacrifice", "Rating 2094 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1209, "4rrk1/pp4pp/2p5/q1np2P1/5P2/3B2QP/P1P1R3/1R1K4 w - - 5 22", listOf("d3h7", "g8h7", "g5g6", "h7g8", "g3h4"), "Sacrifice", "Rating 2105 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1210, "r4r2/p1p3bk/1p1p2Rp/3P1q1Q/5p2/2N5/PPP2P1R/2K5 w - - 0 24", listOf("h5h6", "g7h6", "h2h6"), "Sacrifice", "Rating 2118 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1211, "8/8/2pQ4/p1Pp1k1p/3q4/Kp4P1/6RP/4r3 w - - 10 50", listOf("g2f2", "d4f2", "d6f8", "f5e5", "f8f2", "e1a1", "a3b3"), "Sacrifice", "Rating 2131 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1212, "r2q1knr/1b5p/p2pR1p1/1p1PbnN1/5QP1/1BN5/PP3P1P/R5K1 w - - 1 19", listOf("e6e5", "d6e5", "g5e6"), "Sacrifice", "Rating 2144 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1213, "r4rk1/1pp2ppp/pbb5/3pq1P1/1P3P1P/P1nBP3/2PB4/R2QK2R w KQ - 0 20", listOf("d3h7", "g8h7", "d1h5", "h7g8", "f4e5"), "Sacrifice", "Rating 2156 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1214, "4Q2R/ppp3p1/4bk1p/8/7P/Pn2q3/K1P3P1/8 w - - 4 30", listOf("h8f8", "f6e5", "e8e6", "e5e6", "f8e8", "e6f7", "e8e3"), "Sacrifice", "Rating 1983 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1215, "rnb2rk1/ppp2ppp/8/2q1P3/8/1QN1p3/PP2B1PP/R4RK1 w - - 0 15", listOf("f1f7", "f8f7", "a1f1"), "Sacrifice", "Rating 2181 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1216, "2r3k1/p1p3pp/2p3r1/8/1B1p1q2/8/PPP1Q1PP/4R1K1 w - - 2 24", listOf("e2e8", "c8e8", "e1e8", "g8f7", "e8f8", "f7e6", "f8f4"), "Sacrifice", "Rating 1999 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1217, "1q1r1r2/p3kp2/1p1bpR2/1B1p2pp/3P4/8/PPQ5/1K3R2 w - - 0 30", listOf("f6e6", "f7e6", "c2h7", "f8f7", "h7f7"), "Sacrifice", "Rating 2206 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1218, "3r3r/pb2qpk1/1p2pn2/2b3B1/6N1/2PB4/PPQ2PPP/3R1RK1 b - - 2 20", listOf("f6g4", "g5e7", "h8h2", "e7f6", "g7f6", "d3e4", "d8h8"), "Sacrifice", "Rating 2220 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1219, "r2q1rk1/1bp2p1p/5p2/p1bPpPp1/2P3Q1/1B3N2/P5PP/R4R1K w - - 0 21", listOf("f3g5", "f6g5", "f5f6", "g8h8", "b3c2"), "Sacrifice", "Rating 2236 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1220, "r1b2r2/p1kp2pp/1pp3nq/3P4/2B1P3/Q1N3P1/PPP3KP/R6R b - - 0 16", listOf("g6h4", "g3h4", "h6g6", "g2h3", "f8f3"), "Sacrifice", "Rating 2269 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1221, "r1bQ1nk1/1p3ppp/8/p1pR4/1qP5/1P2P1PB/P3P2P/R5K1 b - - 2 22", listOf("c8h3", "d8a8", "b4c3"), "Sacrifice", "Rating 2304 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1222, "6k1/4r2p/5Qp1/1R6/2p5/P1PqPK2/1P3PPP/8 b - - 4 35", listOf("e7e3", "f2e3", "d3f1", "f3g3", "f1f6"), "Sacrifice", "Rating 2262 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1223, "3R4/kbQ5/1p6/p5q1/5n2/P4P2/5K1P/3R4 b - - 0 39", listOf("g5g2", "f2e3", "g2e2", "e3f4", "e2h2", "f4g4", "h2c7"), "Sacrifice", "Rating 2437 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1224, "6k1/6pp/3qp2r/8/p2b4/2PP3P/R5P1/3Q1B1K b - - 0 35", listOf("d6d5", "d1f3", "h6f6", "f3d5", "f6f1", "h1h2", "d4g1", "h2g3", "e6d5"), "Sacrifice", "Rating 2817 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1225, "1r3rk1/1pq2pn1/p1b1p3/2N2p2/P1B2P1Q/3PR2P/1Pn3P1/4R1K1 w - - 0 24", listOf("e3g3", "c7d8", "h4h6", "d8d4", "g1h1", "c2e1", "c5e6", "c6g2", "g3g2", "e1g2", "e6d4"), "Sacrifice", "Rating 2957 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1226, "2r2k2/2P2pp1/7p/2K5/6P1/7P/2R5/8 b - - 2 50", listOf("c8c7", "c5d5", "c7c2"), "Skewer", "Rating 521 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1227, "8/8/8/8/8/r4kpK/6R1/8 w - - 2 62", listOf("g2g3", "f3e4", "g3a3"), "Skewer", "Rating 689 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1228, "8/6p1/1pbkp2p/7r/3P4/P1P1R1P1/1P3P2/2R2K2 b - - 7 36", listOf("h5h1", "f1e2", "h1c1"), "Skewer", "Rating 728 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1229, "8/8/4k2r/R3p1p1/6P1/5P1p/8/7K w - - 2 74", listOf("a5a6", "e6d5", "a6h6"), "Skewer", "Rating 759 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1230, "8/5k2/5P2/1p1p4/1P1P2p1/2P3Pp/2K2R1P/r7 b - - 8 64", listOf("a1a2", "c2d3", "a2f2"), "Skewer", "Rating 791 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1231, "5k2/8/n1pr1pQP/p2p1B2/3P1P2/P1N3K1/8/5r2 b - - 0 39", listOf("f1g1", "g3f3", "g1g6"), "Skewer", "Rating 805 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1232, "4rr2/ppp3kp/2n1b1q1/3p1pp1/3Pp3/4P1P1/PPPNBPP1/R2Q2KR w - - 0 19", listOf("e2h5", "g6f6", "h5e8"), "Skewer", "Rating 821 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1233, "5R2/p6p/1p2k1p1/8/1B5n/P6P/4r1P1/6K1 w - - 5 45", listOf("f8e8", "e6d5", "e8e2"), "Skewer", "Rating 834 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1234, "r3r3/1p1n1k2/4ppp1/3p2p1/3P2P1/1PB3P1/2P1RPK1/7R w - - 7 29", listOf("h1h7", "f7g8", "h7d7"), "Skewer", "Rating 848 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1235, "6R1/8/8/8/2k5/8/4K3/2r5 w - - 46 79", listOf("g8c8", "c4d5", "c8c1"), "Skewer", "Rating 861 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1236, "5b2/2R2p1k/P3p3/4P1p1/1pKP4/r7/2P5/2N5 b - - 1 36", listOf("a3c3", "c4b5", "c3c7"), "Skewer", "Rating 872 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1237, "8/pp2r3/8/2K3pp/4k3/P1P5/1P3R2/8 w - - 0 50", listOf("f2e2", "e4f5", "e2e7"), "Skewer", "Rating 884 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1238, "8/8/1p6/p6R/r2k4/5K2/5P2/8 w - - 0 64", listOf("h5h4", "d4e5", "h4a4"), "Skewer", "Rating 894 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1239, "7R/1pr2k2/4p3/2P5/2K2P2/8/4n3/8 w - - 9 58", listOf("h8h7", "f7f6", "h7c7"), "Skewer", "Rating 905 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1240, "8/1q3k2/p6R/2P5/KP3p2/8/7P/8 w - - 0 52", listOf("h6h7", "f7e6", "h7b7"), "Skewer", "Rating 914 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1241, "8/R6P/4k2r/3p4/3b2K1/1P6/P5P1/8 w - - 0 39", listOf("a7a6", "e6d7", "a6h6"), "Skewer", "Rating 924 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1242, "5B2/3R4/5pk1/4p3/4n2K/8/5P1P/6r1 w - - 0 44", listOf("d7g7", "g6f5", "g7g1"), "Skewer", "Rating 933 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1243, "8/1p6/p1p1k3/8/3PP3/PR2K3/7r/8 b - - 7 47", listOf("h2h3", "e3f4", "h3b3"), "Skewer", "Rating 941 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1244, "r2k1b1r/1pp1pRpp/3p4/1p1Pq2n/2P1P3/P1N2Q1P/1P4P1/R5K1 w - - 0 18", listOf("f7f8", "h8f8", "f3f8", "d8d7", "f8a8"), "Skewer", "Rating 949 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1245, "8/8/6P1/6Q1/8/5K2/2r2P2/1q1k4 w - - 2 57", listOf("g5g1", "d1d2", "g1b1"), "Skewer", "Rating 957 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1246, "1R6/8/8/1Pk2p2/5P2/3KP2r/8/2b5 w - - 5 64", listOf("b8c8", "c5b5", "c8c1"), "Skewer", "Rating 964 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1247, "8/2N2pk1/8/6P1/7p/2R2K2/5P2/7r b - - 0 47", listOf("h1h3", "f3g4", "h3c3"), "Skewer", "Rating 971 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1248, "1r6/p2k1pbp/4p1p1/3pP3/Q2R1P2/q6P/P5P1/3K3R b - - 5 23", listOf("a3a4", "d4a4", "b8b1", "d1e2", "b1h1"), "Skewer", "Rating 986 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1249, "8/4R3/6p1/5p1p/3k1K1P/6P1/8/2r5 w - - 3 41", listOf("e7d7", "d4c5", "d7c7", "c5d6", "c7c1"), "Skewer", "Rating 991 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1250, "7R/4P3/6bp/2k1K3/1p6/1P5P/P2b1P2/8 b - - 4 49", listOf("d2c3", "e5e6", "c3h8"), "Skewer", "Rating 998 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1251, "8/6bp/2p5/7p/2K3k1/R7/5P2/8 w - - 0 40", listOf("a3g3", "g4f5", "g3g7"), "Skewer", "Rating 1007 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1252, "4rk2/p5p1/5pB1/3p4/2pP4/6PR/PPPq3P/4r1RK w - - 3 29", listOf("h3h8", "f8e7", "h8e8"), "Skewer", "Rating 1015 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1253, "8/1p1b4/6k1/p5P1/2p1KP2/2P5/2N5/8 b - - 3 45", listOf("d7f5", "e4d4", "f5c2"), "Skewer", "Rating 1025 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1254, "3rk2r/p2n1ppp/3bp3/4q3/Q3R3/2N1B3/PP3PPP/R5K1 b k - 0 17", listOf("e5h2", "g1f1", "h2h1", "f1e2", "h1a1"), "Skewer", "Rating 1035 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1255, "2R5/8/4pk2/PPK3p1/4P2p/8/8/1r6 b - - 0 40", listOf("b1c1", "c5d6", "c1c8"), "Skewer", "Rating 1044 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1256, "3r4/p4kb1/1p4p1/3P3p/3pP3/P2B1P2/1P1K1P2/2R5 b - - 1 32", listOf("g7h6", "d2d1", "h6c1"), "Skewer", "Rating 1053 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1257, "8/7p/8/8/2P2rk1/1R2K3/1P5P/8 b - - 1 43", listOf("f4f3", "e3d2", "f3b3"), "Skewer", "Rating 1061 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1258, "rnbr2k1/pppn2p1/3qppN1/3p3Q/3P1P2/2P5/PP1N1PPP/R3K2R w KQ - 4 13", listOf("h5h8", "g8f7", "h8d8"), "Skewer", "Rating 1068 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1259, "7r/ppp2p2/3p4/4k3/4Pp2/5Kb1/P1PB2P1/R7 w - - 1 28", listOf("d2c3", "e5e6", "c3h8"), "Skewer", "Rating 1076 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1260, "r7/2p1R2K/2kp4/2p5/8/2b1B3/5P2/6R1 b - - 4 41", listOf("a8h8", "h7g6", "h8g8", "g6f5", "g8g1"), "Skewer", "Rating 1083 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1261, "7r/p3kp2/P1r5/8/1R3K2/3B1P2/4P2P/8 b - - 1 37", listOf("h8h4", "f4g3", "h4b4"), "Skewer", "Rating 1090 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1262, "8/7R/p1p1rk2/1p6/6P1/1P2p3/P4PK1/8 w - - 0 49", listOf("h7h6", "f6g5", "h6e6"), "Skewer", "Rating 1096 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1263, "q7/p6p/1p3pp1/3kp3/PQ6/5PP1/7P/6K1 w - - 0 35", listOf("b4e4", "d5c5", "e4a8"), "Skewer", "Rating 1103 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1264, "8/5ppk/1PQ3p1/8/2P3P1/P4K1P/8/4q3 b - - 5 34", listOf("e1h1", "f3e2", "h1c6"), "Skewer", "Rating 1110 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1265, "8/8/6p1/pr2k2p/2n1P2P/6R1/3BK1P1/8 w - - 2 44", listOf("g3g5", "e5e4", "g5b5"), "Skewer", "Rating 1116 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1266, "r4k2/pp3p2/2pprqp1/5n2/8/8/PPP2PPQ/2KR3R w - - 2 22", listOf("h2h8", "f6h8", "h1h8", "f8g7", "h8a8"), "Skewer", "Rating 1123 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1267, "8/8/6K1/7R/r4k2/8/8/8 w - - 0 46", listOf("h5h4", "f4e3", "h4a4"), "Skewer", "Rating 1129 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1268, "8/k3r3/p6r/Q2K4/3P4/2PB4/8/8 b - - 3 50", listOf("h6h5", "d5d6", "h5a5"), "Skewer", "Rating 1135 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1269, "8/8/6K1/p4Q2/1p1k4/1P6/P7/3q4 w - - 2 50", listOf("f5d7", "d4c5", "d7d1"), "Skewer", "Rating 1142 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1270, "3R4/6k1/5p1p/2p2r2/8/7P/P5r1/3RK3 w - - 2 36", listOf("d1d7", "g7g6", "d8g8", "g6h5", "g8g2"), "Skewer", "Rating 1147 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1271, "r1bq1rk1/pp2bppp/3p1n2/2p1p3/4P3/P2PBQ2/BPP1NPPP/R4RK1 b - - 1 11", listOf("c8g4", "f3g3", "g4e2"), "Skewer", "Rating 1153 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1272, "8/7R/b4k2/2Pp3p/8/p7/7K/8 w - - 1 51", listOf("h7h6", "f6e5", "h6a6"), "Skewer", "Rating 1158 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1273, "8/1k6/1P5r/8/8/1R3K2/5P1P/8 b - - 0 56", listOf("h6h3", "f3e4", "h3b3"), "Skewer", "Rating 1164 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1274, "8/Bp5p/p1r1p1p1/2P1k3/1P2P3/3K2b1/P5B1/8 w - - 0 44", listOf("a7b8", "e5f6", "b8g3"), "Skewer", "Rating 1168 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1275, "3kr3/Q2r4/1p1qp2p/3p1p2/1Pp5/2P5/P3R1PP/4R2K w - - 2 28", listOf("a7a8", "d8c7", "a8e8"), "Skewer", "Rating 1173 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk17(): List<Puzzle> {
        return listOf(
            Puzzle(1276, "4r3/1p1q4/n1kbbQ2/3p1p2/2pP1P2/2P5/1R4PP/1R4K1 w - - 4 34", listOf("b2b6", "c6c7", "b6b7", "c7c8", "b7d7"), "Skewer", "Rating 1179 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1277, "3Q4/pp3pk1/2p5/P7/1PPKR3/5q2/5P1P/8 b - - 0 38", listOf("f3d1", "d4e3", "d1d8"), "Skewer", "Rating 1184 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1278, "2r4r/R2Q1ppp/8/1pk5/5P2/5K2/1Pq4P/8 w - - 2 37", listOf("a7c7", "c8c7", "d7c7", "c5d5", "c7c2"), "Skewer", "Rating 1190 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1279, "r6r/3Q4/p5p1/1p1Np1kp/4Pn1q/P7/1P3P2/R4RK1 w - - 4 36", listOf("d7e7", "g5h6", "e7h4"), "Skewer", "Rating 1204 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1280, "3r1r2/p4p2/1p2pp2/8/2P1bk2/3p3R/PP1N1P1P/R4K2 w - - 0 31", listOf("h3h4", "f4g5", "h4e4"), "Skewer", "Rating 1213 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1281, "r4rk1/2p2ppp/3p4/3Pp3/1p4P1/qQ1PP2P/2PK3N/3R2R1 b - - 0 23", listOf("a3b3", "c2b3", "a8a2", "d2e1", "a2h2"), "Skewer", "Rating 1223 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1282, "8/4Q3/p7/1p1k3p/2p5/P2pK3/1P6/7q w - - 6 59", listOf("e7b7", "d5d6", "b7h1"), "Skewer", "Rating 1233 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1283, "8/8/7p/pQ6/8/kB1K1P2/P7/4q3 b - - 2 60", listOf("e1f1", "d3e4", "f1b5"), "Skewer", "Rating 1242 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1284, "8/p1p5/6Q1/2p1kp1P/8/1P1p4/P1r3PK/4q3 w - - 2 42", listOf("g6e8", "e5d4", "e8e1"), "Skewer", "Rating 1251 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1285, "7r/ppp4p/8/5k2/8/1Q6/PPK5/5q2 w - - 6 53", listOf("b3f7", "f5e5", "f7f1"), "Skewer", "Rating 1260 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1286, "5r1k/8/1p4p1/2b3R1/8/1B6/P6P/7K b - - 1 40", listOf("f8f1", "h1g2", "f1g1", "g2f3", "g1g5"), "Skewer", "Rating 1268 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1287, "8/8/3Pb1k1/1QK2p2/4r3/P1P5/8/8 b - - 5 57", listOf("e4e5", "c5b6", "e5b5"), "Skewer", "Rating 1277 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1288, "4r3/p5R1/1b2B3/3PK3/2k1P2P/P7/8/8 b - - 0 44", listOf("b6d4", "e5d6", "d4g7"), "Skewer", "Rating 1286 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1289, "5rk1/1b6/p3R2p/1p3pp1/8/P1B3P1/1P3P2/6K1 w - - 2 28", listOf("e6g6", "g8f7", "g6g7", "f7e6", "g7b7"), "Skewer", "Rating 1294 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1290, "r2r2k1/pp1nbppp/4pq2/2p5/3P4/4BN2/PPPQ1PPP/R4RK1 w - - 6 12", listOf("e3g5", "f6g6", "g5e7"), "Skewer", "Rating 1303 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1291, "8/8/8/8/4K3/7R/4k3/r7 w - - 29 74", listOf("h3h2", "e2d1", "h2h1", "d1c2", "h1a1"), "Skewer", "Rating 1311 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1292, "r4rk1/4q3/p2p1pp1/1p1Pb1P1/3p3Q/7R/PP4PP/4R1K1 w - - 0 25", listOf("h4h8", "g8f7", "h3h7", "f7e8", "h7e7"), "Skewer", "Rating 1320 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1293, "1Q6/5p2/2K1p3/8/2k2PP1/8/8/q7 w - - 1 47", listOf("b8b5", "c4d4", "b5e5", "d4d3", "e5a1"), "Skewer", "Rating 1328 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1294, "7k/ppp4p/2n2r2/3P3r/1P1P4/P2QP1K1/8/6R1 b - - 0 29", listOf("h5g5", "g3h4", "g5g1"), "Skewer", "Rating 1337 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1295, "8/7p/1k1p2p1/3P4/4KP2/6RP/r7/8 b - - 4 38", listOf("a2a4", "e4e3", "a4a3", "e3e2", "a3g3"), "Skewer", "Rating 1345 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1296, "8/4B2N/p1p3p1/5p2/5k2/P2K1P2/7r/8 w - - 0 37", listOf("e7d6", "f4f3", "d6h2"), "Skewer", "Rating 1355 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1297, "4r3/7R/4B1p1/8/2P1KP2/r5k1/8/8 w - - 1 42", listOf("h7h3", "g3g2", "h3a3"), "Skewer", "Rating 1364 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1298, "6k1/6p1/4pr1P/4N2K/2pP4/2P1n3/8/7R b - - 4 53", listOf("f6h6", "h5g5", "h6h1"), "Skewer", "Rating 1372 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1299, "5k2/2R2p2/3N2p1/1KP1n1P1/8/8/8/3r4 w - - 3 39", listOf("c7c8", "f8e7", "c8e8", "e7d7", "e8e5"), "Skewer", "Rating 1389 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1300, "8/1R5p/6p1/3k4/1p6/3nP2P/1r1N2PK/8 w - - 11 49", listOf("b7d7", "d5e6", "d7d3"), "Skewer", "Rating 1398 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1301, "8/8/3b1kP1/p2p1P2/P2p4/r7/5K2/1B2R3 b - - 3 47", listOf("d6g3", "f2f1", "g3e1"), "Skewer", "Rating 1407 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1302, "7r/R4R2/2pkp3/4r1pp/P7/6PP/6P1/6K1 w - - 0 29", listOf("f7d7", "d6c5", "a7a5", "c5b4", "a5e5"), "Skewer", "Rating 1416 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1303, "8/1p2k1p1/3p2r1/3Pp3/pB6/Pr6/3R4/5K1R b - - 3 32", listOf("b3b1", "f1e2", "b1h1"), "Skewer", "Rating 1424 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1304, "8/p7/2p5/P3K3/1Pp5/2k2r2/8/R7 w - - 0 41", listOf("a1a3", "c3b4", "a3f3"), "Skewer", "Rating 1433 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1305, "rn1qk2r/pp3pp1/2p1p1p1/6N1/2PPp2P/4P3/PP1Q1PP1/R3K2R b KQkq - 1 12", listOf("d8g5", "h4g5", "h8h1", "e1e2", "h1a1"), "Skewer", "Rating 1441 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1306, "8/8/p7/1p5p/1b2B3/1k1K1P2/7r/R7 w - - 2 59", listOf("e4d5", "b3b2", "a1a2", "b2b1", "a2h2"), "Skewer", "Rating 1450 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1307, "6R1/4K3/R5p1/5pkp/7r/3r4/8/8 w - - 0 54", listOf("g8g6", "g5f4", "a6a4", "f4f3", "a4h4"), "Skewer", "Rating 1459 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1308, "5r2/6p1/3k3r/3p1n2/3P4/7P/6PK/Q7 w - - 12 45", listOf("a1a3", "d6d7", "a3f8"), "Skewer", "Rating 1476 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1309, "r4b1R/p3k3/2p2p2/1p3B1p/4Pp2/2Pn4/PP2N2K/8 w - - 0 29", listOf("h8h7", "e7d6", "h7d7", "d6c5", "d7d3"), "Skewer", "Rating 1485 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1310, "r4k2/1R4p1/6P1/2p2B2/p4P2/2b2K2/2P5/8 w - - 0 34", listOf("b7f7", "f8e8", "f5d7", "e8d8", "f7f8", "d8d7", "f8a8"), "Skewer", "Rating 1494 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1311, "8/5p2/6Pp/p2k4/6PP/6r1/PR2K3/8 b - - 0 46", listOf("g3g2", "e2e3", "g2b2", "g6f7", "b2b8"), "Skewer", "Rating 1504 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1312, "6k1/p5pp/4p3/2B1P3/5q2/1P3b2/P6P/3QK3 w - - 0 37", listOf("d1d8", "g8f7", "d8f8", "f7g6", "f8f4"), "Skewer", "Rating 1513 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1313, "3r1k2/7p/pp2P2r/5PR1/2b2K2/8/P7/3q2R1 w - - 0 37", listOf("g5g8", "f8e7", "g1g7", "e7d6", "g8d8", "d6c6", "d8d1"), "Skewer", "Rating 1521 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1314, "5Q2/p6k/1p5p/4P1p1/3P4/P1B3KP/1P6/2q5 b - - 0 41", listOf("c1g1", "g3f3", "g1f1", "f3e4", "f1f8"), "Skewer", "Rating 1531 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1315, "3rk3/7R/5p2/1P1Kp1b1/3p2P1/7P/5P2/8 w - - 1 40", listOf("d5e6", "e8f8", "h7h8", "f8g7", "h8d8"), "Skewer", "Rating 1540 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1316, "1r4k1/2p2r1p/3pN1pb/p2P4/P3Pp1q/2P5/1PQ2R2/2K2R2 w - - 6 34", listOf("f2h2", "f4f3", "c1b1", "h4g3", "h2h6"), "Skewer", "Rating 1548 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1317, "r1b2q2/pp1pkpp1/2n1p3/8/8/3Q4/P1P2PPP/bNB2RK1 w - - 0 15", listOf("c1a3", "e7e8", "a3f8"), "Skewer", "Rating 1557 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1318, "6k1/p1R3pp/1p4p1/6r1/1B5r/P4n1P/5P2/5R1K w - - 1 28", listOf("c7c8", "g8f7", "c8f8", "f7e6", "f8f3"), "Skewer", "Rating 1566 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1319, "8/r1pK2pp/k1pp4/4p3/8/1R3P1P/6P1/8 w - - 6 46", listOf("d7c6", "a6a5", "b3a3", "a5b4", "a3a7"), "Skewer", "Rating 1574 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1320, "2kr1b1r/1pp2pp1/p4qb1/3B2B1/3pP1pP/P2P4/1PP1QP2/R3K2R b KQ - 1 16", listOf("f6g5", "h4g5", "h8h1", "e1d2", "h1a1", "e2g4", "c8b8"), "Skewer", "Rating 1582 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1321, "r3r2k/b1p3pp/p1P3q1/1p6/3PNp2/PB3Q1b/1P3PPP/3RR1K1 b - - 4 25", listOf("h3g4", "f3f4", "g4d1"), "Skewer", "Rating 1591 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1322, "1nbqkb1r/r3pppp/p7/1p6/3QB3/2P2N2/P1P2PPP/R1B1K2R b KQk - 0 11", listOf("a7d7", "d4e5", "d7d1", "e1e2", "d1h1"), "Skewer", "Rating 1608 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1323, "6k1/2R3p1/4R2p/3p1p1r/3P4/5PP1/5K1P/1r6 b - - 2 33", listOf("h5h2", "f2e3", "b1e1", "e3f4", "e1e6"), "Skewer", "Rating 1617 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1324, "r4rk1/p2nbppp/2p1p3/1pq5/5P1N/PBBP2P1/1PP3KP/R4Q2 w - - 1 19", listOf("c3b4", "c5d4", "b4e7"), "Skewer", "Rating 1625 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1325, "2K2Q2/4R2p/8/8/8/1p6/1k4qP/8 b - - 6 48", listOf("g2a8", "c8c7", "a8f8"), "Skewer", "Rating 1633 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1326, "R7/P4k2/6pK/7p/r4p2/8/6P1/8 w - - 0 46", listOf("a8h8", "a4a7", "h8h7", "f7f6", "h7a7"), "Skewer", "Rating 1643 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1327, "8/5p2/1p2pk2/p3q1n1/P6Q/1P6/2B5/1K6 w - - 1 33", listOf("h4h8", "f6e7", "h8e5"), "Skewer", "Rating 1662 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1328, "4r1k1/pp4pp/2pq4/5QB1/1n1P4/2P4P/PP3PP1/R4K2 b - - 0 21", listOf("d6h2", "g5e3", "h2h1", "f1e2", "h1a1"), "Skewer", "Rating 1672 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1329, "1k6/p4p2/1p6/4PB2/8/2R3r1/PP5r/R4K2 b - - 1 33", listOf("g3c3", "b2c3", "h2h1", "f1e2", "h1a1"), "Skewer", "Rating 1682 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1330, "1r3k1r/pR1P1ppp/q1p1pb2/2P5/P7/B7/5PP1/3R3K w - - 0 26", listOf("b7b8", "f8e7", "b8h8", "a6f1", "d1f1"), "Skewer", "Rating 1692 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1331, "5R2/ppp1k3/8/8/2PP2pp/1P2q3/P7/5R1K w - - 4 36", listOf("f1f7", "e7d6", "f8d8", "d6e6", "d8e8", "e6f7", "e8e3"), "Skewer", "Rating 1702 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1332, "1k1rr3/1p5p/p5p1/1b6/6PP/PPNn4/3N2R1/R2K1B2 b - - 2 27", listOf("e8e1", "d1c2", "e1a1", "f1d3", "b5d3"), "Skewer", "Rating 1713 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1333, "3r2k1/p3qp1p/1p2p1p1/2pn4/8/1P2PQ2/PB3PPP/4R1K1 w - - 2 29", listOf("e3e4", "d5b4", "b2f6", "e7d6", "f6d8"), "Skewer", "Rating 1735 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1334, "3k1r2/8/R5P1/1p1p4/3P3P/5p2/P4P1K/1r6 w - - 3 41", listOf("g6g7", "f8g8", "a6a8", "d8e7", "a8g8"), "Skewer", "Rating 1748 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1335, "r1b2rk1/1pb2pp1/p1p2n1p/2P5/P4q1B/8/1PQ1BNPP/R4RK1 w - - 0 19", listOf("h4g3", "f4g5", "g3c7"), "Skewer", "Rating 1773 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1336, "1R6/8/8/R7/3k2K1/p3r1P1/6r1/8 w - - 10 49", listOf("b8b4", "d4c3", "a5a3", "c3b4", "a3e3"), "Skewer", "Rating 1786 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1337, "6k1/3R1pp1/7p/3Q1p1P/8/4P2K/5PP1/2r3q1 b - - 1 38", listOf("g1h1", "h3g3", "h1h5", "d5a8", "g8h7"), "Skewer", "Rating 1800 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1338, "2kr2nr/ppp2ppp/2bb1q2/8/4P3/2N2N2/PPP1Q1PP/R1B1K2R w KQ - 5 12", listOf("c1g5", "f6g6", "g5d8"), "Skewer", "Rating 1811 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1339, "8/5k2/1R3p2/5P2/K1PP4/1p6/8/1r6 b - - 1 56", listOf("b1a1", "a4b3", "a1b1", "b3c3", "b1b6"), "Skewer", "Rating 1823 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1340, "r2r2k1/1b3pp1/p3p2p/1pR5/1P2q3/P7/4BPPP/2QR2K1 w - - 2 21", listOf("e2f3", "d8d1", "c1d1", "e4h4", "f3b7"), "Skewer", "Rating 1837 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1341, "rn2k2r/pp3ppp/3b1p2/3b4/3P4/5NP1/Pq2PPBP/2RQK2R w Kkq - 0 12", listOf("c1c8", "e8e7", "c8h8", "d6b4", "e1f1"), "Skewer", "Rating 1852 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1342, "8/3Q4/6pk/3P1p2/5K2/2q4P/P7/8 b - - 3 62", listOf("g6g5", "f4f5", "c3h3", "f5e5", "h3d7"), "Skewer", "Rating 1867 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1343, "2r3k1/1p1q1p1p/p3pQp1/7P/1P1p4/8/6P1/5R1K w - - 0 33", listOf("h5h6", "g8f8", "f6g7", "f8e8", "g7g8", "e8e7", "f1f7", "e7d6", "f7d7"), "Skewer", "Rating 1884 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1344, "r3k2r/pp2bppp/2n5/3pP2b/3Pq3/2PQBN2/P3K1PP/1R3B1R b kq - 6 14", listOf("e4d3", "e2d3", "h5g6", "d3d2", "g6b1"), "Skewer", "Rating 1899 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1345, "8/6k1/2p5/2P1p1pP/4PpP1/5K2/2bN4/8 b - - 2 47", listOf("c2d1", "f3f2", "d1g4", "d2c4", "g4h5"), "Skewer", "Rating 1912 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1346, "1r4k1/2pbbppp/4pn2/2Pp4/3P1P2/1qNQPN2/3B2PP/5RK1 b - - 4 19", listOf("d7b5", "d3b1", "b5f1", "b1b3", "b8b3"), "Skewer", "Rating 1925 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1347, "6k1/p4p2/1bp1qn1p/2p3p1/2r2B2/PQ2P1P1/4NP1P/3R2K1 w - - 0 25", listOf("d1d6", "e6e4", "d6f6", "e4c2", "b3c2"), "Skewer", "Rating 1939 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1348, "7r/6k1/4p3/2PnNp1q/3P1Pp1/4P3/R4KP1/4Q3 b - - 4 35", listOf("g4g3", "f2g3", "h5h4", "g3f3", "h4e1"), "Skewer", "Rating 1954 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1349, "r2qk2r/1Q1b1ppp/p1n1p3/4P3/2pP4/B1P2N2/P4PPP/R4RK1 b kq - 0 17", listOf("a8b8", "b7a6", "b8a8", "a6c4", "a8a3"), "Skewer", "Rating 2017 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1350, "8/R6p/6kP/2P5/1p6/p4p2/3K4/6b1 w - - 2 46", listOf("a7g7", "g6f5", "g7g1"), "Skewer", "Rating 2037 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk18(): List<Puzzle> {
        return listOf(
            Puzzle(1351, "1k3b1r/Rp4p1/1r1p3p/8/2Q5/4P3/PqP3PP/5RK1 w - - 0 36", listOf("c4a4", "f8e7", "a7a8", "b8c7", "a8h8"), "Skewer", "Rating 2056 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1352, "6r1/7R/2p1k3/p4pPp/PbPKpB1P/1P6/6P1/8 b - - 4 43", listOf("g8d8", "d4e3", "b4d2", "e3f2", "d2f4"), "Skewer", "Rating 2075 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1353, "2kr1r2/1p6/2p5/2P2R1p/pP1Pp1p1/1q2P3/6PP/RQ4K1 w - - 5 25", listOf("b1b3", "a4b3", "f5f8", "d8f8", "a1a8", "c8d7", "a8f8"), "Skewer", "Rating 2092 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1354, "b1r3k1/2p2p2/p3p1pQ/P1R1P2n/1p1P4/5N2/1P3PPK/1q6 w - - 0 29", listOf("f3g5", "b1f5", "h6h7", "g8f8", "h7h8", "f8e7", "h8c8", "f5f4", "h2g1"), "Skewer", "Rating 2115 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1355, "1r1k4/3P3R/p7/2pK1p2/n2r4/P7/5P2/6R1 w - - 7 44", listOf("d5e5", "d4g4", "g1g4", "f5g4", "h7h8", "d8d7", "h8b8"), "Skewer", "Rating 2142 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1356, "1k1r3r/1bp2pQ1/p7/1p2RB1p/2q2P2/2P1B3/PP4PP/RN4K1 b - - 4 18", listOf("d8d1", "g1f2", "c4f1", "f2g3", "f1g2", "g3h4", "g2g7"), "Skewer", "Rating 2169 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1357, "4Q3/6pk/2p5/1pRp1p2/1P1P3P/3qP1P1/5PK1/r4B2 b - - 4 44", listOf("d3f1", "g2f3", "f1d1", "f3f4", "d1g4", "f4e5", "g4e4", "e5d6", "e4e8"), "Skewer", "Rating 2199 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1358, "2kr3r/1R6/2p1p3/2N4p/1PqP1p1P/5P2/1P3K2/Q6R b - - 4 28", listOf("c4d4", "f2g2", "h8g8", "g2f1", "d4d1", "a1d1", "d8d1", "f1e2", "d1h1"), "Skewer", "Rating 2340 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1359, "8/8/p1b2pK1/1pP1kP1P/1P6/P5P1/8/8 b - - 0 56", listOf("c6e8", "g6g7", "e8h5", "c5c6", "h5g4"), "Skewer", "Rating 2511 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1360, "5r1k/1pp3p1/1p1p3p/8/2Bn1R2/7P/PP2RPP1/6K1 b - - 0 24", listOf("d4e2", "c4e2", "f8f4"), "Zwischenzug", "Rating 689 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1361, "r5k1/5pp1/3r3p/p1Q1p3/2B1b3/2P2P2/5P1P/R4RK1 b - - 0 24", listOf("d6g6", "g1h1", "e4f3"), "Zwischenzug", "Rating 936 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1362, "4r1k1/1p3p1p/pq3Bp1/8/P1P1Q3/1P5P/5PP1/4R1K1 b - - 0 30", listOf("e8e4", "e1e4", "b6f6"), "Zwischenzug", "Rating 981 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1363, "6k1/5pp1/7p/3pPp2/p2r4/1q6/6PP/2R2QK1 w - - 0 32", listOf("c1c8", "g8h7", "f1f5", "g7g6", "f5f7"), "Zwischenzug", "Rating 1009 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1364, "r5k1/1Q3p1p/5p2/2q5/8/2p1P2P/2r2PP1/2R3K1 w - - 0 28", listOf("b7a8", "g8g7", "c1c2"), "Zwischenzug", "Rating 1038 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1365, "1r3bk1/p1qB3p/1p2ppp1/n1p5/Q3b3/2P1BN2/PP3PPP/5RK1 w - - 0 26", listOf("d7e6", "g8h8", "a4e4"), "Zwischenzug", "Rating 1059 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1366, "6k1/p1q1Qp2/6p1/3R3p/1P6/P3PPP1/2rR1K1P/8 b - - 0 34", listOf("c2d2", "d5d2", "c7e7"), "Zwischenzug", "Rating 1081 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1367, "r1qr2k1/pp2bppp/4p3/4n3/N7/6Q1/PP3PPP/2R2RK1 w - - 0 21", listOf("c1c8", "a8c8", "g3e5"), "Zwischenzug", "Rating 1100 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1368, "R7/pb3k2/1p2pr2/8/3P4/2P1KN1p/PP5P/R7 b - - 0 26", listOf("f6f3", "e3e2", "b7a8"), "Zwischenzug", "Rating 1119 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1369, "3q1rk1/4ppbp/3p1np1/2p3B1/4r3/1P1Q1N2/P4PPP/R4RK1 w - - 0 22", listOf("g5f6", "g7f6", "d3e4"), "Zwischenzug", "Rating 1131 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1370, "r1b1r1k1/1p2Bp1p/p4p2/4pP2/3qP3/8/PP4PP/n2Q1R1K w - - 0 20", listOf("d1g4", "g8h8", "e7f6"), "Zwischenzug", "Rating 1143 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1371, "r3r1k1/pb1q1pbp/2p2Rp1/4p3/4P3/2N1B3/PPPQB1PP/R5K1 b - - 0 15", listOf("d7d2", "e3d2", "g7f6"), "Zwischenzug", "Rating 1155 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1372, "4r3/5pkp/1p4p1/p2p1qP1/3P3Q/8/2r2PP1/4R1K1 w - - 0 36", listOf("h4h6", "g7h8", "e1e8"), "Zwischenzug", "Rating 1164 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1373, "5rk1/1R3pp1/2B4p/1p6/2R5/6KP/5PP1/1r6 b - - 0 30", listOf("b1b3", "f2f3", "b5c4"), "Zwischenzug", "Rating 1173 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1374, "1rr3k1/1p1q1pbp/p4Bp1/8/P3p3/1PN2P2/3Q2PP/2RR2K1 b - - 0 19", listOf("d7d2", "d1d2", "g7f6"), "Zwischenzug", "Rating 1182 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1375, "7k/Bq4pp/5p2/1p2p3/1P2B2N/6P1/1Q1r1P1P/6K1 b - - 0 34", listOf("d2d1", "g1g2", "b7e4"), "Zwischenzug", "Rating 1191 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1376, "4r3/p1q2p1k/1np3pb/4p3/4P3/1P5Q/2P2PPP/2BrR1K1 w - - 0 30", listOf("h3h6", "h7g8", "e1d1"), "Zwischenzug", "Rating 1202 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1377, "4r1k1/p4pbp/1q4p1/8/Q1p1BP2/2P3P1/P6P/3rRK2 w - - 0 27", listOf("a4e8", "g7f8", "e1d1"), "Zwischenzug", "Rating 1217 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1378, "4rrk1/pb6/1p5p/3P2p1/2P2P2/3n3P/R1B3P1/4R1K1 w - - 0 29", listOf("e1e8", "f8e8", "c2d3"), "Zwischenzug", "Rating 1231 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1379, "1k1r4/pp3p2/3n4/2qP2Q1/2p4P/2P5/PPK5/4rR2 w - - 0 32", listOf("g5d8", "d6c8", "f1e1", "c5f2", "c2d1"), "Zwischenzug", "Rating 1243 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1380, "2rrb1k1/qN3pbp/p3p1p1/3pP2P/3n4/R4N2/PP3PP1/R2Q1BK1 b - - 0 20", listOf("d4f3", "a3f3", "a7b7"), "Zwischenzug", "Rating 1255 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1381, "2rr2k1/1p2q1P1/4b1p1/4N3/3P4/pP1Q4/P7/1K2R2R b - - 0 31", listOf("e6f5", "h1h8", "g8g7", "d3f5", "g6f5"), "Zwischenzug", "Rating 1266 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1382, "2r2rk1/4bppp/p3p3/1p6/3Bb3/P4N2/1P3PPP/2R1R1K1 w - - 0 23", listOf("c1c8", "f8c8", "e1e4"), "Zwischenzug", "Rating 1277 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1383, "2k4r/ppp2Npp/2nbpq2/5b1B/2P2r2/5Q2/P1P2PPP/3R1RK1 w - - 0 18", listOf("f7d6", "c7d6", "f3f4"), "Zwischenzug", "Rating 1289 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1384, "3r1r1k/7p/p2p4/1pp1p1q1/4P2p/1P1P1Q1P/PBP2P1K/6R1 w - - 0 28", listOf("f3f8", "d8f8", "g1g5"), "Zwischenzug", "Rating 1299 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1385, "r3r1k1/p1p1qpp1/1p1p1b1p/8/2nNNP2/1P4P1/P1Q1P1KP/R4R2 w - - 0 17", listOf("e4f6", "e7f6", "c2c4"), "Zwischenzug", "Rating 1309 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1386, "2kr3r/ppp1q2p/8/5p1P/Q1PP2B1/b3P1P1/P2B1P2/K2R3b w - - 0 23", listOf("g4f5", "c8b8", "d1h1"), "Zwischenzug", "Rating 1319 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1387, "R7/6k1/1np5/3p4/1p6/1P2r3/PK2N3/5R2 b - - 0 32", listOf("e3e2", "b2c1", "b6a8"), "Zwischenzug", "Rating 1328 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1388, "2r3k1/p4p1p/4pbp1/3p4/4N3/Pr2P1P1/1P1q1P1P/1RR3K1 w - - 0 22", listOf("c1c8", "g8g7", "e4d2"), "Zwischenzug", "Rating 1337 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1389, "1r1q1rk1/p4ppp/2pb1B2/3p4/8/2NB1b2/PPP2PPP/1R2R1K1 w - - 0 15", listOf("f6d8", "f8d8", "g2f3"), "Zwischenzug", "Rating 1346 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1390, "5rk1/3p1pp1/p5Np/8/2b1P3/1r2P3/1P4PP/2R2R1K w - - 0 28", listOf("g6e7", "g8h7", "c1c4"), "Zwischenzug", "Rating 1355 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1391, "r1b1k2r/ppp1qppp/2n5/8/2Bp1b2/5n2/PPPN1PPP/R1KQR3 w kq - 0 12", listOf("e1e7", "e8e7", "d1f3", "f4d2", "c1d2"), "Zwischenzug", "Rating 1363 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1392, "r4rk1/p1q2ppp/2p1p3/2bpPb2/4n3/P2BP3/1BQ2PPP/RN3RK1 w - - 0 15", listOf("d3e4", "f5e4", "c2c5"), "Zwischenzug", "Rating 1371 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1393, "7k/p3q1p1/1p6/2pn2r1/5Q2/2B3PP/PP4B1/6K1 w - - 0 36", listOf("f4h4", "h8g8", "g2d5", "g5d5", "h4e7"), "Zwischenzug", "Rating 1380 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1394, "2kr3r/pppq1pp1/3b1n2/3Pn2p/8/P1N1BN1P/1P2BP2/R2Q1RK1 b - - 0 16", listOf("e5f3", "e2f3", "d7h3"), "Zwischenzug", "Rating 1395 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1395, "r1b2rk1/pppp1p2/6p1/4P2p/1n1n2R1/1N3NP1/PP2QP1P/R4BK1 b - - 0 19", listOf("d4e2", "f1e2", "h5g4"), "Zwischenzug", "Rating 1403 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1396, "5r2/1p1b2k1/p1p3p1/4P1q1/8/P3Q1Pp/B4R1K/4R3 b - - 0 31", listOf("g5e3", "e1e3", "f8f2"), "Zwischenzug", "Rating 1410 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1397, "2rq1rk1/pp2ppb1/2n4p/6p1/4b3/1B3NB1/P1P1QPPP/3R2K1 w - - 0 20", listOf("d1d8", "f8d8", "e2e4", "d8d1", "f3e1"), "Zwischenzug", "Rating 1418 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1398, "rn2kb1r/ppq2ppp/2pp1n2/4N2b/2PP4/2N1P2P/PP1BBPP1/R2QK2R b KQkq - 0 9", listOf("h5e2", "d1e2", "d6e5"), "Zwischenzug", "Rating 1425 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1399, "r2q1rk1/4bppp/p1n1p3/1p1p2Bn/3P4/1B3P2/PPP1NP2/R2QK2R w KQ - 0 15", listOf("g5e7", "d8e7", "h1h5"), "Zwischenzug", "Rating 1433 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1400, "4r1kr/ppp3p1/2bq3p/8/2P3R1/1P1P3P/PB4P1/R4nK1 w - - 0 25", listOf("g4g7", "g8f8", "a1f1"), "Zwischenzug", "Rating 1440 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1401, "rq4k1/5pp1/7p/8/rR1PQ3/4P1P1/5P1P/1R4K1 b - - 0 31", listOf("a4b4", "b1b4", "a8a1", "g1g2", "b8b4"), "Zwischenzug", "Rating 1448 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1402, "5rk1/ppp3p1/2p1p3/2P1Rr2/1P6/2Nq2Q1/P4PPP/5RK1 b - - 0 25", listOf("d3g3", "h2g3", "f5e5"), "Zwischenzug", "Rating 1455 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1403, "rnbQ1rk1/p3ppbp/1p4p1/2p1P3/4BP2/2n5/PPP1N1PP/R1B2RK1 b - - 0 11", listOf("c3e2", "g1h1", "f8d8"), "Zwischenzug", "Rating 1461 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1404, "r1bq2k1/1p4bp/p2N2p1/2pP1p2/P3rB2/6P1/1PQ1PP1P/R4RK1 b - - 0 20", listOf("e4f4", "g3f4", "d8d6"), "Zwischenzug", "Rating 1468 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1405, "r4rk1/p1p1b1pp/1p1pp1q1/8/2P1n3/PP1QPNB1/5PPP/R4RK1 b - - 3 18", listOf("e4g3", "d3g6", "g3e2", "g1h1", "h7g6"), "Zwischenzug", "Rating 1481 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1406, "3rq1k1/p4pbp/6p1/5n2/P4B2/NP3Q1P/3R1PP1/6K1 b - - 0 23", listOf("e8e1", "g1h2", "d8d2", "f4d2", "e1d2"), "Zwischenzug", "Rating 1496 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1407, "r2qr1k1/p1pp1pbp/b1p3p1/4N3/3Pn3/1P3Q2/PBP2PPP/R3R1K1 w - - 0 15", listOf("f3f7", "g8h8", "e1e4"), "Zwischenzug", "Rating 1502 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1408, "r4r1k/p3Nppp/1p1p4/5QRn/3P1p2/4P3/PP3q1P/3R3K w - - 0 24", listOf("f5h7", "h8h7", "g5h5"), "Zwischenzug", "Rating 1509 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1409, "5rk1/p4ppp/1p1q4/3B1Q2/P7/4PR1P/1Pr3P1/6K1 w - - 0 25", listOf("d5f7", "g8h8", "f5c2"), "Zwischenzug", "Rating 1515 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1410, "r4rk1/3N1ppp/8/p2b1Q2/1p6/1q3P2/7P/2K4R w - - 0 34", listOf("d7f6", "g7f6", "h1g1", "g8h8", "f5f6"), "Zwischenzug", "Rating 1522 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1411, "r2qr1k1/1ppbbp1p/p3p1p1/3NP1B1/7Q/3B4/PPP3PP/R4RK1 b - - 0 16", listOf("e7g5", "h4f2", "e6d5"), "Zwischenzug", "Rating 1528 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1412, "r2r2k1/pp2bppp/1Bp2n2/4N3/q7/1Q1N3P/PP3PP1/R2R2K1 b - - 0 18", listOf("a4b3", "a2b3", "a7b6", "a1a8", "d8a8"), "Zwischenzug", "Rating 1534 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1413, "6r1/5k2/p1p1r3/8/6Q1/6B1/PPP2PP1/4R1K1 b - - 0 28", listOf("e6e1", "g1h2", "g8g4"), "Zwischenzug", "Rating 1540 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1414, "2R2rk1/5pb1/1p2p3/p3q3/6Q1/P3BP1p/1P2P2P/3R2K1 b - - 0 28", listOf("e5e3", "g1h1", "f8c8"), "Zwischenzug", "Rating 1545 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1415, "4rrk1/6p1/3p1q1p/8/pPPQ4/P4P1P/6P1/1R1R2K1 b - - 0 31", listOf("e8e1", "g1h2", "f6d4", "d1d4", "e1b1"), "Zwischenzug", "Rating 1551 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1416, "6k1/4qbpp/p1p2pn1/1p1p1B2/1P6/2P1Q1B1/P4PPP/6K1 w - - 1 28", listOf("f5g6", "e7e3", "g6f7", "g8f7", "f2e3"), "Zwischenzug", "Rating 1544 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1417, "3r1bk1/2q2b1p/p5p1/1pN1p3/1PB1P3/P6P/4Q1P1/2Rr2BK w - - 0 33", listOf("c4f7", "c7f7", "c1d1", "d8d1", "e2d1"), "Zwischenzug", "Rating 1556 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1418, "1r4k1/6p1/1pp1p1Bp/6q1/3Q2P1/8/PP3R1P/3n2K1 w - - 0 31", listOf("g6f7", "g8h8", "d4d1"), "Zwischenzug", "Rating 1562 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1419, "5k2/R3Rp2/6p1/5r2/r6p/5P1P/6P1/5K2 w - - 0 32", listOf("e7f7", "f5f7", "a7a4"), "Zwischenzug", "Rating 1568 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1420, "5rk1/5Rp1/2p1Qb1p/3p3P/8/2P5/1q3PP1/R5K1 b - - 0 35", listOf("b2a1", "g1h2", "f8f7"), "Zwischenzug", "Rating 1574 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1421, "1k3r2/1p1q4/pR6/4p3/4Pn1p/2pPB2b/2P2P1K/3Q2N1 w - - 0 34", listOf("e3f4", "f8f4", "g1h3"), "Zwischenzug", "Rating 1579 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1422, "r4rk1/5pp1/p5R1/1p3N2/1Pp1P3/P1q3QP/5PP1/4R1K1 b - - 0 26", listOf("c3e1", "g1h2", "f7g6", "f5e7", "g8f7"), "Zwischenzug", "Rating 1585 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1423, "r4rk1/ppp3pp/3p1q2/2b1N3/4Q3/P1P1P2P/1P3PP1/R1B1K2R b KQ - 0 14", listOf("f6f2", "e1d1", "d6e5"), "Zwischenzug", "Rating 1591 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1424, "r1b2rk1/pppp1ppp/2n2n2/4q3/3N4/2QBB3/PPP2PPP/2KR3R w - - 5 11", listOf("d4c6", "e5c3", "c6e7", "g8h8", "b2c3"), "Zwischenzug", "Rating 1597 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1425, "1k3r1r/ppp5/8/P2p3q/3Pp1p1/2P1B1n1/1P2QR1P/R5K1 w - - 0 28", listOf("f2f8", "h8f8", "h2g3", "f8h8", "e2g2"), "Zwischenzug", "Rating 1602 - Find the best sequence of moves!", Color.WHITE)
        )
    }

    private fun getChunk19(): List<Puzzle> {
        return listOf(
            Puzzle(1426, "1r4k1/pr3ppp/4p3/2P5/P2P4/1n2P1P1/1R3P1P/2B2RK1 b - - 2 26", listOf("b3c1", "b2b7", "c1e2", "g1g2", "b8b7"), "Zwischenzug", "Rating 1606 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1427, "3r1r1k/p2qb1p1/1p5p/1P2pN2/P2pn3/7P/QB3PP1/2R1R1K1 w - - 0 25", listOf("f5e7", "d7e7", "e1e4"), "Zwischenzug", "Rating 1608 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1428, "5rk1/ppqbppb1/2Np2p1/3P3p/7P/3QBP2/PPr3P1/1K1R3R w - - 0 18", listOf("c6e7", "g8h7", "d3c2", "c7c2", "b1c2"), "Zwischenzug", "Rating 1613 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1429, "r4rk1/4pq2/3p3p/2pQb1pN/1p2P1P1/2P4P/1P3P2/R4RK1 w - - 0 25", listOf("a1a8", "f7d5", "a8f8", "g8f8", "e4d5"), "Zwischenzug", "Rating 1619 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1430, "Q4rk1/4bppp/4b3/1p2q3/5P2/2P1B3/PP4PP/2K4R w - - 0 20", listOf("a8f8", "g8f8", "f4e5"), "Zwischenzug", "Rating 1624 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1431, "r4r2/p6k/2p1pb1p/1p2Ppp1/3P4/1PP4R/1P6/2K4R w - - 0 27", listOf("h3h6", "h7g8", "e5f6", "g8f7", "h6h7"), "Zwischenzug", "Rating 1630 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1432, "3r1r2/1b3pkp/pq2pbp1/1p1pN3/1P1P1PP1/P2B3Q/4N2P/5RK1 w - - 3 21", listOf("g4g5", "f6e5", "h3h6", "g7g8", "f4e5"), "Zwischenzug", "Rating 1636 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1433, "5r1k/6pq/4Q2p/P1P2b2/8/1R2p1bP/3N2P1/B4RK1 b - - 0 34", listOf("g3f2", "g1h1", "f5e6"), "Zwischenzug", "Rating 1642 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1434, "5r1k/1R4p1/q4n1p/4pP1P/p2p2P1/P2P1Q2/1P4K1/2R5 b - - 0 40", listOf("e5e4", "d3e4", "a6b7"), "Zwischenzug", "Rating 1649 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1435, "2kr2r1/ppp3bp/2q1p1p1/3n4/4Q3/2N5/PPPP1PPP/R1B2RK1 b - - 6 14", listOf("d5c3", "e4c6", "c3e2", "g1h1", "b7c6"), "Zwischenzug", "Rating 1655 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1436, "1kn1q3/p3r2p/1p1pR1p1/2pb1pP1/5P2/2N4P/PPP1Q3/2K2B2 w - - 0 25", listOf("e6e7", "e8e7", "c3d5", "e7e2", "f1e2"), "Zwischenzug", "Rating 1661 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1437, "r3r1k1/1b3p1p/1pn3p1/1Bpq4/8/2N5/PPP2PPP/R3R1K1 w - - 0 18", listOf("e1e8", "a8e8", "c3d5"), "Zwischenzug", "Rating 1667 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1438, "r4rk1/pp1bbpp1/2p1p2p/8/4n3/4BB2/PPP2PPP/R2R2K1 w - - 0 16", listOf("d1d7", "e7g5", "f3e4", "g5e3", "f2e3"), "Zwischenzug", "Rating 1681 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1439, "7r/2k3p1/4p2p/1RnpP3/2nr4/2P5/6PP/R6K w - - 0 33", listOf("b5c5", "c7b6", "c3d4"), "Zwischenzug", "Rating 1687 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1440, "r2r2k1/2q2ppp/b4n2/p3p3/1B2P3/3B1P2/1PP3PP/R3QR1K b - - 0 20", listOf("a6d3", "c2d3", "a5b4", "a1a8", "d8a8"), "Zwischenzug", "Rating 1693 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1441, "r4r2/ppq4k/3Rp2p/4Np2/2P1n1p1/1P2PNPP/PQ3PK1/3R4 b - - 0 26", listOf("g4f3", "e5f3", "e4d6"), "Zwischenzug", "Rating 1700 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1442, "5r1k/2pnb1pp/1p1pR1q1/1P6/2PP4/B3QN2/5PPP/6K1 b - - 0 23", listOf("f8f3", "e6g6", "f3e3", "f2e3", "h7g6"), "Zwischenzug", "Rating 1707 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1443, "r1bq1rk1/pp3ppp/3b4/8/3QBB2/8/PPP3PP/R4RK1 b - - 0 14", listOf("d6f4", "d4d8", "f4e3", "g1h1", "f8d8"), "Zwischenzug", "Rating 1714 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1444, "q5k1/5pbp/rp1p2p1/2pP4/rpQ1P3/4BP1P/RP4P1/R6K w - - 0 29", listOf("c4a6", "a8a6", "a2a4", "a6a4", "a1a4"), "Zwischenzug", "Rating 1722 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1445, "2r4k/p4pqp/1p4np/2p1r2R/2PpQ2N/8/PP4P1/5RK1 w - - 0 32", listOf("h4g6", "h7g6", "h5e5"), "Zwischenzug", "Rating 1737 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1446, "5rk1/3r1ppp/pp6/3p4/4RP2/6P1/PP2P2P/2KR2B1 b - - 0 22", listOf("d7c7", "c1b1", "d5e4"), "Zwischenzug", "Rating 1745 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1447, "2r2rk1/pq1nbppp/b7/1pppN1P1/4n3/1P2BN2/P1Q2PBP/R2R2K1 w - - 0 18", listOf("e5d7", "b7d7", "c2e4"), "Zwischenzug", "Rating 1760 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1448, "5bk1/1b3ppp/p4n2/4p3/1p1BPNQ1/5P1P/BPq3PK/8 w - - 2 27", listOf("a2f7", "g8h8", "f4g6", "h7g6", "g4h4", "f6h5", "f7g6"), "Zwischenzug", "Rating 1727 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1449, "8/3Nqk2/8/6p1/3Qb1P1/6K1/8/8 w - - 0 39", listOf("d7e5", "f7e6", "d4e4", "e7d6", "e4f5", "e6d5", "f5g5"), "Zwischenzug", "Rating 1784 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1450, "r5k1/pB3rbp/3qp1p1/4n3/8/2P4P/P4PP1/R1BQR1K1 b - - 0 19", listOf("d6d1", "e1d1", "f7b7"), "Zwischenzug", "Rating 1792 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1451, "r4n2/1p3pkp/p4p2/2qp4/5P1Q/3BrR1P/PP4P1/R5K1 w - - 0 23", listOf("h4g3", "f8g6", "f3e3"), "Zwischenzug", "Rating 1799 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1452, "r4r2/p1p2p1k/1p2Q2p/8/3P2nN/2Pq2P1/PP3PP1/3R1RK1 b - - 0 22", listOf("d3f1", "d1f1", "f7e6"), "Zwischenzug", "Rating 1807 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1453, "r1r3k1/bppq1p2/p2p1n2/3P2BP/1P2p1P1/2P2N2/P1Q1BP2/R3R1K1 b - - 0 25", listOf("d7g4", "g1h2", "e4f3", "e1g1", "g4h5"), "Zwischenzug", "Rating 1815 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1454, "r4r1k/pb4bp/2p5/2q2p2/4N1Q1/8/PP3PPP/R4RK1 w - - 0 20", listOf("g4g7", "h8g7", "e4c5"), "Zwischenzug", "Rating 1822 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1455, "4r1k1/1b1R1pp1/p5qp/1p6/4r3/P3P2P/1PQ2PPN/2R3K1 b - - 1 26", listOf("e4c4", "c2g6", "c4c1", "h2f1", "f7g6"), "Zwischenzug", "Rating 1599 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1456, "r4rk1/1bq2pb1/1p4Np/p2p4/P7/2Pn2P1/1P1N1PP1/1Q1RR1K1 w - - 0 22", listOf("g6e7", "g8h8", "b1d3"), "Zwischenzug", "Rating 1830 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1457, "6k1/1p2b1pp/p3p3/P2q4/1PQPn3/B4r1P/5PP1/2R3K1 w - - 0 27", listOf("c4d5", "e6d5", "g2f3"), "Zwischenzug", "Rating 1841 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1458, "5rk1/p4qpp/1p1bp1p1/3p4/1PpPN1P1/2P1P2P/P1Q2PK1/2R2R2 b - - 0 29", listOf("f7f3", "g2g1", "d5e4"), "Zwischenzug", "Rating 1850 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1459, "3n1rn1/pp1b2kp/3B2p1/3P2b1/4P2Q/N1r5/Pq4PP/R4R1K w - - 0 22", listOf("d6f8", "g7h8", "h4g5"), "Zwischenzug", "Rating 1861 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1460, "r3k2r/2qn1pp1/B1pb1n1p/p3p3/Pp2P3/1PN2N2/1BPQ1PPP/R4RK1 b kq - 0 14", listOf("b4c3", "d2c3", "a8a6"), "Zwischenzug", "Rating 1870 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1461, "6qk/R6p/1p4b1/4p3/1PPp1QPB/5p2/5P2/5K2 b - - 0 45", listOf("g8c4", "f1g1", "e5f4"), "Zwischenzug", "Rating 1880 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1462, "2r2bR1/1bq1pk2/p2pp2p/1p6/3P1B2/5N2/PP1NBn1P/1K4R1 w - - 3 22", listOf("f3e5", "d6e5", "e2h5", "f7f6", "f4e5"), "Zwischenzug", "Rating 1890 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1463, "1rr3k1/p5Np/3q2p1/5p2/4p3/1Q2P3/P1B3PP/1R2R1K1 b - - 0 24", listOf("b8b3", "c2b3", "g8g7"), "Zwischenzug", "Rating 1899 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1464, "rn4k1/p5p1/1p2rqp1/3p4/3Pn1p1/B2QPN1P/P4PK1/2R2R2 w - - 0 23", listOf("c1c8", "g8h7", "h3g4"), "Zwischenzug", "Rating 1907 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1465, "r2qk2r/pp2bpp1/5n1p/1bppP3/8/2N1P3/PP1B1PPP/R2QK2R w KQkq - 0 12", listOf("e5f6", "e7f6", "c3b5", "f6b2", "a1b1"), "Zwischenzug", "Rating 1914 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1466, "3n2k1/5pp1/1p2pn1p/1P1NN3/8/3r4/4BPPP/6K1 w - - 0 27", listOf("d5f6", "g7f6", "e5d3"), "Zwischenzug", "Rating 1923 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1467, "2kr3r/ppp2p1p/2nb4/8/3Nb1Bq/2P1P3/PP1B1PPP/R2Q1RK1 b - - 0 15", listOf("f7f5", "g2g3", "h4g4"), "Zwischenzug", "Rating 1931 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1468, "4k2r/3qppb1/prbp3p/4B3/4P1p1/1B2Q2P/PPP2PP1/3RR1K1 b k - 0 21", listOf("b6b3", "c2b3", "g7e5"), "Zwischenzug", "Rating 1938 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1469, "6k1/5pp1/7p/p7/1Np1R3/6P1/P2rRPKP/3r4 b - - 0 37", listOf("d2e2", "e4e2", "a5b4"), "Zwischenzug", "Rating 1946 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1470, "7r/2p1b1k1/p1np4/1p2p1p1/5n2/1BPqQ2P/PP1B2PK/5RN1 w - - 0 33", listOf("f1f4", "d3e3", "f4f7", "g7h6", "d2e3"), "Zwischenzug", "Rating 1963 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1471, "r1b2rk1/1p4b1/p1n3nB/2pp4/4p3/P1N3q1/1PP1BNP1/R2QK2R w KQ - 0 18", listOf("d1d5", "f8f7", "c3e4"), "Zwischenzug", "Rating 1972 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1472, "1k1r4/pp5R/5q2/4pP2/4p1Q1/P2PP3/1P6/2K5 b - - 0 29", listOf("f6c6", "c1b1", "e4d3"), "Zwischenzug", "Rating 1982 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1473, "2r5/p3r3/1p2Bkpp/2p2p2/8/2PP4/PP5P/2K1R1R1 b - - 0 29", listOf("c8e8", "c1d2", "e7e6"), "Zwischenzug", "Rating 1991 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1474, "1r5k/p1p1rBpp/1p6/4q3/8/3P1RPP/P1P1n2K/4RQ2 b - - 2 26", listOf("e2g3", "e1e5", "g3f1", "f3f1", "e7e5"), "Zwischenzug", "Rating 2000 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1475, "2kr3r/ppq2Npp/2pbpn2/5b2/2B5/1P4N1/P1PP1PPP/R2QR1K1 b - - 0 14", listOf("d6g3", "h2g3", "c7f7"), "Zwischenzug", "Rating 2011 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1476, "r2q1rk1/4ppBp/2p3p1/1p6/3P4/1QPbPN2/5PPP/R4RK1 b - - 0 18", listOf("d3f1", "a1f1", "g8g7"), "Zwischenzug", "Rating 2021 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1477, "5r2/p3kp1p/r3B1p1/3pQ3/1pp5/1P1P2P1/q1PK1P1P/3RR3 b - - 0 28", listOf("c4c3", "d2e2", "a6e6", "e2f3", "e6e5"), "Zwischenzug", "Rating 2043 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1478, "2k3r1/pp6/2pb4/4P3/7P/6Pq/P3QP2/R2R2K1 b - - 0 27", listOf("g8g3", "f2g3", "d6c5", "e2f2", "h3g3"), "Zwischenzug", "Rating 2084 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1479, "8/8/8/3r1p1R/2k1p1p1/6P1/5PKP/8 b - - 1 41", listOf("f5f4", "h5d5", "f4f3", "g2f1", "c4d5"), "Zwischenzug", "Rating 2054 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1480, "8/5pk1/4q1p1/2p1p3/p5r1/5Q2/1P4R1/4RK2 b - - 1 42", listOf("g4f4", "f3f4", "e6a6", "f1g1", "e5f4"), "Zwischenzug", "Rating 2063 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1481, "rr4bk/p2qn1pp/2p1n3/P3N3/2BPN3/4Q1PP/6K1/1R6 w - - 0 33", listOf("b1b8", "a8b8", "e5d7"), "Zwischenzug", "Rating 2074 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1482, "r4bk1/2q2p1p/B7/3ppN1P/6P1/Pp2QP2/1P6/1K1R3R b - - 0 30", listOf("c7c2", "b1a1", "a8a6", "e3g5", "g8h8", "g5f6", "a6f6"), "Zwischenzug", "Rating 2085 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1483, "5r1k/p4rb1/2Pp2q1/3Q1b2/3P1pP1/1P2pP1p/PB2B2P/3R1RK1 b - - 0 29", listOf("f5g4", "f3g4", "f4f3", "f1f3", "g6g4", "f3g3", "g4e2"), "Zwischenzug", "Rating 2094 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1484, "8/3p2k1/n2Pp2p/p1q1Prp1/PpN5/1P1Q2P1/7P/5R1K b - - 1 29", listOf("c5d5", "d3d5", "f5f1", "h1g2", "e6d5"), "Zwischenzug", "Rating 2106 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1485, "4rrk1/pp3qp1/3b2pp/8/2QP1nP1/7P/P2BR2N/5RK1 w - - 0 24", listOf("c4f7", "g8f7", "d2f4", "d6f4", "f1f4"), "Zwischenzug", "Rating 2136 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1486, "rn1Q1Bk1/p3p2p/2p1qpp1/2P5/8/5bP1/P4P1P/1R3RK1 b - - 1 23", listOf("b8d7", "d8e7", "d7f8", "b1b7", "e6e7"), "Zwischenzug", "Rating 2151 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1487, "r1b1k1nr/ppq1b1pp/4p3/3pn3/8/2PB1N2/5PPP/RNBQ1K1R w kq - 0 12", listOf("c1f4", "e7d6", "f3e5"), "Zwischenzug", "Rating 2164 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1488, "r3k2r/pp1b1ppp/4p3/q7/1bBnP3/5N2/P2B1PPP/R2Q1RK1 w kq - 0 13", listOf("f3d4", "b4d2", "d4b3", "d7a4", "d1d2", "a5d2", "b3d2"), "Zwischenzug", "Rating 2178 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1489, "2rq1r1k/pp1n1pbp/8/3N3p/3p4/3BBP2/PPPQ1P2/2K3R1 w - - 0 17", listOf("g1g7", "h8g7", "e3d4"), "Zwischenzug", "Rating 2194 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1490, "2r2rk1/pp1qppB1/2n3p1/7p/4n2P/1P1P1NP1/P4PK1/1R1QR3 b - - 0 17", listOf("e4f2", "g2f2", "g8g7"), "Zwischenzug", "Rating 2209 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1491, "r3k2r/pp2nppp/1qnQ4/4P1b1/8/5N2/PPP3PP/1K1R1R2 w kq - 0 16", listOf("d6d7", "e8f8", "f3g5", "c6e5", "g5e6", "f8g8", "d7e7"), "Zwischenzug", "Rating 2222 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1492, "rnbq1rk1/pp3p1p/2p4p/4b3/2PNP1B1/2N4P/PP3PP1/R2Q1RK1 b - - 1 13", listOf("d8d4", "g4c8", "d4d1", "a1d1", "f8c8"), "Zwischenzug", "Rating 2236 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1493, "8/8/2p3p1/p2k1pP1/p2PnP2/1P1BK3/8/8 w - - 0 42", listOf("d3e4", "f5e4", "b3a4", "c6c5", "d4c5"), "Zwischenzug", "Rating 2268 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1494, "2Q5/2B2ppk/p1q4p/1p6/8/2P3bK/PP2r2P/5R2 w - - 0 34", listOf("c8f5", "h7h8", "c7g3", "c6g2", "h3g4", "e2e4", "f1f4", "g2e2", "g4h3"), "Zwischenzug", "Rating 2286 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1495, "2b3k1/p1p3pp/1p1p2r1/1P1P1p2/P1PQpP2/2B1P1r1/4RRBq/4K3 b - - 0 35", listOf("g3g2", "f2g2", "h2h1", "e1d2", "g6g2", "e2g2", "h1g2"), "Zwischenzug", "Rating 2304 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1496, "r2q1rk1/p3b1pp/b3N3/3pP3/8/4B3/PP1Q2PP/R4RK1 b - - 0 20", listOf("f8f1", "a1f1", "d8d7", "e6c7", "a6f1"), "Zwischenzug", "Rating 2349 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1497, "3r2k1/1p1r1pp1/2p1p2p/2P5/1P3q2/3B1N1P/2QR1P2/6RK b - - 0 31", listOf("f4f3", "h1h2", "d7d3", "d2d3", "f3d3"), "Zwischenzug", "Rating 2379 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1498, "1k6/p2PBr2/1p5p/5qp1/2b5/4b2P/P1N2PP1/1R4K1 w - - 0 36", listOf("d7d8q", "b8b7", "f2e3"), "Zwischenzug", "Rating 2420 - Find the best sequence of moves!", Color.WHITE),
            Puzzle(1499, "q4rk1/3bppbp/5np1/2pNB3/1p2P3/1B1P3P/1PP1QPP1/4K2R b K - 1 17", listOf("f6d5", "e5g7", "d5f4", "e2f3", "g8g7"), "Zwischenzug", "Rating 2481 - Find the best sequence of moves!", Color.BLACK),
            Puzzle(1500, "8/8/4k3/p2pPp2/P3P2p/3K1P1P/8/8 b - - 0 45", listOf("f5e4", "f3e4", "e6e5"), "Zwischenzug", "Rating 2566 - Find the best sequence of moves!", Color.BLACK)
        )
    }

    val puzzles = getChunk0() + getChunk1() + getChunk2() + getChunk3() + getChunk4() + getChunk5() + getChunk6() + getChunk7() + getChunk8() + getChunk9() + getChunk10() + getChunk11() + getChunk12() + getChunk13() + getChunk14() + getChunk15() + getChunk16() + getChunk17() + getChunk18() + getChunk19()
}
