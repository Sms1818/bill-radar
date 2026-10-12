'use strict';

document.addEventListener('DOMContentLoaded', () => {
  setupEventListeners();
  initDashboard();
});

let webhookData = null;
let toastTimer = null;

async function initDashboard() {
  await checkHealth();
  try {
    await loadDashboard();
  } catch (error) {
    console.error('Initial dashboard load failed', error);
    showToast('Could not load the dashboard. Check the application logs and database connection.', 'error');
  }
}

function setupEventListeners() {
  document.getElementById('btn-ingest')?.addEventListener('click', handleRunIngestion);
  document.getElementById('btn-webhook-preview')?.addEventListener('click', openWebhookModal);
  document.getElementById('modal-close')?.addEventListener('click', closeWebhookModal);
  document.getElementById('btn-copy-webhook')?.addEventListener('click', copyWebhookJson);

  const modal = document.getElementById('webhook-modal');
  modal?.addEventListener('click', event => {
    if (event.target === modal) closeWebhookModal();
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape') { closeWebhookModal(); closeIngestionModal(); }
  });
  document.querySelectorAll('.nav-item').forEach(link => {
    link.addEventListener('click', event => {
      event.preventDefault();
      activateView((link.getAttribute('href') || '#overview').slice(1));
    });
  });
  document.getElementById('ingestion-modal-close')?.addEventListener('click', closeIngestionModal);
  document.getElementById('ingestion-modal-done')?.addEventListener('click', closeIngestionModal);
  const ingestionModal = document.getElementById('ingestion-modal');
  ingestionModal?.addEventListener('click', event => {
    if (event.target === ingestionModal) closeIngestionModal();
  });
  const initialView = window.location.hash.slice(1);
  activateView(['overview', 'budget-alerts', 'workloads', 'data-quality', 'activity'].includes(initialView) ? initialView : 'overview');
}

async function checkHealth() {
  const dot = document.getElementById('health-dot');
  const label = document.getElementById('health-text');
  try {
    const response = await fetch('/api/health', { headers: { Accept: 'application/json' } });
    const data = await response.json();
    const healthy = response.ok && data.status === 'UP';
    dot?.classList.toggle('is-up', healthy);
    dot?.classList.toggle('is-down', !healthy);
    if (label) label.textContent = healthy ? 'Database connected' : 'Database unavailable';
  } catch (error) {
    dot?.classList.remove('is-up');
    dot?.classList.add('is-down');
    if (label) label.textContent = 'Service unreachable';
  }
}

