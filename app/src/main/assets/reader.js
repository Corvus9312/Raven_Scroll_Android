(function () {
  'use strict';

  const FONT_FAMILIES = {
    'lxgw':     "'LXGW WenKai TC', 'LXGW WenKai', serif",
    'serif':    "'Georgia', 'Noto Serif TC', 'STSong', 'SimSun', serif",
    'sans':     "'PingFang TC', 'Microsoft JhengHei', 'Noto Sans TC', sans-serif",
    'kaiti':    "'KaiTi', 'STKaiti', 'DFKai-SB', cursive, serif",
    'fangsong': "'FangSong', 'STFangsong', 'FangSong_GB2312', serif",
    'cutive':   "'Courier New', monospace",
  };

  let chapters = [];
  let fontSize = 14;
  let lineHeight = 1.3;
  let fontFamily = 'lxgw';
  let theme = 'dark';
  let currentUriKey = '';
  let activeChapterIdx = -1;
  let saveTimer = null;
  let scrollTimer = null;
  let nextFileRequested = false;
  let nextBookKey = '';
  let userHasScrolled = false;  // 防止未滾動就以 0 覆蓋既有進度

  const $ = (id) => document.getElementById(id);

  const nextBookBanner = $('next-book-banner');
  const btnNextBook    = $('btn-next-book');
  const sidebar        = $('sidebar');
  const sidebarOverlay = $('sidebar-overlay');
  const bookTitle      = $('book-title');
  const chapterList    = $('chapter-list');
  const chapterSearch  = $('chapter-search');
  const btnSidebar     = $('btn-sidebar');
  const btnClose       = $('btn-close-sidebar');
  const btnFontDec     = $('btn-font-dec');
  const btnFontInc     = $('btn-font-inc');
  const fontLabel      = $('font-label');
  const btnLhDec       = $('btn-lh-dec');
  const btnLhInc       = $('btn-lh-inc');
  const lhLabel        = $('lh-label');
  const fontSelect     = $('font-select');
  const btnTheme       = $('btn-theme');
  const progressLabel  = $('progress-label');
  const readerScroll   = $('reader-scroll');
  const contentEl      = $('content');
  const loadingEl      = $('loading');

  // Called from Android via evaluateJavascript
  window.loadContent = function ({ text, title, savedProgress, savedPercent, prefs, uriKey, nextBook }) {
    console.log('loadContent called, title=' + title + ', chars=' + (text ? text.length : 0));
    currentUriKey = uriKey || '';
    nextFileRequested = false;
    nextBookKey = '';
    userHasScrolled = false;
    nextBookBanner.style.display = 'none';

    fontSize   = (prefs && prefs.fontSize)   || 18;
    lineHeight = (prefs && prefs.lineHeight) || 2.1;
    fontFamily = (prefs && prefs.fontFamily) || 'serif';
    theme      = (prefs && prefs.theme)      || 'dark';

    applyFontSize();
    applyLineHeight();
    applyFont();
    applyTheme();

    bookTitle.textContent = title || '';
    chapters = detectChapters(text);
    renderText(text);
    buildChapterNav();

    loadingEl.style.display = 'none';
    contentEl.style.display = 'block';

    // Prefer percent-based restoration (cross-device compatible with VS Code).
    // Fall back to raw scrollTop only when percent is unavailable.
    const pct = (typeof savedPercent === 'number') ? savedPercent : 0;
    if (pct > 0) {
      requestAnimationFrame(() => {
        const max = readerScroll.scrollHeight - readerScroll.clientHeight;
        readerScroll.scrollTop = Math.round((pct / 100) * max);
        updateProgress();
        syncActiveChapter();
      });
    } else if (savedProgress > 0) {
      requestAnimationFrame(() => {
        readerScroll.scrollTop = savedProgress;
        updateProgress();
        syncActiveChapter();
      });
    } else {
      readerScroll.scrollTop = 0;
      updateProgress();
    }

    // Show next book banner if already at 95%+ when opening
    if (nextBook && nextBook.title && nextBook.uri) {
      window.showNextBook(nextBook.title, nextBook.uri);
    }
  };

  // Called from Android when next book is available
  window.showNextBook = function (name, key) {
    if (!name || !key) return;
    nextBookKey = key;
    btnNextBook.textContent = name;
    nextBookBanner.style.display = 'block';
  };

  // ── Chapter detection ───────────────────────────────────────────────────────
  const PATTERNS = [
    /^第[零○〇一二三四五六七八九十百千万億\d]+[章節节回篇卷幕]/,
    /^Chapter\s+\d+/i,
    /^(?:序[章言]|前言|後記|后记|尾聲|尾声|番外|楔子|引子|正文)[\s\S]{0,20}$/,
    /^\d{1,4}[.、。]\s*.{1,30}$/,
  ];

  function detectChapters(text) {
    const lines = text.split('\n');
    const result = [];
    for (let i = 0; i < lines.length; i++) {
      const trimmed = lines[i].trim();
      if (!trimmed || trimmed.length > 60) continue;
      for (const p of PATTERNS) {
        if (p.test(trimmed)) { result.push({ title: trimmed, lineIdx: i }); break; }
      }
    }
    return result;
  }

  // ── Render ──────────────────────────────────────────────────────────────────
  function renderText(text) {
    if (chapters.length === 0) { contentEl.textContent = text; return; }
    const lines = text.split('\n');
    const chapterLines = new Set(chapters.map(c => c.lineIdx));
    const parts = [];
    for (let i = 0; i < lines.length; i++) {
      const esc = escHtml(lines[i]);
      parts.push(chapterLines.has(i)
        ? `<span id="ch-${i}" class="chapter-line">${esc}</span>`
        : esc);
      parts.push('\n');
    }
    contentEl.innerHTML = parts.join('');
  }

  function escHtml(s) {
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  }

  // ── Chapter nav ─────────────────────────────────────────────────────────────
  function buildChapterNav() {
    chapterList.innerHTML = '';
    if (chapters.length === 0) {
      const li = document.createElement('li');
      li.className = 'no-chapters';
      li.textContent = '未偵測到章節';
      chapterList.appendChild(li);
      return;
    }
    const frag = document.createDocumentFragment();
    chapters.forEach((ch, idx) => {
      const li = document.createElement('li');
      li.textContent = ch.title;
      li.addEventListener('click', () => { jumpTo(idx); closeSidebar(); });
      frag.appendChild(li);
    });
    chapterList.appendChild(frag);
  }

  function jumpTo(idx) {
    const ch = chapters[idx];
    if (!ch) return;
    const el = document.getElementById('ch-' + ch.lineIdx);
    if (el) {
      const r = readerScroll.getBoundingClientRect();
      const target = readerScroll.scrollTop + (el.getBoundingClientRect().top - r.top) - 20;
      readerScroll.scrollTo({ top: Math.max(0, target), behavior: 'smooth' });
    }
    setActiveChapter(idx);
  }

  function setActiveChapter(idx) {
    if (idx === activeChapterIdx) return;
    const items = chapterList.querySelectorAll('li:not(.no-chapters)');
    if (activeChapterIdx >= 0 && items[activeChapterIdx])
      items[activeChapterIdx].classList.remove('active');
    if (items[idx]) {
      items[idx].classList.add('active');
      items[idx].scrollIntoView({ block: 'nearest' });
    }
    activeChapterIdx = idx;
  }

  function syncActiveChapter() {
    if (chapters.length === 0) return;
    const top = readerScroll.getBoundingClientRect().top;
    let best = 0;
    for (let i = 0; i < chapters.length; i++) {
      const el = document.getElementById('ch-' + chapters[i].lineIdx);
      if (el && el.getBoundingClientRect().top <= top + 80) best = i; else break;
    }
    setActiveChapter(best);
  }

  // ── Scroll ──────────────────────────────────────────────────────────────────
  readerScroll.addEventListener('scroll', () => {
    userHasScrolled = true;
    updateProgress();
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
      if (currentUriKey && window.AndroidBridge) {
        AndroidBridge.saveProgress(readerScroll.scrollTop, currentPercent());
      }
    }, 800);
    clearTimeout(scrollTimer);
    scrollTimer = setTimeout(syncActiveChapter, 160);

    if (!nextFileRequested && currentUriKey && currentPercent() >= 95) {
      nextFileRequested = true;
      // Android will call showNextBook() when next book is resolved
    }
  }, { passive: true });

  function currentPercent() {
    const { scrollTop, scrollHeight, clientHeight } = readerScroll;
    const max = scrollHeight - clientHeight;
    return max > 0 ? Math.min(100, Math.round((scrollTop / max) * 100)) : 0;
  }

  function updateProgress() {
    progressLabel.textContent = currentPercent() + '%';
  }

  // ── Chapter search ──────────────────────────────────────────────────────────
  chapterSearch.addEventListener('input', () => {
    const q = chapterSearch.value.toLowerCase();
    chapterList.querySelectorAll('li:not(.no-chapters)').forEach((li, i) => {
      li.style.display = (chapters[i]?.title || '').toLowerCase().includes(q) ? '' : 'none';
    });
  });

  // ── Toolbar ─────────────────────────────────────────────────────────────────
  btnSidebar.addEventListener('click', () => toggleSidebar());
  btnClose.addEventListener('click', closeSidebar);
  sidebarOverlay.addEventListener('click', closeSidebar);

  function toggleSidebar() { sidebar.classList.toggle('open'); sidebarOverlay.classList.toggle('show'); }
  function closeSidebar()  { sidebar.classList.remove('open'); sidebarOverlay.classList.remove('show'); }

  btnFontDec.addEventListener('click', () => {
    fontSize = Math.max(12, fontSize - 1);
    applyFontSize(); savePrefs();
  });
  btnFontInc.addEventListener('click', () => {
    fontSize = Math.min(40, fontSize + 1);
    applyFontSize(); savePrefs();
  });
  btnLhDec.addEventListener('click', () => {
    lineHeight = Math.max(1.2, +(lineHeight - 0.1).toFixed(1));
    applyLineHeight(); savePrefs();
  });
  btnLhInc.addEventListener('click', () => {
    lineHeight = Math.min(3.5, +(lineHeight + 0.1).toFixed(1));
    applyLineHeight(); savePrefs();
  });
  fontSelect.addEventListener('change', () => { fontFamily = fontSelect.value; applyFont(); savePrefs(); });
  btnTheme.addEventListener('click', () => {
    theme = theme === 'dark' ? 'light' : 'dark';
    applyTheme(); savePrefs();
  });

  btnNextBook.addEventListener('click', () => {
    if (nextBookKey && window.AndroidBridge) {
      AndroidBridge.openNextBook(nextBookKey);
    }
  });

  function applyFontSize()   { contentEl.style.fontSize = fontSize + 'px'; fontLabel.textContent = String(fontSize); }
  function applyLineHeight() { contentEl.style.lineHeight = String(lineHeight); lhLabel.textContent = lineHeight.toFixed(1); }
  function applyFont()       { contentEl.style.fontFamily = FONT_FAMILIES[fontFamily] || FONT_FAMILIES['serif']; fontSelect.value = fontFamily; }
  function applyTheme()      { document.body.classList.toggle('light', theme === 'light'); }

  function savePrefs() {
    if (window.AndroidBridge) {
      AndroidBridge.savePrefs(JSON.stringify({ fontSize, lineHeight, fontFamily, theme }));
    }
  }

  document.addEventListener('visibilitychange', () => {
    // 只有使用者實際滾動過才存進度，避免以 (0, 0) 覆蓋 VS Code 的既有進度
    if (document.hidden && currentUriKey && window.AndroidBridge && userHasScrolled) {
      AndroidBridge.saveProgress(readerScroll.scrollTop, currentPercent());
    }
  });

  // Pick up any payload stored by Android before this IIFE finished running
  if (window._pendingPayload) {
    console.log('Executing pending payload');
    window.loadContent(window._pendingPayload);
    window._pendingPayload = null;
  }
})();
