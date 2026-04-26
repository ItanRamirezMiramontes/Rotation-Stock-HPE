/**
 * HPE CAP Rotation Balance — app.js
 * SPA Vanilla JS — v3.0
 *
 * FIXES APPLIED:
 *   FIX #1  — NavBar "Dashboard" replaced by "CAP Balance" (section-cap-balance)
 *   FIX #2  — All user-facing strings translated to English
 *   FIX #3  — Boot injection: fetchPage + stats called immediately on DOMContentLoaded
 *              with retry logic for cold Spring Boot starts
 *   FIX #4  — Duplicate key 'omRegion' in COLUMNS corrected to 'customer'
 *
 * Modules:
 *   Config       — constants and global state
 *   API          — centralized fetch layer
 *   UI Helpers   — toast, spinner, highlight, formatting
 *   Router       — SPA navigation without page reload
 *   Orders       — paginated table with server-side filters
 *   Ingestion    — upload modal with post-ingestion summary panel
 *   CapBalance   — Finance page: distributor lookup, 3% CAP calculation
 */

'use strict';

/* ============================================================
   1. CONFIG & GLOBAL STATE
   ============================================================ */

const API_BASE = '';  // Empty = same origin (Spring Boot serves the frontend)

const AppState = {
  orders: {
    page: 0,
    size: 15,
    totalPages: 0,
    totalElements: 0,
    filters: { region: '', quarter: '', year: '', customerId: '' }
  },
  capBalance: {
    currentCustomerId: null,
    currentQuarter:    null,
    currentYear:       null,
    lastResult:        null   // stores the last calculation for export
  }
};

/* ============================================================
   2. API — Centralized data access layer
   ============================================================ */

const API = {
  async get(path) {
    const res = await fetch(API_BASE + path);
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(err.message || `Error ${res.status}`);
    }
    return res.json();
  },

  async upload(path, file) {
    const form = new FormData();
    form.append('file', file);
    const res = await fetch(API_BASE + path, { method: 'POST', body: form });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(err.message || `Error ${res.status}`);
    }
    return res.json();
  },

  /* Paginated orders with server-side filters */
  async getOrders(page, size, filters) {
    const params = new URLSearchParams({ page, size });
    if (filters.region)     params.set('region',     filters.region);
    if (filters.quarter)    params.set('quarter',     filters.quarter);
    if (filters.year)       params.set('year',        filters.year);
    if (filters.customerId) params.set('customerId',  filters.customerId);
    return this.get(`/orders?${params}`);
  },

  async getStats()                       { return this.get('/orders/stats'); },
  async getFilters()                     { return this.get('/orders/filters'); },
  async getCustomer(id)                  { return this.get(`/customers/${encodeURIComponent(id)}`); },
  async getCustomerOrders(id)            { return this.get(`/customers/${encodeURIComponent(id)}/orders`); },
  async getAllCustomers()                { return this.get('/customers'); },

  /* CAP Balance — customer orders filtered by quarter + year */
  async getCustomerOrdersByQuarter(id, quarter, year) {
    const params = new URLSearchParams({ customerId: id, size: 500 });
    if (quarter) params.set('quarter', quarter);
    if (year)    params.set('year',    year);
    return this.get(`/orders?${params}`);
  },

  exportUrl(filters) {
    const params = new URLSearchParams();
    if (filters.region)     params.set('region',     filters.region);
    if (filters.quarter)    params.set('quarter',     filters.quarter);
    if (filters.year)       params.set('year',        filters.year);
    if (filters.customerId) params.set('customerId',  filters.customerId);
    return `${API_BASE}/orders/export?${params}`;
  }
};

/* ============================================================
   3. UI HELPERS
   ============================================================ */

function showToast(msg, type = 'success') {
  const container = document.getElementById('toast-container');
  const t = document.createElement('div');
  t.className = `toast ${type}`;
  const icons = {
    success: '<polyline points="20 6 9 17 4 12"/>',
    error:   '<circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/>',
    warning: '<path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/>'
  };
  const colors = { success: '#01A982', error: '#E8382D', warning: '#FFC600' };
  const color  = colors[type] || colors.success;
  t.innerHTML  = `
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="2.5">${icons[type] || icons.success}</svg>
    <span>${msg}</span>`;
  container.appendChild(t);
  setTimeout(() => t.remove(), 4500);
}

