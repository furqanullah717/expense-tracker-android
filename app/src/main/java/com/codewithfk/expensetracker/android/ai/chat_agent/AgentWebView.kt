package com.codewithfk.expensetracker.android.ai.chat_agent

import android.annotation.SuppressLint
import android.util.LruCache
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * [성능 최적화 - 렌더 결과 캐싱]
 * 문제: 코드/Mermaid 블록마다 WebView가 있고, 스크롤로 화면을 벗어났다 돌아오면
 *       WebView를 새로 만들어 highlight.js/mermaid.js를 매번 다시 실행했다.
 *       (JS 파싱·실행에 블록당 수십~수백 ms → 빠른 스크롤 시 프레임 드랍)
 * 해결: "렌더가 완료된 결과물"을 내용 해시 기준으로 메모리(LRU)에 저장하고,
 *       같은 코드를 다시 그릴 때는 JS 실행 없이 결과물을 HTML에 정적으로 주입한다.
 *
 * 캐시 키 설계:
 *   "${code.hashCode()}/$dark" — 코드 내용 + 다크모드 여부.
 *   다크모드는 하이라이트 테마(github/github-dark)와 mermaid 테마가 달라지므로 키를 분리.
 *
 * LRU(24개): 최근 본 블록만 유지. 오래된 항목은 자동 삭제되어 메모리 무한 증가를 막는다.
 *   ※ 프로세스 메모리 기준이라 앱 재시작 후에는 각 블록 1회씩 다시 렌더된다.
 */
object CodeRenderCache {
    private const val MAX_ENTRIES = 24

    // key: "${code.hashCode()}/$dark" → hljs가 만든 하이라이트 "span 조각"
    // (<code> 요소의 innerHTML. 껍데기인 <code>/<pre>는 저장하지 않는다 -
    // 재사용 시 캐시 히트 템플릿이 정규화된 구조로 새로 감싸기 때문)
    val highlightedHtml = LruCache<String, String>(MAX_ENTRIES)

    // key: "${code.hashCode()}/$dark" → mermaid.render()가 생성한 SVG 문자열
    val mermaidSvg = LruCache<String, String>(MAX_ENTRIES)

    fun codeKey(code: String, dark: Boolean): String = "${code.hashCode()}/$dark"
}

