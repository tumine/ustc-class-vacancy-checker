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
                
                function getTokens(el, stripTags) {
                    if (!el) return [];
                    var clone = el.cloneNode(true);
                    if (stripTags) {
                        var tagEls = clone.querySelectorAll('.el-tag, .tag, .badge, [class*="tag"], [class*="badge"], [class*="label"]');
                        for (var i = 0; i < tagEls.length; i++) {
                            tagEls[i].parentNode.removeChild(tagEls[i]);
                        }
                    }
                    var safeHtml = (clone.innerHTML || '').replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ');
                    var temp = document.createElement('div');
                    temp.innerHTML = safeHtml;
                    var text = temp.textContent || temp.innerText || '';
                    return text.split(/[\s\t\n,，|]+/).filter(function(p) { return p.length > 0; });
                }
                
                function extractTeachers(el, courseName) {
                    var tokens = getTokens(el, true);
                    var teachers = [];
                    var blacklist = [
                        '理论课', '实验课', '讨论课', '上机课', '大课', '小课',
                        '中文', '英文', '双语', '英语', '日语', '法语', '德语',
                        '笔试', '机考', '开卷', '闭卷',
                        '秋季', '春季', '夏季', '冬季',
                        '选修', '必修', '公选', '专业', '通识', '核心', '基础',
                        '本科', '硕士', '博士', '研究生',
                        '学分', '学时', '周学时',
                        '计划内', '自由',
                        '数学系', '物理系', '化学系', '生物系', '计算机', '电子系',
                        '天文系', '地球', '材料系', '力学系', '光学',
                        '文学系', '历史系', '哲学系', '管理系', '经济系',
                        '天文学系', '信息学院', '工程学院', '理学院',
                        '统计与', '金融系'
                    ];
                    for (var i = 0; i < tokens.length; i++) {
                        var t = tokens[i];
                        if (/^[\u4e00-\u9fa5]{2,4}$/.test(t) && t !== courseName && teachers.indexOf(t) === -1) {
                            if (blacklist.indexOf(t) === -1 && !/系$/.test(t) && !/学院$/.test(t) && !/课$/.test(t)) {
                                teachers.push(t);
                            }
                        }
                    }
                    return teachers.join(',');
                }
                
                function tryExtract() {
                    // 首先检测是否有加载指示器
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
                    
                    // 策略3: 通用提取 - 根据文本模式匹配
                    if (results.length === 0) {
                        var allRows = document.querySelectorAll('tr, [class*="row"], [class*="item"]');
                        logDom("Fallback: scanning " + allRows.length + " generic rows/items");
                        for (var k = 0; k < allRows.length; k++) {
                            var item = extractFromGeneric(allRows[k]);
                            if (item) results.push(item);
                        }
                    }
                    
                    var uniqueResults = [];
                    var seenCodes = {};
                    for (var r = 0; r < results.length; r++) {
                        var c = results[r];
                        if (c.classCode && !seenCodes[c.classCode]) {
                            seenCodes[c.classCode] = true;
                            uniqueResults.push(c);
                        }
                    }
                    
                    if (uniqueResults.length > 0) {
                        logDom("Extracted " + uniqueResults.length + " unique courses");
                        try { AndroidBridge.onSearchResults(JSON.stringify(uniqueResults)); } catch(e) {}
                        return true;
                    } else if (attempts > 5) {
                        logDom("No results found yet, attempt " + attempts);
                    }
                    
                    return false;
                }
                
                function extractFromCells(cells) {
                    var allTokens = [];
                    var courseNameElement = null;
                    var courseCodeElement = null;
                    var row = cells[0] ? cells[0].parentNode : null;

                    for (var i = 0; i < cells.length; i++) {
                        allTokens = allTokens.concat(getTokens(cells[i], false));
                        
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
                    
                    if (courseCodeElement) {
                        classCode = (courseCodeElement.innerText || '').trim();
                    } else {
                        for (var t = 0; t < allTokens.length; t++) {
                            if (!classCode && (/^[A-Z0-9\.]+$/.test(allTokens[t]) && /\d{4}/.test(allTokens[t]))) {
                                classCode = allTokens[t];
                            } else if (!classCode && /^\d{4,}$/.test(allTokens[t])) {
                                classCode = allTokens[t];
                            }
                        }
                    }
                    
                    if (courseNameElement) {
                        courseName = (courseNameElement.innerText || '').trim();
                    }
                    
                    var teacher = row ? extractTeachers(row, courseName) : '';
                    
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

                    var allTokens = getTokens(card);
                    
                    if (allTokens.length === 0 && !extCourseName && !extCourseCode) return null;
                    
                    var classCode = extCourseCode;
                    if (!classCode) {
                        for (var t = 0; t < allTokens.length; t++) {
                            if (!classCode && (/^[A-Z0-9\.]+$/.test(allTokens[t]) && /\d{4}/.test(allTokens[t]))) {
                                classCode = allTokens[t];
                            } else if (!classCode && /^\d{4,}$/.test(allTokens[t])) {
                                classCode = allTokens[t];
                            }
                        }
                    }
                    
                    var courseName = extCourseName;
                    var teacher = extractTeachers(card, courseName);
                    
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

                    var allTokens = getTokens(el);
                    
                    if (allTokens.length === 0 && !extCourseName && !extCourseCode) return null;
                    if (allTokens.length < 2 && !extCourseName && !extCourseCode) return null;
                    
                    var classCode = extCourseCode;
                    if (!classCode) {
                        for (var t = 0; t < allTokens.length; t++) {
                            if (!classCode && (/^[A-Z0-9\.]+$/.test(allTokens[t]) && /\d{4}/.test(allTokens[t]))) {
                                classCode = allTokens[t];
                            } else if (!classCode && /^\d{4,}$/.test(allTokens[t])) {
                                classCode = allTokens[t];
                            }
                        }
                    }
                    
                    var courseName = extCourseName;
                    var teacher = extractTeachers(el, courseName);
                    
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
