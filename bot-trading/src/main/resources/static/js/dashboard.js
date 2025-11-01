(() => {
  const luxon = window.luxon;
  const DateTime = luxon.DateTime;
  const qs = new URLSearchParams(window.location.search);

  const elements = {
    symbol: document.getElementById('symbol'),
    interval: document.getElementById('interval'),
    from: document.getElementById('from'),
    to: document.getElementById('to'),
    side: document.getElementById('side'),
    status: document.getElementById('status'),
    apply: document.getElementById('applyFilters'),
    groupBy: document.getElementById('groupBy'),
    anchorTs: document.getElementById('anchorTs'),
    toggles: {
      vwap: document.getElementById('toggleVwap'),
      anchoredVwap: document.getElementById('toggleAnchoredVwap'),
      atr: document.getElementById('toggleAtr'),
      supertrend: document.getElementById('toggleSupertrend'),
      volume: document.getElementById('toggleVolume'),
      markers: document.getElementById('toggleAdvancedMarkers')
    },
    exports: {
      chartPng: document.getElementById('exportChartPng'),
      tradesCsv: document.getElementById('exportTradesCsv'),
      tradesJson: document.getElementById('exportTradesJson'),
      summaryCsv: document.getElementById('exportSummaryCsv'),
      heatmapCsv: document.getElementById('exportHeatmapCsv')
    },
    kpiContainer: document.getElementById('kpiContainer'),
    tradesTable: document.querySelector('#tradesTable tbody'),
    summaryTable: document.querySelector('#summaryTable tbody'),
    heatmap: document.getElementById('heatmap')
  };

  function initFromDefaults() {
    const body = document.body;
    const defaultSymbol = body.dataset.defaultSymbol;
    const defaultInterval = body.dataset.defaultInterval;
    const defaultFrom = body.dataset.defaultFrom;
    const defaultTo = body.dataset.defaultTo;

    if (qs.get('symbol')) {
      elements.symbol.value = qs.get('symbol');
    } else {
      elements.symbol.value = defaultSymbol;
    }
    if (qs.get('interval')) {
      elements.interval.value = qs.get('interval');
    } else {
      elements.interval.value = defaultInterval;
    }
    const setDateTime = (input, value) => {
      if (!value) return;
      const dt = DateTime.fromISO(value, { zone: 'utc' });
      if (dt.isValid) {
        input.value = dt.toUTC().toFormat("yyyy-LL-dd'T'HH:mm");
      }
    };
    setDateTime(elements.from, qs.get('from') || defaultFrom);
    setDateTime(elements.to, qs.get('to') || defaultTo);
    elements.side.value = qs.get('side') || '';
    elements.status.value = qs.get('status') || '';
    elements.groupBy.value = qs.get('groupBy') || 'day';
    if (qs.get('anchorTs')) {
      setDateTime(elements.anchorTs, qs.get('anchorTs'));
      elements.toggles.anchoredVwap.checked = true;
    }
    elements.toggles.vwap.checked = qs.get('vwap') !== 'false';
    elements.toggles.atr.checked = qs.get('atr') !== 'false';
    elements.toggles.supertrend.checked = qs.get('supertrend') === 'true';
    elements.toggles.volume.checked = qs.get('volume') !== 'false';
    elements.toggles.markers.checked = qs.get('markers') !== 'false';
  }

  function toIso(value) {
    if (!value) return null;
    return DateTime.fromFormat(value, "yyyy-LL-dd'T'HH:mm", { zone: 'utc' }).toUTC().toISO();
  }

  function buildParams() {
    const params = {
      symbol: elements.symbol.value,
      interval: elements.interval.value,
      from: toIso(elements.from.value),
      to: toIso(elements.to.value),
      side: elements.side.value || undefined,
      status: elements.status.value || undefined,
      groupBy: elements.groupBy.value,
      anchorTs: elements.anchorTs.value ? toIso(elements.anchorTs.value) : undefined,
      toggles: {
        vwap: elements.toggles.vwap.checked,
        anchored: elements.toggles.anchoredVwap.checked,
        atr: elements.toggles.atr.checked,
        supertrend: elements.toggles.supertrend.checked,
        volume: elements.toggles.volume.checked,
        markers: elements.toggles.markers.checked
      }
    };
    return params;
  }

  function syncQueryString(params) {
    const next = new URLSearchParams();
    next.set('symbol', params.symbol);
    next.set('interval', params.interval);
    if (params.from) next.set('from', params.from);
    if (params.to) next.set('to', params.to);
    if (params.side) next.set('side', params.side);
    if (params.status) next.set('status', params.status);
    if (params.anchorTs) next.set('anchorTs', params.anchorTs);
    next.set('groupBy', params.groupBy);
    next.set('vwap', params.toggles.vwap);
    next.set('atr', params.toggles.atr);
    next.set('supertrend', params.toggles.supertrend);
    next.set('volume', params.toggles.volume);
    next.set('markers', params.toggles.markers);
    window.history.replaceState(null, '', `${window.location.pathname}?${next.toString()}`);
  }

  let priceChart;
  let volumeChart;
  let equityChart;
  let drawdownChart;
  let heatmapInstance;

  const crosshairPlugin = {
    id: 'crosshair-sync',
    afterEvent(chart, args) {
      const event = args.event;
      if (!priceChart || !equityChart || !drawdownChart) return;
      if (chart !== priceChart) return;
      if (event.type === 'mouseout') {
        equityChart.setActiveElements([]);
        drawdownChart.setActiveElements([]);
        equityChart.update();
        drawdownChart.update();
        return;
      }
      const points = chart.getElementsAtEventForMode(event.native, 'index', { intersect: false });
      if (!points.length) return;
      const index = points[0].index;
      const ts = chart.data.labels[index];
      const syncCharts = [equityChart, drawdownChart];
      syncCharts.forEach((c) => {
        const matchIndex = c.data.labels.indexOf(ts);
        if (matchIndex >= 0) {
          c.setActiveElements([{ datasetIndex: 0, index: matchIndex }]);
          c.tooltip.setActiveElements([{ datasetIndex: 0, index: matchIndex }], { x: 0, y: 0 });
          c.update();
        }
      });
    }
  };
  Chart.register(crosshairPlugin);

  function toCandle(klines) {
    return klines.map((k) => ({
      x: k.closeTime,
      o: Number(k.open),
      h: Number(k.high),
      l: Number(k.low),
      c: Number(k.close)
    }));
  }

  function toVolume(klines) {
    return klines.map((k) => ({ x: k.closeTime, y: Number(k.volume) }));
  }

  function fetchData(params) {
    const base = {
      symbol: params.symbol,
      interval: params.interval,
      from: params.from,
      to: params.to
    };
    const requests = [
      axios.get('/api/market/klines', { params: { ...base, limit: 1500 } }),
      axios.get('/api/reports/trades', { params: { ...base, side: params.side, status: params.status, size: 500 } }),
      axios.get('/api/reports/summary', { params: { ...base, groupBy: params.groupBy } }),
      axios.get('/api/reports/equity', { params: base }),
      axios.get('/api/reports/drawdown', { params: base }),
      axios.get('/api/reports/annotations', { params: { ...base, includeAdvanced: params.toggles.markers } }),
      axios.get('/api/reports/heatmap', { params: base })
    ];
    if (params.toggles.vwap) {
      requests.push(axios.get('/api/market/vwap', { params: base }));
    } else {
      requests.push(Promise.resolve({ data: [] }));
    }
    if (params.toggles.anchored && params.anchorTs) {
      requests.push(
        axios.get('/api/market/vwap', { params: { ...base, anchorTs: params.anchorTs } })
      );
    } else {
      requests.push(Promise.resolve({ data: [] }));
    }
    if (params.toggles.atr) {
      requests.push(axios.get('/api/indicators/atr-bands', { params: base }));
    } else {
      requests.push(Promise.resolve({ data: [] }));
    }
    if (params.toggles.supertrend) {
      requests.push(axios.get('/api/indicators/supertrend', { params: base }));
    } else {
      requests.push(Promise.resolve({ data: [] }));
    }
    return Promise.all(requests);
  }

  function renderCharts(params, responses) {
    const [klinesRes, tradesRes, summaryRes, equityRes, drawdownRes, annotationsRes, heatmapRes, vwapRes, anchoredVwapRes, atrRes, supertrendRes] = responses;
    const klines = klinesRes.data || [];
    const candles = toCandle(klines);
    const volumes = toVolume(klines);
    const annotations = annotationsRes.data || [];
    const vwap = vwapRes.data || [];
    const anchoredVwap = anchoredVwapRes.data || [];
    const atr = atrRes.data || [];
    const supertrend = supertrendRes.data || [];

    const ctx = document.getElementById('priceChart');
    const volCtx = document.getElementById('volumeChart');
    const eqCtx = document.getElementById('equityChart');
    const ddCtx = document.getElementById('drawdownChart');

    const labels = candles.map((c) => c.x);
    const kpi = buildKpi(summaryRes.data);
    renderKpi(kpi);
    renderTrades(tradesRes.data.content || []);
    renderSummary(summaryRes.data || []);
    renderHeatmap(heatmapRes.data);

    const markerData = buildMarkers(annotations);
    if (priceChart) priceChart.destroy();
    const datasets = [
      {
        label: 'Price',
        data: candles,
        type: 'candlestick',
        yAxisID: 'y',
        borderColor: '#1f6feb',
        color: {
          up: '#16a34a',
          down: '#dc2626',
          unchanged: '#6b7280'
        }
      }
    ];
    if (markerData.length && params.toggles.markers) {
      datasets.push(...markerData);
    }
    if (params.toggles.vwap && vwap.length) {
      datasets.push({
        label: 'VWAP',
        data: vwap.map((p) => ({ x: p.ts, y: Number(p.value) })),
        type: 'line',
        borderColor: '#fbbf24',
        borderWidth: 1.5,
        pointRadius: 0,
        tension: 0.1,
        yAxisID: 'y'
      });
    }
    if (params.toggles.anchored && anchoredVwap.length) {
      datasets.push({
        label: 'Anchored VWAP',
        data: anchoredVwap.map((p) => ({ x: p.ts, y: Number(p.value) })),
        type: 'line',
        borderColor: '#a855f7',
        borderDash: [4, 4],
        borderWidth: 1.2,
        pointRadius: 0,
        tension: 0.1,
        yAxisID: 'y'
      });
    }
    if (params.toggles.atr && atr.length) {
      datasets.push(
        {
          label: 'ATR Upper',
          data: atr.map((p) => ({ x: p.ts, y: Number(p.upper) })),
          type: 'line',
          borderColor: 'rgba(59,130,246,0.6)',
          borderWidth: 1,
          pointRadius: 0,
          fill: '+1',
          yAxisID: 'y'
        },
        {
          label: 'ATR Lower',
          data: atr.map((p) => ({ x: p.ts, y: Number(p.lower) })),
          type: 'line',
          borderColor: 'rgba(59,130,246,0.6)',
          borderWidth: 1,
          pointRadius: 0,
          yAxisID: 'y'
        }
      );
    }
    if (params.toggles.supertrend && supertrend.length) {
      datasets.push({
        label: 'Supertrend',
        data: supertrend.map((p) => ({ x: p.ts, y: Number(p.line) })),
        type: 'line',
        borderColor: '#ef4444',
        borderWidth: 1.3,
        pointRadius: 0,
        yAxisID: 'y'
      });
    }

    priceChart = new Chart(ctx, {
      type: 'candlestick',
      data: { labels, datasets },
      options: {
        parsing: false,
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'bottom' },
          tooltip: {
            mode: 'index',
            intersect: false,
            callbacks: {
              label: (context) => {
                if (context.dataset.type === 'candlestick') {
                  const v = context.raw;
                  return `O:${v.o.toFixed(2)} H:${v.h.toFixed(2)} L:${v.l.toFixed(2)} C:${v.c.toFixed(2)}`;
                }
                if (context.dataset.type === 'scatter') {
                  return `${context.dataset.label}: ${context.raw.y.toFixed(2)}`;
                }
                return `${context.dataset.label}: ${context.parsed.y.toFixed(2)}`;
              }
            }
          }
        },
        scales: {
          x: {
            type: 'timeseries',
            adapters: { date: { zone: 'utc' } }
          },
          y: {
            position: 'left',
            beginAtZero: false
          }
        }
      }
    });

    if (volumeChart) volumeChart.destroy();
    volumeChart = new Chart(volCtx, {
      type: 'bar',
      data: {
        labels,
        datasets: [
          {
            label: 'Volume',
            data: volumes,
            backgroundColor: 'rgba(59,130,246,0.35)',
            borderWidth: 0
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { display: false },
          y: { beginAtZero: true }
        }
      }
    });
    volumeChart.canvas.parentElement.style.display = params.toggles.volume ? 'block' : 'none';

    const equity = (equityRes.data || []).map((p) => ({ x: p.ts, y: Number(p.value) }));
    const drawdown = (drawdownRes.data || []).map((p) => ({ x: p.ts, y: Number(p.value) }));

    if (equityChart) equityChart.destroy();
    equityChart = new Chart(eqCtx, {
      type: 'line',
      data: {
        labels: equity.map((p) => p.x),
        datasets: [
          {
            label: 'Equity',
            data: equity,
            borderColor: '#10b981',
            borderWidth: 1.5,
            pointRadius: 0,
            tension: 0.1
          }
        ]
      },
      options: {
        parsing: false,
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { x: { type: 'timeseries' } }
      }
    });

    if (drawdownChart) drawdownChart.destroy();
    drawdownChart = new Chart(ddCtx, {
      type: 'line',
      data: {
        labels: drawdown.map((p) => p.x),
        datasets: [
          {
            label: 'Drawdown',
            data: drawdown,
            borderColor: '#f97316',
            borderWidth: 1.5,
            pointRadius: 0,
            tension: 0.1,
            fill: true,
            backgroundColor: 'rgba(249,115,22,0.12)'
          }
        ]
      },
      options: {
        parsing: false,
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { x: { type: 'timeseries' } }
      }
    });
  }

  function buildKpi(summary) {
    if (!summary || !summary.length) return [];
    const range = summary[summary.length - 1];
    return [
      { label: 'Net PnL', value: fmt(range.netPnL) },
      { label: 'Trades', value: range.trades },
      { label: 'Win Rate', value: pct(range.winRate) },
      { label: 'Profit Factor', value: fmt(range.profitFactor) },
      { label: 'Max DD', value: pct(range.maxDrawdown) },
      { label: 'Sharpe', value: fmt(range.sharpe) },
      { label: 'Sortino', value: fmt(range.sortino) }
    ];
  }

  function renderKpi(items) {
    elements.kpiContainer.innerHTML = '';
    items.forEach((item) => {
      const col = document.createElement('div');
      col.className = 'col-6';
      col.innerHTML = `
        <div class="kpi">
          <div class="text-muted small">${item.label}</div>
          <div class="fs-5 fw-semibold">${item.value ?? '--'}</div>
        </div>`;
      elements.kpiContainer.appendChild(col);
    });
  }

  function renderTrades(trades) {
    const rows = trades.map((t) => `
      <tr>
        <td>${formatTs(t.executedAt)}</td>
        <td>${t.symbol}</td>
        <td>${t.side}</td>
        <td>${fmt(t.price)}</td>
        <td>${fmt(t.quantity)}</td>
        <td>${fmt(t.fee)}</td>
        <td>${fmt(t.pnl)}</td>
        <td>${fmt(t.pnlR)}</td>
        <td>${fmt(t.slippageBps)}</td>
        <td>${escape(t.decisionNote)}</td>
      </tr>`);
    elements.tradesTable.innerHTML = rows.join('');
  }

  function renderSummary(summary) {
    const rows = summary.map((s) => `
      <tr>
        <td>${s.label}</td>
        <td>${s.trades}</td>
        <td>${s.wins}</td>
        <td>${s.losses}</td>
        <td>${pct(s.winRate)}</td>
        <td>${fmt(s.grossPnL)}</td>
        <td>${fmt(s.netPnL)}</td>
        <td>${fmt(s.fees)}</td>
        <td>${fmt(s.profitFactor)}</td>
        <td>${pct(s.maxDrawdown)}</td>
        <td>${fmt(s.sharpe)}</td>
        <td>${fmt(s.sortino)}</td>
      </tr>`);
    elements.summaryTable.innerHTML = rows.join('');
  }

  function renderHeatmap(data) {
    if (!heatmapInstance) {
      heatmapInstance = h337.create({ container: elements.heatmap, radius: 20 });
    }
    if (!data || !data.cells) {
      heatmapInstance.setData({ max: 0, data: [] });
      return;
    }
    const values = data.cells.map((cell) => ({
      x: (cell.x / 23) * elements.heatmap.clientWidth,
      y: (cell.y / 6) * elements.heatmap.clientHeight,
      value: Number(cell.netPnl)
    }));
    const max = Math.max(...values.map((v) => Math.abs(v.value)), 1);
    heatmapInstance.setData({ max, data: values });
  }

  function buildMarkers(annotations) {
    const shapes = {
      BUY: 'triangle',
      SELL: 'triangle',
      SL: 'rectRot',
      TP: 'rect',
      BE: 'rectRounded',
      TRAIL: 'circle'
    };
    const colors = {
      BUY: '#22c55e',
      SELL: '#ef4444',
      SL: '#fb923c',
      TP: '#60a5fa',
      BE: '#9ca3af',
      TRAIL: '#facc15'
    };
    return annotations.map((a) => ({
      label: a.type,
      type: 'scatter',
      data: [{ x: a.ts, y: Number(a.price) }],
      yAxisID: 'y',
      pointRadius: 6,
      pointBackgroundColor: colors[a.type] || '#f59e0b',
      pointBorderColor: '#111827',
      pointBorderWidth: 1,
      pointStyle: (ctx) => (ctx.dataIndex === 0 ? shapes[a.type] || 'circle' : 'circle'),
      tooltip: {
        callbacks: {
          label: () =>
            `${a.type} @ ${fmt(a.price)} qty ${fmt(a.qty)} PnL ${fmt(a.pnl)} fee ${fmt(a.fee)} slippage ${fmt(a.slippageBps)}`,
          afterLabel: () => (a.text ? a.text : '')
        }
      }
    }));
  }

  function fmt(value) {
    if (value === null || value === undefined) return '--';
    if (typeof value === 'number') return value.toFixed(2);
    if (typeof value === 'string') return value;
    if (value instanceof Object && 'toString' in value) return value.toString();
    return `${value}`;
  }

  function pct(value) {
    if (value === null || value === undefined) return '--';
    return `${(Number(value) * 100).toFixed(2)}%`;
  }

  function formatTs(value) {
    if (!value) return '--';
    return DateTime.fromISO(value, { zone: 'utc' }).toFormat('yyyy-LL-dd HH:mm');
  }

  function escape(value) {
    if (!value) return '';
    return value.replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function exportFile(url, params) {
    const query = new URLSearchParams(params).toString();
    window.open(`${url}?${query}`, '_blank');
  }

  function attachEvents() {
    elements.apply.addEventListener('click', () => refresh());
    elements.groupBy.addEventListener('change', () => refresh());
    Object.values(elements.toggles).forEach((toggle) =>
      toggle.addEventListener('change', () => refresh(false))
    );
    elements.exports.chartPng.addEventListener('click', () => {
      const link = document.createElement('a');
      link.href = priceChart.toBase64Image('image/png', 1);
      link.download = 'chart.png';
      link.click();
    });
    elements.exports.tradesCsv.addEventListener('click', () => {
      const params = buildParams();
      exportFile('/api/reports/trades/export.csv', params);
    });
    elements.exports.tradesJson.addEventListener('click', () => {
      const params = buildParams();
      exportFile('/api/reports/trades/export.json', params);
    });
    elements.exports.summaryCsv.addEventListener('click', () => {
      const params = buildParams();
      exportFile('/api/reports/summary/export.csv', params);
    });
    elements.exports.heatmapCsv.addEventListener('click', () => {
      const params = buildParams();
      exportFile('/api/reports/heatmap/export.csv', params);
    });
  }

  function refresh(updateQuery = true) {
    const params = buildParams();
    if (updateQuery) syncQueryString(params);
    fetchData(params)
      .then((responses) => renderCharts(params, responses))
      .catch((err) => {
        console.error('Failed to load dashboard', err);
      });
  }

  initFromDefaults();
  attachEvents();
  refresh();
})();
