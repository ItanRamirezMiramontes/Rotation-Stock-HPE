"use strict";

/* ============================================================
   AUDITORÍA: PROBLEMAS ENCONTRADOS Y CORREGIDOS
   ─────────────────────────────────────────────
   1. Orders.load()     → vacío (solo console.log)
   2. Orders.init()     → vacío (sin listeners de filtros, refresh, export)
   3. Ingestion.init()  → vacío (sin modal, sin file slots, sin upload)
   4. CapBalance.init() → vacío (módulo no usado en HTML actual)
   5. Ningún botón tenía addEventListener real
   6. shutdownBtn: único que funcionaba (se mantiene)
   ============================================================ */

/* ============================================================
   1. CONFIG & GLOBAL STATE
   ============================================================ */

const API_BASE = "";

const AppState = {
  orders: {
    page: 0,
    size: 15,
    totalPages: 0,
    totalElements: 0,
    filters: {
      region: "",
      quarter: "",
      year: "",
      customerId: "",
      headerStatus: "",
    },
  },
};

/* ============================================================
   2. HELPERS GENERALES
   ============================================================ */

function esc(val) {
  if (val == null) return "";
  return String(val)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#x27;");
}

function el(id) {
  return document.getElementById(id);
}

function show(id) {
  const e = el(id);
  if (e) e.style.display = "block";
}

function hide(id) {
  const e = el(id);
  if (e) e.style.display = "none";
}

function setText(id, val) {
  const e = el(id);
  if (e) e.textContent = val != null ? val : "—";
}

/* ============================================================
   3. TOAST
   ============================================================ */

function showToast(message, type = "success") {
  const container = el("toast-container");
  if (!container) return;

  const icons = {
    success: "✓",
    error: "✕",
    warning: "⚠",
  };

  const div = document.createElement("div");
  div.className = `toast ${type}`;
  div.innerHTML = `<span>${icons[type] || "•"}</span><span>${esc(message)}</span>`;
  container.appendChild(div);

  setTimeout(() => {
    div.style.opacity = "0";
    div.style.transition = "opacity 0.3s";
    setTimeout(() => div.remove(), 300);
  }, 4000);
}

/* ============================================================
   4. API HELPER
   ============================================================ */

const API = {
  async get(path) {
    const res = await fetch(API_BASE + path);
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return res.json();
  },

  async post(path, formData) {
    const res = await fetch(API_BASE + path, {
      method: "POST",
      body: formData,
    });
    return res;
  },
};

/* ============================================================
   5. ROUTER
   ============================================================ */

const SECTIONS = ["section-orders", "section-cap-balance"];

function navigate(sectionId) {
  SECTIONS.forEach((id) => {
    const e = el(id);
    if (!e) return;
    e.classList.toggle("active", id === sectionId);
  });

  document.querySelectorAll(".nav-link").forEach((link) => {
    link.classList.toggle("active", link.dataset.section === sectionId);
  });

  if (sectionId === "section-orders") Orders.load();
}

/* ============================================================
   6. MÓDULO: ORDERS
   ============================================================ */

