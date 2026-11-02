// IPTV Cast PWA - Core Logic (100% Android App Parity)
let allChannels = [];
let categories = ['Todos', 'Favoritos', 'Histórico'];
let selectedCategory = 'Todos';
let searchQuery = '';
let selectedChannel = null;
let isFolderViewMode = false;
let expandedFolders = {};
let hls = null;
let selectedProxyMode = 'auto';
let currentConnectionInfo = null;

const STORAGE_KEYS = {
  FAVORITES: 'firehair_pwa_favs',
  HISTORY: 'firehair_pwa_history',
  PLAYLIST_URL: 'firehair_pwa_m3u_url',
  PLAYLIST_CONTENT: 'firehair_pwa_m3u_raw',
  XTREAM_CREDS: 'firehair_pwa_xtream',
  FOLDER_VIEW: 'firehair_pwa_folder_mode',
  PROXY_MODE: 'firehair_pwa_proxy_mode'
};

// Initialize App
document.addEventListener('DOMContentLoaded', () => {
  registerServiceWorker();
  initPlayer();
  loadSavedPreferences();
  loadSavedPlaylist();
  setupEventListeners();
});

function registerServiceWorker() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').catch(err => console.log('SW Registration failed:', err));
  }
}

function toHttpsUpgradeCandidate(url) {
  return url
    .replace(/^http:\/\//i, 'https://')
    .replace(/^(https:\/\/[^\/:]+):80(\/|$)/i, '$1$2');
}

function getProxiedStreamUrl(url) {
  if (location.protocol !== 'https:' || !url.startsWith('http://')) {
    return url;
  }
  if (selectedProxyMode === 'direct') {
    return url;
  }
  if (selectedProxyMode === 'codetabs') {
    return `https://api.codetabs.com/v1/proxy?quest=${encodeURIComponent(url)}`;
  }
  if (selectedProxyMode === 'allorigins') {
    return `https://api.allorigins.win/raw?url=${encodeURIComponent(url)}`;
  }
  return `https://corsproxy.io/?url=${encodeURIComponent(url)}`;
}

function initPlayer() {
  const video = document.getElementById('videoPlayer');
  if (Hls.isSupported()) {
    hls = new Hls({
      enableWorker: true,
      lowLatencyMode: true,
      xhrSetup: function(xhr, url) {
        if (location.protocol === 'https:' && url.startsWith('http://')) {
          const proxied = getProxiedStreamUrl(url);
          xhr.open('GET', proxied, true);
        }
      }
    });
  }
}

function loadSavedPreferences() {
  isFolderViewMode = localStorage.getItem(STORAGE_KEYS.FOLDER_VIEW) === 'true';
  selectedProxyMode = localStorage.getItem(STORAGE_KEYS.PROXY_MODE) || 'auto';

  const selectProxy = document.getElementById('selectProxyMode');
  if (selectProxy) selectProxy.value = selectedProxyMode;

  updateFolderToggleButton();
}

function updateFolderToggleButton() {
  const btn = document.getElementById('btnFolderToggle');
  if (btn) {
    if (isFolderViewMode) {
      btn.classList.add('active');
      btn.title = "Modo Lista Normal";
    } else {
      btn.classList.remove('active');
      btn.title = "Modo Pastas";
    }
  }
}

// Multi-Proxy CORS & Mixed Content Bypass Helper
async function fetchWithCorsFallback(url, options = {}) {
  let targetUrls = [];
  const isHttpsPage = location.protocol === 'https:';
  const isHttpTarget = url.startsWith('http://');

  // 1. Se estivermos em HTTP (local) ou se o destino já for HTTPS, tenta conexão direta primeiro
  if ((selectedProxyMode === 'direct' || selectedProxyMode === 'auto') && (!isHttpsPage || !isHttpTarget)) {
    targetUrls.push({ name: 'Conexão Direta', url: url });
  }

  // 2. Se estivermos em HTTPS e o alvo for HTTP, tenta versão HTTPS sem a porta :80 (evita ERR_SSL_PROTOCOL_ERROR na porta 80)
  if (selectedProxyMode === 'auto' && isHttpTarget) {
    const httpsUrl = toHttpsUpgradeCandidate(url);
    targetUrls.push({ name: 'HTTPS Direto', url: httpsUrl });
  }

  // 3. Proxies HTTPS para contornar CORS e Mixed Content
  if (selectedProxyMode === 'allorigins' || selectedProxyMode === 'auto') {
    targetUrls.push({
      name: 'Proxy (AllOrigins JSON)',
      url: `https://api.allorigins.win/get?url=${encodeURIComponent(url)}`,
      unwrapAllOriginsJson: true
    });
    targetUrls.push({
      name: 'Proxy (AllOrigins Raw)',
      url: `https://api.allorigins.win/raw?url=${encodeURIComponent(url)}`
    });
  }

  if (selectedProxyMode === 'codetabs' || selectedProxyMode === 'auto') {
    targetUrls.push({
      name: 'Proxy (CodeTabs)',
      url: `https://api.codetabs.com/v1/proxy?quest=${encodeURIComponent(url)}`
    });
  }

  if (selectedProxyMode === 'corsproxy' || selectedProxyMode === 'auto') {
    targetUrls.push({
      name: 'Proxy (CorsProxy)',
      url: `https://corsproxy.io/?url=${encodeURIComponent(url)}`
    });
  }

  // Caso o usuário tenha forçado 'direct' mesmo em HTTPS+HTTP
  if (selectedProxyMode === 'direct' && targetUrls.length === 0) {
    targetUrls.push({ name: 'Conexão Direta', url: url });
  }

  let lastError = null;

  for (let i = 0; i < targetUrls.length; i++) {
    const item = targetUrls[i];
    try {
      showLoading('Conectando ao servidor...', `Tentativa ${i + 1}/${targetUrls.length}: ${item.name}`);

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 10000);

      const res = await fetch(item.url, {
        ...options,
        signal: controller.signal
      });
      clearTimeout(timeoutId);

      if (res.ok) {
        if (item.unwrapAllOriginsJson) {
          const data = await res.json();
          if (data && typeof data.contents === 'string' && data.contents.length > 0) {
            const contents = data.contents;
            return {
              ok: true,
              status: (data.status && data.status.http_code) || 200,
              text: async () => contents,
              json: async () => JSON.parse(contents)
            };
          }
          continue;
        }
        return res;
      }
    } catch (err) {
      lastError = err;
    }
  }

  const mixedContentHint = (isHttpsPage && isHttpTarget)
    ? '\n\n⚠️ O navegador bloqueia conexões HTTP diretas em páginas HTTPS (GitHub Pages) e o seu servidor IPTV bloqueou os proxies públicos gratuitos.'
    : '';

  throw new Error((lastError ? lastError.message : 'Falha na conexão.') + mixedContentHint);
}

