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

    val ALL_CATEGORIES = setOf(
        "급여/수입", "상여/보너스",
        "카페/간식", "외식/배달", "교통비", "생활/마트",
        "자녀교육-학원/과외", "자녀교육-입시컨설팅", "자녀용돈",
        "쇼핑/의류", "의료/병원", "여행/여가", "주거/보수",
        "차량/정비", "경조사/명절", "주거/관리비", "가구/가전",
        "통신/인터넷", "보험/금융", "헬스/건강",
        "반려동물/간식/장난감", "반려동물/병원/미용",
        "문화/공연", "OTT", "도서/교육", "저축/적금",
        "주식/코인", "미용/헤어"
    )

    private data class CategoryItem(val title: String, val range: IntRange)

    private val categoryItems: Map<String, List<CategoryItem>> = mapOf(
        "생활/마트" to listOf(
            CategoryItem("이마트 트레이더스 장보기", 120_000..240_000),
            CategoryItem("코스트코 장보기", 180_000..320_000),
            CategoryItem("동네 마트 장보기", 45_000..95_000),
            CategoryItem("다이소 잡화", 15_000..35_000),
            CategoryItem("온라인 식료품 주문", 60_000..140_000),
            CategoryItem("정육점 장보기", 35_000..80_000),
            CategoryItem("과일 전문점 구매", 25_000..65_000),
            CategoryItem("생활세제 구매", 20_000..55_000)
        ),
        "카페/간식" to listOf(
            CategoryItem("스타벅스 커피", 12_000..25_000), CategoryItem("투썸플레이스 디저트", 15_000..28_000),
            CategoryItem("카페 브런치", 18_000..35_000), CategoryItem("편의점 간식", 4_000..12_000),
            CategoryItem("베이커리 빵", 8_000..22_000), CategoryItem("아이스크림", 5_000..15_000),
            CategoryItem("버블티", 6_000..12_000), CategoryItem("회사 근처 커피", 4_000..8_000)
        ),
        "외식/배달" to listOf(
            CategoryItem("아웃백 가족 외식", 140_000..220_000), CategoryItem("양갈비 외식", 110_000..190_000),
            CategoryItem("굽네치킨 배달", 25_000..38_000), CategoryItem("중국집 배달", 30_000..55_000),
            CategoryItem("엽기떡볶이 배달", 15_000..35_000), CategoryItem("교촌치킨 배달",  25_000..38_000),
            CategoryItem("피자 배달", 28_000..45_000), CategoryItem("삼계탕 외식", 20_000..45_000)
        ),
        "교통비" to listOf(
            CategoryItem("가솔린 주유", 85_000..130_000), CategoryItem("대중교통", 30_000..70_000),
            CategoryItem("택시 이용", 12_000..35_000), CategoryItem("고속도로 통행료", 5_000..25_000),
            CategoryItem("렌터카 이용", 70_000..180_000)
        ),
        "도서/교육" to listOf(
            CategoryItem("수능 교재 구매", 45_000..110_000), CategoryItem("온라인 강의 수강", 30_000..90_000),
            CategoryItem("전자책 구매", 8_000..25_000), CategoryItem("자격증 교재 구매", 20_000..55_000),
            CategoryItem("독서 모임 회비", 10_000..30_000), CategoryItem("외국어 공부 앱 구독", 10_000..25_000)
        ),
        "쇼핑/의류" to listOf(
            CategoryItem("백화점 의류 구입", 150_000..350_000), CategoryItem("온라인 쇼핑몰 의류", 40_000..120_000),
            CategoryItem("운동화 구매", 80_000..180_000), CategoryItem("계절 의류 구매", 60_000..160_000),
            CategoryItem("가방 구매", 70_000..200_000), CategoryItem("잠옷 구매", 30_000..70_000),
            CategoryItem("액세서리 구매", 20_000..80_000), CategoryItem("아울렛 쇼핑", 100_000..250_000)
        ),
        "자녀용돈" to listOf(
            CategoryItem("자녀 용돈 지급", 100_000..150_000), CategoryItem("시험 격려 용돈", 30_000..70_000),
            CategoryItem("생일 축하 용돈", 50_000..100_000), CategoryItem("방학 용돈", 50_000..120_000)
        ),
        "의료/병원" to listOf(
            CategoryItem("내과 진료", 35_000..95_000), CategoryItem("치과 스케일링", 40_000..80_000),
            CategoryItem("약국 처방약", 8_000..30_000), CategoryItem("건강검진", 100_000..250_000),
            CategoryItem("안과 진료", 20_000..70_000), CategoryItem("피부과 진료", 40_000..120_000),
            CategoryItem("정형외과 진료", 30_000..100_000), CategoryItem("독감 예방접종", 25_000..45_000)
        ),
        "헬스/건강" to listOf(
            CategoryItem("피트니스 센터 등록", 80_000..150_000), CategoryItem("필라테스 수강", 120_000..250_000),
            CategoryItem("건강보조제 구매", 30_000..90_000), CategoryItem("요가 수강", 70_000..140_000),
            CategoryItem("수영장 회원권 구매", 50_000..100_000), CategoryItem("러닝용품 구매", 30_000..100_000), 
            CategoryItem("건강식품 구매", 25_000..80_000)
        ),
        "반려동물/간식/장난감" to listOf(
            CategoryItem("반려견 수제간식", 25_000..45_000), CategoryItem("애견 장난감", 15_000..30_000),
            CategoryItem("사료 구매", 45_000..90_000), CategoryItem("고양이 모래 구매", 20_000..45_000),
            CategoryItem("반려동물 옷 구매", 20_000..50_000), CategoryItem("반려동물 배변패드 구매", 20_000..45_000),
            CategoryItem("캣타워 용품", 30_000..80_000), CategoryItem("산책용품 구매", 15_000..40_000)
        ),
        "반려동물/병원/미용" to listOf(
            CategoryItem("반려견 미용", 45_000..85_000), CategoryItem("동물병원 정기검진", 35_000..120_000),
            CategoryItem("반려견 예방접종", 30_000..70_000), CategoryItem("반려견 피부 치료", 60_000..150_000),
            CategoryItem("반려견 귀 치료", 30_000..80_000), CategoryItem("반려견 호텔 위탁", 50_000..150_000)
        ),
        "문화/공연" to listOf(
            CategoryItem("영화 관람", 60_000..80_000), CategoryItem("뮤지컬 티켓", 120_000..280_000),
            CategoryItem("전시회 관람", 20_000..60_000), CategoryItem("콘서트 티켓", 90_000..220_000),
            CategoryItem("연극 티켓", 40_000..100_000), CategoryItem("미술관 관람", 15_000..40_000),
            CategoryItem("스포츠 경기 관람", 50_000..130_000),  CategoryItem("박물관 관람", 50_000..130_000)
        ),
        "OTT" to listOf(
            CategoryItem("넷플릭스 구독", 17_000..17_000), CategoryItem("유튜브 프리미엄", 14_900..14_900),
            CategoryItem("디즈니플러스 구독", 13_900..13_900), CategoryItem("티빙 구독", 13_900..13_900),
            CategoryItem("웨이브 구독", 13_900..13_900), CategoryItem("멜론 이용권", 10_900..10_900),
            CategoryItem("밀리의 서재 구독", 11_900..11_900), CategoryItem("쿠팡플레이 구독", 7_890..7_890)
        ),
        "미용/헤어" to listOf(
            CategoryItem("커트 및 펌", 50_000..150_000), CategoryItem("염색", 70_000..160_000),
            CategoryItem("네일 케어", 40_000..90_000), CategoryItem("화장품 구매", 30_000..120_000),
            CategoryItem("피부 관리", 70_000..180_000), CategoryItem("메이크업 제품", 25_000..90_000),
            CategoryItem("두피 관리", 50_000..120_000), CategoryItem("향수 구매", 60_000..150_000)
        ),
        "저축/적금" to listOf(
            CategoryItem("청약 저축", 100_000..200_000), CategoryItem("정기 적금", 500_000..1_000_000),
            CategoryItem("비상금 저축", 100_000..300_000), CategoryItem("자녀 적금", 200_000..500_000),
            CategoryItem("여행 적금", 100_000..300_000), CategoryItem("노후 준비 저축", 200_000..600_000),
            CategoryItem("투자 예비자금", 100_000..400_000), CategoryItem("자동이체 저축", 50_000..150_000)
        )
    )

    private val weightedDailyCategories = listOf(
        "생활/마트", "생활/마트", "생활/마트", "카페/간식", "카페/간식", "외식/배달", "교통비", 
        "도서/교육", "쇼핑/의류", "자녀용돈", "의료/병원", "헬스/건강", "반려동물/간식/장난감", 
        "반려동물/병원/미용", "문화/공연", "OTT", "미용/헤어"
    )

    private fun roundAmount(amount: Double, category: String): Double {
        return if (category == "자녀용돈") {
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
            fun randomDailyExpense(): ExpenseEntity {
                val category = weightedDailyCategories.random()
                val item = (categoryItems[category] ?: listOf(CategoryItem("생활용품 구매", 20_000..50_000))).random()
                return ExpenseEntity(null, item.title, item.range.random() * inflationRate, dateStr, "Expense", category)
            }

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
                        "차량/정비"
                    )
                year == 2023 && month == Calendar.MAY && dayOfMonth == 20 ->
                    ExpenseEntity(
                        null,
                        "거실 소파 및 식탁 교체",
                        4_500_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "가구/가전"
                    )
                year == 2024 && month == Calendar.NOVEMBER && dayOfMonth == 5 ->
                    ExpenseEntity(
                        null,
                        "삼성 비스포크 냉장고",
                        3_800_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "가구/가전"
                    )
                year == 2021 && month == Calendar.JUNE && dayOfMonth == 12 ->
                    ExpenseEntity(
                        null,
                        "반려견 슬개골 탈구 수술",
                        1_800_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "반려동물/병원/미용"
                    )
                year == 2022 && month == Calendar.DECEMBER && dayOfMonth == 28 ->
                    ExpenseEntity(
                        null,
                        "미국 주식 정기 매수",
                        2_500_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "주식/코인"
                    )
                year == 2025 && month == Calendar.JULY && dayOfMonth == 19 ->
                    ExpenseEntity(
                        null,
                        "자녀 대입 컨설팅비",
                        2_600_000.0 * inflationRate,
                        dateStr,
                        "Expense",
                        "자녀교육-입시컨설팅"
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
                    "자녀교육-학원/과외"
                )
                dayOfMonth == 10 -> ExpenseEntity(
                    null,
                    "아파트 관리비/공과금",
                    380_000.0 * inflationRate,
                    dateStr,
                    "Expense",
                    "주거/관리비"
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
                    "자녀교육-학원/과외"
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
                else -> randomDailyExpense()
            }

            val roundedEntity = entity.copy(amount = roundAmount(entity.amount, entity.category))
            expenseList.add(roundedEntity)
            val extraEntity = randomDailyExpense()
            expenseList.add(extraEntity.copy(amount = roundAmount(extraEntity.amount, extraEntity.category)))

            // 100건씩 배치 삽입 (성능과 UI 업데이트 체감 속도 향상)
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
