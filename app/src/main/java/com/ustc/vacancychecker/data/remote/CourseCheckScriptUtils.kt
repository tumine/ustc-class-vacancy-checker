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
     * 通过 AndroidBridge.onSearchComplete 回调结果
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
                // 选课页先把已选人数渲染为 0，再通过 /std-count 异步刷新。
                // 留出足够时间等待该请求完成，不能把初始占位值当成真实人数。
                var maxAttempts = 40;

                function readCount(element) {
                    if (!element) return null;

                    // textContent 不依赖布局计算，在不同版本的 Android WebView 中
                    // 比 innerText 更稳定；同时保留属性和值作为页面改版后的兜底。
                    var candidates = [
                        element.textContent,
                        element.innerText,
                        element.value,
                        element.getAttribute('data-std-count'),
                        element.getAttribute('data-limit-count'),
                        element.getAttribute('data-count'),
                        element.getAttribute('aria-label'),
                        element.getAttribute('title')
                    ];

                    for (var i = 0; i < candidates.length; i++) {
                        if (candidates[i] === null || candidates[i] === undefined) continue;
                        var normalized = String(candidates[i]).replace(/,/g, '').trim();
                        var match = normalized.match(/\d+/);
                        if (match) {
                            var value = Number(match[0]);
                            if (Number.isFinite(value) && value >= 0) return value;
                        }
                    }
                    return null;
                }

                function hasLoadedCountState(targetRow, stdCountEl, limitCountEl) {
                    var progressText = stdCountEl && stdCountEl.closest
                        ? stdCountEl.closest('.progress-text')
                        : null;
                    var progressBar = targetRow.querySelector('.std-count-progress');

                    // 当前教务页的表格会先输出 0/上限，异步人数接口成功后才给
                    // progress-text 添加 text-primary 或 text-danger。只有这一套
                    // 明确的异步结构存在时才要求状态标记，以兼容旧版页面。
                    var usesAsyncPlaceholder = !!progressText && !!progressBar &&
                        stdCountEl.classList.contains('std-count') &&
                        limitCountEl.classList.contains('limit-count');
                    if (!usesAsyncPlaceholder) return true;

                    return progressText.classList.contains('text-primary') ||
                        progressText.classList.contains('text-danger') ||
                        progressText.classList.contains('text-success') ||
                        progressText.classList.contains('text-warning');
                }
                
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
                    var courseNameEl = targetRow.querySelector('.course-name, .course-name-main, [data-course-name]');
                    var courseName = courseNameEl ? (courseNameEl.innerText || '').trim() : "未命名课程";
                    var teacherEl = targetRow.querySelector('.teacher, .teacher-name, .teacher-cell, [data-teacher]');
                    var teacher = teacherEl ? (teacherEl.innerText || '').trim() : "未知";
                    
                    // 检测是否有"选课"按钮（区分已选课程和未选课程）
                    var selectButton = targetRow.querySelector('button, a, span[onclick], input[type="button"]');
                    var hasSelectButton = false;
                    var isAlreadySelected = false;
                    var hasDropButton = false;
                    var hasSwitchButton = false;
                    
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
                                hasDropButton = true;
                                console.log("Detected '退课' button: " + btnText);
                                break;
                            }
                        }
                    }

                    // 无论是否已通过状态文字识别，都完整记录当前已选课堂的按钮组合。
                    var actionButtons = targetRow.querySelectorAll('button, a, span, input[type="button"], div[onclick]');
                    for (var abi = 0; abi < actionButtons.length; abi++) {
                        var actionButton = actionButtons[abi];
                        if (actionButton.offsetWidth <= 0) continue;
                        var actionText = (actionButton.innerText || actionButton.textContent || actionButton.value || '').trim();
                        if (actionText.indexOf('退课') !== -1 || actionText.indexOf('退选') !== -1) {
                            hasDropButton = true;
                        }
                        if (actionText.indexOf('换班') !== -1) {
                            hasSwitchButton = true;
                        }
                    }
                    try { AndroidBridge.onCourseActionButtons("$safeCode", hasDropButton, hasSwitchButton); } catch(e) {}
                    
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
                        if (!hasLoadedCountState(targetRow, stdCountEl, limitCountEl)) {
                            if (attempts === 0 || attempts % 5 === 0) {
                                var progressText = stdCountEl.closest ? stdCountEl.closest('.progress-text') : null;
                                var stateClasses = progressText ? progressText.className : '';
                                var pendingMessage = "Waiting for async std-count: raw=" +
                                    (stdCountEl.textContent || '') + "/" + (limitCountEl.textContent || '') +
                                    ", stateClasses=" + stateClasses + ", attempt=" + attempts;
                                console.log(pendingMessage);
                                try { AndroidBridge.logDomInfo(pendingMessage); } catch(e) {}
                            }
                            return false;
                        }

                        var stdCount = readCount(stdCountEl);
                        var limitCount = readCount(limitCountEl);
                        if (stdCount !== null && limitCount !== null) {
                            console.log("Vacancy data: " + stdCount + "/" + limitCount + " name: " + courseName + " teacher: " + teacher + " hasSelectButton: " + hasSelectButton + " isAlreadySelected: " + isAlreadySelected);
                            try { AndroidBridge.onVacancyResult("$safeCode", stdCount, limitCount, courseName, teacher, hasSelectButton, isAlreadySelected); } catch(e) {}
                            return true;
                        }
                    }
                    
                    // 备选方案：尝试从"已选/上限"格式的文本中解析
                    var cells = targetRow.querySelectorAll('td, .cell, span');
                    for (var j = 0; j < cells.length; j++) {
                        var cellText = (cells[j].textContent || cells[j].innerText || '').trim();
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
     * 仅在当前已选课堂没有“换班”按钮时点击“退课”。脚本自身再次执行安全校验，
     * 避免 Kotlin 层与页面 DOM 更新之间发生竞态而误退课。
     */
    fun getClickDropButtonScript(classCode: String): String {
        val safeCode = escapeJs(classCode)
        return """
            (function() {
                var code = "$safeCode".toLowerCase();
                var rows = Array.from(document.querySelectorAll('tr, .course-row, .course-item')).filter(function(row) {
                    return (row.innerText || row.textContent || '').toLowerCase().indexOf(code) !== -1;
                });
                if (rows.length !== 1) {
                    try { AndroidBridge.onDropButtonClickResult(false, "无法唯一定位当前已选课堂"); } catch(e) {}
                    return;
                }
                var buttons = Array.from(rows[0].querySelectorAll('button, a, span[onclick], input[type="button"], div[onclick]'))
                    .filter(function(el) { return el.offsetWidth > 0; });
                var switchButton = buttons.find(function(el) {
                    return (el.innerText || el.textContent || el.value || '').trim().indexOf('换班') !== -1;
                });
                if (switchButton) {
                    try { AndroidBridge.onDropButtonClickResult(false, "同时存在换班按钮，安全拒绝点击退课"); } catch(e) {}
                    return;
                }
                var dropButtons = buttons.filter(function(el) {
                    var text = (el.innerText || el.textContent || el.value || '').trim();
                    return text.indexOf('退课') !== -1 || text.indexOf('退选') !== -1;
                }).sort(function(a, b) {
                    var at = (a.innerText || a.textContent || a.value || '').trim();
                    var bt = (b.innerText || b.textContent || b.value || '').trim();
                    return at.length - bt.length;
                });
                if (dropButtons.length === 0) {
                    try { AndroidBridge.onDropButtonClickResult(false, "未找到退课按钮"); } catch(e) {}
                    return;
                }
                dropButtons[0].click();
                try { AndroidBridge.onDropButtonClickResult(true, "已点击当前课堂的退课按钮"); } catch(e) {}
            })();
        """.trimIndent()
    }

    /** 确认退课，并等待当前课堂的退课按钮消失。 */
    fun getConfirmDropResultScript(classCode: String): String {
        val safeCode = escapeJs(classCode)
        return """
            (function() {
                var attempts = 0;
                var confirmed = false;
                var code = "$safeCode".toLowerCase();
                function visible(el) { return !!el && el.offsetWidth > 0 && el.offsetHeight > 0; }
                function poll() {
                    attempts++;
                    var dialogs = Array.from(document.querySelectorAll('.modal, .dialog, .layui-layer, [role="dialog"], .ant-modal, .el-dialog'))
                        .filter(visible);
                    for (var i = 0; i < dialogs.length; i++) {
                        var text = (dialogs[i].innerText || dialogs[i].textContent || '').trim();
                        if (/失败|错误|不能|无法|禁止/.test(text)) {
                            try { AndroidBridge.onDropConfirmResult(false, text); } catch(e) {}
                            return;
                        }
                        if (!confirmed) {
                            var confirm = Array.from(dialogs[i].querySelectorAll('button, a, input[type="button"]')).find(function(el) {
                                var t = (el.innerText || el.textContent || el.value || '').trim();
                                return visible(el) && (t === '确定' || t === '确认' || t.indexOf('确认退课') !== -1);
                            });
                            if (confirm) { confirmed = true; confirm.click(); }
                        }
                    }
                    var rows = Array.from(document.querySelectorAll('tr, .course-row, .course-item')).filter(function(row) {
                        return (row.innerText || row.textContent || '').toLowerCase().indexOf(code) !== -1;
                    });
                    if (rows.length === 1) {
                        var hasDrop = Array.from(rows[0].querySelectorAll('button, a, span, input[type="button"], div[onclick]')).some(function(el) {
                            var t = (el.innerText || el.textContent || el.value || '').trim();
                            return visible(el) && (t.indexOf('退课') !== -1 || t.indexOf('退选') !== -1);
                        });
                        if (!hasDrop) {
                            try { AndroidBridge.onDropConfirmResult(true, "退课已确认"); } catch(e) {}
                            return;
                        }
                    }
                    if (attempts >= 30) {
                        try { AndroidBridge.onDropConfirmResult(false, "退课后页面状态未发生变化"); } catch(e) {}
                        return;
                    }
                    setTimeout(poll, 500);
                }
                poll();
            })();
        """.trimIndent()
    }

    /** 同时存在“退课”和“换班”时，只点击“换班”并选择“单课换班”。 */
    fun getClickSingleCourseSwitchScript(classCode: String): String {
        val safeCode = escapeJs(classCode)
        return """
            (function() {
                var code = "$safeCode".toLowerCase();
                function visible(el) { return !!el && el.offsetWidth > 0 && el.offsetHeight > 0; }
                function textOf(el) {
                    return (el && (el.innerText || el.textContent || el.value) || '').trim();
                }
                function exactSingleCourseSwitch(el) {
                    return textOf(el).replace(/\s/g, '') === '单课换班';
                }
                var rows = Array.from(document.querySelectorAll('tr, .course-row, .course-item')).filter(function(row) {
                    return (row.innerText || row.textContent || '').toLowerCase().indexOf(code) !== -1;
                });
                if (rows.length !== 1) {
                    try { AndroidBridge.onSingleCourseSwitchResult(false, "无法唯一定位当前已选课堂"); } catch(e) {}
                    return;
                }
                var buttons = Array.from(rows[0].querySelectorAll('button, a, span[onclick], input[type="button"], div[onclick]')).filter(visible);
                var hasDrop = buttons.some(function(el) {
                    var t = (el.innerText || el.textContent || el.value || '').trim();
                    return t.indexOf('退课') !== -1 || t.indexOf('退选') !== -1;
                });
                var switches = buttons.filter(function(el) {
                    return (el.innerText || el.textContent || el.value || '').trim().indexOf('换班') !== -1;
                }).sort(function(a, b) {
                    return (a.innerText || a.textContent || '').trim().length - (b.innerText || b.textContent || '').trim().length;
                });
                if (!hasDrop || switches.length === 0) {
                    try { AndroidBridge.onSingleCourseSwitchResult(false, "当前课堂未同时出现退课和换班按钮"); } catch(e) {}
                    return;
                }
                // 此分支绝不读取或点击 dropButtons。
                var switchButton = switches[0];
                var buttonGroup = switchButton.closest
                    ? switchButton.closest('.btn-group, .dropdown, .ivu-dropdown, .el-dropdown')
                    : null;
                switchButton.click();
                var attempts = 0;

                function findMenuLink(root) {
                    if (!root || !root.querySelectorAll) return null;

                    // 教务页的 Bootstrap 下拉菜单结构是 li > a。不能把文字同样为
                    // “单课换班”的 li 当作点击目标；HTMLElement.click() 不会把点击
                    // 转发给它的子链接，因此旧实现会回报已点击、页面却没有跳转。
                    var directLinks = Array.from(root.querySelectorAll(
                        '.dropdown-menu a, .dropdown-menu button, ' +
                        'a.dropdown-item, button.dropdown-item, ' +
                        'a[role="menuitem"], button[role="menuitem"], ' +
                        '[role="menuitem"] a, [role="menuitem"] button'
                    ));
                    var direct = directLinks.find(function(el) {
                        return visible(el) && exactSingleCourseSwitch(el);
                    });
                    if (direct) return direct;

                    // 兼容菜单项本身承担点击事件的组件库，但仍优先返回其中真正的
                    // a/button，避免再次点击到仅用于布局的 li 容器。
                    var interactiveItems = Array.from(root.querySelectorAll('[role="menuitem"], [onclick]'));
                    for (var i = 0; i < interactiveItems.length; i++) {
                        var item = interactiveItems[i];
                        if (!visible(item) || !exactSingleCourseSwitch(item)) continue;
                        var child = Array.from(item.querySelectorAll('a, button')).find(function(el) {
                            return visible(el) && exactSingleCourseSwitch(el);
                        });
                        if (child) return child;
                        if (item.tagName === 'A' || item.tagName === 'BUTTON' || item.hasAttribute('onclick')) {
                            return item;
                        }
                    }
                    return null;
                }

                function clickMenu() {
                    attempts++;
                    // 先在刚点击的按钮组内查找，防止页面中其他课堂的隐藏菜单同名项
                    // 被误选；组件把浮层挂到 body 时再使用全页面兜底。
                    var item = findMenuLink(buttonGroup) || findMenuLink(document);
                    if (item) {
                        item.click();
                        try {
                            AndroidBridge.onSingleCourseSwitchResult(
                                true,
                                "已点击换班按钮和单课换班链接"
                            );
                        } catch(e) {}
                        return;
                    }
                    if (attempts >= 20) {
                        try { AndroidBridge.onSingleCourseSwitchResult(false, "未找到单课换班菜单项"); } catch(e) {}
                        return;
                    }
                    setTimeout(clickMenu, 250);
                }
                setTimeout(clickMenu, 200);
            })();
        """.trimIndent()
    }

    /**
     * 一次性抓取“单课换班”表格中的全部课程。浏览器端只做严格解析，不判断可用性、
     * 不点击申请；选中人数、待审核人数和课堂容量的统一评估由 Kotlin 完成。
     */
    fun getReadAdjustmentCourseTableScript(): String = """
        (function() {
            var expectedSeatHeader = '选中/选课上限/课堂容量/待审核人数';
            var collectedCourses = [];
            var visitedPageSignatures = {};
            var pageCount = 0;
            var initialAttempts = 0;
            var completed = false;

            function visible(el) {
                return !!el && el.offsetWidth > 0 && el.offsetHeight > 0;
            }
            function normalizeHeader(text) {
                return String(text || '').replace(/[\s\u00a0]+/g, '').replace(/／/g, '/');
            }
            function normalizeCode(text) {
                return String(text || '').replace(/[\s\u00a0]+/g, '').trim();
            }
            function parseSeatCell(rawText) {
                var normalized = normalizeHeader(rawText);
                var match = normalized.match(/^(\d+)\/(\d+)\/(\d+)\/(\d+)$/);
                if (!match) {
                    return {
                        rawSeatText: String(rawText || '').trim(),
                        selectedCount: null,
                        selectionLimit: null,
                        classroomCapacity: null,
                        pendingCount: null,
                        parseError: '席位字段必须严格符合“非负整数/非负整数/非负整数/非负整数”'
                    };
                }
                return {
                    rawSeatText: String(rawText || '').trim(),
                    selectedCount: Number(match[1]),
                    selectionLimit: Number(match[2]),
                    classroomCapacity: Number(match[3]),
                    pendingCount: Number(match[4]),
                    parseError: null
                };
            }
            function findTableInfo() {
                var tables = Array.from(document.querySelectorAll('table')).filter(visible);
                for (var ti = 0; ti < tables.length; ti++) {
                    var headerRows = Array.from(tables[ti].querySelectorAll('thead tr, tr'));
                    for (var hi = 0; hi < headerRows.length; hi++) {
                        var headers = Array.from(headerRows[hi].querySelectorAll('th, td'));
                        var seatIndex = headers.findIndex(function(cell) {
                            return normalizeHeader(cell.textContent || cell.innerText) === expectedSeatHeader;
                        });
                        if (seatIndex < 0) continue;
                        var codeIndex = headers.findIndex(function(cell) {
                            var text = normalizeHeader(cell.textContent || cell.innerText);
                            return text === '课堂号' || text === '教学班号' || text === '课堂代码' ||
                                text.indexOf('课堂号') !== -1 || text.indexOf('教学班号') !== -1;
                        });
                        if (codeIndex < 0) {
                            return { error: '已找到席位表头，但未找到课堂号表头' };
                        }
                        return {
                            table: tables[ti],
                            headerRow: headerRows[hi],
                            seatIndex: seatIndex,
                            codeIndex: codeIndex
                        };
                    }
                }
                return null;
            }
            function dataRows(info) {
                return Array.from(info.table.querySelectorAll('tbody tr, tr')).filter(function(row) {
                    if (row === info.headerRow || !visible(row)) return false;
                    return Array.from(row.children).some(function(cell) {
                        return cell.tagName && cell.tagName.toLowerCase() === 'td';
                    });
                });
            }
            function applyButtonIn(row) {
                return Array.from(row.querySelectorAll('button, a, span[onclick], input[type="button"]')).find(function(el) {
                    var text = (el.innerText || el.textContent || el.value || '').trim();
                    return visible(el) && (text === '申请' ||
                        (text.indexOf('申请') !== -1 && text.indexOf('已申请') === -1));
                }) || null;
            }
            function pageSignature(info, rows) {
                return rows.map(function(row) {
                    var cells = Array.from(row.children);
                    return normalizeCode(cells[info.codeIndex] && cells[info.codeIndex].textContent) + ':' +
                        normalizeHeader(cells[info.seatIndex] && cells[info.seatIndex].textContent);
                }).join('|');
            }
            function readCurrentPage(info, rows) {
                rows.forEach(function(row) {
                    var cells = Array.from(row.children);
                    var codeCell = cells[info.codeIndex];
                    var seatCell = cells[info.seatIndex];
                    var classCode = normalizeCode(codeCell && (codeCell.textContent || codeCell.innerText));
                    var parsed = parseSeatCell(seatCell && (seatCell.textContent || seatCell.innerText));
                    collectedCourses.push({
                        classCode: classCode,
                        rawSeatText: parsed.rawSeatText,
                        selectedCount: parsed.selectedCount,
                        selectionLimit: parsed.selectionLimit,
                        classroomCapacity: parsed.classroomCapacity,
                        pendingCount: parsed.pendingCount,
                        hasApplyButton: !!applyButtonIn(row),
                        parseError: classCode ? parsed.parseError : '课堂号为空'
                    });
                });
            }
            function findNextPageButton() {
                var selectors = [
                    '.el-pagination .btn-next:not([disabled])',
                    '.ant-pagination-next:not(.ant-pagination-disabled) button',
                    '.ant-pagination-next:not(.ant-pagination-disabled) a',
                    '.pagination .next:not(.disabled) a',
                    'button[aria-label="下一页"]:not([disabled])',
                    'a[aria-label="下一页"]'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var button = document.querySelector(selectors[i]);
                    if (button && visible(button) &&
                        button.getAttribute('aria-disabled') !== 'true' &&
                        !(button.closest('.disabled, .is-disabled, .ant-pagination-disabled'))) {
                        return button;
                    }
                }
                return null;
            }
            function finish(error) {
                if (completed) return;
                completed = true;
                var payload = { courses: collectedCourses, error: error || null };
                try { AndroidBridge.onAdjustmentCourseTableResult(JSON.stringify(payload)); } catch(e) {}
            }
            function waitForPageChange(previousSignature, attempts) {
                var info = findTableInfo();
                if (info && !info.error) {
                    var rows = dataRows(info);
                    var signature = pageSignature(info, rows);
                    if (rows.length > 0 && signature && signature !== previousSignature) {
                        collectCurrentPage();
                        return;
                    }
                }
                if (attempts >= 40) {
                    finish('翻页后表格内容未在规定时间内更新');
                    return;
                }
                setTimeout(function() { waitForPageChange(previousSignature, attempts + 1); }, 250);
            }
            function collectCurrentPage() {
                var info = findTableInfo();
                if (!info) {
                    if (initialAttempts++ >= 40) finish('未找到“' + expectedSeatHeader + '”表格');
                    else setTimeout(collectCurrentPage, 500);
                    return;
                }
                if (info.error) {
                    finish(info.error);
                    return;
                }
                var rows = dataRows(info);
                if (rows.length === 0) {
                    if (initialAttempts++ >= 40) finish('换班表格中没有可读取的课程行');
                    else setTimeout(collectCurrentPage, 500);
                    return;
                }
                var signature = pageSignature(info, rows);
                if (!signature) {
                    finish('无法生成换班表格页签名');
                    return;
                }
                if (visitedPageSignatures[signature]) {
                    finish('检测到重复分页，已停止抓取以避免死循环');
                    return;
                }
                visitedPageSignatures[signature] = true;
                pageCount++;
                readCurrentPage(info, rows);
                var next = findNextPageButton();
                if (!next) {
                    finish(null);
                    return;
                }
                if (pageCount >= 100) {
                    finish('换班表格分页超过安全上限 100 页');
                    return;
                }
                next.click();
                setTimeout(function() { waitForPageChange(signature, 0); }, 300);
            }

            collectCurrentPage();
        })();
    """.trimIndent()

    /** 按本地选出的目标课堂重新读取四项快照；快照未变化时才点击该行“申请”。 */
    fun getClickAdjustmentApplyScript(
        targetClassCode: String,
        selectedCount: Int,
        selectionLimit: Int,
        classroomCapacity: Int,
        pendingCount: Int
    ): String {
        val safeCode = escapeJs(targetClassCode)
        return """
            (function() {
                var code = "$safeCode";
                var expectedSeatHeader = '选中/选课上限/课堂容量/待审核人数';
                var expectedCounts = [$selectedCount, $selectionLimit, $classroomCapacity, $pendingCount];
                var attempts = 0;
                var searchTriggered = false;
                function visible(el) { return !!el && el.offsetWidth > 0 && el.offsetHeight > 0; }
                function normalizeHeader(text) {
                    return String(text || '').replace(/[\s\u00a0]+/g, '').replace(/／/g, '/');
                }
                function normalizeCode(text) {
                    return String(text || '').replace(/[\s\u00a0]+/g, '').trim().toLowerCase();
                }
                function setNativeValue(el, value) {
                    var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    setter.call(el, value);
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                function findTableInfo() {
                    var tables = Array.from(document.querySelectorAll('table')).filter(visible);
                    for (var ti = 0; ti < tables.length; ti++) {
                        var headerRows = Array.from(tables[ti].querySelectorAll('thead tr, tr'));
                        for (var hi = 0; hi < headerRows.length; hi++) {
                            var headers = Array.from(headerRows[hi].querySelectorAll('th, td'));
                            var seatIndex = headers.findIndex(function(cell) {
                                return normalizeHeader(cell.textContent || cell.innerText) === expectedSeatHeader;
                            });
                            if (seatIndex < 0) continue;
                            var codeIndex = headers.findIndex(function(cell) {
                                var text = normalizeHeader(cell.textContent || cell.innerText);
                                return text === '课堂号' || text === '教学班号' || text === '课堂代码' ||
                                    text.indexOf('课堂号') !== -1 || text.indexOf('教学班号') !== -1;
                            });
                            if (codeIndex >= 0) return { table: tables[ti], headerRow: headerRows[hi], seatIndex: seatIndex, codeIndex: codeIndex };
                        }
                    }
                    return null;
                }
                function triggerSearch() {
                    if (searchTriggered) return;
                    searchTriggered = true;
                    var inputs = Array.from(document.querySelectorAll('input[type="text"], input:not([type])')).filter(visible);
                    var input = inputs.find(function(el) {
                        var placeholder = (el.placeholder || '').toLowerCase();
                        return placeholder.indexOf('课堂') !== -1 || placeholder.indexOf('课程') !== -1;
                    }) || inputs[0];
                    if (!input) return;
                    input.focus();
                    setNativeValue(input, code);
                    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, bubbles: true }));
                    var search = Array.from(document.querySelectorAll('button, a')).find(function(el) {
                        var text = (el.innerText || el.textContent || '').trim();
                        return visible(el) && (text === '搜索' || text === '查询');
                    });
                    if (search) search.click();
                }
                function verifyAndApply() {
                    attempts++;
                    triggerSearch();
                    var info = findTableInfo();
                    if (info) {
                        var rows = Array.from(info.table.querySelectorAll('tbody tr, tr')).filter(function(row) {
                            if (row === info.headerRow || !visible(row)) return false;
                            var cells = Array.from(row.children);
                            return normalizeCode(cells[info.codeIndex] && cells[info.codeIndex].textContent) === normalizeCode(code);
                        });
                        if (rows.length > 1) {
                            try { AndroidBridge.onAdjustmentApplyResult(false, '目标课堂在换班表格中不唯一'); } catch(e) {}
                            return;
                        }
                        if (rows.length === 1) {
                            var cells = Array.from(rows[0].children);
                            var rawSeatText = cells[info.seatIndex] && (cells[info.seatIndex].textContent || cells[info.seatIndex].innerText);
                            var match = normalizeHeader(rawSeatText).match(/^(\d+)\/(\d+)\/(\d+)\/(\d+)$/);
                            if (!match) {
                                try { AndroidBridge.onAdjustmentApplyResult(false, '点击申请前无法重新解析席位字段'); } catch(e) {}
                                return;
                            }
                            var actualCounts = [Number(match[1]), Number(match[2]), Number(match[3]), Number(match[4])];
                            var unchanged = actualCounts.every(function(value, index) { return value === expectedCounts[index]; });
                            if (!unchanged) {
                                try { AndroidBridge.onAdjustmentApplyResult(false, '点击申请前席位数据已变化，安全取消本次申请'); } catch(e) {}
                                return;
                            }
                            var apply = Array.from(rows[0].querySelectorAll('button, a, span[onclick], input[type="button"]')).find(function(el) {
                                var text = (el.innerText || el.textContent || el.value || '').trim();
                                return visible(el) && (text === '申请' ||
                                    (text.indexOf('申请') !== -1 && text.indexOf('已申请') === -1));
                            });
                            if (!apply) {
                                try { AndroidBridge.onAdjustmentApplyResult(false, '目标课堂未找到申请按钮'); } catch(e) {}
                                return;
                            }
                            apply.click();
                            try { AndroidBridge.onAdjustmentApplyResult(true, '已复核目标课堂席位快照并点击申请'); } catch(e) {}
                            return;
                        }
                    }
                    if (attempts >= 40) {
                        try { AndroidBridge.onAdjustmentApplyResult(false, '未能按目标课堂号唯一定位申请行'); } catch(e) {}
                        return;
                    }
                    setTimeout(verifyAndApply, 500);
                }
                verifyAndApply();
            })();
        """.trimIndent()
    }

    /** 填写申请原因并点击页面底部“提交”。 */
    fun getFillAndSubmitAdjustmentScript(reason: String = "同课程换班"): String {
        val safeReason = escapeJs(reason)
        return """
            (function() {
                var attempts = 0;
                function visible(el) { return !!el && el.offsetWidth > 0 && el.offsetHeight > 0; }
                function setValue(el, value) {
                    var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                    var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                    setter.call(el, value);
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                function fillAndSubmit() {
                    attempts++;
                    var labels = Array.from(document.querySelectorAll('label, .label, .form-label, td, th, span, div')).filter(function(el) {
                        return visible(el) && (el.innerText || el.textContent || '').indexOf('申请原因及学生本人签名') !== -1;
                    });
                    var input = null;
                    for (var i = 0; i < labels.length && !input; i++) {
                        var container = labels[i].closest('.form-group, .form-item, tr, .row') || labels[i].parentElement;
                        if (container) input = container.querySelector('textarea, input[type="text"]');
                    }
                    if (!input) input = Array.from(document.querySelectorAll('textarea')).find(visible);
                    if (input && visible(input)) {
                        setValue(input, "$safeReason");
                        var submits = Array.from(document.querySelectorAll('button, input[type="submit"], a')).filter(function(el) {
                            var t = (el.innerText || el.textContent || el.value || '').trim();
                            return visible(el) && t === '提交';
                        }).sort(function(a, b) { return b.getBoundingClientRect().top - a.getBoundingClientRect().top; });
                        if (submits.length > 0) {
                            submits[0].click();
                            try { AndroidBridge.onAdjustmentSubmitClicked(true, "已填写同课程换班并点击提交"); } catch(e) {}
                            return;
                        }
                    }
                    if (attempts >= 30) {
                        try { AndroidBridge.onAdjustmentSubmitClicked(false, "未找到申请原因文本框或页面底部提交按钮"); } catch(e) {}
                        return;
                    }
                    setTimeout(fillAndSubmit, 500);
                }
                fillAndSubmit();
            })();
        """.trimIndent()
    }

    /** 提交后任何可见弹窗都按完整文本报错；持续 10 秒无弹窗才进入待核验。 */
    fun getCheckAdjustmentSubmitOutcomeScript(): String = """
        (function() {
            var attempts = 0;
            function visible(el) { return !!el && el.offsetWidth > 0 && el.offsetHeight > 0; }
            function poll() {
                attempts++;
                var popups = Array.from(document.querySelectorAll(
                    '.modal, .dialog, .layui-layer, .alert, .toast, .message, [role="alert"], [role="dialog"], .ant-modal, .ant-message, .el-dialog, .el-message'
                )).filter(visible);
                if (popups.length > 0) {
                    var fullText = popups.map(function(el) { return (el.innerText || el.textContent || '').trim(); })
                        .filter(function(text) { return text.length > 0; }).join('\n---\n');
                    try { AndroidBridge.onAdjustmentSubmitOutcome(true, fullText || "提交后出现未知弹窗"); } catch(e) {}
                    return;
                }
                if (attempts >= 20) {
                    try { AndroidBridge.onAdjustmentSubmitOutcome(false, "换班提交成功、待核验"); } catch(e) {}
                    return;
                }
                setTimeout(poll, 500);
            }
            poll();
        })();
    """.trimIndent()

    private fun escapeJs(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

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
                var modalSelector = [
                    '.modal', '.modal-dialog', '.dialog', '.layui-layer', '.layui-layer-dialog',
                    '.alert', '.toast', '.message', '[role="alert"]', '[role="dialog"]', '[role="alertdialog"]',
                    '.ant-modal', '.ant-modal-wrap', '.ant-message-notice',
                    '.el-dialog', '.el-dialog__wrapper', '.el-message-box', '.el-message-box__wrapper', '.el-message',
                    '.ivu-modal', '.ivu-modal-wrap', '.ivu-message-notice'
                ].join(', ');
                var titleSelector = [
                    '.modal-title', '.dialog-title', '.layui-layer-title', '.ant-modal-title',
                    '.el-dialog__title', '.el-message-box__title',
                    '.ivu-modal-header-inner', '.ivu-modal-header'
                ].join(', ');
                var bodySelector = [
                    '.modal-body', '.dialog-body', '.layui-layer-content', '.ant-modal-body',
                    '.el-dialog__body', '.el-message-box__message', '.ivu-modal-body'
                ].join(', ');
                
                function logDom(msg) {
                    try { AndroidBridge.logDomInfo(msg); } catch(e) { console.log(msg); }
                }
                
                /**
                 * 从弹窗文本中提取核心消息（去除标题和按钮文字）
                 */
                function getText(element) {
                    return ((element && (element.innerText || element.textContent)) || '').trim();
                }

                function isVisible(element) {
                    if (!element) return false;
                    var style = window.getComputedStyle(element);
                    return element.offsetWidth > 0 && element.offsetHeight > 0 &&
                        style.display !== 'none' && style.visibility !== 'hidden';
                }

                function containsAny(text, patterns) {
                    for (var i = 0; i < patterns.length; i++) {
                        if (text.indexOf(patterns[i]) !== -1) return true;
                    }
                    return false;
                }

                function getModalTitle(modal, modalText) {
                    var titleElement = modal.querySelector(titleSelector);
                    if (titleElement) return getText(titleElement);

                    var lines = modalText.split(/\n/);
                    for (var i = 0; i < lines.length; i++) {
                        var line = lines[i].replace(/\s/g, '');
                        if (line === '选课结果') return lines[i].trim();
                    }
                    return '';
                }

                function isSelectResultModal(modal, modalText) {
                    return getModalTitle(modal, modalText).replace(/\s/g, '').indexOf('选课结果') !== -1;
                }

                function extractCoreMessage(modal, isSelectSuccess) {
                    var bodyElement = modal.querySelector(bodySelector);
                    var modalText = getText(bodyElement) || getText(modal);
                    
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

                function closeModal(modal) {
                    var confirmBtns = modal.querySelectorAll('button, a, input[type="button"], input[type="submit"]');
                    for (var i = 0; i < confirmBtns.length; i++) {
                        var btnText = (getText(confirmBtns[i]) || confirmBtns[i].value || '').trim();
                        if (btnText.indexOf('确定') !== -1 || btnText.indexOf('关闭') !== -1 || btnText === 'OK') {
                            confirmBtns[i].click();
                            return;
                        }
                    }
                }

                function reportResult(modal, success) {
                    hasResult = true;
                    var coreMsg = extractCoreMessage(modal, success);
                    logDom((success ? "Select success detected: " : "Select error detected: ") + coreMsg);
                    closeModal(modal);
                    try { AndroidBridge.onSelectResult(success, coreMsg); } catch(e) {}
                    return true;
                }
                
                function tryCheckResult() {
                    if (hasResult) return true;
                    var successPatterns = ['选课成功', '成功', '已完成'];
                    var errorPatterns = [
                        '选课失败', '失败', '错误', '已满', '满额', '冲突', '权限', '限制',
                        '不符合', '不能', '无法', '不允许', '超过', '上限'
                    ];
                    
                    // 查找弹窗/提示框
                    var modals = document.querySelectorAll(modalSelector);
                    
                    for (var i = 0; i < modals.length; i++) {
                        var modal = modals[i];
                        if (!isVisible(modal)) continue;

                        var modalText = getText(modal);
                        var bodyElement = modal.querySelector(bodySelector);
                        var messageText = getText(bodyElement) || modalText;
                        var hasResultTitle = isSelectResultModal(modal, modalText);

                        // 失败优先，避免失败原因中包含“成功”等字样时被误判。
                        if (containsAny(messageText, errorPatterns)) {
                            return reportResult(modal, false);
                        }

                        if (containsAny(messageText, successPatterns)) {
                            return reportResult(modal, true);
                        }

                        // 教务系统的失败原因不固定；标题已经明确为“选课结果”时，
                        // 未出现成功语义就应作为失败回传，而不是等待到超时。
                        if (hasResultTitle) {
                            return reportResult(modal, false);
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