async function loadDashboard() {
  const summaryResponse = await fetch('/api/costs/summary', { headers: { Accept: 'application/json' } });
  if (!summaryResponse.ok) {
    renderLoadError('The cost summary could not be loaded. Check that the application and database are running.');
    throw new Error(`Cost summary request failed (${summaryResponse.status})`);
  }

  const summary = await summaryResponse.json();
  const [alerts, quality, history] = await Promise.all([
    safeFetch('/api/alerts', []),
    safeFetch('/api/data-quality', null),
    safeFetch('/api/workflow/history', [])
  ]);

  renderPeriod(summary);
  renderStatusBanner(summary, alerts);
  renderKpis(summary);
  renderAlerts(alerts);
  renderWorkloadChart(summary.workloads || []);
  renderWorkloadsTable(summary.workloads || []);
  if (quality) renderDataQuality(quality, summary);
  renderWorkflowHistory(history);

  const updated = document.getElementById('last-updated');
  if (updated) updated.textContent = `Synced ${new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
}

async function safeFetch(url, fallback) {
  try {
    const response = await fetch(url, { headers: { Accept: 'application/json' } });
    if (!response.ok) return fallback;
    return await response.json();
  } catch (error) {
    console.warn(`Could not load ${url}`, error);
    return fallback;
  }
}

function renderLoadError(message) {
  const alerts = document.getElementById('alerts-container');
  if (alerts) alerts.innerHTML = `<div class="alert-card warning"><span class="alert-rail"></span><div class="alert-content"><div class="alert-title">Dashboard data unavailable</div><div class="alert-msg">${escapeHtml(message)}</div></div></div>`;
  const workloads = document.getElementById('workloads-tbody');
  if (workloads) workloads.innerHTML = `<tr><td colspan="7" class="table-empty">${escapeHtml(message)}</td></tr>`;
  const chart = document.getElementById('workload-chart');
  if (chart) chart.innerHTML = `<div class="table-empty">Cost analysis will appear here when the API is available.</div>`;
}

function renderPeriod(summary) {
  const [year, month] = String(summary.billingMonth || '2026-10').split('-').map(Number);
  const period = new Date(year, month - 1, 1);
  const periodLabel = period.toLocaleDateString('en-GB', { month: 'long', year: 'numeric' });
  const periodEl = document.getElementById('billing-period');
  const dateEl = document.getElementById('as-of-date');
  if (periodEl) periodEl.textContent = periodLabel;
  if (dateEl && summary.asOfDate) {
    const asOf = new Date(`${summary.asOfDate}T12:00:00`);
    dateEl.textContent = `As of ${asOf.toLocaleDateString('en-GB', { day: 'numeric', month: 'long' })}`;
  }
  const forecastLabel = document.getElementById('forecast-confidence-label');
  if (forecastLabel) forecastLabel.textContent = `${summary.daysInMonth || period.getDate()}-day month`;
}

function renderStatusBanner(summary, alerts) {
  const status = String(summary.overallStatus || 'OK').toUpperCase();
  const breaches = (alerts || []).filter(alert => alert.status === 'BREACH').length;
  const warnings = (alerts || []).filter(alert => alert.status === 'WARNING').length;
  const headline = document.getElementById('status-headline');
  const description = document.getElementById('status-description');
  const badge = document.getElementById('kpi-status-badge');

  if (status === 'BREACH') {
    if (headline) headline.textContent = 'Some workloads are on course to overspend.';
    if (description) description.textContent = `${breaches} budget breach signal${breaches === 1 ? '' : 's'} need attention. The forecast uses the current daily run rate.`;
  } else if (status === 'WARNING') {
    if (headline) headline.textContent = 'A few budgets are getting tight.';
    if (description) description.textContent = `${warnings} workload${warnings === 1 ? '' : 's'} is approaching its limit. Review the forecast before month-end.`;
  } else {
    if (headline) headline.textContent = 'Spend is tracking within budget.';
    if (description) description.textContent = 'No workload is currently forecast to cross its configured budget threshold.';
  }
  if (badge) {
    badge.textContent = status;
    badge.className = `status-pill status-${status.toLowerCase()}`;
  }
}

function renderKpis(summary) {
  setText('kpi-mtd-spend', formatEur(summary.monthToDateSpend));
  setText('kpi-untagged-spend', `Unallocated: ${formatEur(summary.untaggedSpend)} · ${summary.untaggedRecordCount ?? 0} records`);
  setText('kpi-projected-spend', formatEur(summary.projectedMonthEndSpend));
  setText('kpi-forecast-formula', `${formatEur(summary.dailyAverageSpend)} per day × ${summary.daysInMonth || 0} days`);
  setText('kpi-total-budget', formatEur(summary.totalBudget));

  const headroom = Number(summary.projectedBudgetOverrunOrHeadroom ?? 0);
  setText('kpi-headroom', headroom >= 0 ? `${formatEur(headroom)} projected headroom` : `${formatEur(Math.abs(headroom))} projected over budget`);
  setText('kpi-projected-util', `${formatPercent(summary.projectedUtilizationPercent)}%`);
  setText('utilization-caption', `${formatPercent(summary.budgetUtilizationPercent)}% of budget used so far`);

  const utilization = Math.max(0, Number(summary.projectedUtilizationPercent) || 0);
  const utilBar = document.getElementById('overall-util-bar');
  if (utilBar) {
    utilBar.style.width = `${Math.min(100, utilization)}%`;
    utilBar.style.background = utilization > 100 ? 'var(--red)' : utilization >= 85 ? 'var(--amber)' : '#8aa763';
  }
  const spendLine = document.getElementById('mtd-budget-line');
  if (spendLine) {
    const used = Math.max(0, Number(summary.budgetUtilizationPercent) || 0);
    spendLine.style.width = `${Math.min(100, used)}%`;
  }
}

function renderAlerts(alerts) {
  const container = document.getElementById('alerts-container');
  if (!container) return;
  const list = Array.isArray(alerts) ? alerts : [];
  setText('alerts-count-badge', `${list.length} ${list.length === 1 ? 'alert' : 'alerts'}`);
  setText('nav-alert-count', String(list.length));

  if (!list.length) {
    container.innerHTML = `<div class="alert-card ok"><span class="alert-rail"></span><div class="alert-content"><div class="alert-title">No budget alerts</div><div class="alert-msg">Nothing is currently forecast to cross the configured thresholds.</div></div></div>`;
    return;
  }

  container.innerHTML = list.map(alert => {
    const isBreach = alert.status === 'BREACH' || alert.severity === 'CRITICAL';
    const statusClass = isBreach ? 'critical' : 'warning';
    const badgeClass = isBreach ? 'badge-breach' : 'badge-warning';
    const typeLabel = String(alert.type || 'BUDGET_ALERT').replace(/_/g, ' ').toLowerCase();
    const overrun = Number(alert.projectedOverrunAmount || 0);
    return `<article class="alert-card ${statusClass}">
      <span class="alert-rail" aria-hidden="true"></span>
      <div class="alert-content">
        <div class="alert-header"><div class="alert-title">${escapeHtml(alert.workload || 'Unknown workload')} <span style="color:var(--muted);font-weight:500">· ${escapeHtml(typeLabel)}</span></div><span class="badge ${badgeClass}">${escapeHtml(alert.severity || alert.status || 'NOTICE')}</span></div>
        <div class="alert-msg">${escapeHtml(alert.message || 'Budget threshold reached.')}</div>
        <div class="alert-metrics"><span>Spent <strong>${formatEur(alert.currentSpend)}</strong></span><span>Budget <strong>${formatEur(alert.budget)}</strong></span><span>Forecast <strong>${formatEur(alert.projectedSpend)}</strong></span>${overrun > 0 ? `<span>Overrun <strong>${formatEur(overrun)}</strong></span>` : ''}</div>
      </div>
    </article>`;
  }).join('');
}

function renderWorkloadChart(workloads) {
  const chart = document.getElementById('workload-chart');
  if (!chart) return;
  const rows = Array.isArray(workloads) ? [...workloads] : [];
  rows.sort((a, b) => Number(b.projectedUtilizationPercent || 0) - Number(a.projectedUtilizationPercent || 0));
  if (!rows.length) {
    chart.innerHTML = '<div class="table-empty">No workload spend to display.</div>';
    return;
  }
  chart.innerHTML = rows.map(workload => {
    const budget = Math.max(0, Number(workload.budget) || 0);
    const projected = Math.max(0, Number(workload.projectedMonthEndSpend) || 0);
    const scale = Math.max(budget, projected, 1);
    const budgetWidth = Math.min(100, budget / scale * 100);
    const spendWidth = Math.min(100, projected / scale * 100);
    const statusClass = workload.status === 'BREACH' ? 'is-breach' : workload.status === 'WARNING' ? 'is-warning' : '';
    return `<div class="chart-row">
      <div class="chart-label-line"><span class="chart-name">${escapeHtml(workload.workload || 'Unknown workload')}</span><span class="chart-amount">${formatEur(projected)}</span></div>
      <div class="chart-track" aria-label="${escapeHtml(workload.workload || 'Workload')} projected spend ${formatEur(projected)} against budget ${formatEur(budget)}"><span class="chart-budget" style="width:${budgetWidth}%"></span><span class="chart-spend ${statusClass}" style="width:${spendWidth}%"></span></div>
      <div class="chart-subline"><span>Budget ${formatEur(budget)}</span><strong>${formatPercent(workload.projectedUtilizationPercent)}% projected</strong></div>
    </div>`;
  }).join('');
}

function renderWorkloadsTable(workloads) {
  const tbody = document.getElementById('workloads-tbody');
  if (!tbody) return;
  const rows = Array.isArray(workloads) ? workloads : [];
  if (!rows.length) {
    tbody.innerHTML = '<tr><td colspan="7" class="table-empty">No workload records found.</td></tr>';
    return;
  }
  tbody.innerHTML = rows.map(workload => {
    const status = String(workload.status || 'OK').toUpperCase();
    const badgeClass = status === 'BREACH' ? 'badge-breach' : status === 'WARNING' ? 'badge-warning' : 'badge-ok';
    const fillClass = status === 'BREACH' ? 'is-breach' : status === 'WARNING' ? 'is-warning' : '';
    const utilization = Math.max(0, Number(workload.projectedUtilizationPercent) || 0);
    const delta = Number(workload.projectedBudgetOverrunOrHeadroom ?? 0);
    const providers = Object.keys(workload.providerSpend || {}).join(' + ') || 'No tagged provider';
    const initial = String(workload.workload || '?').slice(0, 1);
    return `<tr>
      <td><div class="workload-cell"><span class="workload-avatar">${escapeHtml(initial)}</span><span class="workload-name"><strong>${escapeHtml(workload.workload || 'Unknown')}</strong><small>${escapeHtml(providers)} · ${Number(workload.recordCount || 0)} records</small></span></div></td>
      <td><strong>${formatEur(workload.monthToDateSpend)}</strong></td>
      <td>${formatEur(workload.budget)}</td>
      <td><strong>${formatEur(workload.projectedMonthEndSpend)}</strong></td>
      <td><div class="inline-progress"><span>${formatPercent(utilization)}%</span><span class="inline-progress-track"><span class="${fillClass}" style="width:${Math.min(100, utilization)}%"></span></span></div></td>
      <td><span style="color:${delta < 0 ? 'var(--red)' : 'var(--green-dark)'};font-weight:700">${delta < 0 ? '−' : '+'}${formatEur(Math.abs(delta))}</span></td>
      <td><span class="badge ${badgeClass}">${escapeHtml(status)}</span></td>
    </tr>`;
  }).join('');
}

function renderDataQuality(quality, summary) {
  setText('dq-processed', formatCount(quality.rowsProcessed));
  setText('dq-accepted', formatCount(summary.totalRecordCount));
  setText('dq-duplicates', formatCount(quality.duplicateRowsSkipped));
  const total = Number(quality.rowsProcessed) || 0;
  const untagged = Number(quality.untaggedRows) || 0;
  setText('dq-untagged', total ? `${untagged} · ${(untagged / total * 100).toFixed(1)}%` : String(untagged));
  setText('dq-credits', formatCount(quality.creditRows));
  setText('dq-invalid', formatCount(quality.invalidRows));
  setText('dq-conversions', `${formatCount(quality.currencyConversions)} USD → EUR`);
}

function renderWorkflowHistory(history) {
  const tbody = document.getElementById('history-tbody');
  if (!tbody) return;
  const rows = Array.isArray(history) ? history.slice(0, 5) : [];
  if (!rows.length) {
    tbody.innerHTML = '<tr><td colspan="7" class="table-empty">No imports recorded yet. Run an import to create the first execution.</td></tr>';
    return;
  }
  tbody.innerHTML = rows.map(run => {
    const start = run.startTime ? new Date(run.startTime) : null;
    const finish = run.completionTime ? new Date(run.completionTime) : null;
    const duration = start && finish ? `${Math.max(0, finish - start)} ms` : '—';
    const status = String(run.status || 'UNKNOWN').toUpperCase();
    const badge = status === 'SUCCESS' ? 'badge-ok' : status === 'FAILED' ? 'badge-breach' : 'badge-neutral';
    return `<tr>
      <td><span style="font-family:'DM Mono',monospace;font-size:10px">${escapeHtml(run.executionId || '—')}</span></td>
      <td>${start ? escapeHtml(start.toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })) : '—'}</td>
      <td>${escapeHtml(duration)}</td>
      <td><span class="badge ${badge}">${escapeHtml(status)}</span></td>
      <td>${formatCount(run.recordsProcessed)} / ${formatCount(run.recordsAccepted)}</td>
      <td>${formatCount(run.recordsSkipped)}</td>
      <td><strong>${formatEur(run.totalSpend)}</strong></td>
    </tr>`;
  }).join('');
}

async function handleRunIngestion() {
  const button = document.getElementById('btn-ingest');
  if (!button || button.disabled) return;
  const original = button.innerHTML;
  button.disabled = true;
  button.innerHTML = '<span aria-hidden="true">↻</span> Processing billing…';
  try {
    const response = await fetch('/api/ingest', { method: 'POST', headers: { Accept: 'application/json' } });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.message || `Ingestion failed (${response.status})`);
    try {
      await checkHealth();
      await loadDashboard();
    } catch (refreshError) {
      console.error('Dashboard refresh after ingestion failed', refreshError);
    }
    renderIngestionResult(payload);
  } catch (error) {
    console.error('Ingestion failed', error);
    showToast(`Ingestion failed: ${error.message || 'Unknown error'}`, 'error');
  } finally {
    button.disabled = false;
    button.innerHTML = original;
  }
}

function renderIngestionResult(run) {
  const modal = document.getElementById('ingestion-modal');
  if (!modal) return;
  const processed = Number(run.recordsProcessed) || 0;
  const accepted = Number(run.recordsAccepted) || 0;
  const skipped = Number(run.recordsSkipped) || 0;
  const duplicateRows = Number(run.duplicateRecords ?? run.dataQualityReport?.duplicateRowsSkipped) || 0;
  const existingRows = Math.max(0, skipped - duplicateRows);
  const conversions = Number(run.currencyConversions ?? run.dataQualityReport?.currencyConversions) || 0;
  const untagged = Number(run.untaggedRecords ?? run.dataQualityReport?.untaggedRows) || 0;
  const credits = Number(run.creditRecords ?? run.dataQualityReport?.creditRows) || 0;
  const alerts = Number(run.alertsGenerated) || 0;
  const isSuccess = String(run.status || '').toUpperCase() === 'SUCCESS';
  setText('ingestion-modal-title', isSuccess ? 'Ingestion run complete' : 'Ingestion run failed');
  setText('ingestion-result-intro', isSuccess
    ? `${formatCount(processed)} source rows were processed across the AWS-style and Azure-style billing exports. The results below show what this run did.`
    : (run.errorDetails || 'The ingestion pipeline did not complete.'));
  setText('ingestion-execution-id', `Execution · ${run.executionId || 'not available'}`);

  const steps = [
    ['01', 'Read source files', `${formatCount(processed)} rows received`, isSuccess],
    ['02', 'Check duplicates', `${formatCount(duplicateRows)} duplicate source row${duplicateRows === 1 ? '' : 's'}`, isSuccess],
    ['03', 'Reconcile the ledger', `${formatCount(accepted)} new records saved · ${formatCount(existingRows)} already existed`, isSuccess],
    ['04', 'Refresh cost analysis', `${formatCount(alerts)} budget alerts · ${formatEur(run.totalSpend)} net spend`, isSuccess]
  ];
  const stepsEl = document.getElementById('ingestion-pipeline-steps');
  if (stepsEl) stepsEl.innerHTML = steps.map(([number, title, detail, done]) => `
    <div class="ingestion-step ${done ? 'is-done' : 'is-failed'}"><span class="ingestion-step-number">${number}</span><div><strong>${escapeHtml(title)}</strong><small>${escapeHtml(detail)}</small></div><span class="ingestion-step-status">${done ? '✓' : '!'}</span></div>`).join('');

  const stats = [
    ['Rows processed', processed], ['New records saved', accepted], ['Duplicate source rows', duplicateRows],
    ['Existing rows skipped', existingRows], ['USD → EUR conversions', conversions], ['Credit rows', credits],
    ['Untagged records', untagged], ['Budget alerts generated', alerts]
  ];
  const statsEl = document.getElementById('ingestion-result-stats');
  if (statsEl) statsEl.innerHTML = stats.map(([label, value]) => `<div class="ingestion-stat"><span>${escapeHtml(label)}</span><strong>${formatCount(value)}</strong></div>`).join('');

  const note = document.getElementById('ingestion-result-note');
  if (note) {
    note.className = `ingestion-result-note ${accepted === 0 && isSuccess ? 'is-idempotent' : ''}`;
    note.textContent = !isSuccess
      ? 'The run failed. Review the application logs for diagnostic details; sensitive exception details are not shown here.'
      : accepted === 0
        ? `This dataset is already in the ledger. ${formatCount(existingRows)} existing records were safely skipped, and ${formatCount(duplicateRows)} duplicate source row was not counted twice. Spend was recalculated without double-counting.`
        : `The first import saved ${formatCount(accepted)} new normalized records. ${formatCount(duplicateRows)} duplicate source row was removed before persistence, and the budget forecast was recalculated from the saved ledger.`;
  }
  modal.classList.add('is-open');
  modal.setAttribute('aria-hidden', 'false');
  document.getElementById('ingestion-modal-done')?.focus();
}

function closeIngestionModal() {
  const modal = document.getElementById('ingestion-modal');
  if (!modal) return;
  modal.classList.remove('is-open');
  modal.setAttribute('aria-hidden', 'true');
  document.getElementById('btn-ingest')?.focus();
}

function activateView(view) {
  const allowed = ['overview', 'budget-alerts', 'workloads', 'data-quality', 'activity'];
  const activeView = allowed.includes(view) ? view : 'overview';
  const main = document.querySelector('.main-content');
  if (main) {
    main.dataset.view = activeView;
    main.scrollTop = 0;
  }
  const labels = {
    overview: 'Overview',
    'budget-alerts': 'Budget watch',
    workloads: 'Workloads',
    'data-quality': 'Data quality',
    activity: 'Run history'
  };
  setText('view-title', labels[activeView]);
  document.querySelectorAll('.nav-item').forEach(item => {
    const selected = (item.getAttribute('href') || '').slice(1) === activeView;
    item.classList.toggle('active', selected);
    if (selected) item.setAttribute('aria-current', 'page');
    else item.removeAttribute('aria-current');
  });
  if (window.location.hash !== `#${activeView}`) window.history.replaceState(null, '', `#${activeView}`);
}

