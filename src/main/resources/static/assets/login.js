/* ==========================================================================
   AquaGuard · 登录 / 注册页逻辑
   ========================================================================== */
(function () {
  'use strict';

  const Aqua = window.Aqua;
  const api = Aqua.api;
  const ui = Aqua.ui;
  const REMEMBER_KEY = 'aquaguard.rememberedUser';
  const USERNAME_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.-]{2,31}$/;

  const form = document.getElementById('loginForm');
  const usernameInput = document.getElementById('username');
  const passwordInput = document.getElementById('password');
  const rememberInput = document.getElementById('rememberUser');
  const submitBtn = document.getElementById('loginBtn');
  const pwdToggle = document.getElementById('pwdToggle');

  const registerForm = document.getElementById('registerForm');
  const regUsernameInput = document.getElementById('regUsername');
  const regDisplayNameInput = document.getElementById('regDisplayName');
  const regPasswordInput = document.getElementById('regPassword');
  const regConfirmInput = document.getElementById('regConfirm');
  const registerBtn = document.getElementById('registerBtn');
  const regPwdToggle = document.getElementById('regPwdToggle');
  const regConfirmToggle = document.getElementById('regConfirmToggle');

  const cardTitle = document.getElementById('loginCardTitle');
  const cardSubtitle = document.getElementById('loginCardSubtitle');
  const switchHint = document.getElementById('switchHint');
  const switchModeBtn = document.getElementById('switchModeBtn');

  const alertBox = document.getElementById('loginAlert');
  const alertText = document.getElementById('loginAlertText');
  const themeBtn = document.getElementById('themeBtn');

  const ARROW_SVG =
    '<svg class="icon login-submit-arrow" aria-hidden="true"><use href="#i-arrow-right"></use></svg>';

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
    alertText.textContent = message || '操作失败，请稍后重试';
    alertBox.hidden = false;
  }

  function hideAlert() {
    if (alertBox) alertBox.hidden = true;
  }

  function setBusy(button, busy, label) {
    if (!button) return;
    if (busy) {
      button.disabled = true;
      button.innerHTML =
        '<span class="spinner" aria-hidden="true"></span><span>处理中…</span>';
    } else {
      button.disabled = false;
      button.innerHTML = '<span class="login-submit-text">' + label + '</span>' + ARROW_SVG;
    }
  }

  /* ------------------------------- 登录 / 注册切换 ------------------------------- */

  const TEXT = {
    loginTitle: '欢迎回来',
    loginSubtitle: '登录后进入鱼塘溶氧监控台',
    registerTitle: '创建账号',
    registerSubtitle: '注册后自动登录，立即进入监控台'
  };

  function setMode(next) {
    const isRegister = next === 'register';
    if (form) form.hidden = isRegister;
    if (registerForm) registerForm.hidden = !isRegister;
    if (cardTitle) cardTitle.textContent = isRegister ? TEXT.registerTitle : TEXT.loginTitle;
    if (cardSubtitle) cardSubtitle.textContent = isRegister ? TEXT.registerSubtitle : TEXT.loginSubtitle;
    if (switchHint) switchHint.textContent = isRegister ? '已经有账号了？' : '还没有账号？';
    if (switchModeBtn) switchModeBtn.textContent = isRegister ? '返回登录' : '立即注册';
    hideAlert();
    const focusInput = isRegister ? regUsernameInput : usernameInput;
    if (focusInput) focusInput.focus();
  }

  if (switchModeBtn) {
    switchModeBtn.addEventListener('click', function () {
      setMode(registerForm && registerForm.hidden ? 'register' : 'login');
    });
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

  [usernameInput, passwordInput, regUsernameInput, regDisplayNameInput,
    regPasswordInput, regConfirmInput].forEach(function (input) {
    if (!input) return;
    input.addEventListener('input', hideAlert);
  });

  /* ------------------------------- 密码显隐 ------------------------------- */

  function bindPasswordToggle(button, input) {
    if (!button || !input) return;
    button.addEventListener('click', function () {
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      button.setAttribute('aria-pressed', show ? 'true' : 'false');
      button.setAttribute('aria-label', show ? '隐藏密码' : '显示密码');
      button.setAttribute('title', show ? '隐藏密码' : '显示密码');
      button.innerHTML = '<svg class="icon"><use href="#i-' +
        (show ? 'eye-off' : 'eye') + '"></use></svg>';
      input.focus();
    });
  }

  bindPasswordToggle(pwdToggle, passwordInput);
  bindPasswordToggle(regPwdToggle, regPasswordInput);
  bindPasswordToggle(regConfirmToggle, regConfirmInput);

  /* -------------------------------- 登录 / 注册成功 -------------------------------- */

  function completeLogin(user, fallbackUsername) {
    Aqua.setOperator(user && user.username ? user.username : fallbackUsername);
    try {
      if (user && user.displayName) {
        window.localStorage.setItem('aquaguard.displayName', user.displayName);
      }
    } catch (e) { /* 隐私模式下忽略 */ }
    window.location.replace('/');
  }

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
    setBusy(submitBtn, true);

    try {
      const user = await api.auth.login(username, password);

      try {
        if (rememberInput.checked) {
          window.localStorage.setItem(REMEMBER_KEY, username);
        } else {
          window.localStorage.removeItem(REMEMBER_KEY);
        }
      } catch (e) { /* 隐私模式下忽略 */ }

      completeLogin(user, username);
    } catch (error) {
      setBusy(submitBtn, false, '登录管理台');
      submitting = false;
      showAlert(error && error.message ? error.message : '登录失败，请稍后重试');
      passwordInput.focus();
      passwordInput.select();
    }
  });

  /* -------------------------------- 注册提交 -------------------------------- */

  let registering = false;

  if (registerForm) {
    registerForm.addEventListener('submit', async function (event) {
      event.preventDefault();
      if (registering) return;

      const username = regUsernameInput.value.trim();
      const displayName = regDisplayNameInput.value.trim();
      const password = regPasswordInput.value;
      const confirmPassword = regConfirmInput.value;

      if (!username) {
        showAlert('请输入用户名');
        regUsernameInput.focus();
        return;
      }
      if (!USERNAME_PATTERN.test(username)) {
        showAlert('用户名需为 3-32 位，以字母或数字开头，可包含字母、数字、下划线、点或中划线');
        regUsernameInput.focus();
        return;
      }
      if (displayName.length > 32) {
        showAlert('显示名称最多 32 个字符');
        regDisplayNameInput.focus();
        return;
      }
      if (password.length < 6 || password.length > 64) {
        showAlert('密码长度需为 6-64 位');
        regPasswordInput.focus();
        return;
      }
      if (password !== confirmPassword) {
        showAlert('两次输入的密码不一致');
        regConfirmInput.focus();
        regConfirmInput.select();
        return;
      }

      hideAlert();
      registering = true;
      setBusy(registerBtn, true);

      try {
        const user = await api.auth.register({
          username: username,
          displayName: displayName,
          password: password,
          confirmPassword: confirmPassword
        });
        completeLogin(user, username);
      } catch (error) {
        setBusy(registerBtn, false, '注册并进入');
        registering = false;
        showAlert(error && error.message ? error.message : '注册失败，请稍后重试');
        regPasswordInput.focus();
        regPasswordInput.select();
      }
    });
  }
})();
