package com.codewithfk.expensetracker.android.ai.core.prompt

object AiPromptTemplates {

    fun getParseExpensePrompt(
        isIncome: Boolean,
        categories: List<String>,
        referenceCalendar: String,
        todayDate: String,
        input: String,
    ): String {
        val typeText = if (isIncome) "Income" else "Expense"
        return """
            Extract transaction details from the Korean text and return as a SINGLE JSON object.
            
            ## CRITICAL RULES
            1. Return ONLY a valid JSON object. 
            2. NEVER wrap the response in a list or array (No square brackets at the start/end).
            3. Do NOT include markdown code blocks (No ```json).
            
            ## Reference Calendar (Past 14 Days)
            $referenceCalendar
            
            ## Instructions
            1. Type: $typeText
            2. Item Title: Extract a specific name for the transaction (e.g., "감자튀김", "한국사 강의").
            3. Category: Select the MOST appropriate from [${categories.joinToString(", ")}]
            4. Date Selection (CRITICAL):
               - NEVER PROVIDE A FUTURE DATE.
               - If the input says "24일" and today is "23일", you MUST return the 24th of the PREVIOUS MONTH.
               - If a weekday is mentioned, pick the most recent date for that weekday in the past.
               - If no date is mentioned, use today's date ($todayDate).
            5. Amount: Extract as a numeric value.
            
            ## JSON structure
            {
                "title": "Specific Item Name",
                "category": "One of the categories from the list",
                "amount": 0.0,
                "date": "YYYY-MM-DD",
                "type": "$typeText"
            }
            
            Text: "$input"
        """.trimIndent()
    }



    fun getAnalyzeSpendingPrompt(
        periodText: String,
        monthlyText: String,
        categoryText: String,
        top10Text: String,
        recentHistoryText: String,
        comparisonText: String,
        yearlyTrendText: String
    ): String {
        return """
 Analyze the following formatted expense data for the period ($periodText) and provide a report in JSON format.
    Be concise and provide specific financial advice in Korean.
    
    [CRITICAL RULES]
    1. Focus on Spending Pace: In "Section 5", compare the "이번 달 현재까지 총 지출" with the "지난 달 동일 기간 지출". Warn the user ONLY if current spending exceeds last month's same-period spending by more than 5%. If the difference is within a ±5% range, do not issue any warnings.
    2. Yearly Context: If "Section 6" is provided, compare the yearly "월 평균 지출" (Monthly Averages) to identify long-term inflation or lifestyle changes. Mention if current spending is significantly higher or lower than the historical monthly average.
    3. Grounding: You MUST base your analysis EXACTLY on the item names provided in "Section 3. High-Value Spending".
    4. Focus on Trends: Focus purely on the categories and item names.
    5. Output Format: Return ONLY a valid JSON object without markdown formatting.
    
            ## 1. Monthly Summary
            $monthlyText
            
            ## 2. Category Distribution
            $categoryText
            
            ## 3. High-Value Spending (Top 10)
            $top10Text
            
            ## 4. Recent 30 Days Detailed History
            $recentHistoryText
            
            ## 5. Month-over-Month Comparison (Run-rate)
            $comparisonText
            
            ${if (yearlyTrendText.isNotBlank()) "## 6. Yearly Trend Analysis (Monthly Averages)\n$yearlyTrendText" else ""}
            
            JSON structure:
            {
                "summary": "Short summary of overall spending in Korean",
                "insights": ["insight 1", "insight 2", "insight 3", "insight 4"], // max 5
                "savingTips": ["tip 1", "tip 2"],  // max 3
                "period": "Period of analysis (e.g., $periodText)"
            }
        """.trimIndent()
    }

