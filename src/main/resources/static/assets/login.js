/* ==========================================================================
   AquaGuard · 登录页逻辑
   ========================================================================== */
(function () {
  'use strict';

  const Aqua = window.Aqua;
  const api = Aqua.api;
  const ui = Aqua.ui;
  const REMEMBER_KEY = 'aquaguard.rememberedUser';

  const form = document.getElementById('loginForm');
  const usernameInput = document.getElementById('username');
  const passwordInput = document.getElementById('password');
  const rememberInput = document.getElementById('rememberUser');
  const submitBtn = document.getElementById('loginBtn');
  const pwdToggle = document.getElementById('pwdToggle');
  const alertBox = document.getElementById('loginAlert');
  const alertText = document.getElementById('loginAlertText');
  const themeBtn = document.getElementById('themeBtn');

  ui.initTheme();
  if (themeBtn) {
    themeBtn.addEventListener('click', ui.toggleTheme);
  }

  /* ----------------------------- 已有会话检查 ----------------------------- */

  api.auth.me().then(function (user) {
    if (user) {
      window.location.replace('/');
    }
  }).catch(function () {
    /* 未登录 / 后端未启动：留在登录页 */
  });

  /* ------------------------------- 表单状态 ------------------------------- */

  function showAlert(message) {
    if (!alertBox || !alertText) return;
    alertText.textContent = message || '登录失败，请稍后重试';
    alertBox.hidden = false;
  }

  function hideAlert() {
    if (alertBox) alertBox.hidden = true;
  }

  function setBusy(busy) {
    if (!submitBtn) return;
    if (busy) {
      submitBtn.disabled = true;
      submitBtn.innerHTML =
        '<span class="spinner" aria-hidden="true"></span><span>正在登录…</span>';
    } else {
      submitBtn.disabled = false;
      submitBtn.innerHTML =
        '<span class="login-submit-text">登录管理台</span>' +
        '<svg class="icon login-submit-arrow" aria-hidden="true"><use href="#i-arrow-right"></use></svg>';
    }
  }

  /* ------------------------------- 记住用户名 ------------------------------ */

  let remembered = null;
  try { remembered = window.localStorage.getItem(REMEMBER_KEY); } catch (e) { /* ignore */ }

  if (remembered) {
    usernameInput.value = remembered;
    rememberInput.checked = true;
    passwordInput.focus();
  } else {
    usernameInput.focus();
  }

  usernameInput.addEventListener('input', hideAlert);
  passwordInput.addEventListener('input', hideAlert);

  /* ------------------------------- 密码显隐 ------------------------------- */

  pwdToggle.addEventListener('click', function () {
    const show = passwordInput.type === 'password';
    passwordInput.type = show ? 'text' : 'password';
    pwdToggle.setAttribute('aria-pressed', show ? 'true' : 'false');
    pwdToggle.setAttribute('aria-label', show ? '隐藏密码' : '显示密码');
    pwdToggle.setAttribute('title', show ? '隐藏密码' : '显示密码');
    pwdToggle.innerHTML = '<svg class="icon"><use href="#i-' +
      (show ? 'eye-off' : 'eye') + '"></use></svg>';
    passwordInput.focus();
  });

  /* -------------------------------- 登录提交 -------------------------------- */

  let submitting = false;

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    if (submitting) return;

    const username = usernameInput.value.trim();
    const password = passwordInput.value;

    if (!username) {
      showAlert('请输入用户名');
      usernameInput.focus();
      return;
    }
    if (!password) {
      showAlert('请输入密码');
      passwordInput.focus();
      return;
    }

    hideAlert();
    submitting = true;
    setBusy(true);

    try {
      const user = await api.auth.login(username, password);
      Aqua.setOperator(user && user.username ? user.username : username);

      try {
        if (rememberInput.checked) {
          window.localStorage.setItem(REMEMBER_KEY, username);
        } else {
          window.localStorage.removeItem(REMEMBER_KEY);
        }
        if (user && user.displayName) {
          window.localStorage.setItem('aquaguard.displayName', user.displayName);
        }
      } catch (e) { /* 隐私模式下忽略 */ }

      window.location.replace('/');
    } catch (error) {
      setBusy(false);
      submitting = false;
      showAlert(error && error.message ? error.message : '登录失败，请稍后重试');
      passwordInput.focus();
      passwordInput.select();
    }
  });
})();
