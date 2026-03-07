package com.ustc.vacancychecker.data.remote

/**
 * 选课页面 JS 脚本工具类
 * 
 * 注意：这些脚本中的 DOM 选择器是框架级占位符。
 * 需要用户提供选课页面截图后，根据实际 DOM 结构完善。
 */
object CourseCheckScriptUtils {

    /**
     * 检测是否存在"进入选课"按钮，如存在则点击
     * 通过 AndroidBridge.onEnterCourseSelectResult(found) 回调结果
     */
    fun getCheckEnterButtonScript(): String {
        return """
            (function() {
                var attempts = 0;
                var maxAttempts = 20;
                
                function findAndClickEnterButton() {
                    // 查找"进入选课"按钮
                    var buttons = document.querySelectorAll('a, button, span');
                    var enterBtn = null;
                    
                    for (var i = 0; i < buttons.length; i++) {
                        var text = (buttons[i].innerText || buttons[i].textContent || '').trim();
                        if (text.indexOf('进入选课') !== -1) {
                            enterBtn = buttons[i];
                            break;
                        }
                    }
                    
                    if (enterBtn) {
                        console.log("Found '进入选课' button, clicking...");
                        enterBtn.click();
                        try { AndroidBridge.onEnterCourseSelectResult(true); } catch(e) {}
                        return true;
                    }
                    
                    return false;
                }
                
                // 轮询查找按钮
                var intervalId = setInterval(function() {
                    attempts++;
                    if (findAndClickEnterButton()) {
                        clearInterval(intervalId);
                    } else if (attempts >= maxAttempts) {
                        clearInterval(intervalId);
                        // 超时未找到"进入选课"按钮，说明不在选课时间内
                        console.log("'进入选课' button not found after timeout");
                        try { AndroidBridge.onEnterCourseSelectResult(false); } catch(e) {}
                    }
                }, 500);
                
                // 立即尝试一次
                findAndClickEnterButton();
            })();
        """.trimIndent()
    }

    /**
     * 点击"全部课程"选项卡，在搜索框输入课堂号并搜索
     * @param classCode 课堂号
     */
    fun getSearchCourseScript(classCode: String): String {
        val safeCode = classCode.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'")
        
        return """
            (function() {
                var attempts = 0;
                var maxAttempts = 30;
                
                // 使用原生 setter 设置值
                var nativeSetter = Object.getOwnPropertyDescriptor(
                    window.HTMLInputElement.prototype, 'value'
                ).set;
                
                function setNativeValue(element, value) {
                    nativeSetter.call(element, value);
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                    element.dispatchEvent(new Event('change', { bubbles: true }));
                    element.dispatchEvent(new Event('keyup', { bubbles: true }));
                }
                
                function trySearchCourse() {
                    // Step 1: 点击"全部课程"选项卡
                    // TODO: 根据实际页面 DOM 结构更新选择器
                    var tabs = document.querySelectorAll('.tab, .nav-tab, [role="tab"], a, span');
                    var allCoursesTab = null;
                    for (var i = 0; i < tabs.length; i++) {
                        var text = (tabs[i].innerText || tabs[i].textContent || '').trim();
                        if (text === '全部课程') {
                            allCoursesTab = tabs[i];
                            break;
                        }
                    }
                    
                    if (allCoursesTab) {
                        console.log("Found '全部课程' tab, clicking...");
                        allCoursesTab.click();
                    } else {
                        console.log("'全部课程' tab not found yet");
                        return false;
                    }
                    
                    // Step 2: 延迟后在搜索框中输入课堂号
                    setTimeout(function() {
                        // TODO: 根据实际页面 DOM 结构更新选择器
                        var searchInput = document.querySelector('input[placeholder*="关键词"]')
                            || document.querySelector('input[placeholder*="搜索"]')
                            || document.querySelector('input[type="search"]')
                            || document.querySelector('.search-input input')
                            || document.querySelector('input.form-control');
                        
                        if (searchInput) {
                            console.log("Found search input, typing class code: $safeCode");
                            setNativeValue(searchInput, "$safeCode");
                            
                            // 触发搜索（模拟回车或点击搜索按钮）
                            setTimeout(function() {
                                searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, bubbles: true }));
                                searchInput.dispatchEvent(new KeyboardEvent('keypress', { key: 'Enter', keyCode: 13, bubbles: true }));
                                searchInput.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', keyCode: 13, bubbles: true }));
                                
                                // 也尝试点击搜索按钮
                                var searchBtn = document.querySelector('.search-btn, button[type="submit"], .btn-search');
                                if (searchBtn) searchBtn.click();
                                
                                console.log("Search triggered, notifying Android...");
                                try { AndroidBridge.onSearchComplete(); } catch(e) {}
                            }, 500);
                        } else {
                            console.log("Search input not found");
                            try { AndroidBridge.onCourseNotFound(); } catch(e) {}
                        }
                    }, 1000);
                    
                    return true;
                }
                
                var intervalId = setInterval(function() {
                    attempts++;
                    if (trySearchCourse() || attempts >= maxAttempts) {
                        clearInterval(intervalId);
                        if (attempts >= maxAttempts) {
                            try { AndroidBridge.onCourseNotFound(); } catch(e) {}
                        }
                    }
                }, 500);
            })();
        """.trimIndent()
    }