function setupEventListeners() {
  const selectProxy = document.getElementById('selectProxyMode');
  if (selectProxy) {
    selectProxy.addEventListener('change', (e) => {
      selectedProxyMode = e.target.value;
      localStorage.setItem(STORAGE_KEYS.PROXY_MODE, selectedProxyMode);
    });
  }

  document.getElementById('btnSearchToggle').addEventListener('click', () => {
    const searchContainer = document.getElementById('searchContainer');
    searchContainer.classList.toggle('hidden');
    if (!searchContainer.classList.contains('hidden')) {
      document.getElementById('searchInput').focus();
    }
  });

  document.getElementById('searchInput').addEventListener('input', (e) => {
    searchQuery = e.target.value.trim().toLowerCase();
    renderChannels();
  });

  document.getElementById('btnRefresh').addEventListener('click', () => {
    loadSavedPlaylist();
  });

  document.getElementById('btnFolderToggle').addEventListener('click', () => {
    isFolderViewMode = !isFolderViewMode;
    localStorage.setItem(STORAGE_KEYS.FOLDER_VIEW, isFolderViewMode.toString());
    updateFolderToggleButton();
    renderChannels();
  });

  document.getElementById('btnSettings').addEventListener('click', () => {
    openModal();
  });

  document.getElementById('btnCloseModal').addEventListener('click', () => {
    closeModal();
  });

  // Tab Switching in Settings
  document.getElementById('tabM3u').addEventListener('click', () => switchTab('m3u'));
  document.getElementById('tabXtream').addEventListener('click', () => switchTab('xtream'));
  document.getElementById('tabFile').addEventListener('click', () => switchTab('file'));
  document.getElementById('tabText').addEventListener('click', () => switchTab('text'));

  document.getElementById('btnConnectM3u').addEventListener('click', () => {
    const url = document.getElementById('inputM3uUrl').value.trim();
    if (url) loadM3uPlaylist(url);
  });

  document.getElementById('btnConnectXtream').addEventListener('click', () => {
    const server = document.getElementById('inputXtreamServer').value.trim();
    const user = document.getElementById('inputXtreamUser').value.trim();
    const pass = document.getElementById('inputXtreamPass').value.trim();

    if (server) {
      if (server.includes('get.php') || server.includes('.m3u') || (!user && !pass && server.includes('http'))) {
        loadM3uPlaylist(server);
      } else if (user && pass) {
        loadXtreamPlaylist(server, user, pass);
      } else {
        alert('Por favor, informe o Usuário e a Senha ou cole o link da playlist no campo Servidor.');
      }
    }
  });

  // Local M3U File Processing
  document.getElementById('btnProcessFile').addEventListener('click', () => {
    const fileInput = document.getElementById('inputM3uFile');
    if (!fileInput.files || fileInput.files.length === 0) {
      alert('Por favor, selecione um arquivo .m3u ou .m3u8 no seu aparelho.');
      return;
    }

    const file = fileInput.files[0];
    showLoading('Lendo arquivo local...', file.name);

    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const text = e.target.result;
        loadM3uContent(text, `Arquivo Local (${file.name})`);
      } catch (err) {
        alert(`Erro ao ler arquivo: ${err.message}`);
      } finally {
        hideLoading();
      }
    };
    reader.onerror = () => {
      alert('Erro ao carregar o arquivo local.');
      hideLoading();
    };
    reader.readAsText(file);
  });

  // Paste Text M3U Processing
  document.getElementById('btnProcessText').addEventListener('click', () => {
    const text = document.getElementById('textareaM3uText').value.trim();
    if (!text) {
      alert('Por favor, cole o conteúdo do seu arquivo M3U no campo de texto.');
      return;
    }
    loadM3uContent(text, 'Texto M3U Colado');
  });

  document.getElementById('btnPresetPluto').addEventListener('click', () => {
    loadM3uPlaylist('https://iptv-org.github.io/iptv/countries/br.m3u');
  });

  document.getElementById('btnPresetDemo').addEventListener('click', () => {
    loadDemoChannels();
  });
}