const Orders = (() => {
  // ── Cargar filtros dinámicos desde el backend ──
  async function loadFilters() {
    try {
      const data = await API.get("/orders/filters");

      populateSelect("filter-region", data.regions, "All regions");
      populateSelect("filter-quarter", data.quarters, "All quarters");
      populateSelect("filter-year", data.years, "All years");
      populateSelect(
        "filter-header-status",
        data.headerStatuses,
        "All header statuses",
      );
    } catch (e) {
      // No bloquear la UI si los filtros fallan
      console.warn("Could not load filters:", e.message);
    }
  }

  function populateSelect(selectId, values, allLabel) {
    const sel = el(selectId);
    if (!sel || !Array.isArray(values)) return;
    // Guardar valor actual seleccionado
    const current = sel.value;
    // Mantener la opción "All"
    sel.innerHTML = `<option value="">${allLabel}</option>`;
    values.forEach((v) => {
      if (v == null) return;
      const opt = document.createElement("option");
      opt.value = String(v);
      opt.textContent = String(v);
      if (String(v) === current) opt.selected = true;
      sel.appendChild(opt);
    });
  }

  // ── Cargar tabla paginada ──
  async function load() {
    const f = AppState.orders.filters;
    const p = AppState.orders.page;
    const s = AppState.orders.size;

    const params = new URLSearchParams();
    if (f.region) params.set("region", f.region);
    if (f.quarter) params.set("quarter", f.quarter);
    if (f.year) params.set("year", f.year);
    if (f.customerId) params.set("customerId", f.customerId);
    if (f.headerStatus) params.set("headerStatus", f.headerStatus);
    params.set("page", p);
    params.set("size", s);
    params.set("sort", "updatedAt,desc");

    show("orders-spinner");

    const container = el("orders-table-container");
    if (container) container.style.opacity = "0.4";

    try {
      const data = await API.get(`/orders?${params.toString()}`);

      AppState.orders.totalPages = data.totalPages || 0;
      AppState.orders.totalElements = data.totalElements || 0;

      renderTable(data.content || []);
      renderPagination();
      renderPagInfo();
    } catch (e) {
      showToast("Error loading orders: " + e.message, "error");
      if (container)
        container.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">⚠️</div>
          <div class="empty-title">Error loading data</div>
          <div class="empty-subtitle">${esc(e.message)}</div>
        </div>`;
    } finally {
      hide("orders-spinner");
      if (container) container.style.opacity = "1";
    }
  }

  // ── Renderizar tabla HTML ──
  function renderTable(orders) {
    const container = el("orders-table-container");
    if (!container) return;

    if (!orders.length) {
      container.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">📭</div>
          <div class="empty-title">No orders found</div>
          <div class="empty-subtitle">Try changing or clearing the filters</div>
        </div>`;
      return;
    }

    const rows = orders
      .map((o) => {
        const statusBadge = internalStatusBadge(o.internalStatus);
        const value =
          o.orderValue != null
            ? `<span class="td-mono">${Number(o.orderValue).toLocaleString("en-US", { minimumFractionDigits: 2 })}</span>`
            : `<span class="td-pending">PENDING</span>`;

        return `<tr>
        <td class="td-mono">${esc(o.hpeOrderId) || "—"}</td>
        <td class="td-mono">${esc(o.entryDate) || "—"}</td>
        <td>${esc(o.omRegion) || "—"}</td>
        <td>${esc(o.headerStatus) || "—"}</td>
        <td>${statusBadge}</td>
        <td class="td-mono">${o.customer ? esc(o.customer.customerId) : "—"}</td>
        <td>${esc(o.fiscalQuarter) || "—"}</td>
        <td>${o.fiscalYear || "—"}</td>
        <td style="text-align:right">${value}</td>
        <td>${esc(o.currency) || "—"}</td>
      </tr>`;
      })
      .join("");

    container.innerHTML = `
      <div class="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>HPE Order</th>
              <th>Entry Date</th>
              <th>OM Region</th>
              <th>Header Status</th>
              <th>Internal Status</th>
              <th>Customer ID</th>
              <th>Fiscal Q</th>
              <th>FY</th>
              <th style="text-align:right">Order Value</th>
              <th>Currency</th>
            </tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
      </div>`;
  }

  function internalStatusBadge(status) {
    if (!status) return '<span class="badge badge-gray">—</span>';
    const map = {
      LOADED: "badge-blue",
      PRICE_SYNCED: "badge-green",
    };
    const cls = map[status.toUpperCase()] || "badge-gray";
    return `<span class="badge ${cls}">${esc(status)}</span>`;
  }

  // ── Paginación ──
  function renderPagination() {
    const container = el("orders-pagination");
    if (!container) return;

    const total = AppState.orders.totalPages;
    const cur = AppState.orders.page;

    if (total <= 1) {
      container.innerHTML = "";
      return;
    }

    let html = "";

    // Prev
    html += `<button class="btn btn-ghost btn-sm" ${cur === 0 ? "disabled" : ""}
      onclick="Orders._goPage(${cur - 1})">‹ Prev</button>`;

    // Páginas numeradas (ventana de 5)
    const start = Math.max(0, Math.min(cur - 2, total - 5));
    const end = Math.min(total, start + 5);

    for (let i = start; i < end; i++) {
      const active = i === cur ? "btn-primary" : "btn-ghost";
      html += `<button class="btn ${active} btn-sm" onclick="Orders._goPage(${i})">${i + 1}</button>`;
    }

    // Next
    html += `<button class="btn btn-ghost btn-sm" ${cur >= total - 1 ? "disabled" : ""}
      onclick="Orders._goPage(${cur + 1})">Next ›</button>`;

    container.innerHTML = html;
  }

  function renderPagInfo() {
    const total = AppState.orders.totalElements;
    const size = AppState.orders.size;
    const page = AppState.orders.page;
    const from = total === 0 ? 0 : page * size + 1;
    const to = Math.min((page + 1) * size, total);
    setText("orders-pag-info", `Showing ${from}–${to} of ${total}`);
  }

  function _goPage(n) {
    AppState.orders.page = n;
    load();
  }

  // ── Aplicar filtros ──
  function applyFilters() {
    AppState.orders.filters.region = (el("filter-region") || {}).value || "";
    AppState.orders.filters.quarter = (el("filter-quarter") || {}).value || "";
    AppState.orders.filters.year = (el("filter-year") || {}).value || "";
    AppState.orders.filters.customerId =
      (el("filter-customer") || {}).value || "";
    AppState.orders.filters.headerStatus =
      (el("filter-header-status") || {}).value || "";
    AppState.orders.page = 0;
    load();
  }

  // ── Limpiar filtros ──
  function clearFilters() {
    [
      "filter-region",
      "filter-quarter",
      "filter-year",
      "filter-header-status",
    ].forEach((id) => {
      const s = el(id);
      if (s) s.value = "";
    });
    const fc = el("filter-customer");
    if (fc) fc.value = "";

    AppState.orders.filters = {
      region: "",
      quarter: "",
      year: "",
      customerId: "",
      headerStatus: "",
    };
    AppState.orders.page = 0;
    load();
  }

  // ── Exportar Excel ──
  function exportExcel() {
    const f = AppState.orders.filters;
    const params = new URLSearchParams();
    if (f.region) params.set("region", f.region);
    if (f.quarter) params.set("quarter", f.quarter);
    if (f.year) params.set("year", f.year);
    if (f.customerId) params.set("customerId", f.customerId);
    if (f.headerStatus) params.set("headerStatus", f.headerStatus);

    const url = `/orders/export?${params.toString()}`;
    const a = document.createElement("a");
    a.href = url;
    a.download = "";
    document.body.appendChild(a);
    a.click();
    a.remove();
    showToast("Export started — file will download shortly", "success");
  }

  // ── Chip de último upload ──
  async function loadLastUploadChip() {
    try {
      const data = await API.get("/ingestion/last");
      const chip = el("last-upload-chip");
      if (!chip) return;

      if (data.hasData) {
        const d = new Date(data.timestamp);
        const fmt = d.toLocaleString("en-US", {
          month: "short",
          day: "2-digit",
          year: "numeric",
          hour: "2-digit",
          minute: "2-digit",
        });
        chip.innerHTML = `🕒 <strong>Last updated:</strong> ${fmt}`;
        chip.style.display = "flex";
      }
    } catch (_) {
      // silencioso
    }
  }

  // ── Init: registrar listeners ──
  function init() {
    const btnApply = el("btn-apply-filters");
    const btnClear = el("btn-clear-filters");
    const btnRefresh = el("btn-refresh-orders");
    const btnExport = el("btn-export");

    if (btnApply) btnApply.addEventListener("click", applyFilters);
    if (btnClear) btnClear.addEventListener("click", clearFilters);
    if (btnRefresh)
      btnRefresh.addEventListener("click", () => {
        loadFilters();
        load();
      });
    if (btnExport) btnExport.addEventListener("click", exportExcel);

    // Enter en el input de customer
    const fcInput = el("filter-customer");
    if (fcInput)
      fcInput.addEventListener("keydown", (e) => {
        if (e.key === "Enter") applyFilters();
      });

    loadFilters();
    loadLastUploadChip();
  }

  // Exponemos _goPage globalmente para los botones de paginación
  return {
    init,
    load,
    _goPage,
    exportExcel,
    reloadAfterUpload: () => {
      loadFilters();
      loadLastUploadChip();
      load();
    },
  };
})();

