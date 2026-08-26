package com.ustc.vacancychecker.data.remote

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SecondFactorWebViewTest {

    @Test
    fun selectsSmsTabThenRequestsCodeOnce_inAndroidWebView() {
        verifyPage(
            header = "二次身份验证",
            smsTab = "短信验证码",
            requestCode = "获取验证码"
        )
    }

    @Test
    fun selectsSmsTabThenRequestsCodeOnce_onEnglishPage() {
        verifyPage(
            header = "2-Factor Authentication",
            smsTab = "SMS",
            requestCode = "Obtain Verification Code"
        )
    }

    private fun verifyPage(header: String, smsTab: String, requestCode: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pageLoaded = CountDownLatch(1)
        var webView: WebView? = null

        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(LoginScriptUtils.getSecondFactorAutoRequestScript(), null)
                        pageLoaded.countDown()
                    }
                }
                loadDataWithBaseURL(
                    "https://id.ustc.edu.cn/",
                    """
                        <html><body>
                          <h1>$header</h1>
                          <div role="tab" id="sms" onclick="
                            window.smsClicks = (window.smsClicks || 0) + 1;
                            if (!document.getElementById('request')) {
                              var suffix = document.createElement('span');
                              suffix.className = 'ant-input-suffix';
                              var decorator = document.createElement('span');
                              decorator.className = 'input-decorator-icon';
                              var button = document.createElement('a');
                              button.id = 'request';
                              button.className = 'font-class-text-button';
                              button.textContent = '$requestCode';
                              button.onclick = function(event) {
                                window.requestClicks = (window.requestClicks || 0) + 1;
                                window.requestClickTarget = event.currentTarget.tagName;
                              };
                              decorator.appendChild(button);
                              suffix.appendChild(decorator);
                              document.body.appendChild(suffix);
                            }
                          ">$smsTab</div>
                        </body></html>
                    """.trimIndent(),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }

        check(pageLoaded.await(5, TimeUnit.SECONDS)) { "Test page did not load" }
        Thread.sleep(750)
        assertEquals("[0,0]", readClickCounts(instrumentation, webView))

        Thread.sleep(3_000)
        assertEquals("[1,1]", readClickCounts(instrumentation, webView))
        assertEquals("A", readJavaScriptString(instrumentation, webView, "window.requestClickTarget"))

        instrumentation.runOnMainSync {
            webView?.destroy()
        }
    }

    private fun readClickCounts(
        instrumentation: android.app.Instrumentation,
        webView: WebView?
    ): String? {
        val resultReady = CountDownLatch(1)
        var result: String? = null
        instrumentation.runOnMainSync {
            webView?.evaluateJavascript(
                "JSON.stringify([window.smsClicks || 0, window.requestClicks || 0])"
            ) {
                result = it?.trim('"')?.replace("\\\"", "\"")
                resultReady.countDown()
            }
        }
        check(resultReady.await(5, TimeUnit.SECONDS)) { "JavaScript result timed out" }
        return result
    }

    private fun readJavaScriptString(
        instrumentation: android.app.Instrumentation,
        webView: WebView?,
        expression: String
    ): String? {
        val resultReady = CountDownLatch(1)
        var result: String? = null
        instrumentation.runOnMainSync {
            webView?.evaluateJavascript(expression) {
                result = it?.trim('"')
                resultReady.countDown()
            }
        }
        check(resultReady.await(5, TimeUnit.SECONDS)) { "JavaScript result timed out" }
        return result
    }
}
