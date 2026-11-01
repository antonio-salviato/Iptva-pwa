// Firehair IPTV Player PWA - Core Logic
let allChannels = [];
let categories = ['Todos', 'Favoritos', 'Histórico'];
let selectedCategory = 'Todos';
let searchQuery = '';
let selectedChannel = null;
let hls = null;

const STORAGE_KEYS = {
  FAVORITES: 'firehair_pwa_favs',
  HISTORY: 'firehair_pwa_history',
  PLAYLIST_URL: 'firehair_pwa_m3u_url',
  XTREAM_CREDS: 'firehair_pwa_xtream'
};

// Initialize App
document.addEventListener('DOMContentLoaded', () => {
  registerServiceWorker();
  initPlayer();
  loadSavedPlaylist();
  setupEventListeners();
});

function registerServiceWorker() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').catch(err => console.log('SW Registration failed:', err));
  }
}

function initPlayer() {
  const video = document.getElementById('videoPlayer');
  if (Hls.isSupported()) {
    hls = new Hls({
      enableWorker: true,
      lowLatencyMode: true,
      xhrSetup: function(xhr, url) {
        // Intercept HTTP requests on HTTPS site (GitHub Pages) to bypass Mixed Content & CORS for stream fragments
        if (location.protocol === 'https:' && url.startsWith('http://')) {
          const proxied = `https://corsproxy.io/?${encodeURIComponent(url)}`;
          xhr.open('GET', proxied, true);
        }
      }
    });
  }
}

// CORS & Mixed Content Bypass Helper
async function fetchWithCorsFallback(url, options = {}) {
  // 1. Try direct fetch
  try {
    const directRes = await fetch(url, options);
    if (directRes.ok) return directRes;
  } catch (err) {
    console.warn('Direct fetch failed (Mixed Content or CORS). Retrying with CORS Proxy...', err);
  }

  // 2. Try CorsProxy.io
  try {
    const proxyUrl1 = `https://corsproxy.io/?${encodeURIComponent(url)}`;
    const proxyRes1 = await fetch(proxyUrl1, options);
    if (proxyRes1.ok) return proxyRes1;
  } catch (err) {
    console.warn('Corsproxy failed. Retrying with AllOrigins...', err);
  }

  // 3. Try AllOrigins Proxy Raw
  try {
    const proxyUrl2 = `https://api.allorigins.win/raw?url=${encodeURIComponent(url)}`;
    const proxyRes2 = await fetch(proxyUrl2, options);
    if (proxyRes2.ok) return proxyRes2;
  } catch (err) {
    console.warn('AllOrigins failed. Trying ThingProxy...', err);
  }

  // 4. Try ThingProxy
  const proxyUrl3 = `https://thingproxy.freeboard.io/fetch/${url}`;
  return await fetch(proxyUrl3, options);
}

function setupEventListeners() {
  document.getElementById('searchInput').addEventListener('input', (e) => {
    searchQuery = e.target.value.trim().toLowerCase();
    renderChannels();
  });

  document.getElementById('btnSettings').addEventListener('click', () => {
    openModal();
  });

  document.getElementById('btnCloseModal').addEventListener('click', () => {
    closeModal();
  });

  document.getElementById('tabM3u').addEventListener('click', () => {
    switchTab('m3u');
  });

  document.getElementById('tabXtream').addEventListener('click', () => {
    switchTab('xtream');
  });

  document.getElementById('btnConnectM3u').addEventListener('click', () => {
    const url = document.getElementById('inputM3uUrl').value.trim();
    if (url) loadM3uPlaylist(url);
  });

  document.getElementById('btnConnectXtream').addEventListener('click', () => {
    const server = document.getElementById('inputXtreamServer').value.trim();
    const user = document.getElementById('inputXtreamUser').value.trim();
    const pass = document.getElementById('inputXtreamPass').value.trim();
    if (server && user && pass) loadXtreamPlaylist(server, user, pass);
  });

  document.getElementById('btnPresetPluto').addEventListener('click', () => {
    loadM3uPlaylist('https://iptv-org.github.io/iptv/countries/br.m3u');
  });

  document.getElementById('btnPresetDemo').addEventListener('click', () => {
    loadDemoChannels();
  });
}

function switchTab(tab) {
  if (tab === 'm3u') {
    document.getElementById('tabM3u').classList.add('active');
    document.getElementById('tabXtream').classList.remove('active');
    document.getElementById('formM3u').classList.remove('hidden');
    document.getElementById('formXtream').classList.add('hidden');
  } else {
    document.getElementById('tabXtream').classList.add('active');
    document.getElementById('tabM3u').classList.remove('active');
    document.getElementById('formXtream').classList.remove('hidden');
    document.getElementById('formM3u').classList.add('hidden');
  }
}

function openModal() {
  document.getElementById('settingsModal').classList.remove('hidden');
}

function closeModal() {
  document.getElementById('settingsModal').classList.add('hidden');
}

