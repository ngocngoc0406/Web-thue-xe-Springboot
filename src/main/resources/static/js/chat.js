let stompClient = null;

function connect() {
  const socket = new SockJS('/ws');
  stompClient = Stomp.over(socket);
  stompClient.connect({}, function (frame) {
    stompClient.subscribe('/topic/messages', function (message) {
      const m = JSON.parse(message.body);
      showMessage(m);
    });
  }, function(error) {
    console.error('STOMP error', error);
  });
}

function sendMessage() {
  const contentEl = document.getElementById('msg');
  const userEl = document.getElementById('username');
  const askAiEl = document.getElementById('askAi');
  const content = contentEl.value.trim();
  if (!content || !stompClient) return;
  const sender = (userEl.value || 'Anonymous').trim();
  const askAi = !!askAiEl.checked;
  // show typing indicator when asking AI
  if (askAi) document.getElementById('ai-typing').style.display = 'block';
  stompClient.send('/app/chat', {}, JSON.stringify({ sender, content, type: 'CHAT', askAi }));
  contentEl.value = '';
}

function showMessage(m) {
  const messages = document.getElementById('messages');
  const item = document.createElement('div');
  if (m.type === 'TYPING') {
    // handled globally by ai-typing element
    return;
  }
  item.className = 'message';
  const time = m.timestamp ? new Date(m.timestamp).toLocaleTimeString() : '';
  item.innerHTML = `<span class="meta">${escapeHtml(m.sender)} @ ${time}:</span> <span class="content">${escapeHtml(m.content)}</span>`;
  if (m.sender === 'AI') {
    item.style.background = '#f1f6ff';
    item.style.padding = '6px';
    // hide typing indicator
    document.getElementById('ai-typing').style.display = 'none';
  }
  messages.appendChild(item);
  messages.scrollTop = messages.scrollHeight;
}

function showMessage(m) {
  const messages = document.getElementById('messages');
  const item = document.createElement('div');
  item.className = 'message';
  const time = m.timestamp ? new Date(m.timestamp).toLocaleTimeString() : '';
  item.innerHTML = `<span class="meta">${escapeHtml(m.sender)} @ ${time}:</span> <span class="content">${escapeHtml(m.content)}</span>`;
  messages.appendChild(item);
  messages.scrollTop = messages.scrollHeight;
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/[&"'<>]/g, function (c) { return {'&':'&amp;','"':'&quot;','\'':'&#39;','<':'&lt;','>':'&gt;'}[c]; });
}

window.addEventListener('load', () => connect());