function switchTab(tab) {
  const tabs = ['m3u', 'xtream', 'file', 'text'];
  tabs.forEach(t => {
    const tabBtn = document.getElementById(`tab${capitalize(t)}`);
    const formElem = document.getElementById(`form${capitalize(t)}`);
    if (tabBtn && formElem) {
      if (t === tab) {
        tabBtn.classList.add('active');
        formElem.classList.remove('hidden');
      } else {
        tabBtn.classList.remove('active');
        formElem.classList.add('hidden');
      }
    }
  });
}

function capitalize(s) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function openModal() {
  updateActiveConnectionCard();
  document.getElementById('settingsModal').classList.remove('hidden');
}

function closeModal() {
  document.getElementById('settingsModal').classList.add('hidden');
}

function updateActiveConnectionCard() {
  const cardElem = document.getElementById('activeConnectionCard');
  if (!cardElem) return;

  if (currentConnectionInfo) {
    cardElem.classList.remove('hidden');
    document.getElementById('connType').innerText = currentConnectionInfo.type;
    document.getElementById('connUrl').innerText = currentConnectionInfo.url;
    document.getElementById('connCount').innerText = `${currentConnectionInfo.count} canais`;
  } else {
    cardElem.classList.add('hidden');
  }
}

// Loaders
function loadSavedPlaylist() {
  const savedContent = localStorage.getItem(STORAGE_KEYS.PLAYLIST_CONTENT);
  const savedM3u = localStorage.getItem(STORAGE_KEYS.PLAYLIST_URL);
  const savedXtream = localStorage.getItem(STORAGE_KEYS.XTREAM_CREDS);

  if (savedContent) {
    loadM3uContent(savedContent, 'Lista Local Salva', false);
  } else if (savedXtream) {
    try {
      const creds = JSON.parse(savedXtream);
      loadXtreamPlaylist(creds.server, creds.user, creds.pass);
    } catch (e) {
      loadDemoChannels();
    }
  } else if (savedM3u) {
    loadM3uPlaylist(savedM3u);
  } else {
    loadDemoChannels();
  }
}