// Loaders
function loadSavedPlaylist() {
  const savedM3u = localStorage.getItem(STORAGE_KEYS.PLAYLIST_URL);
  const savedXtream = localStorage.getItem(STORAGE_KEYS.XTREAM_CREDS);

  if (savedXtream) {
    const creds = JSON.parse(savedXtream);
    loadXtreamPlaylist(creds.server, creds.user, creds.pass);
  } else if (savedM3u) {
    loadM3uPlaylist(savedM3u);
  } else {
    loadDemoChannels();
  }
}

async function loadM3uPlaylist(url) {
  try {
    showLoading();
    const res = await fetchWithCorsFallback(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const text = await res.text();
    allChannels = parseM3u(text);
    localStorage.setItem(STORAGE_KEYS.PLAYLIST_URL, url);
    localStorage.removeItem(STORAGE_KEYS.XTREAM_CREDS);
    updateCategories();
    renderChannels();
    closeModal();
  } catch (err) {
    alert(`Erro ao carregar playlist M3U: ${err.message}`);
  } finally {
    hideLoading();
  }
}

async function loadXtreamPlaylist(serverUrl, username, password) {
  try {
    showLoading();
    let cleanServer = serverUrl.trim();
    if (!cleanServer.startsWith('http://') && !cleanServer.startsWith('https://')) {
      cleanServer = `http://${cleanServer}`;
    }
    while (cleanServer.endsWith('/')) cleanServer = cleanServer.slice(0, -1);

    // Auth & Check
    const authUrl = `${cleanServer}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`;
    const authRes = await fetchWithCorsFallback(authUrl);
    if (!authRes.ok) throw new Error(`HTTP ${authRes.status}`);
    const authData = await authRes.json();
    if (!authData.user_info || authData.user_info.auth === 0) {
      throw new Error('Usuário ou senha incorretos.');
    }

    // Categories
    let catMap = {};
    try {
      const catUrl = `${cleanServer}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&action=get_live_categories`;
      const catRes = await fetchWithCorsFallback(catUrl);
      if (catRes.ok) {
        const catArray = await catRes.json();
        catArray.forEach(c => { catMap[c.category_id] = c.category_name; });
      }
    } catch (e) {}

    // Streams
    const streamsUrl = `${cleanServer}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&action=get_live_streams`;
    const streamsRes = await fetchWithCorsFallback(streamsUrl);
    if (!streamsRes.ok) throw new Error('Falha ao carregar lista de canais');
    const streams = await streamsRes.json();

    allChannels = streams.map(s => {
      const streamId = s.stream_id;
      return {
        id: `xtream_${streamId}`,
        name: s.name || `Canal ${streamId}`,
        group: catMap[s.category_id] || 'Canais Ao Vivo',
        logoUrl: s.stream_icon || null,
        streamUrl: `${cleanServer}/live/${encodeURIComponent(username)}/${encodeURIComponent(password)}/${streamId}.m3u8`
      };
    });

    localStorage.setItem(STORAGE_KEYS.XTREAM_CREDS, JSON.stringify({ server: cleanServer, user: username, pass: password }));
    localStorage.removeItem(STORAGE_KEYS.PLAYLIST_URL);
    updateCategories();
    renderChannels();
    closeModal();
  } catch (err) {
    alert(`Erro ao conectar Xtream Codes: ${err.message}`);
  } finally {
    hideLoading();
  }
}

function loadDemoChannels() {
  allChannels = [
    {
      id: 'demo_nasa',
      name: 'NASA TV Live (Ciência & Espaço)',
      group: 'Canais Ao Vivo',
      logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/e/e5/NASA_logo.svg',
      streamUrl: 'https://ntv1.akamaized.net/hls/live/2014075/NASA-TV-HD/master.m3u8'
    },
    {
      id: 'demo_redbull',
      name: 'Red Bull TV (Esportes)',
      group: 'Esportes',
      logoUrl: 'https://upload.wikimedia.org/wikipedia/en/2/2f/Red_Bull_TV_logo.svg',
      streamUrl: 'https://rbmn-live.akamaized.net/hls/live/591070/FLO_HLS_S0/master.m3u8'
    },
    {
      id: 'demo_bbb',
      name: 'Big Buck Bunny 1080p',
      group: 'Filmes & Demo',
      logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/c/c5/Big_buck_bunny_poster_big.jpg',
      streamUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8'
    }
  ];
  updateCategories();
  renderChannels();
  closeModal();
}

function parseM3u(text) {
  const lines = text.split('\n');
  let channels = [];
  let name = 'Canal';
  let group = 'Geral';
  let logo = null;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (line.startsWith('#EXTINF:')) {
      const groupMatch = line.match(/group-title="([^"]+)"/);
      if (groupMatch) group = groupMatch[1];

      const logoMatch = line.match(/tvg-logo="([^"]+)"/);
      if (logoMatch) logo = logoMatch[1];

      const commaIndex = line.lastIndexOf(',');
      if (commaIndex !== -1) name = line.substring(commaIndex + 1).trim();
    } else if (line.length > 0 && !line.startsWith('#')) {
      channels.push({
        id: `m3u_${channels.length}_${Date.now()}`,
        name: name,
        group: group,
        logoUrl: logo,
        streamUrl: line
      });
      name = 'Canal';
      group = 'Geral';
      logo = null;
    }
  }
  return channels;
}