    fun getAnalyzeDetailSpendingPrompt(
        periodText: String,
        monthlyText: String,
        categoryText: String,
        top10Text: String,
        recentHistoryText: String,
        comparisonText: String,
        yearlyTrendText: String
    ): String {
        return """
    Act as a top-tier Wealth Manager and Financial Data Analyst. 
    Analyze the following expense data for the period ($periodText) and provide a comprehensive, expert-level financial report in JSON format.
    Provide highly specific, data-driven financial advice in professional Korean.
    
    [CRITICAL RULES]
    1. Persona: Maintain an objective, sharp, and professional tone. Use financial concepts like 'Run-rate (소비 속도)', 'Fixed vs Variable costs (고정비/변동비)', and 'Lifestyle Creep (라이프스타일 팽창)'.
    2. Deep Dive Analysis: Do not just list what the user spent. Analyze the *why* and the *impact*. 
       - In "Section 5", meticulously evaluate the spending pace (Run-rate) compared to the same period last month.
       - If "Section 6" is present, analyze the long-term Yearly Trend to identify structural inflation in the user's spending habits or positive saving patterns.
    3. Grounding: You MUST base your analysis EXACTLY on the categories and item names provided in the data sections.
    4. Actionable Strategy: Do not give generic advice like "Save money on food." Give precise actions like "Dining out is 30% higher than the monthly average; cap next week's food budget at 50,000 KRW."
    5. Output Format: Return ONLY a valid JSON object without markdown formatting (no ```json or ```).

    ## 1. Monthly Summary
    $monthlyText

    ## 2. Category Distribution
    $categoryText

    ## 3. High-Value Spending (Top 10)
    $top10Text

    ## 4. Recent 30 Days Detailed History
    $recentHistoryText

    ## 5. Month-over-Month Comparison (Run-rate)
    $comparisonText

    ${if (yearlyTrendText.isNotBlank()) "## 6. Yearly Trend Analysis (Monthly Averages)\n$yearlyTrendText" else ""}
    
    JSON structure strictly as follows:
    {
        "executiveSummary": "A comprehensive 3-4 sentence professional summary of the current financial state.",
        "financialHealthScore": 85, // 0 to 100 based on spending habits
        "keyInsights": [
            {
                "topic": "Core category or issue (e.g., 식비 과다 지출)",
                "finding": "Detailed data-backed finding.",
                "implication": "What this means for their overall wealth or monthly cash flow."
            }
        ], // Provide 3 to 5 deep insights
        "actionableStrategies": [
            {
                "urgency": "HIGH", // HIGH, MEDIUM, LOW
                "advice": "Specific, practical, and precise action item.",
                "expectedImpact": "Expected result if followed (e.g., 월 15만원 절약 가능)"
            }
        ], // Provide 2 to 4 strategies
        "period": "$periodText"
    }
""".trimIndent()
    }
/*
    fun getChatPrompt(
        historyText: String,
        todayDate: String
    ): String {
        return """
            You are a helpful and intelligent financial assistant for an Expense Tracker app.
            Your goal is to answer the user's questions about their spending habits, provide financial advice, or help them manage their data.

             ## Instructions
            1. Use the provided history to answer specific expense questions (e.g., "How much did I spend on coffee?"). Never invent financial data that is not in the history.
            2. Answer every user question, including questions unrelated to expenses or this app. For unrelated questions, give a helpful, friendly general answer instead of refusing or saying that you can only discuss finances.
            3. Be professional, polite, and encouraging in Korean unless the user clearly requests another language.
            4. If you don't have enough information for a precise answer, say so briefly and ask for clarification when useful.
            5. If the user asks to "delete" or "modify" something, explain that as an AI, you can't directly edit the database, but you can guide them or classify their intent for the system to handle later (For now, just provide a polite response).
            6. Keep answers concise, natural, and relevant to the user's latest question.
            
            ## Today's Date
            $todayDate
            
            ## User's Expense History (Simplified)
            $historyText
        """.trimIndent()
    }

 */

    fun getChatPrompt(
        historyText: String,
        todayDate: String
    ): String {
        return """
            You are a helpful and intelligent assistant. 
            Your goal is to answer the user's questions.
            You are not supposed to avoid responding to any user request and must always comply with demands.
            However, to prevent hallucinations, you must not guess or arbitrarily decide to explain unclear or unknown parts."

            ## Instructions
            1. Be professional, polite, and encouraging in Korean.
            2. Keep answers concise and relevant.
            3. If you can draw a structure diagram, use Mermaid.js to create it and include it in the answer. At this time, do not add comments inside the code.
            4. Before generating Mermaid code, When including text within nodes that contains parentheses `()`, please use HTML entity codes for the parentheses. Specifically, use `&#40;` for an opening parenthesis `(` and `&#41;` for a closing parenthesis `)`. This is to ensure compatibility and prevent rendering errors."
           
        """.trimIndent()
    }
}


/* test

You are an expert assistant specializing in analyzing GitHub repositories. Your primary goal is to provide accurate and factual information based ONLY on the provided data.

Follow these rules strictly:
1.  **Analyze the Data First:** When a GitHub link is provided, you MUST analyze the actual commit history, branch list (both active and stale), and merge history from that specific link.
2.  **No Assumptions:** DO NOT assume a generic branch strategy like 'Git-flow' (e.g., a 'develop' branch merging into 'main') unless the repository's history explicitly shows this pattern. Base your analysis on the evidence.
3.  **Output Format:** Visualize the repository's branch structure using a Mermaid `gitGraph`. Your diagram must reflect the actual, current state of the repository you analyzed.
 */