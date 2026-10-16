/* CLARITY live page integrations. */
(function () {
  'use strict';
  const C=window.Clarity,e=C.escape,m=C.money,$=id=>document.getElementById(id);
  const page=document.body.dataset.page || '',params=new URLSearchParams(location.search);
  const badge=status=>'<span class="live-pill '+e(status)+'">'+e(C.statusLabels[status] || status)+'</span>';
  const normalize=value=>String(value).normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/\u0111/g,'d').replace(/\u0110/g,'D').toLowerCase();
  async function customer(){const u=await C.sessionPromise;if(!u){C.goLogin();return null;}if(u.role!=='CUSTOMER'){location.href='/pages/access-denied.html';return null;}return u;}
  function fail(error,id){if(error.status===401){C.goLogin();return;}C.showError(error,id);}
  function card(p){
    return '<article class="catalog-card" data-product="'+p.id+'"><img src="'+C.image(p.imageUrl)+'" alt="'+e(p.name)+'" loading="lazy" width="300" height="340"><div class="catalog-card__body"><p class="catalog-series">'+e(p.series)+'</p><h2>'+e(p.name)+'</h2><p class="catalog-meta">'+p.variants.map(v=>e(v.label)).join(' / ')+'<br>Shop: '+e(p.shopName)+'</p><p class="catalog-price">'+m(p.dailyPrice)+' <small>/ ng\u00e0y</small></p><p class="catalog-meta">C\u1ecdc: '+m(p.depositPerUnit)+' / b\u1ed9</p><a class="review-button" href="/pages/product-detail.html?product='+p.id+'">Ch\u1ecdn l\u1ecbch thu\u00ea</a></div></article>';
  }
  async function login(){
    const form=$('login-form'),message=form.querySelector('.form-message'),button=form.querySelector('[type="submit"]'),google=$('google-login-link');
    const next=params.get('next');
    if(google && next && next.startsWith('/') && !next.startsWith('//') && !next.includes('://')) google.href='/api/auth/google/start?next='+encodeURIComponent(next);
    if(params.get('loggedout')==='1'){message.textContent='\u0110\u00e3 \u0111\u0103ng xu\u1ea5t v\u00e0 k\u1ebft th\u00fac phi\u00ean tr\u00ean m\u00e1y ch\u1ee7.';message.hidden=false;}
    if(params.get('google')==='not-configured'){message.textContent='\u0110\u0103ng nh\u1eadp Google ch\u01b0a \u0111\u01b0\u1ee3c c\u1ea5u h\u00ecnh tr\u00ean m\u00e1y n\u00e0y.';message.hidden=false;}
    else if(params.get('google')==='failed'){message.textContent='Kh\u00f4ng th\u1ec3 \u0111\u0103ng nh\u1eadp b\u1eb1ng Google. Vui l\u00f2ng th\u1eed l\u1ea1i.';message.hidden=false;}
    form.addEventListener('submit',async event=>{
      event.preventDefault();if(!form.reportValidity())return;button.disabled=true;message.hidden=true;
      try{
        const result=await C.api('/auth/login',{method:'POST',body:new URLSearchParams({identifier:form.identifier.value.trim(),password:form.password.value})});
        form.password.value='';C.setUser(result.user);
        location.href=C.safeNext(params.get('next'),result.user.role);
      }catch(error){message.textContent=error.message;message.hidden=false;button.disabled=false;}
    });
  }

  function validPassword(value){return value.length>=8 && /[^A-Za-z0-9]/.test(value);}
  async function register(){
    const form=$('register-form'),message=form.querySelector('.form-message'),button=form.querySelector('[type="submit"]'),resend=$('resend-verification');
    let registeredEmail='';
    form.addEventListener('submit',async event=>{
      event.preventDefault();message.hidden=true;
      const password=form.password.value,confirm=form.confirmPassword.value;
      if(!form.reportValidity())return;
      if(password!==confirm){message.textContent='Mật khẩu xác nhận chưa khớp.';message.hidden=false;form.confirmPassword.focus();return;}
      if(!validPassword(password)){message.textContent=C.messages.WEAK_PASSWORD;message.hidden=false;form.password.focus();return;}
      button.disabled=true;
      try{
        const result=await C.api('/auth/register',{method:'POST',body:{
          displayName:form.displayName.value.trim(),email:form.email.value.trim(),phone:form.phone.value.trim(),password,
          role:form.role.value,acceptTerms:form.acceptTerms.checked
        }});
        registeredEmail=result.email;form.password.value='';form.confirmPassword.value='';
        message.textContent=result.mailSent?'Đăng ký thành công. CLARITY đã gửi liên kết xác nhận đến '+result.email+'. Hãy kiểm tra hộp thư và thư rác.':'Tài khoản đã được tạo nhưng chưa gửi được email. Hãy cấu hình Gmail trong AppConstants.java rồi bấm “Gửi lại email xác nhận”.';
        message.hidden=false;$('verification-resend').hidden=false;button.disabled=true;
      }catch(error){
        message.textContent=error.message;message.hidden=false;button.disabled=false;
        if(error.code==='EMAIL_ALREADY_REGISTERED'){registeredEmail=form.email.value.trim();$('verification-resend').hidden=false;}
      }
    });
    resend.addEventListener('click',async()=>{
      const email=registeredEmail || form.email.value.trim();if(!email){message.textContent='Nhập email cần gửi lại liên kết xác nhận.';message.hidden=false;return;}
      resend.disabled=true;message.hidden=true;
      try{const result=await C.api('/auth/resend-verification',{method:'POST',body:{email}});message.textContent=result.mailConfigured?'Nếu tài khoản đang chờ xác nhận, CLARITY đã xử lý yêu cầu gửi lại email. Hãy kiểm tra hộp thư và thư rác.':'SMTP chưa được cấu hình. Hãy điền Gmail và App Password trong AppConstants.java rồi chạy lại Spring Boot.';message.hidden=false;}
      catch(error){message.textContent=error.message;message.hidden=false;}finally{setTimeout(()=>{resend.disabled=false;},1000);}
    });
  }
  async function forgotPassword(){
    const form=$('forgot-password-form'),message=form.querySelector('.form-message'),button=form.querySelector('[type="submit"]');
    form.addEventListener('submit',async event=>{
      event.preventDefault();if(!form.reportValidity())return;button.disabled=true;message.hidden=true;
      try{const result=await C.api('/auth/forgot-password',{method:'POST',body:{email:form.email.value.trim()}});message.textContent=result.mailConfigured?'Nếu email tồn tại và tài khoản đã được xác nhận, CLARITY sẽ gửi liên kết đặt lại mật khẩu. Hãy kiểm tra cả thư rác.':'SMTP chưa được cấu hình. Hãy điền Gmail và App Password trong AppConstants.java rồi chạy lại Spring Boot.';message.hidden=false;}
      catch(error){message.textContent=error.message;message.hidden=false;}finally{button.disabled=false;}
    });
  }
  async function verifyEmail(){
    const status=$('verify-email-status'),button=$('verify-login'),token=params.get('token');
    if(!token){status.textContent='Liên kết xác nhận không hợp lệ.';C.showError(new C.ApiError('INVALID_OR_EXPIRED_TOKEN'));return;}
    try{await C.api('/auth/verify-email',{method:'POST',body:{token}});status.textContent='Email đã được xác nhận thành công. Bạn có thể đăng nhập CLARITY ngay bây giờ.';button.hidden=false;}
    catch(error){status.textContent='Không thể xác nhận email.';C.showError(error);}
  }
  async function resetPassword(){
    const form=$('reset-password-form'),message=form.querySelector('.form-message'),button=form.querySelector('[type="submit"]'),token=params.get('token');
    if(!token){message.textContent=C.messages.INVALID_OR_EXPIRED_TOKEN;message.hidden=false;button.disabled=true;return;}
    form.addEventListener('submit',async event=>{
      event.preventDefault();if(!form.reportValidity())return;
      if(form.password.value!==form.confirmPassword.value){message.textContent='Mật khẩu xác nhận chưa khớp.';message.hidden=false;return;}
      if(!validPassword(form.password.value)){message.textContent=C.messages.WEAK_PASSWORD;message.hidden=false;return;}
      button.disabled=true;message.hidden=true;
      try{await C.api('/auth/reset-password',{method:'POST',body:{token,password:form.password.value}});form.reset();message.textContent='Đã đổi mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới.';message.hidden=false;button.textContent='Đã đổi mật khẩu';}
      catch(error){message.textContent=error.message;message.hidden=false;button.disabled=false;}
    });
  }
  async function catalog(){
    const query=$('catalog-query'),category=$('filter-category'),series=$('filter-series'),price=$('filter-price'),available=$('filter-available'),sort=$('catalog-sort');
    query.value=(params.get('q') || '').slice(0,120);
    if(Array.from(category.options).some(o=>o.value===params.get('category')))category.value=params.get('category');
    const result=await C.api('/products'),products=result.items;
    const allSeries=Array.from(new Set(products.map(p=>p.series))).sort();
    series.innerHTML='<option value="">T\u1ea5t c\u1ea3 series</option>'+allSeries.map(s=>'<option>'+e(s)+'</option>').join('');
    function render(){
      const term=normalize(query.value.trim());
      const list=products.filter(p=>(!term || normalize(p.name+' '+p.series+' '+p.shopName).includes(term)) && (!category.value || p.category===category.value) && (!series.value || p.series===series.value) && (!price.value || Number(p.dailyPrice)<=Number(price.value)) && (!available.checked || p.variants.some(v=>v.stockQuantity>0)));
      list.sort((a,b)=>sort.value==='asc'?Number(a.dailyPrice)-Number(b.dailyPrice):sort.value==='desc'?Number(b.dailyPrice)-Number(a.dailyPrice):a.id-b.id);
      document.querySelector('.catalog-grid').innerHTML=list.map(card).join('');
      $('catalog-count').textContent=list.length+' trang ph\u1ee5c t\u1eeb database';
      if($('catalog-empty'))$('catalog-empty').hidden=list.length!==0;
    }
    [query,category,series,price,available,sort].forEach(n=>{n.addEventListener('input',render);n.addEventListener('change',render);});
    $('catalog-search').addEventListener('submit',event=>{event.preventDefault();render();});
    $('clear-filters').addEventListener('click',()=>{query.value='';category.value='';series.value='';price.value='';available.checked=false;sort.value='default';render();});
    render();
  }
  async function product(){
    const id=params.get('product') || '1',form=$('rental-form'),controls=Array.from(form.querySelectorAll('input,select'));
    let currentQuote=null,sequence=0,timer,busy=false;
    const disable=value=>{ $('add-to-cart').disabled=value;$('rent-now').disabled=value; };
    if(!/^[1-9][0-9]*$/.test(id))throw new C.ApiError('NOT_FOUND',404);
    const [p,config]=await Promise.all([C.api('/products/'+id),C.configPromise]);
    document.title=p.name+' | CLARITY Cosplay Rental';
    const text=(id,value)=>{const n=$(id);if(n)n.textContent=value;};
    text('product-name',p.name);text('breadcrumb-product',p.name);text('product-series',p.series);text('product-price',m(p.dailyPrice));text('product-deposit',m(p.depositPerUnit));text('product-owner',p.shopName);text('product-area',p.city);text('product-description',p.description);text('product-size',p.variants.map(v=>v.label).join(' / '));
    $('product-image').src=C.image(p.imageUrl);$('product-image').alt=p.name;
    const thumbs=document.querySelector('.gallery__thumbs');
    if(p.images.length>1){thumbs.hidden=false;thumbs.innerHTML=p.images.map(i=>'<button type="button" data-image="'+C.image(i.url)+'" aria-label="'+e(i.alt)+'"><img src="'+C.image(i.url)+'" alt="'+e(i.alt)+'"></button>').join('');thumbs.addEventListener('click',event=>{const b=event.target.closest('[data-image]');if(b)$('product-image').src=b.dataset.image;});}
    $('rental-variant').innerHTML=p.variants.map(v=>'<option value="'+v.id+'">'+e(v.label)+' ('+v.stockQuantity+' b\u1ed9)</option>').join('');
    controls.forEach(n=>{n.disabled=false;});
    const start=$('rental-start'),end=$('rental-end'),quantity=$('rental-quantity');
    start.min=end.min=config.today;start.max=end.max=C.addDays(config.today,config.advanceDays);
    start.value=/^\d{4}-\d{2}-\d{2}$/.test(params.get('startDate') || '')?params.get('startDate'):C.addDays(config.today,1);
    end.value=/^\d{4}-\d{2}-\d{2}$/.test(params.get('endDate') || '')?params.get('endDate'):C.addDays(start.value,1);
    const selected=params.get('variantId');if(p.variants.some(v=>String(v.id)===selected))$('rental-variant').value=selected;
    if(/^[1-9]$|^10$/.test(params.get('quantity') || ''))quantity.value=params.get('quantity');
    text('rental-rules','T\u00ednh c\u1ea3 ng\u00e0y nh\u1eadn v\u00e0 tr\u1ea3; t\u1ed1i \u0111a '+config.maxRentalDays+' ng\u00e0y. Ch\u1eeba '+config.cleaningDays+' ng\u00e0y v\u1ec7 sinh sau khi tr\u1ea3 (kh\u00f4ng t\u00ednh ph\u00ed).');
    function values(){return {variantId:Number($('rental-variant').value),quantity:Number(quantity.value),startDate:start.value,endDate:end.value};}
    async function quote(){
      const ticket=++sequence;currentQuote=null;disable(true);C.clearError('product-error');
      if(!form.checkValidity()){$('rental-quote').textContent='Vui l\u00f2ng ch\u1ecdn k\u00edch c\u1ee1, ng\u00e0y v\u00e0 s\u1ed1 l\u01b0\u1ee3ng h\u1ee3p l\u1ec7.';return;}
      $('rental-quote').textContent='\u0110ang ki\u1ec3m tra l\u1ecbch v\u00e0 t\u00ednh ti\u1ec1n...';
      try{
        const result=await C.api('/products/'+id+'/availability?'+new URLSearchParams(values()));if(ticket!==sequence)return;
        currentQuote=result;
        $('product-status').textContent=result.available?'C\u00f2n '+result.availableQuantity+' b\u1ed9 cho l\u1ecbch \u0111\u00e3 ch\u1ecdn':'Kh\u00f4ng \u0111\u1ee7 b\u1ed9 cho l\u1ecbch n\u00e0y';
        $('rental-quote').innerHTML='<div>'+result.days+' ng\u00e0y \u00d7 '+quantity.value+' b\u1ed9: <b>'+m(result.rentalTotal)+'</b></div><div>Ti\u1ec1n c\u1ecdc: <b>'+m(result.depositTotal)+'</b></div><div>T\u1ed5ng d\u1ef1 ki\u1ebfn: <strong>'+m(result.total)+'</strong></div>';
        if(!result.available)C.showError(new C.ApiError('STOCK_CONFLICT'),'product-error');
        disable(!result.available || busy);
      }catch(error){if(ticket===sequence){C.showError(error,'product-error');$('rental-quote').textContent='Ch\u01b0a c\u00f3 b\u00e1o gi\u00e1 h\u1ee3p l\u1ec7.';}}
    }
    controls.forEach(n=>n.addEventListener('input',()=>{++sequence;currentQuote=null;disable(true);clearTimeout(timer);timer=setTimeout(quote,220);}));
    async function add(checkout){
      if(busy || !currentQuote || !currentQuote.available || !form.reportValidity())return;
      const u=await C.sessionPromise;
      if(!u){const query=new URLSearchParams(Object.assign({product:id},values()));C.goLogin('/pages/product-detail.html?'+query);return;}
      if(u.role!=='CUSTOMER'){C.showError(new C.ApiError('FORBIDDEN'),'product-error');return;}
      busy=true;disable(true);C.clearError('product-error');
      try{
        const cart=await C.api('/cart/items',{method:'POST',body:values()});C.cartBadge(cart);
        if(checkout)location.href='/pages/rental-checkout.html';else C.notify('\u0110\u00e3 l\u01b0u trang ph\u1ee5c v\u00e0o gi\u1ecf h\u00e0ng.');
      }catch(error){fail(error,'product-error');}finally{busy=false;disable(!currentQuote || !currentQuote.available);}
    }
    form.addEventListener('submit',event=>{event.preventDefault();add(false);});$('rent-now').addEventListener('click',()=>add(true));
    const tabs=Array.from(document.querySelectorAll('[data-tab]'));
    tabs.forEach((tab,index)=>{
      tab.setAttribute('role','tab');tab.setAttribute('aria-selected',String(index===0));tab.tabIndex=index===0?0:-1;
      const activate=()=>{tabs.forEach(t=>{t.classList.toggle('is-active',t===tab);t.setAttribute('aria-selected',String(t===tab));t.tabIndex=t===tab?0:-1;});document.querySelectorAll('[data-panel]').forEach(panel=>{const active=panel.dataset.panel===tab.dataset.tab;panel.classList.toggle('is-active',active);panel.hidden=!active;});};
      tab.addEventListener('click',activate);tab.addEventListener('keydown',event=>{if(['ArrowRight','ArrowLeft'].includes(event.key)){event.preventDefault();const target=tabs[(index+(event.key==='ArrowRight'?1:tabs.length-1))%tabs.length];target.click();target.focus();}});
    });
    await quote();
  }
  async function checkout(){
    const u=await customer();if(!u)return;
    const config=await C.configPromise,form=$('checkout-form'),container=$('cart-items'),drafts=new Map();
    let cart=null,mutating=false,submitting=false;
    $('recipient-name').value=u.name;$('recipient-phone').value=u.phone;
    $('hold-note').textContent='\u0110\u01a1n ch\u1edd x\u00e1c nh\u1eadn gi\u1eef l\u1ecbch t\u1ea1m '+config.holdHours+' gi\u1edd. H\u1ebft h\u1ea1n th\u00ec gi\u1ea3i ph\u00f3ng l\u1ecbch.';
    function syncSubmit(){
      $('create-order').disabled=!cart || !cart.canCheckout || drafts.size>0 || mutating || submitting;
      const issues=cart?cart.problems.map(code=>C.messages[code] || code):[];
      if(drafts.size)issues.push('C\u00f3 thay \u0111\u1ed5i ch\u01b0a l\u01b0u. B\u1ea5m "L\u01b0u thay \u0111\u1ed5i" \u1edf c\u00e1c d\u00f2ng \u0111\u00e3 s\u1eeda tr\u01b0\u1edbc khi \u0111\u1eb7t thu\u00ea.');
      $('cart-problems').textContent=issues.join(' ');$('cart-problems').hidden=!issues.length;
    }
    function render(value){
      cart=value;C.cartBadge(cart);$('cart-loading').hidden=true;$('cart-empty').hidden=cart.items.length!==0;$('checkout-layout').hidden=cart.items.length===0;
      $('cart-units').textContent=cart.units+' b\u1ed9';$('summary-rental').textContent=m(cart.rentalTotal);$('summary-deposit').textContent=m(cart.depositTotal);$('summary-total').textContent=m(cart.total);
      container.innerHTML=cart.items.map(item=>{
        const d=drafts.get(item.id) || item;
        return '<article class="live-cart-line" data-item="'+item.id+'"><img src="'+C.image(item.imageUrl)+'" alt="'+e(item.productName)+'"><div><h3><a href="product-detail.html?product='+item.productId+'">'+e(item.productName)+'</a></h3><p class="live-muted">'+e(item.variantLabel)+'<br>'+e(item.shopName)+' \u00b7 '+e(item.city)+'</p><p class="live-line-money">'+m(item.dailyPrice)+' \u00d7 '+item.rentalDays+' ng\u00e0y \u00d7 '+item.quantity+' b\u1ed9 = <b>'+m(item.rentalTotal)+'</b><br>C\u1ecdc: '+m(item.depositTotal)+'</p><form data-cart-editor="'+item.id+'"><div class="live-cart-editor"><div><label for="quantity-'+item.id+'">S\u1ed1 l\u01b0\u1ee3ng</label><input class="review-input" id="quantity-'+item.id+'" name="quantity" type="number" min="1" max="10" value="'+e(d.quantity)+'" required></div><div><label for="start-'+item.id+'">Ng\u00e0y nh\u1eadn</label><input class="review-input" id="start-'+item.id+'" name="startDate" type="date" min="'+config.today+'" max="'+C.addDays(config.today,config.advanceDays)+'" value="'+e(d.startDate)+'" required></div><div><label for="end-'+item.id+'">Ng\u00e0y tr\u1ea3</label><input class="review-input" id="end-'+item.id+'" name="endDate" type="date" min="'+config.today+'" max="'+C.addDays(config.today,config.advanceDays)+'" value="'+e(d.endDate)+'" required></div></div><div class="live-line-buttons"><button class="review-button" type="submit">L\u01b0u thay \u0111\u1ed5i</button><button class="review-button live-remove" type="button" data-remove="'+item.id+'">X\u00f3a</button></div></form></div></article>';
      }).join('');syncSubmit();
    }
    async function reload(){render(await C.api('/cart'));}
    container.addEventListener('input',event=>{
      const f=event.target.closest('form[data-cart-editor]');if(!f)return;
      drafts.set(Number(f.dataset.cartEditor),{quantity:f.quantity.value,startDate:f.startDate.value,endDate:f.endDate.value});syncSubmit();
    });
    async function mutation(action,id,body){
      if(mutating || submitting)return;mutating=true;syncSubmit();C.clearError();
      try{
        const result=await C.api('/cart/items/'+id+(action==='DELETE'?'?version='+cart.version:''),{method:action,body:action==='DELETE'?undefined:body});
        drafts.delete(id);render(result);C.notify(action==='DELETE'?'\u0110\u00e3 x\u00f3a kh\u1ecfi gi\u1ecf.':'\u0110\u00e3 l\u01b0u l\u1ecbch v\u00e0 s\u1ed1 l\u01b0\u1ee3ng m\u1edbi.');
      }catch(error){
        if(['CART_CHANGED','CONCURRENT_CHANGE','PRICE_CHANGED'].includes(error.code)){drafts.clear();try{await reload();}catch(second){C.showError(second);}}
        fail(error);
      }finally{mutating=false;syncSubmit();}
    }
    container.addEventListener('submit',event=>{
      const f=event.target.closest('form[data-cart-editor]');if(!f)return;event.preventDefault();if(!f.reportValidity())return;
      mutation('PATCH',Number(f.dataset.cartEditor),{cartVersion:cart.version,quantity:Number(f.quantity.value),startDate:f.startDate.value,endDate:f.endDate.value});
    });
    container.addEventListener('click',event=>{const b=event.target.closest('[data-remove]');if(!b)return;if(confirm('X\u00f3a trang ph\u1ee5c n\u00e0y kh\u1ecfi gi\u1ecf h\u00e0ng?'))mutation('DELETE',Number(b.dataset.remove));});
    form.addEventListener('submit',async event=>{
      event.preventDefault();if(submitting || mutating || !cart || !cart.canCheckout || drafts.size || !form.reportValidity())return;
      C.clearError();submitting=true;syncSubmit();
      const body={cartVersion:cart.version,quotedTotal:cart.total,recipientName:$('recipient-name').value.trim(),recipientPhone:$('recipient-phone').value.trim(),note:$('order-note').value.trim(),acceptTerms:$('accept-terms').checked};
      const fingerprint=JSON.stringify({userId:u.id,body});let pending=null;
      try{pending=JSON.parse(sessionStorage.getItem('clarity.checkout.pending'));}catch(ignore){}
      if(!pending || pending.fingerprint!==fingerprint)pending={fingerprint,key:crypto.randomUUID()};
      try{
        sessionStorage.setItem('clarity.checkout.pending',JSON.stringify(pending));
        const order=await C.api('/orders',{method:'POST',headers:{'Idempotency-Key':pending.key},body});
        sessionStorage.removeItem('clarity.checkout.pending');
        location.href='/pages/rental-detail.html?id='+order.id+'&created=1';
      }catch(error){
        if(error.status>=400 && error.status<500 && !['CSRF_INVALID','LOGIN_REQUIRED'].includes(error.code))sessionStorage.removeItem('clarity.checkout.pending');
        if(['CART_CHANGED','PRICE_CHANGED','STOCK_CONFLICT','PRODUCT_UNAVAILABLE','EMPTY_CART','CONCURRENT_CHANGE'].includes(error.code)){try{await reload();}catch(second){C.showError(second);}}
        fail(error);
      }finally{submitting=false;syncSubmit();}
    });
    await reload();
    if(sessionStorage.getItem('clarity.checkout.pending'))C.showError({message:'C\u00f3 y\u00eau c\u1ea7u \u0111\u1eb7t thu\u00ea ch\u01b0a nh\u1eadn \u0111\u01b0\u1ee3c ph\u1ea3n h\u1ed3i. H\u00e3y xem "\u0110\u01a1n thu\u00ea c\u1ee7a t\u00f4i" tr\u01b0\u1edbc khi \u0111\u1eb7t th\u00eam.'});
  }
  async function orders(){
    if(!await customer())return;
    let current=1,totalPages=1,sequence=0;
    async function load(){
      const ticket=++sequence;C.clearError();$('order-count').textContent='\u0110ang t\u1ea3i...';
      try{
        const result=await C.api('/orders?'+new URLSearchParams({page:current,size:10,status:$('order-status').value}));if(ticket!==sequence)return;totalPages=result.totalPages;
        $('order-count').textContent=result.total+' \u0111\u01a1n thu\u00ea';
        $('orders-list').innerHTML=result.items.length?result.items.map(order=>{
          const first=order.items[0];return '<article class="live-order-row"><img src="'+C.image(first?first.imageUrl:'')+'" alt="'+e(first?first.productName:'\u0110\u01a1n thu\u00ea')+'"><div><h3>'+e(order.code)+'</h3><p>'+e(C.time(order.createdAt))+'</p><p>'+order.items.map(i=>e(i.productName)+' \u00d7 '+i.quantity).join('<br>')+'</p><p>'+e(C.paymentLabels[order.paymentStatus] || order.paymentStatus)+'</p></div><div class="live-order-right">'+badge(order.status)+'<b>'+m(order.totalDue)+'</b><a class="review-button" href="rental-detail.html?id='+order.id+'">Xem chi ti\u1ebft</a></div></article>';
        }).join(''):'<div class="live-empty"><h2>Ch\u01b0a c\u00f3 \u0111\u01a1n ph\u00f9 h\u1ee3p</h2><p>\u0110\u01a1n b\u1ea1n t\u1ea1o s\u1ebd xu\u1ea5t hi\u1ec7n t\u1ea1i \u0111\u00e2y. Th\u1eed \u0111\u1ed5i b\u1ed9 l\u1ecdc ho\u1eb7c ch\u1ecdn trang ph\u1ee5c m\u1edbi.</p></div>';
        $('orders-pagination').hidden=totalPages<2;$('page-number').textContent=current+' / '+totalPages;$('previous-page').disabled=current<=1;$('next-page').disabled=current>=totalPages;
      }catch(error){if(ticket===sequence){$('order-count').textContent='Ch\u01b0a t\u1ea3i \u0111\u01b0\u1ee3c \u0111\u01a1n';fail(error);}}
    }
    $('order-status').addEventListener('change',()=>{current=1;load();});$('previous-page').addEventListener('click',()=>{if(current>1){current--;load();}});$('next-page').addEventListener('click',()=>{if(current<totalPages){current++;load();}});
    await load();
  }
  async function orderDetail(){
    if(!await customer())return;
    const id=params.get('id');if(!/^[1-9][0-9]*$/.test(id || ''))throw new C.ApiError('NOT_FOUND',404);
    const o=await C.api('/orders/'+id);$('order-loading').hidden=true;$('order-content').hidden=false;
    $('order-title').textContent=o.code;$('order-created').textContent='T\u1ea1o l\u00fac '+C.time(o.createdAt);
    const line=i=>'<article class="live-detail-item"><img src="'+C.image(i.imageUrl)+'" alt="'+e(i.productName)+'"><div><h3>'+e(i.productName)+'</h3><p>'+e(i.variantLabel)+' \u00b7 '+e(i.shopName)+'</p><p>Nh\u1eadn '+C.date(i.startDate)+' \u2192 Tr\u1ea3 '+C.date(i.endDate)+' ('+i.rentalDays+' ng\u00e0y)</p><p>'+m(i.dailyPrice)+' \u00d7 '+i.rentalDays+' ng\u00e0y \u00d7 '+i.quantity+' b\u1ed9 = <b>'+m(i.rentalTotal)+'</b></p><p>Ti\u1ec1n c\u1ecdc: '+m(i.depositTotal)+'</p></div></article>';
    const isPending=o.status==='PENDING_CONFIRMATION';
    const paymentNote=o.paymentStatus==='UNPAID'?'Ch\u01b0a thanh to\u00e1n.':'Tr\u1ea1ng th\u00e1i thanh to\u00e1n: '+(C.paymentLabels[o.paymentStatus] || o.paymentStatus)+'.';
    $('order-content').innerHTML=(params.get('created')==='1'?'<div class="live-alert success" role="status">\u0110\u01a1n thu\u00ea \u0111\u00e3 \u0111\u01b0\u1ee3c t\u1ea1o th\u00e0nh c\u00f4ng. '+e(paymentNote)+'</div>':'')+
      '<div class="live-detail-grid"><div><section class="live-card"><div class="live-section-title"><h2>Trang ph\u1ee5c trong \u0111\u01a1n</h2>'+badge(o.status)+'</div>'+o.items.map(line).join('')+'</section><section class="live-card"><h2>Th\u00f4ng tin nh\u1eadn</h2><dl class="live-kv"><dt>Ng\u01b0\u1eddi nh\u1eadn</dt><dd>'+e(o.recipientName)+'</dd><dt>\u0110i\u1ec7n tho\u1ea1i</dt><dd>'+e(o.recipientPhone)+'</dd><dt>H\u00ecnh th\u1ee9c</dt><dd>Nh\u1eadn tr\u1ef1c ti\u1ebfp t\u1ea1i t\u1eebng shop</dd><dt>Ghi ch\u00fa</dt><dd>'+e(o.note || 'Kh\u00f4ng c\u00f3')+'</dd></dl></section><section class="live-card"><h2>L\u1ecbch s\u1eed ghi nh\u1eadn</h2><ol class="live-timeline">'+o.events.map(event=>'<li><small>'+e(C.time(event.at))+'</small><p>'+e(event.type==='ORDER_CREATED'?'\u0110\u00e3 t\u1ea1o \u0111\u01a1n v\u00e0 gi\u1eef l\u1ecbch t\u1ea1m.':event.description)+'</p></li>').join('')+'</ol></section></div><aside class="live-card live-summary"><span class="eyebrow">GI\u00c1 TR\u1eca \u0110\u01a0N</span><h2>Th\u00f4ng tin chi ph\u00ed</h2><div class="live-money-row"><span>Ti\u1ec1n thu\u00ea</span><b>'+m(o.rentalTotal)+'</b></div><div class="live-money-row"><span>Ti\u1ec1n c\u1ecdc</span><b>'+m(o.depositTotal)+'</b></div><div class="live-money-row total"><span>T\u1ed5ng</span><strong>'+m(o.totalDue)+'</strong></div><div class="live-money-row"><span>\u0110\u00e3 thu</span><b>'+m(o.paidTotal)+'</b></div><div class="live-alert warning">'+e(paymentNote)+'</div>'+(isPending?'<p class="live-footnote">Gi\u1eef l\u1ecbch \u0111\u1ebfn '+e(C.time(o.holdExpiresAt))+'. Đơn đang chờ shop xác nhận. Lịch thuê được giữ tạm đến thời điểm hiển thị bên trên.</p>':'')+(o.status==='EXPIRED'?'<p class="live-footnote">\u0110\u01a1n h\u1ebft th\u1eddi gian gi\u1eef l\u1ecbch, kh\u00f4ng c\u00f2n gi\u1eef s\u1ed1 l\u01b0\u1ee3ng. S\u1ed1 ti\u1ec1n tr\u00ean l\u00e0 gi\u00e1 tr\u1ecb l\u00fac \u0111\u1eb7t.</p>':'')+'<button type="button" id="print-order" class="review-button full-width live-print">In th\u00f4ng tin \u0111\u01a1n</button></aside></div>';
    $('print-order').addEventListener('click',()=>window.print());
  }
  async function account(){
    const u=await C.sessionPromise;if(!u){C.goLogin();return;}
    const roles={CUSTOMER:'Kh\u00e1ch thu\u00ea',OWNER:'Ch\u1ee7 cho thu\u00ea',STAFF:'Nh\u00e2n vi\u00ean',ADMIN:'Qu\u1ea3n tr\u1ecb vi\u00ean'};
    $('account-info').innerHTML='<h2>'+e(u.name)+'</h2><dl class="live-kv"><dt>Email</dt><dd>'+e(u.email)+'</dd><dt>\u0110i\u1ec7n tho\u1ea1i</dt><dd>'+e(u.phone)+'</dd><dt>Vai tr\u00f2</dt><dd>'+e(roles[u.role] || u.role)+'</dd></dl>';
    if(u.role!=='CUSTOMER')document.querySelectorAll('[data-customer-link]').forEach(a=>{a.hidden=true;});
  }
  async function start(){
    if($('login-form'))await login();
    else if(page==='register')await register();
    else if(page==='forgot-password')await forgotPassword();
    else if(page==='verify-email')await verifyEmail();
    else if(page==='reset-password')await resetPassword();
    else if(page==='products')await catalog();
    else if(page==='product-detail')await product();
    else if(page==='rental-checkout')await checkout();
    else if(page==='my-rentals')await orders();
    else if(page==='rental-detail')await orderDetail();
    else if(page==='account')await account();
    else if($('home-products')){$('home-products').innerHTML=(await C.api('/products')).items.slice(0,8).map(card).join('');}
    else if($('rules-current')){const c=await C.configPromise;$('rules-current').textContent='C\u1ea5u h\u00ecnh hi\u1ec7n h\u00e0nh: thu\u00ea t\u1ed1i \u0111a '+c.maxRentalDays+' ng\u00e0y; \u0111\u1eb7t tr\u01b0\u1edbc '+c.advanceDays+' ng\u00e0y; v\u1ec7 sinh '+c.cleaningDays+' ng\u00e0y; gi\u1eef l\u1ecbch '+c.holdHours+' gi\u1edd. M\u00fai gi\u1edd: '+c.zone+'.';}
  }
  start().catch(error=>{
    ['cart-loading','order-loading'].forEach(id=>{if($(id))$(id).hidden=true;});
    if(page==='product-detail'){
      if($('product-name'))$('product-name').textContent='Kh\u00f4ng th\u1ec3 t\u1ea3i trang ph\u1ee5c';
      if(document.querySelector('.gallery'))document.querySelector('.gallery').hidden=true;
      if($('rental-form'))$('rental-form').querySelectorAll('button,input,select').forEach(n=>{n.disabled=true;});
      fail(error,'product-error');
    }else fail(error);
  });
})();
