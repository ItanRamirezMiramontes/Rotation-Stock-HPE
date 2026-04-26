/**
 * HPE CAP Rotation Balance — app.js
 * SPA Vanilla JS — v2.0 Monolito Desacoplado
 *
 * Módulos:
 *   Config       — constantes y estado global
 *   API          — todas las llamadas fetch centralizadas
 *   UI Helpers   — toast, spinner, highlight, formateo
 *   Router       — navegación SPA sin recarga
 *   Dashboard    — stat cards y tabla de recientes
 *   Orders       — tabla paginada con filtros server-side
 *   Ingestion    — upload modal con panel de resumen post-ingesta
 */

'use strict';

/* ============================================================
   1. CONFIG & ESTADO GLOBAL
   ============================================================ */

const API_BASE = '';  // Vacío = mismo origen (monolito Spring Boot sirve el frontend)

const AppState = {
  orders: {
    page: 0,
    size: 15,
    totalPages: 0,
    totalElements: 0,
    filters: { region: '', quarter: '', year: '', customerId: '' }
  },
  dashboard: {
    currentCustomerId: null
  }
};

/* ============================================================
   2. API — Capa de acceso a datos (fetch centralizado)
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

  async post(path, body) {
    const res = await fetch(API_BASE + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
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

  /* Órdenes paginadas con filtros server-side */
  async getOrders(page, size, filters) {
    const params = new URLSearchParams({ page, size });
    if (filters.region)     params.set('region',     filters.region);
    if (filters.quarter)    params.set('quarter',    filters.quarter);
    if (filters.year)       params.set('year',       filters.year);
    if (filters.customerId) params.set('customerId', filters.customerId);
    return this.get(`/orders?${params}`);
  },

  async getStats()      { return this.get('/orders/stats'); },
  async getFilters()    { return this.get('/orders/filters'); },
  async getCustomer(id) { return this.get(`/customers/${encodeURIComponent(id)}`); },
  async getCustomerOrders(id) { return this.get(`/customers/${encodeURIComponent(id)}/orders`); },
  async getAllCustomers()     { return this.get('/customers'); },
  async getRecentOrders()    { return this.get('/orders/recent'); },

  exportUrl(filters) {
    const params = new URLSearchParams();
    if (filters.region)     params.set('region',     filters.region);
    if (filters.quarter)    params.set('quarter',    filters.quarter);
    if (filters.year)       params.set('year',       filters.year);
    if (filters.customerId) params.set('customerId', filters.customerId);
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
  const color = colors[type] || colors.success;
  t.innerHTML = `
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="2.5">${icons[type] || icons.success}</svg>
    <span>${msg}</span>`;
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
  if (st.includes('synced')  || st.includes('complet') || st.includes('ok'))     return 'badge-green';
  if (st.includes('loaded')  || st.includes('pend')    || st.includes('process')) return 'badge-yellow';
  if (st.includes('cancel')  || st.includes('error')   || st.includes('fail'))    return 'badge-red';
  return 'badge-blue';
}

function formatDate(val) {
  if (!val) return '—';
  try { return new Date(val).toLocaleDateString('es-MX', { year: 'numeric', month: 'short', day: '2-digit' }); }
  catch { return val; }
}

function formatMoney(val, currency) {
  if (val == null) return null; // null = pendiente, lo maneja el caller
  const sym = currency === 'MXN' ? 'MX$' : currency === 'CAD' ? 'CA$' : '$';
  return sym + Number(val).toLocaleString('en-US', { minimumFractionDigits: 2 });
}

function highlight(val, term) {
  if (!term || !val) return val ?? '—';
  const s = String(val);
  const idx = s.toLowerCase().indexOf(term.toLowerCase());
  if (idx === -1) return s;
  return s.slice(0, idx) +
    `<mark style="background:rgba(1,169,130,0.25);border-radius:2px;padding:0 1px;">${s.slice(idx, idx + term.length)}</mark>` +
    s.slice(idx + term.length);
}

