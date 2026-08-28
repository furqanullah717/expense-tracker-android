package com.smartspend.ai.ai

import com.smartspend.ai.utils.FakeDataGenerator

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
            2. Item Title: Extract a specific name for the transaction (e.g., "ê°ì?€ê¹€", "?œêµ­??ê°•ì˜").
            3. Category: Select the MOST appropriate from [${categories.joinToString(", ")}]
            4. Date Selection (CRITICAL):
               - NEVER PROVIDE A FUTURE DATE.
               - If the input says "24?? and today is "23??, you MUST return the 24th of the PREVIOUS MONTH.
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
    1. Focus on Spending Pace: In "Section 5", compare the "?´ë²ˆ ???„ì¬ê¹Œì? ì´?ì§€ì¶? with the "ì§€?????™ì¼ ê¸°ê°„ ì§€ì¶?. Warn the user ONLY if current spending exceeds last month's same-period spending by more than 5%. If the difference is within a Â±5% range, do not issue any warnings.
    2. Yearly Context: If "Section 6" is provided, compare the yearly "???‰ê·  ì§€ì¶? (Monthly Averages) to identify long-term inflation or lifestyle changes. Mention if current spending is significantly higher or lower than the historical monthly average.
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
    1. Persona: Maintain an objective, sharp, and professional tone. Use financial concepts like 'Run-rate (?Œë¹„ ?ë„)', 'Fixed vs Variable costs (ê³ ì •ë¹?ë³€?™ë¹„)', and 'Lifestyle Creep (?¼ì´?„ìŠ¤?€???½ì°½)'.
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
                "topic": "Core category or issue (e.g., ?ë¹„ ê³¼ë‹¤ ì§€ì¶?",
                "finding": "Detailed data-backed finding.",
                "implication": "What this means for their overall wealth or monthly cash flow."
            }
        ], // Provide 3 to 5 deep insights
        "actionableStrategies": [
            {
                "urgency": "HIGH", // HIGH, MEDIUM, LOW
                "advice": "Specific, practical, and precise action item.",
                "expectedImpact": "Expected result if followed (e.g., ??15ë§Œì› ?ˆì•½ ê°€??"
            }
        ], // Provide 2 to 4 strategies
        "period": "$periodText"
    }
