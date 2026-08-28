package com.smartspend.ai.ai.chat_agent

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
 * [?±ëŠ¥ ìµœì ??- ?Œë” ê²°ê³¼ ìºì‹±]
 * ë¬¸ì œ: ì½”ë“œ/Mermaid ë¸”ë¡ë§ˆë‹¤ WebViewê°€ ?ˆê³ , ?¤í¬ë¡¤ë¡œ ?”ë©´??ë²—ì–´?¬ë‹¤ ?Œì•„?¤ë©´
 *       WebViewë¥??ˆë¡œ ë§Œë“¤??highlight.js/mermaid.jsë¥?ë§¤ë²ˆ ?¤ì‹œ ?¤í–‰?ˆë‹¤.
 *       (JS ?Œì‹±Â·?¤í–‰??ë¸”ë¡???˜ì‹­~?˜ë°± ms ??ë¹ ë¥¸ ?¤í¬ë¡????„ë ˆ???œë)
 * ?´ê²°: "?Œë”ê°€ ?„ë£Œ??ê²°ê³¼ë¬????´ìš© ?´ì‹œ ê¸°ì??¼ë¡œ ë©”ëª¨ë¦?LRU)???€?¥í•˜ê³?
 *       ê°™ì? ì½”ë“œë¥??¤ì‹œ ê·¸ë¦´ ?ŒëŠ” JS ?¤í–‰ ?†ì´ ê²°ê³¼ë¬¼ì„ HTML???•ì ?¼ë¡œ ì£¼ì…?œë‹¤.
 *
 * ìºì‹œ ???¤ê³„:
 *   "${code.hashCode()}/$dark" ??ì½”ë“œ ?´ìš© + ?¤í¬ëª¨ë“œ ?¬ë?.
 *   ?¤í¬ëª¨ë“œ???˜ì´?¼ì´???Œë§ˆ(github/github-dark)?€ mermaid ?Œë§ˆê°€ ?¬ë¼ì§€ë¯€ë¡??¤ë? ë¶„ë¦¬.
 *
 * LRU(24ê°?: ìµœê·¼ ë³?ë¸”ë¡ë§?? ì?. ?¤ë˜????ª©?€ ?ë™ ?? œ?˜ì–´ ë©”ëª¨ë¦?ë¬´í•œ ì¦ê?ë¥?ë§‰ëŠ”??
 *   ???„ë¡œ?¸ìŠ¤ ë©”ëª¨ë¦?ê¸°ì??´ë¼ ???¬ì‹œ???„ì—??ê°?ë¸”ë¡ 1?Œì”© ?¤ì‹œ ?Œë”?œë‹¤.
 */
object CodeRenderCache {
    private const val MAX_ENTRIES = 24

    // key: "${code.hashCode()}/$dark" ??hljsê°€ ë§Œë“  ?˜ì´?¼ì´??"span ì¡°ê°"
    // (<code> ?”ì†Œ??innerHTML. ê»ë°ê¸°ì¸ <code>/<pre>???€?¥í•˜ì§€ ?ŠëŠ”??-
    // ?¬ì‚¬????ìºì‹œ ?ˆíŠ¸ ?œí”Œë¦¿ì´ ?•ê·œ?”ëœ êµ¬ì¡°ë¡??ˆë¡œ ê°ì‹¸ê¸??Œë¬¸)
    val highlightedHtml = LruCache<String, String>(MAX_ENTRIES)

    // key: "${code.hashCode()}/$dark" ??mermaid.render()ê°€ ?ì„±??SVG ë¬¸ì??    val mermaidSvg = LruCache<String, String>(MAX_ENTRIES)

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
    // [ìºì‹œ ?ˆì „?¥ì¹˜] JS ë¸Œë¦¬ì§€ ì½œë°±?€ ë¹„ë™ê¸°ë¼?? ?‘ë‹µ???„ì°©?ˆì„ ?ŒëŠ”
    // ?´ë? codeê°€ ë°”ë€Œì–´(?¤íŠ¸ë¦¬ë° ?? ?´ì „ WebView??ì½œë°±?????ˆë‹¤.
    // rememberUpdatedStateë¡?"??ƒ ìµœì‹  ??ë¥?ì°¸ì¡°?˜ê²Œ ??stale ê²°ê³¼ê°€
    // ?˜ëª»???¤ë¡œ ìºì‹œ?˜ëŠ” ê²ƒì„ ë§‰ëŠ”?? (activeRenderTokenê³??™ì¼???¨í„´)
    val activeCacheKey = rememberUpdatedState(CodeRenderCache.codeKey(code, dark))
    // ?¤íŠ¸ë¦¬ë° ì¤?ì½”ë“œ ë·°ì—??Mermaid ë·°ë¡œ ?„í™˜?¼ë„ ?¤ë¥˜ ë©”ì‹œì§€ë¥?? ì??œë‹¤.
    var mermaidError by remember(code) { mutableStateOf<String?>(null) }
    var showMermaidErrorDialog by remember { mutableStateOf(false) }
    val height = remember(code, mermaid) {
        mutableStateOf(
            if (mermaid) 300.dp
            else (code.lines().size * 20 + 27).coerceAtLeast(50).dp   // ì½”ë“œ?ì—­ ?’ì´ (ë³´ì •ê°?+27)
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
                // [?±ëŠ¥ ìµœì ??9ë²?- ìºì‹œ ?ˆíŠ¸ ê²½ë¡œ]
                // ê³¼ê±°??mermaid.render()ê°€ ë§Œë“  SVGê°€ ?´ë? ?ˆìœ¼ë¯€ë¡?                // mermaid.js <script> ?œê·¸ ?ì²´ë¥??£ì? ?ŠëŠ”????JS ?¤ìš´ë¡œë“œ/?Œì‹±/?¤í–‰ 0ms.
                // SVGë¥?#diagram???•ì ?¼ë¡œ ?£ê³ , ?’ì´ ?™ê¸°??ResizeObserver)ë§??˜í–‰?œë‹¤.
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
                // [ìºì‹œ ë¯¸ìŠ¤ ê²½ë¡œ + ?±ëŠ¥ ìµœì ??8ë²?
                // mermaid.min.jsë¥?CDN ?€??APK assets?ì„œ ë¡œë“œ?œë‹¤ (?¤í”„?¼ì¸ ì§€?? ?¤íŠ¸?Œí¬ ì§€???œê±°).
                // ?Œë” ?±ê³µ ??onDiagramSvg ë¸Œë¦¬ì§€ë¡?SVGë¥?Kotlin???„ë‹¬???¤ìŒ ?Œë”ë¶€???¬ì‚¬?©í•œ??
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
            // ì½”ë“œ ?˜ì´?¼ì´??            val language = detectLanguage(code)
            val cachedHighlighted = CodeRenderCache.highlightedHtml.get(cacheKey)

            if (cachedHighlighted != null) {
                // [?±ëŠ¥ ìµœì ??9ë²?- ìºì‹œ ?ˆíŠ¸ ê²½ë¡œ]
                // ê³¼ê±°??hljsê°€ ?˜ì´?¼ì´?…ì„ ë§ˆì¹œ HTML(<span> ë§ˆí¬?????´ë? ?ˆìœ¼ë¯€ë¡?                // highlight.min.js <script> ?œê·¸ ?ì²´ë¥??£ì? ?ŠëŠ”????JS ?¤í–‰ 0ms.
                // CSSë§?ë¡œë“œ?˜ë©´ ?¤íŒ¬ ?´ë˜???‰ìƒ??ê·¸ë?ë¡??ìš©?œë‹¤.
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
                        ::-webkit-scrollbar { width: 0; height: 3.5px; }  // ?¤í¬ë¡¤ë°” ?’ì´
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
                // [ìºì‹œ ë¯¸ìŠ¤ ê²½ë¡œ]
                // [?±ëŠ¥ ìµœì ??8ë²?- ?„ì‹œ ë¡¤ë°±] highlight.js/CSS??ë¡œì»¬ ?ì…‹ ?€??CDN ?¬ìš©.
                //   ë¡œì»¬ ?ì…‹(file:///android_asset) ë¡œë“œê°€ ?¬ìƒ?±ëœ WebView?ì„œ ê°„í— ?¤íŒ¨?˜ë©´
                //   hljs ë¯¸ì‹¤?????˜ì´?¼ì´???†ëŠ” ?¨ìƒ‰ ?ìŠ¤?¸ê? ê·¸ë ¤ì§€ê³? ê·¸ê²ƒ??ê·¸ë?ë¡?                //   ìºì‹œ???€?¥ë˜??ê³„ì† ê¹¨ì§„ ?‰ìœ¼ë¡?ë³´ì´??ë¬¸ì œê°€ ?•ì¸?˜ì—ˆ??
                // [ìºì‹œ ?€??ì¡°ê±´] renderKey != 0 (?¤íŠ¸ë¦¬ë° ì¢…ë£Œ ???ë§Œ ì½œë°±???¬ê³ ,
                //   JS??window load ?´í›„ + hljs ?„ì—­ ì¡´ì¬ ê²€ì¦ì„ ê±°ì¹œ ?¤ì—ë§?                //   ê²°ê³¼ë¥??€?¥í•œ?????´ë–¤ ê²½ìš°?ë„ "ê¹¨ì§„ ?Œë”"??ìºì‹œ?˜ì? ?ŠëŠ”??
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
                        ::-webkit-scrollbar { width: 0; height: 3.5px; }  // ?¤í¬ë¡¤ë°” ?’ì´
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
                        // hljs ?¤í¬ë¦½íŠ¸ê°€ ?„ì „??ë¡œë“œ???¤ì—ë§??˜ì´?¼ì´??ìº¡ì²˜ë¥??¤í–‰?œë‹¤.
                        // (inline script??body ?Œì‹± ì§í›„ ?¤í–‰?˜ë?ë¡? ??²Œ ?„ì°©?˜ëŠ” ?¸ë? ?¤í¬ë¦½íŠ¸ë¥?ê¸°ë‹¤ë¦¬ë ¤ë©?load ?´ë²¤???„ìš”)
                        function doHighlight() {
                            try {
                                if (!window.hljs) return; // ?¤í¬ë¦½íŠ¸ ë¡œë“œ ?¤íŒ¨ ???˜ì´?¼ì´?¸ë„ ìºì‹œ???¬ê¸° (ê¹¨ì§„ ê²°ê³¼ ?€??ë°©ì?)
                                hljs.highlightAll();
                                // [ìºì‹œ ?€??ê·œì¹™]
                                // - code ?”ì†Œ??"?´ë? span ì¡°ê°"ë§??€?¥í•œ??(<pre>/<code> ê»ë°ê¸??œì™¸).
                                // - $shouldCache: ?¤íŠ¸ë¦¬ë° ì¤?renderKey == 0)?ëŠ” ?€?¥í•˜ì§€ ?ŠëŠ”??
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

                        // JavaScript ?¸í„°?˜ì´??ì¶”ê?
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
                                    // [?±ëŠ¥ ìµœì ??9ë²? mermaid ?Œë” ê²°ê³¼ SVGë¥?ìºì‹œ???€??
                                    // token ê²€?¬ë¡œ "?´ì „ WebView????? ?‘ë‹µ"?€ ?ê¸°?œë‹¤.
                                    if (token == activeRenderToken.value && svg.isNotBlank()) {
                                        CodeRenderCache.mermaidSvg.put(activeCacheKey.value, svg)
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onCodeHighlighted(token: Int, html: String) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    // [?±ëŠ¥ ìµœì ??9ë²? hljs ?˜ì´?¼ì´??ê²°ê³¼ HTML??ìºì‹œ???€??
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
                        // [?±ëŠ¥ ìµœì ??8ë²? base URL??asset ê²½ë¡œë¡?ì§€?•í•´
                        // ?ë?/?ˆë? file:///android_asset ë¦¬ì†Œ??ë¡œë“œë¥??ˆìš©?œë‹¤.
                        // (?´ì „?ëŠ” https://cdn.jsdelivr.net ë¥?baseë¡??¬ìš©)
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
                    // [?±ëŠ¥ ìµœì ??5ë²?
                    // LazyColumn?ì„œ ?„ì´?œì´ ?”ë©´??ë²—ì–´??Composable???Œê´´?????¸ì¶œ?œë‹¤.
                    // ëª…ì‹œ?ìœ¼ë¡?destroy()?˜ì? ?Šìœ¼ë©?WebView???¤ì´?°ë¸Œ ë¦¬ì†Œ?¤ê?
                    // GCë¥?ê¸°ë‹¤ë¦¬ë©° ë©”ëª¨ë¦¬ì— ?„ì ?˜ì–´ ?¥ì‹œê°??¤í¬ë¡???ë²„ë²…?„Â·OOM ?„í—˜???ˆë‹¤.
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
                    text = "\uD83D\uDD17",  // ë³µì‚¬ ?„ì´ì½?                    modifier = Modifier
                        .clickable {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("code", code))
                        }
                        .padding(8.dp),
                    fontSize = 16.sp
                )

                if (mermaid && mermaidError != null) {
                    Text(
                        text = "\uD83D\uDCE2",  // ?ëŸ¬ ?”í…Œ??ì°??„ì´ì½?                        modifier = Modifier
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
            title = { Text("Mermaid ?¤ë¥˜") },
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
                                "$errorMessage\n\n?¤ë¥˜ê°€ ë°œìƒ??Mermaid ?ë³¸ ì½”ë“œ:\n```mermaid\n$code\n```"
                            )
                        }
                    ) {
                        Text("Gemini?ê²Œ ë¬¼ì–´ë³´ê¸°")
                    }
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("mermaid-error", errorMessage)
                            )
                        }
                    ) {
                        Text("ë³µì‚¬")
                    }
                    TextButton(onClick = { showMermaidErrorDialog = false }) {
                        Text("?•ì¸")
                    }
                }
            }
        )
    }
}

// ?¸ì–´ ê°ì? ?¨ìˆ˜
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

// HTML ?´ìŠ¤ì¼€?´í”„ ?¨ìˆ˜
private fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")
        .replace("/", "&#x2F;")
}
