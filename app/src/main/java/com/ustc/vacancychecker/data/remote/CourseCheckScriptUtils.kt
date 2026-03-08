package com.ustc.vacancychecker.data.remote

/**
 * 选课页面 JS 脚本工具类
 * 
 * 注意：这些脚本中的 DOM 选择器是框架级占位符。
 * 需要用户提供选课页面截图后，根据实际 DOM 结构完善。
 */
object CourseCheckScriptUtils {

    /**
     * 检测是否存在"选课公告"弹窗，如存在则点击"确定"消除
     * 通过 AndroidBridge.onAnnouncementDismissed(found) 回调结果
     * @param found: true 表示找到并关闭了弹窗，false 表示未找到弹窗（可直接继续）
     */
    fun getDismissAnnouncementScript(): String {
        return """
            (function() {
                var attempts = 0;
                var maxAttempts = 30;
                var hasDumped = false;
                
                function logDom(msg) {
                    try { AndroidBridge.logDomInfo(msg); } catch(e) { console.log(msg); }
                }
                
                function isConfirmButton(text) {
                    var t = text.trim();
                    return t === '确定' || t === '确 定' || t === 'OK' || t === 'ok' 
                        || t === '知道了' || t === '我知道了' || t === '关闭'
                        || t.indexOf('确定') !== -1;
                }
                
                function dumpDomDiagnostics() {
                    if (hasDumped) return;
                    hasDumped = true;
                    
                    logDom('=== DOM DIAGNOSTICS START ===');
                    logDom('URL: ' + window.location.href);
                    logDom('Body children count: ' + document.body.children.length);
                    
                    // 1. 查找所有 fixed/absolute 定位的可见元素
                    var allEls = document.querySelectorAll('*');
                    var overlays = [];
                    for (var i = 0; i < allEls.length; i++) {
                        var el = allEls[i];
                        if (el.offsetWidth <= 0 || el.offsetHeight <= 0) continue;
                        var style = window.getComputedStyle(el);
                        if (style.position === 'fixed' || (style.position === 'absolute' && style.zIndex > 0)) {
                            overlays.push({
                                tag: el.tagName,
                                id: el.id,
                                className: (el.className && typeof el.className === 'string') ? el.className.substring(0, 100) : '',
                                text: (el.innerText || '').substring(0, 80).replace(/\n/g, ' '),
                                zIndex: style.zIndex,
                                size: el.offsetWidth + 'x' + el.offsetHeight
                            });
                        }
                    }
                    logDom('Fixed/Absolute overlays (' + overlays.length + '):');
                    for (var o = 0; o < overlays.length; o++) {
                        logDom('  [' + o + '] <' + overlays[o].tag + ' id="' + overlays[o].id + '" class="' + overlays[o].className + '"> z=' + overlays[o].zIndex + ' ' + overlays[o].size + ' text="' + overlays[o].text + '"');
                    }
                    
                    // 2. 查找包含"公告"文字的所有元素
                    var announcementEls = [];
                    for (var j = 0; j < allEls.length; j++) {
                        var el2 = allEls[j];
                        var directText = '';
                        for (var cn = 0; cn < el2.childNodes.length; cn++) {
                            if (el2.childNodes[cn].nodeType === 3) directText += el2.childNodes[cn].textContent;
                        }
                        if (directText.indexOf('公告') !== -1 || directText.indexOf('通知') !== -1) {
                            announcementEls.push({
                                tag: el2.tagName,
                                id: el2.id,
                                className: (el2.className && typeof el2.className === 'string') ? el2.className.substring(0, 100) : '',
                                text: directText.substring(0, 80)
                            });
                        }
                    }
                    logDom('Elements with "公告/通知" text (' + announcementEls.length + '):');
                    for (var a = 0; a < announcementEls.length; a++) {
                        logDom('  [' + a + '] <' + announcementEls[a].tag + ' id="' + announcementEls[a].id + '" class="' + announcementEls[a].className + '"> text="' + announcementEls[a].text + '"');
                    }
                    
                    // 3. 查找所有可见的按钮类元素
                    var btns = document.querySelectorAll('a, button, input[type="button"], input[type="submit"], span[onclick], div[onclick]');
                    var visibleBtns = [];
                    for (var k = 0; k < btns.length; k++) {
                        if (btns[k].offsetWidth > 0 && btns[k].offsetHeight > 0) {
                            var bText = (btns[k].innerText || btns[k].textContent || btns[k].value || '').trim();
                            if (bText.length > 0 && bText.length < 20) {
                                visibleBtns.push({
                                    tag: btns[k].tagName,
                                    id: btns[k].id,
                                    className: (btns[k].className && typeof btns[k].className === 'string') ? btns[k].className.substring(0, 80) : '',
                                    text: bText,
                                    outerHTML: btns[k].outerHTML.substring(0, 150)
                                });
                            }
                        }
                    }
                    logDom('Visible buttons (' + visibleBtns.length + '):');
                    for (var b = 0; b < visibleBtns.length; b++) {
                        logDom('  [' + b + '] <' + visibleBtns[b].tag + ' id="' + visibleBtns[b].id + '" class="' + visibleBtns[b].className + '"> text="' + visibleBtns[b].text + '"');
                        logDom('    html: ' + visibleBtns[b].outerHTML);
                    }
                    
                    // 4. 输出 body 直接子元素
                    logDom('Body direct children:');
                    for (var c = 0; c < document.body.children.length; c++) {
                        var child = document.body.children[c];
                        logDom('  [' + c + '] <' + child.tagName + ' id="' + child.id + '" class="' + ((child.className && typeof child.className === 'string') ? child.className.substring(0, 80) : '') + '"> visible=' + (child.offsetWidth > 0));
                    }
                    
                    logDom('=== DOM DIAGNOSTICS END ===');
                }
                
                function tryDismissAnnouncement() {
                    // 策略1: 直接查找常见弹窗确定按钮类名
                    var quickBtns = document.querySelectorAll(
                        '.layui-layer-btn0, .layui-layer-btn a:first-child, ' +
                        '.modal-footer .btn-primary, .modal-footer .btn-default, ' +
                        '.ant-modal-footer .ant-btn-primary, ' +
                        '.el-dialog__footer .el-button--primary, ' +
                        '.el-message-box__btns .el-button--primary, ' +
                        '.ivu-modal-footer .ivu-btn-primary'
                    );
                    for (var q = 0; q < quickBtns.length; q++) {
                        if (quickBtns[q].offsetWidth > 0) {
                            logDom("Strategy1: Found confirm button via quick selector, clicking...");
                            quickBtns[q].click();
                            try { AndroidBridge.onAnnouncementDismissed(true); } catch(e) {}
                            return true;
                        }
                    }
                    
                    // 策略2: 查找包含"公告"/"通知"的弹窗容器
                    var modals = document.querySelectorAll(
                        '.modal, .modal-dialog, .dialog, .layui-layer, .layui-layer-dialog, ' +
                        '.popup, [role="dialog"], [role="alertdialog"], ' +
                        '.ant-modal, .ant-modal-wrap, .el-dialog, .el-dialog__wrapper, .el-message-box__wrapper, ' +
                        '.ivu-modal, .ivu-modal-wrap'
                    );
                    
                    for (var i = 0; i < modals.length; i++) {
                        if (modals[i].offsetWidth <= 0) continue;
                        var modalText = modals[i].innerText || modals[i].textContent || '';
                        if (modalText.indexOf('公告') !== -1 || modalText.indexOf('通知') !== -1) {
                            logDom("Strategy2: Found popup with announcement text");
                            var clickables = modals[i].querySelectorAll('a, button, span, div, input[type="button"], input[type="submit"]');
                            for (var ci = 0; ci < clickables.length; ci++) {
                                var btnText = (clickables[ci].innerText || clickables[ci].textContent || '').trim();
                                if (isConfirmButton(btnText)) {
                                    logDom("Strategy2: Clicking button: '" + btnText + "'");
                                    clickables[ci].click();
                                    try { AndroidBridge.onAnnouncementDismissed(true); } catch(e) {}
                                    return true;
                                }
                            }
                        }
                    }
                    
                    // 策略3: 搜索页面上所有可见的"确定"类按钮（在弹窗/遮罩层内的）
                    var allBtns = document.querySelectorAll('a, button, input[type="button"], input[type="submit"]');
                    for (var j = 0; j < allBtns.length; j++) {
                        var btn = allBtns[j];
                        if (btn.offsetWidth <= 0 || btn.offsetHeight <= 0) continue;
                        var bText = (btn.innerText || btn.textContent || '').trim();
                        if (isConfirmButton(bText)) {
                            var parent = btn.parentElement;
                            while (parent && parent !== document.body) {
                                var style = window.getComputedStyle(parent);
                                var className = (parent.className && typeof parent.className === 'string') ? parent.className : '';
                                if (style.position === 'fixed' || style.position === 'absolute' ||
                                    className.indexOf('layer') !== -1 || className.indexOf('modal') !== -1 ||
                                    className.indexOf('dialog') !== -1 || className.indexOf('popup') !== -1 ||
                                    className.indexOf('mask') !== -1 || className.indexOf('overlay') !== -1) {
                                    logDom("Strategy3: Found confirm button in overlay: '" + bText + "'");
                                    btn.click();
                                    try { AndroidBridge.onAnnouncementDismissed(true); } catch(e) {}
                                    return true;
                                }
                                parent = parent.parentElement;
                            }
                        }
                    }
                    
                    return false;
                }
                
                var intervalId = setInterval(function() {
                    attempts++;
                    
                    // 在第5次尝试时输出DOM诊断信息
                    if (attempts === 5) {
                        dumpDomDiagnostics();
                    }
                    
                    if (tryDismissAnnouncement()) {
                        clearInterval(intervalId);
                    } else if (attempts >= maxAttempts) {
                        clearInterval(intervalId);
                        logDom("Timeout: No announcement popup dismissed after " + maxAttempts + " attempts");
                        dumpDomDiagnostics();
                        try { AndroidBridge.onAnnouncementDismissed(false); } catch(e) {}
                    }
                }, 500);
                
                // 立即尝试一次
                tryDismissAnnouncement();
            })();
        """.trimIndent()
    }

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
