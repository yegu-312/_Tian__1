/* ==========================================================================
   AquaGuard · API 客户端
   后端统一返回 { code, message, data, timestamp }，code === 200 视为成功
   ========================================================================== */
(function () {
  'use strict';

  const isFile = window.location.protocol === 'file:';
  const ORIGIN = isFile ? 'http://localhost:8080' : window.location.origin;
  const BASE = ORIGIN + '/api';
  const OP_KEY = 'aquaguard.operator';

  class ApiError extends Error {
    constructor(message, code) {
      super(message || '请求失败');
      this.name = 'ApiError';
      this.code = code;
    }
  }

  /**
   * 操作人会写进 X-Operator，后端会落系统日志。
   * HTTP 头只能安全承载 ASCII，因此这里限制为英文 / 数字 / _ . -
   */
  function normalizeOperator(raw) {
    const cleaned = String(raw == null ? '' : raw)
      .trim()
      .replace(/[^A-Za-z0-9_.-]/g, '')
      .slice(0, 24);
    return cleaned || 'admin';
  }

  function getOperator() {
    try {
      return normalizeOperator(window.localStorage.getItem(OP_KEY));
    } catch (e) {
      return 'admin';
    }
  }

  function setOperator(value) {
    const normalized = normalizeOperator(value);
    try {
      window.localStorage.setItem(OP_KEY, normalized);
    } catch (e) { /* 隐私模式下忽略 */ }
    return normalized;
  }

  /** 会话失效时统一回到登录页；登录页自身的请求通过 suppressAuthRedirect 跳过 */
  function redirectToLogin() {
    if (window.location.pathname === '/login.html') return;
    try {
      window.location.replace('/login.html');
    } catch (e) { /* ignore */ }
  }

  async function request(path, options) {
    const opts = options || {};
    const method = opts.method || 'GET';
    const skipAuthRedirect = !!opts.suppressAuthRedirect;
    let url;
    try {
      url = new URL(BASE + path);
    } catch (e) {
      throw new ApiError('接口地址不正确：' + path, 0);
    }
    if (opts.params) {
      Object.entries(opts.params).forEach(function (entry) {
        const key = entry[0];
        const value = entry[1];
        if (value !== undefined && value !== null && value !== '') {
          url.searchParams.set(key, String(value));
        }
      });
    }

    const headers = { Accept: 'application/json' };
    if (opts.body !== undefined) {
      headers['Content-Type'] = 'application/json;charset=UTF-8';
    }
    if (method !== 'GET' && path.indexOf('/auth/') !== 0) {
      headers['X-Operator'] = getOperator();
    }

    let response;
    try {
      response = await fetch(url.toString(), {
        method: method,
        headers: headers,
        body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
        cache: 'no-store',
        credentials: 'same-origin'
      });
    } catch (e) {
      throw new ApiError('无法连接后端（' + ORIGIN + '），请确认服务已启动', 0);
    }

    const text = await response.text();
    let payload = null;
    if (text) {
      try {
        payload = JSON.parse(text);
      } catch (e) {
        throw new ApiError('响应不是合法 JSON（HTTP ' + response.status + '）', response.status);
      }
    }

    if (response.status === 401) {
      if (!skipAuthRedirect) redirectToLogin();
      throw new ApiError((payload && payload.message) || '登录已过期，请重新登录', 401);
    }
    if (!response.ok) {
      throw new ApiError((payload && payload.message) || ('HTTP ' + response.status), response.status);
    }
    if (payload && typeof payload.code === 'number' && payload.code !== 200) {
      if (payload.code === 401 && !skipAuthRedirect) redirectToLogin();
      throw new ApiError(payload.message || '请求失败', payload.code);
    }
    return payload ? payload.data : null;
  }

  /**
   * POST + SSE 流式读取。handlers 以事件名为 key：
   * text / tool / pending_action / done / error；连接提前结束时调用 closed。
   */
  async function streamSse(path, body, handlers, signal) {
    const h = handlers || {};
    let response;
    try {
      response = await fetch(BASE + path, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json;charset=UTF-8',
          'Accept': 'text/event-stream, application/json',
          'X-Operator': getOperator()
        },
        body: JSON.stringify(body || {}),
        cache: 'no-store',
        credentials: 'same-origin',
        signal: signal
      });
    } catch (e) {
      if (e && e.name === 'AbortError') throw e;
      throw new ApiError('无法连接后端（' + ORIGIN + '），请确认服务已启动', 0);
    }

    if (response.status === 401) {
      redirectToLogin();
      throw new ApiError('登录已过期，请重新登录', 401);
    }

    const contentType = response.headers.get('Content-Type') || '';
    if (!response.ok || contentType.indexOf('text/event-stream') < 0) {
      let text = '';
      try { text = await response.text(); } catch (e) { /* ignore */ }
      let payload = null;
      if (text) {
        try { payload = JSON.parse(text); } catch (e) { /* ignore */ }
      }
      if (payload && payload.code === 401) {
        redirectToLogin();
        throw new ApiError(payload.message || '登录已过期，请重新登录', 401);
      }
      const message = (payload && payload.message) || ('HTTP ' + response.status);
      throw new ApiError(message, response.status);
    }

    if (!response.body || !response.body.getReader) {
      throw new ApiError('当前浏览器不支持流式响应', 0);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let finished = false;

    function dispatch(block) {
      let event = 'message';
      const dataLines = [];
      block.split('\n').forEach(function (line) {
        if (!line || line.charAt(0) === ':') return;
        if (line.indexOf('event:') === 0) {
          event = line.slice(6).trim();
        } else if (line.indexOf('data:') === 0) {
          dataLines.push(line.slice(5).replace(/^ /, ''));
        }
      });
      if (!dataLines.length) return false;

      const raw = dataLines.join('\n');
      let payload = raw;
      try { payload = JSON.parse(raw); } catch (e) { /* 允许纯文本 data */ }

      const handler = h[event];
      if (typeof handler === 'function') handler(payload);
      return event === 'done' || event === 'error';
    }

    try {
      while (!finished) {
        const chunk = await reader.read();
        if (chunk.done) break;
        buffer += decoder.decode(chunk.value, { stream: true });
        buffer = buffer.replace(/\r\n/g, '\n');

        let index;
        while ((index = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, index);
          buffer = buffer.slice(index + 2);
          if (dispatch(block)) {
            finished = true;
            break;
          }
        }
      }

      if (!finished) {
        buffer += decoder.decode();
        buffer = buffer.replace(/\r\n/g, '\n');
        if (buffer.trim() && dispatch(buffer.trim())) {
          finished = true;
        }
      }

      if (!finished && typeof h.closed === 'function') {
        h.closed();
      }
    } finally {
      if (finished) {
        try { reader.cancel(); } catch (e) { /* ignore */ }
      }
    }
  }

  const api = {
    baseUrl: BASE,

    /* 登录与当前用户 */
    auth: {
      login: function (username, password) {
        return request('/auth/login', {
          method: 'POST',
          body: { username: username, password: password },
          suppressAuthRedirect: true
        });
      },
      register: function (dto) {
        return request('/auth/register', {
          method: 'POST',
          body: dto,
          suppressAuthRedirect: true
        });
      },
      me: function () {
        return request('/auth/me', { suppressAuthRedirect: true });
      },
      updateAccount: function (dto) {
        return request('/auth/account', { method: 'PUT', body: dto });
      },
      logout: function () {
        return request('/auth/logout', { method: 'POST', suppressAuthRedirect: true });
      }
    },

    /* 基础体检 */
    ping: function () { return request('/ping'); },
    dbCheck: function () { return request('/db-check'); },

    /* 鱼塘管理 */
    ponds: {
      list: function () { return request('/ponds'); },
      create: function (pondName) {
        return request('/ponds', { method: 'POST', body: { pondName: pondName } });
      }
    },

    /* 实时监控 */
    realtime: function (predictMinutes, historyLimit, pondId) {
      return request('/monitor/realtime', {
        params: { pondId: pondId, predictMinutes: predictMinutes, historyLimit: historyLimit }
      });
    },
    compare: function (pondIds, predictMinutes, historyLimit) {
      return request('/monitor/compare', {
        params: {
          pondIds: Array.isArray(pondIds) ? pondIds.join(',') : pondIds,
          predictMinutes: predictMinutes,
          historyLimit: historyLimit
        }
      });
    },
    history: function (limit, pondId) {
      return request('/monitor/history', { params: { limit: limit, pondId: pondId } });
    },
    buffer: function (pondId) { return request('/monitor/buffer', { params: { pondId: pondId } }); },

    /* 鱼塘时钟 */
    clock: {
      pause: function (pondId) {
        return request('/clock/pause', { method: 'POST', params: { pondId: pondId } });
      },
      resume: function (pondId) {
        return request('/clock/resume', { method: 'POST', params: { pondId: pondId } });
      },
      speed: function (speed, pondId) {
        return request('/clock/speed', { method: 'POST', params: { speed: speed, pondId: pondId } });
      },
      setTime: function (time, pondId) {
        return request('/clock/time', { method: 'POST', params: { time: time, pondId: pondId } });
      }
    },

    /* 水体参数 */
    pond: {
      get: function (pondId) { return request('/pond/config', { params: { pondId: pondId } }); },
      update: function (dto, pondId) {
        return request('/pond/config', { method: 'PUT', params: { pondId: pondId }, body: dto });
      }
    },

    /* 增氧策略 */
    aerator: {
      getStrategy: function (pondId) {
        return request('/aerator/strategy', { params: { pondId: pondId } });
      },
      updateStrategy: function (dto, pondId) {
        return request('/aerator/strategy', { method: 'PUT', params: { pondId: pondId }, body: dto });
      },
      manual: function (on, pondId) {
        return request('/aerator/manual', { method: 'POST', params: { on: !!on, pondId: pondId } });
      }
    },

    /* 分时电价 */
    electricity: {
      get: function () { return request('/electricity'); },
      update: function (periods) {
        return request('/electricity', { method: 'PUT', body: { periods: periods } });
      }
    },

    /* 告警 */
    alerts: {
      list: function (params) { return request('/alert/list', { params: params }); },
      ack: function (id) { return request('/alert/ack/' + encodeURIComponent(id), { method: 'PUT' }); }
    },

    /* 日志 */
    logs: {
      operation: function (params) { return request('/log/operation', { params: params }); },
      aerator: function (params) { return request('/log/aerator', { params: params }); }
    },

    /* 系统设置 */
    settings: {
      get: function (pondId) { return request('/setting', { params: { pondId: pondId } }); },
      update: function (dto, pondId) {
        return request('/setting', { method: 'PUT', params: { pondId: pondId }, body: dto });
      },
      reset: function (pondId) {
        return request('/setting/reset', { method: 'POST', params: { pondId: pondId } });
      }
    },

    /* AI 助手 */
    agent: {
      status: function () { return request('/agent/status'); },
      history: function (limit) {
        return request('/agent/history', { params: { limit: limit } });
      },
      reset: function () { return request('/agent/reset', { method: 'POST' }); },
      reject: function (actionId) {
        return request('/agent/reject', { method: 'POST', body: { actionId: actionId } });
      },
      chat: function (message, pondId, handlers, signal) {
        return streamSse('/agent/chat', { message: message, pondId: pondId }, handlers, signal);
      },
      confirm: function (actionId, handlers, signal) {
        return streamSse('/agent/confirm', { actionId: actionId }, handlers, signal);
      }
    }
  };

  window.Aqua = window.Aqua || {};
  window.Aqua.api = api;
  window.Aqua.ApiError = ApiError;
  window.Aqua.getOperator = getOperator;
  window.Aqua.setOperator = setOperator;
  window.Aqua.normalizeOperator = normalizeOperator;
  window.Aqua.origin = ORIGIN;
})();
