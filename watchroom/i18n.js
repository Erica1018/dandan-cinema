'use strict';
window.cinemaLanguage = localStorage.getItem('cinema-language') || (navigator.language.startsWith('zh') ? 'zh' : 'en');
window.t = (zh, en) => window.cinemaLanguage === 'zh' ? zh : en;
window.translatePage = () => {
  document.documentElement.lang = cinemaLanguage === 'zh' ? 'zh-CN' : 'en';
  document.querySelectorAll('[data-zh][data-en]').forEach(el => { el.textContent = t(el.dataset.zh,el.dataset.en); });
  document.querySelectorAll('[data-placeholder-zh]').forEach(el => { el.placeholder=t(el.dataset.placeholderZh,el.dataset.placeholderEn); });
  document.title=t('蛋蛋影院 · 我的片单','Dandan Cinema · My library');
};
