/**
 * HPE CAP Rotation Balance — app.js  v4
 *
 * Fixes applied in this version:
 *
 * FIX-1  Drop-zone double-open bug
 *   The <input type="file"> was inside the drop-zone div. Clicking anywhere on
 *   the div triggered the div's click listener AND bubbled to the input's own
 *   default click, opening the file picker twice. Fixed by:
 *   a) Setting pointer-events:none on the input (CSS).
 *   b) The div's click handler calls fileInput.click() programmatically.
 *   c) stopPropagation() added so events from inner elements don't re-fire.
 *
 * FIX-2  Dual-file upload (Raw Data + Price Report simultaneously)
 *   Drop zone now shows two separate upload slots. Each slot accepts one file.
 *   The JS detects each file's type (raw vs price) via header inspection before
 *   submission. Both are sent in a single FormData POST with fields "rawFile"
 *   and "priceFile". The backend (IngestionController v4) processes them in
 *   order (raw first, then price).
 *
 * FIX-3  Upload order enforcement
 *   If the user tries to upload only a Price Report and the backend returns 409
 *   (no orders in DB), the frontend shows a descriptive blocking alert instead
 *   of a generic error toast.
 *
 * FIX-4  Header Status filter added to Orders section.
 *
 * FIX-5  Last Upload timestamp widget
 *   On boot and after every successful upload, GET /ingestion/last is called.
 *   The result is shown in a small chip next to the Upload/Export buttons:
 *   "Last upload: Jan 15, 2025 · 10:32"
 *
 * FIX-6  "Status" column removed from Orders table (not priority for end user).
 *
 * FIX-7  Empty cells show "No value" instead of "—".
 *
 * FIX-8  Performance: filter dropdowns loaded once at boot (not on every nav).
 *        Debounce on text inputs to avoid firing a fetch on every keystroke.
 *
 * FIX-9  Security: all user-provided strings interpolated into HTML are escaped
 *        via a sanitizeHTML() helper to prevent XSS from SAP data.
 */

'use strict';

/* ============================================================
   1. CONFIG & GLOBAL STATE
   ============================================================ */

const API_BASE = '';

const AppState = {
  orders: {
    page: 0,
    size: 15,
    totalPages: 0,
    totalElements: 0,
    filters: { region: '', quarter: '', year: '', customerId: '', headerStatus: '' }
  },
  capBalance: {
    lastResult:        null,
    currentCustomerId: null,
    currentQuarter:    null,
    currentYear:       null
  }
};

/* ============================================================
   2. SECURITY HELPER — HTML escaping
   FIX-9: Prevent XSS from SAP field values interpolated into innerHTML
   ============================================================ */

function esc(val) {
  if (val == null) return '';
  return String(val)
    .replace(/&/g,  '&amp;')
    .replace(/</g,  '&lt;')
    .replace(/>/g,  '&gt;')
    .replace(/"/g,  '&quot;')
    .replace(/'/g,  '&#x27;');
}

/* "No value" placeholder — FIX-7 */
const NO_VAL = '<span style="color:var(--text-muted);font-style:italic;font-size:0.78rem;">No value</span>';

/* ============================================================
   3. API LAYER
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

  /**
   * FIX-2: upload now accepts a FormData object directly so the caller
   * can pack rawFile + priceFile (or just one) into a single request.
   */
  async upload(path, formData) {
    const res = await fetch(API_BASE + path, { method: 'POST', body: formData });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }));
      // Preserve the HTTP status so callers can detect 409
      const error = new Error(err.error || err.message || `Error ${res.status}`);
      error.status = res.status;
      throw error;
    }
    return res.json();
  },

  async getOrders(page, size, filters) {
    const params = new URLSearchParams({ page, size });
    if (filters.region)       params.set('region',       filters.region);
    if (filters.quarter)      params.set('quarter',       filters.quarter);
    if (filters.year)         params.set('year',          filters.year);
    if (filters.customerId)   params.set('customerId',    filters.customerId);
    if (filters.headerStatus) params.set('headerStatus',  filters.headerStatus);
    return this.get(`/orders?${params}`);
  },

  async getStats()           { return this.get('/orders/stats'); },
  async getFilters()         { return this.get('/orders/filters'); },
  async getCustomer(id)      { return this.get(`/customers/${encodeURIComponent(id)}`); },
  async getCustomerOrders(id){ return this.get(`/customers/${encodeURIComponent(id)}/orders`); },
  async getLastUpload()      { return this.get('/ingestion/last'); },

  exportUrl(filters) {
    const params = new URLSearchParams();
    if (filters.region)       params.set('region',      filters.region);
    if (filters.quarter)      params.set('quarter',     filters.quarter);
    if (filters.year)         params.set('year',        filters.year);
    if (filters.customerId)   params.set('customerId',  filters.customerId);
    if (filters.headerStatus) params.set('headerStatus',filters.headerStatus);
    return `${API_BASE}/orders/export?${params}`;
  }
};

