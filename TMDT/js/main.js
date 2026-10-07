/* ===== TMDT - Clarity Cosplay Rental : JS dùng chung ===== */
(function () {
  'use strict';
  var page = document.body.getAttribute('data-page') || '';
  var group = document.body.getAttribute('data-group') || '';

  /* 1. Đánh dấu mục sidebar đang mở + bấm cả hàng để chuyển trang */
  document.querySelectorAll('.sidebar-item').forEach(function (item) {
    var a = item.querySelector('a[href]');
    if (!a) return;
    if (a.getAttribute('href') === page + '.html') item.classList.add('is-active');
    item.addEventListener('click', function (e) { if (e.target !== a) window.location.href = a.getAttribute('href'); });
  });

  /* 2. Hiện / ẩn mật khẩu */
  document.querySelectorAll('.js-toggle-pass').forEach(function (t) {
    t.addEventListener('click', function () {
      var scope = t.closest('.input') || t.parentElement;
      var input = scope && scope.querySelector('input');
      if (!input) return;
      var show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      t.textContent = show ? 'Ẩn' : 'Hiện';
    });
  });

  /* 3. Đăng nhập: kiểm tra form rồi chuyển trang theo vai trò */
  if (page === 'dang-nhap' || page === 'back-office-dang-nhap') {
    var btns = document.querySelectorAll('.btn-primary');
    var btn = btns[btns.length - 1];
    var err = document.querySelector('.js-login-error');
    if (btn) btn.addEventListener('click', function (ev) {
      ev.stopImmediatePropagation();
      var inputs = document.querySelectorAll('input[type=text],input[type=email],input[type=password]');
      var ok = inputs.length >= 2 && inputs[0].value.trim() && inputs[1].value.length >= 6;
      if (!ok) { if (err) err.hidden = false; if (inputs[0]) inputs[0].focus(); return; }
      if (err) err.hidden = true;
      var v = inputs[0].value.toLowerCase();
      localStorage.setItem('tmdt_user', inputs[0].value);
      if (page === 'back-office-dang-nhap') window.location.href = v.indexOf('admin') > -1 ? 'admin-nguoi-dung.html' : 'staff-san-pham-cho-kiem-duyet.html';
      else if (v.indexOf('shop') > -1 || v.indexOf('chu') > -1) window.location.href = 'chu-cho-thue-tong-quan.html';
      else window.location.href = 'tai-khoan-cua-toi.html';
    }, true);
  }

  /* 4. Nút bấm: lùi trang / sang trang chi tiết / bước kế tiếp */
  var NEXT = {
    'dang-ky': 'dang-nhap.html', 'quen-mat-khau-va-dang-xuat': 'dang-nhap.html',
    'checkout-dat-thue-thanh-toan': 'thanh-toan-thanh-cong.html', 'thanh-toan-that-bai': 'checkout-dat-thue-thanh-toan.html',
    'thanh-toan-thanh-cong': 'don-thue-cua-toi.html', 'dang-trang-phuc': 'chu-cho-thue-san-pham.html',
    'cap-nhat-trang-phuc': 'chu-cho-thue-san-pham.html', 'staff-chi-tiet-kiem-duyet': 'staff-san-pham-cho-kiem-duyet.html',
    'staff-xu-ly-bao-cao-vi-pham': 'staff-kiem-tra-bao-cao-vi-pham.html', 'staff-chi-tiet-tranh-chap': 'staff-danh-sach-tranh-chap.html',
    'admin-chi-tiet-tranh-chap': 'admin-danh-sach-tranh-chap.html', 'kiem-tra-tinh-trang-sau-tra': 'chu-cho-thue-don-thue.html'
  };
  var DETAIL = {
    'danh-sach-tim-kiem-loc-cosplay': 'chi-tiet-san-pham.html', 'don-thue-cua-toi': 'chi-tiet-don-thue.html',
    'chu-cho-thue-don-thue': 'chu-cho-thue-chi-tiet-don.html', 'chu-cho-thue-san-pham': 'cap-nhat-trang-phuc.html',
    'staff-san-pham-cho-kiem-duyet': 'staff-chi-tiet-kiem-duyet.html', 'staff-kiem-tra-bao-cao-vi-pham': 'staff-xu-ly-bao-cao-vi-pham.html',
    'staff-danh-sach-tranh-chap': 'staff-chi-tiet-tranh-chap.html', 'staff-theo-doi-don-thue': 'staff-chi-tiet-theo-doi-don.html',
    'admin-danh-sach-tranh-chap': 'admin-chi-tiet-tranh-chap.html', 'admin-san-pham': 'admin-san-pham-xoa-vi-pham.html',
    'admin-don-thue-giao-dich': 'admin-can-thiep-don-thue.html'
  };
  document.querySelectorAll('.btn:not(a[href])').forEach(function (b) {
    var label = (b.textContent || '').trim().toLowerCase();
    b.setAttribute('role', 'button'); b.tabIndex = 0;
    var go = function () {
      if (/^(quay lại|hủy|đóng|bỏ qua)/.test(label)) { history.length > 1 ? history.back() : (window.location.href = '../index.html'); return; }
      if (/đăng trang phục mới/.test(label)) { window.location.href = 'dang-trang-phuc.html'; return; }
      if (/chi tiết|xử lý|kiểm tra|^xem|^sửa|chỉnh sửa/.test(label) && DETAIL[page]) { var target = DETAIL[page]; var productEl = b.closest('[data-product]'); if (page === 'danh-sach-tim-kiem-loc-cosplay' && productEl) target += '?product=' + encodeURIComponent(productEl.getAttribute('data-product')); window.location.href = target; return; }
      if (NEXT[page] && b.classList.contains('btn-primary')) { window.location.href = NEXT[page]; return; }
      if (b.classList.contains('btn-primary')) flash('Đã ghi nhận ✓');
    };
    b.addEventListener('click', go);
    b.addEventListener('keydown', function (e) { if (e.key === 'Enter') go(); });
  });
  function flash(msg) {
    var t = document.createElement('div');
    t.textContent = msg;
    t.style.cssText = 'position:fixed;right:24px;bottom:24px;background:#2e2a25;color:#faf9f6;padding:12px 18px;border-radius:6px;font:600 13px Inter,sans-serif;z-index:99';
    document.body.appendChild(t); setTimeout(function () { t.remove(); }, 1800);
  }

  /* 5. Ô tìm kiếm: lọc dòng bảng; Enter ở header => trang tìm kiếm */
  document.querySelectorAll('input').forEach(function (inp) {
    inp.addEventListener('input', function () {
      var q = inp.value.trim().toLowerCase();
      document.querySelectorAll('.table-row').forEach(function (r) { r.style.display = !q || r.textContent.toLowerCase().indexOf(q) > -1 ? '' : 'none'; });
    });
    inp.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && inp.closest('.site-header') && group === 'customer') window.location.href = 'danh-sach-tim-kiem-loc-cosplay.html';
    });
  });

  /* 6. Chip / tab lọc */
  document.querySelectorAll('.chip').forEach(function (c) {
    c.addEventListener('click', function () {
      c.parentElement.querySelectorAll('.chip').forEach(function (x) { x.classList.remove('is-active'); });
      c.classList.add('is-active');
    });
  });

  /* 7. Avatar ở header về trang cá nhân */
  document.querySelectorAll('.site-header .avatar').forEach(function (a) {
    a.classList.add('is-clickable');
    a.addEventListener('click', function () { window.location.href = group === 'owner' ? 'chu-cho-thue-tong-quan.html' : 'tai-khoan-cua-toi.html'; });
  });
})();