// Exponer _goPage globalmente (los botones de paginación usan onclick inline)
window.Orders = Orders;

/* ============================================================
   7. MÓDULO: INGESTION
   ============================================================ */

const Ingestion = (() => {
  let rawFile = null;
  let priceFile = null;

  // ── Abrir / cerrar modal ──
  function openModal() {
    resetModal();
    const overlay = el("modal-ingestion");
    if (overlay) overlay.classList.add("open");
  }

  function closeModal() {
    const overlay = el("modal-ingestion");
    if (overlay) overlay.classList.remove("open");
    resetModal();
  }

  function resetModal() {
    rawFile = null;
    priceFile = null;

    setFileName("ingest-raw-name", "No file selected");
    setFileName("ingest-price-name", "No file selected");

    const rawInput = el("ingest-raw-input");
    const priceInput = el("ingest-price-input");
    if (rawInput) rawInput.value = "";
    if (priceInput) priceInput.value = "";

    resetDropZone("ingest-raw-slot");
    resetDropZone("ingest-price-slot");

    // Deshabilitar botón de upload
    setUploadBtnState(false);

    // Ocultar paneles
    hide("ingest-progress-wrap");
    hide("ingest-result-panel");
    hide("ingest-order-warning");

    // Resetear barra de progreso
    const fill = el("ingest-progress-fill");
    if (fill) fill.style.width = "0%";
  }

  function resetDropZone(slotId) {
    const slot = el(slotId);
    if (!slot) return;
    slot.classList.remove("has-file", "drag-over");
  }

  function setFileName(elId, name) {
    const e = el(elId);
    if (e) e.textContent = name;
  }

  function setUploadBtnState(enabled) {
    const btn = el("btn-ingest-upload");
    if (btn) btn.disabled = !enabled;
  }

  // ── File slot setup (click + drag-and-drop) ──
  function setupSlot(slotId, inputId, nameId, isRaw) {
    const slot = el(slotId);
    const input = el(inputId);
    if (!slot || !input) return;

    // Click en el div → disparar input
    slot.addEventListener("click", () => input.click());

    // Cambio en input
    input.addEventListener("change", () => {
      const file = input.files[0] || null;
      handleFileSelected(file, nameId, slotId, isRaw);
    });

    // Drag & Drop
    slot.addEventListener("dragover", (e) => {
      e.preventDefault();
      slot.classList.add("drag-over");
    });
    slot.addEventListener("dragleave", () =>
      slot.classList.remove("drag-over"),
    );
    slot.addEventListener("drop", (e) => {
      e.preventDefault();
      slot.classList.remove("drag-over");
      const file = e.dataTransfer.files[0] || null;
      handleFileSelected(file, nameId, slotId, isRaw);
    });
  }

  function handleFileSelected(file, nameId, slotId, isRaw) {
    if (!file) return;

    const ext = file.name.toLowerCase();
    if (!ext.endsWith(".xlsx") && !ext.endsWith(".xls")) {
      showToast("Only .xlsx and .xls files are accepted", "error");
      return;
    }

    if (isRaw) {
      rawFile = file;
    } else {
      priceFile = file;
    }

    setFileName(nameId, file.name);
    const slot = el(slotId);
    if (slot) slot.classList.add("has-file");

    // Habilitar botón si hay al menos un archivo
    setUploadBtnState(rawFile != null || priceFile != null);

    // Ocultar warning si estaba visible
    hide("ingest-order-warning");
  }

  // ── Simular barra de progreso ──
  function animateProgress(targetPct, durationMs) {
    return new Promise((resolve) => {
      const fill = el("ingest-progress-fill");
      if (!fill) {
        resolve();
        return;
      }
      const start = parseFloat(fill.style.width) || 0;
      const diff = targetPct - start;
      const steps = 20;
      const interval = durationMs / steps;
      let i = 0;
      const timer = setInterval(() => {
        i++;
        fill.style.width = `${start + (diff * i) / steps}%`;
        if (i >= steps) {
          clearInterval(timer);
          resolve();
        }
      }, interval);
    });
  }

  // ── Ejecutar upload ──
  async function doUpload() {
    if (!rawFile && !priceFile) return;

    const btn = el("btn-ingest-upload");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Uploading…";
    }

    hide("ingest-order-warning");
    hide("ingest-result-panel");
    show("ingest-progress-wrap");

    await animateProgress(30, 300);

    const formData = new FormData();
    if (rawFile) formData.append("rawFile", rawFile);
    if (priceFile) formData.append("priceFile", priceFile);

    await animateProgress(70, 400);

    try {
      const res = await fetch("/ingestion/upload", {
        method: "POST",
        body: formData,
      });

      await animateProgress(100, 200);

      if (res.status === 409) {
        // Orden de carga incorrecto
        let errMsg = "Please upload Raw Data Report first.";
        try {
          const errData = await res.json();
          errMsg = errData.error || errMsg;
        } catch (_) {}

        const warnSpan = document.querySelector(".order-warn-msg");
        if (warnSpan) warnSpan.textContent = errMsg;

        show("ingest-order-warning");
        hide("ingest-progress-wrap");
        setUploadBtnState(true);
        if (btn) btn.textContent = "Upload and Process";
        return;
      }

      if (!res.ok) {
        let errMsg = `Server error (HTTP ${res.status})`;
        try {
          const errData = await res.json();
          errMsg = errData.error || errMsg;
        } catch (_) {}
        throw new Error(errMsg);
      }

      const responseData = await res.json();
      hide("ingest-progress-wrap");

      // El backend retorna un solo IngestionResponseDTO o un Map { rawData, priceReport }
      if (responseData.rawData || responseData.priceReport) {
        // Dual upload — mostramos el resultado del último procesado (priceReport si existe, si no rawData)
        const primary = responseData.priceReport || responseData.rawData;
        renderResult(primary);
      } else {
        renderResult(responseData);
      }

      show("ingest-result-panel");
      showToast("Upload completed successfully", "success");

      // Recargar tabla de órdenes
      Orders.reloadAfterUpload();
    } catch (e) {
      hide("ingest-progress-wrap");
      setUploadBtnState(true);
      if (btn) btn.textContent = "Upload and Process";
      showToast("Upload failed: " + e.message, "error");
    }

    if (btn) btn.textContent = "Upload and Process";
  }

  function renderResult(dto) {
    if (!dto) return;

    // Badge de status
    const statusEl = el("ingest-res-status");
    if (statusEl) {
      const cls =
        dto.status === "SUCCESS"
          ? "badge-green"
          : dto.status === "PARTIAL"
            ? "badge-yellow"
            : "badge-red";
      statusEl.className = `badge ${cls}`;
      statusEl.textContent = dto.status || "—";
    }

    setText("ingest-res-type", dto.reportType || "—");
    setText(
      "ingest-res-ts",
      dto.timestamp
        ? new Date(dto.timestamp).toLocaleString("en-US", {
            month: "short",
            day: "2-digit",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit",
          })
        : "",
    );
    setText("ingest-res-msg", dto.message || "");
    setText("ingest-res-total", dto.totalRead ?? 0);
    setText("ingest-res-insert", dto.inserted ?? 0);
    setText("ingest-res-update", dto.updated ?? 0);
    setText("ingest-res-skip", dto.skipped ?? 0);
    setText("ingest-res-error", dto.errors ?? 0);

    const errorSection = el("ingest-error-section");
    const errorList = el("ingest-error-list");

    if (dto.errors > 0 && dto.errorDetails && dto.errorDetails.length > 0) {
      if (errorSection) errorSection.style.display = "block";
      if (errorList) {
        errorList.innerHTML = dto.errorDetails
          .map(
            (d) =>
              `<div style="padding:0.5rem 0.85rem; border-bottom:1px solid var(--border); font-size:0.8rem;">
            <span class="td-mono">${esc(d.hpeOrderId)}</span>
            <span style="color:var(--text-secondary); margin-left:0.5rem;">${esc(d.reason)}</span>
           </div>`,
          )
          .join("");
      }
    } else {
      if (errorSection) errorSection.style.display = "none";
    }
  }

  // ── Init ──
  function init() {
    // Botones para abrir modal (puede haber varios .btn-open-ingest)
    document.querySelectorAll(".btn-open-ingest").forEach((btn) => {
      btn.addEventListener("click", openModal);
    });

    // Cerrar modal
    const closeBtn = el("modal-close-ingest");
    const cancelBtn = el("modal-cancel-ingest");
    const overlay = el("modal-ingestion");

    if (closeBtn) closeBtn.addEventListener("click", closeModal);
    if (cancelBtn) cancelBtn.addEventListener("click", closeModal);

    // Click fuera del modal → cerrar
    if (overlay) {
      overlay.addEventListener("click", (e) => {
        if (e.target === overlay) closeModal();
      });
    }

    // Configurar slots de archivos
    setupSlot("ingest-raw-slot", "ingest-raw-input", "ingest-raw-name", true);
    setupSlot(
      "ingest-price-slot",
      "ingest-price-input",
      "ingest-price-name",
      false,
    );

    // Botón de upload
    const uploadBtn = el("btn-ingest-upload");
    if (uploadBtn) uploadBtn.addEventListener("click", doUpload);
  }

  return { init, openModal, closeModal };
})();