function loadM3uContent(text, sourceName = 'Arquivo Local M3U', saveToStorage = true) {
  allChannels = parseM3u(text);
  if (allChannels.length === 0) {
    alert('Nenhum canal válido foi encontrado no conteúdo fornecido.');
    return;
  }

  if (saveToStorage) {
    try {
      localStorage.setItem(STORAGE_KEYS.PLAYLIST_CONTENT, text);
    } catch (storageErr) {
      console.warn('Lista muito grande para salvar no localStorage:', storageErr);
    }
    localStorage.removeItem(STORAGE_KEYS.PLAYLIST_URL);
    localStorage.removeItem(STORAGE_KEYS.XTREAM_CREDS);
  }

  currentConnectionInfo = {
    type: sourceName,
    url: 'Importado Localmente (Sem Bloqueios de Rede)',
    count: allChannels.length
  };

  updateCategories();
  renderChannels();
  closeModal();
}

async function loadM3uPlaylist(url) {
  try {
    showLoading('Baixando playlist M3U...', 'Iniciando conexão...');
    const res = await fetchWithCorsFallback(url);
    if (!res.ok) throw new Error(`Status ${res.status}`);
    const text = await res.text();
    allChannels = parseM3u(text);
    if (allChannels.length === 0) {
      throw new Error('A lista M3U baixada não contém canais válidos.');
    }
    localStorage.setItem(STORAGE_KEYS.PLAYLIST_URL, url);
    localStorage.removeItem(STORAGE_KEYS.PLAYLIST_CONTENT);
    localStorage.removeItem(STORAGE_KEYS.XTREAM_CREDS);

    currentConnectionInfo = {
      type: 'Playlist M3U via Link',
      url: url,
      count: allChannels.length
    };

    updateCategories();
    renderChannels();
    closeModal();
  } catch (err) {
    alert(`Erro ao carregar playlist M3U:\n\n${err.message}\n\n💡 OUTRAS OPÇÕES:\n1. Baixe o arquivo .m3u no seu aparelho e use a aba "📁 Arquivo" nas Configurações.\n2. Ou copie o texto do arquivo e use a aba "📋 Colar".\n3. Para usar listas HTTP sem restrições, rode localmente via HTTP.`);
  } finally {
    hideLoading();
  }
}

