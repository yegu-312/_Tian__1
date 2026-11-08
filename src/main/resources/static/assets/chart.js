/* ==========================================================================
   AquaGuard · 轻量 Canvas 图表（无第三方依赖）
   - DoChart：溶氧历史 / 预测 / 三条阈值线 / 增氧时段底色
   - renderPriceBand：分时电价 24 小时色带
   ========================================================================== */
(function () {
  'use strict';

  const FMT = window.Aqua.fmt;
  const ESC = window.Aqua.ui.esc;

  function cssVar(name) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  }

  function clamp(v, min, max) {
    return Math.min(max, Math.max(min, v));
  }

  function niceStep(raw) {
    const value = Math.max(0.05, raw);
    const power = Math.pow(10, Math.floor(Math.log10(value)));
    const normalized = value / power;
    let step;
    if (normalized <= 1) step = 1;
    else if (normalized <= 2) step = 2;
    else if (normalized <= 5) step = 5;
    else step = 10;
    return step * power;
  }

  function tickDecimals(step) {
    if (step >= 1) return 0;
    if (step >= 0.1) return 1;
    return 2;
  }

  class DoChart {
    constructor(canvas, tooltipEl) {
      this.canvas = canvas;
      this.ctx = canvas.getContext('2d');
      this.tooltip = tooltipEl;
      this.emptyEl = canvas.parentElement ? canvas.parentElement.querySelector('.chart-empty') : null;
      this.data = null;
      this.geom = null;
      this.hover = null;
      this.selection = null;
      this.drag = null;
      this.onRangeSelect = null;
      this.onRangeClear = null;
      this.showPrediction = true;
      this.showThresholds = true;
      this.w = 0;
      this.h = 0;

      this._onDown = this._onDown.bind(this);
      this._onMove = this._onMove.bind(this);
      this._onUp = this._onUp.bind(this);
      this._onLeave = this._onLeave.bind(this);

      canvas.addEventListener('pointerdown', this._onDown);
      canvas.addEventListener('pointermove', this._onMove);
      canvas.addEventListener('pointerup', this._onUp);
      canvas.addEventListener('pointercancel', this._onUp);
      canvas.addEventListener('pointerleave', this._onLeave);

      if (window.ResizeObserver) {
        this._ro = new ResizeObserver(() => this.resize());
        this._ro.observe(canvas.parentElement);
      } else {
        window.addEventListener('resize', () => this.resize());
      }
      this.resize();
    }

    setData(data) {
      this.data = data;
      this.hover = null;
      this._hideTooltip();
      if (!data) {
        this.selection = null;
        this.drag = null;
        this._notifyRangeClear();
      }
      this.draw();
    }

    clearSelection() {
      const hadSelection = !!this.selection;
      this.selection = null;
      this.drag = null;
      this._hideTooltip();
      this.draw();
      if (hadSelection) this._notifyRangeClear();
    }

    _notifyRangeClear() {
      if (typeof this.onRangeClear === 'function') this.onRangeClear(this);
    }

    setShowPrediction(show) {
      this.showPrediction = !!show;
      this.draw();
    }

    setShowThresholds(show) {
      this.showThresholds = !!show;
      this.draw();
    }

    resize() {
      if (!this.canvas || !this.canvas.parentElement) return;
      const rect = this.canvas.parentElement.getBoundingClientRect();
      if (rect.width < 10 || rect.height < 10) return;
      const dpr = window.devicePixelRatio || 1;
      this.w = rect.width;
      this.h = rect.height;
      this.canvas.width = Math.round(rect.width * dpr);
      this.canvas.height = Math.round(rect.height * dpr);
      this.canvas.style.width = rect.width + 'px';
      this.canvas.style.height = rect.height + 'px';
      this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      this.draw();
    }

    _hasData() {
      const d = this.data;
      return !!(d && ((d.history && d.history.length) || (d.prediction && d.prediction.length)));
    }

    draw() {
      if (!this.ctx || !this.w || !this.h) return;
      const ctx = this.ctx;
      const w = this.w;
      const h = this.h;
      ctx.clearRect(0, 0, w, h);

      if (this.emptyEl) this.emptyEl.hidden = this._hasData();
      if (!this._hasData()) {
        this.geom = null;
        return;
      }

      const d = this.data;
      const history = (d.history || []).slice().sort((a, b) => a.minuteOffset - b.minuteOffset);
      const prediction = (d.prediction || []).slice().sort((a, b) => a.minuteOffset - b.minuteOffset);
      const now = Number(d.elapsedMinutes) || 0;
      const currentDo = Number(d.currentDo);
      const thresholds = d.thresholds || {};

      const padL = 46;
      const padR = 48;
      const padT = 16;
      const padB = 28;
      const plotW = w - padL - padR;
      const plotH = h - padT - padB;
      if (plotW <= 0 || plotH <= 0) return;

      let xMin = now;
      let xMax = now + 1;
      history.forEach(p => {
        xMin = Math.min(xMin, p.minuteOffset);
        xMax = Math.max(xMax, p.minuteOffset);
      });
      if (this.showPrediction) {
        prediction.forEach(p => {
          xMin = Math.min(xMin, p.minuteOffset);
          xMax = Math.max(xMax, p.minuteOffset);
        });
      }
      if (!history.length && !prediction.length) {
        xMin = now - 5;
        xMax = now + 5;
      }
      if (xMax - xMin < 2) {
        xMin -= 5;
        xMax += 5;
      }

      const values = [];
      if (Number.isFinite(currentDo)) values.push(currentDo);
      history.forEach(p => values.push(Number(p.doValue)));
      if (this.showPrediction) prediction.forEach(p => values.push(Number(p.doValue)));
      if (this.showThresholds) {
        [thresholds.safetyFloor, thresholds.onThreshold, thresholds.offThreshold].forEach(v => {
          if (v !== null && v !== undefined && Number.isFinite(Number(v))) values.push(Number(v));
        });
      }
      let yMin = values.length ? Math.min.apply(null, values) : 0;
      let yMax = values.length ? Math.max.apply(null, values) : 10;
      const span = Math.max(1, yMax - yMin);
      yMin = Math.max(0, yMin - span * 0.18);
      yMax = yMax + span * 0.18;
      const yStep = niceStep((yMax - yMin) / 4);
      yMin = Math.floor(yMin / yStep) * yStep;
      yMax = Math.ceil(yMax / yStep) * yStep;
      if (yMax <= yMin) yMax = yMin + yStep;

      const X = minuteOffset => padL + ((minuteOffset - xMin) / (xMax - xMin)) * plotW;
      const Y = value => padT + ((yMax - value) / (yMax - yMin)) * plotH;

      this.geom = {
        padL, padR, padT, padB, plotW, plotH, xMin, xMax, yMin, yMax, X, Y, now
      };

      /* 增氧时段底色 */
      ctx.fillStyle = cssVar('--aerator-band');
      for (let i = 1; i < history.length; i++) {
        if (history[i - 1].aeratorOn && history[i].aeratorOn) {
          const x1 = X(history[i - 1].minuteOffset);
          const x2 = X(history[i].minuteOffset);
          ctx.fillRect(x1, padT, Math.max(0.5, x2 - x1), plotH);
        }
      }
      if (this.showPrediction && d.aeratorOn) {
        const first = prediction.length ? prediction[0].minuteOffset : now;
        const last = prediction.length ? prediction[prediction.length - 1].minuteOffset : now;
        const x1 = X(first);
        const x2 = X(last);
        if (x2 > x1) ctx.fillRect(x1, padT, x2 - x1, plotH);
      }

      /* 网格与 Y 轴刻度 */
      const decimals = tickDecimals(yStep);
      ctx.font = '500 10px "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif';
      ctx.textBaseline = 'middle';
      ctx.textAlign = 'right';
      ctx.lineWidth = 1;
      for (let v = yMin; v <= yMax + yStep * 0.001; v += yStep) {
        const y = Y(v);
        ctx.strokeStyle = cssVar('--chart-grid');
        ctx.beginPath();
        ctx.moveTo(padL, Math.round(y) + 0.5);
        ctx.lineTo(padL + plotW, Math.round(y) + 0.5);
        ctx.stroke();
        ctx.fillStyle = cssVar('--chart-axis');
        ctx.fillText(Number(v).toFixed(decimals), padL - 9, y);
      }

      /* X 轴时间刻度 */
      const tickCount = clamp(Math.floor(plotW / 96), 3, 7);
      const baseTime = d.pondTime;
      ctx.textBaseline = 'top';
      for (let i = 0; i <= tickCount; i++) {
        const offset = xMin + ((xMax - xMin) * i) / tickCount;
        const x = X(offset);
        ctx.strokeStyle = cssVar('--chart-grid');
        ctx.beginPath();
        ctx.moveTo(Math.round(x) + 0.5, padT);
        ctx.lineTo(Math.round(x) + 0.5, padT + plotH);
        ctx.stroke();
        ctx.fillStyle = cssVar('--chart-axis');
        ctx.textAlign = i === 0 ? 'left' : i === tickCount ? 'right' : 'center';
        const label = baseTime ? FMT.shifted(baseTime, offset - now, false) : Math.round(offset) + 'm';
        ctx.fillText(label, x, padT + plotH + 8);
      }

      /* 三条阈值线 */
      if (this.showThresholds) {
        const list = [
          { value: thresholds.safetyFloor, color: '--chart-floor' },
          { value: thresholds.onThreshold, color: '--chart-on' },
          { value: thresholds.offThreshold, color: '--chart-off' }
        ];
        list.forEach(item => {
          const value = Number(item.value);
          if (!Number.isFinite(value) || value < yMin || value > yMax) return;
          const y = Y(value);
          const color = cssVar(item.color);
          ctx.save();
          ctx.setLineDash([5, 5]);
          ctx.lineWidth = 1.2;
          ctx.strokeStyle = color;
          ctx.globalAlpha = 0.9;
          ctx.beginPath();
          ctx.moveTo(padL, y);
          ctx.lineTo(padL + plotW, y);
          ctx.stroke();
          ctx.restore();

          ctx.fillStyle = color;
          ctx.font = '700 10px "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif';
          ctx.textAlign = 'right';
          ctx.textBaseline = 'middle';
          ctx.fillText(FMT.num(value, 2), w - 7, y);
        });
      }

      /* 历史曲线与面积 */
      if (history.length) {
        if (history.length > 1) {
          ctx.beginPath();
          ctx.moveTo(X(history[0].minuteOffset), Y(history[0].doValue));
          for (let i = 1; i < history.length; i++) {
            ctx.lineTo(X(history[i].minuteOffset), Y(history[i].doValue));
          }
          ctx.lineTo(X(history[history.length - 1].minuteOffset), padT + plotH);
          ctx.lineTo(X(history[0].minuteOffset), padT + plotH);
          ctx.closePath();
          const grad = ctx.createLinearGradient(0, padT, 0, padT + plotH);
          grad.addColorStop(0, cssVar('--chart-history-fill'));
          grad.addColorStop(1, 'rgba(0,0,0,0)');
          ctx.fillStyle = grad;
          ctx.fill();
        }

        ctx.beginPath();
        history.forEach((p, index) => {
          const x = X(p.minuteOffset);
          const y = Y(p.doValue);
          if (index === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        });
        if (history.length === 1) {
          ctx.arc(X(history[0].minuteOffset), Y(history[0].doValue), 2.2, 0, Math.PI * 2);
          ctx.fillStyle = cssVar('--chart-history');
          ctx.fill();
        } else {
          ctx.strokeStyle = cssVar('--chart-history');
          ctx.lineWidth = 2.3;
          ctx.lineJoin = 'round';
          ctx.lineCap = 'round';
          ctx.stroke();
        }

        /* 历史最后一个点 → 当前状态 的浅色连接线 */
        if (Number.isFinite(currentDo) && history.length) {
          const last = history[history.length - 1];
          const x1 = X(last.minuteOffset);
          const y1 = Y(last.doValue);
          const x2 = X(now);
          const y2 = Y(currentDo);
          if (x2 !== x1 || y2 !== y1) {
            ctx.save();
            ctx.setLineDash([2, 4]);
            ctx.strokeStyle = cssVar('--chart-history');
            ctx.globalAlpha = 0.4;
            ctx.lineWidth = 1.2;
            ctx.beginPath();
            ctx.moveTo(x1, y1);
            ctx.lineTo(x2, y2);
            ctx.stroke();
            ctx.restore();
          }
        }
      }

      /* 预测曲线 */
      if (this.showPrediction && prediction.length) {
        ctx.save();
        ctx.setLineDash([6, 5]);
        ctx.lineWidth = 2;
        ctx.lineJoin = 'round';
        ctx.lineCap = 'round';
        ctx.strokeStyle = cssVar('--chart-prediction');
        ctx.beginPath();
        let started = false;
        if (Number.isFinite(currentDo)) {
          ctx.moveTo(X(now), Y(currentDo));
          started = true;
        }
        prediction.forEach(p => {
          const x = X(p.minuteOffset);
          const y = Y(p.doValue);
          if (!started) {
            ctx.moveTo(x, y);
            started = true;
          } else {
            ctx.lineTo(x, y);
          }
        });
        ctx.stroke();
        ctx.restore();
      }

      /* 当前时刻 */
      if (Number.isFinite(currentDo)) {
        const xn = X(now);
        const yn = Y(currentDo);
        ctx.save();
        ctx.setLineDash([2, 4]);
        ctx.strokeStyle = cssVar('--chart-grid');
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(xn, padT);
        ctx.lineTo(xn, padT + plotH);
        ctx.stroke();
        ctx.restore();

        ctx.beginPath();
        ctx.arc(xn, yn, 8, 0, Math.PI * 2);
        ctx.fillStyle = cssVar('--chart-history-fill');
        ctx.fill();
        ctx.beginPath();
        ctx.arc(xn, yn, 4.4, 0, Math.PI * 2);
        ctx.fillStyle = cssVar('--chart-history');
        ctx.fill();
        ctx.lineWidth = 2;
        ctx.strokeStyle = cssVar('--surface');
        ctx.stroke();
      }

      /* 框选区间 */
      if (this.selection &&
          Number.isFinite(Number(this.selection.start)) &&
          Number.isFinite(Number(this.selection.end))) {
        const start = Math.max(Math.min(this.selection.start, this.selection.end), xMin);
        const end = Math.min(Math.max(this.selection.start, this.selection.end), xMax);
        if (end > start) {
          const x1 = X(start);
          const x2 = X(end);
          ctx.save();
          ctx.fillStyle = cssVar('--chart-selection');
          ctx.fillRect(x1, padT, x2 - x1, plotH);
          ctx.strokeStyle = cssVar('--chart-selection-line');
          ctx.lineWidth = 1;
          ctx.setLineDash([4, 4]);
          ctx.beginPath();
          ctx.moveTo(x1, padT);
          ctx.lineTo(x1, padT + plotH);
          ctx.moveTo(x2, padT);
          ctx.lineTo(x2, padT + plotH);
          ctx.stroke();
          ctx.setLineDash([]);
          ctx.fillStyle = cssVar('--chart-selection-line');
          ctx.fillRect(x1 - 1.5, padT, 3, 10);
          ctx.fillRect(x2 - 1.5, padT, 3, 10);
          ctx.restore();
        }
      }

      /* 悬停指示 */
      if (this.hover && this.hover.point) {
        const hp = this.hover.point;
        const x = X(hp.minuteOffset);
        const y = Y(hp.doValue);
        ctx.save();
        ctx.strokeStyle = cssVar('--chart-axis');
        ctx.globalAlpha = 0.4;
        ctx.setLineDash([3, 3]);
        ctx.beginPath();
        ctx.moveTo(x, padT);
        ctx.lineTo(x, padT + plotH);
        ctx.stroke();
        ctx.restore();
        ctx.beginPath();
        ctx.arc(x, y, 4, 0, Math.PI * 2);
        ctx.fillStyle = hp.kind === 'prediction' ? cssVar('--chart-prediction') : cssVar('--chart-history');
        ctx.fill();
        ctx.lineWidth = 2;
        ctx.strokeStyle = cssVar('--surface');
        ctx.stroke();
      }
    }

    _allPoints() {
      const d = this.data;
      if (!d) return [];
      const list = [];
      (d.history || []).forEach(p => list.push({
        kind: 'history', minuteOffset: Number(p.minuteOffset),
        doValue: Number(p.doValue), temperature: Number(p.temperature),
        aeratorOn: !!p.aeratorOn
      }));
      if (Number.isFinite(Number(d.currentDo)) && Number.isFinite(Number(d.elapsedMinutes))) {
        list.push({
          kind: 'current', minuteOffset: Number(d.elapsedMinutes),
          doValue: Number(d.currentDo), temperature: Number(d.temperature),
          aeratorOn: !!d.aeratorOn
        });
      }
      if (this.showPrediction) {
        (d.prediction || []).forEach(p => list.push({
          kind: 'prediction', minuteOffset: Number(p.minuteOffset),
          doValue: Number(p.doValue), temperature: Number(p.temperature),
          aeratorOn: !!p.aeratorOn
        }));
      }
      return list;
    }

    _offsetFromPx(px) {
      if (!this.geom) return null;
      const ratio = clamp((px - this.geom.padL) / this.geom.plotW, 0, 1);
      return this.geom.xMin + ratio * (this.geom.xMax - this.geom.xMin);
    }

    _onDown(event) {
      if (!this.geom) return;
      if (event.button !== undefined && event.button !== 0) return;
      const rect = this.canvas.getBoundingClientRect();
      const px = event.clientX - rect.left;
      const py = event.clientY - rect.top;
      if (px < this.geom.padL || px > this.geom.padL + this.geom.plotW ||
          py < this.geom.padT || py > this.geom.padT + this.geom.plotH) {
        return;
      }
      if (event.pointerType === 'mouse') event.preventDefault();

      this.drag = {
        pointerId: event.pointerId,
        startPx: px,
        lastPx: px,
        startOffset: this._offsetFromPx(px),
        moved: false
      };
      try { this.canvas.setPointerCapture(event.pointerId); } catch (error) { /* ignore */ }
      this.hover = null;
      this._hideTooltip();
    }

    _onMove(event) {
      if (!this.geom) return;
      const rect = this.canvas.getBoundingClientRect();
      let px = event.clientX - rect.left;

      if (this.drag) {
        if (event.pointerId !== undefined && this.drag.pointerId !== event.pointerId) return;
        px = clamp(px, this.geom.padL, this.geom.padL + this.geom.plotW);
        this.drag.lastPx = px;
        if (Math.abs(px - this.drag.startPx) > 6) {
          this.drag.moved = true;
          const endOffset = this._offsetFromPx(px);
          this.selection = {
            start: Math.min(this.drag.startOffset, endOffset),
            end: Math.max(this.drag.startOffset, endOffset)
          };
          this.hover = null;
          this._hideTooltip();
          this.draw();
        }
        return;
      }

      if (px < this.geom.padL || px > this.geom.padL + this.geom.plotW) {
        this._hideTooltip();
        if (this.hover) {
          this.hover = null;
          this.draw();
        }
        return;
      }

      const points = this._allPoints();
      let best = null;
      let bestDistance = Infinity;
      points.forEach(point => {
        const distance = Math.abs(this.geom.X(point.minuteOffset) - px);
        if (distance < bestDistance && distance <= 32) {
          bestDistance = distance;
          best = point;
        }
      });

      if (!best) {
        this._hideTooltip();
        if (this.hover) {
          this.hover = null;
          this.draw();
        }
        return;
      }

      this.hover = { point: best };
      this.draw();
      this._showTooltip(best, px);
    }

    _onUp(event) {
      if (!this.drag) return;
      const drag = this.drag;
      this.drag = null;
      try {
        if (this.canvas.hasPointerCapture && this.canvas.hasPointerCapture(drag.pointerId)) {
          this.canvas.releasePointerCapture(drag.pointerId);
        }
      } catch (error) { /* ignore */ }

      this.draw();
      if (drag.moved && this.selection && typeof this.onRangeSelect === 'function') {
        this.onRangeSelect(this.selection, this);
      }
    }

    _showTooltip(point, px) {
      if (!this.tooltip || !this.geom) return;
      const d = this.data;
      const offset = point.minuteOffset - this.geom.now;
      const timeText = d.pondTime ? FMT.shifted(d.pondTime, offset, true) : '--';
      const kindLabel = point.kind === 'prediction' ? '预测' : point.kind === 'current' ? '当前' : '历史';
      const color = point.kind === 'prediction' ? 'var(--chart-prediction)' : 'var(--chart-history)';

      this.tooltip.innerHTML =
        '<div class="tip-time">' + ESC(timeText) + '</div>' +
        '<div class="tip-row"><span class="tip-dot" style="background:' + color + '"></span>' +
          '溶氧 <strong>' + FMT.num(point.doValue) + '</strong> mg/L' +
          '<span class="tip-muted">· ' + kindLabel + '</span></div>' +
        '<div class="tip-row tip-muted">水温 ' + FMT.num(point.temperature, 1) + ' ℃ · 增氧机' +
          (point.aeratorOn ? '运行' : '关闭') + '</div>';

      const y = this.geom.Y(point.doValue);
      const left = clamp(px, 84, this.w - 84);
      this.tooltip.style.left = left + 'px';
      this.tooltip.style.top = Math.max(y - 6, 58) + 'px';
      this.tooltip.classList.add('show');
    }

    _hideTooltip() {
      if (this.tooltip) this.tooltip.classList.remove('show');
    }

    _onLeave() {
      if (this.drag) return;
      this._hideTooltip();
      if (this.hover) {
        this.hover = null;
        this.draw();
      }
    }
  }

  /* --------------------------- 分时电价色带 --------------------------- */

  function renderPriceBand(container, periods, currentHour, options) {
    if (!container) return;
    const opts = options || {};
    const showLabels = opts.labels !== false;
    const list = (periods || []).slice().sort((a, b) => Number(a.startHour) - Number(b.startHour));
    const html = list.map(p => {
      const type = FMT.normalizePeriodType(p.periodType);
      const start = clamp(Number(p.startHour), 0, 24);
      const end = clamp(Number(p.endHour), 0, 24);
      const left = (start / 24) * 100;
      const width = Math.max(0, ((end - start) / 24) * 100);
      const label = showLabels && width >= 7 ? ESC(p.periodDesc || type) : '';
      const title = ESC((p.periodDesc || type) + ' ' + start + ':00 - ' + end + ':00 · ' + FMT.num(p.price, 2) + ' 元/kWh');
      return '<span class="seg ' + type + '" style="left:' + left + '%;width:' + width + '%" title="' + title + '">' +
        '<span>' + label + '</span></span>';
    }).join('');

    const hour = Number(currentHour);
    const nowMark = Number.isFinite(hour)
      ? '<span class="now" style="left:' + (clamp(hour, 0, 23.99) / 24 * 100) + '%" title="当前时段"></span>'
      : '';

    container.innerHTML = html + nowMark;
  }

  function priceForHour(periods, hour) {
    const h = Number(hour);
    if (!Number.isFinite(h)) return null;
    const list = periods || [];
    for (let i = 0; i < list.length; i++) {
      const p = list[i];
      if (h >= Number(p.startHour) && h < Number(p.endHour)) return p;
    }
    return null;
  }

  window.Aqua = window.Aqua || {};
  window.Aqua.DoChart = DoChart;
  window.Aqua.renderPriceBand = renderPriceBand;
  window.Aqua.priceForHour = priceForHour;
})();
