package com.smartspend.ai.utils

import com.smartspend.ai.data.dao.ExpenseDao
import com.smartspend.ai.data.model.ExpenseEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * ?˜ë¥´?Œë‚˜: ?„ì´ ?˜ì„ ?¤ìš°??50?€ ì´ˆë°˜ ê°€??(?œë“œë¨¸ë‹ˆ 1????ë³´ìœ )
 * - 10ê°œë…„ ?°ì´??(3,652??: 10????ê³¼ì¥/ì°¨ì¥) ~ ?„ì¬(?€??ë¶€??
 * - 10?????”ê¸‰: ??520ë§???-> ?„ì¬ ?”ê¸‰: ??940ë§???(10ê°œë…„ ?¹ì§„/?¸ë´‰ ê³¡ì„  ë°˜ì˜, ë§¤ì›” 25??
 * - 1?„ì— 3??ë³´ë„ˆ??(10ë§?99ë§????¬ì´)
 */
object FakeDataGenerator {

    val ALL_CATEGORIES = setOf(
        "ê¸‰ì—¬/?˜ì…", "?ì—¬/ë³´ë„ˆ??,
        "ì¹´í˜/ê°„ì‹", "?¸ì‹/ë°°ë‹¬", "êµí†µë¹?, "?í™œ/ë§ˆíŠ¸",
        "?ë?êµìœ¡-?™ì›/ê³¼ì™¸", "?ë?êµìœ¡-?…ì‹œì»¨ì„¤??, "?ë??©ëˆ",
        "?¼í•‘/?˜ë¥˜", "?˜ë£Œ/ë³‘ì›", "?¬í–‰/?¬ê?", "ì£¼ê±°/ë³´ìˆ˜",
        "ì°¨ëŸ‰/?•ë¹„", "ê²½ì¡°??ëª…ì ˆ", "ì£¼ê±°/ê´€ë¦¬ë¹„", "ê°€êµ?ê°€??,
        "?µì‹ /?¸í„°??, "ë³´í—˜/ê¸ˆìœµ", "?¬ìŠ¤/ê±´ê°•",
        "ë°˜ë ¤?™ë¬¼/ê°„ì‹/?¥ë‚œê°?, "ë°˜ë ¤?™ë¬¼/ë³‘ì›/ë¯¸ìš©",
        "ë¬¸í™”/ê³µì—°", "OTT", "?„ì„œ/êµìœ¡", "?€ì¶??ê¸ˆ",
        "ì£¼ì‹/ì½”ì¸", "ë¯¸ìš©/?¤ì–´"
    )

    private data class CategoryItem(val title: String, val range: IntRange)

    private val categoryItems: Map<String, List<CategoryItem>> = mapOf(
        "?í™œ/ë§ˆíŠ¸" to listOf(
            CategoryItem("?´ë§ˆ???¸ë ˆ?´ë”???¥ë³´ê¸?, 120_000..240_000),
            CategoryItem("ì½”ìŠ¤?¸ì½” ?¥ë³´ê¸?, 180_000..320_000),
            CategoryItem("?™ë„¤ ë§ˆíŠ¸ ?¥ë³´ê¸?, 45_000..95_000),
            CategoryItem("?¤ì´???¡í™”", 15_000..35_000),
            CategoryItem("?¨ë¼???ë£Œ??ì£¼ë¬¸", 60_000..140_000),
            CategoryItem("?•ìœ¡???¥ë³´ê¸?, 35_000..80_000),
            CategoryItem("ê³¼ì¼ ?„ë¬¸??êµ¬ë§¤", 25_000..65_000),
            CategoryItem("?í™œ?¸ì œ êµ¬ë§¤", 20_000..55_000)
        ),
        "ì¹´í˜/ê°„ì‹" to listOf(
            CategoryItem("?¤í?ë²…ìŠ¤ ì»¤í”¼", 12_000..25_000), CategoryItem("?¬ì¸?Œë ˆ?´ìŠ¤ ?”ì???, 15_000..28_000),
            CategoryItem("ì¹´í˜ ë¸ŒëŸ°ì¹?, 18_000..35_000), CategoryItem("?¸ì˜??ê°„ì‹", 4_000..12_000),
            CategoryItem("ë² ì´ì»¤ë¦¬ ë¹?, 8_000..22_000), CategoryItem("?„ì´?¤í¬ë¦?, 5_000..15_000),
            CategoryItem("ë²„ë¸”??, 6_000..12_000), CategoryItem("?Œì‚¬ ê·¼ì²˜ ì»¤í”¼", 4_000..8_000)
        ),
        "?¸ì‹/ë°°ë‹¬" to listOf(
            CategoryItem("?„ì›ƒë°?ê°€ì¡??¸ì‹", 140_000..220_000), CategoryItem("?‘ê°ˆë¹??¸ì‹", 110_000..190_000),
            CategoryItem("êµ½ë„¤ì¹˜í‚¨ ë°°ë‹¬", 25_000..38_000), CategoryItem("ì¤‘êµ­ì§?ë°°ë‹¬", 30_000..55_000),
            CategoryItem("?½ê¸°?¡ë³¶??ë°°ë‹¬", 15_000..35_000), CategoryItem("êµì´Œì¹˜í‚¨ ë°°ë‹¬",  25_000..38_000),
            CategoryItem("?¼ì ë°°ë‹¬", 28_000..45_000), CategoryItem("?¼ê³„???¸ì‹", 20_000..45_000)
        ),
        "êµí†µë¹? to listOf(
            CategoryItem("ê°€?”ë¦° ì£¼ìœ ", 85_000..130_000), CategoryItem("?€ì¤‘êµ??, 30_000..70_000),
            CategoryItem("?ì‹œ ?´ìš©", 12_000..35_000), CategoryItem("ê³ ì†?„ë¡œ ?µí–‰ë£?, 5_000..25_000),
            CategoryItem("?Œí„°ì¹??´ìš©", 70_000..180_000)
        ),
        "?„ì„œ/êµìœ¡" to listOf(
            CategoryItem("?˜ëŠ¥ êµì¬ êµ¬ë§¤", 45_000..110_000), CategoryItem("?¨ë¼??ê°•ì˜ ?˜ê°•", 30_000..90_000),
            CategoryItem("?„ìì±?êµ¬ë§¤", 8_000..25_000), CategoryItem("?ê²©ì¦?êµì¬ êµ¬ë§¤", 20_000..55_000),
            CategoryItem("?…ì„œ ëª¨ì„ ?Œë¹„", 10_000..30_000), CategoryItem("?¸êµ­??ê³µë? ??êµ¬ë…", 10_000..25_000)
        ),
        "?¼í•‘/?˜ë¥˜" to listOf(
            CategoryItem("ë°±í™”???˜ë¥˜ êµ¬ì…", 150_000..350_000), CategoryItem("?¨ë¼???¼í•‘ëª??˜ë¥˜", 40_000..120_000),
            CategoryItem("?´ë™??êµ¬ë§¤", 80_000..180_000), CategoryItem("ê³„ì ˆ ?˜ë¥˜ êµ¬ë§¤", 60_000..160_000),
            CategoryItem("ê°€ë°?êµ¬ë§¤", 70_000..200_000), CategoryItem("? ì˜· êµ¬ë§¤", 30_000..70_000),
            CategoryItem("?¡ì„¸?œë¦¬ êµ¬ë§¤", 20_000..80_000), CategoryItem("?„ìš¸???¼í•‘", 100_000..250_000)
        ),
        "?ë??©ëˆ" to listOf(
            CategoryItem("?ë? ?©ëˆ ì§€ê¸?, 100_000..150_000), CategoryItem("?œí—˜ ê²©ë ¤ ?©ëˆ", 30_000..70_000),
            CategoryItem("?ì¼ ì¶•í•˜ ?©ëˆ", 50_000..100_000), CategoryItem("ë°©í•™ ?©ëˆ", 50_000..120_000)
        ),
        "?˜ë£Œ/ë³‘ì›" to listOf(
            CategoryItem("?´ê³¼ ì§„ë£Œ", 35_000..95_000), CategoryItem("ì¹˜ê³¼ ?¤ì??¼ë§", 40_000..80_000),
            CategoryItem("?½êµ­ ì²˜ë°©??, 8_000..30_000), CategoryItem("ê±´ê°•ê²€ì§?, 100_000..250_000),
            CategoryItem("?ˆê³¼ ì§„ë£Œ", 20_000..70_000), CategoryItem("?¼ë?ê³?ì§„ë£Œ", 40_000..120_000),
            CategoryItem("?•í˜•?¸ê³¼ ì§„ë£Œ", 30_000..100_000), CategoryItem("?…ê° ?ˆë°©?‘ì¢…", 25_000..45_000)
        ),
        "?¬ìŠ¤/ê±´ê°•" to listOf(
            CategoryItem("?¼íŠ¸?ˆìŠ¤ ?¼í„° ?±ë¡", 80_000..150_000), CategoryItem("?„ë¼?ŒìŠ¤ ?˜ê°•", 120_000..250_000),
            CategoryItem("ê±´ê°•ë³´ì¡°??êµ¬ë§¤", 30_000..90_000), CategoryItem("?”ê? ?˜ê°•", 70_000..140_000),
            CategoryItem("?˜ì˜???Œì›ê¶?êµ¬ë§¤", 50_000..100_000), CategoryItem("?¬ë‹?©í’ˆ êµ¬ë§¤", 30_000..100_000), 
            CategoryItem("ê±´ê°•?í’ˆ êµ¬ë§¤", 25_000..80_000)
        ),
        "ë°˜ë ¤?™ë¬¼/ê°„ì‹/?¥ë‚œê°? to listOf(
            CategoryItem("ë°˜ë ¤ê²??˜ì œê°„ì‹", 25_000..45_000), CategoryItem("? ê²¬ ?¥ë‚œê°?, 15_000..30_000),
            CategoryItem("?¬ë£Œ êµ¬ë§¤", 45_000..90_000), CategoryItem("ê³ ì–‘??ëª¨ë˜ êµ¬ë§¤", 20_000..45_000),
            CategoryItem("ë°˜ë ¤?™ë¬¼ ??êµ¬ë§¤", 20_000..50_000), CategoryItem("ë°˜ë ¤?™ë¬¼ ë°°ë??¨ë“œ êµ¬ë§¤", 20_000..45_000),
            CategoryItem("ìº£í????©í’ˆ", 30_000..80_000), CategoryItem("?°ì±…?©í’ˆ êµ¬ë§¤", 15_000..40_000)
        ),
        "ë°˜ë ¤?™ë¬¼/ë³‘ì›/ë¯¸ìš©" to listOf(
            CategoryItem("ë°˜ë ¤ê²?ë¯¸ìš©", 45_000..85_000), CategoryItem("?™ë¬¼ë³‘ì› ?•ê¸°ê²€ì§?, 35_000..120_000),
            CategoryItem("ë°˜ë ¤ê²??ˆë°©?‘ì¢…", 30_000..70_000), CategoryItem("ë°˜ë ¤ê²??¼ë? ì¹˜ë£Œ", 60_000..150_000),
            CategoryItem("ë°˜ë ¤ê²?ê·€ ì¹˜ë£Œ", 30_000..80_000), CategoryItem("ë°˜ë ¤ê²??¸í…” ?„íƒ", 50_000..150_000)
        ),
        "ë¬¸í™”/ê³µì—°" to listOf(
            CategoryItem("?í™” ê´€??, 60_000..80_000), CategoryItem("ë®¤ì?ì»??°ì¼“", 120_000..280_000),
            CategoryItem("?„ì‹œ??ê´€??, 20_000..60_000), CategoryItem("ì½˜ì„œ???°ì¼“", 90_000..220_000),
            CategoryItem("?°ê·¹ ?°ì¼“", 40_000..100_000), CategoryItem("ë¯¸ìˆ ê´€ ê´€??, 15_000..40_000),
            CategoryItem("?¤í¬ì¸?ê²½ê¸° ê´€??, 50_000..130_000),  CategoryItem("ë°•ë¬¼ê´€ ê´€??, 50_000..130_000)
        ),
        "OTT" to listOf(
            CategoryItem("?·í”Œë¦?Š¤ êµ¬ë…", 17_000..17_000), CategoryItem("? íŠœë¸??„ë¦¬ë¯¸ì—„", 14_900..14_900),
            CategoryItem("?”ì¦ˆ?ˆí”Œ?¬ìŠ¤ êµ¬ë…", 13_900..13_900), CategoryItem("?°ë¹™ êµ¬ë…", 13_900..13_900),
            CategoryItem("?¨ì´ë¸?êµ¬ë…", 13_900..13_900), CategoryItem("ë©œë¡  ?´ìš©ê¶?, 10_900..10_900),
            CategoryItem("ë°€ë¦¬ì˜ ?œì¬ êµ¬ë…", 11_900..11_900), CategoryItem("ì¿ íŒ¡?Œë ˆ??êµ¬ë…", 7_890..7_890)
        ),
        "ë¯¸ìš©/?¤ì–´" to listOf(
            CategoryItem("ì»¤íŠ¸ ë°???, 50_000..150_000), CategoryItem("?¼ìƒ‰", 70_000..160_000),
            CategoryItem("?¤ì¼ ì¼€??, 40_000..90_000), CategoryItem("?”ì¥??êµ¬ë§¤", 30_000..120_000),
            CategoryItem("?¼ë? ê´€ë¦?, 70_000..180_000), CategoryItem("ë©”ì´?¬ì—… ?œí’ˆ", 25_000..90_000),
            CategoryItem("?í”¼ ê´€ë¦?, 50_000..120_000), CategoryItem("?¥ìˆ˜ êµ¬ë§¤", 60_000..150_000)
        ),
        "?€ì¶??ê¸ˆ" to listOf(
            CategoryItem("ì²?•½ ?€ì¶?, 100_000..200_000), CategoryItem("?•ê¸° ?ê¸ˆ", 500_000..1_000_000),
            CategoryItem("ë¹„ìƒê¸??€ì¶?, 100_000..300_000), CategoryItem("?ë? ?ê¸ˆ", 200_000..500_000),
            CategoryItem("?¬í–‰ ?ê¸ˆ", 100_000..300_000), CategoryItem("?¸í›„ ì¤€ë¹??€ì¶?, 200_000..600_000),
            CategoryItem("?¬ì ?ˆë¹„?ê¸ˆ", 100_000..400_000), CategoryItem("?ë™?´ì²´ ?€ì¶?, 50_000..150_000)
        )
    )

    private val weightedDailyCategories = listOf(
        "?í™œ/ë§ˆíŠ¸", "?í™œ/ë§ˆíŠ¸", "?í™œ/ë§ˆíŠ¸", "ì¹´í˜/ê°„ì‹", "ì¹´í˜/ê°„ì‹", "?¸ì‹/ë°°ë‹¬", "êµí†µë¹?, 
        "?„ì„œ/êµìœ¡", "?¼í•‘/?˜ë¥˜", "?ë??©ëˆ", "?˜ë£Œ/ë³‘ì›", "?¬ìŠ¤/ê±´ê°•", "ë°˜ë ¤?™ë¬¼/ê°„ì‹/?¥ë‚œê°?, 
        "ë°˜ë ¤?™ë¬¼/ë³‘ì›/ë¯¸ìš©", "ë¬¸í™”/ê³µì—°", "OTT", "ë¯¸ìš©/?¤ì–´"
    )

    private fun roundAmount(amount: Double, category: String): Double {
        return if (category == "?ë??©ëˆ") {
            // ?ë? ?©ëˆ?€ ë§????¨ìœ„ë¡?ë°˜ì˜¬ë¦?
            (Math.round(amount / 10000.0) * 10000.0)
        } else {
            // ê·???ëª¨ë“  ê¸ˆì•¡?€ ìµœì†Œ 10???¨ìœ„ë¡?ë°˜ì˜¬ë¦?
            (Math.round(amount / 10.0) * 10.0)
        }
    }

    suspend fun generateFakeData(dao: ExpenseDao) {
        dao.deleteAllExpenses()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val calendar = Calendar.getInstance().apply { add(Calendar.YEAR, -10) }
        val endCalendar = Calendar.getInstance()

        val totalDays = 3652
        var currentDayIndex = 0
        val expenseList = mutableListOf<ExpenseEntity>()

        while (!calendar.after(endCalendar)) {
            val progress = (currentDayIndex.toDouble() / totalDays.toDouble()).coerceIn(0.0, 1.0)
            val inflationRate = 0.73 + (0.27  * progress)
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val month = calendar.get(Calendar.MONTH)
            val year = calendar.get(Calendar.YEAR)
            val dateStr = dateFormat.format(calendar.time)
            fun randomDailyExpense(): ExpenseEntity {
                val category = weightedDailyCategories.random()
                val item = (categoryItems[category] ?: listOf(CategoryItem("?í™œ?©í’ˆ êµ¬ë§¤", 20_000..50_000))).random()
                return ExpenseEntity(null, item.title, item.range.random() * inflationRate, dateStr, "Expense", category)
            }

            val entity = when {
                // A. ?€???´ìƒì¹?
                year == 2018 && month == Calendar.AUGUST && dayOfMonth == 2 ->
                    ExpenseEntity(
                        null,
                        "ì½”í??¤ë‚˜ë°œë£¨ ê°€ì¡??´ê?",
                        3_200_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "?¬í–‰/?¬ê?"
                    )
                year == 2020 && month == Calendar.OCTOBER && dayOfMonth == 14 ->
                    ExpenseEntity(
                        null,
                        "?„íŒŒ???¸í…Œë¦¬ì–´ ê³µì‚¬",
                        5_500_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "ì£¼ê±°/ë³´ìˆ˜"
                    )
                year == 2022 && month == Calendar.AUGUST && dayOfMonth == 4 ->
                    ExpenseEntity(
                        null,
                        "?œìœ ???´ì™¸?¬í–‰ ê²½ë¹„",
                        6_800_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "?¬í–‰/?¬ê?"
                    )
                year == 2024 && month == Calendar.MARCH && dayOfMonth == 14 ->
                    ExpenseEntity(
                        null,
                        "?¨ë?ë¦¬ì¹´ ? ì°¨ êµì²´",
                        7_200_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "ì°¨ëŸ‰/?•ë¹„"
                    )
                year == 2023 && month == Calendar.MAY && dayOfMonth == 20 ->
                    ExpenseEntity(
                        null,
                        "ê±°ì‹¤ ?ŒíŒŒ ë°??íƒ êµì²´",
                        4_500_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "ê°€êµ?ê°€??
                    )
                year == 2024 && month == Calendar.NOVEMBER && dayOfMonth == 5 ->
                    ExpenseEntity(
                        null,
                        "?¼ì„± ë¹„ìŠ¤?¬í¬ ?‰ì¥ê³?,
                        3_800_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "ê°€êµ?ê°€??
                    )
                year == 2021 && month == Calendar.JUNE && dayOfMonth == 12 ->
                    ExpenseEntity(
                        null,
                        "ë°˜ë ¤ê²??¬ê°œê³??ˆêµ¬ ?˜ìˆ ",
                        1_800_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "ë°˜ë ¤?™ë¬¼/ë³‘ì›/ë¯¸ìš©"
                    )
                year == 2022 && month == Calendar.DECEMBER && dayOfMonth == 28 ->
                    ExpenseEntity(
                        null,
                        "ë¯¸êµ­ ì£¼ì‹ ?•ê¸° ë§¤ìˆ˜",
                        2_500_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "ì£¼ì‹/ì½”ì¸"
                    )
                year == 2025 && month == Calendar.JULY && dayOfMonth == 19 ->
                    ExpenseEntity(
                        null,
                        "?ë? ?€??ì»¨ì„¤?…ë¹„",
                        2_600_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "?ë?êµìœ¡-?…ì‹œì»¨ì„¤??
                    )
                month == Calendar.FEBRUARY && dayOfMonth == 7 ->
                    ExpenseEntity(
                        null,
                        "??ëª…ì ˆ ë¶€ëª¨ë‹˜ ?©ëˆ/? ë¬¼",
                        ((800_000..1_200_000).random() * inflationRate),
                        dateStr,
                        "Expense",
                        "ê²½ì¡°??ëª…ì ˆ"
                    )
                month == Calendar.SEPTEMBER && dayOfMonth == 11 ->
                    ExpenseEntity(
                        null,
                        "ì¶”ì„ ëª…ì ˆ ê·€??ê²½ë¹„",
                        ((700_000..1_100_000).random() * inflationRate),
                        dateStr,
                        "Expense",
                        "ê²½ì¡°??ëª…ì ˆ"
                    )

                // B. ?˜ì…
                dayOfMonth == 25 ->
                    ExpenseEntity(
                        null,
                        "?”ê¸‰ (ê¸‰ì—¬ ?´ì²´)",
                        (5_200_000.0 + (4_200_000.0 * progress)),
                        dateStr,
                        "Income",
                        "ê¸‰ì—¬/?˜ì…"
                    )
                (month == Calendar.FEBRUARY && dayOfMonth == 9) || (month == Calendar.SEPTEMBER && dayOfMonth == 13) || (month == Calendar.DECEMBER && dayOfMonth == 23) ->
                    ExpenseEntity(
                        null,
                        "?ì—¬ê¸?ë°?ë³´ë„ˆ??,
                        ((150_000..950_000).random() * inflationRate),
                        dateStr,
                        "Income",
                        "?ì—¬/ë³´ë„ˆ??
                    )

                // C. ê³ ì • ì§€ì¶?
                dayOfMonth == 5 -> ExpenseEntity(
                    null,
                    "ì²«ì§¸ ?ë? ?™ì›ë¹?,
                    (500_000..850_000).random() * inflationRate,
                    dateStr,
                    "Expense",
                    "?ë?êµìœ¡-?™ì›/ê³¼ì™¸"
                )
                dayOfMonth == 10 -> ExpenseEntity(
                    null,
                    "?„íŒŒ??ê´€ë¦¬ë¹„/ê³µê³¼ê¸?,
                    380_000.0 * inflationRate,
                    dateStr,
                    "Expense",
                    "ì£¼ê±°/ê´€ë¦¬ë¹„"
                )
                dayOfMonth == 15 -> ExpenseEntity(
                    null,
                    "ê°€ì¡?ê²°í•© ?µì‹ ë¹?,
                    (140_000..210_000).random() * inflationRate,
                    dateStr,
                    "Expense",
                    "?µì‹ /?¸í„°??
                )
                dayOfMonth == 18 -> ExpenseEntity(
                    null,
                    "?˜ì§¸ ?ë? ê³¼ì™¸ë¹?,
                    (350_000..620_000).random() * inflationRate,
                    dateStr,
                    "Expense",
                    "?ë?êµìœ¡-?™ì›/ê³¼ì™¸"
                )
                dayOfMonth == 20 -> ExpenseEntity(
                    null,
                    "ê°€ì¡??µí•©ë³´ì¥ë³´í—˜ë£?,
                    260_000.0 * inflationRate,
                    dateStr,
                    "Expense",
                    "ë³´í—˜/ê¸ˆìœµ"
                )

                // D. ?¼ìƒ ì§€ì¶?
                else -> randomDailyExpense()
            }

            val roundedEntity = entity.copy(amount = roundAmount(entity.amount, entity.category))
            expenseList.add(roundedEntity)
            val extraEntity = randomDailyExpense()
            expenseList.add(extraEntity.copy(amount = roundAmount(extraEntity.amount, extraEntity.category)))

            // 100ê±´ì”© ë°°ì¹˜ ?½ì… (?±ëŠ¥ê³?UI ?…ë°?´íŠ¸ ì²´ê° ?ë„ ?¥ìƒ)
            if (expenseList.size >= 300) {
                dao.insertAll(expenseList)
                expenseList.clear()
            }

            calendar.add(Calendar.DAY_OF_YEAR, 1)
            currentDayIndex++
        }
        if (expenseList.isNotEmpty()) {
            dao.insertAll(expenseList)
        }
    }
}