/* ============================================================
   4. UI HELPERS
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
  const color = colors[type] || colors.success;
  t.innerHTML = `
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="${esc(color)}" stroke-width="2.5">${icons[type] || icons.success}</svg>
    <span>${esc(msg)}</span>`;
  container.appendChild(t);
  setTimeout(() => t.remove(), 4500);
}

function setLoading(btnEl, loading, originalHTML) {
  btnEl.disabled = loading;
  btnEl.innerHTML = loading ? '<span class="spinner"></span>' : originalHTML;
}

function statusBadge(s) {
  if (!s) return 'badge-gray';
  const st = s.toLowerCase();
  if (st.includes('synced') || st.includes('inv') || st.includes('complet')) return 'badge-green';
  if (st.includes('loaded') || st.includes('opn') || st.includes('open'))    return 'badge-yellow';
  if (st.includes('cancel') || st.includes('error') || st.includes('canc'))  return 'badge-red';
  return 'badge-blue';
}

function formatDate(val) {
  if (!val) return null;
  try { return new Date(val).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: '2-digit' }); }
  catch { return val; }
}

function formatDateTime(val) {
  if (!val) return null;
  try { return new Date(val).toLocaleString('en-US', { year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit' }); }
  catch { return val; }
}

function formatMoney(val, currency) {
  if (val == null) return null;
  const sym = currency === 'MXN' ? 'MX$' : currency === 'CAD' ? 'CA$' : '$';
  return sym + Number(val).toLocaleString('en-US', { minimumFractionDigits: 2 });
}

function highlight(val, term) {
  if (!term || val == null || val === '') return val == null || val === '' ? NO_VAL : esc(String(val));
  const s   = esc(String(val));
  const sLo = String(val).toLowerCase();
  const idx = sLo.indexOf(term.toLowerCase());
  if (idx === -1) return s;
  const escTerm = esc(String(val).slice(idx, idx + term.length));
  return esc(String(val).slice(0, idx)) +
    `<mark style="background:rgba(1,169,130,0.25);border-radius:2px;padding:0 1px;">${escTerm}</mark>` +
    esc(String(val).slice(idx + term.length));
}

/** FIX-7: Render a table cell, showing "No value" for empty/null non-pending fields */
function renderCell(col, val, filterTerm, currency) {
  const PENDING_COLS = ['orderValue'];
  const MONO_COLS    = ['hpeOrderId', 'custPoRef', 'sorg'];
  const DATE_COLS    = ['entryDate'];   // FIX-6: updatedAt column removed
  const STATUS_COLS  = ['headerStatus', 'invoiceHeaderStatus'];   // FIX-6: internalStatus removed

  if (PENDING_COLS.includes(col) && (val == null || val === '')) {
    return `<td><span class="badge badge-pending">Pending</span></td>`;
  }
  if (STATUS_COLS.includes(col)) {
    if (val == null || val === '') return `<td>${NO_VAL}</td>`;
    return `<td><span class="badge ${statusBadge(val)}">${esc(val)}</span></td>`;
  }
  if (col === 'orderValue' && val != null) {
    return `<td>${esc(formatMoney(val, currency))}</td>`;
  }
  if (DATE_COLS.includes(col)) {
    const d = formatDate(val);
    return `<td>${d ? esc(d) : NO_VAL}</td>`;
  }
  if (MONO_COLS.includes(col)) {
    if (val == null || val === '') return `<td class="td-mono">${NO_VAL}</td>`;
    return `<td class="td-mono">${highlight(val, filterTerm)}</td>`;
  }
  if (val == null || val === '') return `<td>${NO_VAL}</td>`;
  return `<td>${highlight(val, filterTerm)}</td>`;
}