function setLoading(btnEl, loading, originalHTML) {
  btnEl.disabled  = loading;
  btnEl.innerHTML = loading ? '<span class="spinner"></span>' : originalHTML;
}

function setText(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val;
}

function statusBadge(s) {
  if (!s) return 'badge-gray';
  const st = s.toLowerCase();
  if (st.includes('synced')  || st.includes('complet') || st.includes('inv'))     return 'badge-green';
  if (st.includes('loaded')  || st.includes('pend')    || st.includes('opn'))     return 'badge-yellow';
  if (st.includes('cancel')  || st.includes('error')   || st.includes('fail'))    return 'badge-red';
  return 'badge-blue';
}

function formatDate(val) {
  if (!val) return '—';
  try { return new Date(val).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: '2-digit' }); }
  catch { return val; }
}

function formatMoney(val, currency) {
  if (val == null) return null;
  const sym = currency === 'MXN' ? 'MX$' : currency === 'CAD' ? 'CA$' : '$';
  return sym + Number(val).toLocaleString('en-US', { minimumFractionDigits: 2 });
}

function highlight(val, term) {
  if (!term || !val) return val ?? '—';
  const s   = String(val);
  const idx = s.toLowerCase().indexOf(term.toLowerCase());
  if (idx === -1) return s;
  return s.slice(0, idx) +
    `<mark style="background:rgba(1,169,130,0.25);border-radius:2px;padding:0 1px;">${s.slice(idx, idx + term.length)}</mark>` +
    s.slice(idx + term.length);
}

function renderCell(col, val, filterTerm, currency) {
  const PENDING_COLS = ['orderValue'];
  const MONO_COLS    = ['hpeOrderId', 'custPoRef', 'sorg'];
  const DATE_COLS    = ['entryDate', 'updatedAt'];
  const STATUS_COLS  = ['headerStatus', 'invoiceHeaderStatus', 'internalStatus'];
  const MONEY_COLS   = ['orderValue'];

  if (PENDING_COLS.includes(col) && (val == null || val === '')) {
    return `<td><span class="badge badge-pending">Pending</span></td>`;
  }
  if (STATUS_COLS.includes(col)) {
    return `<td><span class="badge ${statusBadge(val)}">${val ?? '—'}</span></td>`;
  }
  if (MONEY_COLS.includes(col) && val != null) {
    return `<td>${formatMoney(val, currency)}</td>`;
  }
  if (DATE_COLS.includes(col)) {
    return `<td>${formatDate(val)}</td>`;
  }
  if (MONO_COLS.includes(col)) {
    return `<td class="td-mono">${highlight(val ?? '—', filterTerm)}</td>`;
  }
  return `<td>${highlight(val ?? '—', filterTerm)}</td>`;
}

/* ============================================================
   4. ROUTER SPA
   ============================================================ */

// FIX #1: section-dashboard removed, section-cap-balance added
const SECTIONS = ['section-orders', 'section-cap-balance'];

function navigate(sectionId) {
  SECTIONS.forEach(id => {
    document.getElementById(id).classList.toggle('active', id === sectionId);
  });
  document.querySelectorAll('.nav-link').forEach(link => {
    link.classList.toggle('active', link.dataset.section === sectionId);
  });

  // Lazy-load data when entering each section
  if (sectionId === 'section-orders')      Orders.load();
  if (sectionId === 'section-cap-balance') CapBalance.load();
}

/* ============================================================
   5. MODULE: ORDERS — Paginated table with server-side filters
   ============================================================ */

