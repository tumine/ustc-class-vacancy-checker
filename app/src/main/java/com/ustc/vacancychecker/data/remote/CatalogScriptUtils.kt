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
     * @param isTeacher 是否按教师搜索
     */
    fun getSearchScript(keyword: String, isTeacher: Boolean = false): String {
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
                    // 检查页面是否正在加载。在加载数据时直接输入可能会被 Vue 清除。
                    var loadingElements = document.querySelectorAll('.el-loading-mask, .ant-spin, .loading, [class*="loading"]');
                    var isSpinnerVisible = Array.from(loadingElements).some(function(el) {
                        // 检查元素是否真正可见
                        var style = window.getComputedStyle(el);
                        return style && style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0' && el.offsetWidth > 0;
                    });
                    
                    var isLoading = isSpinnerVisible 
                        || document.body.innerText.includes('正在加载') 
                        || document.body.innerText.includes('请稍候');
                    
                    if (isLoading) {
                        logDom("Page is loading, waiting before searching...");
                        return false;
                    }
                    
                    var searchInput = null;
                    
                    // 获取所有可能是我们要找的输入框
                    var allInputs = Array.from(document.querySelectorAll('input.el-input__inner, input.ant-input, input[type="text"], input[type="search"], input:not([type])'));
                    // 过滤掉隐藏的输入框
                    var visibleInputs = allInputs.filter(function(el) {
                        var style = window.getComputedStyle(el);
                        return style.display !== 'none' && style.visibility !== 'hidden' && el.offsetWidth > 0;
                    });
                    
                    var inputsToCheck = visibleInputs.length > 0 ? visibleInputs : allInputs;
                    
                    if (inputsToCheck.length === 0) {
                        logDom("No inputs found at all, attempt " + attempts);
                        return false;
                    }
                    
                    if ($isTeacher) {
                        // 1. 根据 placeholder 寻找教师
                        searchInput = inputsToCheck.find(function(el) { 
                            var p = el.placeholder || "";
                            return p.includes("教师") || p.includes("老师") || p.includes("教员") || p.includes("姓名");
                        });
                        
                        // 2. 如果没找到，降级为第二个输入框 (在教务系统中往往第二个才是教师)
                        if (!searchInput && inputsToCheck.length >= 2) {
                            searchInput = inputsToCheck[1];
                        }
                    } else {
                        // 1. 根据 placeholder 寻找课程
                        searchInput = inputsToCheck.find(function(el) { 
                            var p = el.placeholder || "";
                            return p.includes("课程") || p.includes("编号") || p.includes("名称") || p.includes("课堂") || p.includes("关键");
                        });
                    }
                    
                    // 3. 终极兜底：选第一个可见的输入框
                    if (!searchInput) {
                        logDom("Fallback to the first available input...");
                        searchInput = inputsToCheck[0];
                    }
                    
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
                        }, 4000);
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
                    // 首先检测是否有加指示器
                    var loadingElements = document.querySelectorAll('.el-loading-mask, .ant-spin, .loading, [class*="loading"]');
                    var isSpinnerVisible = Array.from(loadingElements).some(function(el) {
                        var style = window.getComputedStyle(el);
                        return style && style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0' && el.offsetWidth > 0;
                    });
                    
                    var isLoading = isSpinnerVisible 
                        || document.body.innerText.includes('正在加载') 
                        || document.body.innerText.includes('请稍候');
                        
                    if (isLoading) {
                        logDom("Page is still loading, waiting...");
                        return false;
                    }
                    
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
                    
                    // 如果找到结果，或者没有加载动画并且已经尝试了足够的次数(比如至少延时了3秒)，才返回
                    if (results.length > 0) {
                        logDom("Extracted " + results.length + " courses");
                        try { AndroidBridge.onSearchResults(JSON.stringify(results)); } catch(e) {}
                        return true;
                    } else if (attempts > 5) {
                        // 如果多次尝试仍无结果，且页面未在加载，可能真的没有结果，继续重试直到超时
                        logDom("No results found yet, attempt " + attempts);
                    }
                    
                    return false;
                }
                
                function extractFromCells(cells) {
                    var texts = [];
                    var courseNameElement = null;
                    var courseCodeElement = null;

                    for (var i = 0; i < cells.length; i++) {
                        texts.push((cells[i].innerText || '').trim());
                        var cnElem = cells[i].querySelector('.course-name');
                        if (cnElem) {
                            courseNameElement = cnElem;
                        } else if (cells[i].classList.contains('course-name')) {
                            courseNameElement = cells[i];
                        }
                        
                        var ccElem = cells[i].querySelector('.course-code');
                        if (ccElem) {
                            courseCodeElement = ccElem;
                        } else if (cells[i].classList.contains('course-code')) {
                            courseCodeElement = cells[i];
                        }
                    }
                    
                    var classCode = '';
                    var courseName = '';
                    var teacher = '';
                    
                    if (courseCodeElement) {
                        classCode = (courseCodeElement.innerText || '').trim();
                    } else {
                        for (var t = 0; t < texts.length; t++) {
                            if (/^\d{4,}$/.test(texts[t]) && !classCode) {
                                classCode = texts[t];
                            }
                        }
                    }
                    
                    if (courseNameElement) {
                        courseName = (courseNameElement.innerText || '').trim();
                    }
                    
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
                    var courseNameElement = card.querySelector('.course-name');
                    var extCourseName = '';
                    if (courseNameElement) {
                        extCourseName = (courseNameElement.innerText || '').trim();
                    }
                    
                    var courseCodeElement = card.querySelector('.course-code');
                    var extCourseCode = '';
                    if (courseCodeElement) {
                        extCourseCode = (courseCodeElement.innerText || '').trim();
                    }

                    var text = (card.innerText || '').trim();
                    if (!text && !extCourseName && !extCourseCode) return null;
                    
                    var classCode = extCourseCode;
                    if (!classCode) {
                        var codeMatch = text.match(/(?:课堂号|编号|code)[：:\s]*(\d{4,})/i) || text.match(/\b(\d{4,})\b/);
                        classCode = codeMatch ? codeMatch[1] : '';
                    }
                    
                    var lines = text.split('\n').map(function(l) { return l.trim(); }).filter(function(l) { return l; });
                    var courseName = extCourseName;
                    var teacher = '';
                    
                    for (var i = 0; i < lines.length; i++) {
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
                    var courseNameElement = el.querySelector('.course-name');
                    var extCourseName = '';
                    if (courseNameElement) {
                        extCourseName = (courseNameElement.innerText || '').trim();
                    }
                    
                    var courseCodeElement = el.querySelector('.course-code');
                    var extCourseCode = '';
                    if (courseCodeElement) {
                        extCourseCode = (courseCodeElement.innerText || '').trim();
                    }

                    var text = (el.innerText || '').trim();
                    if (!text && !extCourseName && !extCourseCode) return null;
                    if (text && text.length < 4 && !extCourseName && !extCourseCode) return null;
                    if (text && text.length > 500 && !extCourseName && !extCourseCode) return null;
                    
                    var classCode = extCourseCode;
                    if (!classCode) {
                        var codeMatch = text.match(/\b(\d{4,})\b/);
                        if (!codeMatch) return null;
                        classCode = codeMatch[1];
                    }
                    
                    var remaining = text.replace(classCode, '').trim();
                    var parts = remaining.split(/[\s\t,，|]+/).filter(function(p) { return p.length > 0; });
                    
                    var courseName = extCourseName;
                    var teacher = '';
                    
                    for (var i = 0; i < parts.length; i++) {
                        if (/^[\u4e00-\u9fa5]{2,4}$/.test(parts[i]) && parts[i] !== courseName) {
                            teacher = parts[i];
                        }
                    }
                    
                    if (classCode || courseName) {
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
