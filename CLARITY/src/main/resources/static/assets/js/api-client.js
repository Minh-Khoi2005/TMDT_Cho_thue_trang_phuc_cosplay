/* API/session client. Never stores passwords or auth tokens in browser storage. */
(function () {
  'use strict';
  const messages=window.CLARITY_MESSAGES, statusLabels=window.CLARITY_STATUSES, paymentLabels=window.CLARITY_PAYMENTS;
  let csrfToken=null, csrfPending=null, user=null, noticeTimer;
  class ApiError extends Error {
    constructor(code,status) { super(messages[code] || messages.SERVER_ERROR);this.code=code;this.status=status || 0; }
  }
  async function raw(path,options) {
    if(location.protocol==='file:') throw new ApiError('WRONG_ORIGIN');
    const controller=new AbortController(), timer=setTimeout(()=>controller.abort(),20000);
    try {
      const response=await fetch('/api'+path,Object.assign({credentials:'same-origin',cache:'no-store',signal:controller.signal},options));
      if(!(response.headers.get('content-type') || '').includes('application/json')) throw new ApiError('WRONG_ORIGIN',response.status);
      const data=await response.json();
      if(!response.ok) throw new ApiError(data.code || 'SERVER_ERROR',response.status);
      return data;
    }catch(error) {if(error instanceof ApiError)throw error;throw new ApiError('NETWORK_ERROR');}
    finally{clearTimeout(timer);}
  }
  async function csrf() {
    if(csrfToken)return csrfToken;
    if(!csrfPending)csrfPending=raw('/auth/csrf').then(token=>{csrfToken=token;return token;}).finally(()=>{csrfPending=null;});
    return csrfPending;
  }
  async function api(path,options) {
    const config=Object.assign({},options || {}),method=(config.method || 'GET').toUpperCase();
    config.headers=Object.assign({},config.headers || {});
    if(!['GET','HEAD','OPTIONS'].includes(method)){const token=await csrf();config.headers[token.headerName]=token.token;}
    if(config.body && !(config.body instanceof URLSearchParams) && typeof config.body!=='string'){
      config.headers['Content-Type']='application/json';config.body=JSON.stringify(config.body);
    }
    try{return await raw(path,config);}catch(error){if(error.code==='CSRF_INVALID')csrfToken=null;throw error;}
  }
  function escape(value){return String(value==null?'':value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
  function money(value){return new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:0}).format(Number(value || 0));}
  function date(value){if(!value)return '';const p=String(value).slice(0,10).split('-');return p.length===3?p[2]+'/'+p[1]+'/'+p[0]:String(value);}
  function time(value){return value?new Intl.DateTimeFormat('vi-VN',{dateStyle:'short',timeStyle:'short',timeZone:'Asia/Ho_Chi_Minh'}).format(new Date(value)):'';}
  function addDays(value,amount){const d=new Date(value+'T00:00:00Z');d.setUTCDate(d.getUTCDate()+amount);return d.toISOString().slice(0,10);}
  function image(value){return /^\/assets\/img\/[a-zA-Z0-9_./%-]+$/.test(value || '')?value:'/assets/img/favicon.svg';}
  function notify(message){
    let node=document.querySelector('.review-toast');
    if(!node){node=document.createElement('div');node.className='review-toast';node.setAttribute('role','status');document.body.appendChild(node);}
    node.textContent=message;node.hidden=false;clearTimeout(noticeTimer);noticeTimer=setTimeout(()=>{node.hidden=true;},6500);
  }
  function showError(error,id='page-error'){const node=document.getElementById(id);if(node){node.textContent=error.message || messages.SERVER_ERROR;node.hidden=false;}else notify(error.message || messages.SERVER_ERROR);}
  function clearError(id='page-error'){const node=document.getElementById(id);if(node)node.hidden=true;}
  function goLogin(next){location.href='/pages/login.html?next='+encodeURIComponent(next || location.pathname+location.search);}
  function homeFor(role){return ({CUSTOMER:'/pages/products.html',OWNER:'/pages/owner-dashboard.html',ADMIN:'/pages/admin-dashboard.html',STAFF:'/pages/staff-rentals.html'})[role] || '/index.html';}
  function safeNext(value,role){
    try{const next=new URL(value,location.origin);if(next.origin===location.origin && /^\/(index\.html|pages\/[a-z0-9-]+\.html)$/.test(next.pathname) && !/login|register|forgot-password/.test(next.pathname))return next.pathname+next.search;}catch(ignore){}
    return homeFor(role);
  }
  async function cartBadge(cart){
    if(!user || user.role!=='CUSTOMER')return;
    try{const value=cart || await api('/cart');document.querySelectorAll('[data-cart-count]').forEach(n=>{n.textContent=String(value.units);});}
    catch(error){if(error.status!==401)notify(error.message);}
  }
  function renderSession(){
    const e=escape;
    document.querySelectorAll('.review-actions').forEach(node=>{
      if(!user)node.innerHTML='<a class="live-basket" href="/pages/rental-checkout.html">Gi\u1ecf h\u00e0ng</a><a href="/pages/login.html">\u0110\u0103ng nh\u1eadp</a>';
      else node.innerHTML=(user.role==='CUSTOMER'?'<a class="live-basket" href="/pages/rental-checkout.html">Gi\u1ecf <span class="live-basket-count" data-cart-count>0</span></a><a class="live-nav-orders" href="/pages/my-rentals.html">\u0110\u01a1n thu\u00ea</a>':'<a href="'+homeFor(user.role)+'">Khu v\u1ef1c '+e(user.role)+'</a>')+'<a class="live-nav-name" href="/pages/account.html" title="'+e(user.name)+'">'+e(user.name)+'</a><button type="button" class="live-logout" data-live-logout>\u0110\u0103ng xu\u1ea5t</button>';
    });
  }
  const sessionPromise=api('/auth/me').then(data=>{user=data.user;renderSession();return user;}).catch(error=>{if(error.status!==401)notify(error.message);user=null;renderSession();return null;});
  const configPromise=api('/config');configPromise.catch(error=>{if(document.body.dataset.live==='true')notify(error.message);});
  sessionPromise.then(()=>cartBadge());
  document.addEventListener('click',async event=>{
    const button=event.target.closest('[data-live-logout]');if(!button)return;
    event.preventDefault();event.stopImmediatePropagation();button.disabled=true;
    try{await api('/auth/logout',{method:'POST'});csrfToken=null;user=null;sessionStorage.removeItem('clarity.checkout.pending');location.href='/pages/login.html?loggedout=1';}
    catch(error){notify(error.message);button.disabled=false;}
  },true);
  window.Clarity={api,csrf,escape,money,date,time,addDays,image,notify,showError,clearError,goLogin,safeNext,homeFor,cartBadge,sessionPromise,configPromise,messages,statusLabels,paymentLabels,ApiError,
    get user(){return user;},setUser(value){user=value;csrfToken=null;renderSession();}};
})();