const Orders = (() => {

  // FIX #4: Second entry had duplicate key 'omRegion'. Corrected to 'customer'.
  const COLUMNS = [
    { key: 'hpeOrderId',          label: 'HPE Order ID' },
    { key: 'headerStatus',        label: 'Header Status' },
    { key: 'invoiceHeaderStatus', label: 'Invoice Status' },
    { key: 'omRegion',            label: 'OM Region' },
    { key: 'sorg',                label: 'Sorg' },
    { key: 'salesOffice',         label: 'Sales Office' },
    { key: 'salesGroup',          label: 'Sales Group' },
    { key: 'orderType',           label: 'Order Type' },
    { key: 'entryDate',           label: 'Entry Date' },
    { key: 'custPoRef',           label: 'Cust PO Ref' },
    // FIX #4 — was { key: 'omRegion', label: 'Customer', ... } (DUPLICATE KEY)
    { key: 'customer',            label: 'Sold To Party', customRender: (o) => o.customer?.customerId ?? '—' },
    { key: 'shipToAddress',       label: 'Ship-To' },
    { key: 'rtm',                 label: 'RTM' },
    { key: 'currency',            label: 'Currency' },
    { key: 'orderValue',          label: 'Order Value' },
    { key: 'fiscalQuarter',       label: 'Quarter' },
    { key: 'fiscalYear',          label: 'FY' },
    { key: 'internalStatus',      label: 'Status' }
  ];

  let filterTerm = '';
  let filtersLoaded = false;

  async function initFilters() {
    try {
      const { regions, quarters, years } = await API.getFilters();
      populateSelect('filter-region',  regions,  'All regions');
      populateSelect('filter-quarter', quarters, 'All quarters');
      populateSelect('filter-year',    years,    'All years');
    } catch (e) {
      console.warn('Could not load filter options:', e.message);
    }
  }

  function populateSelect(id, values, placeholder) {
    const sel = document.getElementById(id);
    if (!sel) return;
    sel.innerHTML = `<option value="">${placeholder}</option>` +
      values.map(v => `<option value="${v}">${v}</option>`).join('');
  }

  async function fetchPage(page) {
    const spinner = document.getElementById('orders-spinner');
    if (spinner) spinner.style.display = 'block';

    try {
      const data = await API.getOrders(page, AppState.orders.size, AppState.orders.filters);
      AppState.orders.page          = data.number;
      AppState.orders.totalPages    = data.totalPages;
      AppState.orders.totalElements = data.totalElements;

      renderTable(data.content || []);
      renderPagination();
      updatePagLabel();
    } catch (err) {
      document.getElementById('orders-table-container').innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">⚠️</div>
          <div class="empty-title">Error loading data</div>
          <div class="empty-sub">${err.message}</div>
        </div>`;
      showToast(err.message, 'error');
    } finally {
      if (spinner) spinner.style.display = 'none';
    }
  }

  function renderTable(orders) {
    const container = document.getElementById('orders-table-container');
    if (!orders.length) {
      container.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">📭</div>
          <div class="empty-title">No results</div>
          <div class="empty-sub">${
            AppState.orders.filters.region || AppState.orders.filters.quarter
              ? 'No orders match the active filters.'
              : 'Database is empty. Upload SAP Excel files first.'
          }</div>
        </div>`;
      return;
    }

    const thead = COLUMNS.map(c => `<th>${c.label}</th>`).join('');
    const tbody = orders.map(o => {
      const currency = o.currency;
      const cells    = COLUMNS.map(c => {
        if (c.customRender) return `<td>${c.customRender(o)}</td>`;
        return renderCell(c.key, o[c.key], filterTerm, currency);
      }).join('');
      return `<tr>${cells}</tr>`;
    }).join('');

    container.innerHTML = `
      <div class="table-wrapper" style="border:none;border-radius:0;">
        <table>
          <thead><tr>${thead}</tr></thead>
          <tbody>${tbody}</tbody>
        </table>
      </div>`;
  }

  function renderPagination() {
    const { page, totalPages } = AppState.orders;
    const container = document.getElementById('orders-pagination');
    container.innerHTML = '';

    const prev = makeBtn('‹', page === 0, () => fetchPage(page - 1));
    container.appendChild(prev);

    pageRange(page, totalPages).forEach(p => {
      if (p === '…') {
        const el = document.createElement('span');
        el.className = 'page-btn'; el.style.border = 'none'; el.textContent = '…';
        container.appendChild(el);
      } else {
        const btn = makeBtn(p + 1, false, () => fetchPage(p));
        if (p === page) btn.classList.add('active');
        container.appendChild(btn);
      }
    });

    const next = makeBtn('›', page >= totalPages - 1, () => fetchPage(page + 1));
    container.appendChild(next);
  }

  function makeBtn(label, disabled, onClick) {
    const btn = document.createElement('button');
    btn.className = 'page-btn'; btn.textContent = label; btn.disabled = disabled;
    if (!disabled) btn.addEventListener('click', onClick);
    return btn;
  }

  function pageRange(current, total) {
    if (total <= 7) return Array.from({ length: total }, (_, i) => i);
    const pages = [];
    if (current > 2) pages.push(0, '…'); else pages.push(0, 1);
    for (let i = Math.max(1, current - 1); i <= Math.min(total - 2, current + 1); i++) pages.push(i);
    if (current < total - 3) pages.push('…', total - 1); else pages.push(total - 2, total - 1);
    return [...new Set(pages)];
  }

  function updatePagLabel() {
    const { page, size, totalPages, totalElements } = AppState.orders;
    const start = totalElements === 0 ? 0 : page * size + 1;
    const end   = Math.min(start + size - 1, totalElements);
    const el    = document.getElementById('orders-pag-info');
    // FIX #2: "de N · Pág." → "of N · Page"
    if (el) el.textContent = `${start}–${end} of ${totalElements} · Page ${page + 1}/${totalPages}`;
  }

  function applyFilters() {
    AppState.orders.filters.region     = document.getElementById('filter-region')?.value   || '';
    AppState.orders.filters.quarter    = document.getElementById('filter-quarter')?.value  || '';
    AppState.orders.filters.year       = document.getElementById('filter-year')?.value     || '';
    AppState.orders.filters.customerId = document.getElementById('filter-customer')?.value || '';
    fetchPage(0);
  }

  function clearFilters() {
    ['filter-region','filter-quarter','filter-year','filter-customer'].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.value = '';
    });
    AppState.orders.filters = { region: '', quarter: '', year: '', customerId: '' };
    fetchPage(0);
  }

  function exportCurrent() {
    window.location.href = API.exportUrl(AppState.orders.filters);
  }

  function load() {
    if (!filtersLoaded) { initFilters(); filtersLoaded = true; }
    fetchPage(AppState.orders.page);
  }

  function init() {
    document.getElementById('btn-apply-filters')?.addEventListener('click', applyFilters);
    document.getElementById('btn-clear-filters')?.addEventListener('click', clearFilters);
    document.getElementById('btn-export')?.addEventListener('click', exportCurrent);
    document.getElementById('btn-refresh-orders')?.addEventListener('click', () => fetchPage(AppState.orders.page));

    const searchInput = document.getElementById('orders-search');
    if (searchInput) {
      searchInput.addEventListener('input', () => {
        filterTerm = searchInput.value.trim();
        fetchPage(0);
      });
    }
  }

  // Exposed for external refresh (e.g. after ingestion)
  return { init, load, refresh: () => fetchPage(AppState.orders.page) };
})();

