/* ==========================================================================
   AquaGuard · 鱼塘溶氧后台管理系统
   页面路由、实时轮询与各业务视图
   ========================================================================== */
(function () {
  'use strict';

  const api = window.Aqua.api;
  const ui = window.Aqua.ui;
  const fmt = window.Aqua.fmt;
  const esc = ui.esc;
  const icon = ui.icon;

  const ROUTES = {
    overview: { label: '实时监控', title: '鱼塘运行总览', sub: '溶氧、水温与增氧机的实时状态' },
    clock: { label: '鱼塘时钟', title: '鱼塘时钟', sub: '暂停、继续、倍速与时间跳转' },
    pond: { label: '水体参数', title: '水体参数', sub: '玩具水体模型的产氧、耗氧与温度参数' },
    aerator: { label: '增氧策略', title: '增氧策略', sub: '自动阈值控制与手动增氧开关' },
    ponds: { label: '鱼塘管理', title: '鱼塘管理', sub: '新增鱼塘并在多塘之间切换、对比' },
    electricity: { label: '电价管理', title: '电价管理', sub: '峰平谷时段必须完整覆盖 0 - 24 点' },
    alerts: { label: '告警中心', title: '告警中心', sub: '查看告警等级并确认处理' },
    logs: { label: '系统日志', title: '系统日志', sub: '人的操作记录与增氧机的动作记录' },
    settings: { label: '系统设置', title: '系统设置', sub: '仿真步长、账号安全与系统重置' }
  };

  const POND_KEY = 'aquaguard.activePond';
  const COMPARE_KEY = 'aquaguard.comparePond';

  const state = {
    route: 'overview',
    user: null,
    operator: window.Aqua.getOperator(),
    ponds: [],
    activePondId: null,
    comparePondId: null,
    compareData: [],
    compareInFlight: false,
    realtime: null,
    strategy: null,
    pondConfig: null,
    electricity: null,
    settings: null,
    pollInterval: 2000,
    pollTimer: null,
    pollInFlight: false,
    conn: null,
    chart: {
      historyLimit: 120,
      predictMinutes: 30,
      showPrediction: true,
      showThresholds: true
    },
    alerts: { page: 1, size: 10, level: '', status: '', alertType: '', pondId: '' },
    logs: {
      tab: 'operation',
      operation: { page: 1, size: 10, module: '', operator: '' },
      aerator: { page: 1, size: 10, action: '', startTime: '', endTime: '', pondId: '' }
    }
  };

  let doChart = null;
  let compareChartA = null;
  let compareChartB = null;
  let extrasTimer = null;
  let badgeTimer = null;

  /* ------------------------------ 小工具 ------------------------------ */

  function el(id) { return document.getElementById(id); }

  function setText(id, value) {
    const node = el(id);
    if (node) node.textContent = value === null || value === undefined ? '' : String(value);
  }

  function setBadge(id, text, tone) {
    const node = el(id);
    if (!node) return;
    node.className = 'badge ' + (tone || 'neutral');
    node.textContent = text;
  }

  function option(value, label, selected) {
    return '<option value="' + esc(value) + '"' + (selected ? ' selected' : '') + '>' + esc(label) + '</option>';
  }

  function busy(button, task) {
    return ui.busy(button, task);
  }

  function pct(value, max) {
    const v = Number(value);
    const m = Number(max) || 1;
    return ui.clamp((Number.isFinite(v) ? v : 0) / m * 100, 0, 100);
  }

  function statusColor(tone) {
    if (tone === 'danger') return 'var(--danger)';
    if (tone === 'warn') return 'var(--warning)';
    if (tone === 'good') return 'var(--primary)';
    return 'var(--muted)';
  }

  function errorMessage(error) {
    return error && error.message ? error.message : '请求失败，请稍后重试';
  }

  function alertTone(level) {
    return fmt.levelTone(level);
  }

  function currentHour(pondTime) {
    const p = fmt.parseLocal(pondTime);
    return p ? p.h + p.mi / 60 : new Date().getHours();
  }

  function currentPrice() {
    if (!state.electricity) return null;
    return window.Aqua.priceForHour(state.electricity.periods, currentHour(state.realtime && state.realtime.pondTime));
  }

  function currentPriceText() {
    const period = currentPrice();
    return period ? (period.periodDesc + '段 ' + fmt.num(period.price, 2) + ' 元/kWh') : '未配置';
  }

  function syncSpeedButtons(speed) {
    document.querySelectorAll('#qcSpeedSeg button, #speedSeg button').forEach(function (button) {
      button.classList.toggle('active', Number(button.dataset.speed) === Number(speed));
    });
  }

  /* ------------------------------- 初始化 ------------------------------- */

  document.addEventListener('DOMContentLoaded', init);

  async function init() {
    ui.initTheme();

    let user;
    try {
      user = await api.auth.me();
    } catch (error) {
      window.location.replace('/login.html');
      return;
    }
    if (!user) {
      window.location.replace('/login.html');
      return;
    }

    state.user = user;
    state.operator = window.Aqua.setOperator(user.username);
    applySignedInUser(user);
    bindLogout();

    await loadPonds();
    bindShell();
    initAssistant();
    buildOverview();
    doChart = new window.Aqua.DoChart(el('doChart'), el('chartTip'));
    createCompareCharts();
    bindOverview();
    bindClockShortcuts();

    document.addEventListener('aqua:theme', function () {
      if (doChart) doChart.draw();
      if (compareChartA) compareChartA.draw();
      if (compareChartB) compareChartB.draw();
    });
    window.addEventListener('hashchange', handleRoute);
    document.addEventListener('visibilitychange', function () {
      if (!document.hidden) pollRealtime();
    });

    if (!window.location.hash) {
      try { history.replaceState(null, '', '#/overview'); } catch (e) { /* ignore */ }
    }
    handleRoute();
    startPolling();
    loadOverviewExtras();

    badgeTimer = setInterval(loadBadge, 20000);
    extrasTimer = setInterval(function () {
      if (state.route === 'overview' && !document.hidden) {
        loadRecentAlerts();
        loadRecentAerator();
        loadElectricityQuick();
      }
    }, 20000);
  }

  function applySignedInUser(user) {
    const name = user.displayName || user.username || '用户';
    const role = user.role === 'admin' ? '管理员' : user.role === 'operator' ? '操作员' : (user.role || '');
    setText('currentUserName', name);
    setText('currentUserRole',
      [user.username, role].filter(Boolean).join(' · ') || '已登录');
    setText('userAvatar', String(name).trim().slice(0, 1).toUpperCase() || 'A');
  }

  function bindLogout() {
    const btn = el('logoutBtn');
    if (!btn) return;
    btn.addEventListener('click', async function () {
      const confirmed = await ui.confirm({
        title: '退出登录？',
        message: '退出后将回到登录页，需要重新输入账号密码才能访问监控台。',
        confirmText: '退出登录'
      });
      if (!confirmed) return;

      btn.disabled = true;
      try {
        await api.auth.logout();
      } catch (error) {
        /* 会话可能已失效，直接回登录页 */
      }
      try { window.localStorage.removeItem('aquaguard.operator'); } catch (e) { /* ignore */ }
      window.location.replace('/login.html');
    });
  }

  function bindShell() {
    const menuBtn = el('menuBtn');
    const sidebar = el('sidebar');
    const scrim = el('sidebarScrim');
    const themeBtn = el('themeBtn');

    function openSidebar() {
      if (!sidebar || !scrim) return;
      sidebar.classList.add('open');
      scrim.hidden = false;
    }

    function closeSidebar() {
      if (!sidebar || !scrim) return;
      sidebar.classList.remove('open');
      scrim.hidden = true;
    }

    if (menuBtn) menuBtn.addEventListener('click', openSidebar);
    if (scrim) scrim.addEventListener('click', closeSidebar);
    if (themeBtn) themeBtn.addEventListener('click', function () { ui.toggleTheme(); });
    document.querySelectorAll('.nav-item').forEach(function (item) {
      item.addEventListener('click', closeSidebar);
    });
    window.addEventListener('resize', function () {
      if (window.innerWidth > 900) closeSidebar();
    });

    const pondSelect = el('pondSelect');
    if (pondSelect) {
      pondSelect.addEventListener('change', function () {
        selectPond(Number(pondSelect.value));
      });
    }
  }

  /* ------------------------------- 鱼塘切换 ------------------------------- */

  function storedPondId(key) {
    try {
      const value = Number(window.localStorage.getItem(key));
      return Number.isFinite(value) && value > 0 ? value : null;
    } catch (e) {
      return null;
    }
  }

  function persistPondId(key, value) {
    try {
      if (value === null || value === undefined) window.localStorage.removeItem(key);
      else window.localStorage.setItem(key, String(value));
    } catch (e) { /* 隐私模式下忽略 */ }
  }

  function pondById(id) {
    return state.ponds.find(function (pond) { return Number(pond.id) === Number(id); }) || null;
  }

  function pondLabel(pond) {
    return pond ? (pond.pondName || ('鱼塘#' + pond.id)) : '--';
  }

  function pondNameById(id) {
    const pond = pondById(id);
    return pond ? pondLabel(pond) : ('鱼塘#' + id);
  }

  function pondFilterOptions(selected) {
    return option('', '全部鱼塘', selected === '' || selected === null || selected === undefined) +
      state.ponds.map(function (pond) {
        return option(String(pond.id), pondLabel(pond), String(selected) === String(pond.id));
      }).join('');
  }

  function ensureActivePond() {
    const ids = state.ponds.map(function (pond) { return Number(pond.id); });
    let active = storedPondId(POND_KEY);
    if (ids.indexOf(active) < 0) active = ids.length ? ids[0] : null;
    state.activePondId = active;
    persistPondId(POND_KEY, active);

    const others = ids.filter(function (id) { return id !== active; });
    let compare = storedPondId(COMPARE_KEY);
    if (others.indexOf(compare) < 0) compare = others.length ? others[0] : null;
    state.comparePondId = compare;
    persistPondId(COMPARE_KEY, compare);
  }

  function renderPondControls() {
    const active = pondById(state.activePondId);

    const select = el('pondSelect');
    if (select) {
      select.innerHTML = state.ponds.length
        ? state.ponds.map(function (pond) {
            return option(pond.id, pondLabel(pond), Number(pond.id) === Number(state.activePondId));
          }).join('')
        : '<option value="">暂无鱼塘</option>';
      select.disabled = state.ponds.length < 2;
      select.title = state.ponds.length < 2 ? '当前只有一个鱼塘，可在鱼塘管理中新增' : '切换当前鱼塘';
    }

    setText('sidebarPondName', pondLabel(active));
    setText('topPondEyebrow', active ? 'POND ' + fmt.pad(Number(active.id)) : 'POND --');
    setText('pondCountBadge', state.ponds.length);
    const badge = el('pondCountBadge');
    if (badge) badge.hidden = state.ponds.length <= 0;

    updateCompareSelector();
  }

  async function loadPonds() {
    try {
      const list = await api.ponds.list();
      state.ponds = Array.isArray(list) ? list : [];
    } catch (error) {
      state.ponds = [];
    }
    const previous = state.activePondId;
    ensureActivePond();
    renderPondControls();
    if (previous !== null && previous !== state.activePondId) resetPondCaches();
  }

  function resetPondCaches() {
    state.realtime = null;
    state.strategy = null;
    state.pondConfig = null;
    state.settings = null;
    state.compareData = [];
    if (doChart) doChart.setData(null);
    if (compareChartA) compareChartA.setData(null);
    if (compareChartB) compareChartB.setData(null);
  }

  function selectPond(id) {
    const target = Number(id);
    if (!pondById(target) || target === Number(state.activePondId)) return;

    state.activePondId = target;
    persistPondId(POND_KEY, target);
    if (Number(state.comparePondId) === target) {
      const other = state.ponds.find(function (pond) { return Number(pond.id) !== target; });
      state.comparePondId = other ? Number(other.id) : null;
      persistPondId(COMPARE_KEY, state.comparePondId);
    }
    resetPondCaches();
    renderPondControls();
    ui.toast('已切换到「' + pondLabel(pondById(target)) + '」', 'info');
    handleRoute();
  }

  function compareCandidates() {
    return state.ponds.filter(function (pond) {
      return Number(pond.id) !== Number(state.activePondId);
    });
  }

  function ensureComparePond() {
    const candidates = compareCandidates();
    if (!candidates.length) {
      state.comparePondId = null;
      persistPondId(COMPARE_KEY, null);
      return null;
    }
    if (!candidates.some(function (pond) { return Number(pond.id) === Number(state.comparePondId); })) {
      state.comparePondId = Number(candidates[0].id);
      persistPondId(COMPARE_KEY, state.comparePondId);
    }
    return state.comparePondId;
  }

  function updateCompareSelector() {
    const select = el('comparePondSelect');
    if (!select) return;
    const candidates = compareCandidates();
    if (!candidates.length) {
      select.innerHTML = '<option value="">暂无其他鱼塘</option>';
      select.disabled = true;
      return;
    }
    ensureComparePond();
    select.disabled = false;
    select.innerHTML = candidates.map(function (pond) {
      return option(pond.id, pondLabel(pond), Number(pond.id) === Number(state.comparePondId));
    }).join('');
  }

  function createCompareCharts() {
    const canvasA = el('compareChartA');
    const canvasB = el('compareChartB');
    if (!canvasA || !canvasB) return;
    compareChartA = new window.Aqua.DoChart(canvasA, el('compareTipA'));
    compareChartB = new window.Aqua.DoChart(canvasB, el('compareTipB'));
    updateCompareSelector();
  }

  async function pollCompare() {
    if (state.route !== 'overview') return;
    updateCompareSelector();
    if (ensureComparePond() === null) {
      state.compareData = [];
      updateCompare();
      return;
    }
    if (state.compareInFlight) return;
    state.compareInFlight = true;
    try {
      const list = await api.compare(
        [state.activePondId, state.comparePondId],
        state.chart.predictMinutes,
        state.chart.historyLimit
      );
      state.compareData = Array.isArray(list) ? list : [];
      updateCompare();
    } catch (error) {
      setText('compareDelta', '对比数据加载失败');
    } finally {
      state.compareInFlight = false;
    }
  }

  function compareEntry(pondId) {
    return state.compareData.find(function (entry) {
      return Number(entry.pondId) === Number(pondId);
    }) || null;
  }

  function updateCompareSide(prefix, entry, fallbackName) {
    const rt = entry && entry.realtime ? entry.realtime : null;
    setText('compareName' + prefix, entry ? (entry.pondName || fallbackName) : fallbackName);
    if (!rt) {
      setText('compareMeta' + prefix, '暂无数据');
      setText('compareDo' + prefix, '--');
      setText('compareTemp' + prefix, '--');
      setText('compareAir' + prefix, '--');
      setBadge('compareState' + prefix, '等待数据', 'neutral');
      return;
    }
    setText('compareMeta' + prefix, fmt.dateTimeShort(rt.pondTime) + ' · ×' + (rt.speed || 1));
    setText('compareDo' + prefix, fmt.num(rt.currentDo));
    setText('compareTemp' + prefix, fmt.num(rt.temperature, 1) + ' ℃');
    setText('compareAir' + prefix, rt.aeratorOn ? '增氧中' : '已停止');
    setBadge('compareState' + prefix, rt.paused ? '已暂停' : '运行中', rt.paused ? 'warn' : 'good');
  }

  function updateCompare() {
    if (!el('compareChartA')) return;
    const activeEntry = compareEntry(state.activePondId) ||
      (state.realtime ? {
        pondId: state.activePondId,
        pondName: pondLabel(pondById(state.activePondId)),
        realtime: state.realtime
      } : null);
    const compareEntryData = compareEntry(state.comparePondId);
    updateCompareSide('A', activeEntry, pondLabel(pondById(state.activePondId)));
    updateCompareSide('B', compareEntryData, pondLabel(pondById(state.comparePondId)));

    if (compareChartA) compareChartA.setData(activeEntry ? activeEntry.realtime : null);
    if (compareChartB) compareChartB.setData(compareEntryData ? compareEntryData.realtime : null);

    const a = activeEntry && activeEntry.realtime;
    const b = compareEntryData && compareEntryData.realtime;
    if (!state.comparePondId) {
      setText('compareDelta', '再新增一个鱼塘即可双塘对比');
    } else if (a && b) {
      const doGap = Number(b.currentDo) - Number(a.currentDo);
      const tempGap = Number(b.temperature) - Number(a.temperature);
      setText('compareDelta',
        'Δ溶氧 ' + (doGap >= 0 ? '+' : '') + fmt.num(doGap, 2) + ' mg/L · ' +
        'Δ水温 ' + (tempGap >= 0 ? '+' : '') + fmt.num(tempGap, 1) + ' ℃');
    } else {
      setText('compareDelta', '等待两塘数据…');
    }
  }

  /* ------------------------------- 路由 ------------------------------- */

  function routeFromHash() {
    const raw = (window.location.hash || '').replace(/^#\/?/, '');
    return ROUTES[raw] ? raw : 'overview';
  }

  function handleRoute() {
    const route = routeFromHash();
    state.route = route;

    document.querySelectorAll('.view').forEach(function (view) {
      view.classList.toggle('active', view.dataset.view === route);
    });
    document.querySelectorAll('.nav-item').forEach(function (item) {
      const active = item.dataset.route === route;
      item.classList.toggle('active', active);
      if (active) item.setAttribute('aria-current', 'page');
      else item.removeAttribute('aria-current');
    });

    const meta = ROUTES[route];
    setText('topPageName', meta.label);
    document.title = meta.title + ' · AquaGuard 鱼塘溶氧后台';

    try { el('sidebar').classList.remove('open'); } catch (e) { /* ignore */ }
    if (el('sidebarScrim')) el('sidebarScrim').hidden = true;

    switch (route) {
      case 'overview':
        pollRealtime();
        loadOverviewExtras();
        pollCompare();
        break;
      case 'clock':
        loadClockView();
        break;
      case 'pond':
        loadPondView();
        break;
      case 'aerator':
        loadAeratorView();
        break;
      case 'ponds':
        loadPondsView();
        break;
      case 'electricity':
        loadElectricityView();
        break;
      case 'alerts':
        loadAlertsView();
        break;
      case 'logs':
        loadLogsView();
        break;
      case 'settings':
        loadSettingsView();
        break;
      default:
        break;
    }
  }

  /* ------------------------------ 实时数据 ------------------------------ */

  function startPolling() {
    if (state.pollTimer) {
      clearInterval(state.pollTimer);
      state.pollTimer = null;
    }
    if (state.pollInterval > 0) {
      state.pollTimer = setInterval(function () {
        if (!document.hidden) pollRealtime();
      }, state.pollInterval);
    }
    pollRealtime();
  }

  async function pollRealtime() {
    if (state.pollInFlight) return;
    state.pollInFlight = true;
    try {
      const data = await api.realtime(state.chart.predictMinutes, state.chart.historyLimit, state.activePondId);
      state.realtime = data;
      setConnection(true);
      applyRealtime(data);
      if (state.route === 'overview') pollCompare();
    } catch (error) {
      setConnection(false, errorMessage(error));
    } finally {
      state.pollInFlight = false;
    }
  }

  function setConnection(ok, message) {
    const pill = el('connPill');
    const text = el('connText');
    if (pill) {
      pill.classList.toggle('online', ok);
      pill.classList.toggle('offline', !ok);
      pill.title = ok ? '后端连接正常' : (message || '后端连接失败');
    }
    if (text) text.textContent = ok ? '实时连接' : '连接中断';
    if (state.conn === null || state.conn !== ok) {
      if (!ok) {
        ui.toast(message || '无法连接后端，请检查服务是否已启动', 'error', '连接中断');
      } else if (state.conn === false) {
        ui.toast('数据刷新已恢复', 'success', '连接恢复');
      }
    }
    state.conn = ok;
  }

  function mergeSnapshot(snapshot) {
    if (!snapshot) return;
    if (!state.realtime) {
      pollRealtime();
      return;
    }
    ['pondTime', 'currentDo', 'temperature', 'aeratorOn', 'paused', 'speed', 'elapsedMinutes'].forEach(function (key) {
      if (snapshot[key] !== undefined) state.realtime[key] = snapshot[key];
    });
    applyRealtime(state.realtime);
  }

  function applyRealtime(data) {
    if (!data) return;
    updateShell(data);
    updateOverview(data);
    updateClockLive(data);
    updateStrategyLive(data);
    updatePondInfo(data);
    if (state.route === 'electricity' && priceRows.length) refreshPriceDerived();
  }

  function updateShell(data) {
    setText('topPondTime', fmt.dateTimeShort(data.pondTime));
    const timePill = el('topPondTime') ? el('topPondTime').closest('.top-pill') : null;
    if (timePill) {
      timePill.classList.toggle('paused', !!data.paused);
      timePill.title = '鱼塘时间：' + fmt.dateTime(data.pondTime) + (data.paused ? '（已暂停）' : '');
    }

    const airPill = el('topAeratorPill');
    if (airPill) {
      airPill.classList.toggle('on', !!data.aeratorOn);
      airPill.classList.toggle('off', !data.aeratorOn);
    }
    setText('topAeratorText', data.aeratorOn ? '增氧中' : '已停止');
  }

  /* ------------------------------- 总览 ------------------------------- */

  function buildOverview() {
    const view = el('view-overview');
    if (!view) return;

    view.innerHTML =
      '<div class="page-head">' +
        '<div>' +
          '<p class="eyebrow">实时监控 · 多鱼塘对比</p>' +
          '<h1>鱼塘运行总览</h1>' +
          '<p class="page-sub">溶氧、水温与增氧机的实时状态<span class="dot-sep">·</span><span id="overviewUpdated">等待首次刷新</span></p>' +
        '</div>' +
        '<div class="page-actions">' +
          '<label class="inline-field"><span>自动刷新</span>' +
            '<select class="input compact" id="pollSelect">' +
              option('1000', '1 秒', state.pollInterval === 1000) +
              option('2000', '2 秒', state.pollInterval === 2000) +
              option('5000', '5 秒', state.pollInterval === 5000) +
              option('0', '暂停', state.pollInterval === 0) +
            '</select>' +
          '</label>' +
          '<button class="btn ghost" id="refreshNowBtn" type="button">' + icon('refresh') + '立即刷新</button>' +
        '</div>' +
      '</div>' +

      '<div class="metrics">' +
        '<article class="metric-card" data-tone="aqua">' +
          '<div class="metric-head"><span>当前溶氧</span><span class="metric-ico">' + icon('droplet') + '</span></div>' +
          '<div class="metric-value"><span id="mDoValue">--</span><small>mg/L</small></div>' +
          '<div class="metric-foot"><span class="badge neutral" id="mDoStatus">等待数据</span><span class="muted" id="mDoRange">--</span></div>' +
          '<div class="do-meter" id="mDoMeter" aria-hidden="true">' +
            '<div class="do-meter-track"></div>' +
            '<span class="do-meter-dot" id="mDoDot"></span>' +
          '</div>' +
        '</article>' +
        '<article class="metric-card" data-tone="amber">' +
          '<div class="metric-head"><span>水体温度</span><span class="metric-ico">' + icon('thermometer') + '</span></div>' +
          '<div class="metric-value"><span id="mTempValue">--</span><small>℃</small></div>' +
          '<div class="metric-foot"><span class="badge neutral" id="mTempMode">--</span><span class="muted" id="mSaturation">饱和溶氧 --</span></div>' +
        '</article>' +
        '<article class="metric-card" data-tone="blue">' +
          '<div class="metric-head"><span>增氧机</span><span class="metric-ico">' + icon('fan') + '</span></div>' +
          '<div class="metric-value metric-text"><span id="mAirValue">--</span></div>' +
          '<div class="metric-foot"><span class="badge neutral" id="mAirMode">--</span><span class="muted" id="mAirHint">--</span></div>' +
        '</article>' +
        '<article class="metric-card" data-tone="violet">' +
          '<div class="metric-head"><span>鱼塘时间</span><span class="metric-ico">' + icon('clock') + '</span></div>' +
          '<div class="metric-value metric-time"><span id="mPondTime">--:--</span></div>' +
          '<div class="metric-foot"><span class="muted" id="mPondDate">--</span><span class="badge neutral" id="mPondMeta">--</span></div>' +
        '</article>' +
      '</div>' +

      '<article class="card compare-card">' +
        '<div class="card-head">' +
          '<div class="card-title"><h3>双塘实时对比</h3><p>左侧为当前鱼塘，右侧可切换另一个鱼塘；两条溶氧曲线同步刷新</p></div>' +
          '<div class="compare-controls">' +
            '<span class="badge info" id="compareDelta">等待数据</span>' +
            '<label class="mini-field">对比鱼塘' +
              '<select class="input compact" id="comparePondSelect"></select>' +
            '</label>' +
          '</div>' +
        '</div>' +
        '<div class="compare-grid">' +
          comparePanelMarkup('A') +
          comparePanelMarkup('B') +
        '</div>' +
        '<p class="chart-note">' + icon('info') + '<span>两塘各自独立运行，差值 = 右侧鱼塘 − 左侧鱼塘；可随时在顶部切换当前鱼塘。</span></p>' +
      '</article>' +

      '<div class="overview-grid">' +
        '<article class="card chart-card">' +
          '<div class="card-head">' +
            '<div class="card-title"><h3>溶氧趋势</h3><p>历史采样 + 未来趋势推演，阈值线辅助判断</p></div>' +
            '<div class="chart-controls">' +
              '<label class="mini-field">历史' +
                '<select class="input compact" id="chartHistory">' +
                  option('60', '60 点', state.chart.historyLimit === 60) +
                  option('120', '120 点', state.chart.historyLimit === 120) +
                  option('240', '240 点', state.chart.historyLimit === 240) +
                  option('480', '480 点', state.chart.historyLimit === 480) +
                '</select>' +
              '</label>' +
              '<label class="mini-field">预测' +
                '<select class="input compact" id="chartPredict">' +
                  option('0', '关闭', state.chart.predictMinutes === 0) +
                  option('15', '15 分钟', state.chart.predictMinutes === 15) +
                  option('30', '30 分钟', state.chart.predictMinutes === 30) +
                  option('60', '60 分钟', state.chart.predictMinutes === 60) +
                  option('120', '120 分钟', state.chart.predictMinutes === 120) +
                '</select>' +
              '</label>' +
              '<button class="chip active" id="togglePred" type="button" aria-pressed="true">预测线</button>' +
              '<button class="chip active" id="toggleThr" type="button" aria-pressed="true">阈值线</button>' +
            '</div>' +
          '</div>' +
          '<div class="chart-legend">' +
            '<span class="lg"><i class="line history"></i>历史溶氧</span>' +
            '<span class="lg"><i class="line prediction"></i>预测趋势</span>' +
            '<span class="lg"><i class="line floor"></i>安全下限</span>' +
            '<span class="lg"><i class="line on"></i>开机阈值</span>' +
            '<span class="lg"><i class="line off"></i>关机阈值</span>' +
            '<span class="lg"><i class="swatch aerator"></i>增氧时段</span>' +
          '</div>' +
          '<div class="chart-wrap" id="chartWrap">' +
            '<canvas id="doChart" role="img" aria-label="溶氧历史与预测曲线"></canvas>' +
            '<div class="chart-tooltip" id="chartTip"></div>' +
            '<div class="chart-empty" id="chartEmpty">等待仿真数据…</div>' +
          '</div>' +
          '<p class="chart-note">' + icon('info') + '<span id="predictionNote">预测为无噪声确定性前推，仅供趋势参考。</span></p>' +
        '</article>' +

        '<aside class="side-stack">' +
          '<article class="card quick-card">' +
            '<div class="card-head">' +
              '<div><h3>时钟控制</h3><p>暂停、继续与倍速</p></div>' +
              '<a class="text-link" href="#/clock">详细控制' + icon('chevron-right') + '</a>' +
            '</div>' +
            '<div class="quick-clock"><span class="quick-time" id="qcTime">--:--</span><span class="quick-date" id="qcDate">--</span></div>' +
            '<div class="quick-row">' +
              '<span class="badge neutral" id="qcState">--</span>' +
              '<span class="quick-speed">倍速 <strong id="qcSpeed">×1</strong></span>' +
            '</div>' +
            '<div class="quick-actions">' +
              '<button class="btn primary" id="qcPauseBtn" type="button">' + icon('pause') + '暂停</button>' +
              '<div class="segmented" id="qcSpeedSeg">' +
                '<button type="button" data-speed="1">1×</button>' +
                '<button type="button" data-speed="60">60×</button>' +
                '<button type="button" data-speed="600">600×</button>' +
              '</div>' +
            '</div>' +
          '</article>' +

          '<article class="card quick-card">' +
            '<div class="card-head">' +
              '<div><h3>增氧机</h3><p>当前设备状态</p></div>' +
              '<a class="text-link" href="#/aerator">策略设置' + icon('chevron-right') + '</a>' +
            '</div>' +
            '<div class="aerator-state">' +
              '<div class="aerator-orb" id="qaOrb">' + icon('fan') + '</div>' +
              '<div><div class="aerator-state-text" id="qaState">--</div><div class="muted small" id="qaMode">--</div></div>' +
            '</div>' +
            '<button class="btn ghost full" id="qaToggleBtn" type="button">手动开启</button>' +
            '<p class="tiny muted" style="margin-top:8px">手动操作会把策略切换到手动模式。</p>' +
          '</article>' +

          '<article class="card quick-card">' +
            '<div class="card-head">' +
              '<div><h3>分时电价</h3><p>当前鱼塘所处时段</p></div>' +
              '<a class="text-link" href="#/electricity">电价设置' + icon('chevron-right') + '</a>' +
            '</div>' +
            '<div class="price-now"><span class="badge neutral" id="qpBadge">--</span><strong id="qpPrice">--</strong><small>元 / kWh</small></div>' +
            '<div class="price-band mini" id="qpBand"></div>' +
            '<div class="price-legend"><span><i class="peak"></i>峰</span><span><i class="flat"></i>平</span><span><i class="valley"></i>谷</span></div>' +
          '</article>' +
        '</aside>' +
      '</div>' +

      '<div class="bottom-grid">' +
        '<article class="card">' +
          '<div class="card-head">' +
            '<div><h3>最近未确认告警</h3><p>需要人工处理的事件</p></div>' +
            '<a class="text-link" href="#/alerts">查看全部' + icon('chevron-right') + '</a>' +
          '</div>' +
          '<div class="mini-list" id="recentAlerts"><div class="empty">加载中…</div></div>' +
        '</article>' +
        '<article class="card">' +
          '<div class="card-head">' +
            '<div><h3>最近增氧动作</h3><p>自动与手动开关记录</p></div>' +
            '<a class="text-link" href="#/logs">查看日志' + icon('chevron-right') + '</a>' +
          '</div>' +
          '<div class="mini-list" id="recentAerator"><div class="empty">加载中…</div></div>' +
        '</article>' +
      '</div>';
  }

  function comparePanelMarkup(side) {
    return '<div class="compare-panel">' +
        '<div class="compare-panel-head">' +
          '<div><strong id="compareName' + side + '">--</strong>' +
            '<span class="muted tiny" id="compareMeta' + side + '">--</span></div>' +
          '<span class="badge neutral" id="compareState' + side + '">--</span>' +
        '</div>' +
        '<div class="compare-stats">' +
          '<div><span>溶氧</span><strong id="compareDo' + side + '">--</strong><small>mg/L</small></div>' +
          '<div><span>水温</span><strong id="compareTemp' + side + '">--</strong></div>' +
          '<div><span>增氧机</span><strong id="compareAir' + side + '">--</strong></div>' +
        '</div>' +
        '<div class="compare-chart-wrap">' +
          '<canvas id="compareChart' + side + '" role="img" aria-label="鱼塘溶氧趋势对比"></canvas>' +
          '<div class="chart-tooltip" id="compareTip' + side + '"></div>' +
          '<div class="chart-empty" id="compareEmpty' + side + '">等待对比数据…</div>' +
        '</div>' +
      '</div>';
  }

  function bindOverview() {
    const pollSelect = el('pollSelect');
    if (pollSelect) {
      pollSelect.addEventListener('change', function () {
        state.pollInterval = Number(pollSelect.value);
        startPolling();
        ui.toast(state.pollInterval ? '自动刷新间隔已改为 ' + state.pollInterval / 1000 + ' 秒' : '自动刷新已暂停',
          'info');
      });
    }

    const refreshBtn = el('refreshNowBtn');
    if (refreshBtn) {
      refreshBtn.addEventListener('click', function () {
        busy(refreshBtn, async function () {
          await pollRealtime();
          loadOverviewExtras();
        });
      });
    }

    const compareSelect = el('comparePondSelect');
    if (compareSelect) {
      compareSelect.addEventListener('change', function () {
        const id = Number(compareSelect.value);
        if (!pondById(id)) return;
        state.comparePondId = id;
        persistPondId(COMPARE_KEY, id);
        state.compareData = [];
        if (compareChartB) compareChartB.setData(null);
        pollCompare();
      });
    }

    const historySelect = el('chartHistory');
    if (historySelect) {
      historySelect.addEventListener('change', function () {
        state.chart.historyLimit = Number(historySelect.value);
        pollRealtime();
      });
    }

    const predictSelect = el('chartPredict');
    if (predictSelect) {
      predictSelect.addEventListener('change', function () {
        state.chart.predictMinutes = Number(predictSelect.value);
        pollRealtime();
      });
    }

    const togglePred = el('togglePred');
    if (togglePred) {
      togglePred.addEventListener('click', function () {
        state.chart.showPrediction = !state.chart.showPrediction;
        togglePred.classList.toggle('active', state.chart.showPrediction);
        togglePred.setAttribute('aria-pressed', String(state.chart.showPrediction));
        if (doChart) doChart.setShowPrediction(state.chart.showPrediction);
        if (compareChartA) compareChartA.setShowPrediction(state.chart.showPrediction);
        if (compareChartB) compareChartB.setShowPrediction(state.chart.showPrediction);
      });
    }

    const toggleThr = el('toggleThr');
    if (toggleThr) {
      toggleThr.addEventListener('click', function () {
        state.chart.showThresholds = !state.chart.showThresholds;
        toggleThr.classList.toggle('active', state.chart.showThresholds);
        toggleThr.setAttribute('aria-pressed', String(state.chart.showThresholds));
        if (doChart) doChart.setShowThresholds(state.chart.showThresholds);
        if (compareChartA) compareChartA.setShowThresholds(state.chart.showThresholds);
        if (compareChartB) compareChartB.setShowThresholds(state.chart.showThresholds);
      });
    }

    const pauseBtn = el('qcPauseBtn');
    if (pauseBtn) {
      pauseBtn.addEventListener('click', function () {
        busy(pauseBtn, togglePause).then(function () {
          if (state.realtime) {
            updateQuickClock(state.realtime);
            updateClockLive(state.realtime);
          }
        });
      });
    }

    const speedSeg = el('qcSpeedSeg');
    if (speedSeg) {
      speedSeg.addEventListener('click', function (event) {
        const button = event.target.closest('button[data-speed]');
        if (!button) return;
        changeSpeed(Number(button.dataset.speed), button);
      });
    }

    const airToggle = el('qaToggleBtn');
    if (airToggle) {
      airToggle.addEventListener('click', function () {
        const target = !(state.realtime && state.realtime.aeratorOn);
        busy(airToggle, function () { return manualAerator(target); }).then(function () {
          if (state.realtime) updateQuickAerator(state.realtime);
        });
      });
    }
  }

  function updateOverview(data) {
    if (!el('mDoValue')) return;
    const thresholds = data.thresholds || {};
    const status = fmt.doStatus(data.currentDo, thresholds);
    setText('mDoValue', fmt.num(data.currentDo));
    setBadge('mDoStatus', status.label, status.tone);
    setText('mDoRange', '安全下限 ' + fmt.num(thresholds.safetyFloor, 1) + ' mg/L');
    updateDoMeter(data, status);

    setText('mTempValue', fmt.num(data.temperature, 1));
    setText('mSaturation', '饱和溶氧 ' + fmt.num(fmt.saturationDo(data.temperature), 1) + ' mg/L');
    const modeDesc = state.pondConfig ? state.pondConfig.temperatureModeDesc : null;
    setBadge('mTempMode', modeDesc || '水温', 'neutral');

    setText('mAirValue', data.aeratorOn ? '运行中' : '已停止');
    const mode = state.strategy ? state.strategy.mode : null;
    setBadge('mAirMode', mode === 'manual' ? '手动模式' : mode === 'auto' ? '自动模式' : '策略未载入',
      mode === 'manual' ? 'info' : mode === 'auto' ? 'good' : 'neutral');

    let airHint = '--';
    if (Number.isFinite(Number(data.currentDo)) && Number.isFinite(Number(thresholds.onThreshold))) {
      if (data.aeratorOn) {
        const gap = Number(data.currentDo) - Number(thresholds.offThreshold);
        airHint = gap >= 0 ? '高于关机阈值 ' + gap.toFixed(2) : '距关机还差 ' + Math.abs(gap).toFixed(2);
      } else {
        const gap = Number(data.currentDo) - Number(thresholds.onThreshold);
        airHint = gap >= 0 ? '距开机阈值 ' + gap.toFixed(2) : '已低于开机阈值';
      }
    }
    setText('mAirHint', airHint);

    setText('mPondTime', fmt.time(data.pondTime));
    setText('mPondDate', fmt.date(data.pondTime));
    setBadge('mPondMeta', (data.paused ? '已暂停' : '运行中') + ' · ×' + (data.speed || 1), data.paused ? 'warn' : 'good');
    setText('overviewUpdated', '更新于 ' + new Date().toLocaleTimeString('zh-CN', { hour12: false }));

    if (el('predictionNote') && data.predictionNote) setText('predictionNote', data.predictionNote);

    if (doChart) doChart.setData(data);
    updateQuickClock(data);
    updateQuickAerator(data);
    updatePriceQuick(data.pondTime);
  }

  function updateDoMeter(data, status) {
    const meter = el('mDoMeter');
    const dot = el('mDoDot');
    if (!meter || !dot) return;

    const thresholds = data.thresholds || {};
    const safety = Number(thresholds.safetyFloor);
    const on = Number(thresholds.onThreshold);
    const off = Number(thresholds.offThreshold);
    const max = Math.max(10, Number(data.currentDo) * 1.2 || 0, (off || 0) * 1.25);

    const track = meter.querySelector('.do-meter-track');
    if (track && Number.isFinite(safety) && Number.isFinite(on) && Number.isFinite(off)) {
      track.style.background =
        'linear-gradient(90deg,' +
        'color-mix(in srgb, var(--danger) 30%, transparent) 0 ' + pct(safety, max) + '%,' +
        'color-mix(in srgb, var(--warning) 32%, transparent) ' + pct(safety, max) + '% ' + pct(on, max) + '%,' +
        'color-mix(in srgb, var(--success) 28%, transparent) ' + pct(on, max) + '% ' + pct(off, max) + '%,' +
        'color-mix(in srgb, var(--info) 25%, transparent) ' + pct(off, max) + '% 100%)';
    }

    meter.querySelectorAll('.do-meter-tick').forEach(function (node) { node.remove(); });
    [safety, on, off].forEach(function (value, index) {
      if (!Number.isFinite(value)) return;
      const tick = document.createElement('span');
      tick.className = 'do-meter-tick';
      tick.style.left = pct(value, max) + '%';
      tick.title = ['安全下限', '开机阈值', '关机阈值'][index] + ' ' + fmt.num(value, 2) + ' mg/L';
      meter.appendChild(tick);
    });

    dot.style.left = pct(data.currentDo, max) + '%';
    dot.style.background = statusColor(status.tone);
  }

  function updateQuickClock(data) {
    setText('qcTime', fmt.time(data.pondTime));
    setText('qcDate', fmt.date(data.pondTime));
    setBadge('qcState', data.paused ? '已暂停' : '运行中', data.paused ? 'warn' : 'good');
    setText('qcSpeed', '×' + (data.speed || 1));
    const button = el('qcPauseBtn');
    if (button) button.innerHTML = icon(data.paused ? 'play' : 'pause') + (data.paused ? '继续' : '暂停');
    syncSpeedButtons(data.speed);
  }

  function updateQuickAerator(data) {
    setText('qaState', data.aeratorOn ? '运行中' : '已停止');
    const modeLabel = state.strategy
      ? (state.strategy.modeDesc || (state.strategy.mode === 'manual' ? '手动' : '自动'))
      : '策略未载入';
    setText('qaMode', modeLabel + ' · 当前溶氧 ' + fmt.num(data.currentDo) + ' mg/L');
    const orb = el('qaOrb');
    if (orb) orb.classList.toggle('on', !!data.aeratorOn);
    const button = el('qaToggleBtn');
    if (button) button.textContent = data.aeratorOn ? '手动关闭' : '手动开启';
  }

  function updatePriceQuick(pondTime) {
    if (!state.electricity) return;
    const hour = currentHour(pondTime);
    const period = window.Aqua.priceForHour(state.electricity.periods, hour);
    if (period) {
      const type = fmt.normalizePeriodType(period.periodType);
      setBadge('qpBadge', (period.periodDesc || type) + '段', type);
      setText('qpPrice', fmt.num(period.price, 2));
    } else {
      setBadge('qpBadge', '未配置', 'neutral');
      setText('qpPrice', '--');
    }
    window.Aqua.renderPriceBand(el('qpBand'), state.electricity.periods, hour, { labels: false });
  }

  /* ------------------------------ 总览附加数据 ------------------------------ */

  async function loadOverviewExtras() {
    loadStrategy();
    loadPondConfig();
    loadElectricityQuick();
    loadRecentAlerts();
    loadRecentAerator();
    loadBadge();
  }

  async function loadStrategy() {
    try {
      const strategy = await api.aerator.getStrategy(state.activePondId);
      state.strategy = strategy;
      if (state.realtime) {
        updateQuickAerator(state.realtime);
        updateStrategyLive(state.realtime);
      }
    } catch (error) {
      /* 总览不阻塞，策略页会展示错误 */
    }
  }

  async function loadPondConfig() {
    try {
      const config = await api.pond.get(state.activePondId);
      state.pondConfig = config;
      applyPondIdentity(config);
      if (state.realtime) {
        setBadge('mTempMode', config.temperatureModeDesc || '水温', 'neutral');
        updatePondInfo();
      }
    } catch (error) {
      /* 同上 */
    }
  }

  async function loadElectricityQuick() {
    try {
      const data = await api.electricity.get();
      state.electricity = data;
      updatePriceQuick(state.realtime ? state.realtime.pondTime : null);
      if (state.route === 'aerator') {
        setText('aCurrentPrice', currentPriceText());
      }
    } catch (error) {
      /* 忽略，电价页会给出错误 */
    }
  }

  async function loadBadge() {
    try {
      const page = await api.alerts.list({ page: 1, size: 1, status: 'pending' });
      const badge = el('alertBadge');
      if (!badge) return;
      const total = Number(page.total) || 0;
      badge.hidden = total <= 0;
      badge.textContent = total > 99 ? '99+' : String(total);
    } catch (error) {
      /* ignore */
    }
  }

  async function loadRecentAlerts() {
    const container = el('recentAlerts');
    if (!container) return;
    try {
      const page = await api.alerts.list({
        page: 1, size: 5, status: 'pending', pondId: state.activePondId
      });
      if (!page.records || !page.records.length) {
        container.innerHTML = ui.emptyState('暂无未确认告警', 'check-circle');
        return;
      }
      container.innerHTML = page.records.map(function (item) {
        const tone = alertTone(item.level);
        return '<a class="mini-item" href="#/alerts">' +
          '<span class="level-dot ' + tone + '"></span>' +
          '<div class="mini-main">' +
            '<strong>' + esc(item.alertTypeDesc || item.alertType) + '</strong>' +
            '<span>' + esc(item.message || '') + ' · ' + fmt.num(item.doValue) + ' mg/L · ' + fmt.dateTimeShort(item.pondTime) + '</span>' +
          '</div>' +
          '<span class="badge ' + tone + '">' + esc(item.levelDesc || item.level) + '</span>' +
        '</a>';
      }).join('');
    } catch (error) {
      container.innerHTML = ui.emptyState('告警加载失败', 'alert-triangle');
    }
  }

  async function loadRecentAerator() {
    const container = el('recentAerator');
    if (!container) return;
    try {
      const page = await api.logs.aerator({ page: 1, size: 5, pondId: state.activePondId });
      if (!page.records || !page.records.length) {
        container.innerHTML = ui.emptyState('暂无增氧机动作', 'fan');
        return;
      }
      container.innerHTML = page.records.map(function (item) {
        const on = item.action === 'on';
        return '<a class="mini-item" href="#/logs">' +
          '<span class="level-dot ' + (on ? 'low' : '') + '"></span>' +
          '<div class="mini-main">' +
            '<strong>' + esc(item.actionDesc || item.action) + ' · 溶氧 ' + fmt.num(item.doValue) + ' mg/L</strong>' +
            '<span>' + esc(item.triggerReason || '无触发原因') + '</span>' +
          '</div>' +
          '<span class="muted tiny nowrap">' + fmt.dateTimeShort(item.pondTime) + '</span>' +
        '</a>';
      }).join('');
    } catch (error) {
      container.innerHTML = ui.emptyState('增氧日志加载失败', 'alert-triangle');
    }
  }

  /* ------------------------------ 时钟操作 ------------------------------ */

  async function togglePause() {
    const paused = !!(state.realtime && state.realtime.paused);
    try {
      const snapshot = paused
        ? await api.clock.resume(state.activePondId)
        : await api.clock.pause(state.activePondId);
      mergeSnapshot(snapshot);
      ui.toast(paused ? '仿真已继续，鱼塘时间开始推进' : '仿真已暂停，鱼塘时间保持不变', 'success');
      pollRealtime();
      updatePriceQuick(snapshot.pondTime);
    } catch (error) {
      ui.toast(errorMessage(error), 'error');
    }
  }

  async function changeSpeed(speed, button) {
    try {
      if (button) {
        button.disabled = true;
        try {
          const snapshot = await api.clock.speed(speed, state.activePondId);
          mergeSnapshot(snapshot);
          ui.toast('倍速已改为 ×' + speed + '，' + fmt.speedHint(speed), 'success');
          pollRealtime();
        } finally {
          button.disabled = false;
        }
      } else {
        const snapshot = await api.clock.speed(speed, state.activePondId);
        mergeSnapshot(snapshot);
        ui.toast('倍速已改为 ×' + speed + '，' + fmt.speedHint(speed), 'success');
        pollRealtime();
      }
    } catch (error) {
      ui.toast(errorMessage(error), 'error');
    }
  }

  async function setPondTime(value) {
    if (!value) {
      ui.toast('请先选择目标时间', 'warn');
      return null;
    }
    const time = value.length === 16 ? value + ':00' : value;
    try {
      const snapshot = await api.clock.setTime(time, state.activePondId);
      mergeSnapshot(snapshot);
      ui.toast('鱼塘时间已跳转到 ' + fmt.dateTime(snapshot.pondTime), 'success');
      pollRealtime();
      updatePriceQuick(snapshot.pondTime);
      return snapshot;
    } catch (error) {
      ui.toast(errorMessage(error), 'error');
      return null;
    }
  }

  async function manualAerator(on) {
    try {
      const strategy = await api.aerator.manual(on, state.activePondId);
      state.strategy = strategy;
      ui.toast(on ? '已发送手动开机指令' : '已发送手动关机指令', 'success');
      if (state.route === 'aerator') applyStrategyToForm(strategy);
      pollRealtime();
      setTimeout(pollRealtime, 1300);
      loadRecentAerator();
      return strategy;
    } catch (error) {
      ui.toast(errorMessage(error), 'error');
      return null;
    }
  }

  function bindClockShortcuts() {
    document.addEventListener('click', function () {
      /* 预留：页面级快捷键可在此扩展 */
    });
  }

  /* ------------------------------- 时钟页 ------------------------------- */

  async function loadClockView() {
    const view = el('view-clock');
    if (!view) return;
    view.innerHTML = ui.loadingCard('正在读取鱼塘时钟…');

    try {
      const results = await Promise.all([
        api.realtime(state.chart.predictMinutes, state.chart.historyLimit, state.activePondId),
        api.aerator.getStrategy(state.activePondId).catch(function () { return state.strategy; })
      ]);
      const data = results[0];
      state.realtime = data;
      if (results[1]) state.strategy = results[1];
      if (!state.pondConfig) loadPondConfig();
      view.innerHTML = clockTemplate(data);
      bindClockView(data);
      applyRealtime(data);
    } catch (error) {
      view.innerHTML = ui.errorCard(errorMessage(error));
    }
  }

  function clockTemplate(data) {
    const speed = Number(data.speed) || 1;
    const presetButtons = [6, 12, 18, 0].map(function (hour) {
      return '<button class="chip" type="button" data-hour="' + hour + '">' +
        fmt.pad(hour) + ':00</button>';
    }).join('');

    return '<div class="page-head">' +
        '<div>' +
          '<p class="eyebrow">鱼塘时钟 · POND CLOCK</p>' +
          '<h1>时间与倍速控制</h1>' +
          '<p class="page-sub">暂停不会清空历史；跳转时间会改变水温与光照曲线。</p>' +
        '</div>' +
        '<div class="page-actions">' +
          '<span class="badge ' + (data.paused ? 'warn' : 'good') + '" id="clockHeaderBadge">' +
            (data.paused ? '已暂停' : '运行中') + ' · ×' + speed +
          '</span>' +
        '</div>' +
      '</div>' +
      '<div class="clock-layout">' +
        '<article class="card clock-hero">' +
          '<div class="clock-hero-top">' +
            '<span class="eyebrow">SIMULATED POND TIME</span>' +
            '<span class="badge neutral" id="clockStateBadge">--</span>' +
          '</div>' +
          '<div class="clock-big" id="clockBigTime">--:--</div>' +
          '<div class="clock-sub" id="clockBigDate">--</div>' +
          '<div class="clock-meta">' +
            '<div><span>模拟倍速</span><strong id="clockSpeed">×' + speed + '</strong></div>' +
            '<div><span>已运行</span><strong id="clockElapsed">' + fmt.elapsed(data.elapsedMinutes) + '</strong></div>' +
            '<div><span>当前溶氧</span><strong id="clockDo">' + fmt.num(data.currentDo) + '</strong></div>' +
            '<div><span>水体温度</span><strong id="clockTemp">' + fmt.num(data.temperature, 1) + ' ℃</strong></div>' +
          '</div>' +
          '<button class="btn primary lg" id="clockPauseBtn" type="button">' +
            icon(data.paused ? 'play' : 'pause') + (data.paused ? '继续仿真' : '暂停仿真') +
          '</button>' +
          '<p class="tiny muted">暂停后仿真引擎不再推进鱼塘时间，恢复后从当前时刻继续。</p>' +
        '</article>' +
        '<div class="clock-side">' +
          '<article class="card">' +
            '<div class="card-head"><div><h3>模拟倍速</h3><p>1 真实秒对应多少模拟秒</p></div></div>' +
            '<div class="segmented wrap" id="speedSeg">' +
              [1, 10, 60, 600, 3600].map(function (value) {
                return '<button type="button" data-speed="' + value + '">' + value + '×</button>';
              }).join('') +
            '</div>' +
            '<div class="field" style="margin-top:16px">' +
              '<label for="speedCustom">自定义倍速（1 - 3600）</label>' +
              '<div class="input-row">' +
                '<input class="input" id="speedCustom" type="number" min="1" max="3600" step="1" value="' + speed + '">' +
                '<button class="btn soft" id="speedApply" type="button">应用</button>' +
              '</div>' +
            '</div>' +
            '<p class="field-hint" id="speedHint">' + esc(fmt.speedPerRealMinute(speed)) + '</p>' +
          '</article>' +
          '<article class="card">' +
            '<div class="card-head"><div><h3>跳转鱼塘时间</h3><p>只改时钟，不清空历史</p></div></div>' +
            '<div class="field">' +
              '<label for="pondTimeInput">目标时间</label>' +
              '<input class="input" id="pondTimeInput" type="datetime-local" value="' + esc(fmt.dateInputValue(data.pondTime)) + '">' +
            '</div>' +
            '<div class="preset-row" id="timePresets">' + presetButtons + '</div>' +
            '<div class="form-actions" style="margin-top:16px;padding-top:14px">' +
              '<button class="btn primary" id="timeApply" type="button">' + icon('calendar') + '跳转时间</button>' +
            '</div>' +
            '<p class="field-hint">时间会影响水温与光照曲线，但不会改变已运行分钟数。</p>' +
          '</article>' +
        '</div>' +
      '</div>';
  }

  function bindClockView(data) {
    syncSpeedButtons(data.speed);

    const pauseBtn = el('clockPauseBtn');
    if (pauseBtn) {
      pauseBtn.addEventListener('click', function () {
        busy(pauseBtn, togglePause).then(function () {
          if (state.realtime) {
            updateClockLive(state.realtime);
            updateQuickClock(state.realtime);
          }
        });
      });
    }

    const speedSeg = el('speedSeg');
    if (speedSeg) {
      speedSeg.addEventListener('click', function (event) {
        const button = event.target.closest('button[data-speed]');
        if (!button) return;
        changeSpeed(Number(button.dataset.speed), button);
      });
    }

    const speedApply = el('speedApply');
    if (speedApply) {
      speedApply.addEventListener('click', function () {
        const input = el('speedCustom');
        const value = Number(input && input.value);
        if (!Number.isInteger(value) || value < 1 || value > 3600) {
          ui.toast('倍速必须是 1 - 3600 之间的整数', 'warn');
          return;
        }
        busy(speedApply, function () {
          return changeSpeed(value, null).then(function () {
            setText('speedHint', fmt.speedPerRealMinute(value));
          });
        });
      });
    }

    const timeApply = el('timeApply');
    if (timeApply) {
      timeApply.addEventListener('click', function () {
        const input = el('pondTimeInput');
        busy(timeApply, function () { return setPondTime(input ? input.value : ''); });
      });
    }

    const presets = el('timePresets');
    if (presets) {
      presets.addEventListener('click', function (event) {
        const button = event.target.closest('button[data-hour]');
        if (!button) return;
        const current = state.realtime ? state.realtime.pondTime : null;
        const date = fmt.date(current);
        const input = el('pondTimeInput');
        if (input) {
          input.value = date + 'T' + fmt.pad(Number(button.dataset.hour)) + ':00';
          input.focus();
        }
      });
    }
  }

  function updateClockLive(data) {
    if (!data) return;
    const hero = document.querySelector('#view-clock .clock-hero');
    if (!hero) return;

    setText('clockBigTime', fmt.time(data.pondTime));
    setText('clockBigDate', fmt.dateLong(data.pondTime));
    setBadge('clockStateBadge', data.paused ? '已暂停' : '运行中', data.paused ? 'warn' : 'good');
    setText('clockSpeed', '×' + (data.speed || 1));
    setText('clockElapsed', fmt.elapsed(data.elapsedMinutes));
    setText('clockDo', fmt.num(data.currentDo) + ' mg/L');
    setText('clockTemp', fmt.num(data.temperature, 1) + ' ℃');
    setBadge('clockHeaderBadge', (data.paused ? '已暂停' : '运行中') + ' · ×' + (data.speed || 1), data.paused ? 'warn' : 'good');

    const pauseBtn = el('clockPauseBtn');
    if (pauseBtn) pauseBtn.innerHTML = icon(data.paused ? 'play' : 'pause') + (data.paused ? '继续仿真' : '暂停仿真');

    const input = el('speedCustom');
    if (input && document.activeElement !== input) input.value = data.speed || 1;
    setText('speedHint', fmt.speedPerRealMinute(data.speed || 1));
    syncSpeedButtons(data.speed);
  }

  /* ------------------------------ 水体参数页 ------------------------------ */

  async function loadPondView() {
    const view = el('view-pond');
    if (!view) return;
    view.innerHTML = ui.loadingCard('正在读取水体参数…');

    try {
      const config = await api.pond.get(state.activePondId);
      state.pondConfig = config;
      applyPondIdentity(config);
      view.innerHTML = pondTemplate(config);
      bindPondView(config);
      updatePondInfo();
      if (!state.realtime) pollRealtime();
    } catch (error) {
      view.innerHTML = ui.errorCard(errorMessage(error));
    }
  }

  function applyPondIdentity(config) {
    if (!config) return;
    setText('sidebarPondName', config.pondName || pondLabel(pondById(state.activePondId)));
    setText('topPondEyebrow', 'POND ' + fmt.pad(Number(config.id || state.activePondId)));
    const pond = pondById(config.id || state.activePondId);
    if (pond) pond.pondName = config.pondName;
    if (state.realtime) {
      setBadge('mTempMode', config.temperatureModeDesc || '水温', 'neutral');
    }
  }

  function pondTemplate(config) {
    function fieldNumber(id, label, value, min, max, step, hint) {
      return '<div class="field">' +
        '<label for="' + id + '">' + esc(label) + '</label>' +
        '<input class="input" id="' + id + '" name="' + id + '" type="number" min="' + min + '" max="' + max +
          '" step="' + step + '" value="' + esc(value) + '">' +
        (hint ? '<span class="field-hint">' + esc(hint) + '</span>' : '') +
      '</div>';
    }

    return '<div class="page-head">' +
        '<div>' +
          '<p class="eyebrow">水体参数 · WATER MODEL</p>' +
          '<h1>玩具水体模型参数</h1>' +
          '<p class="page-sub">白天通过光合作用产氧，夜间持续耗氧；增氧机按缺氧程度补氧。</p>' +
        '</div>' +
        '<div class="page-actions">' +
          '<span class="badge neutral">' + esc(config.temperatureModeDesc || '日内曲线') + '</span>' +
        '</div>' +
      '</div>' +
      '<div class="two-col">' +
        '<article class="card">' +
          '<form id="pondForm" novalidate>' +
            '<div class="form-grid">' +
              '<div class="field" style="grid-column:1 / -1">' +
                '<label for="pondName">鱼塘名称</label>' +
                '<input class="input" id="pondName" name="pondName" maxlength="64" value="' + esc(config.pondName || '') + '">' +
              '</div>' +
              '<div class="field">' +
                '<label for="temperatureMode">温度模式</label>' +
                '<select class="input" id="temperatureMode" name="temperatureMode">' +
                  option('diurnal', '日内曲线（余弦昼夜温差）', config.temperatureMode === 'diurnal') +
                  option('fixed', '固定值（不随昼夜变化）', config.temperatureMode === 'fixed') +
                '</select>' +
                '<span class="field-hint">日内曲线在 15:00 达到最高温。</span>' +
              '</div>' +
              fieldNumber('baseTemperature', '基准温度（℃）', config.baseTemperature, 0, 45, 0.1, '允许 0 - 45 ℃') +
              fieldNumber('dailyAmplitude', '昼夜温差振幅（℃）', config.dailyAmplitude, 0, 15, 0.1, '固定模式下不生效') +
              fieldNumber('doMin', '溶氧量程下限（mg/L）', config.doMin, 0, 20, 0.1, '参考范围，不是告警阈值') +
              fieldNumber('doMax', '溶氧量程上限（mg/L）', config.doMax, 0, 30, 0.1, '必须大于量程下限') +
              fieldNumber('produceCoefficient', '产氧系数', config.produceCoefficient, 0, 5, 0.01, '白天产氧强度，0 - 5') +
              fieldNumber('consumeCoefficient', '耗氧系数', config.consumeCoefficient, 0, 5, 0.01, '温度越高耗氧越快，0 - 5') +
              fieldNumber('aeratorRate', '增氧速率', config.aeratorRate, 0, 10, 0.01, '增氧机满负荷补氧强度，0 - 10') +
            '</div>' +
            '<div class="form-actions">' +
              '<button class="btn ghost" type="button" id="pondResetBtn">还原</button>' +
              '<button class="btn primary" type="submit">保存参数</button>' +
            '</div>' +
          '</form>' +
        '</article>' +
        '<aside class="side-col">' +
          '<article class="card info-card">' +
            '<h3>模型速览</h3>' +
            '<p>这是用于练手的简化水体，不追求真实物理公式：光照决定产氧，水温决定耗氧速度，增氧机只在缺氧时起作用。</p>' +
            '<ul class="info-list">' +
              '<li><span>当前水温</span><strong id="pondCurrentTemp">--</strong></li>' +
              '<li><span>饱和溶氧</span><strong id="pondSat">--</strong></li>' +
              '<li><span>当前溶氧</span><strong id="pondCurrentDo">--</strong></li>' +
              '<li><span>温度模式</span><strong id="pondModeDesc">--</strong></li>' +
              '<li><span>量程</span><strong id="pondRange">--</strong></li>' +
            '</ul>' +
            '<div class="preset-block">' +
              '<p class="tiny muted">场景预设会填入表单，点击“保存参数”后生效。</p>' +
              '<div class="preset-row">' +
                '<button class="chip" type="button" data-preset="summer">夏日晴好</button>' +
                '<button class="chip" type="button" data-preset="muggy">闷热缺氧</button>' +
                '<button class="chip" type="button" data-preset="winter">冬季低温</button>' +
              '</div>' +
            '</div>' +
          '</article>' +
        '</aside>' +
      '</div>';
  }

  function bindPondView(config) {
    const form = el('pondForm');
    if (!form) return;

    const modeSelect = form.elements.namedItem('temperatureMode');
    const amplitude = form.elements.namedItem('dailyAmplitude');

    function syncAmplitude() {
      if (!amplitude) return;
      const fixed = modeSelect && modeSelect.value === 'fixed';
      amplitude.disabled = !!fixed;
      amplitude.closest('.field').classList.toggle('is-disabled', !!fixed);
    }
    if (modeSelect) modeSelect.addEventListener('change', syncAmplitude);
    syncAmplitude();

    const resetBtn = el('pondResetBtn');
    if (resetBtn) {
      resetBtn.addEventListener('click', function () {
        fillPondForm(form, config);
        syncAmplitude();
        ui.toast('已还原为服务器上的当前参数', 'info');
      });
    }

    document.querySelectorAll('#view-pond [data-preset]').forEach(function (button) {
      button.addEventListener('click', function () {
        const preset = POND_PRESETS[button.dataset.preset];
        if (!preset) return;
        Object.keys(preset).forEach(function (key) {
          const input = form.elements.namedItem(key);
          if (input) input.value = preset[key];
        });
        syncAmplitude();
        ui.toast('已填入场景预设，保存后生效', 'info');
      });
    });

    form.addEventListener('submit', function (event) {
      event.preventDefault();
      const submitBtn = form.querySelector('button[type="submit"]');
      busy(submitBtn, async function () {
        const payload = {
          pondName: String(form.elements.namedItem('pondName').value || '').trim(),
          temperatureMode: form.elements.namedItem('temperatureMode').value,
          baseTemperature: Number(form.elements.namedItem('baseTemperature').value),
          dailyAmplitude: Number(form.elements.namedItem('dailyAmplitude').value),
          doMin: Number(form.elements.namedItem('doMin').value),
          doMax: Number(form.elements.namedItem('doMax').value),
          produceCoefficient: Number(form.elements.namedItem('produceCoefficient').value),
          consumeCoefficient: Number(form.elements.namedItem('consumeCoefficient').value),
          aeratorRate: Number(form.elements.namedItem('aeratorRate').value)
        };

        const validation = validatePondPayload(payload);
        if (validation) {
          ui.toast(validation, 'warn', '请检查参数');
          return;
        }

        try {
          const updated = await api.pond.update(payload, state.activePondId);
          state.pondConfig = updated;
          applyPondIdentity(updated);
          updatePondInfo();
          ui.toast('水体参数已保存，仿真引擎已热更新', 'success');
          pollRealtime();
        } catch (error) {
          ui.toast(errorMessage(error), 'error', '保存失败');
        }
      });
    });
  }

  const POND_PRESETS = {
    summer: {
      temperatureMode: 'diurnal',
      baseTemperature: '26',
      dailyAmplitude: '4',
      doMin: '3',
      doMax: '12',
      produceCoefficient: '0.65',
      consumeCoefficient: '0.10',
      aeratorRate: '1.4'
    },
    muggy: {
      temperatureMode: 'diurnal',
      baseTemperature: '31',
      dailyAmplitude: '3',
      doMin: '2',
      doMax: '10',
      produceCoefficient: '0.45',
      consumeCoefficient: '0.22',
      aeratorRate: '1.8'
    },
    winter: {
      temperatureMode: 'diurnal',
      baseTemperature: '11',
      dailyAmplitude: '2',
      doMin: '4',
      doMax: '14',
      produceCoefficient: '0.35',
      consumeCoefficient: '0.035',
      aeratorRate: '1.2'
    }
  };

  function fillPondForm(form, config) {
    const values = {
      pondName: config.pondName || '',
      temperatureMode: config.temperatureMode || 'diurnal',
      baseTemperature: config.baseTemperature,
      dailyAmplitude: config.dailyAmplitude,
      doMin: config.doMin,
      doMax: config.doMax,
      produceCoefficient: config.produceCoefficient,
      consumeCoefficient: config.consumeCoefficient,
      aeratorRate: config.aeratorRate
    };
    Object.keys(values).forEach(function (key) {
      const input = form.elements.namedItem(key);
      if (input && values[key] !== null && values[key] !== undefined) input.value = values[key];
    });
  }

  function validatePondPayload(payload) {
    if (!payload.pondName) return '鱼塘名称不能为空';
    if (payload.pondName.length > 64) return '鱼塘名称最多 64 个字符';
    if (payload.temperatureMode !== 'diurnal' && payload.temperatureMode !== 'fixed') return '温度模式只能是 diurnal 或 fixed';
    const checks = [
      [payload.baseTemperature, 0, 45, '基准温度需在 0 - 45 ℃'],
      [payload.dailyAmplitude, 0, 15, '昼夜温差振幅需在 0 - 15 ℃'],
      [payload.doMin, 0, Infinity, '溶氧量程下限不能为负'],
      [payload.doMax, 0, Infinity, '溶氧量程上限不能为负'],
      [payload.produceCoefficient, 0, 5, '产氧系数需在 0 - 5'],
      [payload.consumeCoefficient, 0, 5, '耗氧系数需在 0 - 5'],
      [payload.aeratorRate, 0, 10, '增氧速率需在 0 - 10']
    ];
    for (let i = 0; i < checks.length; i++) {
      const value = checks[i][0];
      if (!Number.isFinite(value) || value < checks[i][1] || value > checks[i][2]) return checks[i][3];
    }
    if (payload.doMax <= payload.doMin) return '溶氧量程上限必须大于下限';
    return null;
  }

  function updatePondInfo() {
    if (!state.realtime) return;
    const data = state.realtime;
    setText('pondCurrentTemp', fmt.num(data.temperature, 1) + ' ℃');
    setText('pondSat', fmt.num(fmt.saturationDo(data.temperature), 2) + ' mg/L');
    setText('pondCurrentDo', fmt.num(data.currentDo) + ' mg/L');
    if (state.pondConfig) {
      setText('pondModeDesc', state.pondConfig.temperatureModeDesc || state.pondConfig.temperatureMode || '--');
      setText('pondRange', fmt.num(state.pondConfig.doMin, 1) + ' - ' + fmt.num(state.pondConfig.doMax, 1) + ' mg/L');
    }
  }

  /* ------------------------------ 增氧策略页 ------------------------------ */

  async function loadAeratorView() {
    const view = el('view-aerator');
    if (!view) return;
    view.innerHTML = ui.loadingCard('正在读取增氧策略…');

    try {
      const results = await Promise.all([
        api.aerator.getStrategy(state.activePondId),
        api.settings.get(state.activePondId).catch(function () { return state.settings; }),
        api.electricity.get().catch(function () { return state.electricity; }),
        api.realtime(state.chart.predictMinutes, state.chart.historyLimit, state.activePondId).catch(function () { return state.realtime; })
      ]);
      state.strategy = results[0];
      if (results[1]) state.settings = results[1];
      if (results[2]) state.electricity = results[2];
      if (results[3]) {
        state.realtime = results[3];
        applyRealtime(results[3]);
      }
      view.innerHTML = aeratorTemplate(state.strategy);
      bindAeratorView(state.strategy);
      renderStrategyScale(state.strategy);
      updateStrategyLive(state.realtime);
      if (!state.pondConfig) loadPondConfig();
    } catch (error) {
      view.innerHTML = ui.errorCard(errorMessage(error));
    }
  }

  function aeratorTemplate(strategy) {
    return '<div class="page-head">' +
        '<div>' +
          '<p class="eyebrow">增氧策略 · AERATOR</p>' +
          '<h1>自动与手动增氧</h1>' +
          '<p class="page-sub">安全否决优先于一切；自动模式根据当前与预测溶氧决定开关机。</p>' +
        '</div>' +
        '<div class="page-actions">' +
          '<span class="badge neutral">' + esc(strategy.modeDesc || strategy.mode) + '</span>' +
        '</div>' +
      '</div>' +
      '<div class="two-col">' +
        '<article class="card">' +
          '<form id="strategyForm" novalidate>' +
            '<h3 style="margin-bottom:11px">控制模式</h3>' +
            '<div class="mode-cards">' +
              '<button class="mode-card' + (strategy.mode === 'auto' ? ' active' : '') + '" type="button" data-mode="auto">' +
                icon('activity') +
                '<strong>自动控制</strong>' +
                '<span>按阈值与预测自动开机关机</span>' +
              '</button>' +
              '<button class="mode-card' + (strategy.mode === 'manual' ? ' active' : '') + '" type="button" data-mode="manual">' +
                icon('power') +
                '<strong>手动控制</strong>' +
                '<span>由人工决定增氧机目标状态</span>' +
              '</button>' +
            '</div>' +
            '<input type="hidden" name="mode" value="' + esc(strategy.mode) + '">' +
            '<div class="form-grid">' +
              numberField('onThreshold', '开机阈值（mg/L）', strategy.onThreshold, 0, 20, 0.1, '溶氧低于该值自动开机') +
              numberField('offThreshold', '关机阈值（mg/L）', strategy.offThreshold, 0, 20, 0.1, '必须大于开机阈值') +
              numberField('safetyFloor', '安全下限（mg/L）', strategy.safetyFloor, 0, 20, 0.1, '不能高于开机阈值') +
              numberField('predictMinutes', '提前开机分钟数', strategy.predictMinutes, 0, 1440, 1, '0 表示不做预测开机') +
            '</div>' +
            '<div class="switch-row" style="margin-top:16px">' +
              '<div><strong>手动开关状态</strong><p>手动模式下增氧机的目标状态</p></div>' +
              '<button class="switch" id="manualSwitch" type="button" role="switch" aria-checked="' + (strategy.manualOn ? 'true' : 'false') + '"></button>' +
            '</div>' +
            '<div class="form-actions">' +
              '<button class="btn ghost" id="strategyReload" type="button">还原</button>' +
              '<button class="btn primary" type="submit">保存策略</button>' +
            '</div>' +
          '</form>' +
        '</article>' +
        '<aside class="side-col">' +
          '<article class="card">' +
            '<div class="card-head"><div><h3>当前设备状态</h3><p>仿真引擎每秒决策一次</p></div></div>' +
            '<div class="live-aerator">' +
              '<div class="aerator-orb" id="strategyLiveOrb">' + icon('fan') + '</div>' +
              '<div>' +
                '<div class="live-aerator-title" id="strategyLiveState">--</div>' +
                '<div class="muted small" id="strategyLiveMode">--</div>' +
              '</div>' +
            '</div>' +
            '<div class="quick-actions">' +
              '<button class="btn primary" id="manualOnBtn" type="button">' + icon('power') + '手动开机</button>' +
              '<button class="btn ghost" id="manualOffBtn" type="button">手动关机</button>' +
            '</div>' +
            '<p class="tiny muted" style="margin-top:9px">手动开关会把模式切换为“手动”，并写入操作日志。</p>' +
          '</article>' +
          '<article class="card">' +
            '<div class="card-head"><div><h3>阈值分布</h3><p>安全区与开关机区间</p></div></div>' +
            '<div class="threshold-track" id="strategyScale"></div>' +
            '<div class="kv-list">' +
              '<div><span>当前溶氧</span><strong id="aCurrentDo">--</strong></div>' +
              '<div><span>安全否决</span><strong id="vetoState">--</strong></div>' +
              '<div><span>当前电价</span><strong id="aCurrentPrice">--</strong></div>' +
            '</div>' +
            '<p class="field-hint" style="margin-top:9px">溶氧低于安全下限且开启安全否决时，会立即强制开机。</p>' +
          '</article>' +
        '</aside>' +
      '</div>';
  }

  function numberField(id, label, value, min, max, step, hint) {
    return '<div class="field">' +
      '<label for="' + id + '">' + esc(label) + '</label>' +
      '<input class="input" id="' + id + '" name="' + id + '" type="number" min="' + min + '" max="' + max +
        '" step="' + step + '" value="' + esc(value) + '">' +
      (hint ? '<span class="field-hint">' + esc(hint) + '</span>' : '') +
    '</div>';
  }

  function bindAeratorView(strategy) {
    const form = el('strategyForm');
    if (!form) return;

    form.querySelectorAll('.mode-card').forEach(function (card) {
      card.addEventListener('click', function () {
        form.elements.namedItem('mode').value = card.dataset.mode;
        form.querySelectorAll('.mode-card').forEach(function (item) {
          item.classList.toggle('active', item === card);
        });
      });
    });

    const manualSwitch = el('manualSwitch');
    if (manualSwitch) {
      manualSwitch.addEventListener('click', function () {
        const current = manualSwitch.getAttribute('aria-checked') === 'true';
        manualSwitch.setAttribute('aria-checked', String(!current));
      });
    }

    const reloadBtn = el('strategyReload');
    if (reloadBtn) reloadBtn.addEventListener('click', function () { loadAeratorView(); });

    const manualOnBtn = el('manualOnBtn');
    if (manualOnBtn) manualOnBtn.addEventListener('click', function () {
      busy(manualOnBtn, function () { return manualAerator(true); });
    });
    const manualOffBtn = el('manualOffBtn');
    if (manualOffBtn) manualOffBtn.addEventListener('click', function () {
      busy(manualOffBtn, function () { return manualAerator(false); });
    });

    ['onThreshold', 'offThreshold', 'safetyFloor'].forEach(function (id) {
      const input = el(id);
      if (input) input.addEventListener('input', function () { renderStrategyScaleFromForm(); });
    });

    form.addEventListener('submit', function (event) {
      event.preventDefault();
      const submitBtn = form.querySelector('button[type="submit"]');
      busy(submitBtn, async function () {
        const payload = {
          mode: form.elements.namedItem('mode').value,
          onThreshold: Number(form.elements.namedItem('onThreshold').value),
          offThreshold: Number(form.elements.namedItem('offThreshold').value),
          safetyFloor: Number(form.elements.namedItem('safetyFloor').value),
          predictMinutes: Number(form.elements.namedItem('predictMinutes').value),
          manualOn: manualSwitch ? manualSwitch.getAttribute('aria-checked') === 'true' : false
        };

        const validation = validateStrategy(payload);
        if (validation) {
          ui.toast(validation, 'warn', '请检查策略参数');
          return;
        }

        try {
          const updated = await api.aerator.updateStrategy(payload, state.activePondId);
          state.strategy = updated;
          applyStrategyToForm(updated);
          renderStrategyScale(updated);
          pollRealtime();
          ui.toast('增氧策略已保存并热更新', 'success');
        } catch (error) {
          ui.toast(errorMessage(error), 'error', '保存失败');
        }
      });
    });

    updateVetoState();
  }

  function validateStrategy(payload) {
    if (payload.mode !== 'auto' && payload.mode !== 'manual') return '控制模式不合法';
    const values = [payload.onThreshold, payload.offThreshold, payload.safetyFloor];
    if (values.some(function (v) { return !Number.isFinite(v) || v < 0 || v > 20; })) {
      return '三个溶氧阈值都必须在 0 - 20 mg/L 之间';
    }
    if (payload.offThreshold <= payload.onThreshold) return '关机阈值必须大于开机阈值，否则增氧机会反复开关';
    if (payload.safetyFloor > payload.onThreshold) return '安全下限不能高于开机阈值';
    if (!Number.isInteger(payload.predictMinutes) || payload.predictMinutes < 0 || payload.predictMinutes > 1440) {
      return '提前开机分钟数需为 0 - 1440 的整数';
    }
    return null;
  }

  function applyStrategyToForm(strategy) {
    const form = el('strategyForm');
    if (!form || !strategy) return;
    const modeInput = form.elements.namedItem('mode');
    if (modeInput) modeInput.value = strategy.mode;
    form.querySelectorAll('.mode-card').forEach(function (card) {
      card.classList.toggle('active', card.dataset.mode === strategy.mode);
    });
    const switchEl = el('manualSwitch');
    if (switchEl) switchEl.setAttribute('aria-checked', String(!!strategy.manualOn));
    ['onThreshold', 'offThreshold', 'safetyFloor', 'predictMinutes'].forEach(function (id) {
      const input = el(id);
      if (input && strategy[id] !== undefined) input.value = strategy[id];
    });
    updateVetoState();
    if (state.realtime) updateStrategyLive(state.realtime);
  }

  function updateVetoState() {
    if (!state.settings) return;
    const enabled = !!state.settings.safetyVetoEnabled;
    const node = el('vetoState');
    if (node) {
      node.textContent = enabled ? '已启用' : '已关闭';
      node.style.color = enabled ? 'var(--success)' : 'var(--warning)';
    }
  }

  function renderStrategyScale(strategy) {
    const scale = el('strategyScale');
    if (!scale || !strategy) return;
    const currentDo = state.realtime ? Number(state.realtime.currentDo) : null;
    const off = Number(strategy.offThreshold) || 0;
    const max = Math.max(12, off + 2, (currentDo || 0) + 1);
    scale.dataset.max = String(max);
    const safety = Number(strategy.safetyFloor);
    const on = Number(strategy.onThreshold);

    scale.style.background =
      'linear-gradient(90deg,' +
      'color-mix(in srgb, var(--danger) 30%, transparent) 0 ' + pct(safety, max) + '%,' +
      'color-mix(in srgb, var(--warning) 32%, transparent) ' + pct(safety, max) + '% ' + pct(on, max) + '%,' +
      'color-mix(in srgb, var(--success) 28%, transparent) ' + pct(on, max) + '% ' + pct(off, max) + '%,' +
      'color-mix(in srgb, var(--info) 25%, transparent) ' + pct(off, max) + '% 100%)';

    scale.innerHTML =
      marker(safety, max, '安全 ' + fmt.num(safety, 1), 'var(--chart-floor)') +
      marker(on, max, '开机 ' + fmt.num(on, 1), 'var(--chart-on)') +
      marker(off, max, '关机 ' + fmt.num(off, 1), 'var(--chart-off)') +
      '<span class="threshold-current" id="strategyScaleCurrent" style="left:' + pct(currentDo || 0, max) + '%"></span>';
  }

  function marker(value, max, label, color) {
    if (!Number.isFinite(Number(value))) return '';
    return '<span class="threshold-marker" style="left:' + pct(value, max) + '%;--marker:' + color + '" data-label="' + esc(label) + '"></span>';
  }

  function renderStrategyScaleFromForm() {
    const form = el('strategyForm');
    const scale = el('strategyScale');
    if (!form || !scale) return;
    const on = Number(form.elements.namedItem('onThreshold').value);
    const off = Number(form.elements.namedItem('offThreshold').value);
    const safety = Number(form.elements.namedItem('safetyFloor').value);
    if (![on, off, safety].every(Number.isFinite)) return;
    renderStrategyScale({
      onThreshold: on,
      offThreshold: off,
      safetyFloor: safety
    });
  }

  function updateStrategyLive(data) {
    if (!data) return;
    const liveView = document.querySelector('#view-aerator .live-aerator');
    const orb = el('strategyLiveOrb');
    if (orb) orb.classList.toggle('on', !!data.aeratorOn);
    if (liveView) {
      setText('strategyLiveState', data.aeratorOn ? '运行中' : '已停止');
      const mode = state.strategy
        ? (state.strategy.modeDesc || state.strategy.mode)
        : '--';
      setText('strategyLiveMode', mode + '模式 · 当前溶氧 ' + fmt.num(data.currentDo) + ' mg/L');
      setText('aCurrentDo', fmt.num(data.currentDo) + ' mg/L');
      setText('aCurrentPrice', currentPriceText());
      updateVetoState();

      const dot = el('strategyScaleCurrent');
      const scale = el('strategyScale');
      if (dot && scale) {
        const max = Number(scale.dataset.max) || 12;
        dot.style.left = pct(data.currentDo, max) + '%';
      }
    }
  }

  /* ------------------------------ 电价管理页 ------------------------------ */

  let priceRows = [];

  async function loadElectricityView() {
    const view = el('view-electricity');
    if (!view) return;
    view.innerHTML = ui.loadingCard('正在读取分时电价…');

    try {
      const data = await api.electricity.get();
      state.electricity = data;
      priceRows = (data.periods || []).map(function (period) {
        return {
          periodType: fmt.normalizePeriodType(period.periodType),
          periodDesc: period.periodDesc || '',
          startHour: Number(period.startHour),
          endHour: Number(period.endHour),
          price: Number(period.price)
        };
      });
      view.innerHTML = electricityTemplate(data);
      bindElectricityView();
      renderPriceRows();
      updatePriceQuick(state.realtime ? state.realtime.pondTime : null);
    } catch (error) {
      view.innerHTML = ui.errorCard(errorMessage(error));
    }
  }

  function electricityTemplate(data) {
    return '<div class="page-head">' +
        '<div>' +
          '<p class="eyebrow">电价管理 · TOU PRICE</p>' +
          '<h1>峰平谷分时电价</h1>' +
          '<p class="page-sub">所有时段首尾相接覆盖 0 - 24 点；当前鱼塘时间会跟随时钟变化。</p>' +
        '</div>' +
        '<div class="page-actions">' +
          '<button class="btn ghost" id="priceAdd" type="button">' + icon('plus') + '拆分添加</button>' +
          '<button class="btn ghost" id="priceFill" type="button">补齐空档</button>' +
          '<button class="btn primary" id="priceSave" type="button">' + icon('check') + '保存电价</button>' +
        '</div>' +
      '</div>' +
      '<div class="two-col">' +
        '<article class="card">' +
          '<div class="card-head">' +
            '<div><h3>峰平谷时段</h3><p>可以自由拆分或合并时段，但必须完整覆盖一天</p></div>' +
            '<span class="badge neutral" id="priceCoverage">--</span>' +
          '</div>' +
          '<div class="table-wrap">' +
            '<table class="edit-table">' +
              '<thead><tr><th style="width:120px">类型</th><th style="width:110px">开始</th><th style="width:110px">结束</th><th>单价（元 / kWh）</th><th style="width:46px"></th></tr></thead>' +
              '<tbody id="priceRows"></tbody>' +
            '</table>' +
          '</div>' +
          '<p class="field-hint" id="priceError" style="margin-top:9px"></p>' +
          '<div class="price-band" id="priceBand" style="margin-top:16px"></div>' +
          '<div class="price-legend"><span><i class="peak"></i>峰</span><span><i class="flat"></i>平</span><span><i class="valley"></i>谷</span><span style="margin-left:auto">深色竖线 = 当前鱼塘小时</span></div>' +
        '</article>' +
        '<aside class="side-col">' +
          '<article class="card">' +
            '<div class="card-head"><div><h3>当前时段</h3><p>跟随鱼塘时间实时变化</p></div></div>' +
            '<div class="price-current" id="priceCurrent">--</div>' +
            '<div class="kv-list">' +
              '<div><span>鱼塘时间</span><strong id="pricePondTime">--</strong></div>' +
              '<div><span>当前小时</span><strong id="priceHour">--</strong></div>' +
            '</div>' +
          '</article>' +
          '<article class="card">' +
            '<div class="card-head"><div><h3>24 小时电价</h3><p>按小时展开的单价</p></div></div>' +
            '<div class="price-bars" id="priceBars"></div>' +
            '<div class="price-legend" style="margin-top:22px"><span><i class="peak"></i>峰</span><span><i class="flat"></i>平</span><span><i class="valley"></i>谷</span></div>' +
          '</article>' +
        '</aside>' +
      '</div>';
  }

  function bindElectricityView() {
    const addBtn = el('priceAdd');
    if (addBtn) {
      addBtn.addEventListener('click', function () {
        const sorted = priceRows.slice().sort(function (a, b) { return a.startHour - b.startHour; });
        let target = null;
        for (let i = 0; i < sorted.length; i++) {
          if (sorted[i].endHour - sorted[i].startHour >= 2) { target = sorted[i]; break; }
        }
        if (!target) {
          ui.toast('没有可拆分的时段（每个时段至少需要 2 小时）', 'warn');
          return;
        }
        const mid = Math.floor((target.startHour + target.endHour) / 2);
        const index = priceRows.indexOf(target);
        const second = {
          periodType: target.periodType,
          startHour: mid,
          endHour: target.endHour,
          price: target.price
        };
        priceRows[index] = {
          periodType: target.periodType,
          startHour: target.startHour,
          endHour: mid,
          price: target.price
        };
        priceRows.splice(index + 1, 0, second);
        renderPriceRows();
      });
    }

    const fillBtn = el('priceFill');
    if (fillBtn) {
      fillBtn.addEventListener('click', function () {
        const analysis = analyzePriceRows(priceRows);
        if (!analysis.gaps.length) {
          ui.toast('当前时段已经完整覆盖 0 - 24 点', 'info');
          return;
        }
        analysis.gaps.forEach(function (gap) {
          priceRows.push({
            periodType: 'flat',
            startHour: gap[0],
            endHour: gap[1],
            price: 0.7
          });
        });
        ui.toast('已用“平”段补齐 ' + analysis.gaps.length + ' 个空档，可继续调整', 'success');
        renderPriceRows();
      });
    }

    const saveBtn = el('priceSave');
    if (saveBtn) {
      saveBtn.addEventListener('click', function () {
        const analysis = analyzePriceRows(priceRows);
        if (!analysis.ok) {
          ui.toast(analysis.error || '电价时段不完整', 'warn', '无法保存');
          return;
        }
        const payload = priceRows.slice().sort(function (a, b) { return a.startHour - b.startHour; }).map(function (row) {
          return {
            periodType: row.periodType,
            startHour: Number(row.startHour),
            endHour: Number(row.endHour),
            price: Number(row.price)
          };
        });
        busy(saveBtn, async function () {
          try {
            const updated = await api.electricity.update(payload);
            state.electricity = updated;
            priceRows = (updated.periods || []).map(function (period) {
              return {
                periodType: fmt.normalizePeriodType(period.periodType),
                periodDesc: period.periodDesc || '',
                startHour: Number(period.startHour),
                endHour: Number(period.endHour),
                price: Number(period.price)
              };
            });
            renderPriceRows();
            updatePriceQuick(state.realtime ? state.realtime.pondTime : null);
            ui.toast('分时电价已保存，共 ' + priceRows.length + ' 个时段', 'success');
          } catch (error) {
            ui.toast(errorMessage(error), 'error', '保存失败');
          }
        });
      });
    }
  }

  function renderPriceRows() {
    const tbody = el('priceRows');
    if (!tbody) return;

    tbody.innerHTML = priceRows.map(function (row, index) {
      return '<tr data-index="' + index + '">' +
        '<td>' +
          '<select class="input" data-field="periodType">' +
            option('peak', '峰', row.periodType === 'peak') +
            option('flat', '平', row.periodType === 'flat') +
            option('valley', '谷', row.periodType === 'valley') +
          '</select>' +
        '</td>' +
        '<td><input class="input" type="number" min="0" max="23" step="1" data-field="startHour" value="' + esc(row.startHour) + '"></td>' +
        '<td><input class="input" type="number" min="1" max="24" step="1" data-field="endHour" value="' + esc(row.endHour) + '"></td>' +
        '<td><input class="input" type="number" min="0" max="999" step="0.001" data-field="price" value="' + esc(row.price) + '"></td>' +
        '<td><button class="row-del" type="button" data-delete aria-label="删除时段">' + icon('trash') + '</button></td>' +
      '</tr>';
    }).join('');

    tbody.querySelectorAll('tr').forEach(function (tr) {
      const index = Number(tr.dataset.index);
      tr.querySelectorAll('[data-field]').forEach(function (input) {
        input.addEventListener('input', function () {
          const field = input.dataset.field;
          if (field === 'periodType') priceRows[index][field] = input.value;
          else priceRows[index][field] = Number(input.value);
          refreshPriceDerived();
        });
        input.addEventListener('change', function () {
          if (input.dataset.field === 'periodType') refreshPriceDerived();
        });
      });
      const del = tr.querySelector('[data-delete]');
      if (del) {
        del.addEventListener('click', function () {
          priceRows.splice(index, 1);
          renderPriceRows();
        });
      }
    });

    refreshPriceDerived();
  }

  function analyzePriceRows(rows) {
    const gaps = [];
    const overlaps = [];
    let error = null;

    if (!rows.length) {
      return { ok: false, gaps: [[0, 24]], overlaps: [], error: '至少需要配置一个电价时段' };
    }

    const sorted = rows.slice().sort(function (a, b) { return Number(a.startHour) - Number(b.startHour); });
    for (let i = 0; i < sorted.length; i++) {
      const row = sorted[i];
      const start = Number(row.startHour);
      const end = Number(row.endHour);
      const price = Number(row.price);
      if (['peak', 'flat', 'valley'].indexOf(row.periodType) < 0) {
        error = '时段类型只能是峰 / 平 / 谷';
      } else if (!Number.isInteger(start) || !Number.isInteger(end)) {
        error = '时段小时数必须是整数';
      } else if (start < 0 || end > 24) {
        error = '时段小时数必须在 0 - 24 之间';
      } else if (end <= start) {
        error = '每个时段的结束小时必须大于开始小时（' + start + ' → ' + end + '）';
      } else if (!Number.isFinite(price) || price < 0) {
        error = '电价必须是不小于 0 的数字';
      } else if (price >= 1000) {
        error = '电价最多 3 位整数';
      } else if (Math.abs(price * 1000 - Math.round(price * 1000)) > 1e-6) {
        error = '电价最多保留 3 位小数';
      }
    }

    let cursor = 0;
    sorted.forEach(function (row) {
      const start = Number(row.startHour);
      const end = Number(row.endHour);
      if (!Number.isFinite(start) || !Number.isFinite(end)) return;
      if (start > cursor) gaps.push([cursor, start]);
      else if (start < cursor) overlaps.push([start, Math.min(cursor, end)]);
      cursor = Math.max(cursor, end);
    });
    if (cursor < 24) gaps.push([cursor, 24]);

    if (!error) {
      if (overlaps.length) error = overlaps[0][0] + ' - ' + overlaps[0][1] + ' 点存在重复配置';
      else if (gaps.length) error = gaps[0][0] + ' - ' + gaps[0][1] + ' 点没有配置电价';
    }

    return { ok: !error, gaps: gaps, overlaps: overlaps, error: error };
  }

  function refreshPriceDerived() {
    const analysis = analyzePriceRows(priceRows);

    const coverage = el('priceCoverage');
    if (coverage) {
      if (analysis.ok) {
        coverage.className = 'badge good';
        coverage.textContent = '完整覆盖 24 小时';
      } else if (analysis.overlaps.length) {
        coverage.className = 'badge danger';
        coverage.textContent = '存在重叠';
      } else {
        coverage.className = 'badge warn';
        coverage.textContent = '存在空档';
      }
    }

    const errorNode = el('priceError');
    if (errorNode) {
      errorNode.textContent = analysis.ok ? '时段校验通过，可以保存。' : (analysis.error || '');
      errorNode.className = 'field-hint' + (analysis.ok ? '' : ' error');
    }

    const saveBtn = el('priceSave');
    if (saveBtn) saveBtn.disabled = !analysis.ok;

    const pondTime = state.realtime ? state.realtime.pondTime : null;
    const hour = currentHour(pondTime);
    window.Aqua.renderPriceBand(el('priceBand'), priceRows, hour, { labels: true });
    renderPriceBars();

    const currentPeriod = window.Aqua.priceForHour(priceRows, hour);
    const currentNode = el('priceCurrent');
    if (currentNode) {
      if (currentPeriod) {
        currentNode.innerHTML = '<span class="badge ' + fmt.normalizePeriodType(currentPeriod.periodType) + '">' +
          esc(currentPeriod.periodDesc || periodLabel(currentPeriod.periodType)) + '段</span>' +
          '<strong>' + fmt.num(currentPeriod.price, 2) + '</strong><span>元 / kWh</span>';
      } else {
        currentNode.innerHTML = '<span class="badge neutral">未配置</span><span>当前小时没有电价</span>';
      }
    }
    setText('pricePondTime', pondTime ? fmt.dateTime(pondTime) : '--');
    setText('priceHour', fmt.pad(Math.floor(hour)) + ':00 前后');
  }

  function renderPriceBars() {
    const container = el('priceBars');
    if (!container) return;

    const prices = [];
    for (let h = 0; h < 24; h++) {
      const period = window.Aqua.priceForHour(priceRows, h);
      prices.push(period ? { price: Number(period.price), type: fmt.normalizePeriodType(period.periodType), desc: period.periodDesc } : null);
    }
    const max = Math.max(0.01, Math.max.apply(null, prices.map(function (item) { return item ? item.price : 0; })));

    container.innerHTML = prices.map(function (item, hour) {
      if (!item) {
        return '<div class="bar-col gap" title="' + hour + ':00 未配置">' +
          '<div class="bar" style="height:3px"></div>' +
          (hour % 6 === 0 || hour === 23 ? '<span class="bar-label">' + fmt.pad(hour) + '</span>' : '') +
        '</div>';
      }
      const height = ui.clamp(item.price / max * 100, 4, 100);
      return '<div class="bar-col ' + item.type + '" title="' + fmt.pad(hour) + ':00 - ' + fmt.pad(hour + 1) +
        ':00 · ' + esc(item.desc || periodLabel(item.type)) + ' · ' + fmt.num(item.price, 2) + ' 元/kWh">' +
        '<div class="bar" style="height:' + height + '%"></div>' +
        (hour % 6 === 0 || hour === 23 ? '<span class="bar-label">' + fmt.pad(hour) + '</span>' : '') +
      '</div>';
    }).join('');
  }

  function periodLabel(type) {
    return type === 'peak' ? '峰' : type === 'valley' ? '谷' : '平';
  }

  /* ------------------------------ 告警中心 ------------------------------ */

  async function loadAlertsView() {
    const view = el('view-alerts');
    if (!view) return;

    view.innerHTML = '<div class="page-head">' +
        '<div>' +
          '<p class="eyebrow">告警中心 · ALERT CENTER</p>' +
          '<h1>异常告警与确认</h1>' +
          '<p class="page-sub">告警由仿真引擎自动产生，确认操作会记录操作人与时间。</p>' +
        '</div>' +
        '<div class="page-actions">' +
          '<button class="btn ghost" id="alertRefresh" type="button">' + icon('refresh') + '刷新列表</button>' +
        '</div>' +
      '</div>' +
      '<article class="card">' +
        '<div class="filters">' +
          '<label class="field"><span>鱼塘</span><select class="input" id="alertPond">' +
            pondFilterOptions(state.alerts.pondId) +
          '</select></label>' +
          '<label class="field"><span>等级</span><select class="input" id="alertLevel">' +
            option('', '全部等级', state.alerts.level === '') +
            option('info', '提示', state.alerts.level === 'info') +
            option('warn', '警告', state.alerts.level === 'warn') +
            option('urgent', '紧急', state.alerts.level === 'urgent') +
          '</select></label>' +
          '<label class="field"><span>状态</span><select class="input" id="alertStatus">' +
            option('', '全部状态', state.alerts.status === '') +
            option('pending', '待确认', state.alerts.status === 'pending') +
            option('acknowledged', '已确认', state.alerts.status === 'acknowledged') +
            option('recovered', '已恢复', state.alerts.status === 'recovered') +
          '</select></label>' +
          '<label class="field"><span>类型</span><select class="input" id="alertType">' +
            option('', '全部类型', state.alerts.alertType === '') +
            option('do_safety_floor', '溶氧跌破安全下限', state.alerts.alertType === 'do_safety_floor') +
            option('do_on_threshold', '溶氧低于开机阈值', state.alerts.alertType === 'do_on_threshold') +
          '</select></label>' +
          '<label class="field"><span>每页</span><select class="input" id="alertSize">' +
            option('10', '10 条', state.alerts.size === 10) +
            option('20', '20 条', state.alerts.size === 20) +
            option('50', '50 条', state.alerts.size === 50) +
          '</select></label>' +
          '<button class="btn ghost" id="alertReset" type="button">重置筛选</button>' +
        '</div>' +
        '<div id="alertTableWrap">' + ui.loadingCard('正在加载告警…') + '</div>' +
        '<div class="pager" id="alertPager"></div>' +
      '</article>';

    const refreshBtn = el('alertRefresh');
    if (refreshBtn) refreshBtn.addEventListener('click', function () {
      busy(refreshBtn, fetchAlerts);
    });

    ['alertPond', 'alertLevel', 'alertStatus', 'alertType'].forEach(function (id) {
      const node = el(id);
      if (!node) return;
      node.addEventListener('change', function () {
        const key = id === 'alertPond' ? 'pondId'
          : id === 'alertLevel' ? 'level'
          : id === 'alertStatus' ? 'status' : 'alertType';
        state.alerts[key] = node.value;
        state.alerts.page = 1;
        fetchAlerts();
      });
    });

    const sizeSelect = el('alertSize');
    if (sizeSelect) sizeSelect.addEventListener('change', function () {
      state.alerts.size = Number(sizeSelect.value);
      state.alerts.page = 1;
      fetchAlerts();
    });

    const resetBtn = el('alertReset');
    if (resetBtn) resetBtn.addEventListener('click', function () {
      state.alerts = { page: 1, size: state.alerts.size, level: '', status: '', alertType: '', pondId: '' };
      loadAlertsView();
    });

    fetchAlerts();
  }

  async function fetchAlerts() {
    const wrap = el('alertTableWrap');
    const pager = el('alertPager');
    if (!wrap) return;
    wrap.innerHTML = ui.loadingCard('正在加载告警…');
    if (pager) pager.innerHTML = '';

    try {
      const page = await api.alerts.list({
        page: state.alerts.page,
        size: state.alerts.size,
        level: state.alerts.level,
        status: state.alerts.status,
        alertType: state.alerts.alertType,
        pondId: state.alerts.pondId
      });

      if (!page.records || !page.records.length) {
        wrap.innerHTML = ui.emptyState('没有符合条件的告警', 'bell');
        if (pager) pager.innerHTML = '<span>共 0 条</span>';
        return;
      }

      const rows = page.records.map(function (item) {
        const tone = alertTone(item.level);
        const canAck = item.status !== 'acknowledged';
        const ackInfo = item.ackBy
          ? '<span class="cell-sub">' + esc(item.ackBy) + ' · ' + fmt.dateTimeShort(item.ackTime) + '</span>'
          : '';
        return '<tr>' +
          '<td class="nowrap"><span class="badge neutral">' + esc(pondNameById(item.pondId)) + '</span></td>' +
          '<td><span class="badge ' + tone + '">' + esc(item.levelDesc || item.level) + '</span></td>' +
          '<td class="cell-wrap"><span class="cell-strong">' + esc(item.alertTypeDesc || item.alertType) + '</span>' +
            '<span class="cell-sub">' + esc(item.message || '') + '</span></td>' +
          '<td class="num">' + fmt.num(item.doValue) + '<span class="cell-sub">mg/L</span></td>' +
          '<td class="nowrap">' + fmt.dateTimeShort(item.pondTime) + '</td>' +
          '<td><span class="badge ' + (item.status === 'pending' ? 'warn' : item.status === 'acknowledged' ? 'good' : 'info') + '">' +
            esc(item.statusDesc || item.status) + '</span>' + ackInfo + '</td>' +
          '<td class="nowrap muted">' + fmt.dateTimeShort(item.createTime) + '</td>' +
          '<td class="num">' +
            (canAck
              ? '<button class="btn soft sm" type="button" data-ack="' + esc(item.id) + '">' + icon('check') + '确认</button>'
              : '<span class="muted tiny">已处理</span>') +
          '</td>' +
        '</tr>';
      }).join('');

      wrap.innerHTML = '<div class="table-wrap"><table>' +
        '<thead><tr>' +
          '<th>鱼塘</th><th>等级</th><th>告警内容</th><th class="num">溶氧</th><th>鱼塘时间</th><th>状态</th><th>记录时间</th><th class="num">操作</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table></div>';

      wrap.querySelectorAll('[data-ack]').forEach(function (button) {
        button.addEventListener('click', function () { ackAlert(button.dataset.ack, button); });
      });

      if (pager) {
        pager.innerHTML = pagerMarkup(page);
        bindPager(pager, function (targetPage) {
          state.alerts.page = targetPage;
          fetchAlerts();
        });
      }
    } catch (error) {
      wrap.innerHTML = ui.errorCard(errorMessage(error));
    }
  }

  async function ackAlert(id, button) {
    const confirmed = await ui.confirm({
      title: '确认这条告警？',
      message: '确认后状态会变为“已确认”，操作人 ' + state.operator + ' 会写入系统日志。',
      confirmText: '确认告警'
    });
    if (!confirmed) return;

    busy(button, async function () {
      try {
        await api.alerts.ack(id);
        ui.toast('告警 #' + id + ' 已确认', 'success');
        state.alerts.page = 1;
        loadBadge();
        loadRecentAlerts();
        await fetchAlerts();
      } catch (error) {
        ui.toast(errorMessage(error), 'error', '确认失败');
      }
    });
  }

  /* ------------------------------- 日志页 ------------------------------- */

  async function loadLogsView() {
    const view = el('view-logs');
    if (!view) return;

    view.innerHTML = '<div class="page-head">' +
        '<div>' +
          '<p class="eyebrow">系统日志 · AUDIT TRAIL</p>' +
          '<h1>操作与增氧记录</h1>' +
          '<p class="page-sub">操作日志记录“人对系统做了什么”，增氧日志记录“机器自己做了什么”。</p>' +
        '</div>' +
        '<div class="page-actions">' +
          '<button class="btn ghost" id="logRefresh" type="button">' + icon('refresh') + '刷新</button>' +
        '</div>' +
      '</div>' +
      '<article class="card">' +
        '<div class="tabs">' +
          '<button class="tab' + (state.logs.tab === 'operation' ? ' active' : '') + '" type="button" data-log-tab="operation">操作日志</button>' +
          '<button class="tab' + (state.logs.tab === 'aerator' ? ' active' : '') + '" type="button" data-log-tab="aerator">增氧机日志</button>' +
        '</div>' +
        '<div id="logFilters"></div>' +
        '<div id="logTableWrap">' + ui.loadingCard('正在加载日志…') + '</div>' +
        '<div class="pager" id="logPager"></div>' +
      '</article>';

    const refreshBtn = el('logRefresh');
    if (refreshBtn) refreshBtn.addEventListener('click', function () {
      busy(refreshBtn, fetchLogs);
    });

    view.querySelectorAll('[data-log-tab]').forEach(function (tab) {
      tab.addEventListener('click', function () {
        state.logs.tab = tab.dataset.logTab;
        view.querySelectorAll('[data-log-tab]').forEach(function (node) {
          node.classList.toggle('active', node === tab);
        });
        renderLogFilters();
        fetchLogs();
      });
    });

    renderLogFilters();
    fetchLogs();
  }

  function renderLogFilters() {
    const container = el('logFilters');
    if (!container) return;

    if (state.logs.tab === 'operation') {
      const filter = state.logs.operation;
      container.innerHTML = '<div class="filters">' +
        '<label class="field"><span>模块</span><select class="input" id="logModule">' +
          option('', '全部模块', filter.module === '') +
          option('pond_config', '水体参数', filter.module === 'pond_config') +
          option('aerator', '增氧控制', filter.module === 'aerator') +
          option('setting', '系统设置', filter.module === 'setting') +
          option('clock', '鱼塘时钟', filter.module === 'clock') +
          option('alert', '告警中心', filter.module === 'alert') +
          option('price', '电价管理', filter.module === 'price') +
        '</select></label>' +
        '<label class="field grow"><span>操作人</span>' +
          '<input class="input" id="logOperator" placeholder="精确匹配，如 admin" value="' + esc(filter.operator) + '">' +
        '</label>' +
        '<label class="field"><span>每页</span><select class="input" id="logSize">' +
          option('10', '10 条', filter.size === 10) +
          option('20', '20 条', filter.size === 20) +
          option('50', '50 条', filter.size === 50) +
        '</select></label>' +
        '<button class="btn ghost" id="logSearch" type="button">查询</button>' +
      '</div>';

      const search = function () {
        filter.module = el('logModule') ? el('logModule').value : '';
        filter.operator = el('logOperator') ? el('logOperator').value.trim() : '';
        filter.size = el('logSize') ? Number(el('logSize').value) : 10;
        filter.page = 1;
        fetchLogs();
      };
      if (el('logModule')) el('logModule').addEventListener('change', search);
      if (el('logSize')) el('logSize').addEventListener('change', search);
      if (el('logSearch')) el('logSearch').addEventListener('click', search);
      if (el('logOperator')) el('logOperator').addEventListener('keydown', function (event) {
        if (event.key === 'Enter') search();
      });
    } else {
      const filter = state.logs.aerator;
      container.innerHTML = '<div class="filters">' +
        '<label class="field"><span>鱼塘</span><select class="input" id="logPond">' +
          pondFilterOptions(filter.pondId) +
        '</select></label>' +
        '<label class="field"><span>动作</span><select class="input" id="logAction">' +
          option('', '全部动作', filter.action === '') +
          option('on', '开机', filter.action === 'on') +
          option('off', '关机', filter.action === 'off') +
        '</select></label>' +
        '<label class="field"><span>开始时间</span>' +
          '<input class="input" id="logStart" type="datetime-local" value="' + esc(filter.startTime) + '">' +
        '</label>' +
        '<label class="field"><span>结束时间</span>' +
          '<input class="input" id="logEnd" type="datetime-local" value="' + esc(filter.endTime) + '">' +
        '</label>' +
        '<label class="field"><span>每页</span><select class="input" id="logSize">' +
          option('10', '10 条', filter.size === 10) +
          option('20', '20 条', filter.size === 20) +
          option('50', '50 条', filter.size === 50) +
        '</select></label>' +
        '<button class="btn ghost" id="logSearch" type="button">查询</button>' +
      '</div>';

      const search = function () {
        filter.pondId = el('logPond') ? el('logPond').value : '';
        filter.action = el('logAction') ? el('logAction').value : '';
        filter.startTime = el('logStart') ? el('logStart').value : '';
        filter.endTime = el('logEnd') ? el('logEnd').value : '';
        filter.size = el('logSize') ? Number(el('logSize').value) : 10;
        filter.page = 1;
        fetchLogs();
      };
      ['logPond', 'logAction', 'logSize'].forEach(function (id) {
        if (el(id)) el(id).addEventListener('change', search);
      });
      if (el('logSearch')) el('logSearch').addEventListener('click', search);
    }
  }

  async function fetchLogs() {
    const wrap = el('logTableWrap');
    const pager = el('logPager');
    if (!wrap) return;
    wrap.innerHTML = ui.loadingCard('正在加载日志…');
    if (pager) pager.innerHTML = '';

    try {
      let page;
      if (state.logs.tab === 'operation') {
        const filter = state.logs.operation;
        page = await api.logs.operation({
          page: filter.page,
          size: filter.size,
          module: filter.module,
          operator: filter.operator
        });
      } else {
        const filter = state.logs.aerator;
        page = await api.logs.aerator({
          page: filter.page,
          size: filter.size,
          action: filter.action,
          startTime: withSeconds(filter.startTime),
          endTime: withSeconds(filter.endTime),
          pondId: filter.pondId
        });
      }

      if (!page.records || !page.records.length) {
        wrap.innerHTML = ui.emptyState('没有符合条件的日志', 'list');
        if (pager) pager.innerHTML = '<span>共 0 条</span>';
        return;
      }

      if (state.logs.tab === 'operation') {
        wrap.innerHTML = '<div class="table-wrap"><table>' +
          '<thead><tr><th>鱼塘时间</th><th>模块</th><th>动作</th><th>详情</th><th>操作人</th><th>记录时间</th></tr></thead>' +
          '<tbody>' + page.records.map(function (item) {
            return '<tr>' +
              '<td class="nowrap">' + fmt.dateTimeShort(item.pondTime) + '</td>' +
              '<td><span class="badge info">' + esc(item.moduleDesc || item.module) + '</span></td>' +
              '<td class="nowrap">' + esc(item.actionDesc || item.action) + '</td>' +
              '<td class="cell-wrap">' + esc(item.detail || '--') + '</td>' +
              '<td class="nowrap">' + esc(item.operator || '--') + '</td>' +
              '<td class="nowrap muted">' + fmt.dateTimeShort(item.createTime) + '</td>' +
            '</tr>';
          }).join('') + '</tbody></table></div>';
      } else {
        wrap.innerHTML = '<div class="table-wrap"><table>' +
          '<thead><tr><th>鱼塘</th><th>鱼塘时间</th><th>动作</th><th>触发原因</th><th class="num">溶氧</th><th>操作人</th><th>记录时间</th></tr></thead>' +
          '<tbody>' + page.records.map(function (item) {
            const on = item.action === 'on';
            return '<tr>' +
              '<td class="nowrap"><span class="badge neutral">' + esc(pondNameById(item.pondId)) + '</span></td>' +
              '<td class="nowrap">' + fmt.dateTimeShort(item.pondTime) + '</td>' +
              '<td><span class="badge ' + (on ? 'good' : 'neutral') + '">' + esc(item.actionDesc || item.action) + '</span></td>' +
              '<td class="cell-wrap">' + esc(item.triggerReason || '--') + '</td>' +
              '<td class="num">' + fmt.num(item.doValue) + '<span class="cell-sub">mg/L</span></td>' +
              '<td class="nowrap">' + esc(item.operator || '系统') + '</td>' +
              '<td class="nowrap muted">' + fmt.dateTimeShort(item.createTime) + '</td>' +
            '</tr>';
          }).join('') + '</tbody></table></div>';
      }

      if (pager) {
        pager.innerHTML = pagerMarkup(page);
        bindPager(pager, function (targetPage) {
          if (state.logs.tab === 'operation') state.logs.operation.page = targetPage;
          else state.logs.aerator.page = targetPage;
          fetchLogs();
        });
      }
    } catch (error) {
      wrap.innerHTML = ui.errorCard(errorMessage(error));
    }
  }

  function withSeconds(value) {
    if (!value) return '';
    return value.length === 16 ? value + ':00' : value;
  }

  function pagerMarkup(page) {
    const current = Number(page.current) || 1;
    const pages = Math.max(1, Number(page.pages) || 1);
    const total = Number(page.total) || 0;
    return '<span>共 <strong>' + total + '</strong> 条 · 第 ' + current + ' / ' + pages + ' 页</span>' +
      '<div class="pager-btns">' +
        '<button class="btn ghost sm" type="button" data-page="' + (current - 1) + '"' + (current <= 1 ? ' disabled' : '') + '>' +
          icon('chevron-left') + '</button>' +
        '<span class="pager-current">' + current + ' / ' + pages + '</span>' +
        '<button class="btn ghost sm" type="button" data-page="' + (current + 1) + '"' + (current >= pages ? ' disabled' : '') + '>' +
          icon('chevron-right') + '</button>' +
      '</div>';
  }

  function bindPager(container, onPage) {
    container.querySelectorAll('[data-page]').forEach(function (button) {
      button.addEventListener('click', function () {
        const target = Number(button.dataset.page);
        if (target >= 1) onPage(target);
      });
    });
  }

  /* ------------------------------ 鱼塘管理页 ------------------------------ */

  async function loadPondsView() {
    const view = el('view-ponds');
    if (!view) return;
    view.innerHTML = ui.loadingCard('正在读取鱼塘列表…');

    try {
      const list = await api.ponds.list();
      state.ponds = Array.isArray(list) ? list : [];
      ensureActivePond();
      renderPondControls();
      view.innerHTML = pondsTemplate();
      bindPondsView();
    } catch (error) {
      view.innerHTML = ui.errorCard(errorMessage(error));
    }
  }

  function pondsTemplate() {
    return '<div class="page-head">' +
        '<div>' +
          '<p class="eyebrow">鱼塘管理 · PONDS</p>' +
          '<h1>多鱼塘与双塘对比</h1>' +
          '<p class="page-sub">新增鱼塘后，每个鱼塘拥有独立的仿真引擎、策略、时钟与告警；在总览可并排观察两塘变化。</p>' +
        '</div>' +
        '<div class="page-actions">' +
          '<span class="badge info">共 ' + state.ponds.length + ' 个鱼塘</span>' +
          '<button class="btn ghost" id="pondsRefresh" type="button">' + icon('refresh') + '刷新状态</button>' +
        '</div>' +
      '</div>' +
      '<div class="ponds-layout">' +
        '<article class="card">' +
          '<div class="card-head">' +
            '<div><h3>鱼塘列表</h3><p>点击“切换”把某个鱼塘设为当前操作对象</p></div>' +
            '<a class="text-link" href="#/overview">去总览对比' + icon('chevron-right') + '</a>' +
          '</div>' +
          '<div class="pond-cards" id="pondCards">' + pondCardsMarkup() + '</div>' +
        '</article>' +
        '<aside class="side-col">' +
          '<article class="card create-pond-card">' +
            '<h3>新增鱼塘</h3>' +
            '<p class="muted small">水体参数会复制当前第一个鱼塘，并自动创建默认增氧策略、系统设置和独立仿真引擎。</p>' +
            '<form id="pondCreateForm" novalidate>' +
              '<div class="field">' +
                '<label for="newPondName">鱼塘名称</label>' +
                '<input class="input" id="newPondName" name="pondName" maxlength="64" placeholder="例如：二号塘" autocomplete="off">' +
                '<span class="field-hint">不能为空，最多 64 个字符，名称不可重复。</span>' +
              '</div>' +
              '<div class="form-actions">' +
                '<button class="btn primary full" type="submit">' + icon('plus') + '新增鱼塘</button>' +
              '</div>' +
            '</form>' +
          '</article>' +
          '<article class="card info-card">' +
            '<h3>双塘检测说明</h3>' +
            '<ul class="info-list">' +
              '<li><span>当前鱼塘</span><strong>顶部选择器</strong></li>' +
              '<li><span>对比鱼塘</span><strong>总览中的卡片</strong></li>' +
              '<li><span>对比内容</span><strong>溶氧 / 水温 / 增氧机</strong></li>' +
              '<li><span>刷新频率</span><strong>跟随实时轮询</strong></li>' +
            '</ul>' +
            '<p class="tiny muted" style="margin-top:10px">两塘时间与状态相互独立；切换鱼塘后，参数页、策略页、时钟页都会操作当前选中的鱼塘。</p>' +
          '</article>' +
        '</aside>' +
      '</div>';
  }

  function pondCardsMarkup() {
    if (!state.ponds.length) {
      return ui.emptyState('还没有鱼塘，请在右侧新增', 'waves');
    }
    return state.ponds.map(function (pond) {
      const active = Number(pond.id) === Number(state.activePondId);
      const comparing = Number(pond.id) === Number(state.comparePondId);
      return '<div class="pond-card' + (active ? ' active' : '') + '" data-pond-id="' + esc(pond.id) + '">' +
          '<div class="pond-card-head">' +
            '<div class="pond-title">' +
              '<span class="pond-index">POND ' + fmt.pad(Number(pond.id)) + '</span>' +
              '<strong>' + esc(pond.pondName || ('鱼塘#' + pond.id)) + '</strong>' +
            '</div>' +
            '<div class="pond-tags">' +
              (active ? '<span class="badge good">当前鱼塘</span>' : '') +
              (comparing ? '<span class="badge info">对比中</span>' : '') +
              '<span class="badge ' + (pond.paused ? 'warn' : 'neutral') + '">' + (pond.paused ? '已暂停' : '运行中') + '</span>' +
            '</div>' +
          '</div>' +
          '<div class="pond-card-stats">' +
            '<div><span>溶氧</span><strong>' + fmt.num(pond.currentDo) + '</strong><small>mg/L</small></div>' +
            '<div><span>水温</span><strong>' + fmt.num(pond.temperature, 1) + '</strong><small>℃</small></div>' +
            '<div><span>增氧机</span><strong>' + (pond.aeratorOn ? '增氧中' : '已停止') + '</strong></div>' +
            '<div><span>鱼塘时间</span><strong class="nowrap">' + fmt.dateTimeShort(pond.pondTime) + '</strong><small>×' + (pond.speed || 1) + '</small></div>' +
          '</div>' +
          '<div class="pond-card-actions">' +
            (active
              ? '<span class="muted tiny">正在操作此鱼塘</span>'
              : '<button class="btn soft sm" type="button" data-pond-switch="' + esc(pond.id) + '">切换为当前</button>') +
            (active
              ? ''
              : '<button class="btn ghost sm" type="button" data-pond-compare="' + esc(pond.id) + '">与当前对比</button>') +
          '</div>' +
        '</div>';
    }).join('');
  }

  function bindPondsView() {
    const refreshBtn = el('pondsRefresh');
    if (refreshBtn) {
      refreshBtn.addEventListener('click', function () {
        busy(refreshBtn, loadPondsView);
      });
    }

    const cards = el('pondCards');
    if (cards) {
      cards.addEventListener('click', function (event) {
        const switchBtn = event.target.closest('[data-pond-switch]');
        if (switchBtn) {
          selectPond(Number(switchBtn.dataset.pondSwitch));
          return;
        }
        const compareBtn = event.target.closest('[data-pond-compare]');
        if (compareBtn) {
          const id = Number(compareBtn.dataset.pondCompare);
          state.comparePondId = id;
          persistPondId(COMPARE_KEY, id);
          state.compareData = [];
          if (window.location.hash === '#/overview') handleRoute();
          else window.location.hash = '#/overview';
        }
      });
    }

    const form = el('pondCreateForm');
    if (form) {
      form.addEventListener('submit', function (event) {
        event.preventDefault();
        const input = el('newPondName');
        const name = String(input ? input.value : '').trim();
        if (!name) {
          ui.toast('请先填写鱼塘名称', 'warn');
          return;
        }
        if (name.length > 64) {
          ui.toast('鱼塘名称最多 64 个字符', 'warn');
          return;
        }
        const submitBtn = form.querySelector('button[type="submit"]');
        busy(submitBtn, async function () {
          const previous = state.activePondId;
          try {
            const created = await api.ponds.create(name);
            await loadPonds();
            state.activePondId = Number(created.id);
            persistPondId(POND_KEY, state.activePondId);
            if (previous && Number(previous) !== Number(created.id) && pondById(previous)) {
              state.comparePondId = Number(previous);
            } else {
              ensureComparePond();
            }
            persistPondId(COMPARE_KEY, state.comparePondId);
            resetPondCaches();
            renderPondControls();
            ui.toast('已新增「' + created.pondName + '」，正在进入双塘对比', 'success');
            if (window.location.hash === '#/overview') handleRoute();
            else window.location.hash = '#/overview';
          } catch (error) {
            ui.toast(errorMessage(error), 'error', '新增失败');
          }
        });
      });
    }
  }

  /* ------------------------------ 系统设置页 ------------------------------ */

  async function loadSettingsView() {
    const view = el('view-settings');
    if (!view) return;
    view.innerHTML = ui.loadingCard('正在读取系统设置…');

    try {
      const settings = await api.settings.get(state.activePondId);
      state.settings = settings;
      view.innerHTML = settingsTemplate(settings);
      bindSettingsView(settings);
      checkHealth();
    } catch (error) {
      view.innerHTML = ui.errorCard(errorMessage(error));
    }
  }

  function accountCardMarkup() {
    const user = state.user || {};
    const username = user.username || '';
    const role = user.role === 'admin' ? '管理员' : user.role === 'operator' ? '操作员' : (user.role || '');
    return '<article class="card">' +
        '<div class="card-head">' +
          '<div><h3>账号与安全</h3><p>当前登录：<strong>' + esc(username) + '</strong>' +
            (role ? ' · ' + esc(role) : '') + '</p></div>' +
          '<span class="badge neutral">' + icon('shield') + '会话保护</span>' +
        '</div>' +
        '<form id="accountForm" novalidate autocomplete="off">' +
          '<div class="form-grid">' +
            '<div class="field">' +
              '<label for="accountUsername">登录账号</label>' +
              '<input class="input" id="accountUsername" name="username" type="text" maxlength="32" autocomplete="username" value="' + esc(username) + '">' +
              '<span class="field-hint">3-32 位，以字母或数字开头，可包含 _ . -；修改后需用新账号名登录。</span>' +
            '</div>' +
            '<div class="field">' +
              '<label for="accountCurrentPassword">当前密码</label>' +
              '<input class="input" id="accountCurrentPassword" name="currentPassword" type="password" maxlength="64" autocomplete="current-password" placeholder="修改前必须验证">' +
              '<span class="field-hint">修改账号名或密码前需要验证当前密码。</span>' +
            '</div>' +
            '<div class="field">' +
              '<label for="accountNewPassword">新密码</label>' +
              '<input class="input" id="accountNewPassword" name="newPassword" type="password" maxlength="64" autocomplete="new-password" placeholder="不修改密码请留空">' +
              '<span class="field-hint">6-64 位；留空表示只修改账号名。</span>' +
            '</div>' +
            '<div class="field">' +
              '<label for="accountConfirmPassword">确认新密码</label>' +
              '<input class="input" id="accountConfirmPassword" name="confirmPassword" type="password" maxlength="64" autocomplete="new-password" placeholder="再次输入新密码">' +
              '<span class="field-hint">两次输入需保持一致。</span>' +
            '</div>' +
          '</div>' +
          '<div class="form-actions">' +
            '<button class="btn primary" type="submit">保存账号设置</button>' +
          '</div>' +
        '</form>' +
      '</article>';
  }

  function settingsTemplate(settings) {
    return '<div class="page-head">' +
        '<div>' +
          '<p class="eyebrow">系统设置 · SYSTEM</p>' +
          '<h1>账号安全与仿真引擎</h1>' +
          '<p class="page-sub">可修改登录账号和密码；仿真步长与采样间隔必须是 60 秒的整数倍，修改会热更新到运行中的引擎。</p>' +
        '</div>' +
        '<div class="page-actions">' +
          '<span class="badge neutral">步长 ' + esc(settings.stepSeconds) + ' 秒 · 采样 ' + esc(settings.sampleIntervalSec) + ' 秒</span>' +
        '</div>' +
      '</div>' +
      '<div class="two-col">' +
        '<div class="side-col">' +
          accountCardMarkup() +
          '<article class="card">' +
            '<form id="settingForm" novalidate>' +
            '<div class="switch-row">' +
              '<div><strong>启用安全否决</strong><p>溶氧低于安全下限时，无视自动 / 手动模式强制开机</p></div>' +
              '<button class="switch" id="safetyVetoSwitch" type="button" role="switch" aria-checked="' +
                (settings.safetyVetoEnabled ? 'true' : 'false') + '"></button>' +
            '</div>' +
            '<div class="form-grid" style="margin-top:16px">' +
              '<div class="field">' +
                '<label for="stepSeconds">模拟步长（秒）</label>' +
                '<input class="input" id="stepSeconds" name="stepSeconds" type="number" min="60" max="3600" step="60" value="' + esc(settings.stepSeconds) + '">' +
                '<span class="field-hint">每次定时器 tick 推进的模拟秒数，60 - 3600 且为 60 的倍数。</span>' +
              '</div>' +
              '<div class="field">' +
                '<label for="sampleIntervalSec">采样间隔（秒）</label>' +
                '<input class="input" id="sampleIntervalSec" name="sampleIntervalSec" type="number" min="60" max="86400" step="60" value="' + esc(settings.sampleIntervalSec) + '">' +
                '<span class="field-hint">溶氧历史点的采样间隔，60 - 86400 且为 60 的倍数。</span>' +
              '</div>' +
            '</div>' +
            '<div class="form-actions">' +
              '<button class="btn ghost" type="button" id="settingResetForm">还原</button>' +
              '<button class="btn primary" type="submit">保存设置</button>' +
            '</div>' +
          '</form>' +
          '</article>' +
        '</div>' +
        '<aside class="side-col">' +
          '<article class="card">' +
            '<div class="card-head"><div><h3>系统健康</h3><p>Web、数据库与历史缓冲</p></div></div>' +
            '<div class="health-list" id="healthList"><div class="empty">检测中…</div></div>' +
            '<button class="btn ghost full" id="healthBtn" type="button">' + icon('activity') + '重新检测</button>' +
          '</article>' +
          '<article class="card danger-card">' +
            '<h3>危险操作</h3>' +
            '<p>重置会把鱼塘时间恢复到今天 00:00，溶氧回到 6.00 mg/L，倍速回到 1，并清空内存中的历史曲线。数据库里的配置与日志不会删除。</p>' +
            '<button class="btn danger full" id="resetSimBtn" type="button">' + icon('alert-triangle') + '重置仿真系统</button>' +
          '</article>' +
        '</aside>' +
      '</div>';
  }

  function bindSettingsView(settings) {
    bindAccountForm();

    const form = el('settingForm');
    if (!form) return;

    const vetoSwitch = el('safetyVetoSwitch');
    if (vetoSwitch) {
      vetoSwitch.addEventListener('click', function () {
        const current = vetoSwitch.getAttribute('aria-checked') === 'true';
        vetoSwitch.setAttribute('aria-checked', String(!current));
      });
    }

    const resetFormBtn = el('settingResetForm');
    if (resetFormBtn) resetFormBtn.addEventListener('click', function () { loadSettingsView(); });

    const healthBtn = el('healthBtn');
    if (healthBtn) healthBtn.addEventListener('click', function () { busy(healthBtn, checkHealth); });

    form.addEventListener('submit', function (event) {
      event.preventDefault();
      const submitBtn = form.querySelector('button[type="submit"]');
      busy(submitBtn, async function () {
        const payload = {
          safetyVetoEnabled: vetoSwitch ? vetoSwitch.getAttribute('aria-checked') === 'true' : false,
          stepSeconds: Number(form.elements.namedItem('stepSeconds').value),
          sampleIntervalSec: Number(form.elements.namedItem('sampleIntervalSec').value)
        };
        const validation = validateSettings(payload);
        if (validation) {
          ui.toast(validation, 'warn', '请检查设置');
          return;
        }
        try {
          const updated = await api.settings.update(payload, state.activePondId);
          state.settings = updated;
          updateVetoState();
          ui.toast('系统设置已保存并热更新', 'success');
          loadSettingsView();
        } catch (error) {
          ui.toast(errorMessage(error), 'error', '保存失败');
        }
      });
    });

    const resetBtn = el('resetSimBtn');
    if (resetBtn) {
      resetBtn.addEventListener('click', function () {
        ui.confirm({
          title: '重置仿真系统？',
          message: '鱼塘时间会回到今天 00:00，溶氧回到 6.00 mg/L，倍速回到 1，并清空内存中的历史曲线。\n此操作会写入系统日志。',
          confirmText: '确认重置',
          danger: true
        }).then(function (confirmed) {
          if (!confirmed) return;
          busy(resetBtn, async function () {
            try {
              const snapshot = await api.settings.reset(state.activePondId);
              ui.toast('仿真已重置到 ' + fmt.dateTime(snapshot.pondTime), 'success');
              mergeSnapshot(snapshot);
              if (doChart) doChart.setData(null);
              pollRealtime();
              loadRecentAerator();
              loadRecentAlerts();
              loadBadge();
            } catch (error) {
              ui.toast(errorMessage(error), 'error', '重置失败');
            }
          });
        });
      });
    }
  }

  function bindAccountForm() {
    const form = el('accountForm');
    if (!form) return;

    form.addEventListener('submit', function (event) {
      event.preventDefault();
      const submitBtn = form.querySelector('button[type="submit"]');
      const username = String(form.elements.namedItem('username').value || '').trim();
      const currentPassword = form.elements.namedItem('currentPassword').value;
      const newPassword = form.elements.namedItem('newPassword').value;
      const confirmPassword = form.elements.namedItem('confirmPassword').value;

      const validation = validateAccountForm(username, currentPassword, newPassword, confirmPassword);
      if (validation) {
        ui.toast(validation, 'warn', '请检查账号设置');
        return;
      }

      busy(submitBtn, async function () {
        try {
          const previousUsername = state.user ? state.user.username : '';
          const updated = await api.auth.updateAccount({
            currentPassword: currentPassword,
            newUsername: username,
            newPassword: newPassword || null
          });
          const usernameChanged = updated.username !== previousUsername;

          state.user = updated;
          state.operator = window.Aqua.setOperator(updated.username);
          applySignedInUser(updated);

          try {
            if (window.localStorage.getItem('aquaguard.rememberedUser')) {
              window.localStorage.setItem('aquaguard.rememberedUser', updated.username);
            }
            if (updated.displayName) {
              window.localStorage.setItem('aquaguard.displayName', updated.displayName);
            }
          } catch (e) { /* 隐私模式下忽略 */ }

          let message = '账号信息已保存';
          if (usernameChanged) message += '，下次登录请使用新账号名';
          if (newPassword) message += '，密码已更新';
          ui.toast(message, 'success', '保存成功');
          loadSettingsView();
        } catch (error) {
          ui.toast(errorMessage(error), 'error', '保存失败');
        }
      });
    });
  }

  function validateAccountForm(username, currentPassword, newPassword, confirmPassword) {
    if (!/^[A-Za-z0-9][A-Za-z0-9_.-]{2,31}$/.test(username)) {
      return '账号名需为 3-32 位，以字母或数字开头，可包含 _ . -';
    }
    if (!currentPassword) return '请输入当前密码';
    if (newPassword) {
      if (newPassword.length < 6 || newPassword.length > 64) return '新密码长度需为 6-64 位';
      if (newPassword !== confirmPassword) return '两次输入的新密码不一致';
    } else if (confirmPassword) {
      return '请输入新密码，或清空确认新密码';
    }
    if (state.user && username === state.user.username && !newPassword) {
      return '账号名和新密码都没有变化';
    }
    return null;
  }

  function validateSettings(payload) {
    if (!Number.isInteger(payload.stepSeconds) || payload.stepSeconds < 60 || payload.stepSeconds > 3600) {
      return '模拟步长需在 60 - 3600 秒之间';
    }
    if (payload.stepSeconds % 60 !== 0) return '模拟步长必须是 60 的整数倍';
    if (!Number.isInteger(payload.sampleIntervalSec) || payload.sampleIntervalSec < 60 || payload.sampleIntervalSec > 86400) {
      return '采样间隔需在 60 - 86400 秒之间';
    }
    if (payload.sampleIntervalSec % 60 !== 0) return '采样间隔必须是 60 的整数倍';
    return null;
  }

  async function checkHealth() {
    const container = el('healthList');
    if (!container) return;
    container.innerHTML = ui.loadingCard('检测中…');

    const results = await Promise.allSettled([
      api.ping(),
      api.dbCheck(),
      api.buffer(state.activePondId)
    ]);

    const row = function (iconName, name, badgeTone, badgeText, detail) {
      return '<div class="health-row">' + icon(iconName) +
        '<div class="health-main"><strong>' + esc(name) + '</strong><span title="' + esc(detail) + '">' + esc(detail) + '</span></div>' +
        '<span class="badge ' + badgeTone + '">' + esc(badgeText) + '</span>' +
      '</div>';
    };

    const web = results[0];
    const db = results[1];
    const buffer = results[2];

    let html = '';
    html += row('activity', 'Web 服务', web.status === 'fulfilled' ? 'good' : 'danger',
      web.status === 'fulfilled' ? '正常' : '失败',
      web.status === 'fulfilled' ? ('/api/ping → ' + web.value) : errorMessage(web.reason));
    html += row('database', '数据库', db.status === 'fulfilled' ? 'good' : 'danger',
      db.status === 'fulfilled' ? '连通' : '失败',
      db.status === 'fulfilled' ? db.value : errorMessage(db.reason));
    html += row('list', '历史缓冲', buffer.status === 'fulfilled' ? 'good' : 'warn',
      buffer.status === 'fulfilled' ? (buffer.value + ' 点') : '未知',
      buffer.status === 'fulfilled' ? '仿真引擎内存中缓存的溶氧采样数' : errorMessage(buffer.reason));
    html += row('bolt', '后端地址', 'info', '同源', api.baseUrl);

    container.innerHTML = html;
  }

  /* ------------------------------ AI 助手 ------------------------------ */

  const WRITE_TOOLS = {
    set_manual_aerator: true,
    update_aerator_strategy: true,
    pause_pond: true,
    resume_pond: true
  };

  const assistant = {
    open: false,
    configured: null,
    statusLoaded: false,
    historyLoaded: false,
    busy: false,
    pending: null,
    controller: null,
    writeSucceeded: false
  };

  function initAssistant() {
    const root = el('assistantRoot');
    if (!root) return;
    root.innerHTML =
      '<button class="assistant-fab" id="assistantFab" type="button" aria-label="打开 AI 助手" title="AI 助手">' +
        icon('spark') +
      '</button>' +
      '<section class="assistant-panel" id="assistantPanel" hidden aria-label="AI 助手">' +
        '<header class="assistant-head">' +
          '<div class="assistant-title">' +
            '<span class="assistant-avatar">' + icon('spark') + '</span>' +
            '<div><strong>AquaGuard 助手</strong><span id="assistantStatus">正在连接…</span></div>' +
          '</div>' +
          '<div class="assistant-head-actions">' +
            '<button class="icon-btn" id="assistantReset" type="button" title="清空对话" aria-label="清空对话">' +
              icon('refresh') +
            '</button>' +
            '<button class="icon-btn" id="assistantClose" type="button" title="收起" aria-label="收起">' +
              icon('x') +
            '</button>' +
          '</div>' +
        '</header>' +
        '<div class="assistant-notice" id="assistantNotice" hidden></div>' +
        '<div class="assistant-body" id="assistantBody">' + assistantWelcomeMarkup() + '</div>' +
        '<div class="assistant-pending" id="assistantPending" hidden></div>' +
        '<form class="assistant-form" id="assistantForm">' +
          '<textarea id="assistantInput" rows="1" maxlength="2000" placeholder="问问鱼塘的情况…"></textarea>' +
          '<div class="assistant-form-foot">' +
            '<span class="assistant-hint" id="assistantHint">写操作需要你确认后才会执行</span>' +
            '<div class="assistant-send">' +
              '<button class="btn ghost sm" id="assistantStop" type="button" hidden>停止</button>' +
              '<button class="btn primary sm" id="assistantSend" type="submit">发送</button>' +
            '</div>' +
          '</div>' +
        '</form>' +
      '</section>';

    el('assistantFab').addEventListener('click', openAssistant);
    el('assistantClose').addEventListener('click', closeAssistant);
    el('assistantReset').addEventListener('click', resetAssistant);
    el('assistantStop').addEventListener('click', stopAssistant);
    el('assistantForm').addEventListener('submit', function (event) {
      event.preventDefault();
      sendAssistantMessage(el('assistantInput').value);
    });
    el('assistantInput').addEventListener('keydown', function (event) {
      if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
        event.preventDefault();
        sendAssistantMessage(el('assistantInput').value);
      }
    });
    el('assistantInput').addEventListener('input', autosizeAssistantInput);
    el('assistantBody').addEventListener('click', function (event) {
      const quick = event.target.closest('[data-q]');
      if (!quick) return;
      sendAssistantMessage(quick.getAttribute('data-q'));
    });

    loadAssistantStatus();
  }

  function assistantWelcomeMarkup() {
    return '<div class="assistant-welcome" id="assistantWelcome">' +
      '<span class="assistant-welcome-mark">' + icon('spark') + '</span>' +
      '<strong>有什么可以帮你？</strong>' +
      '<p>我可以查询鱼塘溶氧、告警与增氧记录，也能在你确认后调整策略。</p>' +
      '<div class="assistant-quick">' +
        '<button type="button" data-q="现在哪口塘最危险？">哪口塘最危险？</button>' +
        '<button type="button" data-q="对比当前鱼塘和其他鱼塘的溶氧">对比各塘溶氧</button>' +
        '<button type="button" data-q="解释最近的告警">解释最近的告警</button>' +
        '<button type="button" data-q="现在现实中几点了？">现在几点？</button>' +
        '<button type="button" data-q="开启当前鱼塘的增氧机">开启增氧机</button>' +
      '</div>' +
    '</div>';
  }

  function openAssistant() {
    const panel = el('assistantPanel');
    if (!panel) return;
    panel.hidden = false;
    el('assistantFab').hidden = true;
    assistant.open = true;
    if (!assistant.statusLoaded) loadAssistantStatus();
    if (!assistant.historyLoaded) {
      assistant.historyLoaded = true;
      loadAssistantHistory();
    }
    updateAssistantComposer();
    setTimeout(function () {
      const input = el('assistantInput');
      if (input) input.focus();
    }, 30);
    scrollAssistant(true);
  }

  function closeAssistant() {
    const panel = el('assistantPanel');
    if (panel) panel.hidden = true;
    const fab = el('assistantFab');
    if (fab) fab.hidden = false;
    assistant.open = false;
  }

  function setAssistantStatus(text, warn) {
    const node = el('assistantStatus');
    if (!node) return;
    node.textContent = text;
    node.classList.toggle('warn', !!warn);
  }

  async function loadAssistantStatus() {
    try {
      const status = await api.agent.status();
      assistant.statusLoaded = true;
      assistant.configured = !!(status && status.enabled && status.configured);

      if (!status || !status.enabled) {
        setAssistantStatus('助手已关闭', true);
      } else if (!status.configured) {
        setAssistantStatus('未配置 API Key', true);
      } else {
        setAssistantStatus((status.provider || 'AI') + ' · ' + (status.model || ''));
      }

      const notice = el('assistantNotice');
      if (notice) {
        if (status && !status.enabled) {
          notice.hidden = false;
          notice.textContent = 'AI 助手已在配置中关闭（app.agent.enabled=false）。';
        } else if (status && !status.configured) {
          notice.hidden = false;
          notice.textContent = '尚未配置 DEEPSEEK_API_KEY。请设置环境变量后重启服务；其他监控功能不受影响。';
        } else {
          notice.hidden = true;
        }
      }
    } catch (error) {
      assistant.statusLoaded = false;
      assistant.configured = null;
      setAssistantStatus('连接失败，稍后重试', true);
    }
    updateAssistantComposer();
  }

  async function loadAssistantHistory() {
    let list;
    try {
      list = await api.agent.history(50);
    } catch (error) {
      return;
    }
    if (!Array.isArray(list) || !list.length) return;
    hideAssistantWelcome();
    list.forEach(function (message) {
      if (!message) return;
      if (message.role === 'user') {
        appendAssistantUserMessage(message.content);
      } else if (message.role === 'assistant' && message.content) {
        const turn = beginAssistantTurn();
        turn.text.textContent = message.content;
        turn.hasContent = true;
      }
    });
    scrollAssistant(true);
  }

  function hideAssistantWelcome() {
    const welcome = el('assistantWelcome');
    if (welcome) welcome.remove();
  }

  function appendAssistantUserMessage(text) {
    hideAssistantWelcome();
    const node = document.createElement('div');
    node.className = 'assistant-msg user';
    const bubble = document.createElement('div');
    bubble.className = 'assistant-bubble';
    bubble.textContent = text;
    node.appendChild(bubble);
    el('assistantBody').appendChild(node);
    scrollAssistant();
  }

  function beginAssistantTurn() {
    hideAssistantWelcome();
    const node = document.createElement('div');
    node.className = 'assistant-msg assistant';
    node.innerHTML = '<span class="assistant-avatar small">' + icon('spark') + '</span>' +
      '<div class="assistant-msg-body">' +
        '<div class="assistant-text"></div>' +
        '<div class="assistant-tools"></div>' +
      '</div>';
    el('assistantBody').appendChild(node);
    scrollAssistant();
    return {
      node: node,
      text: node.querySelector('.assistant-text'),
      tools: node.querySelector('.assistant-tools'),
      hasContent: false
    };
  }

  function appendAssistantNote(text, tone) {
    const node = document.createElement('div');
    node.className = 'assistant-note' + (tone ? ' ' + tone : '');
    node.textContent = text;
    el('assistantBody').appendChild(node);
    scrollAssistant();
  }

  function assistantHandlers(turn, onError) {
    return {
      text: function (payload) {
        if (!payload || typeof payload.text !== 'string' || !payload.text) return;
        turn.hasContent = true;
        turn.text.textContent += payload.text;
        scrollAssistant();
      },
      tool: function (payload) {
        renderAssistantTool(turn, payload);
      },
      pending_action: function (payload) {
        if (payload) showAssistantPending(payload);
      },
      done: function () { /* 由 finally 统一收尾 */ },
      error: function (payload) {
        onError((payload && payload.message) || 'AI 助手暂时不可用，请稍后重试');
      },
      closed: function () {
        onError('连接中断，请重试');
      }
    };
  }

  function renderAssistantTool(turn, payload) {
    if (!payload || !payload.name) return;
    if (payload.status === 'start') {
      const chip = document.createElement('div');
      chip.className = 'assistant-tool running';
      chip.dataset.name = payload.name;

      const dot = document.createElement('span');
      dot.className = 'assistant-tool-dot';
      const label = document.createElement('span');
      label.className = 'assistant-tool-label';
      label.textContent = payload.label || payload.name;
      const summary = document.createElement('span');
      summary.className = 'assistant-tool-summary';
      summary.textContent = '执行中…';

      chip.appendChild(dot);
      chip.appendChild(label);
      chip.appendChild(summary);
      turn.tools.appendChild(chip);
    } else if (payload.status === 'done') {
      const running = turn.tools.querySelectorAll('.assistant-tool.running');
      let chip = null;
      for (let i = 0; i < running.length; i += 1) {
        if (running[i].dataset.name === payload.name) {
          chip = running[i];
          break;
        }
      }
      if (!chip) return;

      chip.classList.remove('running');
      chip.classList.add(payload.ok === false ? 'error' : 'ok');
      const summary = chip.querySelector('.assistant-tool-summary');
      if (summary) {
        summary.textContent = payload.summary ||
          (payload.ok === false ? '执行失败' : '完成');
      }
      if (payload.ok !== false && WRITE_TOOLS[payload.name]) {
        assistant.writeSucceeded = true;
      }
    }
    scrollAssistant();
  }

  async function sendAssistantMessage(raw) {
    const text = String(raw == null ? '' : raw).trim();
    if (!text) return;
    if (assistant.busy) {
      ui.toast('助手正在回复，请稍候', 'info');
      return;
    }
    if (assistant.configured === false) {
      ui.toast('AI 助手尚未配置 API Key', 'warn');
      return;
    }
    if (assistant.pending) {
      ui.toast('请先确认或取消上方待执行操作', 'info');
      return;
    }

    const input = el('assistantInput');
    if (input) input.value = '';
    autosizeAssistantInput();
    appendAssistantUserMessage(text);
    const turn = beginAssistantTurn();
    await runAssistantStream(function (signal, handlers) {
      return api.agent.chat(text, state.activePondId, handlers, signal);
    }, turn);
  }

  async function runAssistantStream(start, turn) {
    assistant.busy = true;
    assistant.controller = new AbortController();
    updateAssistantComposer();
    scrollAssistant();

    let streamError = null;
    let aborted = false;
    const handlers = assistantHandlers(turn, function (message) {
      streamError = message;
    });

    try {
      await start(assistant.controller.signal, handlers);
    } catch (error) {
      if (error && error.name === 'AbortError') {
        aborted = true;
      } else {
        streamError = errorMessage(error);
      }
    } finally {
      assistant.busy = false;
      assistant.controller = null;

      if (aborted) {
        appendAssistantNote('已停止生成。', 'muted');
      } else if (streamError) {
        appendAssistantNote(streamError, 'error');
        ui.toast(streamError, 'error');
        if (/API Key|未配置|关闭/.test(streamError)) {
          assistant.configured = false;
          loadAssistantStatus();
        }
      }

      if (!turn.hasContent && !turn.tools.children.length && !streamError
          && !aborted && !assistant.pending) {
        turn.text.textContent = '（没有收到有效回复，请换个问法再试）';
        turn.text.classList.add('muted');
      }

      const wrote = assistant.writeSucceeded;
      assistant.writeSucceeded = false;
      updateAssistantComposer();
      scrollAssistant();
      if (wrote) refreshAssistantData();
    }
  }

  function showAssistantPending(payload) {
    assistant.pending = payload;
    const box = el('assistantPending');
    if (!box) return;
    box.hidden = false;
    box.classList.remove('busy');
    box.textContent = '';

    const head = document.createElement('div');
    head.className = 'assistant-pending-head';
    const mark = document.createElement('span');
    mark.innerHTML = icon('alert-triangle');
    const title = document.createElement('strong');
    title.textContent = payload.title || '待确认操作';
    head.appendChild(mark);
    head.appendChild(title);

    const description = document.createElement('p');
    description.textContent = payload.description || '该操作需要你确认后才会执行。';

    const meta = document.createElement('span');
    meta.className = 'assistant-pending-meta';
    meta.textContent = '执行后会写入系统操作日志，操作人为当前登录账号。';

    const actions = document.createElement('div');
    actions.className = 'assistant-pending-actions';
    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'btn ghost sm';
    cancelBtn.textContent = '取消';
    const confirmBtn = document.createElement('button');
    confirmBtn.type = 'button';
    confirmBtn.className = 'btn primary sm';
    confirmBtn.textContent = '确认执行';

    confirmBtn.addEventListener('click', function () {
      confirmAssistantAction(payload.id, confirmBtn, cancelBtn);
    });
    cancelBtn.addEventListener('click', function () {
      cancelAssistantAction(payload.id, confirmBtn, cancelBtn);
    });

    actions.appendChild(cancelBtn);
    actions.appendChild(confirmBtn);
    box.appendChild(head);
    box.appendChild(description);
    box.appendChild(meta);
    box.appendChild(actions);

    updateAssistantComposer();
    scrollAssistant();
  }

  async function confirmAssistantAction(actionId, confirmBtn, cancelBtn) {
    if (assistant.busy) return;
    confirmBtn.disabled = true;
    cancelBtn.disabled = true;
    const box = el('assistantPending');
    if (box) box.classList.add('busy');

    const turn = beginAssistantTurn();
    await runAssistantStream(function (signal, handlers) {
      return api.agent.confirm(actionId, handlers, signal);
    }, turn);

    if (assistant.pending && assistant.pending.id === actionId) {
      clearAssistantPending();
    } else {
      updateAssistantComposer();
    }
  }

  async function cancelAssistantAction(actionId, confirmBtn, cancelBtn) {
    confirmBtn.disabled = true;
    cancelBtn.disabled = true;
    try {
      await api.agent.reject(actionId);
      clearAssistantPending();
      appendAssistantNote('已取消该操作，没有做任何修改。', 'muted');
      ui.toast('已取消操作', 'info');
    } catch (error) {
      const message = errorMessage(error);
      ui.toast(message, 'error');
      if (/过期|失效|处理/.test(message)) {
        clearAssistantPending();
      } else {
        confirmBtn.disabled = false;
        cancelBtn.disabled = false;
      }
      updateAssistantComposer();
    }
  }

  function clearAssistantPending() {
    assistant.pending = null;
    const box = el('assistantPending');
    if (box) {
      box.hidden = true;
      box.textContent = '';
      box.classList.remove('busy');
    }
    updateAssistantComposer();
  }

  function stopAssistant() {
    if (assistant.controller) {
      assistant.controller.abort();
      return;
    }
    if (assistant.busy) assistant.busy = false;
    updateAssistantComposer();
  }

  function updateAssistantComposer() {
    const send = el('assistantSend');
    const stop = el('assistantStop');
    const input = el('assistantInput');
    const hint = el('assistantHint');
    const configured = assistant.configured !== false;

    if (send) send.disabled = assistant.busy || !!assistant.pending || !configured;
    if (stop) stop.hidden = !assistant.busy;
    if (input) input.disabled = !configured;
    if (hint) {
      hint.textContent = !configured ? '未配置 API Key'
        : assistant.pending ? '请确认或取消待执行操作'
        : assistant.busy ? '正在生成…'
        : '写操作需要你确认后才会执行';
    }
  }

  function autosizeAssistantInput() {
    const input = el('assistantInput');
    if (!input) return;
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight || 40, 120) + 'px';
  }

  function scrollAssistant(force) {
    const body = el('assistantBody');
    if (!body) return;
    const nearBottom = body.scrollHeight - body.scrollTop - body.clientHeight < 160;
    if (force || nearBottom) body.scrollTop = body.scrollHeight;
  }

  async function resetAssistant() {
    if (assistant.busy) {
      ui.toast('正在生成，请先停止', 'info');
      return;
    }
    const confirmed = await ui.confirm({
      title: '清空对话？',
      message: '将删除当前会话的聊天记录；尚未确认的操作也会一起取消。',
      confirmText: '清空',
      danger: true
    });
    if (!confirmed) return;

    try {
      await api.agent.reset();
      hideAssistantWelcome();
      const body = el('assistantBody');
      if (body) body.innerHTML = assistantWelcomeMarkup();
      clearAssistantPending();
      assistant.historyLoaded = true;
      ui.toast('对话已清空', 'success');
    } catch (error) {
      ui.toast(errorMessage(error), 'error');
    }
  }

  async function refreshAssistantData() {
    try {
      await loadPonds();
      resetPondCaches();
      handleRoute();
      if (typeof loadBadge === 'function') loadBadge();
    } catch (error) {
      /* 刷新失败不影响对话 */
    }
  }

  /* --------------------------- 页面定时器清理 --------------------------- */

  window.addEventListener('beforeunload', function () {
    if (state.pollTimer) clearInterval(state.pollTimer);
    if (badgeTimer) clearInterval(badgeTimer);
    if (extrasTimer) clearInterval(extrasTimer);
  });
})();
