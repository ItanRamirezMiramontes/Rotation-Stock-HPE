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
    lastResult: null,
    currentCustomerId: null,
    currentQuarter: null,
    currentYear: null
  }
};

/* ============================================================
   2. HELPERS
   ============================================================ */

function esc(val) {
  if (val == null) return '';
  return String(val)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;');
}

function setText(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val;
}

function show(id) {
  const el = document.getElementById(id);
  if (el) el.style.display = 'block';
}

function hide(id) {
  const el = document.getElementById(id);
  if (el) el.style.display = 'none';
}

/* ============================================================
   3. ROUTER (🔥 FIX PRINCIPAL)
   ============================================================ */

const SECTIONS = ['section-orders', 'section-cap-balance'];

function navigate(sectionId) {

  SECTIONS.forEach(id => {
    const el = document.getElementById(id);

    if (!el) {
      console.warn('Sección no encontrada:', id);
      return; // 🔥 evita que truene
    }

    el.classList.toggle('active', id === sectionId);
  });

  document.querySelectorAll('.nav-link').forEach(link => {
    if (!link) return;

    link.classList.toggle('active', link.dataset.section === sectionId);
  });

  if (sectionId === 'section-orders') Orders.load();
}

/* ============================================================
   4. API
   ============================================================ */

const API = {
  async get(path) {
    const res = await fetch(API_BASE + path);
    if (!res.ok) throw new Error(res.statusText);
    return res.json();
  }
};

/* ============================================================
   5. ORDERS (MIN)
   ============================================================ */

const Orders = (() => {

  async function load() {
    console.log('Loading Orders...');
  }

  function init() {}

  return { init, load };
})();

/* ============================================================
   6. CAP BALANCE (MIN)
   ============================================================ */

const CapBalance = (() => {

  function load() {
    console.log('Loading CAP Balance...');
  }

  function init() {}

  return { init, load };
})();

/* ============================================================
   7. INGESTION (MIN)
   ============================================================ */

const Ingestion = (() => {

  function init() {
    console.log('Ingestion ready');
  }

  return { init };
})();

/* ============================================================
   8. SHUTDOWN BUTTON (🔥 CORREGIDO)
   ============================================================ */

function cerrarUI(message = "Aplicación cerrada") {

  document.body.innerHTML = `
    <div style="
      display:flex;
      justify-content:center;
      align-items:center;
      height:100vh;
      font-family:system-ui;
      background:#0f172a;
      color:white;
      flex-direction:column;
      text-align:center;
    ">
      <h2 style="margin-bottom:10px;">${message}</h2>
      <p style="opacity:0.7;">El servidor ha sido detenido correctamente</p>
      <p style="opacity:0.5; font-size:0.8rem;">Puedes cerrar esta pestaña</p>
    </div>
  `;
}

/* ============================================================
   9. BOOTSTRAP (🔥 LIMPIO)
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {

  // NAV
  document.querySelectorAll('.nav-link[data-section]').forEach(link => {
    link.addEventListener('click', () => navigate(link.dataset.section));
  });

  // INIT
  Orders.init();
  Ingestion.init();
  CapBalance.init();

  const shutdownBtn = document.getElementById("shutdownBtn");

  if (shutdownBtn) {
    shutdownBtn.addEventListener("click", async () => {

      if (!confirm("¿Seguro que quieres cerrar la aplicación?")) return;

      shutdownBtn.disabled = true;
      shutdownBtn.textContent = "Cerrando servidor...";

      let message = "Servidor detenido.";

      try {
        const res = await fetch("/api/system/shutdown", { method: "POST" });

        // ⚠️ Puede fallar si el server muere rápido
        try {
          const data = await res.json();
          message = data.message || message;
        } catch {
          // Ignorar error de parseo (normal cuando el server muere)
        }

      } catch (err) {
        // 🔥 ESTO ES NORMAL
        // El servidor se apagó antes de responder completamente
        console.warn("Conexión cerrada porque el servidor se apagó");
      }

      // 🔥 SIEMPRE cerrar UI
      cerrarUI(message);
    });
  }

  // DEFAULT VIEW (🔥 SOLO UNA VEZ)
  navigate('section-orders');
});