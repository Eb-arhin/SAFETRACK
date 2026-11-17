package com.example.util

import java.nio.charset.StandardCharsets

data class QrMatrix(
    val size: Int,
    val version: Int,
    val modules: List<BooleanArray>
) {
    fun get(row: Int, col: Int): Boolean {
        if (row !in 0 until size || col !in 0 until size) return false
        return modules[row][col]
    }
}

/**
 * Pure-Kotlin ISO/IEC 18004 Byte-mode QR code generator (Error Correction Level M, Versions 1–10).
 * Supports scannable URLs up to 213 UTF-8 bytes offline with zero native dependencies.
 */
object QrCodeGenerator {

    private data class VersionSpec(
        val version: Int,
        val totalCodewords: Int,
        val ecPerBlock: Int,
        val g1Blocks: Int,
        val g1DataLen: Int,
        val g2Blocks: Int,
        val g2DataLen: Int,
        val alignCenters: IntArray
    ) {
        val totalDataCodewords: Int get() = g1Blocks * g1DataLen + g2Blocks * g2DataLen
        val size: Int get() = version * 4 + 17
    }

    private val LEVEL_M_SPECS = listOf(
        VersionSpec(1, 26, 10, 1, 16, 0, 0, intArrayOf()),
        VersionSpec(2, 44, 16, 1, 28, 0, 0, intArrayOf(6, 18)),
        VersionSpec(3, 70, 26, 1, 44, 0, 0, intArrayOf(6, 22)),
        VersionSpec(4, 100, 18, 2, 32, 0, 0, intArrayOf(6, 26)),
        VersionSpec(5, 134, 24, 2, 43, 0, 0, intArrayOf(6, 30)),
        VersionSpec(6, 172, 16, 4, 27, 0, 0, intArrayOf(6, 34)),
        VersionSpec(7, 196, 18, 4, 31, 0, 0, intArrayOf(6, 22, 38)),
        VersionSpec(8, 242, 22, 2, 38, 2, 39, intArrayOf(6, 24, 42)),
        VersionSpec(9, 292, 22, 3, 36, 2, 37, intArrayOf(6, 26, 46)),
        VersionSpec(10, 346, 26, 4, 43, 1, 44, intArrayOf(6, 28, 50))
    )

    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) {
            GF_EXP[i] = GF_EXP[i - 255]
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[GF_LOG[a] + GF_LOG[b]]
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val factor = GF_EXP[i]
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], factor)
            }
            poly = next
        }
        return poly
    }

    private fun rsRemainder(data: IntArray, ecLen: Int): IntArray {
        val gen = rsGeneratorPoly(ecLen)
        val result = IntArray(ecLen)
        for (b in data) {
            val factor = (b and 0xFF) xor result[0]
            System.arraycopy(result, 1, result, 0, ecLen - 1)
            result[ecLen - 1] = 0
            if (factor != 0) {
                for (i in 0 until ecLen) {
                    result[i] = result[i] xor gfMul(gen[i + 1], factor)
                }
            }
        }
        return result
    }

    fun encode(content: String): QrMatrix {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val spec = LEVEL_M_SPECS.firstOrNull { spec ->
            val countBits = if (spec.version >= 10) 16 else 8
            val overheadBits = 4 + countBits
            overheadBits + bytes.size * 8 <= spec.totalDataCodewords * 8
        } ?: throw IllegalArgumentException("QR content exceeds supported length (${bytes.size} bytes)")

        val bits = ArrayList<Int>(spec.totalDataCodewords * 8)
        fun appendBits(value: Int, length: Int) {
            for (i in length - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Byte mode indicator = 0100
        appendBits(0b0100, 4)
        val countBits = if (spec.version >= 10) 16 else 8
        appendBits(bytes.size, countBits)
        for (b in bytes) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        val maxBits = spec.totalDataCodewords * 8
        val terminator = minOf(4, maxBits - bits.size)
        repeat(terminator) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        var padToggle = true
        while (bits.size < maxBits) {
            appendBits(if (padToggle) 0xEC else 0x11, 8)
            padToggle = !padToggle
        }

        val dataCodewords = IntArray(spec.totalDataCodewords) { idx ->
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or bits[idx * 8 + b]
            }
            v
        }

        // Split into RS blocks & interleave
        val dataBlocks = ArrayList<IntArray>()
        val ecBlocks = ArrayList<IntArray>()
        var offset = 0
        repeat(spec.g1Blocks) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.g1DataLen)
            offset += spec.g1DataLen
            dataBlocks.add(block)
            ecBlocks.add(rsRemainder(block, spec.ecPerBlock))
        }
        repeat(spec.g2Blocks) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.g2DataLen)
            offset += spec.g2DataLen
            dataBlocks.add(block)
            ecBlocks.add(rsRemainder(block, spec.ecPerBlock))
        }

        val interleaved = ArrayList<Int>(spec.totalCodewords)
        val maxDataBlockLen = maxOf(spec.g1DataLen, spec.g2DataLen)
        for (i in 0 until maxDataBlockLen) {
            for (blk in dataBlocks) {
                if (i < blk.size) interleaved.add(blk[i])
            }
        }
        for (i in 0 until spec.ecPerBlock) {
            for (blk in ecBlocks) {
                if (i < blk.size) interleaved.add(blk[i])
            }
        }

        val streamBits = ArrayList<Int>(interleaved.size * 8 + 7)
        for (cw in interleaved) {
            for (b in 7 downTo 0) streamBits.add((cw ushr b) and 1)
        }
        repeat(7) { streamBits.add(0) }

        val n = spec.size
        val baseModules = List(n) { BooleanArray(n) }
        val isFunction = List(n) { BooleanArray(n) }

        fun setFunc(r: Int, c: Int, dark: Boolean) {
            if (r in 0 until n && c in 0 until n) {
                baseModules[r][c] = dark
                isFunction[r][c] = true
            }
        }

        fun drawFinder(top: Int, left: Int) {
            for (dy in -1..7) {
                for (dx in -1..7) {
                    val r = top + dy
                    val c = left + dx
                    if (r !in 0 until n || c !in 0 until n) continue
                    val dark = dy in 0..6 && dx in 0..6 &&
                        (dy == 0 || dy == 6 || dx == 0 || dx == 6 || (dy in 2..4 && dx in 2..4))
                    setFunc(r, c, dark)
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, n - 7)
        drawFinder(n - 7, 0)

        // Timing patterns
        for (i in 8 until n - 8) {
            val dark = i % 2 == 0
            if (!isFunction[6][i]) setFunc(6, i, dark)
            if (!isFunction[i][6]) setFunc(i, 6, dark)
        }

        // Alignment patterns
        for (cy in spec.alignCenters) {
            for (cx in spec.alignCenters) {
                val overlapsFinder =
                    (cy < 9 && cx < 9) || (cy < 9 && cx > n - 10) || (cy > n - 10 && cx < 9)
                if (!overlapsFinder) {
                    for (dy in -2..2) {
                        for (dx in -2..2) {
                            val dist = maxOf(kotlin.math.abs(dy), kotlin.math.abs(dx))
                            setFunc(cy + dy, cx + dx, dist != 1)
                        }
                    }
                }
            }
        }

        // Dark module
        setFunc(4 * spec.version + 9, 8, true)

        // Reserve format info modules
        for (i in 0..8) {
            if (i != 6) {
                setFunc(8, i, false)
                setFunc(i, 8, false)
            }
        }
        for (i in 0..7) {
            setFunc(8, n - 1 - i, false)
            if (i < 7) setFunc(n - 1 - i, 8, false)
        }

        // Version info (V7+)
        if (spec.version >= 7) {
            var rem = spec.version shl 12
            for (i in 17 downTo 12) {
                if (((rem ushr i) and 1) != 0) rem = rem xor (0x1F25 shl (i - 12))
            }
            val verBits = (spec.version shl 12) or rem
            for (i in 0 until 18) {
                val dark = ((verBits ushr i) and 1) != 0
                val a = n - 11 + (i % 3)
                val b = i / 3
                setFunc(a, b, dark)
                setFunc(b, a, dark)
            }
        }

        // Place data bits in zig-zag
        var bitIdx = 0
        var col = n - 1
        var upward = true
        while (col > 0) {
            if (col == 6) col--
            for (step in 0 until n) {
                val r = if (upward) n - 1 - step else step
                for (dx in 0..1) {
                    val c = col - dx
                    if (!isFunction[r][c]) {
                        val bit = if (bitIdx < streamBits.size) streamBits[bitIdx++] == 1 else false
                        baseModules[r][c] = bit
                    }
                }
            }
            col -= 2
            upward = !upward
        }

        // Evaluate all 8 masks and pick lowest penalty
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestGrid = baseModules

        for (mask in 0..7) {
            val candidate = List(n) { r ->
                BooleanArray(n) { c ->
                    if (isFunction[r][c]) {
                        baseModules[r][c]
                    } else {
                        val flip = when (mask) {
                            0 -> (r + c) % 2 == 0
                            1 -> r % 2 == 0
                            2 -> c % 3 == 0
                            3 -> (r + c) % 3 == 0
                            4 -> (r / 2 + c / 3) % 2 == 0
                            5 -> (r * c) % 2 + (r * c) % 3 == 0
                            6 -> ((r * c) % 2 + (r * c) % 3) % 2 == 0
                            else -> ((r + c) % 2 + (r * c) % 3) % 2 == 0
                        }
                        baseModules[r][c] xor flip
                    }
                }
            }
            applyFormatBits(candidate, mask, n)
            val penalty = computePenalty(candidate, n)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestGrid = candidate
            }
        }

        applyFormatBits(bestGrid, bestMask, n)
        return QrMatrix(size = n, version = spec.version, modules = bestGrid)
    }

    private fun applyFormatBits(grid: List<BooleanArray>, mask: Int, n: Int) {
        // Error correction level M = 00
        val data = mask and 0x7 // since ecLevel M = 00
        var rem = data shl 10
        for (i in 14 downTo 10) {
            if (((rem ushr i) and 1) != 0) rem = rem xor (0x537 shl (i - 10))
        }
        val fmt = ((data shl 10) or rem) xor 0x5412

        for (i in 0..5) grid[8][i] = ((fmt ushr i) and 1) != 0
        grid[8][7] = ((fmt ushr 6) and 1) != 0
        grid[8][8] = ((fmt ushr 7) and 1) != 0
        grid[7][8] = ((fmt ushr 8) and 1) != 0
        for (i in 9..14) grid[14 - i][8] = ((fmt ushr i) and 1) != 0

        for (i in 0..7) grid[8][n - 1 - i] = ((fmt ushr i) and 1) != 0
        for (i in 8..14) grid[n - 15 + i][8] = ((fmt ushr i) and 1) != 0
    }

    private fun computePenalty(grid: List<BooleanArray>, n: Int): Int {
        var score = 0
        // Rule 1: 5+ consecutive same color in row/col
        for (r in 0 until n) {
            var run = 1
            for (c in 1 until n) {
                if (grid[r][c] == grid[r][c - 1]) {
                    run++
                    if (run == 5) score += 3 else if (run > 5) score += 1
                } else {
                    run = 1
                }
            }
        }
        for (c in 0 until n) {
            var run = 1
            for (r in 1 until n) {
                if (grid[r][c] == grid[r - 1][c]) {
                    run++
                    if (run == 5) score += 3 else if (run > 5) score += 1
                } else {
                    run = 1
                }
            }
        }
        // Rule 2: 2x2 blocks
        for (r in 0 until n - 1) {
            for (c in 0 until n - 1) {
                val color = grid[r][c]
                if (grid[r][c + 1] == color && grid[r + 1][c] == color && grid[r + 1][c + 1] == color) {
                    score += 3
                }
            }
        }
        // Rule 4: dark ratio
        var darkCount = 0
        for (r in 0 until n) for (c in 0 until n) if (grid[r][c]) darkCount++
        val pct = (darkCount * 100) / (n * n)
        val diff = kotlin.math.abs(pct - 50) / 5
        score += diff * 10
        return score
    }
}