/* ============================================================
   8. SHUTDOWN BUTTON
   ============================================================ */

function cerrarUI(message = "Aplicación cerrada") {
  document.body.innerHTML = `
    <div style="
      display:flex; justify-content:center; align-items:center;
      height:100vh; font-family:system-ui; background:#0f172a;
      color:white; flex-direction:column; text-align:center; gap:0.5rem;
    ">
      <div style="font-size:2.5rem;">✓</div>
      <h2 style="margin-bottom:4px;">${esc(message)}</h2>
      <p style="opacity:0.7;">El servidor ha sido detenido correctamente</p>
      <p style="opacity:0.5; font-size:0.8rem;">Puedes cerrar esta pestaña</p>
    </div>`;
}

/* ============================================================
   9. BOOTSTRAP — DOMContentLoaded
   ============================================================ */

document.addEventListener("DOMContentLoaded", () => {
  // Navegación SPA
  document.querySelectorAll(".nav-link[data-section]").forEach((link) => {
    link.addEventListener("click", () => navigate(link.dataset.section));
  });

  // Inicializar módulos
  Orders.init();
  Ingestion.init();

  // Shutdown button
  const shutdownBtn = el("shutdownBtn");
  if (shutdownBtn) {
    shutdownBtn.addEventListener("click", async () => {
      if (!confirm("¿Seguro que quieres cerrar la aplicación?")) return;

      shutdownBtn.disabled = true;
      shutdownBtn.textContent = "Cerrando servidor…";

      let message = "Servidor detenido.";

      try {
        const res = await fetch("/api/system/shutdown", { method: "POST" });
        try {
          const data = await res.json();
          message = data.message || message;
        } catch (_) {
          // Normal: server dies before full response
        }
      } catch (_) {
        // Normal: connection closed because server shut down
      }

      cerrarUI(message);
    });
  }

  // Vista por defecto
  navigate("section-orders");
});
