package com.ailover.app.util

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination

/**
 * 拼音工具类，用于通讯录按拼音首字母分组排序。
 */
object PinyinUtils {

    private val pinyinFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.UPPERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
    }

    /**
     * 获取字符串的拼音首字母（大写）。
     * - 中文字符：取第一个字的拼音首字母
     * - 英文字符：取第一个字母大写
     * - 数字/符号：归为 "#"
     */
    fun getFirstLetter(text: String): String {
        if (text.isBlank()) return "#"

        val firstChar = text.trim().first()

        // 英文字母
        if (firstChar.isLetter() && firstChar.code < 128) {
            return firstChar.uppercaseChar().toString()
        }

        // 中文字符：用 pinyin4j 转拼音
        if (isChinese(firstChar)) {
            try {
                val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(firstChar, pinyinFormat)
                if (!pinyinArray.isNullOrEmpty()) {
                    return pinyinArray[0].first().uppercaseChar().toString()
                }
            } catch (e: BadHanyuPinyinOutputFormatCombination) {
                // 忽略，返回 #
            }
        }

        // 数字/符号
        return "#"
    }

    /**
     * 获取完整拼音（用于排序）。
     */
    fun getPinyin(text: String): String {
        if (text.isBlank()) return ""

        val sb = StringBuilder()
        for (char in text) {
            if (isChinese(char)) {
                try {
                    val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(char, pinyinFormat)
                    if (!pinyinArray.isNullOrEmpty()) {
                        sb.append(pinyinArray[0])
                    } else {
                        sb.append(char)
                    }
                } catch (e: BadHanyuPinyinOutputFormatCombination) {
                    sb.append(char)
                }
            } else {
                sb.append(char.uppercaseChar())
            }
        }
        return sb.toString()
    }

    /**
     * 判断是否为中文字符。
     */
    private fun isChinese(c: Char): Boolean {
        val ub = Character.UnicodeBlock.of(c)
        return ub === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                ub === Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                ub === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                ub === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                ub === Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
                ub === Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS ||
                ub === Character.UnicodeBlock.GENERAL_PUNCTUATION
    }

    /**
     * 按拼音首字母分组，并排序。
     * 返回：分组字母 -> 该组的角色列表（按拼音排序）
     * 顺序：A-Z，# 在最后
     */
    fun <T> groupByPinyin(
        items: List<T>,
        nameGetter: (T) -> String
    ): List<Pair<String, List<T>>> {
        if (items.isEmpty()) return emptyList()

        // 按拼音排序
        val sorted = items.sortedBy { getPinyin(nameGetter(it)) }

        // 按首字母分组
        val groups = sorted.groupBy { getFirstLetter(nameGetter(it)) }

        // 排序：A-Z 在前，# 在最后
        val letters = groups.keys.sortedWith { a, b ->
            when {
                a == "#" -> 1
                b == "#" -> -1
                else -> a.compareTo(b)
            }
        }

        return letters.map { it to (groups[it] ?: emptyList()) }
    }
}