    /**
     * 在搜索结果中查找匹配课堂号的课程，读取已选/上限数据
     * @param classCode 课堂号
     * 通过 AndroidBridge.onVacancyResult(stdCount, limitCount) 回调结果
     * 通过 AndroidBridge.onCourseNotFound() 回调未找到
     */
    fun getReadVacancyScript(classCode: String): String {
        val safeCode = classCode.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'")
        
        return """
            (function() {
                var attempts = 0;
                var maxAttempts = 20;
                
                function tryReadVacancy() {
                    // TODO: 根据实际页面 DOM 结构更新选择器
                    // 查找包含课堂号的课程行
                    var rows = document.querySelectorAll('tr, .course-row, .course-item');
                    var targetRow = null;
                    
                    for (var i = 0; i < rows.length; i++) {
                        var rowText = rows[i].innerText || rows[i].textContent || '';
                        if (rowText.indexOf("$safeCode") !== -1) {
                            targetRow = rows[i];
                            break;
                        }
                    }
                    
                    if (!targetRow) {
                        if (attempts >= maxAttempts - 1) {
                            console.log("Course with code '$safeCode' not found");
                            try { AndroidBridge.onCourseNotFound(); } catch(e) {}
                        }
                        return false;
                    }
                    
                    console.log("Found course row for code: $safeCode");
                    
                    // 读取 std-count 和 limit-count
                    // TODO: 根据实际页面 DOM 结构更新选择器
                    var stdCountEl = targetRow.querySelector('.std-count, [data-std-count], .enrolled');
                    var limitCountEl = targetRow.querySelector('.limit-count, [data-limit-count], .capacity');
                    
                    if (stdCountEl && limitCountEl) {
                        var stdCount = parseInt(stdCountEl.innerText.trim()) || 0;
                        var limitCount = parseInt(limitCountEl.innerText.trim()) || 0;
                        console.log("Vacancy data: " + stdCount + "/" + limitCount);
                        try { AndroidBridge.onVacancyResult(stdCount, limitCount); } catch(e) {}
                        return true;
                    }
                    
                    // 备选方案：尝试从"已选/上限"格式的文本中解析
                    var cells = targetRow.querySelectorAll('td, .cell, span');
                    for (var j = 0; j < cells.length; j++) {
                        var cellText = (cells[j].innerText || '').trim();
                        var match = cellText.match(/(\d+)\s*\/\s*(\d+)/);
                        if (match) {
                            var stdCount = parseInt(match[1]);
                            var limitCount = parseInt(match[2]);
                            console.log("Vacancy data (parsed): " + stdCount + "/" + limitCount);
                            try { AndroidBridge.onVacancyResult(stdCount, limitCount); } catch(e) {}
                            return true;
                        }
                    }
                    
                    console.log("Could not find vacancy data in course row");
                    if (attempts >= maxAttempts - 1) {
                        try { AndroidBridge.onCourseNotFound(); } catch(e) {}
                    }
                    return false;
                }
                
                var intervalId = setInterval(function() {
                    attempts++;
                    if (tryReadVacancy() || attempts >= maxAttempts) {
                        clearInterval(intervalId);
                    }
                }, 500);
                
                tryReadVacancy();
            })();
        """.trimIndent()
    }
}
