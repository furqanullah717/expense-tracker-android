package com.codewithfk.expensetracker.android.data

import com.codewithfk.expensetracker.android.data.dao.ExpenseDao
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 페르소나: 아이 둘을 키우는 50대 초반 가장 (시드머니 2억 원 보유)
 * - title(거래명/상호명)과 category(분류)를 명확히 분리
 * - 2년 전 월급: 약 780만 원 -> 현재 월급: 약 940만 원 (수입 변동성 작음, 매월 25일)
 * - 1년에 3회 보너스 (10만~99만 원 사이)
 * - 고정비 (특정일에 월 1회만 결제하여 중복/비정상 누적 방지):
 *     5일: 대치동 학원비 (첫째)
 *    10일: 아파트 관리비/공과금
 *    15일: SKT 4인 가족 결합 통신비 (월 1회 고정)
 *    18일: 과외/예체능 학원비 (둘째)
 *    20일: 가족 통합보장보험료
 * - 의도적인 대형 지출(튀는 값/이상치): 가족 해외여행(450만), 입시 컨설팅(260만), 차량 정비(185만), 차량 교체 계약금(720만), 명절 부모님 용돈(120만)
 * - 카테고리별 할당량 기반으로 하루 1건씩 30일 데이터 생성 (1일 1건 엄격 준수)
 */
object FakeDataGenerator {

    private data class CategoryItem(val title: String, val range: IntRange)

    // -------------------------------------------------------------
    // 1. 카테고리별 아이템 목록 (상호명 + 금액 범위)
    // -------------------------------------------------------------
    private val categoryItems: Map<String, List<CategoryItem>> = mapOf(
        "식비/장보기" to listOf(
            CategoryItem("이마트 트레이더스 주말 장보기", 120_000..240_000),
            CategoryItem("코스트코 주말 대량 장보기", 180_000..320_000),
            CategoryItem("쿠팡 로켓프레시 신선식품", 45_000..95_000),
            CategoryItem("홈플러스 장보기", 80_000..180_000)
        ),
        "카페/간식" to listOf(
            CategoryItem("스타벅스 DT점 커피", 12_000..25_000),
            CategoryItem("투썸플레이스 디저트 및 커피", 15_000..28_000),
            CategoryItem("던킨도너츠 도넛 및 커피", 12_000..25_000),
            CategoryItem("로컬 카페 아메리카노", 8_000..18_000)
        ),
        "외식/배달" to listOf(
            CategoryItem("아웃백 스테이크하우스 가족 외식", 140_000..220_000),
            CategoryItem("상도생고기(정육식당) 가족 외식", 110_000..190_000),
            CategoryItem("쿠우쿠우 스시 뷔페 가족 외식", 95_000..150_000),
            CategoryItem("배달의민족 주말 가족 저녁", 45_000..85_000)
        ),
        "교통/차량" to listOf(
            CategoryItem("GS칼텍스 가솔린 주유", 85_000..130_000),
            CategoryItem("SK에너지 가솔린 주유", 80_000..125_000),
            CategoryItem("현대오일뱅크 셀프 주유", 75_000..120_000)
        ),
        "생활/마트" to listOf(
            CategoryItem("다이소 생활잡화 구매", 15_000..35_000),
            CategoryItem("쿠팡 생활용품 로켓배송", 25_000..65_000),
            CategoryItem("대형마트 생활용품 구매", 30_000..70_000)
        ),
        "자녀교육/교재" to listOf(
            CategoryItem("교보문고 수능 기출 교재", 45_000..110_000),
            CategoryItem("메가스터디 수능 인강 결제", 180_000..320_000),
            CategoryItem("수학 문제집/모의고사 구매", 25_000..55_000)
        ),
        "쇼핑/의류" to listOf(
            CategoryItem("현대백화점 계절의류 구입", 150_000..350_000),
            CategoryItem("온라인 쇼핑몰 의류 구매", 80_000..200_000),
            CategoryItem("프리미엄 아울렛 의류 구매", 120_000..280_000)
        ),
        "자녀/용돈" to listOf(
            CategoryItem("자녀 월간 용돈 지급", 100_000..150_000)
        ),
        "의료/건강" to listOf(
            CategoryItem("서울아산병원 내과 진료/검사", 35_000..95_000),
            CategoryItem("온누리약국 의약품/영양제 구매", 40_000..85_000)
        ),
        "경조사/기타" to listOf(
            CategoryItem("직장 동료 경조사 부조금", 100_000..200_000),
            CategoryItem("동창회 모임 회비", 50_000..100_000)
        )
    )

