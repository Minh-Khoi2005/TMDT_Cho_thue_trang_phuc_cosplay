/* CLARITY shared interface controller. */
(function () {
  'use strict';
  const page = document.body.dataset.page || '';
  let toastTimer;
  function notify(message) {
    let toast = document.querySelector('.review-toast');
    if (!toast) {
      toast = document.createElement('div');
      toast.className = 'review-toast';
      toast.setAttribute('role', 'status');
      toast.setAttribute('aria-live', 'polite');
      document.body.appendChild(toast);
    }
    toast.textContent = message;
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { toast.hidden = true; }, 5500);
  }
  // Navigation is explicit HTML links, never inferred from arbitrary button text.
  let menuTrigger = null;
  function syncSidebarAccess() {
    const sidebar = document.querySelector('.review-sidebar');
    if (!sidebar) return;
    const hidden = window.innerWidth <= 960 && !sidebar.classList.contains('is-open');
    sidebar.inert = hidden;
    sidebar.setAttribute('aria-hidden', String(hidden));
  }
  function closeMenus(restoreFocus) {
    document.querySelectorAll('.review-sidebar.is-open,.review-nav.is-open').forEach(function (el) { el.classList.remove('is-open'); });
    document.querySelectorAll('[data-menu]').forEach(function (el) { el.setAttribute('aria-expanded', 'false'); });
    const overlay = document.querySelector('.sidebar-overlay');
    if (overlay) overlay.classList.remove('is-open');
    syncSidebarAccess();
    if (restoreFocus && menuTrigger) menuTrigger.focus();
  }
  document.querySelectorAll('[data-menu]').forEach(function (button) {
    button.addEventListener('click', function () {
      const menu = document.getElementById(button.dataset.menu);
      if (!menu) return;
      const opening = !menu.classList.contains('is-open');
      closeMenus(false);
      menuTrigger = button;
      menu.classList.toggle('is-open', opening);
      button.setAttribute('aria-expanded', String(opening));
      syncSidebarAccess();
      if (menu.classList.contains('review-sidebar')) {
        const overlay = document.querySelector('.sidebar-overlay');
        if (overlay) overlay.classList.toggle('is-open', opening);
        if (opening) { const first = menu.querySelector('a'); if (first) first.focus(); }
      }
    });
  });
  const overlay = document.querySelector('.sidebar-overlay');
  if (overlay) overlay.addEventListener('click', function () { closeMenus(true); });
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') closeMenus(true);
    const sidebar = document.querySelector('.review-sidebar.is-open');
    if (sidebar && event.key === 'Tab') {
      const links = Array.from(sidebar.querySelectorAll('a[href],button:not([disabled])'));
      const first = links[0], last = links[links.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
  });
  window.addEventListener('resize', function () { if (window.innerWidth > 960) closeMenus(false); syncSidebarAccess(); });
  syncSidebarAccess();
  // Password visibility never saves or transmits the password.
  document.querySelectorAll('.password-toggle,.js-toggle-pass').forEach(function (button) {
    button.addEventListener('click', function () {
      const scope = button.closest('.password-field,.input') || button.parentElement;
      const input = scope.querySelector('input[type="password"],input[data-password-visible]');
      if (!input) return;
      const visible = input.type === 'password';
      input.type = visible ? 'text' : 'password';
      input.toggleAttribute('data-password-visible', visible);
      button.textContent = visible ? '\u1ea8n' : 'Hi\u1ec7n';
      button.setAttribute('aria-pressed', String(visible));
      button.setAttribute('aria-label', visible ? '\u1ea8n m\u1eadt kh\u1ea9u' : 'Hi\u1ec7n m\u1eadt kh\u1ea9u');
    });
  });
  document.querySelectorAll('form[data-auth]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      event.preventDefault();
      const message = form.querySelector('.form-message');
      const controls = Array.from(form.querySelectorAll('input[required]'));
      let invalid = controls.find(function (input) { return !input.checkValidity() || (input.type !== 'checkbox' && !input.value.trim()); });
      let text = 'Vui l\u00f2ng nh\u1eadp \u0111\u1ea7y \u0111\u1ee7 th\u00f4ng tin h\u1ee3p l\u1ec7. M\u1eadt kh\u1ea9u c\u1ea7n \u00edt nh\u1ea5t 8 k\u00fd t\u1ef1; h\u00e3y ki\u1ec3m tra email v\u00e0 \u0111i\u1ec1u kho\u1ea3n.';
      const identifier = form.querySelector('[name="identifier"]');
      if (!invalid && identifier && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(identifier.value.trim()) && !/^\+?[\d\s()-]{9,16}$/.test(identifier.value.trim())) {
        invalid = identifier;
        text = 'Vui l\u00f2ng nh\u1eadp email ho\u1eb7c s\u1ed1 \u0111i\u1ec7n tho\u1ea1i h\u1ee3p l\u1ec7.';
      }
      const phone = form.querySelector('[name="phone"]');
      if (!invalid && phone && !/^\+?[\d\s()-]{9,16}$/.test(phone.value.trim())) { invalid = phone; text = 'Vui l\u00f2ng ki\u1ec3m tra s\u1ed1 \u0111i\u1ec7n tho\u1ea1i.'; }
      const confirm = form.querySelector('[name="confirm-password"]');
      if (!invalid && confirm && confirm.value !== form.querySelector('[name="password"]').value) { invalid = confirm; text = 'M\u1eadt kh\u1ea9u x\u00e1c nh\u1eadn ch\u01b0a kh\u1edbp.'; }
      if (invalid) {
        message.textContent = text; message.hidden = false; invalid.setAttribute('aria-invalid', 'true'); invalid.focus(); return;
      }
      controls.forEach(function (input) { input.removeAttribute('aria-invalid'); });
      message.hidden = true;
      notify('Bi\u1ec3u m\u1eabu h\u1ee3p l\u1ec7.');
    });
    form.addEventListener('input', function (event) { if (event.target.matches('input')) event.target.removeAttribute('aria-invalid'); });
  });
  document.querySelectorAll('[data-open-dialog]').forEach(function (button) {
    button.addEventListener('click', function () { const dialog = document.getElementById(button.dataset.openDialog); if (dialog && !dialog.open) dialog.showModal(); });
  });
  document.querySelectorAll('[data-close-dialog]').forEach(function (button) { button.addEventListener('click', function () { const dialog = button.closest('dialog'); if (dialog) dialog.close(); }); });
  document.querySelectorAll('dialog').forEach(function (dialog) {
    dialog.addEventListener('click', function (event) {
      const box = dialog.getBoundingClientRect();
      if (event.target === dialog && (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom)) dialog.close();
    });
  });
  document.querySelectorAll('.acc__title').forEach(function (button, index) {
    const panel = button.parentElement.querySelector('.acc__text');
    if (!panel) return;
    panel.id = 'accordion-panel-' + index;
    button.setAttribute('aria-controls', panel.id);
    function sync() { const open = button.parentElement.classList.contains('open'); button.setAttribute('aria-expanded', String(open)); panel.hidden = !open; }
    sync();
    button.addEventListener('click', function () { button.parentElement.classList.toggle('open'); sync(); });
  });
  // Local-only table search is scoped to the table near the input.
  document.querySelectorAll('[data-table-search]').forEach(function (input) {
    input.addEventListener('input', function () {
      const scope = input.closest('.workspace-body') || document;
      const rows = scope.querySelectorAll('.table-row,[class*="usr-row-"]');
      const q = normalize(input.value);
      rows.forEach(function (row) { row.hidden = Boolean(q && !normalize(row.textContent).includes(q)); });
    });
  });
  function normalize(value) { return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/\u0111/g, 'd').replace(/\u0110/g, 'D').toLowerCase().trim(); }
  // Catalog filtering only touches the nine supplied static product cards.
  if (page === 'products' && document.body.dataset.live !== 'true') {
    const query = document.getElementById('catalog-query');
    const category = document.getElementById('filter-category');
    const series = document.getElementById('filter-series');
    const price = document.getElementById('filter-price');
    const available = document.getElementById('filter-available');
    const sort = document.getElementById('catalog-sort');
    const grid = document.querySelector('.catalog-grid');
    const cards = Array.from(grid.querySelectorAll('.catalog-card'));
    const params = new URLSearchParams(location.search);
    query.value = (params.get('q') || '').slice(0, 120);
    const requestedCategory = params.get('category') || '';
    if (Array.from(category.options).some(function (option) { return option.value === requestedCategory; })) category.value = requestedCategory;
    function applyFilters() {
      let count = 0;
      const q = normalize(query.value);
      cards.forEach(function (card) {
        const show = (!q || normalize(card.textContent).includes(q)) && (!category.value || card.dataset.category === category.value) && (!series.value || card.dataset.series === series.value) && (!price.value || Number(card.dataset.price) <= Number(price.value)) && (!available.checked || card.dataset.available === 'true');
        card.hidden = !show;
        if (show) count += 1;
      });
      const ordered = cards.slice().sort(function (a, b) {
        if (sort.value === 'asc') return Number(a.dataset.price) - Number(b.dataset.price);
        if (sort.value === 'desc') return Number(b.dataset.price) - Number(a.dataset.price);
        return Number(a.dataset.product) - Number(b.dataset.product);
      });
      ordered.forEach(function (card) { grid.appendChild(card); });
      document.getElementById('catalog-count').textContent = count + ' trang ph\u1ee5c cosplay \u0111\u01b0\u1ee3c t\u00ecm th\u1ea5y';
      document.getElementById('catalog-empty').hidden = count !== 0;
    }
    [query, category, series, price, available, sort].forEach(function (input) { input.addEventListener('input', applyFilters); input.addEventListener('change', applyFilters); });
    document.getElementById('catalog-search').addEventListener('submit', function (event) { event.preventDefault(); applyFilters(); });
    document.getElementById('clear-filters').addEventListener('click', function () { query.value = ''; category.value = ''; series.value = ''; price.value = ''; available.checked = false; sort.value = 'default'; applyFilters(); });
    applyFilters();
  }
})();
