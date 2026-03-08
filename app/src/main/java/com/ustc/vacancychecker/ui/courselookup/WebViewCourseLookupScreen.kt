package com.ustc.vacancychecker.ui.courselookup

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ustc.vacancychecker.data.remote.CatalogScriptUtils

/**
 * WebView 课程目录搜索页面
 * 加载 catalog.ustc.edu.cn/query/lesson，注入 JS 脚本搜索并提取课程数据
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewCourseLookupScreen(
    keyword: String,
    onSearchResults: (String) -> Unit,
    onSearchError: (String) -> Unit
) {
    val catalogUrl = "https://catalog.ustc.edu.cn/query/lesson"

    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var hasTriggeredSearch by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 进度条
        if (isLoading) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // WebView
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    // 添加 JavaScript 接口
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun logDomInfo(info: String) {
                            Log.d("CourseLookup-DOM", info)
                        }

                        @JavascriptInterface
                        fun onSearchTriggered() {
                            Log.d("CourseLookup", "Search triggered, extracting results...")
                            hasTriggeredSearch = true
                            // 搜索已触发，注入提取脚本
                            post {
                                val js = CatalogScriptUtils.getExtractResultsScript()
                                evaluateJavascript(js, null)
                            }
                        }

                        @JavascriptInterface
                        fun onSearchResults(json: String) {
                            Log.d("CourseLookup", "Results received: $json")
                            post { onSearchResults(json) }
                        }

                        @JavascriptInterface
                        fun onSearchError(message: String) {
                            Log.e("CourseLookup", "Search error: $message")
                            post { onSearchError(message) }
                        }
                    }, "AndroidBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            Log.d("CourseLookup", "Page started: $url")
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            Log.d("CourseLookup", "Page finished: $url")

                            // 页面加载完成后注入搜索脚本
                            if (url?.contains("catalog.ustc.edu.cn") == true && !hasTriggeredSearch) {
                                Log.d("CourseLookup", "Catalog page loaded, injecting search script for: $keyword")
                                // 等待 Vue SPA 渲染完成
                                view?.postDelayed({
                                    val js = CatalogScriptUtils.getSearchScript(keyword)
                                    view.evaluateJavascript(js, null)
                                }, 2000)
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            val errorMsg = error?.description?.toString() ?: "未知错误"
                            Log.e("CourseLookup", "Error: $errorMsg, URL: ${request?.url}")
                            if (request?.isForMainFrame == true) {
                                post { onSearchError("页面加载失败: $errorMsg") }
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            loadingProgress = newProgress
                        }
                    }

                    loadUrl(catalogUrl)
                }
            },
            update = { }
        )
    }
}