@Composable
fun CodeWebView(
    code: String,
    mermaid: Boolean,
    renderKey: Int = 0,
    onAskGemini: (String) -> Unit = {}
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val backgroundColor = if (dark) AgentPalette.codeDarkBackgroundColor else AgentPalette.codeBackgroundColor
    val backgroundHex = if (dark) AgentPalette.codeDarkBackgroundHex else AgentPalette.codeBackgroundHex
    val highlightStyle = if (dark) "github-dark" else "github"
    val mermaidTheme = if (dark) "dark" else "default"
    val errorColorHex = AgentPalette.mermaidErrorHex
    val renderToken = listOf(code, mermaid, dark, renderKey).hashCode()
    val activeRenderToken = rememberUpdatedState(renderToken)
    // [캐시 안전장치] JS 브리지 콜백은 비동기라서, 응답이 도착했을 때는
    // 이미 code가 바뀌어(스트리밍 등) 이전 WebView의 콜백일 수 있다.
    // rememberUpdatedState로 "항상 최신 키"를 참조하게 해 stale 결과가
    // 잘못된 키로 캐시되는 것을 막는다. (activeRenderToken과 동일한 패턴)
    val activeCacheKey = rememberUpdatedState(CodeRenderCache.codeKey(code, dark))
    // 스트리밍 중 코드 뷰에서 Mermaid 뷰로 전환돼도 오류 메시지를 유지한다.
    var mermaidError by remember(code) { mutableStateOf<String?>(null) }
    var showMermaidErrorDialog by remember { mutableStateOf(false) }
    val height = remember(code, mermaid) {
        mutableStateOf(
            if (mermaid) 300.dp
            else (code.lines().size * 20 + 27).coerceAtLeast(50).dp   // 코드영역 높이 (보정값 +27)
        )
    }

    val html = remember(code, mermaid, dark, renderKey) {
        val cacheKey = CodeRenderCache.codeKey(code, dark)
        if (mermaid) {
            val escapedCode = code
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("${'$'}", "\\${'$'}")

            val cachedSvg = CodeRenderCache.mermaidSvg.get(cacheKey)

            if (cachedSvg != null) {
                // [성능 최적화 9번 - 캐시 히트 경로]
                // 과거에 mermaid.render()가 만든 SVG가 이미 있으므로
                // mermaid.js <script> 태그 자체를 넣지 않는다 → JS 다운로드/파싱/실행 0ms.
                // SVG를 #diagram에 정적으로 넣고, 높이 동기화(ResizeObserver)만 수행한다.
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body {
                            margin: 0;
                            padding: 16px;
                            background: $backgroundHex;
                            overflow: visible;
                            box-sizing: border-box;
                        }
                        #diagram {
                            width: 100%;
                            text-align: center;
                        }
                        #diagram svg {
                            display: block;
                            width: 100%;
                            height: auto;
                            overflow: visible;
                        }
                    </style>
                </head>
                <body>
                    <div id="diagram">$cachedSvg</div>
                    <script>
                        const diagram = document.getElementById('diagram');
                        const reportHeight = () => {
                            const bodyStyle = window.getComputedStyle(document.body);
                            const verticalPadding = parseFloat(bodyStyle.paddingTop) + parseFloat(bodyStyle.paddingBottom);
                            const height = Math.ceil(diagram.getBoundingClientRect().height + verticalPadding);
                            if (window.Android) window.Android.onDiagramRendered($renderToken, height);
                        };
                        new ResizeObserver(reportHeight).observe(diagram);
                        requestAnimationFrame(reportHeight);
                        setTimeout(reportHeight, 100);
                        setTimeout(reportHeight, 300);
                        setTimeout(reportHeight, 700);
                        if (document.fonts?.ready) document.fonts.ready.then(reportHeight);
                    </script>
                </body>
                </html>
                """.trimIndent()
            } else {
                // [캐시 미스 경로 + 성능 최적화 8번]
                // mermaid.min.js를 CDN 대신 APK assets에서 로드한다 (오프라인 지원, 네트워크 지연 제거).
                // 렌더 성공 시 onDiagramSvg 브리지로 SVG를 Kotlin에 전달해 다음 렌더부터 재사용한다.
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <script src="file:///android_asset/agent/mermaid.min.js"></script>
                    <style>
                        body {
                            margin: 0;
                            padding: 16px;
                            background: $backgroundHex;
                            overflow: visible;
                            box-sizing: border-box;
                        }
                        #diagram {
                            width: 100%;
                            text-align: center;
                        }
                        #diagram svg {
                            display: block;
                            width: 100%;
                            height: auto;
                            overflow: visible;
                        }
                        .error {
                            color: $errorColorHex;
                            padding: 10px;
                            font-family: monospace;
                        }
                    </style>
                </head>
                <body>
                    <div id="diagram"></div>
                    <script>
                        const mermaidCode = `${escapedCode}`;

                        mermaid.initialize({
                            startOnLoad: true,
                            theme: '$mermaidTheme',
                            securityLevel: 'loose',
                            themeVariables: {
                                'fontSize': '14px',
                                'fontFamily': 'sans-serif'
                            }
                        });

                        async function renderMermaid() {
                            try {
                                const { svg } = await mermaid.render('mermaid-diagram', mermaidCode);
                                document.getElementById('diagram').innerHTML = svg;
                                if (window.Android) window.Android.onDiagramSvg($renderToken, svg);
                                if (window.Android) window.Android.onMermaidError($renderToken, '');

                                if (window.Android) {
                                    const reportHeight = () => {
                                        const diagram = document.getElementById('diagram');
                                        const bodyStyle = window.getComputedStyle(document.body);
                                        const verticalPadding = parseFloat(bodyStyle.paddingTop) + parseFloat(bodyStyle.paddingBottom);
                                        const height = Math.ceil(diagram.getBoundingClientRect().height + verticalPadding);
                                        window.Android.onDiagramRendered($renderToken, height);
                                    };
                                    new ResizeObserver(reportHeight).observe(document.getElementById('diagram'));
                                    requestAnimationFrame(reportHeight);
                                    setTimeout(reportHeight, 100);
                                    setTimeout(reportHeight, 300);
                                    setTimeout(reportHeight, 700);
                                    if (document.fonts?.ready) document.fonts.ready.then(reportHeight);
                                }
                            } catch (error) {
                                console.error('Mermaid error:', error);
                                if (window.Android) window.Android.onMermaidError($renderToken, String(error?.message || error));
                                document.getElementById('diagram').innerHTML =
                                    '<div class="error">Mermaid Error: ' + error.message + '</div>';
                            }
                        }

                        renderMermaid();
                    </script>
                </body>
                </html>
                """.trimIndent()
            }
        } else {
            // 코드 하이라이팅
            val language = detectLanguage(code)
            val cachedHighlighted = CodeRenderCache.highlightedHtml.get(cacheKey)

            if (cachedHighlighted != null) {
                // [성능 최적화 9번 - 캐시 히트 경로]
                // 과거에 hljs가 하이라이팅을 마친 HTML(<span> 마크업)이 이미 있으므로
                // highlight.min.js <script> 태그 자체를 넣지 않는다 → JS 실행 0ms.
                // CSS만 로드하면 스팬 클래스 색상이 그대로 적용된다.
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <link rel="stylesheet" href="file:///android_asset/agent/$highlightStyle.min.css">
                    <style>
                        body {
                            margin: 0;
                            padding: 0px;
                            background: $backgroundHex;
                            overflow-x: auto;
                            overflow-y: hidden;
                        }
                        ::-webkit-scrollbar { width: 0; height: 3.5px; }  // 스크롤바 높이
                        ::-webkit-scrollbar-track { background: transparent; }
                        ::-webkit-scrollbar-thumb { background: #808890; border-radius: 5px; }
                        pre {
                            margin: 0;
                            padding: 0px;
                            background: $backgroundHex;
                            border-radius: 6px;
                            overflow-x: auto;
                            white-space: pre;
                        }
                        code {
                            font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
                            font-size: 13px;
                            line-height: 1.5;
                        }
                        .hljs {
                            background: transparent !important;
                        }
                    </style>
                </head>
                <body>
                    <pre><code class="language-${language} hljs">$cachedHighlighted</code></pre>
                </body>
                </html>
                """.trimIndent()
            } else {
                // [캐시 미스 경로]
                // [성능 최적화 8번 - 임시 롤백] highlight.js/CSS는 로컬 에셋 대신 CDN 사용.
                //   로컬 에셋(file:///android_asset) 로드가 재생성된 WebView에서 간헐 실패하면
                //   hljs 미실행 → 하이라이트 없는 단색 텍스트가 그려지고, 그것이 그대로
                //   캐시에 저장되어 계속 깨진 색으로 보이는 문제가 확인되었다.
                // [캐시 저장 조건] renderKey != 0 (스트리밍 종료 후)에만 콜백을 심고,
                //   JS는 window load 이후 + hljs 전역 존재 검증을 거친 뒤에만
                //   결과를 저장한다 → 어떤 경우에도 "깨진 렌더"는 캐시되지 않는다.
                val shouldCache = renderKey != 0
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/$highlightStyle.min.css">
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
                    <style>
                        body {
                            margin: 0;
                            padding: 0px;
                            background: $backgroundHex;
                            overflow-x: auto;
                            overflow-y: hidden;
                        }
                        ::-webkit-scrollbar { width: 0; height: 3.5px; }  // 스크롤바 높이
                        ::-webkit-scrollbar-track { background: transparent; }
                        ::-webkit-scrollbar-thumb { background: #808890; border-radius: 5px; }
                        pre {
                            margin: 0;
                            padding: 0px;
                            background: $backgroundHex;
                            border-radius: 6px;
                            overflow-x: auto;
                            white-space: pre;
                        }
                        code {
                            font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
                            font-size: 13px;
                            line-height: 1.5;
                        }
                        .hljs {
                            background: transparent !important;
                        }
                    </style>
                </head>
                <body>
                    <pre><code class="language-${language}">${escapeHtml(code)}</code></pre>
                    <script>
                        // hljs 스크립트가 완전히 로드된 뒤에만 하이라이팅+캡처를 실행한다.
                        // (inline script는 body 파싱 직후 실행되므로, 늦게 도착하는 외부 스크립트를 기다리려면 load 이벤트 필요)
                        function doHighlight() {
                            try {
                                if (!window.hljs) return; // 스크립트 로드 실패 시 하이라이트도 캐시도 포기 (깨진 결과 저장 방지)
                                hljs.highlightAll();
                                // [캐시 저장 규칙]
                                // - code 요소의 "내부 span 조각"만 저장한다 (<pre>/<code> 껍데기 제외).
                                // - $shouldCache: 스트리밍 중(renderKey == 0)에는 저장하지 않는다.
                                if (window.Android && $shouldCache) {
                                    window.Android.onCodeHighlighted($renderToken, document.getElementsByTagName('code')[0].innerHTML);
                                }
                            } catch (e) {
                                console.error('highlight failed:', e);
                            }
                        }
                        if (document.readyState === 'complete') doHighlight();
                        else window.addEventListener('load', doHighlight);
                    </script>
                </body>
                </html>
                """.trimIndent()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.value)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
    ) {
        key(mermaid, renderKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isHorizontalScrollBarEnabled = true
                        isVerticalScrollBarEnabled = false
                        isScrollbarFadingEnabled = false
                        scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                        }

                        // JavaScript 인터페이스 추가
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onDiagramRendered(token: Int, newHeight: Int) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    if (token == activeRenderToken.value) {
                                        val newDpHeight = newHeight.dp + 12.dp
                                        height.value = newDpHeight.coerceAtLeast(50.dp)
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onMermaidError(token: Int, message: String) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    if (token == activeRenderToken.value) {
                                        mermaidError = message.takeIf { it.isNotBlank() }
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onDiagramSvg(token: Int, svg: String) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    // [성능 최적화 9번] mermaid 렌더 결과 SVG를 캐시에 저장.
                                    // token 검사로 "이전 WebView의 늦은 응답"은 폐기한다.
                                    if (token == activeRenderToken.value && svg.isNotBlank()) {
                                        CodeRenderCache.mermaidSvg.put(activeCacheKey.value, svg)
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onCodeHighlighted(token: Int, html: String) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    // [성능 최적화 9번] hljs 하이라이팅 결과 HTML을 캐시에 저장.
                                    if (token == activeRenderToken.value && html.isNotBlank()) {
                                        CodeRenderCache.highlightedHtml.put(activeCacheKey.value, html)
                                    }
                                }
                            }
                        }, "Android")

                        webViewClient = object : WebViewClient() {
                            @SuppressLint("ClickableViewAccessibility")
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                            }
                        }
                        setOnTouchListener { view, event ->
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            false
                        }
                    }
                },
                update = { webView ->
                    if (webView.tag != html) {
                        // [성능 최적화 8번] base URL을 asset 경로로 지정해
                        // 상대/절대 file:///android_asset 리소스 로드를 허용한다.
                        // (이전에는 https://cdn.jsdelivr.net 를 base로 사용)
                        webView.loadDataWithBaseURL(
                            "file:///android_asset/",
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                        webView.tag = html
                    }
                },
                onRelease = { webView ->
                    // [성능 최적화 5번]
                    // LazyColumn에서 아이템이 화면을 벗어나 Composable이 파괴될 때 호출된다.
                    // 명시적으로 destroy()하지 않으면 WebView의 네이티브 리소스가
                    // GC를 기다리며 메모리에 누적되어 장시간 스크롤 시 버벅임·OOM 위험이 있다.
                    webView.stopLoading()
                    webView.destroy()
                }
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent
        ) {
            Row {
                Text(
                    text = "\uD83D\uDD17",  // 복사 아이콘
                    modifier = Modifier
                        .clickable {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("code", code))
                        }
                        .padding(8.dp),
                    fontSize = 16.sp
                )

                if (mermaid && mermaidError != null) {
                    Text(
                        text = "\uD83D\uDCE2",  // 에러 디테일 창 아이콘
                        modifier = Modifier
                            .clickable { showMermaidErrorDialog = true }
                            .padding(8.dp),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
    if (showMermaidErrorDialog && mermaidError != null) {
        val errorMessage = mermaidError.orEmpty()
        AlertDialog(
            onDismissRequest = { showMermaidErrorDialog = false },
            title = { Text("Mermaid 오류") },
            text = {
                SelectionContainer {
                    Column {
                        Text(errorMessage)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text(
                            text = code,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            showMermaidErrorDialog = false
                            onAskGemini(
                                "$errorMessage\n\n오류가 발생한 Mermaid 원본 코드:\n```mermaid\n$code\n```"
                            )
                        }
                    ) {
                        Text("Gemini에게 물어보기")
                    }
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("mermaid-error", errorMessage)
                            )
                        }
                    ) {
                        Text("복사")
                    }
                    TextButton(onClick = { showMermaidErrorDialog = false }) {
                        Text("확인")
                    }
                }
            }
        )
    }
}

// 언어 감지 함수
private fun detectLanguage(code: String): String {
    return when {
        code.contains("fun ") || code.contains("val ") -> "kotlin"
        code.contains("def ") || code.contains("import ") -> "python"
        code.contains("function") || code.contains("const ") -> "javascript"
        code.contains("class ") || code.contains("public ") -> "java"
        code.contains("SELECT") || code.contains("INSERT") -> "sql"
        code.contains("<!DOCTYPE") || code.contains("<html") -> "html"
        code.contains("{") && code.contains("}") -> "json"
        else -> "plaintext"
    }
}

// HTML 이스케이프 함수
private fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")
        .replace("/", "&#x2F;")
}