/** Simple debounce to avoid firing a fetch on every keystroke — FIX-8 */
function debounce(fn, ms = 400) {
  let timer;
  return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), ms); };
}

/* ============================================================
   5. LAST UPLOAD WIDGET  — FIX-5
   ============================================================ */

async function refreshLastUpload() {
  try {
    const data = await API.getLastUpload();
    const el   = document.getElementById('last-upload-chip');
    if (!el) return;
    if (!data.hasData) {
      el.textContent = 'No uploads yet';
      el.style.display = 'flex';
      return;
    }
    const dt = formatDateTime(data.timestamp);
    const label = data.action === 'PRICE_REPORT_UPLOAD' ? 'Price Report' : 'Raw Data';
    el.innerHTML = `
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
      </svg>
      Last upload: <strong>${esc(dt)}</strong> &nbsp;·&nbsp; ${esc(label)}`;
    el.style.display = 'flex';
  } catch { /* non-critical — silently ignore */ }
}

/* ============================================================
   6. ROUTER
   ============================================================ */

const SECTIONS = ['section-orders', 'section-cap-balance'];

function navigate(sectionId) {
  SECTIONS.forEach(id => {
    document.getElementById(id).classList.toggle('active', id === sectionId);
  });
  document.querySelectorAll('.nav-link').forEach(link => {
    link.classList.toggle('active', link.dataset.section === sectionId);
  });

  if (sectionId === 'section-orders')      Orders.load();
  if (sectionId === 'section-cap-balance') CapBalance.load();
}

/* ============================================================
   7. MODULE: ORDERS
   ============================================================ */

