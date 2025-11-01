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
    heatmap: document.getElementById('heatmap'),
    regimeRibbon: document.getElementById('regimeRibbon'),
    regimeLegend: document.getElementById('regimeLegend'),
    status: {
      allocatorBadge: document.getElementById('allocatorBadge'),
      allocatorNote: document.getElementById('allocatorNote'),
      driftBadge: document.getElementById('driftBadge'),
      driftNote: document.getElementById('driftNote'),
      healthBadge: document.getElementById('healthBadge'),
      healthNote: document.getElementById('healthNote'),
      modeBadge: document.getElementById('modeBadge'),
      modeNote: document.getElementById('modeNote'),
      varBadge: document.getElementById('varBadge'),
      varNote: document.getElementById('varNote')
    },
    tca: {
      samples: document.getElementById('tcaSamples'),
      avgBps: document.getElementById('tcaAvgBps'),
      avgQueue: document.getElementById('tcaAvgQueue'),
      recommendation: document.getElementById('tcaRecommendation'),
      hourlyBody: document.querySelector('#tcaHourlyTable tbody')
    },
    bandit: {
      algorithm: document.getElementById('banditAlgorithm'),
      canaryShare: document.getElementById('banditCanaryShare'),
      armsBody: document.querySelector('#banditArmsTable tbody'),
      pullsBody: document.querySelector('#banditPullsTable tbody')
    },
    varSnapshotsBody: document.querySelector('#varSnapshots tbody'),
    varSnapshotMeta: document.getElementById('varSnapshotMeta')
  };

  const currencyFormatter = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
    notation: 'compact'
  });

  function formatCurrency(value) {
    if (!Number.isFinite(value)) {
      return '--';
    }
    return currencyFormatter.format(value);
  }

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
    requests.push(axios.get('/api/regime/status', { params: { symbol: params.symbol } }));
    requests.push(axios.get('/api/status/overview', { params: { symbol: params.symbol } }));
    requests.push(
      axios.get('/api/tca/slippage', {
        params: { symbol: params.symbol, from: params.from || undefined, to: params.to || undefined }
      })
    );
    requests.push(axios.get('/api/var/snapshots', { params: { symbol: params.symbol } }));
    return Promise.all(requests);
  }

  function renderCharts(params, responses) {
    const [
      klinesRes,
      tradesRes,
      summaryRes,
      equityRes,
      drawdownRes,
      annotationsRes,
      heatmapRes,
      vwapRes,
      anchoredVwapRes,
      atrRes,
      supertrendRes,
      regimeStatusRes,
      overviewRes,
      tcaRes,
      varSnapshotsRes
    ] = responses;
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
    renderVarSnapshots(varSnapshotsRes?.data || []);

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

    renderRegime(regimeStatusRes?.data);
    renderStatusCards(overviewRes?.data);
    renderTcaPanel(tcaRes?.data);
    const regimePayload = regimeStatusRes?.data;
    const currentTrend =
      regimePayload?.status?.regime?.trend || regimePayload?.trend || regimePayload?.status?.trend;
    loadBanditData(params.symbol, currentTrend);
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

  function renderBanditArms(arms) {
    const tbody = elements.bandit?.armsBody;
    if (!tbody) return;
    tbody.innerHTML = '';
    if (!Array.isArray(arms) || !arms.length) {
      const row = document.createElement('tr');
      row.innerHTML = '<td colspan="6" class="text-muted">No bandit data</td>';
      tbody.appendChild(row);
      return;
    }
    const rows = arms.map((arm) => {
      const preset = arm.presetId ? arm.presetId.substring(0, 8) : 'n/a';
      const mean = Number.isFinite(arm.stats?.mean) ? arm.stats.mean.toFixed(3) : '--';
      const variance = Number.isFinite(arm.stats?.variance)
        ? arm.stats.variance.toFixed(3)
        : '--';
      return `
        <tr>
          <td title="${arm.presetId}">${preset}</td>
          <td>${arm.stats?.pulls ?? 0}</td>
          <td>${mean}</td>
          <td>${variance}</td>
          <td>${arm.status}</td>
          <td>${arm.role}</td>
        </tr>`;
    });
    tbody.innerHTML = rows.join('');
  }

  function renderBanditPulls(pulls) {
    const tbody = elements.bandit?.pullsBody;
    if (!tbody) return;
    tbody.innerHTML = '';
    if (!Array.isArray(pulls) || !pulls.length) {
      const row = document.createElement('tr');
      row.innerHTML = '<td colspan="7" class="text-muted">No pulls recorded</td>';
      tbody.appendChild(row);
      return;
    }
    const rows = pulls.map((pull) => {
      const preset = pull.armId ? pull.armId.substring(0, 8) : 'n/a';
      const reward = Number.isFinite(pull.reward) ? pull.reward.toFixed(3) : '--';
      const pnlR = Number.isFinite(pull.pnlR) ? pull.pnlR.toFixed(3) : '--';
      const slippage = Number.isFinite(pull.slippageBps) ? pull.slippageBps.toFixed(2) : '--';
      const fees = Number.isFinite(pull.feesBps) ? pull.feesBps.toFixed(2) : '--';
      return `
        <tr>
          <td>${formatTs(pull.timestamp)}</td>
          <td title="${pull.armId}">${preset}</td>
          <td>${reward}</td>
          <td>${pnlR}</td>
          <td>${slippage}</td>
          <td>${fees}</td>
          <td>${pull.decisionId || ''}</td>
        </tr>`;
    });
    tbody.innerHTML = rows.join('');
  }

  function loadBanditData(symbol, trend) {
    if (!elements.bandit?.armsBody) {
      return;
    }
    const params = {
      symbol,
      regime: trend || undefined,
      side: 'BUY'
    };
    Promise.all([
      axios.get('/api/bandit/arms', { params }),
      axios.get('/api/bandit/pulls', { params: { ...params, limit: 20 } }),
      axios.get('/api/bandit/overview', { params: { symbol } })
    ])
      .then(([armsRes, pullsRes, overviewRes]) => {
        renderBanditArms(armsRes.data || []);
        renderBanditPulls(pullsRes.data || []);
        if (overviewRes?.data) {
          const overview = overviewRes.data;
          if (elements.bandit.algorithm) {
            elements.bandit.algorithm.textContent = `Algo: ${overview.algorithm}`;
          }
          if (elements.bandit.canaryShare) {
            const pctShare = (Number(overview.candidateShare) * 100).toFixed(1);
            elements.bandit.canaryShare.textContent = `Canary share: ${pctShare}%`;
          }
        }
      })
      .catch(() => {
        renderBanditArms([]);
        renderBanditPulls([]);
        if (elements.bandit.algorithm) {
          elements.bandit.algorithm.textContent = '';
        }
        if (elements.bandit.canaryShare) {
          elements.bandit.canaryShare.textContent = '';
        }
      });
  }

  function renderRegime(payload) {
    const ribbon = elements.regimeRibbon;
    const legend = elements.regimeLegend;
    if (!ribbon || !legend) return;
    ribbon.innerHTML = '';
    if (!payload || !payload.status) {
      legend.textContent = '--';
      return;
    }
    const current = payload.status;
    const history = Array.isArray(current.history) ? current.history.slice(-60) : [];
    const segments = history
      .map((entry) => ({
        entry,
        ts: entry.timestamp ? new Date(entry.timestamp).getTime() : Number.NaN
      }))
      .filter((item) => Number.isFinite(item.ts))
      .sort((a, b) => a.ts - b.ts);

    if (!segments.length) {
      legend.textContent = 'No regime data';
    }

    for (let i = 0; i < segments.length; i++) {
      const { entry, ts } = segments[i];
      const nextTs = i < segments.length - 1 ? segments[i + 1].ts : ts + 60_000;
      const duration = Math.max(1, nextTs - ts);
      const trend = (entry.trend || 'RANGE').toLowerCase();
      const volatility = (entry.volatility || 'LO').toLowerCase();
      const segment = document.createElement('div');
      segment.className = `regime-segment trend-${trend} vol-${volatility}`;
      segment.style.flexGrow = duration.toString();
      segment.title = `${entry.trend || 'RANGE'} / ${entry.volatility || 'LO'}`;
      ribbon.appendChild(segment);
    }

    if (current.regime) {
      const r = current.regime;
      const atrValue =
        typeof r.normalizedAtr === 'number' && Number.isFinite(r.normalizedAtr)
          ? `${(r.normalizedAtr * 100).toFixed(2)}%`
          : '--';
      const adxValue =
        typeof r.adx === 'number' && Number.isFinite(r.adx) ? r.adx.toFixed(1) : '--';
      legend.textContent = `${r.trend} · ${r.volatility} · ATR ${atrValue} · ADX ${adxValue}`;
    } else {
      legend.textContent = '--';
    }
  }

  function setPill(element, label, state) {
    if (!element) return;
    element.textContent = label || '--';
    element.classList.remove('ok', 'warn', 'error');
    if (state) {
      element.classList.add(state);
    }
  }

  function renderStatusCards(overview) {
    const status = elements.status;
    if (!status) return;
    if (!overview) {
      Object.values(status).forEach((el) => {
        if (el) {
          el.classList && el.classList.remove('ok', 'warn', 'error');
          el.textContent = '--';
        }
      });
      return;
    }

    const allocator = overview.allocator;
    if (allocator) {
      setPill(status.allocatorBadge, allocator.allowed ? 'Allowed' : 'Blocked', allocator.allowed ? 'ok' : 'error');
      status.allocatorNote.textContent = `${allocator.reason || 'OK'} · x${Number(
        allocator.sizingMultiplier ?? 1
      ).toFixed(2)}`;
    } else {
      setPill(status.allocatorBadge, '--');
      status.allocatorNote.textContent = '--';
    }

    const drift = overview.drift;
    if (drift) {
      const stage = (drift.stage || 'NORMAL').toUpperCase();
      const stageState = stage === 'NORMAL' ? 'ok' : stage === 'REDUCED' ? 'warn' : 'error';
      setPill(status.driftBadge, stage, stageState);
      const pf =
          drift.live && Number.isFinite(drift.live.profitFactor)
            ? drift.live.profitFactor.toFixed(2)
            : '--';
      const win =
          drift.live && Number.isFinite(drift.live.winRate)
            ? `${(drift.live.winRate * 100).toFixed(1)}%`
            : '--';
      status.driftNote.textContent = `x${Number(drift.sizingMultiplier ?? 1).toFixed(2)} · PF ${pf} · Win ${win}`;
    } else {
      setPill(status.driftBadge, '--');
      status.driftNote.textContent = '--';
    }

    const health = overview.health;
    if (health) {
      const healthy = Boolean(health.healthy);
      const healthState = healthy ? 'ok' : 'error';
      setPill(status.healthBadge, healthy ? 'Healthy' : 'Degraded', healthState);
      const errorRate = Number.isFinite(health.apiErrorRatePct)
        ? `${Number(health.apiErrorRatePct).toFixed(1)}%`
        : '--';
      status.healthNote.textContent = `Errors ${errorRate} · WS ${health.wsReconnects ?? 0}/h`;
    } else {
      setPill(status.healthBadge, '--');
      status.healthNote.textContent = '--';
    }

    const trading = overview.trading || {};
    const killSwitch = Boolean(trading.killSwitch);
    const liveEnabled = Boolean(trading.liveEnabled);
    const mode = (trading.mode || 'UNKNOWN').toString().toUpperCase();
    let label = 'Shadow';
    let state = 'warn';
    if (killSwitch || mode === 'PAUSED') {
      label = 'Paused';
      state = 'error';
    } else if (liveEnabled) {
      label = 'Live';
      state = 'ok';
    } else if (mode === 'LIVE') {
      label = 'Live (disabled)';
    }
    setPill(status.modeBadge, label, state);
    status.modeNote.textContent = `Mode ${mode} · Kill-switch ${killSwitch ? 'ON' : 'OFF'}`;

    const varData = overview.var || {};
    if (status.varBadge) {
      const ratio = Number(varData.ratio);
      let varState = 'ok';
      if (ratio >= 1) {
        varState = 'error';
      } else if (ratio >= 0.7) {
        varState = 'warn';
      }
      const cvarValue = Number(varData.cvar);
      setPill(status.varBadge, cvarValue ? `-${formatCurrency(cvarValue)}` : '--', varState);
      const sizeRatio = Number(varData.qtyRatio);
      const exposurePct = Number.isFinite(ratio) ? `${(ratio * 100).toFixed(1)}% budget` : '--';
      const sizeText = Number.isFinite(sizeRatio) ? `Size x${sizeRatio.toFixed(2)}` : 'Size --';
      status.varNote.textContent = `${sizeText} · ${exposurePct}`;
    }
  }

  function renderTcaPanel(stats) {
    const tca = elements.tca;
    if (!tca) return;
    if (!stats) {
      tca.samples.textContent = '0 samples';
      tca.avgBps.textContent = '--';
      tca.avgQueue.textContent = '--';
      tca.recommendation.textContent = '--';
      if (tca.hourlyBody) tca.hourlyBody.innerHTML = '';
      return;
    }
    const samples = Number(stats.samples || 0);
    tca.samples.textContent = `${samples} sample${samples === 1 ? '' : 's'}`;
    tca.avgBps.textContent = Number.isFinite(stats.averageBps) ? stats.averageBps.toFixed(2) : '--';
    tca.avgQueue.textContent = Number.isFinite(stats.averageQueueMs)
      ? stats.averageQueueMs.toFixed(0)
      : '--';
    let recommendation = '--';
    if (samples > 0 && Number.isFinite(stats.averageBps)) {
      if (stats.averageBps > 8) {
        recommendation = 'Prefer LIMIT';
      } else if (stats.averageBps < 4) {
        recommendation = 'Prefer MARKET';
      } else {
        recommendation = 'Keep baseline';
      }
    }
    tca.recommendation.textContent = recommendation;

    if (tca.hourlyBody) {
      tca.hourlyBody.innerHTML = '';
      const entries = Object.entries(stats.hourlyAverage || {})
        .map(([hour, value]) => ({ hour: Number(hour), value: Number(value) }))
        .filter((item) => Number.isFinite(item.hour))
        .sort((a, b) => a.hour - b.hour);
      entries.forEach(({ hour, value }) => {
        const row = document.createElement('tr');
        const hourLabel = `${hour.toString().padStart(2, '0')}:00`;
        row.innerHTML = `<td>${hourLabel}</td><td>${Number.isFinite(value) ? value.toFixed(2) : '--'}</td>`;
        tca.hourlyBody.appendChild(row);
      });
    }
  }

  function renderVarSnapshots(rows) {
    const tbody = elements.varSnapshotsBody;
    const meta = elements.varSnapshotMeta;
    if (!tbody) return;
    tbody.innerHTML = '';
    const snapshots = Array.isArray(rows) ? rows.slice(0, 20) : [];
    if (meta) {
      meta.textContent = snapshots.length ? `${snapshots.length} shown` : 'No samples';
    }
    if (!snapshots.length) {
      return;
    }
    snapshots.forEach((row) => {
      const tr = document.createElement('tr');
      const dt = row.timestamp ? DateTime.fromISO(row.timestamp, { zone: 'utc' }) : null;
      const tsCell = document.createElement('td');
      tsCell.textContent = dt && dt.isValid ? dt.toFormat('HH:mm:ss') : '--';
      const cvarCell = document.createElement('td');
      const cvarValue = Number(row.cvar);
      cvarCell.textContent = cvarValue ? `-${formatCurrency(cvarValue)}` : '--';
      const ratioCell = document.createElement('td');
      const ratio = Number(row.qtyRatio);
      ratioCell.textContent = Number.isFinite(ratio) ? `x${ratio.toFixed(2)}` : '--';
      const reasonCell = document.createElement('td');
      let reasonText = '--';
      if (row.reasonsJson) {
        try {
          const reasons = JSON.parse(row.reasonsJson);
          if (Array.isArray(reasons) && reasons.length) {
            reasonText = reasons.map((r) => r.code || r).join(', ');
          }
        } catch (err) {
          reasonText = row.reasonsJson;
        }
      }
      reasonCell.textContent = reasonText;
      tr.append(tsCell, cvarCell, ratioCell, reasonCell);
      tbody.appendChild(tr);
    });
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