""".trimIndent()
    }

    fun getChatPrompt(
        historyText: String,
        todayDate: String
    ): String {
        return """
            You are a helpful and intelligent financial assistant for an Smart Spend with AI Agent app.
            Your goal is to answer the user's questions about their spending habits, provide financial advice, or help them manage their data.

             ## Instructions
            1. Use the provided history to answer specific expense questions (e.g., "How much did I spend on coffee?"). Never invent financial data that is not in the history.
            2. Answer every user question, including questions unrelated to expenses or this app. For unrelated questions, give a helpful, friendly general answer instead of refusing or saying that you can only discuss finances.
            3. Be professional, polite, and encouraging in Korean unless the user clearly requests another language.
            4. If you don't have enough information for a precise answer, say so briefly and ask for clarification when useful.
            5. If the user asks to "delete" or "modify" something, explain that as an AI, you can't directly edit the database, but you can guide them or classify their intent for the system to handle later (For now, just provide a polite response).
            6. Keep answers concise, natural, and relevant to the user's latest question.
            7. If you determine that it would be better to explain it in a structured way, use Mermaid.js to create it and include it in the answer. At this time, do not add comments inside the code.
            8. Before generating Mermaid code, When including text within nodes that contains parentheses `()`,  wrap the entire text in double quotes ex. C["entire text includes ()"];.

            
            ## Today's Date
            $todayDate
            
            ## User's Expense History (Simplified)
            $historyText
        """.trimIndent()
    }

    fun getMasterRouterPrompt(
        input: String,
        todayDate: String
    ): String {
        return """
            Analyze the user's natural language input and classify it into one of the following 9 intent categories for an  Smart Spend with AI Agent app.
            
            ## Today's Date
            $todayDate
            
            ## Intent Categories (High-level classification)
            1. DATA_RETRIEVAL: Simple search, statistics, or calculations requiring DB lookup. (e.g., "?´ë²ˆ ???ë¹„ ?¼ë§ˆ??")
            2. DATA_ANALYSIS: Deep analysis, cause analysis, budget planning, or advice. (e.g., "?ë¹„ê°€ ???˜ì—ˆì§€?")
               - **CRITICAL**: If the user input contains the word 'ë¶„ì„' (analysis), the intent MUST be classified as DATA_ANALYSIS.
            3. DATA_MANIPULATION: Adding, modifying, or deleting data. (e.g., "?´ì œ ?ì‹œë¹?ì¶”ê?")
            4. SIMPLE_RESPONSE: App usage, general knowledge, or small talk.
            5. APP_ACTION: Navigating to specific screens or executing native app features. 
               - ONLY the following actions are allowed: [EXPORT_EXCEL, NAVIGATE_HOME, NAVIGATE_CHARTS, NAVIGATE_TRANSACTIONS].
               - Example: "?‘ì?ë¡??´ë³´??ì¤?, "???”ë©´?¼ë¡œ ê°€ì¤?, "ì°¨íŠ¸ ?”ë©´?¼ë¡œ ê°€ì¤?
               - DO NOT use this for generic requests like setting alarms, reminders, or features not listed here.
            6. DATA_VISUALIZATION: Requests to generate diagrams (Mermaid), charts, or structured visual summaries based on spending data.
               - Example: "ì§€ì¶??´ì—­ ?¤ì´?´ê·¸?¨ìœ¼ë¡?ê·¸ë ¤ì¤?, "?´ë²ˆ ???Œë¹„ ?ë¦„ Mermaidë¡??•ë¦¬?´ì¤˜"
            7. CONTEXT_REFERENCE: Referring to previous conversations.
            8. MERMAID_ERROR: **ONLY** when the user reports a failure/syntax error in a PREVIOUSLY generated diagram and asks to FIX it.
               - Example: "Mermaid ?¤ë¥˜ ?¬ì–´ ê³ ì³ì¤?, "?¤ì´?´ê·¸?¨ì´ ??ë³´ì—¬ ?˜ì •?´ì¤˜"
            9. FALLBACK: Unidentifiable text or requests completely unrelated to the app.

            ## Available Sub-categories (Select applicable items from below)
            [${FakeDataGenerator.ALL_CATEGORIES.joinToString(", ")}]

            ## CRITICAL RULES
            1. Return ONLY a valid JSON object.
            2. Do NOT include markdown code blocks (No ```json).
            3. For APP_ACTION, 'sub_categories' must contain exactly ONE of [EXPORT_EXCEL, NAVIGATE_HOME, NAVIGATE_CHARTS, NAVIGATE_TRANSACTIONS]. If the requested action is not in this list, classify it as FALLBACK or SIMPLE_RESPONSE.
            4. 'sub_categories' selection (DATABASE QUERY SCOPE):
               - **IMPORTANT**: This is for a DB 'WHERE category IN (...)' query. If you miss a category, the user will see WRONG data.
               - **BRAND-CATEGORY HARD MAPPING (MANDATORY)**:
                 - "?¤ì´?? (Daiso): MUST ALWAYS return ["?í™œ/ë§ˆíŠ¸", "?¼í•‘/?˜ë¥˜"]. NEVER choose just one.
                 - "ì£¼ìœ " (Fueling): MUST ALWAYS return ["êµí†µë¹?, "ì°¨ëŸ‰/?•ë¹„"].
                 - "?¸ì˜?? (Convenience Store): MUST ALWAYS return ["?í™œ/ë§ˆíŠ¸", "ì¹´í˜/ê°„ì‹"].
                 - "?¤í?ë²…ìŠ¤" (Starbucks): MUST ALWAYS return ["ì¹´í˜/ê°„ì‹"].
               - **PROCESS**: 
                 1) Identify the core topics and brands in the input.
                 2) If a brand from the HARD MAPPING is found, use the mapping ABOVE.
                 3) Select EVERY other category that matches ANY of those topics.
               - **NO GUESSING**: Do NOT try to guess where the "most expensive" or "specific" item is. Include ALL potentially relevant categories.
               - Your performance is measured by how well you BROADEN the search scope. Under-inclusion is a CRITICAL ERROR.
            5. 'is_bulk' (Very Important - Strict Rule):
               - Set to true ONLY if the user wants to act on an ENTIRE category or an ENTIRE time period without ANY keyword filtering.
               - **CRITICAL**: If the user mentions a specific item, brand, or topic (e.g., "ì£¼ìœ ", "?¤í?ë²…ìŠ¤", "ì¹˜í‚¨"), `is_bulk` MUST be **false**.
               - Using words like "ëª¨ë“ ", "?„ë?", or "?? (all/every) DOES NOT automatically make it bulk if a keyword like "ì£¼ìœ " is present. "ì£¼ìœ  ?´ì—­ ??ë³´ì—¬ì¤? means "all refueling items", which requires keyword filtering, so `is_bulk` must be **false**.
               - Only "?´ë²ˆ ???´ì—­ ??ë³´ì—¬ì¤? (without category/keyword) or "?ë¹„ ?´ì—­ ?„ë? ë³´ì—¬ì¤? (entire category) should be `is_bulk: true`.
               - If in doubt, set to **false** to trigger a 2nd pass for title-based filtering.
            6. 'retrieval_operation' (For DATA_RETRIEVAL):
               - SUM: Only if asking for total amount (e.g., "?©ê³„", "ì´ì•¡", "?„ì²´ ?¼ë§ˆ").
               - MAX: Only if asking for the **largest monetary amount** (e.g., "ê°€??ë¹„ì‹¼ ê±?, "ìµœë? ì§€ì¶?ê¸ˆì•¡"). 
               - MIN: Only if asking for the **smallest monetary amount** (e.g., "ê°€????ê±?, "ìµœì†Œ ì§€ì¶?ê¸ˆì•¡").
               - COUNT: Only if asking for the number of items (e.g., "ëª?ê±?, "?Ÿìˆ˜", "ëª?ë²?).
               - LIST: **DEFAULT operation**. Use this for "Show details", "Most recent (ê°€??ìµœê·¼)", "Last transaction (ë§ˆì?ë§??´ì—­)", or any request to see the items themselves.
            7. 'manipulation_type' (For DATA_MANIPULATION):
               - INSERT: Add new data.
               - UPDATE: Modify existing.
               - DELETE: Remove data.
            8. 'start_date' and 'end_date' selection (Context-Aware):
               - Return the date range required to perform the necessary DATA RETRIEVAL or ANALYSIS.
               - If the user compares with a previous period (e.g., "than last month", "compared to last year"), the 'start_date' MUST be the beginning of that PREVIOUS period.
               - Example (Today is 2026-08-19): "Why did I spend more than last month?" -> start_date: "2026-07-01", end_date: "2026-08-19" (July is last month).
               - Example (Today is 2026-08-19): "My son's academy fees seem to have increased compared to last year" -> start_date: "2025-01-01", end_date: "2026-08-19" (2025 is last year).
               - Example (Today is 2026-08-19): "Show me yesterday's snacks" -> start_date: "2026-08-18", end_date: "2026-08-18".
               - If no specific or comparative period is implied, return null for both.
            9. 'reasoning' (CRITICAL):
               - Provide a natural, user-friendly logical explanation in Korean.
               - **DO NOT** use technical terms from this prompt (e.g., "Original Input", "Intent", "Sub-categories", "Master Router", "Available Names").
               - Example: "ì£¼ìœ ë¹„ì— ?€??ë¬¼ì–´ë³´ì…”??ì°¨ëŸ‰ ? ì?ë¹?ì¹´í…Œê³ ë¦¬???´ë²ˆ ???´ì—­???•ì¸?©ë‹ˆ??"
            
            ## Output JSON Format
            {
              "intent": "High-level category",
              "sub_categories": ["Category 1", "Category 2"],
              "is_bulk": boolean,
              "manipulation_type": "INSERT | UPDATE | DELETE | null", 
              "retrieval_operation": "SUM | MAX | MIN | COUNT | LIST | null",
              "start_date": "YYYY-MM-DD or null",
              "end_date": "YYYY-MM-DD or null",
              "confidence_score": float (0.0 to 1.0),
              "reasoning": "?¬ìš©??ì¹œí™”?ì¸ ë¶„ë¥˜ ê·¼ê±° (Korean)",
              "reply_message": "?¬ìš©?ì—ê²?ì¦‰ì‹œ ë³´ì—¬ì¤??ìŠ¤??(Korean)"
            }

            User Input: "$input"
        """.trimIndent()
    }

    /**
     * 2-Pass: DATA_RETRIEVAL ???˜ë? ê¸°ë°˜ ?„í„°ë§?ë°??°ì‚° ê²°ì • ?„ë¡¬?„íŠ¸
     */
    fun getSecondPassRetrievalPrompt(
        originalInput: String,
        uniqueNames: List<String>
    ): String {
        return """
            Identify semantically relevant transaction items based on the user's request.
            
            Original Input: "$originalInput"
            Available Names: [${uniqueNames.joinToString(", ")}]
            
            ## Instructions
            1. Select names from "Available Names" that are semantically related to the "Original Input".
            
            ## Output JSON Format
            {
              "relevant_names": ["matched name 1", "matched name 2"],
              "reasoning": "?¬ìš©?ê? ?´í•´?˜ê¸° ?¬ìš´ ??ª© ? íƒ ?´ìœ  (Korean). **?ˆë?** 'Original Input'?´ë‚˜ 'Available Names' ê°™ì? ê¸°ìˆ  ?©ì–´ë¥??¬ìš©?˜ì? ë§ˆì„¸??"
            }
        """.trimIndent()
    }

    /**
     * 2-Pass: DATA_MANIPULATION ???˜ë? ê¸°ë°˜ ?„í„°ë§?ë°?ì¡°ì‘ ?¡ì…˜ ê²°ì • ?„ë¡¬?„íŠ¸
     */
    fun getSecondPassManipulationPrompt(
        originalInput: String,
        uniqueNames: List<String>
    ): String {
        return """
            Identify the target transaction items for the requested change.
            
            Original Input: "$originalInput"
            Available Names: [${uniqueNames.joinToString(", ")}]
            
            ## Instructions
            1. Select names from "Available Names" that are the targets of the requested change.
            2. If the user's intent is to UPDATE, identify which field should be changed: title, amount, category.
            
            ## Output JSON Format
            {
              "relevant_names": ["matched name 1", "matched name 2"],
              "update_field": "title | amount | category | date | null",
              "new_value": "The new value provided by the user (e.g., '?ë¹„' for category, '2024-08-25' for date, or '20000' for amount). Return null if not specified.",
              "reasoning": "?¬ìš©?ê? ?´í•´?˜ê¸° ?¬ìš´ ? íƒ ë°??„ë“œ ê²°ì • ?´ìœ  (Korean). ê¸°ìˆ  ?©ì–´ ?¬ìš© ê¸ˆì?."
            }
        """.trimIndent()
    }

    /**
     * 2-Pass: DATA_ANALYSIS ??ë¶„ì„ ?„ë¡¬?„íŠ¸ (ê°„ì†Œ??ë²„ì „)
     */
    fun getSecondPassAnalysisPrompt(
        originalInput: String,
        rawDataText: String
    ): String {
        return """
            Analyze the following transaction data to answer the user's question.
            User Question: "$originalInput"
            
            Data:
            $rawDataText
            
            Provide a deep analysis and helpful advice in Korean.
        """.trimIndent()
    }

    /**
     * 2-Pass: CONTEXT_REFERENCE ??ë§¥ë½ ?´ì†Œ ?„ë¡¬?„íŠ¸
     */
    fun getContextResolutionPrompt(
        historyContext: String,
        currentInput: String,
        todayDate: String,
        similarityThreshold: Float = 0.5f
    ): String {
        return """
            The user has provided a follow-up request that refers to the previous conversation context.
            Your task is to resolve this into a concrete 'AiMasterRouterResponse' JSON.
            
            Today's Date: $todayDate
            
            ## Previous Context (User & Assistant interaction):
            $historyContext
            
            ## Current Follow-up Request:
            "$currentInput"
            
            ## Goal:
            Convert this follow-up into a full 'AiMasterRouterResponse' JSON object.
            
            ## CRITICAL CONTEXT RULES:
            1. **Current Request Priority**: The Current Follow-up Request is the PRIMARY source of truth for the scope. 
            2. **Scope Broadening**: If the current request uses a BROADER term (e.g., "Child-related") than the previous context (e.g., "Child Education"), you MUST broaden the 'sub_categories' accordingly (e.g., include both "Child Education" AND "Child Allowance").
            3. **Context as Reference Only**: Use context ONLY to fill in missing gaps (like dates or specific items referred to as "that", "it"). DO NOT let the context limit a more general current request.
            4. **Independence**: If the current request is a new standalone question even if it seems related, evaluate it based on its own words first.
            
            Example 1: 
              Context: User asked "What was expensive yesterday?", Assistant answered "Chicken (30,000 KRW)".
              Current Request: "Delete that."
              Resolved Response: { "intent": "DATA_MANIPULATION", "sub_categories": ["?ë¹„/?¥ë³´ê¸?], "start_date": "yesterday's date", "end_date": "yesterday's date", ... }

            ## Instructions for 'sub_categories' (DATABASE QUERY SCOPE):
            - **MANDATORY**: Think of this as defining the search range for a database.
            - **BRAND-CATEGORY HARD MAPPING**:
              - "?¤ì´??: Return ["?í™œ/ë§ˆíŠ¸", "?¼í•‘/?˜ë¥˜"].
              - "ì£¼ìœ ": Return ["êµí†µë¹?, "ì°¨ëŸ‰/?•ë¹„"].
              - "?¸ì˜??: Return ["?í™œ/ë§ˆíŠ¸", "ì¹´í˜/ê°„ì‹"].
            - If the request is "Child-related", you MUST return ALL categories containing "?ë?" OR related to children (e.g., ["?ë?êµìœ¡-?™ì›/ê³¼ì™¸", "?ë?êµìœ¡-?…ì‹œì»¨ì„¤??, "?ë??©ëˆ"]).
            - Never filter down to a single category if the user's term is broad. Over-inclusion is the goal.

            ## Available Categories:
            [${FakeDataGenerator.ALL_CATEGORIES.joinToString(", ")}]
            
            ## Output JSON Format (ONLY AiMasterRouterResponse):
            {
              "intent": "DATA_RETRIEVAL | DATA_ANALYSIS | DATA_MANIPULATION | SIMPLE_RESPONSE | APP_ACTION",
              "sub_categories": ["Category Name"],
              "is_bulk": boolean,
              "manipulation_type": "INSERT | UPDATE | DELETE | null", 
              "retrieval_operation": "SUM | MAX | MIN | COUNT | LIST | null", 
              "start_date": "YYYY-MM-DD or null",
              "end_date": "YYYY-MM-DD or null",
              "confidence_score": float (0.0 to 1.0),
              "reasoning": "?´ì „ ?€?”ì—???´ë–¤ ?•ë³´ë¥?ì°¸ê³ ?ˆëŠ”ì§€ ?¬ìš©?ê? ?´í•´?˜ê¸° ?½ê²Œ ?¤ëª… (Korean). ê¸°ìˆ  ?©ì–´(Context, Input ?? ?¬ìš© ê¸ˆì?.",
              "reply_message": null
            }
            
            ## Rules for 'is_bulk':
            - Set to true ONLY for "Show all in category" or "Show all in period".
            - Set to false if ANY specific item/brand keyword is mentioned (e.g., "all coffee" -> false).
            
            ## Rules for 'sub_categories' (AGGRESSIVE MULTI-TAGGING):
            - **DO NOT** limit the scope based on previous context if the current request is broader.
            - If the user says "Child-related" after talking about "Education", they are EXPANDING the scope. Include ALL relevant categories (Education, Allowance, etc.).
            - Inclusion over Precision: When in doubt, include more categories.
            - Default to LIST unless a specific statistical question (total, count, amount-based max, amount-based min) is asked.
            - "Most recent" or "Last" request must be LIST, not MAX.
            - If the current request refers to 'ë¶„ì„' (analysis), intent must be DATA_ANALYSIS.
        """.trimIndent()
    }
}