async function openWebhookModal() {
  const modal = document.getElementById('webhook-modal');
  const code = document.getElementById('webhook-json');
  if (!modal || !code) return;
  code.textContent = 'Loading payload…';
  modal.classList.add('is-open');
  modal.setAttribute('aria-hidden', 'false');
  document.getElementById('modal-close')?.focus();
  try {
    const response = await fetch('/api/webhooks/preview', { headers: { Accept: 'application/json' } });
    const payload = await response.json();
    if (!response.ok) throw new Error(payload.message || `Request failed (${response.status})`);
    webhookData = payload;
    code.textContent = JSON.stringify(payload, null, 2);
  } catch (error) {
    webhookData = null;
    code.textContent = `Unable to load preview: ${error.message || 'Unknown error'}`;
  }
}

function closeWebhookModal() {
  const modal = document.getElementById('webhook-modal');
  if (!modal) return;
  modal.classList.remove('is-open');
  modal.setAttribute('aria-hidden', 'true');
  document.getElementById('btn-webhook-preview')?.focus();
}

async function copyWebhookJson() {
  if (!webhookData) {
    showToast('There is no payload to copy yet.', 'error');
    return;
  }
  try {
    await navigator.clipboard.writeText(JSON.stringify(webhookData, null, 2));
    showToast('Webhook payload copied.', 'success');
  } catch (error) {
    showToast('Clipboard access is unavailable in this browser context.', 'error');
  }
}

function formatEur(amount) {
  if (amount === null || amount === undefined || amount === '') return '€0.00';
  const number = Number(amount);
  if (!Number.isFinite(number)) return '€0.00';
  return new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(number);
}

function formatPercent(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number.toFixed(1).replace(/\.0$/, '') : '0';
}

function formatCount(value) {
  const number = Number(value);
  return Number.isFinite(number) ? new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(number) : '0';
}

function setText(id, value) {
  const element = document.getElementById(id);
  if (element) element.textContent = value;
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
}

function showToast(message, type = 'success') {
  const toast = document.getElementById('toast');
  if (!toast) return;
  toast.textContent = message;
  toast.classList.remove('is-error', 'is-success', 'show');
  toast.classList.add(type === 'error' ? 'is-error' : 'is-success', 'show');
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 4200);
}
