package com.kakario.minigames.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** 카드 한 장: suit 0♠ 1♥ 2♦ 3♣ (조커는 suit 4, rank 1 컬러 / 2 흑백), rank 1..13 */
data class PCard(val suit: Int, val rank: Int) {
    val joker get() = suit == 4
    val asset: String get() = if (joker) (if (rank == 1) "JK1" else "JK2") else "SHDC"[suit] + when (rank) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> rank.toString() }
    val label: String get() = if (joker) (if (rank == 1) "컬러 조커" else "흑백 조커") else "♠♥♦♣"[suit] + when (rank) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> rank.toString() }
    companion object {
        fun deck(jokers: Boolean = false): MutableList<PCard> {
            val d = MutableList(52) { PCard(it / 13, it % 13 + 1) }
            if (jokers) { d += PCard(4, 1); d += PCard(4, 2) }
            return d
        }
    }
}

/** 키티 카드 그림을 크기별로 줄여서 보관. */
class CardArt(private val c: Context) {
    private val raw = HashMap<String, Bitmap>()
    private val scaled = HashMap<String, Bitmap>()
    private var w = 0
    fun get(name: String, width: Int): Bitmap {
        if (width != w) { scaled.clear(); w = width }
        return scaled.getOrPut(name) {
            val r = raw.getOrPut(name) { c.assets.open("cards/$name.png").use { BitmapFactory.decodeStream(it) } }
            Bitmap.createScaledBitmap(r, width.coerceAtLeast(4), (r.height * width.toFloat() / r.width).toInt().coerceAtLeast(4), true)
        }
    }
}
