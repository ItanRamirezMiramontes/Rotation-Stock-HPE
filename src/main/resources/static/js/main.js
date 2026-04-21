// =============================================
//  HPE CAP Rotation Balance — main.js
//  Global utilities & nav active state
// =============================================

document.addEventListener('DOMContentLoaded', () => {
  // Mark active nav link based on current path
  const path = window.location.pathname;
  document.querySelectorAll('.nav-link').forEach(link => {
    link.classList.remove('active');
    const href = link.getAttribute('href');
    if (href && path.startsWith(href)) {
      link.classList.add('active');
    }
  });
});
