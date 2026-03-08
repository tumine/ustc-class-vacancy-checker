package com.ustc.vacancychecker.data.remote

/**
 * catalog.ustc.edu.cn 课程目录页面 JS 脚本工具类
 *
 * 用于在 WebView 中对 catalog.ustc.edu.cn/query/lesson 页面注入搜索和提取脚本。
 */
object CatalogScriptUtils {

    /**
     * 在 catalog.ustc.edu.cn/query/lesson 页面的搜索框中输入关键字并触发搜索
     * @param keyword 搜索关键字
     */
    fun getSearchScript(keyword: String): String {
        val safeKeyword = keyword
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")

        return """
            (function() {
                var attempts = 0;
                var maxAttempts = 40;
                
                function logDom(msg) {
                    try { AndroidBridge.logDomInfo(msg); } catch(e) { console.log(msg); }
                }
                
                // 使用原生 setter 设置值（兼容 Vue/React 等框架）
                var nativeSetter = Object.getOwnPropertyDescriptor(
                    window.HTMLInputElement.prototype, 'value'
                ).set;
                
                function setNativeValue(element, value) {
                    nativeSetter.call(element, value);
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                    element.dispatchEvent(new Event('change', { bubbles: true }));
                    element.dispatchEvent(new Event('keyup', { bubbles: true }));
                }
                
                function trySearch() {
                    // 查找搜索输入框
                    var searchInput = document.querySelector('input[placeholder*="搜索"]')
                        || document.querySelector('input[placeholder*="关键"]')
                        || document.querySelector('input[placeholder*="课程"]')
                        || document.querySelector('input[type="search"]')
                        || document.querySelector('.el-input__inner')
                        || document.querySelector('.ant-input')
                        || document.querySelector('input.search-input')
                        || document.querySelector('input[type="text"]');
                    
                    if (!searchInput) {
                        logDom("Search input not found, attempt " + attempts);
                        return false;
                    }
                    
                    logDom("Found search input, typing keyword: $safeKeyword");
                    
                    // 聚焦并输入关键字
                    searchInput.focus();
                    setNativeValue(searchInput, "$safeKeyword");
                    
                    // 延迟后触发搜索
                    setTimeout(function() {
                        // 模拟回车触发搜索
                        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, bubbles: true }));
                        searchInput.dispatchEvent(new KeyboardEvent('keypress', { key: 'Enter', keyCode: 13, bubbles: true }));
                        searchInput.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', keyCode: 13, bubbles: true }));
                        
                        // 也尝试点击搜索按钮
                        var searchBtn = document.querySelector('button[type="submit"]')
                            || document.querySelector('.el-input__suffix')
                            || document.querySelector('.search-btn')
                            || document.querySelector('button.ant-btn-primary');
                        if (searchBtn) {
                            logDom("Found search button, clicking...");
                            searchBtn.click();
                        }
                        
                        logDom("Search triggered, waiting for results...");
                        
                        // 等待搜索结果加载后提取
                        setTimeout(function() {
                            try { AndroidBridge.onSearchTriggered(); } catch(e) {}
                        }, 2000);
                    }, 500);
                    
                    return true;
                }
                
                var intervalId = setInterval(function() {
                    attempts++;
                    if (trySearch()) {
                        clearInterval(intervalId);
                    } else if (attempts >= maxAttempts) {
                        clearInterval(intervalId);
                        logDom("Timeout: search input not found after " + maxAttempts + " attempts");
                        try { AndroidBridge.onSearchError("搜索框未找到，页面加载超时"); } catch(e) {}
                    }
                }, 500);
                
                // 立即尝试一次
                trySearch();
            })();
        """.trimIndent()
    }

