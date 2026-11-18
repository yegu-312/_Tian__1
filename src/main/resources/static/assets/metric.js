/* ==========================================================================
   AquaGuard · 指标分析（多形态轻量图表）
   为「溶氧 / 水温 / pH」单指标页提供 折线 / 柱状 / 散点 / 分布 四种视图，
   纯 SVG 生成、无第三方依赖，配色沿用 app.css 的 --chart-* 变量。
   ========================================================================== */
(function () {
  'use strict';

  /* ------------------------------ 指标定义 ------------------------------ */

  const METRICS = {
    do: {
      key: 'do', label: '溶氧', unit: 'mg/L', decimals: 2,
      icon: 'droplet', tone: 'aqua',
      pick: function (p) { return Number(p.doValue); },
      thresholds: [
        { label: '安全下限', color: 'var(--chart-floor)', from: 'safetyFloor' },
        { label: '开机阈值', color: 'var(--chart-on)', from: 'onThreshold' },
        { label: '关机阈值', color: 'var(--chart-off)', from: 'offThreshold' }
      ]
    },
    temp: {
      key: 'temp', label: '水温', unit: '℃', decimals: 1,
      icon: 'thermometer', tone: 'amber',
      pick: function (p) { return Number(p.temperature); },
      thresholds: [
        { label: '适宜下限', color: 'var(--chart-floor)', value: 18 },
        { label: '适宜上限', color: 'var(--chart-off)', value: 32 }
      ]
    },
    ph: {
      key: 'ph', label: 'pH', unit: '', decimals: 2,
      icon: 'activity', tone: 'green',
      pick: function (p) { return Number(p.ph); },
      thresholds: [
        { label: '偏酸 6.5', color: 'var(--chart-floor)', value: 6.5 },
        { label: '偏碱 8.5', color: 'var(--chart-off)', value: 8.5 }
      ]
    }
  };

  /* ------------------------------ 基础工具 ------------------------------ */

  function pad2(n) { return String(n).padStart(2, '0'); }

  function hhmm(date) {
    return pad2(date.getHours()) + ':' + pad2(date.getMinutes());
  }

  function mmdd(date) {
    return (date.getMonth() + 1) + '-' + pad2(date.getDate());
  }

  function niceStep(raw) {
    if (!isFinite(raw) || raw <= 0) return 1;
    const exp = Math.floor(Math.log10(raw));
    const base = Math.pow(10, exp);
    const n = raw / base;
    const step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
    return step * base;
  }

  function decimalsOf(step) {
    if (step >= 1) return 0;
    return Math.min(3, Math.ceil(-Math.log10(step)));
  }

  /** 解析阈值：支持固定值或从 summary 字段取值 */
  function resolveThresholds(metric, summary) {
    const list = [];
    (metric.thresholds || []).forEach(function (item) {
      const raw = item.from ? (summary || {})[item.from] : item.value;
      const value = Number(raw);
      if (isFinite(value) && value > 0) {
        list.push({ label: item.label, color: item.color, value: value });
      }
    });
    return list;
  }

  /* --------------------------- 折线 / 柱状 / 散点 --------------------------- */

  function trendSvg(points, options) {
    const o = options || {};
    const mode = o.mode === 'bar' || o.mode === 'scatter' ? o.mode : 'line';
    const unit = o.unit || '';
    const decimals = o.decimals == null ? 2 : o.decimals;
    const thresholds = o.thresholds || [];

    const data = (points || []).filter(function (p) {
      return p && isFinite(p.value) && p.time;
    });
    if (!data.length) {
      return '<div class="chart-empty">当前时间范围没有可用采样</div>';
    }

    const W = 860;
    const H = 250;
    const padL = 54;
    const padR = 62;
    const padT = 16;
    const padB = 28;
    const plotW = W - padL - padR;
    const plotH = H - padT - padB;

    const values = data.map(function (p) { return p.value; });
    const thValues = thresholds.map(function (t) { return t.value; });
    let lo = Math.min.apply(null, values.concat(thValues));
    let hi = Math.max.apply(null, values.concat(thValues));
    if (!isFinite(lo) || !isFinite(hi)) { lo = 0; hi = 1; }
    if (hi - lo < 1e-6) { hi = lo + 1; }
    const margin = (hi - lo) * 0.12;
    lo -= margin;
    hi += margin;

    const step = niceStep((hi - lo) / 4);
    lo = Math.floor(lo / step) * step;
    hi = Math.ceil(hi / step) * step;

    const X = function (i) {
      return data.length === 1
        ? padL + plotW / 2
        : padL + (i / (data.length - 1)) * plotW;
    };
    const Y = function (v) { return padT + ((hi - v) / (hi - lo)) * plotH; };

    const parts = [];
    parts.push('<svg class="metric-svg" viewBox="0 0 ' + W + ' ' + H + '" role="img" aria-label="' +
      (o.label || '指标') + '趋势图">');

    /* 网格与 Y 轴刻度 */
    const gridDecimals = decimalsOf(step);
    for (let v = lo; v <= hi + step * 0.001; v += step) {
      const y = Y(v).toFixed(1);
      parts.push('<line x1="' + padL + '" y1="' + y + '" x2="' + (padL + plotW) + '" y2="' + y +
        '" stroke="var(--chart-grid)" stroke-width="1"/>');
      parts.push('<text x="' + (padL - 10) + '" y="' + (Number(y) + 3.5).toFixed(1) +
        '" text-anchor="end" font-size="10" fill="var(--chart-axis)">' +
        v.toFixed(gridDecimals) + '</text>');
    }

    /* X 轴时间刻度 */
    const spanMs = data[data.length - 1].time.getTime() - data[0].time.getTime();
    const useDate = spanMs > 48 * 3600 * 1000;
    const tickCount = Math.min(6, Math.max(2, data.length));
    for (let i = 0; i < tickCount; i++) {
      const index = Math.round((i / (tickCount - 1)) * (data.length - 1));
      const point = data[index];
      const label = useDate ? mmdd(point.time) : hhmm(point.time);
      parts.push('<text x="' + X(index).toFixed(1) + '" y="' + (H - 8) +
        '" text-anchor="middle" font-size="10" fill="var(--chart-axis)">' + label + '</text>');
    }

    /* 阈值线 */
    thresholds.forEach(function (t) {
      const y = Y(t.value).toFixed(1);
      parts.push('<line x1="' + padL + '" y1="' + y + '" x2="' + (padL + plotW) + '" y2="' + y +
        '" stroke="' + t.color + '" stroke-width="1.2" stroke-dasharray="6 5" opacity="0.9"/>');
      parts.push('<text x="' + (padL + plotW + 6) + '" y="' + (Number(y) + 3.5).toFixed(1) +
        '" font-size="9.5" fill="' + t.color + '">' + t.label + '</text>');
    });

    if (mode === 'bar') {
      const slot = plotW / Math.max(1, data.length);
      const barW = Math.max(1.5, slot * 0.62);
      data.forEach(function (p, i) {
        const y = Y(p.value);
        const h = Math.max(1, padT + plotH - y);
        parts.push('<rect x="' + (X(i) - barW / 2).toFixed(1) + '" y="' + y.toFixed(1) +
          '" width="' + barW.toFixed(1) + '" height="' + h.toFixed(1) +
          '" rx="1.5" fill="var(--chart-history)" opacity="0.78"><title>' +
          p.iso + ' · ' + p.value.toFixed(decimals) + ' ' + unit + '</title></rect>');
      });
    } else if (mode === 'scatter') {
      data.forEach(function (p, i) {
        parts.push('<circle cx="' + X(i).toFixed(1) + '" cy="' + Y(p.value).toFixed(1) +
          '" r="2.4" fill="var(--chart-history)" opacity="0.75"><title>' +
          p.iso + ' · ' + p.value.toFixed(decimals) + ' ' + unit + '</title></circle>');
      });
    } else {
      let line = '';
      let area = '';
      data.forEach(function (p, i) {
        const x = X(i).toFixed(1);
        const y = Y(p.value).toFixed(1);
        line += (i === 0 ? 'M' : 'L') + x + ' ' + y + ' ';
        area += (i === 0 ? 'M' : 'L') + x + ' ' + y + ' ';
      });
      const firstX = X(0).toFixed(1);
      const lastX = X(data.length - 1).toFixed(1);
      const bottom = (padT + plotH).toFixed(1);
      area += 'L' + lastX + ' ' + bottom + ' L' + firstX + ' ' + bottom + ' Z';
      parts.push('<path d="' + area + '" fill="var(--chart-history-fill)" opacity="0.55"/>');
      parts.push('<path d="' + line + '" fill="none" stroke="var(--chart-history)" stroke-width="1.8" ' +
        'stroke-linejoin="round" stroke-linecap="round"/>');
      /* 最高 / 最低标记 */
      let maxI = 0;
      let minI = 0;
      data.forEach(function (p, i) {
        if (p.value > data[maxI].value) maxI = i;
        if (p.value < data[minI].value) minI = i;
      });
      [maxI, minI].forEach(function (i) {
        parts.push('<circle cx="' + X(i).toFixed(1) + '" cy="' + Y(data[i].value).toFixed(1) +
          '" r="3" fill="var(--chart-history)" stroke="var(--surface)" stroke-width="1.5"><title>' +
          data[i].iso + ' · ' + data[i].value.toFixed(decimals) + ' ' + unit + '</title></circle>');
      });
    }

    parts.push('</svg>');
    return parts.join('');
  }

  /* ------------------------------- 分布直方图 ------------------------------- */

  function distributionSvg(points, options) {
    const o = options || {};
    const unit = o.unit || '';
    const decimals = o.decimals == null ? 2 : o.decimals;
    const data = (points || []).filter(function (p) { return p && isFinite(p.value); });
    if (!data.length) {
      return '<div class="chart-empty">当前时间范围没有可用采样</div>';
    }

    const values = data.map(function (p) { return p.value; });
    let lo = Math.min.apply(null, values);
    let hi = Math.max.apply(null, values);
    if (hi - lo < 1e-6) { hi = lo + 0.5; lo -= 0.5; }
    const bins = 12;
    const width = (hi - lo) / bins;
    const counts = new Array(bins).fill(0);
    values.forEach(function (v) {
      let idx = Math.floor((v - lo) / width);
      if (idx >= bins) idx = bins - 1;
      if (idx < 0) idx = 0;
      counts[idx]++;
    });
    const maxCount = Math.max.apply(null, counts) || 1;

    const W = 860;
    const H = 250;
    const padL = 54;
    const padR = 20;
    const padT = 16;
    const padB = 34;
    const plotW = W - padL - padR;
    const plotH = H - padT - padB;
    const slot = plotW / bins;

    const parts = [];
    parts.push('<svg class="metric-svg" viewBox="0 0 ' + W + ' ' + H + '" role="img" aria-label="分布直方图">');

    for (let i = 0; i <= 4; i++) {
      const y = padT + (i / 4) * plotH;
      const label = Math.round(maxCount * (1 - i / 4));
      parts.push('<line x1="' + padL + '" y1="' + y.toFixed(1) + '" x2="' + (padL + plotW) +
        '" y2="' + y.toFixed(1) + '" stroke="var(--chart-grid)" stroke-width="1"/>');
      parts.push('<text x="' + (padL - 10) + '" y="' + (y + 3.5).toFixed(1) +
        '" text-anchor="end" font-size="10" fill="var(--chart-axis)">' + label + '</text>');
    }

    counts.forEach(function (count, i) {
      const h = (count / maxCount) * plotH;
      const x = padL + i * slot + slot * 0.14;
      const w = slot * 0.72;
      const y = padT + plotH - h;
      const from = lo + i * width;
      const to = from + width;
      parts.push('<rect x="' + x.toFixed(1) + '" y="' + y.toFixed(1) + '" width="' + w.toFixed(1) +
        '" height="' + Math.max(0, h).toFixed(1) + '" rx="2" fill="var(--chart-history)" opacity="' +
        (count ? '0.8' : '0.18') + '"><title>' + from.toFixed(decimals) + ' – ' + to.toFixed(decimals) +
        ' ' + unit + ' · ' + count + ' 点</title></rect>');
      if (i % 2 === 0) {
        parts.push('<text x="' + (x + w / 2).toFixed(1) + '" y="' + (H - 12) +
          '" text-anchor="middle" font-size="9.5" fill="var(--chart-axis)">' +
          from.toFixed(decimals) + '</text>');
      }
    });

    parts.push('<text x="' + (padL + plotW) + '" y="' + (H - 12) +
      '" text-anchor="end" font-size="9.5" fill="var(--chart-axis)">' + hi.toFixed(decimals) + ' ' + unit + '</text>');
    parts.push('</svg>');
    return parts.join('');
  }

  /* -------------------------------- 统计摘要 -------------------------------- */

  function summarize(points, metric, summary) {
    const data = (points || []).filter(function (p) { return p && isFinite(p.value); });
    if (!data.length) {
      return { current: null, avg: null, min: null, max: null, low: 0, high: 0, count: 0 };
    }
    const values = data.map(function (p) { return p.value; });
    const avg = values.reduce(function (a, b) { return a + b; }, 0) / values.length;
    const th = resolveThresholds(metric, summary);
    const lowLine = th.length ? Math.min.apply(null, th.map(function (t) { return t.value; })) : null;
    const highLine = th.length ? Math.max.apply(null, th.map(function (t) { return t.value; })) : null;
    let low = 0;
    let high = 0;
    values.forEach(function (v) {
      if (lowLine !== null && v < lowLine) low++;
      if (highLine !== null && v > highLine) high++;
    });
    return {
      current: values[values.length - 1],
      avg: avg,
      min: Math.min.apply(null, values),
      max: Math.max.apply(null, values),
      low: low,
      high: high,
      count: values.length
    };
  }

  /** 单点状态判定，用于明细表的状态列 */
  function statusOf(value, metric, summary) {
    if (!isFinite(value)) return { text: '—', tone: 'neutral' };
    if (metric.key === 'do') {
      const floor = Number((summary || {}).safetyFloor) || 0;
      const on = Number((summary || {}).onThreshold) || 0;
      if (floor && value < floor) return { text: '低于安全下限', tone: 'bad' };
      if (on && value < on) return { text: '低于开机阈值', tone: 'warn' };
      return { text: '正常', tone: 'good' };
    }
    if (metric.key === 'temp') {
      if (value < 18) return { text: '低于适宜下限', tone: 'warn' };
      if (value > 32) return { text: '高于适宜上限', tone: 'warn' };
      return { text: '适宜', tone: 'good' };
    }
    if (value < 6.5) return { text: '偏酸', tone: 'warn' };
    if (value > 8.5) return { text: '偏碱', tone: 'warn' };
    return { text: '适宜', tone: 'good' };
  }

  window.Aqua = window.Aqua || {};
  window.Aqua.metric = {
    METRICS: METRICS,
    trendSvg: trendSvg,
    distributionSvg: distributionSvg,
    summarize: summarize,
    statusOf: statusOf,
    resolveThresholds: resolveThresholds
  };
})();
