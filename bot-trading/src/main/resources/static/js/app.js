(function () {
  const toastQueue = [];
  let toastTimer;

  function renderToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) {
      return;
    }
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `<span>${message}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
      toast.classList.add('opacity-0', 'translate-y-2');
      setTimeout(() => toast.remove(), 250);
    }, 3200);
  }

  function flushQueue() {
    if (toastQueue.length === 0) {
      toastTimer = undefined;
      return;
    }
    renderToast(...toastQueue.shift());
    toastTimer = setTimeout(flushQueue, 400);
  }

  window.showToast = function showToast(message, type = 'info') {
    toastQueue.push([message, type]);
    if (!toastTimer) {
      flushQueue();
    }
  };

  function initCharts() {
    if (typeof Chart === 'undefined') {
      return;
    }
    document.querySelectorAll('canvas[data-chart]').forEach((canvas) => {
      try {
        const config = JSON.parse(canvas.dataset.chart || '{}');
        if (!config.type) {
          config.type = 'line';
        }
        const context = canvas.getContext('2d');
        new Chart(context, config); // eslint-disable-line no-new
      } catch (error) {
        console.warn('Error rendering chart', error);
      }
    });
  }

  window.toggleBotMode = async function toggleBotMode(event) {
    const id = event?.currentTarget?.dataset?.botId;
    if (!id) {
      showToast('No se pudo identificar el bot', 'error');
      return;
    }
    try {
      const response = await fetch(`/api/bots/${id}/mode/toggle`, { method: 'POST' });
      if (!response.ok) {
        throw new Error('Respuesta inválida');
      }
      showToast('Modo actualizado correctamente', 'success');
    } catch (error) {
      console.error(error);
      showToast('No se pudo actualizar el modo', 'error');
    }
  };

  window.confirmPause = async function confirmPause(event) {
    const id = event?.currentTarget?.dataset?.botId;
    if (!id) {
      return;
    }
    if (!window.confirm('¿Pausar el bot seleccionado?')) {
      return;
    }
    try {
      const response = await fetch(`/api/bots/${id}/mode/paused`, { method: 'POST' });
      if (!response.ok) {
        throw new Error('Respuesta inválida');
      }
      showToast('Bot pausado', 'info');
    } catch (error) {
      console.error(error);
      showToast('No se pudo pausar el bot', 'error');
    }
  };

  window.appLayout = function appLayout() {
    const stored = localStorage.getItem('theme');
    const prefersDark = stored ? stored === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;

    return {
      darkMode: prefersDark,
      sidebarOpen: false,
      notifications: [],
      init() {
        document.documentElement.classList.toggle('dark', this.darkMode);
        initCharts();
      },
      toggleDarkMode() {
        this.darkMode = !this.darkMode;
        document.documentElement.classList.toggle('dark', this.darkMode);
        localStorage.setItem('theme', this.darkMode ? 'dark' : 'light');
      },
      showToast,
    };
  };

  document.addEventListener('htmx:afterSwap', initCharts);
  document.addEventListener('DOMContentLoaded', initCharts);
})();