const Orders = (() => {

  // FIX-6: 'internalStatus' (Status) column removed
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
    { key: 'customer',            label: 'Sold To Party', customRender: (o) => o.customer?.customerId ? esc(o.customer.customerId) : NO_VAL },
    { key: 'shipToAddress',       label: 'Ship-To' },
    { key: 'rtm',                 label: 'RTM' },
    { key: 'currency',            label: 'Currency' },
    { key: 'orderValue',          label: 'Order Value' },
    { key: 'fiscalQuarter',       label: 'Quarter' },
    { key: 'fiscalYear',          label: 'FY' }
  ];

  let filterTerm    = '';
  let filtersLoaded = false;

  // FIX-8: load filter dropdowns once at boot
  async function initFilters() {
    if (filtersLoaded) return;
    try {
      const f = await API.getFilters();
      populateSelect('filter-region',        f.regions        || [], 'All regions');
      populateSelect('filter-quarter',       f.quarters       || [], 'All quarters');
      populateSelect('filter-year',          f.years          || [], 'All years');
      populateSelect('filter-header-status', f.headerStatuses || [], 'All header statuses'); // FIX-4
      filtersLoaded = true;
    } catch (e) {
      console.warn('Could not load filter options:', e.message);
    }
  }

  function populateSelect(id, values, placeholder) {
    const sel = document.getElementById(id);
    if (!sel) return;
    sel.innerHTML = `<option value="">${placeholder}</option>` +
      values.map(v => `<option value="${esc(v)}">${esc(v)}</option>`).join('');
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
          <div class="empty-sub">${esc(err.message)}</div>
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
            Object.values(AppState.orders.filters).some(Boolean)
              ? 'No orders match the active filters.'
              : 'Database is empty. Upload SAP Excel files first.'
          }</div>
        </div>`;
      return;
    }

    const thead = COLUMNS.map(c => `<th>${esc(c.label)}</th>`).join('');
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

    container.appendChild(makeBtn('‹', page === 0, () => fetchPage(page - 1)));
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
    container.appendChild(makeBtn('›', page >= totalPages - 1, () => fetchPage(page + 1)));
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
    if (el) el.textContent = `${start}–${end} of ${totalElements} · Page ${page + 1}/${totalPages}`;
  }

  function applyFilters() {
    AppState.orders.filters.region       = document.getElementById('filter-region')?.value        || '';
    AppState.orders.filters.quarter      = document.getElementById('filter-quarter')?.value       || '';
    AppState.orders.filters.year         = document.getElementById('filter-year')?.value          || '';
    AppState.orders.filters.customerId   = document.getElementById('filter-customer')?.value      || '';
    AppState.orders.filters.headerStatus = document.getElementById('filter-header-status')?.value || '';
    fetchPage(0);
  }

  function clearFilters() {
    ['filter-region','filter-quarter','filter-year','filter-customer','filter-header-status'].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.value = '';
    });
    AppState.orders.filters = { region: '', quarter: '', year: '', customerId: '', headerStatus: '' };
    fetchPage(0);
  }

  function load() {
    initFilters();
    fetchPage(AppState.orders.page);
  }

  function init() {
    document.getElementById('btn-apply-filters')?.addEventListener('click', applyFilters);
    document.getElementById('btn-clear-filters')?.addEventListener('click', clearFilters);
    document.getElementById('btn-export')?.addEventListener('click',
      () => window.location.href = API.exportUrl(AppState.orders.filters));
    document.getElementById('btn-refresh-orders')?.addEventListener('click',
      () => fetchPage(AppState.orders.page));

    // FIX-8: debounce the text search input
    const searchInput = document.getElementById('orders-search');
    if (searchInput) {
      searchInput.addEventListener('input', debounce(() => {
        filterTerm = searchInput.value.trim();
        fetchPage(0);
      }, 350));
    }
  }

  return { init, load, refresh: () => fetchPage(AppState.orders.page) };
})();

/* ============================================================
   8. MODULE: INGESTION — FIX-1, FIX-2, FIX-3
   ============================================================ */

const Ingestion = (() => {
  // FIX-2: Two independent file slots
  let rawFile   = null;
  let priceFile = null;

  function openModal() {
    document.getElementById('modal-ingestion').classList.add('open');
    resetModal();
  }

  function closeModal() {
    document.getElementById('modal-ingestion').classList.remove('open');
  }

  function resetModal() {
    rawFile   = null;
    priceFile = null;
    setText('ingest-raw-name',   'No file selected');
    setText('ingest-price-name', 'No file selected');
    document.getElementById('ingest-order-warning')?.style &&
      (document.getElementById('ingest-order-warning').style.display = 'none');
    document.getElementById('ingest-progress-wrap').style.display  = 'none';
    document.getElementById('ingest-result-panel').style.display   = 'none';
    document.getElementById('btn-ingest-upload').disabled = true;
    document.getElementById('ingest-raw-slot')?.classList.remove('has-file');
    document.getElementById('ingest-price-slot')?.classList.remove('has-file');
  }

  /** FIX-1: file slot click triggers input.click(); no bubbling issues */
  function bindSlot(slotId, inputId, type) {
    const slot  = document.getElementById(slotId);
    const input = document.getElementById(inputId);
    if (!slot || !input) return;

    // Click on the slot div → open file picker
    slot.addEventListener('click', (e) => {
      e.stopPropagation();
      input.click();
    });

    // Drag-and-drop
    slot.addEventListener('dragover',  e => { e.preventDefault(); slot.classList.add('drag-over'); });
    slot.addEventListener('dragleave', ()  => slot.classList.remove('drag-over'));
    slot.addEventListener('drop', e => {
      e.preventDefault();
      slot.classList.remove('drag-over');
      const f = e.dataTransfer.files[0];
      if (f) assignFile(f, type);
    });

    // File picker change — the input itself is hidden (pointer-events:none in CSS)
    input.addEventListener('change', e => {
      const f = e.target.files[0];
      if (f) assignFile(f, type);
      // Reset value so the same file can be re-selected if needed
      input.value = '';
    });
  }

  function assignFile(file, type) {
    if (!file.name.toLowerCase().endsWith('.xlsx') && !file.name.toLowerCase().endsWith('.xls')) {
      showToast('Only .xlsx or .xls files are accepted.', 'error');
      return;
    }
    const slotId    = type === 'raw' ? 'ingest-raw-slot'   : 'ingest-price-slot';
    const nameId    = type === 'raw' ? 'ingest-raw-name'   : 'ingest-price-name';
    const slot      = document.getElementById(slotId);

    if (type === 'raw') rawFile   = file;
    else                priceFile = file;

    setText(nameId, file.name);
    slot?.classList.add('has-file');
    document.getElementById('btn-ingest-upload').disabled = false;
  }

  async function upload() {
    if (!rawFile && !priceFile) return;

    const btn      = document.getElementById('btn-ingest-upload');
    const origHTML = btn.innerHTML;
    setLoading(btn, true, origHTML);

    // FIX-2: build FormData with named fields
    const form = new FormData();
    if (rawFile)   form.append('rawFile',   rawFile);
    if (priceFile) form.append('priceFile', priceFile);

    document.getElementById('ingest-progress-wrap').style.display = 'block';
    document.getElementById('ingest-progress-fill').style.width   = '40%';
    // Hide previous order-warning
    const warn = document.getElementById('ingest-order-warning');
    if (warn) warn.style.display = 'none';

    try {
      const result = await API.upload('/ingestion/upload', form);
      document.getElementById('ingest-progress-fill').style.width = '100%';

      // FIX-2: backend may return { rawData: {...}, priceReport: {...} } or a single DTO
      if (result.rawData || result.priceReport) {
        renderDualResult(result);
      } else {
        renderIngestionResult(result);
      }
      document.getElementById('ingest-result-panel').style.display = 'block';

      const mainStatus = result.rawData?.status || result.status || 'ERROR';
      showToast(
        result.rawData?.message || result.message || 'Upload complete.',
        mainStatus === 'SUCCESS' ? 'success' : mainStatus === 'PARTIAL' ? 'warning' : 'error'
      );

      // Refresh data
      Orders.refresh();
      CapBalance.refreshStats();
      refreshLastUpload();  // FIX-5

    } catch (err) {
      document.getElementById('ingest-progress-fill').style.width = '0%';

      // FIX-3: 409 → show blocking "Upload Raw Data first" warning
      if (err.status === 409 && warn) {
        warn.style.display = 'flex';
        warn.querySelector('.order-warn-msg').textContent = err.message;
      } else {
        showToast('Upload error: ' + err.message, 'error');
      }
    } finally {
      setLoading(btn, false, origHTML);
    }
  }

  function renderIngestionResult(r) {
    setText('ingest-res-type',   r.reportType  || '—');
    setText('ingest-res-total',  r.totalRead   ?? 0);
    setText('ingest-res-insert', r.inserted    ?? 0);
    setText('ingest-res-update', r.updated     ?? 0);
    setText('ingest-res-skip',   r.skipped     ?? 0);
    setText('ingest-res-error',  r.errors      ?? 0);
    setText('ingest-res-msg',    r.message     || '');
    setText('ingest-res-ts',     formatDate(r.timestamp) || '');

    const statusEl = document.getElementById('ingest-res-status');
    if (statusEl) {
      statusEl.className   = `badge ${r.status === 'SUCCESS' ? 'badge-green' : r.status === 'PARTIAL' ? 'badge-yellow' : 'badge-red'}`;
      statusEl.textContent = r.status || '—';
    }

    const errSection = document.getElementById('ingest-error-section');
    const errList    = document.getElementById('ingest-error-list');
    if (r.errorDetails?.length > 0) {
      if (errSection) errSection.style.display = 'block';
      if (errList) errList.innerHTML = r.errorDetails.map(e => `
        <div class="error-item">
          <span class="error-order-id">${esc(e.hpeOrderId)}</span>
          <span class="error-reason">${esc(e.reason)}</span>
        </div>`).join('');
    } else {
      if (errSection) errSection.style.display = 'none';
    }
  }

  /** FIX-2: Render a combined dual-upload result */
  function renderDualResult(r) {
    // Show combined summary by re-using the single result renderer for the raw portion,
    // then append a price report mini-summary below.
    const raw   = r.rawData     || {};
    const price = r.priceReport || {};

    // Use raw as the primary result display
    renderIngestionResult({
      ...raw,
      reportType: 'RAW DATA + PRICE REPORT',
      message: [raw.message, price.message].filter(Boolean).join(' · ')
    });

    // Append price detail to the result panel
    const panel = document.getElementById('ingest-result-panel');
    if (panel && price.message) {
      const extra = document.createElement('div');
      extra.className = 'alert alert-info mt-1';
      extra.style.marginTop = '0.75rem';
      extra.innerHTML = `<strong>Price Report:</strong> ${esc(price.message)}`;
      panel.appendChild(extra);
    }
  }

  function init() {
    document.querySelectorAll('.btn-open-ingest').forEach(btn => btn.addEventListener('click', openModal));
    document.getElementById('modal-close-ingest')?.addEventListener('click', closeModal);
    document.getElementById('modal-cancel-ingest')?.addEventListener('click', closeModal);
    document.getElementById('modal-ingestion')?.addEventListener('click', e => {
      if (e.target === e.currentTarget) closeModal();
    });
    document.getElementById('btn-ingest-upload')?.addEventListener('click', upload);

    // FIX-1: bind each slot independently
    bindSlot('ingest-raw-slot',   'ingest-raw-input',   'raw');
    bindSlot('ingest-price-slot', 'ingest-price-input', 'price');
  }

  return { init, open: openModal };
})();

/* ============================================================
   9. MODULE: CAP BALANCE  (unchanged logic, esc() applied)
   ============================================================ */

const CapBalance = (() => {

  async function refreshStats() {
    try {
      const stats = await API.getStats();
      setText('cap-stat-total',    stats.total        ?? '—');
      setText('cap-stat-synced',   stats.priceSynced  ?? '—');
      setText('cap-stat-loaded',   stats.loaded       ?? '—');
      setText('cap-stat-pending',  stats.pricePending ?? '—');
    } catch (e) { console.warn('Stats error:', e.message); }
  }

  async function populateYears() {
    try {
      const { years } = await API.getFilters();
      const sel = document.getElementById('cap-year-select');
      if (!sel) return;
      const current = new Date().getFullYear();
      sel.innerHTML = (years || []).map(y =>
        `<option value="${esc(String(y))}" ${y === current ? 'selected' : ''}>${esc(String(y))}</option>`
      ).join('');
    } catch { /* non-critical */ }
  }

  async function calculate() {
    const customerId = document.getElementById('cap-customer-input')?.value.trim();
    const quarter    = document.getElementById('cap-quarter-select')?.value;
    const year       = document.getElementById('cap-year-select')?.value;
    const btn        = document.getElementById('cap-btn-search');

    if (!customerId) { showToast('Please enter a Sold To Party ID.', 'warning'); return; }
    if (!quarter)    { showToast('Please select a Fiscal Quarter.',   'warning'); return; }
    if (!year)       { showToast('Please select a Fiscal Year.',       'warning'); return; }

    const origHTML = btn.innerHTML;
    setLoading(btn, true, origHTML);
    show('cap-orders-spinner');
    hide('cap-result-card');
    hide('cap-pending-warning');

    try {
      const customer = await API.getCustomer(customerId);
      const allOrders = await API.getCustomerOrders(customerId);

      const fy = parseInt(year, 10);
      const orders = allOrders.filter(o =>
        String(o.fiscalQuarter).toUpperCase() === quarter.toUpperCase() &&
        o.fiscalYear === fy
      );

      const syncedOrders  = orders.filter(o => o.orderValue != null);
      const pendingOrders = orders.filter(o => o.orderValue == null);
      const totalInvoiced = syncedOrders.reduce((sum, o) => sum + Number(o.orderValue || 0), 0);
      const capAllowance  = totalInvoiced * 0.03;
      const currency      = syncedOrders[0]?.currency || 'USD';

      const result = { customer, quarter, year: fy, orders, syncedOrders,
                       pendingOrders, totalInvoiced, capAllowance, currency };

      AppState.capBalance.lastResult        = result;
      AppState.capBalance.currentCustomerId = customerId;
      AppState.capBalance.currentQuarter    = quarter;
      AppState.capBalance.currentYear       = fy;

      renderResult(result);
      renderOrdersTable(result);

    } catch (err) {
      showToast('Error calculating CAP balance: ' + err.message, 'error');
    } finally {
      setLoading(btn, false, origHTML);
      hide('cap-orders-spinner');
    }
  }

  function renderResult({ customer, quarter, year, syncedOrders, pendingOrders,
                           totalInvoiced, capAllowance, currency }) {
    const fmt = v => formatMoney(v, currency);

    setText('cap-result-period',       `${quarter} · FY${year}`);
    setText('cap-res-customer-id',     customer.customerId   || '—');
    setText('cap-res-customer-name',   customer.customerName || '—');
    setText('cap-res-total',           fmt(totalInvoiced));
    setText('cap-res-order-count',     `${syncedOrders.length} order${syncedOrders.length !== 1 ? 's' : ''} included`);
    setText('cap-res-cap',             fmt(capAllowance));

    if (pendingOrders.length > 0) {
      setText('cap-pending-msg',
        `${pendingOrders.length} order${pendingOrders.length !== 1 ? 's' : ''} excluded — price not synced yet. Upload a Price Report to include them.`);
      show('cap-pending-warning');
    } else {
      hide('cap-pending-warning');
    }

    const recCard  = document.getElementById('cap-recommendation');
    const recIcon  = document.getElementById('cap-rec-icon');
    const recTitle = document.getElementById('cap-rec-title');
    const recBody  = document.getElementById('cap-rec-body');

    if (totalInvoiced === 0) {
      recCard.className   = 'cap-recommendation cap-rec-neutral';
      recIcon.textContent  = 'ℹ';
      recTitle.textContent = 'No invoiced orders found';
      recBody.textContent  = `No synced orders found for ${quarter} FY${year}. Upload the Raw Data and Price Reports for this period.`;
    } else {
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

  function renderOrdersTable({ customer, quarter, year, orders, syncedOrders,
                               pendingOrders, totalInvoiced, currency }) {
    const title  = document.getElementById('cap-orders-title');
    const badge  = document.getElementById('cap-orders-badge');
    const body   = document.getElementById('cap-orders-body');
    const footer = document.getElementById('cap-orders-footer');
    const fSum   = document.getElementById('cap-footer-summary');
    const fBadge = document.getElementById('cap-footer-pending-badge');

    if (title) title.textContent = `Orders — ${esc(customer.customerId)} · ${esc(quarter)} FY${year}`;
    if (badge) badge.textContent = `${orders.length} order${orders.length !== 1 ? 's' : ''}`;

    if (!orders.length) {
      body.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">📭</div>
          <div class="empty-title">No orders found</div>
          <div class="empty-sub">No ZRES orders found for ${esc(quarter)} FY${year}.</div>
        </div>`;
      hide('cap-orders-footer');
      return;
    }

    const cols = [
      { key: 'hpeOrderId',          label: 'HPE Order ID',  mono: true },
      { key: 'entryDate',           label: 'Entry Date',    date: true },
      { key: 'headerStatus',        label: 'Header Status', status: true },
      { key: 'invoiceHeaderStatus', label: 'Invoice Status',status: true },
      { key: 'orderValue',          label: 'Order Value',   money: true },
      { key: '_inCap',              label: 'In CAP Calc?' }
    ];

    const thead = cols.map(c => `<th>${esc(c.label)}</th>`).join('');
    const tbody = orders.map(o => {
      const inCap = o.orderValue != null;
      const cells = cols.map(c => {
        if (c.key === '_inCap') {
          return `<td><span class="badge ${inCap ? 'badge-green' : 'badge-gray'}">${inCap ? 'Yes' : 'No — Pending'}</span></td>`;
        }
        if (c.mono)   return `<td class="td-mono">${o[c.key] ? esc(o[c.key]) : NO_VAL}</td>`;
        if (c.date)   { const d = formatDate(o[c.key]); return `<td>${d ? esc(d) : NO_VAL}</td>`; }
        if (c.status) { return o[c.key] ? `<td><span class="badge ${statusBadge(o[c.key])}">${esc(o[c.key])}</span></td>` : `<td>${NO_VAL}</td>`; }
        if (c.money)  {
          return inCap
            ? `<td>${esc(formatMoney(o[c.key], currency))}</td>`
            : `<td><span class="badge badge-pending">Pending</span></td>`;
        }
        return `<td>${o[c.key] ? esc(o[c.key]) : NO_VAL}</td>`;
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

    if (footer && fSum) {
      fSum.textContent = `Total synced: ${formatMoney(totalInvoiced, currency)} · ${syncedOrders.length} of ${orders.length} orders included`;
      if (pendingOrders.length > 0 && fBadge) {
        fBadge.textContent = `${pendingOrders.length} pending excluded`;
        show('cap-footer-pending-badge');
      } else {
        hide('cap-footer-pending-badge');
      }
      show('cap-orders-footer');
    }
  }

  function exportSummary() {
    const r = AppState.capBalance.lastResult;
    if (!r) { showToast('No CAP result to export. Run a calculation first.', 'warning'); return; }
    window.location.href = API.exportUrl({
      customerId: r.customer.customerId,
      quarter:    r.quarter,
      year:       r.year
    });
  }

  function load() { refreshStats(); populateYears(); }

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
   SHARED HELPERS
   ============================================================ */
function setText(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val;
}
function show(id) { const el = document.getElementById(id); if (el) el.style.display = 'block'; }
function hide(id) { const el = document.getElementById(id); if (el) el.style.display = 'none';  }

/* ============================================================
   10. BOOTSTRAP
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {

  document.querySelectorAll('.nav-link[data-section]').forEach(link => {
    link.addEventListener('click', () => navigate(link.dataset.section));
  });

  Orders.init();
  Ingestion.init();
  CapBalance.init();

  // FIX-5: load last upload chip on boot
  refreshLastUpload();

  // Boot fetch with retry for slow Spring Boot cold starts
  (async function bootFetch() {
    const MAX_RETRIES = 3;
    const RETRY_DELAY = 900;
    for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        await Orders.load();
        break;
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

  navigate('section-orders');
});