    /**
     * 从搜索结果页面中提取课程列表
     * 通过 AndroidBridge.onSearchResults(json) 回调 JSON 数组
     * 每个元素包含 classCode, courseName, teacher
     */
    fun getExtractResultsScript(): String {
        return """
            (function() {
                var attempts = 0;
                var maxAttempts = 20;
                
                function logDom(msg) {
                    try { AndroidBridge.logDomInfo(msg); } catch(e) { console.log(msg); }
                }
                
                function tryExtract() {
                    var results = [];
                    
                    // 策略1: 表格行
                    var rows = document.querySelectorAll('table tbody tr, .el-table__body tbody tr');
                    if (rows.length > 0) {
                        logDom("Found " + rows.length + " table rows");
                        for (var i = 0; i < rows.length; i++) {
                            var cells = rows[i].querySelectorAll('td');
                            if (cells.length >= 3) {
                                var item = extractFromCells(cells);
                                if (item) results.push(item);
                            }
                        }
                    }
                    
                    // 策略2: 卡片式/列表式布局
                    if (results.length === 0) {
                        var cards = document.querySelectorAll('.lesson-item, .course-item, .course-card, .list-item, .el-card');
                        logDom("Found " + cards.length + " card items");
                        for (var j = 0; j < cards.length; j++) {
                            var item = extractFromCard(cards[j]);
                            if (item) results.push(item);
                        }
                    }
                    
                    // 策略3: 通用提取 - 根据文本模式匹配（课堂号通常是纯数字）
                    if (results.length === 0) {
                        var allRows = document.querySelectorAll('tr, [class*="row"], [class*="item"]');
                        logDom("Fallback: scanning " + allRows.length + " generic rows/items");
                        for (var k = 0; k < allRows.length; k++) {
                            var item = extractFromGeneric(allRows[k]);
                            if (item) results.push(item);
                        }
                    }
                    
                    if (results.length > 0) {
                        logDom("Extracted " + results.length + " courses");
                        try { AndroidBridge.onSearchResults(JSON.stringify(results)); } catch(e) {}
                        return true;
                    }
                    
                    return false;
                }
                
                function extractFromCells(cells) {
                    // 尝试从表格单元格中提取课堂号、课程名、教师
                    var texts = [];
                    for (var i = 0; i < cells.length; i++) {
                        texts.push((cells[i].innerText || '').trim());
                    }
                    
                    // 查找课堂号（纯数字字段）
                    var classCode = '';
                    var courseName = '';
                    var teacher = '';
                    
                    for (var t = 0; t < texts.length; t++) {
                        if (/^\d{4,}$/.test(texts[t]) && !classCode) {
                            classCode = texts[t];
                        }
                    }
                    
                    // 课程名通常是最长的中文文本
                    var maxLen = 0;
                    for (var n = 0; n < texts.length; n++) {
                        if (texts[n].length > maxLen && /[\u4e00-\u9fa5]/.test(texts[n]) && texts[n] !== classCode) {
                            maxLen = texts[n].length;
                            courseName = texts[n];
                        }
                    }
                    
                    // 教师名通常是2-4个汉字
                    for (var p = 0; p < texts.length; p++) {
                        if (/^[\u4e00-\u9fa5]{2,4}$/.test(texts[p]) && texts[p] !== courseName) {
                            teacher = texts[p];
                            break;
                        }
                    }
                    
                    if (classCode || courseName) {
                        return { classCode: classCode, courseName: courseName, teacher: teacher };
                    }
                    return null;
                }
                
                function extractFromCard(card) {
                    var text = (card.innerText || '').trim();
                    if (!text) return null;
                    
                    // 从卡片文本中提取
                    var codeMatch = text.match(/(?:课堂号|编号|code)[：:\s]*(\d{4,})/i) || text.match(/\b(\d{4,})\b/);
                    var classCode = codeMatch ? codeMatch[1] : '';
                    
                    var lines = text.split('\n').map(function(l) { return l.trim(); }).filter(function(l) { return l; });
                    var courseName = '';
                    var teacher = '';
                    
                    for (var i = 0; i < lines.length; i++) {
                        if (lines[i].length > courseName.length && /[\u4e00-\u9fa5]/.test(lines[i]) && !/^\d+$/.test(lines[i])) {
                            courseName = lines[i];
                        }
                        if (/^[\u4e00-\u9fa5]{2,4}$/.test(lines[i])) {
                            teacher = lines[i];
                        }
                    }
                    
                    if (classCode || courseName) {
                        return { classCode: classCode, courseName: courseName, teacher: teacher };
                    }
                    return null;
                }
                
                function extractFromGeneric(el) {
                    var text = (el.innerText || '').trim();
                    if (!text || text.length < 4 || text.length > 500) return null;
                    
                    var codeMatch = text.match(/\b(\d{4,})\b/);
                    if (!codeMatch) return null;
                    
                    var classCode = codeMatch[1];
                    var remaining = text.replace(classCode, '').trim();
                    
                    var parts = remaining.split(/[\s\t,，|/]+/).filter(function(p) { return p.length > 0; });
                    var courseName = '';
                    var teacher = '';
                    
                    for (var i = 0; i < parts.length; i++) {
                        if (parts[i].length > courseName.length && /[\u4e00-\u9fa5]/.test(parts[i])) {
                            courseName = parts[i];
                        }
                        if (/^[\u4e00-\u9fa5]{2,4}$/.test(parts[i]) && parts[i] !== courseName) {
                            teacher = parts[i];
                        }
                    }
                    
                    if (courseName) {
                        return { classCode: classCode, courseName: courseName, teacher: teacher };
                    }
                    return null;
                }
                
                var intervalId = setInterval(function() {
                    attempts++;
                    if (tryExtract()) {
                        clearInterval(intervalId);
                    } else if (attempts >= maxAttempts) {
                        clearInterval(intervalId);
                        logDom("Timeout: no results extracted after " + maxAttempts + " attempts");
                        try { AndroidBridge.onSearchResults("[]"); } catch(e) {}
                    }
                }, 500);
                
                // 立即尝试一次
                tryExtract();
            })();
        """.trimIndent()
    }
}
