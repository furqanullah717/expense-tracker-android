package com.codewithfk.expensetracker.android.utils

import com.codewithfk.expensetracker.android.data.dao.ExpenseDao
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 페르소나: 아이 둘을 키우는 50대 초반 가장 (시드머니 1억 원 보유)
 * - 10개년 데이터 (3,652일): 10년 전(과장/차장) ~ 현재(팀장/부장)
 * - 10년 전 월급: 약 520만 원 -> 현재 월급: 약 940만 원 (10개년 승진/호봉 곡선 반영, 매월 25일)
 * - 1년에 3회 보너스 (10만~99만 원 사이)
 */
object FakeDataGenerator {

    private data class CategoryItem(val title: String, val range: IntRange)

    private val categoryItems: Map<String, List<CategoryItem>> = mapOf(
        "식비/장보기" to listOf(CategoryItem("이마트 트레이더스 장보기", 120_000..240_000), CategoryItem("코스트코 장보기", 180_000..320_000)),
        "카페/간식" to listOf(CategoryItem("스타벅스 커피", 12_000..25_000), CategoryItem("투썸플레이스 디저트", 15_000..28_000)),
        "외식/배달" to listOf(CategoryItem("아웃백 가족 외식", 140_000..220_000), CategoryItem("상도생고기 외식", 110_000..190_000)),
        "교통/차량" to listOf(CategoryItem("가솔린 주유", 85_000..130_000)),
        "생활/마트" to listOf(CategoryItem("다이소 잡화", 15_000..35_000)),
        "자녀교육/교재" to listOf(CategoryItem("수능 교재 구매", 45_000..110_000)),
        "쇼핑/의류" to listOf(CategoryItem("백화점 의류 구입", 150_000..350_000)),
        "자녀/용돈" to listOf(CategoryItem("자녀 용돈 지급", 100_000..150_000)),
        "의료/건강" to listOf(CategoryItem("내과 진료", 35_000..95_000)),
        "경조사/기타" to listOf(CategoryItem("경조사 부조금", 100_000..200_000))
    )

    private val weightedDailyCategories = listOf(
        "식비/장보기", "식비/장보기", "카페/간식", "외식/배달", "교통/차량", "생활/마트", "자녀교육/교재", "쇼핑/의류", "자녀/용돈", "의료/건강", "경조사/기타"
    )

    private fun roundAmount(amount: Double, category: String): Double {
        return if (category == "자녀/용돈") {
            // 자녀 용돈은 만 원 단위로 반올림
            (Math.round(amount / 10000.0) * 10000.0)
        } else {
            // 그 외 모든 금액은 최소 10원 단위로 반올림
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

            val entity = when {
                // A. 대형 이상치
                year == 2018 && month == Calendar.AUGUST && dayOfMonth == 2 ->
                    ExpenseEntity(
                        null,
                        "코타키나발루 가족 휴가",
                        3_200_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "여행/여가"
                    )
                year == 2020 && month == Calendar.OCTOBER && dayOfMonth == 14 ->
                    ExpenseEntity(
                        null,
                        "아파트 인테리어 공사",
                        5_500_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "주거/보수"
                    )
                year == 2022 && month == Calendar.AUGUST && dayOfMonth == 4 ->
                    ExpenseEntity(
                        null,
                        "서유럽 해외여행 경비",
                        6_800_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "여행/여가"
                    )
                year == 2024 && month == Calendar.MARCH && dayOfMonth == 14 ->
                    ExpenseEntity(
                        null,
                        "패밀리카 신차 교체",
                        7_200_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "차량/구입"
                    )
                year == 2025 && month == Calendar.JULY && dayOfMonth == 19 ->
                    ExpenseEntity(
                        null,
                        "자녀 대입 컨설팅비",
                        2_600_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "자녀교육/입시"
                    )
                month == Calendar.FEBRUARY && dayOfMonth == 7 ->
                    ExpenseEntity(
                        null,
                        "설 명절 부모님 용돈/선물",
                        ((800_000..1_200_000).random() * inflationRate),
                        dateStr,
                        "Expense",
                        "경조사/명절"
                    )
                month == Calendar.SEPTEMBER && dayOfMonth == 11 ->
                    ExpenseEntity(
                        null,
                        "추석 명절 귀성 경비",
                        ((700_000..1_100_000).random() * inflationRate),
                        dateStr,
                        "Expense",
                        "경조사/명절"
                    )

                // B. 수입
                dayOfMonth == 25 ->
                    ExpenseEntity(
                        null,
                        "월급 (급여 이체)",
                        (5_200_000.0 + (4_200_000.0 * progress)),
                        dateStr,
                        "Income",
                        "급여/수입"
                    )
                (month == Calendar.FEBRUARY && dayOfMonth == 9) || (month == Calendar.SEPTEMBER && dayOfMonth == 13) || (month == Calendar.DECEMBER && dayOfMonth == 23) ->
                    ExpenseEntity(
                        null,
                        "상여금 및 보너스",
                        ((150_000..950_000).random() * inflationRate),
                        dateStr,
                        "Income",
                        "상여/보너스"
                    )

                // C. 고정 지출
                dayOfMonth == 5 -> ExpenseEntity(
                    null,
                    "첫째 자녀 학원비",
                    (500_000..850_000).random() * inflationRate,
                    dateStr,
                    "Expense",
                    "자녀교육/학원"
                )
                dayOfMonth == 10 -> ExpenseEntity(
                    null,
                    "아파트 관리비/공과금",
                    380_000.0 * inflationRate,
                    dateStr,
                    "Expense",
                    "주거/공과금"
                )
                dayOfMonth == 15 -> ExpenseEntity(
                    null,
                    "가족 결합 통신비",
                    (140_000..210_000).random() * inflationRate,
                    dateStr,
                    "Expense",
                    "통신/인터넷"
                )
                dayOfMonth == 18 -> ExpenseEntity(
                    null,
                    "둘째 자녀 과외비",
                    (350_000..620_000).random() * inflationRate,
                    dateStr,
                    "Expense",
                    "자녀교육/과외"
                )
                dayOfMonth == 20 -> ExpenseEntity(
                    null,
                    "가족 통합보장보험료",
                    260_000.0 * inflationRate,
                    dateStr,
                    "Expense",
                    "보험/금융"
                )

                // D. 일상 지출
                else -> {
                    val cat = weightedDailyCategories.random()
                    val item = (categoryItems[cat] ?: listOf(CategoryItem("생활용품 구매", 20_000..50_000))).random()
                    ExpenseEntity(
                        null,
                        item.title,
                        item.range.random() * inflationRate,
                        dateStr,
                        "Expense",
                        cat
                    )
                }
            }

            val roundedEntity = entity.copy(amount = roundAmount(entity.amount, entity.category))
            expenseList.add(roundedEntity)

            // 100건씩 배치 삽입 (성능과 UI 업데이트 체감 속도 향상)
            if (expenseList.size >= 100) {
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