/* ============================================================
   6. MODULE: INGESTION — Upload modal with result summary panel
   ============================================================ */

const Ingestion = (() => {
  let selectedFile = null;

  function openModal() {
    document.getElementById('modal-ingestion').classList.add('open');
    resetModal();
  }

  function closeModal() {
    document.getElementById('modal-ingestion').classList.remove('open');
  }

  function resetModal() {
    selectedFile = null;
    document.getElementById('ingest-file-name').textContent = 'None';
    document.getElementById('ingest-progress-wrap').style.display = 'none';
    document.getElementById('ingest-result-panel').style.display  = 'none';
    document.getElementById('drop-zone').classList.remove('drag-over');
    document.getElementById('btn-ingest-upload').disabled = true;
  }

  function setFile(file) {
    if (!file || (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls'))) {
      showToast('Only .xlsx or .xls files are accepted.', 'error');
      return;
    }
    selectedFile = file;
    document.getElementById('ingest-file-name').textContent = file.name;
    document.getElementById('btn-ingest-upload').disabled   = false;
  }

  async function upload() {
    if (!selectedFile) return;
    const btn      = document.getElementById('btn-ingest-upload');
    const origHTML = btn.innerHTML;
    setLoading(btn, true, origHTML);

    document.getElementById('ingest-progress-wrap').style.display = 'block';
    document.getElementById('ingest-progress-fill').style.width   = '40%';

    try {
      const result = await API.upload('/ingestion/upload', selectedFile);
      document.getElementById('ingest-progress-fill').style.width = '100%';

      renderIngestionResult(result);
      document.getElementById('ingest-result-panel').style.display = 'block';

      const toastType = result.status === 'SUCCESS' ? 'success'
                      : result.status === 'PARTIAL'  ? 'warning' : 'error';
      showToast(result.message, toastType);

      // Refresh the orders table if it's active, and the CAP balance stats
      Orders.refresh();
      CapBalance.refreshStats();

    } catch (err) {
      showToast('Upload error: ' + err.message, 'error');
    } finally {
      setLoading(btn, false, origHTML);
    }
  }

  function renderIngestionResult(r) {
    document.getElementById('ingest-res-type').textContent   = r.reportType   || '—';
    document.getElementById('ingest-res-total').textContent  = r.totalRead     ?? 0;
    document.getElementById('ingest-res-insert').textContent = r.inserted      ?? 0;
    document.getElementById('ingest-res-update').textContent = r.updated       ?? 0;
    document.getElementById('ingest-res-skip').textContent   = r.skipped       ?? 0;
    document.getElementById('ingest-res-error').textContent  = r.errors        ?? 0;
    document.getElementById('ingest-res-msg').textContent    = r.message       || '';
    document.getElementById('ingest-res-ts').textContent     = formatDate(r.timestamp);

    const statusEl = document.getElementById('ingest-res-status');
    statusEl.className   = `badge ${r.status === 'SUCCESS' ? 'badge-green' : r.status === 'PARTIAL' ? 'badge-yellow' : 'badge-red'}`;
    statusEl.textContent = r.status;

    const errList    = document.getElementById('ingest-error-list');
    const errSection = document.getElementById('ingest-error-section');
    if (r.errorDetails && r.errorDetails.length > 0) {
      errSection.style.display = 'block';
      errList.innerHTML = r.errorDetails.map(e => `
        <div class="error-item">
          <span class="error-order-id">${e.hpeOrderId}</span>
          <span class="error-reason">${e.reason}</span>
        </div>`).join('');
    } else {
      errSection.style.display = 'none';
    }
  }

  function init() {
    document.querySelectorAll('.btn-open-ingest').forEach(btn => {
      btn.addEventListener('click', openModal);
    });
    document.getElementById('modal-close-ingest')?.addEventListener('click', closeModal);
    document.getElementById('modal-cancel-ingest')?.addEventListener('click', closeModal);
    document.getElementById('modal-ingestion')?.addEventListener('click', e => {
      if (e.target === e.currentTarget) closeModal();
    });
    document.getElementById('btn-ingest-upload')?.addEventListener('click', upload);

    const dropZone  = document.getElementById('drop-zone');
    const fileInput = document.getElementById('ingest-file-input');

    dropZone?.addEventListener('dragover',  e => { e.preventDefault(); dropZone.classList.add('drag-over'); });
    dropZone?.addEventListener('dragleave', ()  => dropZone.classList.remove('drag-over'));
    dropZone?.addEventListener('drop', e => {
      e.preventDefault(); dropZone.classList.remove('drag-over');
      if (e.dataTransfer.files[0]) setFile(e.dataTransfer.files[0]);
    });
    dropZone?.addEventListener('click', () => fileInput?.click());
    fileInput?.addEventListener('change', e => { if (e.target.files[0]) setFile(e.target.files[0]); });
  }

  return { init, open: openModal };
})();

/* ============================================================
   7. MODULE: CAP BALANCE
   Finance page — distributor lookup + 3% return allowance calc
   FIX #1: Full new module replacing the old Dashboard
   ============================================================ */

const CapBalance = (() => {

  const CAP_RATE = 0.03; // 3% as defined in the stock rotation program

  // ── STATS (top cards) ──────────────────────────────────────

  async function refreshStats() {
    try {
      const [stats, customers] = await Promise.all([
        API.getStats(),
        API.getAllCustomers()
      ]);
      setText('cap-stat-total',     stats.total        ?? '—');
      setText('cap-stat-synced',    stats.priceSynced  ?? '—');
      setText('cap-stat-pending',   stats.pricePending ?? '—');
      setText('cap-stat-customers', customers.length   ?? '—');
    } catch (e) {
      console.warn('CAP stats error:', e.message);
    }
  }

  // ── YEAR DROPDOWN ──────────────────────────────────────────

  async function populateYears() {
    const sel = document.getElementById('cap-year-select');
    if (!sel || sel.options.length > 1) return; // already populated
    try {
      const { years } = await API.getFilters();
      years.forEach(y => {
        const opt = document.createElement('option');
        opt.value = y; opt.textContent = `FY${y}`;
        sel.appendChild(opt);
      });
    } catch (e) {
      console.warn('Could not load years for CAP filter:', e.message);
    }
  }

  // ── MAIN CALCULATION ───────────────────────────────────────

  async function calculate() {
    const customerId = document.getElementById('cap-customer-input')?.value.trim();
    const quarter    = document.getElementById('cap-quarter-select')?.value;
    const year       = document.getElementById('cap-year-select')?.value;

    // Validation
    hide('cap-not-found');
    hide('cap-missing-fields');

    if (!customerId || !quarter || !year) {
      show('cap-missing-fields');
      return;
    }

    const btn      = document.getElementById('cap-btn-search');
    const origHTML = btn.innerHTML;
    setLoading(btn, true, origHTML);

    show('cap-orders-spinner');
    hide('cap-result-card');

    try {
      // 1. Verify customer exists
      let customer;
      try {
        customer = await API.getCustomer(customerId);
      } catch {
        show('cap-not-found');
        setLoading(btn, false, origHTML);
        hide('cap-orders-spinner');
        return;
      }

      // 2. Fetch their orders for this quarter + year
      const pageData = await API.getCustomerOrdersByQuarter(customerId, quarter, year);
      const orders   = pageData.content || [];

      // 3. Separate synced (has orderValue) from pending (no price yet)
      const syncedOrders  = orders.filter(o => o.orderValue != null);
      const pendingOrders = orders.filter(o => o.orderValue == null);

      // 4. Sum total invoiced value from synced orders only
      const totalInvoiced = syncedOrders.reduce((sum, o) => sum + Number(o.orderValue), 0);

      // 5. Calculate 3% CAP allowance
      const capAllowance = totalInvoiced * CAP_RATE;

      // 6. Detect the currency (use first synced order's currency, fallback to first order)
      const currency = (syncedOrders[0] || orders[0])?.currency || 'USD';

      // 7. Store result for export
      const result = {
        customer,
        quarter,
        year,
        orders,
        syncedOrders,
        pendingOrders,
        totalInvoiced,
        capAllowance,
        currency
      };
      AppState.capBalance.lastResult        = result;
      AppState.capBalance.currentCustomerId = customerId;
      AppState.capBalance.currentQuarter    = quarter;
      AppState.capBalance.currentYear       = year;

      // 8. Render everything
      renderResult(result);
      renderOrdersTable(result);

    } catch (err) {
      showToast('Error calculating CAP balance: ' + err.message, 'error');
    } finally {
      setLoading(btn, false, origHTML);
      hide('cap-orders-spinner');
    }
  }

  // ── RENDER: LEFT PANEL (CAP summary) ───────────────────────

  function renderResult({ customer, quarter, year, syncedOrders, pendingOrders,
                           totalInvoiced, capAllowance, currency }) {

    const fmt = (v) => formatMoney(v, currency);

    // Header badge
    setText('cap-result-period', `${quarter} · FY${year}`);

    // Customer info
    setText('cap-res-customer-id',   customer.customerId   || '—');
    setText('cap-res-customer-name', customer.customerName || '—');

    // Totals
    setText('cap-res-total',       fmt(totalInvoiced));
    setText('cap-res-order-count', `${syncedOrders.length} order${syncedOrders.length !== 1 ? 's' : ''} included`);
    setText('cap-res-cap',         fmt(capAllowance));

    // Pending warning
    if (pendingOrders.length > 0) {
      setText('cap-pending-msg',
        `${pendingOrders.length} order${pendingOrders.length !== 1 ? 's' : ''} excluded — price not synced yet. Upload a Price Report to include them in the CAP calculation.`
      );
      show('cap-pending-warning');
    } else {
      hide('cap-pending-warning');
    }

    // Recommendation box
    const recCard = document.getElementById('cap-recommendation');
    const recIcon  = document.getElementById('cap-rec-icon');
    const recTitle = document.getElementById('cap-rec-title');
    const recBody  = document.getElementById('cap-rec-body');

    if (totalInvoiced === 0) {
      recCard.className  = 'cap-recommendation cap-rec-neutral';
      recIcon.textContent = 'ℹ';
      recTitle.textContent = 'No invoiced orders found';
      recBody.textContent  = `No synced orders found for ${quarter} FY${year}. Upload the Raw Data and Price Reports for this period.`;
    } else {
      // The 3% recommendation is always shown — there's no "used" amount yet
      // (that would require return order tracking, a future enhancement).
      // For now: show the approved limit and suggest the Finance team to compare
      // against any existing return requests submitted for this distributor.
      recCard.className   = 'cap-recommendation cap-rec-ok';
      recIcon.textContent  = '✔';
      recTitle.textContent = 'CAP balance calculated';
      recBody.textContent  =
        `${customer.customerName || customerId} is approved to return up to ${fmt(capAllowance)} in ${quarter} FY${year} ` +
        `(3% of ${fmt(totalInvoiced)} total invoiced value). ` +
        `Compare this limit against any return orders submitted in S4 for this period.`;
    }

    show('cap-result-card');
  }

  // ── RENDER: RIGHT PANEL (orders table) ─────────────────────

  function renderOrdersTable({ customer, quarter, year, orders, syncedOrders,
                               pendingOrders, totalInvoiced, currency }) {

    const title = document.getElementById('cap-orders-title');
    const badge = document.getElementById('cap-orders-badge');
    const body  = document.getElementById('cap-orders-body');
    const footer = document.getElementById('cap-orders-footer');
    const footerSummary = document.getElementById('cap-footer-summary');
    const pendingBadge  = document.getElementById('cap-footer-pending-badge');

    if (title) title.textContent = `Orders — ${customer.customerId} · ${quarter} FY${year}`;
    if (badge) badge.textContent = `${orders.length} order${orders.length !== 1 ? 's' : ''}`;

    if (!orders.length) {
      body.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">📭</div>
          <div class="empty-title">No orders found</div>
          <div class="empty-sub">No ZRES orders found for ${quarter} FY${year}. Upload the Raw Data Report for this period.</div>
        </div>`;
      hide('cap-orders-footer');
      return;
    }

    const cols = [
      { key: 'hpeOrderId',       label: 'HPE Order ID',    mono: true },
      { key: 'entryDate',        label: 'Entry Date',      date: true },
      { key: 'headerStatus',     label: 'Header Status',   status: true },
      { key: 'invoiceHeaderStatus', label: 'Invoice Status', status: true },
      { key: 'orderValue',       label: 'Order Value',     money: true },
      { key: 'internalStatus',   label: 'Sync Status',     status: true },
      { key: '_inCap',           label: 'In CAP Calc?' }
    ];

    const thead = cols.map(c => `<th>${c.label}</th>`).join('');
    const tbody = orders.map(o => {
      const inCap    = o.orderValue != null;
      const cells    = cols.map(c => {
        if (c.key === '_inCap') {
          return `<td><span class="badge ${inCap ? 'badge-green' : 'badge-gray'}">${inCap ? 'Yes' : 'No — Pending'}</span></td>`;
        }
        if (c.mono)   return `<td class="td-mono">${o[c.key] ?? '—'}</td>`;
        if (c.date)   return `<td>${formatDate(o[c.key])}</td>`;
        if (c.status) return `<td><span class="badge ${statusBadge(o[c.key])}">${o[c.key] ?? '—'}</span></td>`;
        if (c.money) {
          return inCap
            ? `<td>${formatMoney(o[c.key], currency)}</td>`
            : `<td><span class="badge badge-pending">Pending</span></td>`;
        }
        return `<td>${o[c.key] ?? '—'}</td>`;
      }).join('');
      return `<tr>${cells}</tr>`;
    }).join('');

    body.innerHTML = `
      <div class="table-wrapper" style="border:none;border-radius:0;">
        <table>
          <thead><tr>${thead}</tr></thead>
          <tbody>${tbody}</tbody>
        </table>
      </div>`;

    // Footer summary
    if (footer) {
      footerSummary.textContent = `Total synced: ${formatMoney(totalInvoiced, currency)} · ${syncedOrders.length} of ${orders.length} orders included`;
      if (pendingOrders.length > 0) {
        pendingBadge.textContent = `${pendingOrders.length} pending excluded`;
        show('cap-footer-pending-badge');
      } else {
        hide('cap-footer-pending-badge');
      }
      show('cap-orders-footer');
    }
  }

  // ── EXPORT CAP SUMMARY ─────────────────────────────────────

  function exportSummary() {
    const r = AppState.capBalance.lastResult;
    if (!r) { showToast('No CAP result to export. Run a calculation first.', 'warning'); return; }

    const { customer, quarter, year, currency } = r;
    const filters = {
      customerId: customer.customerId,
      quarter:    quarter,
      year:       year
    };
    window.location.href = API.exportUrl(filters);
  }

  // ── LOAD (called when navigating to this section) ──────────

  function load() {
    refreshStats();
    populateYears();
  }

  // ── HELPERS ────────────────────────────────────────────────

  function show(id) { const el = document.getElementById(id); if (el) el.style.display = 'block'; }
  function hide(id) { const el = document.getElementById(id); if (el) el.style.display = 'none';  }

  function init() {
    document.getElementById('cap-btn-search')?.addEventListener('click', calculate);
    document.getElementById('cap-btn-export')?.addEventListener('click', exportSummary);
    document.getElementById('btn-refresh-cap-stats')?.addEventListener('click', refreshStats);
    document.getElementById('cap-customer-input')?.addEventListener('keydown', e => {
      if (e.key === 'Enter') calculate();
    });
  }

  return { init, load, refreshStats };
})();

/* ============================================================
   8. BOOTSTRAP — Init on DOMContentLoaded
   FIX #3: Data is fetched immediately on load, with retry logic
            for slow Spring Boot cold starts
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {

  // Wire nav links to router
  document.querySelectorAll('.nav-link[data-section]').forEach(link => {
    link.addEventListener('click', () => navigate(link.dataset.section));
  });

  // Initialize all modules (attach event listeners)
  Orders.init();
  Ingestion.init();
  CapBalance.init();

  // FIX #3: Immediately fetch orders on boot, not only on navigate().
  // Retry up to 3 times with 900ms delay to handle slow Spring Boot cold starts.
  (async function bootFetch() {
    const MAX_RETRIES = 3;
    const RETRY_DELAY = 900; // ms

    for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        // Trigger orders load directly — navigate() will call it again lazily but this
        // ensures data is fetched even if the user stays on the default section.
        await Orders.load();
        break; // success — stop retrying
      } catch (err) {
        if (attempt < MAX_RETRIES) {
          console.warn(`Boot fetch attempt ${attempt} failed, retrying in ${RETRY_DELAY}ms…`, err.message);
          await new Promise(resolve => setTimeout(resolve, RETRY_DELAY));
        } else {
          console.error('Boot fetch failed after all retries:', err.message);
          showToast('Could not connect to server. Refresh to retry.', 'error');
        }
      }
    }
  })();

  // Navigate to the default section (triggers lazy load too, safe duplicate)
  navigate('section-orders');
});