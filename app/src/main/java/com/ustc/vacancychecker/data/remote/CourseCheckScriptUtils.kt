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
     * 点击"全部课程"选项卡（仅在第一次搜索时使用）
     * 通过 AndroidBridge.onTabClicked(true/false) 回调结果
     */
    fun getClickAllCoursesTabScript(): String {
        return """
            (function() {
                var attempts = 0;
                var maxAttempts = 20;
                
                function tryClickTab() {
                    // 查找"全部课程"选项卡
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
                        // 延迟后通知完成
                        setTimeout(function() {
                            try { AndroidBridge.onTabClicked(true); } catch(e) {}
                        }, 500);
                        return true;
                    }
                    
                    return false;
                }
                
                var intervalId = setInterval(function() {
                    attempts++;
                    if (tryClickTab() || attempts >= maxAttempts) {
                        clearInterval(intervalId);
                        if (attempts >= maxAttempts) {
                            console.log("'全部课程' tab not found after timeout");
                            try { AndroidBridge.onTabClicked(false); } catch(e) {}
                        }
                    }
                }, 500);
            })();
        """.trimIndent()
    }
    
    /**
     * 在搜索框输入课堂号并搜索（不点击选项卡，直接替换搜索内容）
     * @param classCode 课堂号
     */
    fun getQuickSearchScript(classCode: String): String {
        val safeCode = classCode.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
        
        return """
            (function() {
                var attempts = 0;
                var maxAttempts = 20;
                
                // 使用原生 setter 设置值
                var nativeSetter = Object.getOwnPropertyDescriptor(
                    window.HTMLInputElement.prototype, 'value'
                ).set;
                
                function setNativeValue(element, value) {
                    element.focus();
                    
                    // 先尝试清理之前的内容
                    nativeSetter.call(element, '');
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                    
                    try {
                        var clearBtn = element.parentElement.querySelector('.el-input__clear, .ant-input-clear-icon, .clear-icon, i[class*="close"], i[class*="clear"]');
                        if (clearBtn && clearBtn.offsetWidth > 0) {
                            clearBtn.click();
                        }
                    } catch(e) {}
                    
                    // 重新填入新值
                    nativeSetter.call(element, value);
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                    element.dispatchEvent(new Event('change', { bubbles: true }));
                    element.dispatchEvent(new Event('keyup', { bubbles: true }));
                }
                
                function trySearchCourse() {
                    // 直接在搜索框中输入课堂号（不再点击选项卡）
                    var inputs = document.querySelectorAll('input[placeholder*="关键词"], input[placeholder*="搜索"], input[type="search"], .search-input input, input.form-control, input.el-input__inner');
                    var searchInput = null;
                    for (var i = 0; i < inputs.length; i++) {
                        if (inputs[i].offsetWidth > 0 || inputs[i].offsetHeight > 0 || inputs[i].getClientRects().length > 0) {
                            searchInput = inputs[i];
                            break;
                        }
                    }
                    
                    if (searchInput) {
                        console.log("Found search input, typing class code: $safeCode");
                        setNativeValue(searchInput, "$safeCode");
                        
                        // 触发搜索（模拟回车或点击搜索按钮）
                        setTimeout(function() {
                            searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, bubbles: true }));
                            searchInput.dispatchEvent(new KeyboardEvent('keypress', { key: 'Enter', keyCode: 13, bubbles: true }));
                            searchInput.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', keyCode: 13, bubbles: true }));
                            
                            // 也尝试点击搜索按钮
                            var buttons = document.querySelectorAll('.search-btn, button[type="submit"], .btn-search, button.el-button--primary, button.ant-btn-primary');
                            for (var b = 0; b < buttons.length; b++) {
                                 if (buttons[b].offsetWidth > 0) {
                                     var btnText = (buttons[b].innerText || '').trim();
                                     if (btnText === '查询' || btnText === '搜索' || !!buttons[b].querySelector('i[class*="search"]')) {
                                          buttons[b].click();
                                          break;
                                     } else if ((buttons[b].className || '').indexOf('search') !== -1) {
                                          buttons[b].click();
                                          break;
                                     }
                                 }
                            }
                            
                            console.log("Search triggered, notifying Android...");
                            try { AndroidBridge.onSearchComplete("$safeCode"); } catch(e) {}
                        }, 300);
                        
                        return true;
                    }
                    
                    return false;
                }
                
                var intervalId = setInterval(function() {
                    attempts++;
                    if (trySearchCourse() || attempts >= maxAttempts) {
                        clearInterval(intervalId);
                        if (attempts >= maxAttempts) {
                            console.log("Search input not found after timeout");
                            try { AndroidBridge.onSearchError("$safeCode", "未找到搜索框"); } catch(e) {}
                        }
                    }
                }, 500);
            })();
        """.trimIndent()
    }
    
    /**
     * 点击"全部课程"选项卡，在搜索框输入课堂号并搜索
     * @param classCode 课堂号
     * @deprecated 使用 getClickAllCoursesTabScript 和 getQuickSearchScript 替代
     */
    fun getSearchCourseScript(classCode: String): String {
        val safeCode = classCode.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
        
        return """
            (function() {
                var attempts = 0;
                var maxAttempts = 30;
                
                // 使用原生 setter 设置值
                var nativeSetter = Object.getOwnPropertyDescriptor(
                    window.HTMLInputElement.prototype, 'value'
                ).set;
                
                function setNativeValue(element, value) {
                    element.focus();
                    
                    // 先尝试清理之前的内容
                    nativeSetter.call(element, '');
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                    
                    try {
                        var clearBtn = element.parentElement.querySelector('.el-input__clear, .ant-input-clear-icon, .clear-icon, i[class*="close"], i[class*="clear"]');
                        if (clearBtn && clearBtn.offsetWidth > 0) {
                            clearBtn.click();
                        }
                    } catch(e) {}
                    
                    // 重新填入新值
                    nativeSetter.call(element, value);
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                    element.dispatchEvent(new Event('change', { bubbles: true }));
                    element.dispatchEvent(new Event('keyup', { bubbles: true }));
                }
                
                function trySearchCourse() {
                    // Step 1: 点击"全部课程"选项卡
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
                        var inputs = document.querySelectorAll('input[placeholder*="关键词"], input[placeholder*="搜索"], input[type="search"], .search-input input, input.form-control, input.el-input__inner');
                        var searchInput = null;
                        for (var i = 0; i < inputs.length; i++) {
                            if (inputs[i].offsetWidth > 0 || inputs[i].offsetHeight > 0 || inputs[i].getClientRects().length > 0) {
                                searchInput = inputs[i];
                                break;
                            }
                        }
                        
                        if (searchInput) {
                            console.log("Found search input, typing class code: $safeCode");
                            setNativeValue(searchInput, "$safeCode");
                            
                            // 触发搜索（模拟回车或点击搜索按钮）
                            setTimeout(function() {
                                searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, bubbles: true }));
                                searchInput.dispatchEvent(new KeyboardEvent('keypress', { key: 'Enter', keyCode: 13, bubbles: true }));
                                searchInput.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', keyCode: 13, bubbles: true }));
                                
                                // 也尝试点击搜索按钮
                                var buttons = document.querySelectorAll('.search-btn, button[type="submit"], .btn-search, button.el-button--primary, button.ant-btn-primary');
                                for (var b = 0; b < buttons.length; b++) {
                                     if (buttons[b].offsetWidth > 0) {
                                         var btnText = (buttons[b].innerText || '').trim();
                                         if (btnText === '查询' || btnText === '搜索' || !!buttons[b].querySelector('i[class*="search"]')) {
                                              buttons[b].click();
                                              break;
                                         } else if ((buttons[b].className || '').indexOf('search') !== -1) {
                                              buttons[b].click();
                                              break;
                                         }
                                     }
                                }
                                
                                console.log("Search triggered, notifying Android...");
                                try { AndroidBridge.onSearchComplete("$safeCode"); } catch(e) {}
                            }, 500);
                        } else {
                            console.log("Search input not found");
                            try { AndroidBridge.onCourseNotFound("$safeCode"); } catch(e) {}
                        }
                    }, 1000);
                    
                    return true;
                }
                
                var intervalId = setInterval(function() {
                    attempts++;
                    if (trySearchCourse() || attempts >= maxAttempts) {
                        clearInterval(intervalId);
                        if (attempts >= maxAttempts) {
                            try { AndroidBridge.onCourseNotFound("$safeCode"); } catch(e) {}
                        }
                    }
                }, 500);
            })();
        """.trimIndent()
    }

    /**
     * 在搜索结果中查找匹配课堂号的课程，读取已选/上限数据
     * @param classCode 课堂号
     * 通过 AndroidBridge.onVacancyResult(code, stdCount, limitCount, courseName, teacher) 回调结果
     * 通过 AndroidBridge.onCourseNotFound(code) 回调未找到
     */
    fun getReadVacancyScript(classCode: String): String {
        val safeCode = classCode.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
        
        return """
            (function() {
                var attempts = 0;
                var maxAttempts = 20;
                
                function tryReadVacancy() {
                    // TODO: 根据实际页面 DOM 结构更新选择器
                    // 查找包含课堂号的课程行（大小写不敏感）
                    var rows = document.querySelectorAll('tr, .course-row, .course-item');
                    var matchedRows = [];
                    var searchCode = "$safeCode".toLowerCase();
                    
                    // 收集所有匹配的行
                    for (var i = 0; i < rows.length; i++) {
                        var rowText = (rows[i].innerText || rows[i].textContent || '').toLowerCase();
                        if (rowText.indexOf(searchCode) !== -1) {
                            matchedRows.push(rows[i]);
                        }
                    }
                    
                    // 如果匹配到多个课程，优先选择"已选"的课程
                    var targetRow = null;
                    if (matchedRows.length > 1) {
                        console.log("Multiple courses matched code '$safeCode', count: " + matchedRows.length + ", selecting the one with '已选' status");
                        
                        // 遍历所有匹配的行，寻找"已选"的课程
                        for (var mi = 0; mi < matchedRows.length; mi++) {
                            var row = matchedRows[mi];
                            var allCells = row.querySelectorAll('td, .cell, span, div');
                            for (var ci = 0; ci < allCells.length; ci++) {
                                var cellText = (allCells[ci].innerText || allCells[ci].textContent || '').trim();
                                // 检测"已选中"、"已选"、"选中"等关键词
                                if (cellText === '已选中' || cellText === '已选' || cellText === '选中' || 
                                    cellText.indexOf('已选中') !== -1 || cellText.indexOf('(已选)') !== -1 ||
                                    cellText.indexOf('【已选】') !== -1 || cellText.indexOf('[已选]') !== -1) {
                                    targetRow = row;
                                    console.log("Found '已选中' row among multiple matches");
                                    break;
                                }
                            }
                            if (targetRow) break;
                            
                            // 也检测退课按钮
                            var allButtons = row.querySelectorAll('button, a, span, input[type="button"], div[onclick]');
                            for (var bi = 0; bi < allButtons.length; bi++) {
                                var btn = allButtons[bi];
                                var btnText = (btn.innerText || btn.textContent || btn.value || '').trim();
                                if ((btnText.indexOf('退课') !== -1 || btnText.indexOf('退选') !== -1) && btn.offsetWidth > 0) {
                                    targetRow = row;
                                    console.log("Found row with '退课' button among multiple matches");
                                    break;
                                }
                            }
                            if (targetRow) break;
                        }
                        
                        // 如果没有找到"已选"的课程，选择第一个
                        if (!targetRow) {
                            targetRow = matchedRows[0];
                            console.log("No '已选' row found, using first match");
                        }
                    } else if (matchedRows.length === 1) {
                        targetRow = matchedRows[0];
                    }
                    
                    if (!targetRow) {
                        if (attempts >= maxAttempts - 1) {
                            console.log("Course with code '$safeCode' not found");
                            try { AndroidBridge.onCourseNotFound("$safeCode"); } catch(e) {}
                        }
                        return false;
                    }
                    
                    console.log("Found course row for code: $safeCode");
                    
                    // 读取 std-count 和 limit-count
                    // TODO: 根据实际页面 DOM 结构更新选择器
                    var stdCountEl = targetRow.querySelector('.std-count, [data-std-count], .enrolled');
                    var limitCountEl = targetRow.querySelector('.limit-count, [data-limit-count], .capacity');
                    
                    // 读取课程名和教师
                    var courseNameEl = targetRow.querySelector('.course-name, [data-course-name]');
                    var courseName = courseNameEl ? (courseNameEl.innerText || '').trim() : "未命名课程";
                    var teacherEl = targetRow.querySelector('.teacher, [data-teacher]');
                    var teacher = teacherEl ? (teacherEl.innerText || '').trim() : "未知";
                    
                    // 检测是否有"选课"按钮（区分已选课程和未选课程）
                    var selectButton = targetRow.querySelector('button, a, span[onclick], input[type="button"]');
                    var hasSelectButton = false;
                    var isAlreadySelected = false;
                    
                    // 策略1: 检测选课状态字段（优先级最高）
                    var allCells = targetRow.querySelectorAll('td, .cell, span, div');
                    for (var ci = 0; ci < allCells.length; ci++) {
                        var cellText = (allCells[ci].innerText || allCells[ci].textContent || '').trim();
                        // 检测"已选中"、"已选"、"选中"等关键词
                        if (cellText === '已选中' || cellText === '已选' || cellText === '选中' || 
                            cellText.indexOf('已选中') !== -1 || cellText.indexOf('(已选)') !== -1 ||
                            cellText.indexOf('【已选】') !== -1 || cellText.indexOf('[已选]') !== -1) {
                            isAlreadySelected = true;
                            console.log("Detected '已选中' status in cell: " + cellText);
                            break;
                        }
                    }
                    
                    // 策略2: 检测退课按钮（如果策略1没有检测到）
                    if (!isAlreadySelected) {
                        var allButtons = targetRow.querySelectorAll('button, a, span, input[type="button"], div[onclick]');
                        for (var bi = 0; bi < allButtons.length; bi++) {
                            var btn = allButtons[bi];
                            var btnText = (btn.innerText || btn.textContent || btn.value || '').trim();
                            // 检测退课按钮
                            if ((btnText.indexOf('退课') !== -1 || btnText.indexOf('退选') !== -1) && btn.offsetWidth > 0) {
                                isAlreadySelected = true;
                                console.log("Detected '退课' button: " + btnText);
                                break;
                            }
                        }
                    }
                    
                    // 策略3: 检测选课按钮
                    if (!isAlreadySelected) {
                        var allButtons2 = targetRow.querySelectorAll('button, a, span, input[type="button"], div[onclick]');
                        for (var bi2 = 0; bi2 < allButtons2.length; bi2++) {
                            var btn2 = allButtons2[bi2];
                            var btnText2 = (btn2.innerText || btn2.textContent || btn2.value || '').trim();
                            // 检测选课按钮（排除退课按钮）
                            if ((btnText2.indexOf('选课') !== -1 || btnText2.indexOf('选') !== -1) && 
                                btnText2.indexOf('退') === -1 && btn2.offsetWidth > 0) {
                                hasSelectButton = true;
                                console.log("Detected '选课' button: " + btnText2);
                                break;
                            }
                        }
                    }
                    
                    // 输出调试信息
                    console.log("Detection result - isAlreadySelected: " + isAlreadySelected + ", hasSelectButton: " + hasSelectButton);
                    try { AndroidBridge.logDomInfo("Course row detection - isAlreadySelected: " + isAlreadySelected + ", hasSelectButton: " + hasSelectButton); } catch(e) {}

                    if (!courseNameEl || !teacherEl) {
                        var cellsList = targetRow.querySelectorAll('td');
                        if (cellsList.length > 7) {
                            if (courseName === "未命名课程" || courseName === "") courseName = (cellsList[3].innerText || '').trim();
                            if (teacher === "未知" || teacher === "") {
                                // 处理多个逗号分隔的教师，或者直接取 innerText
                                var teacherNames = [];
                                var teacherLinks = cellsList[6].querySelectorAll('a.click-teacher-info');
                                if (teacherLinks.length > 0) {
                                    for(var ti=0; ti<teacherLinks.length; ti++) {
                                        teacherNames.push((teacherLinks[ti].innerText || '').trim());
                                    }
                                    teacher = teacherNames.join(', ');
                                } else {
                                    teacher = (cellsList[6].innerText || '').trim();
                                }
                            }
                        }
                    }
                    if (!courseName) courseName = "未命名课程";
                    if (!teacher) teacher = "未知";
                    
                    if (stdCountEl && limitCountEl) {
                        var stdCount = parseInt(stdCountEl.innerText.trim()) || 0;
                        var limitCount = parseInt(limitCountEl.innerText.trim()) || 0;
                        console.log("Vacancy data: " + stdCount + "/" + limitCount + " name: " + courseName + " teacher: " + teacher + " hasSelectButton: " + hasSelectButton + " isAlreadySelected: " + isAlreadySelected);
                        try { AndroidBridge.onVacancyResult("$safeCode", stdCount, limitCount, courseName, teacher, hasSelectButton, isAlreadySelected); } catch(e) {}
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
                            console.log("Vacancy data (parsed): " + stdCount + "/" + limitCount + " name: " + courseName + " teacher: " + teacher + " hasSelectButton: " + hasSelectButton + " isAlreadySelected: " + isAlreadySelected);
                            try { AndroidBridge.onVacancyResult("$safeCode", stdCount, limitCount, courseName, teacher, hasSelectButton, isAlreadySelected); } catch(e) {}
                            return true;
                        }
                    }
                    
                    console.log("Could not find vacancy data in course row");
                    if (attempts >= maxAttempts - 1) {
                        try { AndroidBridge.onCourseNotFound("$safeCode"); } catch(e) {}
                    }
                    return false;
                }
                
                var intervalId = setInterval(function() {
                    attempts++;
                    if (tryReadVacancy() || attempts >= maxAttempts) {
                        clearInterval(intervalId);
                    }
                }, 500);
                
                if (tryReadVacancy()) {
                    clearInterval(intervalId);
                }
            })();
        """.trimIndent()
    }

    /**
     * 点击课程行的"选课"按钮
     * @param classCode 课堂号
     * 通过 AndroidBridge.onSelectButtonClickResult(success, message) 回调结果
     */
    fun getClickSelectButtonScript(classCode: String): String {
        val safeCode = classCode.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
        
        return """
            (function() {
                function logDom(msg) {
                    try { AndroidBridge.logDomInfo(msg); } catch(e) { console.log(msg); }
                }
                
                // 查找包含课堂号的课程行（大小写不敏感）
                var rows = document.querySelectorAll('tr, .course-row, .course-item');
                var matchedRows = [];
                var searchCode = "$safeCode".toLowerCase();
                
                // 收集所有匹配的行
                for (var i = 0; i < rows.length; i++) {
                    var rowText = (rows[i].innerText || rows[i].textContent || '').toLowerCase();
                    if (rowText.indexOf(searchCode) !== -1) {
                        matchedRows.push(rows[i]);
                    }
                }
                
                // 如果匹配到多个课程，报错提示
                if (matchedRows.length > 1) {
                    logDom("Multiple courses matched code '$safeCode', count: " + matchedRows.length);
                    try { AndroidBridge.onSelectButtonClickResult(false, "匹配到多个课堂，请检查课堂号"); } catch(e) {}
                    return;
                }
                
                if (matchedRows.length === 0) {
                    logDom("Course row not found for code: $safeCode");
                    try { AndroidBridge.onSelectButtonClickResult(false, "未找到课程"); } catch(e) {}
                    return;
                }
                
                var targetRow = matchedRows[0];
                
                // 查找"选课"按钮
                var buttons = targetRow.querySelectorAll('button, a, span[onclick], input[type="button"], div[onclick]');
                var selectBtn = null;
                
                for (var j = 0; j < buttons.length; j++) {
                    var btnText = (buttons[j].innerText || buttons[j].textContent || buttons[j].value || '').trim();
                    if (btnText.indexOf('选课') !== -1 && btnText.indexOf('退课') === -1 && buttons[j].offsetWidth > 0) {
                        selectBtn = buttons[j];
                        break;
                    }
                }
                
                if (!selectBtn) {
                    // 检查是否有"退课"按钮（表示已选）
                    var hasDropBtn = false;
                    for (var k = 0; k < buttons.length; k++) {
                        var btnText = (buttons[k].innerText || buttons[k].textContent || '').trim();
                        if (btnText.indexOf('退课') !== -1 && buttons[k].offsetWidth > 0) {
                            hasDropBtn = true;
                            break;
                        }
                    }
                    
                    if (hasDropBtn) {
                        logDom("Course already selected (has drop button)");
                        try { AndroidBridge.onSelectButtonClickResult(false, "已选课程"); } catch(e) {}
                    } else {
                        logDom("Select button not found");
                        try { AndroidBridge.onSelectButtonClickResult(false, "未找到选课按钮"); } catch(e) {}
                    }
                    return;
                }
                
                logDom("Found select button, clicking...");
                selectBtn.click();
                
                // 等待一段时间后检查结果
                setTimeout(function() {
                    try { AndroidBridge.onSelectButtonClickResult(true, "已点击选课按钮"); } catch(e) {}
                }, 500);
            })();
        """.trimIndent()
    }

    /**
     * 检测选课结果（成功/失败弹窗）
     * 通过 AndroidBridge.onSelectResult(success, message) 回调结果
     */
    fun getCheckSelectResultScript(): String {
        return """
            (function() {
                var attempts = 0;
                var maxAttempts = 20;
                var hasResult = false;
                
                function logDom(msg) {
                    try { AndroidBridge.logDomInfo(msg); } catch(e) { console.log(msg); }
                }
                
                /**
                 * 从弹窗文本中提取核心消息（去除标题和按钮文字）
                 */
                function extractCoreMessage(modal, isSelectSuccess) {
                    var modalText = (modal.innerText || modal.textContent || '').trim();
                    
                    // 移除常见按钮文字
                    var buttonPatterns = ['确定', '取消', '关闭', 'OK', 'Cancel', 'Close', '确认', '是', '否'];
                    var lines = modalText.split(/\n/);
                    var coreLines = [];
                    
                    for (var i = 0; i < lines.length; i++) {
                        var line = lines[i].trim();
                        if (line.length === 0) continue;
                        
                        // 跳过按钮文字
                        var isButton = false;
                        for (var j = 0; j < buttonPatterns.length; j++) {
                            if (line === buttonPatterns[j] || line === buttonPatterns[j] + ' ') {
                                isButton = true;
                                break;
                            }
                        }
                        if (isButton) continue;
                        
                        // 跳过标题（通常包含"提示"、"消息"、"系统"等）
                        if (line.indexOf('提示') !== -1 && line.length < 10) continue;
                        if (line.indexOf('消息') !== -1 && line.length < 10) continue;
                        if (line.indexOf('系统') !== -1 && line.length < 10) continue;
                        if (line.indexOf('选课结果') !== -1 && line.length < 10) continue;
                        
                        coreLines.push(line);
                    }
                    
                    // 如果提取到了核心内容，返回第一个有效行
                    if (coreLines.length > 0) {
                        return coreLines[0];
                    }
                    
                    // 如果是选课成功，返回简洁的成功信息
                    if (isSelectSuccess) {
                        return "选课成功";
                    }
                    
                    return modalText.substring(0, 50);
                }
                
                function tryCheckResult() {
                    if (hasResult) return true;
                    var successPatterns = ['选课成功', '成功', '已完成'];
                    var errorPatterns = ['选课失败', '失败', '错误', '已满', '冲突', '权限', '限制', '不符合', '不能', '无法'];
                    
                    // 查找弹窗/提示框
                    var modals = document.querySelectorAll(
                        '.modal, .dialog, .layui-layer, .alert, .toast, .message, ' +
                        '[role="alert"], [role="dialog"], ' +
                        '.ant-modal, .ant-message, .el-dialog, .el-message'
                    );
                    
                    for (var i = 0; i < modals.length; i++) {
                        var modal = modals[i];
                        if (modal.offsetWidth <= 0) continue;
                        
                        var modalText = (modal.innerText || modal.textContent || '').trim();
                        
                        // 检查成功消息
                        for (var s = 0; s < successPatterns.length; s++) {
                            if (modalText.indexOf(successPatterns[s]) !== -1) {
                                logDom("Select success detected: " + modalText);
                                hasResult = true;
                                
                                var coreMsg = extractCoreMessage(modal, true);
                                logDom("Core message: " + coreMsg);
                                
                                // 尝试点击确认按钮关闭弹窗
                                var confirmBtns = modal.querySelectorAll('button, a, input[type="button"]');
                                for (var ci = 0; ci < confirmBtns.length; ci++) {
                                    var btnText = (confirmBtns[ci].innerText || '').trim();
                                    if (btnText.indexOf('确定') !== -1 || btnText.indexOf('关闭') !== -1 || btnText === 'OK') {
                                        confirmBtns[ci].click();
                                        break;
                                    }
                                }
                                try { AndroidBridge.onSelectResult(true, coreMsg); } catch(e) {}
                                return true;
                            }
                        }
                        
                        // 检查错误消息
                        for (var e = 0; e < errorPatterns.length; e++) {
                            if (modalText.indexOf(errorPatterns[e]) !== -1) {
                                logDom("Select error detected: " + modalText);
                                hasResult = true;
                                
                                var coreMsg = extractCoreMessage(modal, false);
                                logDom("Core message: " + coreMsg);
                                
                                // 尝试点击确认按钮关闭弹窗
                                var confirmBtns = modal.querySelectorAll('button, a, input[type="button"]');
                                for (var ci = 0; ci < confirmBtns.length; ci++) {
                                    var btnText = (confirmBtns[ci].innerText || '').trim();
                                    if (btnText.indexOf('确定') !== -1 || btnText.indexOf('关闭') !== -1 || btnText === 'OK') {
                                        confirmBtns[ci].click();
                                        break;
                                    }
                                }
                                try { AndroidBridge.onSelectResult(false, coreMsg); } catch(e) {}
                                return true;
                            }
                        }
                    }
                    
                    return false;
                }
                
                var intervalId = setInterval(function() {
                    attempts++;
                    if (tryCheckResult()) {
                        clearInterval(intervalId);
                    } else if (attempts >= maxAttempts) {
                        clearInterval(intervalId);
                        logDom("No select result detected after timeout");
                        try { AndroidBridge.onSelectResult(false, "选课结果未知（超时）"); } catch(e) {}
                    }
                }, 500);
                
                // 立即检查一次
                tryCheckResult();
            })();
        """.trimIndent()
    }
}