async function loadXtreamPlaylist(serverUrl, username, password) {
  try {
    showLoading('Conectando ao servidor Xtream Codes...', 'Iniciando autenticação...');
    let cleanServer = serverUrl.trim();
    if (!cleanServer.startsWith('http://') && !cleanServer.startsWith('https://')) {
      cleanServer = `http://${cleanServer}`;
    }
    while (cleanServer.endsWith('/')) cleanServer = cleanServer.slice(0, -1);

    let loadedChannels = [];

    // Attempt 1: Xtream Codes JSON API (player_api.php)
    try {
      showLoading('Autenticando via API Xtream...', 'Buscando dados da conta...');
      const authUrl = `${cleanServer}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`;
      const authRes = await fetchWithCorsFallback(authUrl);
      if (authRes.ok) {
        const authData = await authRes.json();
        if (authData.user_info && authData.user_info.auth !== 0) {
          showLoading('Obtendo categorias e canais...', 'Carregando lista de transmissão...');

          let catMap = {};
          try {
            const catUrl = `${cleanServer}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&action=get_live_categories`;
            const catRes = await fetchWithCorsFallback(catUrl);
            if (catRes.ok) {
              const catArray = await catRes.json();
              catArray.forEach(c => { catMap[c.category_id] = c.category_name; });
            }
          } catch (e) {}

          const streamsUrl = `${cleanServer}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&action=get_live_streams`;
          const streamsRes = await fetchWithCorsFallback(streamsUrl);
          if (streamsRes.ok) {
            const streams = await streamsRes.json();
            if (Array.isArray(streams) && streams.length > 0) {
              loadedChannels = streams.map(s => {
                const streamId = s.stream_id;
                return {
                  id: `xtream_${streamId}`,
                  name: s.name || `Canal ${streamId}`,
                  group: catMap[s.category_id] || 'Canais Ao Vivo',
                  logoUrl: s.stream_icon || null,
                  streamUrl: `${cleanServer}/live/${encodeURIComponent(username)}/${encodeURIComponent(password)}/${streamId}.m3u8`
                };
              });
            }
          }
        }
      }
    } catch (apiErr) {
      console.warn('API Xtream Codes falhou. Tentando modo de compatibilidade M3U (get.php)...', apiErr);
    }

    // Attempt 2: Fallback to get.php (M3U format) if API JSON was empty or failed
    if (loadedChannels.length === 0) {
      showLoading('Carregando via modo M3U do servidor (get.php)...', 'Processando lista...');
      const m3uFallbackUrl = `${cleanServer}/get.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&type=m3u_plus&output=ts`;
      const m3uRes = await fetchWithCorsFallback(m3uFallbackUrl);
      if (!m3uRes.ok) throw new Error(`Servidor respondeu com status ${m3uRes.status}`);
      const m3uText = await m3uRes.text();
      loadedChannels = parseM3u(m3uText);
    }

    if (loadedChannels.length === 0) {
      throw new Error('Nenhum canal encontrado na conta. Verifique se a sua lista está ativa no provedor.');
    }

    allChannels = loadedChannels;
    localStorage.setItem(STORAGE_KEYS.XTREAM_CREDS, JSON.stringify({ server: cleanServer, user: username, pass: password }));
    localStorage.removeItem(STORAGE_KEYS.PLAYLIST_URL);
    localStorage.removeItem(STORAGE_KEYS.PLAYLIST_CONTENT);

    currentConnectionInfo = {
      type: 'API Xtream Codes',
      url: cleanServer,
      count: allChannels.length
    };

    updateCategories();
    renderChannels();
    closeModal();
  } catch (err) {
    const fallbackDownloadUrl = `${serverUrl.replace(/\/+$/, '')}/get.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&type=m3u_plus&output=ts`;
    alert(`Erro ao conectar ao Xtream Codes:\n\n${err.message}\n\n💡 SOLUÇÃO RÁPIDA:\n1. Baixe seu arquivo .m3u abrindo no navegador:\n${fallbackDownloadUrl}\n2. Use a aba "📁 Arquivo" nas Configurações para carregar instantaneamente.`);
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
  currentConnectionInfo = {
    type: 'Canais Demonstração',
    url: 'demo.iptvcast.app',
    count: allChannels.length
  };
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
  if (isFolderViewMode) {
    container.classList.add('hidden');
    return;
  }
  container.classList.remove('hidden');
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

function toggleFolderExpand(groupName) {
  expandedFolders[groupName] = !expandedFolders[groupName];
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
  } else if (selectedCategory !== 'Todos' && !isFolderViewMode) {
    filtered = filtered.filter(c => c.group === selectedCategory);
  }

  if (searchQuery) {
    filtered = filtered.filter(c => c.name.toLowerCase().includes(searchQuery) || c.group.toLowerCase().includes(searchQuery));
  }

  document.getElementById('channelCountBadge').innerText = `${filtered.length} canais`;

  if (filtered.length === 0) {
    container.innerHTML = `<div style="text-align:center; padding:48px 16px; color:var(--text-secondary);">Nenhum canal encontrado.</div>`;
    return;
  }

  // If Folder View Mode is active (and not searching)
  if (isFolderViewMode && !searchQuery && selectedCategory === 'Todos') {
    const groupsMap = {};
    filtered.forEach(ch => {
      const g = ch.group || 'Geral';
      if (!groupsMap[g]) groupsMap[g] = [];
      groupsMap[g].push(ch);
    });

    const groupNames = Object.keys(groupsMap).sort();

    container.innerHTML = groupNames.map(gName => {
      const chList = groupsMap[gName];
      const isExpanded = !!expandedFolders[gName];

      const cardsHtml = chList.map(ch => renderChannelCardHtml(ch, favs)).join('');

      return `
        <div class="folder-card ${isExpanded ? 'expanded' : ''}">
          <div class="folder-header" onclick="toggleFolderExpand('${gName.replace(/'/g, "\\'")}')">
            <div class="folder-title-box">
              <div class="folder-icon-pill">📁</div>
              <div class="folder-info">
                <span class="folder-name">${gName}</span>
                <span class="folder-count">${chList.length} canais nesta pasta</span>
              </div>
            </div>
            <span class="folder-arrow">▶</span>
          </div>
          ${isExpanded ? `<div class="folder-content">${cardsHtml}</div>` : ''}
        </div>
      `;
    }).join('');
    return;
  }

  // Standard List View Mode
  container.innerHTML = filtered.map(ch => renderChannelCardHtml(ch, favs)).join('');
}

function renderChannelCardHtml(ch, favs) {
  const isFav = favs.includes(ch.id);
  const isSelected = selectedChannel && selectedChannel.id === ch.id;

  const logoHtml = ch.logoUrl 
    ? `<img src="${ch.logoUrl}" class="channel-logo-img" onerror="this.outerHTML='<span class=\\'channel-logo-icon\\'>📺</span>'"/>` 
    : `<span class="channel-logo-icon">📺</span>`;

  return `
    <div class="channel-card ${isSelected ? 'active' : ''}" onclick="playChannel('${ch.id}')">
      <div class="channel-logo-container">
        ${logoHtml}
      </div>
      <div class="channel-details">
        <div class="channel-title">${ch.name}</div>
        <div class="channel-sub-row">
          <span class="group-badge">${ch.group}</span>
          ${isSelected ? `
            <div class="live-indicator">
              <span class="live-dot"></span>
              <span class="live-text">AO VIVO</span>
            </div>
          ` : ''}
        </div>
      </div>
      <button class="btn-fav ${isFav ? 'active' : ''}" onclick="toggleFavorite('${ch.id}', event)">
        ${isFav ? '❤️' : '🤍'}
      </button>
    </div>
  `;
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
    if (location.protocol === 'https:' && targetUrl.startsWith('http://')) {
      targetUrl = getProxiedStreamUrl(targetUrl);
    }
    video.src = targetUrl;
    video.play().catch(e => console.log('Autoplay error:', e));
  }

  document.getElementById('playerContainer').scrollIntoView({ behavior: 'smooth' });

  let history = JSON.parse(localStorage.getItem(STORAGE_KEYS.HISTORY) || '[]');
  history = history.filter(hId => hId !== id);
  history.unshift(id);
  localStorage.setItem(STORAGE_KEYS.HISTORY, JSON.stringify(history.slice(0, 30)));

  renderChannels();
}

function showLoading(msg = 'Carregando canais...', subtext = 'Aguarde um momento...') {
  const textElem = document.getElementById('loadingText');
  const subtextElem = document.getElementById('loadingSubtext');
  if (textElem) textElem.innerText = msg;
  if (subtextElem) subtextElem.innerText = subtext;
  document.getElementById('loadingOverlay').classList.remove('hidden');
}

function hideLoading() {
  document.getElementById('loadingOverlay').classList.add('hidden');
}
