/* ==========================================================================
   AquaGuard · UI 基础工具（图标、提示、确认框、格式化、主题）
   ========================================================================== */
(function () {
  'use strict';

  const THEME_KEY = 'aquaguard.theme';

  /* ------------------------------ 基础工具 ------------------------------ */

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function icon(name, className) {
    return '<svg class="icon' + (className ? ' ' + className : '') + '" aria-hidden="true"><use href="#i-' +
      String(name).replace(/[^a-z0-9-]/gi, '') + '"></use></svg>';
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function debounce(fn, wait) {
    let timer = null;
    return function () {
      const args = arguments;
      const self = this;
      clearTimeout(timer);
      timer = setTimeout(function () { fn.apply(self, args); }, wait);
    };
  }

  function num(value, digits) {
    if (value === null || value === undefined || value === '' || Number.isNaN(Number(value))) return '--';
    const d = digits === undefined ? 2 : digits;
    return Number(value).toFixed(d);
  }

  function numberOr(value, fallback) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  /* ------------------------------ 时间格式 ------------------------------ */

  function pad(n) { return String(n).padStart(2, '0'); }

  function parseLocal(value) {
    if (!value) return null;
    const m = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(String(value));
    if (!m) return null;
    return {
      y: Number(m[1]), mo: Number(m[2]), d: Number(m[3]),
      h: Number(m[4]), mi: Number(m[5]), s: Number(m[6] || 0)
    };
  }

  function toDate(value) {
    const p = parseLocal(value);
    if (!p) return null;
    return new Date(p.y, p.mo - 1, p.d, p.h, p.mi, p.s);
  }

  function formatDate(value) {
    const p = parseLocal(value);
    return p ? p.y + '-' + pad(p.mo) + '-' + pad(p.d) : '--';
  }

  function formatTime(value) {
    const p = parseLocal(value);
    return p ? pad(p.h) + ':' + pad(p.mi) : '--';
  }

  function formatDateTime(value) {
    const p = parseLocal(value);
    return p ? formatDate(value) + ' ' + formatTime(value) : '--';
  }

  function formatDateTimeShort(value) {
    const p = parseLocal(value);
    return p ? pad(p.mo) + '-' + pad(p.d) + ' ' + pad(p.h) + ':' + pad(p.mi) : '--';
  }

  function formatDateLong(value) {
    const p = parseLocal(value);
    if (!p) return '--';
    const week = '日一二三四五六'.charAt(new Date(p.y, p.mo - 1, p.d).getDay());
    return p.y + ' 年 ' + p.mo + ' 月 ' + p.d + ' 日 · 周' + week;
  }

  function dateInputValue(value) {
    const p = parseLocal(value);
    return p ? p.y + '-' + pad(p.mo) + '-' + pad(p.d) + 'T' + pad(p.h) + ':' + pad(p.mi) : '';
  }

  function shiftMinutes(value, minutes) {
    const d = toDate(value);
    if (!d) return null;
    d.setMinutes(d.getMinutes() + Number(minutes || 0));
    return d;
  }

  function formatShifted(value, minutes, withDate) {
    const d = shiftMinutes(value, minutes);
    if (!d) return '--';
    return (withDate ? pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' : '') +
      pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  function formatElapsed(totalMinutes) {
    let m = Math.max(0, Math.round(Number(totalMinutes) || 0));
    if (m < 60) return m + ' 分钟';
    const h = Math.floor(m / 60);
    m = m % 60;
    if (h < 24) return h + ' 小时' + (m ? ' ' + m + ' 分' : '');
    const d = Math.floor(h / 24);
    return d + ' 天' + (h % 24 ? ' ' + (h % 24) + ' 小时' : '');
  }

  function speedHint(speed) {
    const v = Math.max(1, Number(speed) || 1);
    if (v < 60) return '1 真实秒 = ' + v + ' 模拟秒';
    if (v < 3600) return '1 真实秒 = ' + (v / 60) + ' 模拟分钟';
    return '1 真实秒 = ' + (v / 3600) + ' 模拟小时';
  }

  function speedPerRealMinute(speed) {
    const v = Math.max(1, Number(speed) || 1) * 60;
    if (v < 3600) return '1 分钟真实 ≈ ' + (v / 60).toFixed(v % 60 === 0 ? 0 : 1) + ' 分钟模拟';
    return '1 分钟真实 ≈ ' + (v / 3600).toFixed(v % 3600 === 0 ? 0 : 1) + ' 小时模拟';
  }

  /* --------------------------- 与后端一致的模型 --------------------------- */

  /** 与 Physics.saturationDo 保持一致，仅用于展示 */
  function saturationDo(temperature) {
    const t = Number(temperature);
    if (!Number.isFinite(t)) return null;
    return 14.652 - 0.41022 * t + 0.007991 * t * t - 0.000077774 * t * t * t;
  }

  function normalizePeriodType(type) {
    return ['peak', 'flat', 'valley'].indexOf(type) >= 0 ? type : 'flat';
  }

  function levelTone(level) {
    if (level === 'urgent') return 'danger';
    if (level === 'warn') return 'warn';
    return 'info';
  }

  function doStatus(value, thresholds) {
    const v = Number(value);
    if (!Number.isFinite(v) || !thresholds) return { tone: 'neutral', label: '等待数据' };
    if (v < Number(thresholds.safetyFloor)) return { tone: 'danger', label: '危险 · 低于安全下限' };
    if (v < Number(thresholds.onThreshold)) return { tone: 'warn', label: '偏低 · 低于开机阈值' };
    if (v > Number(thresholds.offThreshold)) return { tone: 'good', label: '充足 · 高于关机阈值' };
    return { tone: 'good', label: '正常 · 处于控制区间' };
  }

  /* ------------------------------- Toast ------------------------------- */

  let toastRoot = null;

  function removeToast(node) {
    if (!node || !node.parentNode) return;
    node.classList.add('leaving');
    setTimeout(function () {
      if (node.parentNode) node.parentNode.removeChild(node);
    }, 190);
  }

  function toast(message, type, title) {
    if (!toastRoot) toastRoot = document.getElementById('toastRoot');
    if (!toastRoot) return;
    const kind = type || 'info';
    const iconName = kind === 'success' ? 'check-circle'
      : (kind === 'error' || kind === 'warn') ? 'alert-triangle' : 'info';
    const node = document.createElement('div');
    node.className = 'toast ' + kind;
    node.setAttribute('role', kind === 'error' ? 'alert' : 'status');
    node.innerHTML =
      '<span class="toast-icon">' + icon(iconName) + '</span>' +
      '<div class="toast-body">' +
        (title ? '<strong>' + escapeHtml(title) + '</strong>' : '') +
        '<span>' + escapeHtml(message == null ? '' : message) + '</span>' +
      '</div>' +
      '<button class="toast-close" type="button" aria-label="关闭">' + icon('x') + '</button>';

    const closeBtn = node.querySelector('.toast-close');
    closeBtn.addEventListener('click', function () { removeToast(node); });
    toastRoot.appendChild(node);

    const life = kind === 'error' ? 6500 : kind === 'warn' ? 5200 : 3800;
    const timer = setTimeout(function () { removeToast(node); }, life);
    node.addEventListener('mouseenter', function () { clearTimeout(timer); });
    node.addEventListener('mouseleave', function () {
      setTimeout(function () { removeToast(node); }, 1400);
    });
  }

  /* ------------------------------ 确认弹窗 ------------------------------ */

  function confirmDialog(options) {
    const opts = options || {};
    return new Promise(function (resolve) {
      const root = document.getElementById('modalRoot');
      if (!root) { resolve(false); return; }

      const danger = !!opts.danger;
      root.innerHTML =
        '<div class="modal-backdrop">' +
          '<div class="modal" role="dialog" aria-modal="true" aria-labelledby="aquaModalTitle">' +
            '<div class="modal-top">' +
              '<div class="modal-ico' + (danger ? ' danger' : '') + '">' +
                icon(danger ? 'alert-triangle' : 'info') +
              '</div>' +
              '<h3 id="aquaModalTitle">' + escapeHtml(opts.title || '确认操作') + '</h3>' +
            '</div>' +
            '<p class="modal-text">' + escapeHtml(opts.message || '') + '</p>' +
            '<div class="modal-actions">' +
              '<button class="btn ghost" type="button" data-cancel>' +
                escapeHtml(opts.cancelText || '取消') +
              '</button>' +
              '<button class="btn ' + (danger ? 'danger' : 'primary') + '" type="button" data-confirm>' +
                escapeHtml(opts.confirmText || '确认') +
              '</button>' +
            '</div>' +
          '</div>' +
        '</div>';

      const backdrop = root.querySelector('.modal-backdrop');
      const cancelBtn = root.querySelector('[data-cancel]');
      const confirmBtn = root.querySelector('[data-confirm]');
      const previousFocus = document.activeElement;

      function close(result) {
        document.removeEventListener('keydown', onKey);
        root.innerHTML = '';
        if (previousFocus && previousFocus.focus) previousFocus.focus();
        resolve(result);
      }

      function onKey(event) {
        if (event.key === 'Escape') close(false);
        if (event.key === 'Enter') close(true);
      }

      cancelBtn.addEventListener('click', function () { close(false); });
      confirmBtn.addEventListener('click', function () { close(true); });
      backdrop.addEventListener('mousedown', function (event) {
        if (event.target === backdrop) close(false);
      });
      document.addEventListener('keydown', onKey);
      setTimeout(function () { confirmBtn.focus(); }, 30);
    });
  }

  /* ------------------------------ 按钮忙碌态 ------------------------------ */

  async function busy(button, task) {
    if (!button) return task();
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner spinner-sm" aria-hidden="true"></span><span>处理中…</span>';
    try {
      return await task();
    } finally {
      button.disabled = false;
      button.innerHTML = original;
    }
  }

  /* -------------------------------- 主题 -------------------------------- */

  function prefersDark() {
    return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  }

  function updateThemeButton() {
    const btn = document.getElementById('themeBtn');
    if (!btn) return;
    const dark = document.documentElement.getAttribute('data-theme') === 'dark';
    btn.innerHTML = icon(dark ? 'sun' : 'moon');
    btn.setAttribute('title', dark ? '切换到浅色主题' : '切换到深色主题');
  }

  function initTheme() {
    let saved = null;
    try { saved = window.localStorage.getItem(THEME_KEY); } catch (e) { /* ignore */ }
    const dark = saved ? saved === 'dark' : prefersDark();
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    updateThemeButton();
  }

  function toggleTheme() {
    const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    try { window.localStorage.setItem(THEME_KEY, next); } catch (e) { /* ignore */ }
    updateThemeButton();
    document.dispatchEvent(new CustomEvent('aqua:theme', { detail: { theme: next } }));
  }

  /* ------------------------------ 页面片段 ------------------------------ */

  function loadingCard(text) {
    return '<div class="loading-card"><span class="spinner"></span><span>' +
      escapeHtml(text || '正在加载…') + '</span></div>';
  }

  function errorCard(message) {
    return '<div class="error-card">' + icon('alert-triangle') +
      '<div><strong>数据加载失败</strong><p class="small" style="margin-top:4px">' +
      escapeHtml(message || '未知错误') + '</p></div>' +
      '<button class="btn ghost sm" type="button" onclick="location.reload()">刷新页面</button></div>';
  }

  function emptyState(text, iconName) {
    return '<div class="empty">' + icon(iconName || 'info') + '<span>' +
      escapeHtml(text || '暂无数据') + '</span></div>';
  }

  window.Aqua = window.Aqua || {};
  window.Aqua.ui = {
    escapeHtml: escapeHtml,
    esc: escapeHtml,
    icon: icon,
    toast: toast,
    confirm: confirmDialog,
    busy: busy,
    initTheme: initTheme,
    toggleTheme: toggleTheme,
    updateThemeButton: updateThemeButton,
    loadingCard: loadingCard,
    errorCard: errorCard,
    emptyState: emptyState,
    clamp: clamp,
    debounce: debounce
  };
  window.Aqua.fmt = {
    num: num,
    numberOr: numberOr,
    pad: pad,
    parseLocal: parseLocal,
    date: formatDate,
    time: formatTime,
    dateTime: formatDateTime,
    dateTimeShort: formatDateTimeShort,
    dateLong: formatDateLong,
    dateInputValue: dateInputValue,
    shifted: formatShifted,
    elapsed: formatElapsed,
    speedHint: speedHint,
    speedPerRealMinute: speedPerRealMinute,
    saturationDo: saturationDo,
    normalizePeriodType: normalizePeriodType,
    levelTone: levelTone,
    doStatus: doStatus
  };
})();