// UI Rendering
function updateCategories() {
  const groups = Array.from(new Set(allChannels.map(c => c.group))).sort();
  categories = ['Todos', 'Favoritos', 'Histórico', ...groups];
  renderCategoryChips();
}

function renderCategoryChips() {
  const container = document.getElementById('categoriesBar');
  container.innerHTML = categories.map(cat => `
    <button class="chip ${cat === selectedCategory ? 'active' : ''}" onclick="selectCategory('${cat}')">
      ${cat}
    </button>
  `).join('');
}

function selectCategory(cat) {
  selectedCategory = cat;
  renderCategoryChips();
  renderChannels();
}

function getFavorites() {
  return JSON.parse(localStorage.getItem(STORAGE_KEYS.FAVORITES) || '[]');
}

function toggleFavorite(id, e) {
  e.stopPropagation();
  let favs = getFavorites();
  if (favs.includes(id)) {
    favs = favs.filter(f => f !== id);
  } else {
    favs.push(id);
  }
  localStorage.setItem(STORAGE_KEYS.FAVORITES, JSON.stringify(favs));
  renderChannels();
}

function renderChannels() {
  const container = document.getElementById('channelList');
  const favs = getFavorites();

  let filtered = allChannels;

  if (selectedCategory === 'Favoritos') {
    filtered = filtered.filter(c => favs.includes(c.id));
  } else if (selectedCategory === 'Histórico') {
    const historyIds = JSON.parse(localStorage.getItem(STORAGE_KEYS.HISTORY) || '[]');
    filtered = historyIds.map(id => allChannels.find(c => c.id === id)).filter(Boolean);
  } else if (selectedCategory !== 'Todos') {
    filtered = filtered.filter(c => c.group === selectedCategory);
  }

  if (searchQuery) {
    filtered = filtered.filter(c => c.name.toLowerCase().includes(searchQuery) || c.group.toLowerCase().includes(searchQuery));
  }

  document.getElementById('channelCountBadge').innerText = `${filtered.length} canais`;

  if (filtered.length === 0) {
    container.innerHTML = `<div style="text-align:center; padding:32px; color:var(--text-secondary);">Nenhum canal encontrado.</div>`;
    return;
  }

  container.innerHTML = filtered.map(ch => {
    const isFav = favs.includes(ch.id);
    const logoHtml = ch.logoUrl ? `<img src="${ch.logoUrl}" class="channel-logo" onerror="this.src='https://upload.wikimedia.org/wikipedia/commons/thumb/3/38/Television_icon.svg/40px-Television_icon.svg.png'"/>` : `<div class="channel-logo">📺</div>`;

    return `
      <div class="channel-card ${selectedChannel && selectedChannel.id === ch.id ? 'active' : ''}" onclick="playChannel('${ch.id}')">
        ${logoHtml}
        <div class="channel-info">
          <div class="channel-name">${ch.name}</div>
          <div class="channel-group">${ch.group}</div>
        </div>
        <button class="btn-fav ${isFav ? 'active' : ''}" onclick="toggleFavorite('${ch.id}', event)">★</button>
      </div>
    `;
  }).join('');
}

function playChannel(id) {
  const channel = allChannels.find(c => c.id === id);
  if (!channel) return;

  selectedChannel = channel;
  document.getElementById('playerContainer').classList.remove('hidden');

  const video = document.getElementById('videoPlayer');
  let targetUrl = channel.streamUrl;

  if (Hls.isSupported()) {
    hls.loadSource(targetUrl);
    hls.attachMedia(video);
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      video.play().catch(e => console.log('Autoplay error:', e));
    });
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    // Safari / iOS Native HLS Support
    if (location.protocol === 'https:' && targetUrl.startsWith('http://')) {
      targetUrl = `https://corsproxy.io/?${encodeURIComponent(targetUrl)}`;
    }
    video.src = targetUrl;
    video.play().catch(e => console.log('Autoplay error:', e));
  }

  // Record History
  let history = JSON.parse(localStorage.getItem(STORAGE_KEYS.HISTORY) || '[]');
  history = history.filter(hId => hId !== id);
  history.unshift(id);
  localStorage.setItem(STORAGE_KEYS.HISTORY, JSON.stringify(history.slice(0, 30)));

  renderChannels();
}

function showLoading() {
  document.getElementById('loadingOverlay').classList.remove('hidden');
}

function hideLoading() {
  document.getElementById('loadingOverlay').classList.add('hidden');
}