/* Renderiza una celda. Si el valor es null/vacío en una columna "esperable", muestra badge Pendiente */
function renderCell(col, val, filterTerm, currency) {
  const PENDING_COLS = ['orderValue'];
  const MONO_COLS    = ['hpeOrderId', 'custPoRef', 'sorg'];
  const DATE_COLS    = ['entryDate', 'updatedAt'];
  const STATUS_COLS  = ['headerStatus', 'invoiceHeaderStatus', 'internalStatus'];
  const MONEY_COLS   = ['orderValue'];

  if (PENDING_COLS.includes(col) && (val == null || val === '')) {
    return `<td><span class="badge badge-pending">Pendiente</span></td>`;
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

const SECTIONS = ['section-orders', 'section-ingestion', 'section-dashboard'];

function navigate(sectionId) {
  SECTIONS.forEach(id => {
    document.getElementById(id).classList.toggle('active', id === sectionId);
  });
  document.querySelectorAll('.nav-link').forEach(link => {
    link.classList.toggle('active', link.dataset.section === sectionId);
  });

  // Lazy-load de datos al entrar a cada sección
  if (sectionId === 'section-orders')     Orders.load();
  if (sectionId === 'section-dashboard')  Dashboard.load();
}

/* ============================================================
   5. MÓDULO: ORDERS (Tabla paginada + filtros server-side)
   ============================================================ */

const Orders = (() => {
  // Columnas en el orden del reporte final (15 columnas)
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
    { key: 'omRegion',            label: 'Customer',    customRender: (o) => o.customer?.customerId ?? '—' },
    { key: 'shipToAddress',       label: 'Ship-To' },
    { key: 'rtm',                 label: 'RTM' },
    { key: 'currency',            label: 'Currency' },
    { key: 'orderValue',          label: 'Order Value' },
    { key: 'fiscalQuarter',       label: 'Quarter' },
    { key: 'fiscalYear',          label: 'FY' },
    { key: 'internalStatus',      label: 'Status' }
  ];

  let filterTerm = '';
  let loaded = false;

  async function initFilters() {
    try {
      const { regions, quarters, years } = await API.getFilters();
      populateSelect('filter-region',  regions,  'Todas las regiones');
      populateSelect('filter-quarter', quarters, 'Todos los quarters');
      populateSelect('filter-year',    years,    'Todos los años');
    } catch (e) {
      console.warn('No se pudieron cargar los filtros:', e.message);
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
    spinner.style.display = 'block';

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
          <div class="empty-title">Error al cargar datos</div>
          <div class="empty-sub">${err.message}</div>
        </div>`;
      showToast(err.message, 'error');
    } finally {
      spinner.style.display = 'none';
    }
  }

  function renderTable(orders) {
    const container = document.getElementById('orders-table-container');
    if (!orders.length) {
      container.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">📭</div>
          <div class="empty-title">Sin resultados</div>
          <div class="empty-sub">${AppState.orders.filters.region || AppState.orders.filters.quarter
            ? 'Ninguna orden coincide con los filtros aplicados.'
            : 'La base de datos está vacía. Sube archivos Excel primero.'}</div>
        </div>`;
      return;
    }

    const thead = COLUMNS.map(c => `<th>${c.label}</th>`).join('');
    const tbody = orders.map(o => {
      const currency = o.currency;
      const cells = COLUMNS.map(c => {
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
    if (el) el.textContent = `${start}–${end} de ${totalElements} · Pág. ${page + 1}/${totalPages}`;
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
    if (!loaded) { initFilters(); loaded = true; }
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

  return { init, load };
})();

/* ============================================================
   6. MÓDULO: INGESTION (Upload + panel de resultado)
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
    document.getElementById('ingest-file-name').textContent = '';
    document.getElementById('ingest-progress-wrap').style.display  = 'none';
    document.getElementById('ingest-result-panel').style.display   = 'none';
    document.getElementById('drop-zone').classList.remove('drag-over');
    const btnUpload = document.getElementById('btn-ingest-upload');
    btnUpload.disabled = true;
  }

  function setFile(file) {
    if (!file || (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls'))) {
      showToast('Solo se aceptan archivos .xlsx o .xls', 'error');
      return;
    }
    selectedFile = file;
    document.getElementById('ingest-file-name').textContent = file.name;
    document.getElementById('btn-ingest-upload').disabled = false;
  }

  async function upload() {
    if (!selectedFile) return;
    const btn = document.getElementById('btn-ingest-upload');
    const origHTML = btn.innerHTML;
    setLoading(btn, true, origHTML);

    document.getElementById('ingest-progress-wrap').style.display = 'block';
    document.getElementById('ingest-progress-fill').style.width = '40%';

    try {
      const result = await API.upload('/ingestion/upload', selectedFile);
      document.getElementById('ingest-progress-fill').style.width = '100%';

      renderIngestionResult(result);
      document.getElementById('ingest-result-panel').style.display = 'block';

      const toastType = result.status === 'SUCCESS' ? 'success' : result.status === 'PARTIAL' ? 'warning' : 'error';
      showToast(result.message, toastType);

      // Refrescar stats y tabla si el usuario está en esas secciones
      Dashboard.refreshStats();
      if (document.getElementById('section-orders').classList.contains('active')) {
        Orders.load();
      }
    } catch (err) {
      showToast('Error al subir archivo: ' + err.message, 'error');
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
    document.getElementById('ingest-res-status').className   =
      `badge ${r.status === 'SUCCESS' ? 'badge-green' : r.status === 'PARTIAL' ? 'badge-yellow' : 'badge-red'}`;
    document.getElementById('ingest-res-status').textContent = r.status;
    document.getElementById('ingest-res-ts').textContent     = formatDate(r.timestamp);

    const errList = document.getElementById('ingest-error-list');
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
    // Botón de apertura del modal (en la navbar y en la sección de ingesta)
    document.querySelectorAll('.btn-open-ingest').forEach(btn => {
      btn.addEventListener('click', openModal);
    });
    document.getElementById('modal-close-ingest')?.addEventListener('click', closeModal);
    document.getElementById('modal-overlay-ingest')?.addEventListener('click', (e) => {
      if (e.target === e.currentTarget) closeModal();
    });
    document.getElementById('btn-ingest-upload')?.addEventListener('click', upload);

    // Drop zone
    const dropZone  = document.getElementById('drop-zone');
    const fileInput  = document.getElementById('ingest-file-input');

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
   7. MÓDULO: DASHBOARD (Stat cards + tabla de recientes)
   ============================================================ */

const Dashboard = (() => {
  async function refreshStats() {
    try {
      const stats = await API.getStats();
      setText('dash-stat-total',    stats.total        ?? '—');
      setText('dash-stat-synced',   stats.priceSynced  ?? '—');
      setText('dash-stat-loaded',   stats.loaded       ?? '—');
      setText('dash-stat-pending',  stats.pricePending ?? '—');
    } catch (e) { console.warn('Stats error:', e.message); }
  }

  async function loadRecentOrders() {
    const container = document.getElementById('dash-recent-orders');
    if (!container) return;
    try {
      const orders = await API.getRecentOrders();
      if (!orders.length) {
        container.innerHTML = '<div class="empty-state"><div class="empty-icon">📋</div><div class="empty-title">Sin actividad reciente</div></div>';
        return;
      }
      const cols = ['hpeOrderId', 'omRegion', 'fiscalQuarter', 'orderValue', 'internalStatus', 'updatedAt'];
      const labels = { hpeOrderId: 'HPE Order ID', omRegion: 'Región', fiscalQuarter: 'Quarter',
                       orderValue: 'Valor', internalStatus: 'Status', updatedAt: 'Actualizado' };
      container.innerHTML = `
        <div class="table-wrapper" style="border:none;border-radius:0;">
          <table>
            <thead><tr>${cols.map(c => `<th>${labels[c]}</th>`).join('')}</tr></thead>
            <tbody>
              ${orders.map(o => `<tr>
                ${cols.map(c => renderCell(c, o[c], '', o.currency)).join('')}
              </tr>`).join('')}
            </tbody>
          </table>
        </div>`;
    } catch (e) {
      container.innerHTML = '<div class="empty-state"><div class="empty-sub">No se pudo cargar la actividad reciente.</div></div>';
    }
  }

  /* Búsqueda de cliente desde el dashboard */
  async function searchCustomer() {
    const id  = document.getElementById('dash-customer-input')?.value.trim();
    const btn = document.getElementById('dash-btn-search');
    if (!id) return;
    const origHTML = btn.innerHTML;
    setLoading(btn, true, origHTML);
    document.getElementById('dash-customer-result').style.display    = 'none';
    document.getElementById('dash-customer-notfound').style.display  = 'none';

    try {
      const c = await API.getCustomer(id);
      AppState.dashboard.currentCustomerId = id;
      setText('dash-c-id',   c.customerId  || id);
      setText('dash-c-name', c.customerName || '—');
      document.getElementById('dash-customer-result').style.display = 'block';
    } catch {
      document.getElementById('dash-customer-notfound').style.display = 'block';
    } finally {
      setLoading(btn, false, origHTML);
    }
  }

  async function loadCustomerOrders() {
    const id = AppState.dashboard.currentCustomerId;
    if (!id) return;
    const btn = document.getElementById('dash-btn-load-orders');
    const origHTML = btn.innerHTML;
    setLoading(btn, true, origHTML);

    try {
      const orders = await API.getCustomerOrders(id);
      const body    = document.getElementById('dash-orders-body');
      const badge   = document.getElementById('dash-orders-badge');
      badge.textContent = orders.length + ' órdenes';

      if (!orders.length) {
        body.innerHTML = '<div class="empty-state"><div class="empty-icon">📭</div><div class="empty-title">Sin órdenes</div></div>';
        return;
      }
      const cols = ['hpeOrderId', 'omRegion', 'orderType', 'entryDate', 'orderValue', 'internalStatus'];
      const labels = { hpeOrderId: 'HPE Order ID', omRegion: 'Región', orderType: 'Tipo',
                       entryDate: 'Fecha', orderValue: 'Valor', internalStatus: 'Status' };
      body.innerHTML = `
        <div class="table-wrapper" style="border:none;border-radius:0;">
          <table>
            <thead><tr>${cols.map(c => `<th>${labels[c]}</th>`).join('')}</tr></thead>
            <tbody>
              ${orders.slice(0, 20).map(o => `<tr>
                ${cols.map(c => renderCell(c, o[c], '', o.currency)).join('')}
              </tr>`).join('')}
            </tbody>
          </table>
        </div>`;
    } catch (err) {
      showToast('Error al cargar órdenes: ' + err.message, 'error');
    } finally {
      setLoading(btn, false, origHTML);
    }
  }

  function setText(id, val) {
    const el = document.getElementById(id);
    if (el) el.textContent = val;
  }

  function load() {
    refreshStats();
    loadRecentOrders();
  }

  function init() {
    document.getElementById('dash-btn-search')?.addEventListener('click', searchCustomer);
    document.getElementById('dash-customer-input')?.addEventListener('keydown', e => {
      if (e.key === 'Enter') searchCustomer();
    });
    document.getElementById('dash-btn-load-orders')?.addEventListener('click', loadCustomerOrders);
  }

  return { init, load, refreshStats };
})();

/* ============================================================
   8. BOOTSTRAP — Init al cargar el DOM
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {
  // Router: ligar botones de navegación
  document.querySelectorAll('.nav-link[data-section]').forEach(link => {
    link.addEventListener('click', () => navigate(link.dataset.section));
  });

  // Inicializar módulos
  Orders.init();
  Ingestion.init();
  Dashboard.init();

  // Sección por defecto
  navigate('section-orders');
});