    // -------------------------------------------------------------
    // 2. 카테고리별 일상 지출 생성용 풀 (가중치 리스트)
    // -------------------------------------------------------------
    private val weightedDailyCategories = listOf(
        "식비/장보기", "식비/장보기", "식비/장보기", "식비/장보기", "식비/장보기", "식비/장보기", "식비/장보기",
        "카페/간식", "카페/간식", "카페/간식", "카페/간식", "카페/간식",
        "외식/배달", "외식/배달", "외식/배달", "외식/배달",
        "교통/차량", "교통/차량", "교통/차량", "교통/차량",
        "생활/마트", "생활/마트", "생활/마트",
        "자녀교육/교재", "자녀교육/교재",
        "쇼핑/의류", "쇼핑/의류",
        "자녀/용돈",
        "의료/건강",
        "경조사/기타"
    )

    // -------------------------------------------------------------
    // 3. 본격 데이터 생성 (2년 = 730일간 하루에 정확히 1건씩 생성)
    // -------------------------------------------------------------
    suspend fun generateFakeData(dao: ExpenseDao) {
        dao.deleteAllExpenses()

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val calendar = Calendar.getInstance().apply {
            add(Calendar.YEAR, -2)
        }
        val endCalendar = Calendar.getInstance()

        val totalDays = 730
        var currentDayIndex = 0

        while (!calendar.after(endCalendar)) {
            val progress = (currentDayIndex.toDouble() / totalDays.toDouble()).coerceIn(0.0, 1.0)

            // 2년간 약 20%의 물가 상승률 곡선 반영 (1.0 -> 1.20)
            val inflationRate = 1.0 + (0.20 * progress)

            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val month = calendar.get(Calendar.MONTH) // 0 = Jan, 1 = Feb, ..., 11 = Dec

            val entity = when {
                // ---------------------------------------------------------
                // A. 의도적인 대형 튀는 값 (Outliers)
                // ---------------------------------------------------------
                // 1년 차 여름휴가 (8월 2일): 가족 해외여행 (450만 원)
                month == Calendar.AUGUST && dayOfMonth == 2 -> {
                    ExpenseEntity(
                        id = null,
                        title = "베트남 다낭 4인 가족 해외여행 경비",
                        category = "여행/여가",
                        amount = 4_500_000.0,
                        date = dateFormat.format(calendar.time),
                        type = "Expense"
                    )
                }

                // 2년 차 대입 수시 컨설팅 (7월 19일): 입시 컨설팅 (260만 원)
                month == Calendar.JULY && dayOfMonth == 19 -> {
                    ExpenseEntity(
                        id = null,
                        title = "대치동 자녀 대입 수시 입시 컨설팅비",
                        category = "자녀교육/입시",
                        amount = 2_600_000.0,
                        date = dateFormat.format(calendar.time),
                        type = "Expense"
                    )
                }

                // 1년 차 가을 (11월 4일): 차량 대형 정비 (185만 원)
                month == Calendar.NOVEMBER && dayOfMonth == 4 -> {
                    ExpenseEntity(
                        id = null,
                        title = "패밀리카 타이어 4본 교체 및 미션오일 대형정비",
                        category = "차량/수리",
                        amount = 1_850_000.0,
                        date = dateFormat.format(calendar.time),
                        type = "Expense"
                    )
                }

                // 2년 차 봄 (3월 14일): 차량 교체 계약금 (720만 원)
                month == Calendar.MARCH && dayOfMonth == 14 -> {
                    ExpenseEntity(
                        id = null,
                        title = "패밀리카 신차 교체 계약금 및 취등록세",
                        category = "차량/구입",
                        amount = 7_200_000.0,
                        date = dateFormat.format(calendar.time),
                        type = "Expense"
                    )
                }

                // 설 명절 부모님 용돈 및 선물 (2월 7일): 120만 원
                month == Calendar.FEBRUARY && dayOfMonth == 7 -> {
                    ExpenseEntity(
                        id = null,
                        title = "설 명절 양가 부모님 용돈 및 한우세트",
                        category = "경조사/명절",
                        amount = 1_200_000.0,
                        date = dateFormat.format(calendar.time),
                        type = "Expense"
                    )
                }

                // 추석 명절 귀성 경비 및 선물 (9월 11일): 110만 원
                month == Calendar.SEPTEMBER && dayOfMonth == 11 -> {
                    ExpenseEntity(
                        id = null,
                        title = "추석 명절 귀성 경비 및 명절 선물",
                        category = "경조사/명절",
                        amount = 1_100_000.0,
                        date = dateFormat.format(calendar.time),
                        type = "Expense"
                    )
                }

                // ---------------------------------------------------------
                // B. 수입 (매월 25일 월급 - 변동성 작음, 1년 3회 보너스)
                // ---------------------------------------------------------
                // 매월 25일: 월급 (2년 전 780만 -> 현재 940만, 변동폭 ±3만 원 이내)
                dayOfMonth == 25 -> {
                    val baseSalary = 7_800_000.0 + (1_600_000.0 * progress)
                    val variation = (-30_000..30_000).random()
                    ExpenseEntity(
                        id = null,
                        title = "월급 (급여 이체)",
                        category = "급여/수입",
                        amount = ((baseSalary + variation) / 10_000).toLong() * 10_000.0,
                        date = dateFormat.format(calendar.time),
                        type = "Income"
                    )
                }

                // 1년에 3회 보너스 (설날: 2월 9일, 추석: 9월 13일, 연말 성과급: 12월 23일) - 10만~99만 원 사이
                (month == Calendar.FEBRUARY && dayOfMonth == 9) ||
                (month == Calendar.SEPTEMBER && dayOfMonth == 13) ||
                (month == Calendar.DECEMBER && dayOfMonth == 23) -> {
                    val bonusAmount = (150_000..950_000).random().toDouble()
                    ExpenseEntity(
                        id = null,
                        title = "명절 상여금 및 성과 보너스",
                        category = "상여/보너스",
                        amount = (bonusAmount / 10_000).toLong() * 10_000.0,
                        date = dateFormat.format(calendar.time),
                        type = "Income"
                    )
                }

                // ---------------------------------------------------------
                // C. 고정 지출 (매월 특정일에 딱 1회만 결제)
                // ---------------------------------------------------------
                // 매월 5일: 첫째 자녀 학원비
                dayOfMonth == 5 -> {
                    val academyFee = (700_000..850_000).random() * inflationRate
                    ExpenseEntity(
                        id = null,
                        title = "대치동 수학/영어 종합학원비 (첫째)",
                        category = "자녀교육/학원",
                        amount = (academyFee / 1000).toLong() * 1000.0,
                        date = dateFormat.format(calendar.time),
                        type = "Expense"
                    )
                }

                // 매월 10일: 아파트 관리비 및 공과금
                dayOfMonth == 10 -> {
                    val rent = 480_000.0 * inflationRate
                    ExpenseEntity(
                        id = null,
                        title = "아파트 관리비 및 전기/수도 공과금",
                        category = "주거/공과금",
                        amount = (rent / 1000).toLong() * 1000.0,
                        date = dateFormat.format(calendar.time),
                        type = "Expense"
                    )
                }

                // 매월 15일: 4인 가족 통신비 (월 1회 고정)
                dayOfMonth == 15 -> {
                    val phoneBill = (175_000..210_000).random().toDouble()
                    ExpenseEntity(
                        id = null,
                        title = "SKT 4인 가족 결합 통신비+인터넷",
                        category = "통신/인터넷",
                        amount = (phoneBill / 1000).toLong() * 1000.0,
                        date = dateFormat.format(calendar.time),
                        type = "Expense"
                    )
                }

                // 매월 18일: 둘째 자녀 과외비
                dayOfMonth == 18 -> {
                    val tutoring = (480_000..620_000).random() * inflationRate
                    ExpenseEntity(
                        id = null,
                        title = "둘째 자녀 영어/예체능 과외비",
                        category = "자녀교육/과외",
                        amount = (tutoring / 1000).toLong() * 1000.0,
                        date = dateFormat.format(calendar.time),
                        type = "Expense"
                    )
                }

                // 매월 20일: 가족 통합보험료
                dayOfMonth == 20 -> {
                    ExpenseEntity(
                        id = null,
                        title = "삼성화재 4인 가족 통합보장보험",
                        category = "보험/금융",
                        amount = 320_000.0,
                        date = dateFormat.format(calendar.time),
                        type = "Expense"
                    )
                }

                // ---------------------------------------------------------
                // D. 그 외 일상 변동 지출 (카테고리 할당량 기반 무작위 1건)
                // ---------------------------------------------------------
                else -> {
                    val category = weightedDailyCategories.random()
                    val items = categoryItems[category] ?: listOf(CategoryItem("생활용품 구매", 20_000..50_000))
                    val selectedItem = items.random()
                    val rawAmount = selectedItem.range.random() * inflationRate
                    val finalAmount = if (category == "자녀/용돈") {
                        (rawAmount / 10_000).toLong() * 10_000.0
                    } else {
                        (rawAmount / 100).toLong() * 100.0
                    }

                    ExpenseEntity(
                        id = null,
                        title = selectedItem.title,
                        category = category,
                        amount = finalAmount,
                        date = dateFormat.format(calendar.time),
                        type = "Expense"
                    )
                }
            }

            dao.insertExpense(entity)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            currentDayIndex++
        }
    }
}
