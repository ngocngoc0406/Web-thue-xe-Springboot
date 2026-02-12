let cwStomp = null;

function cwConnect() {
  console.log('[cw] connecting to /ws...');
  const socket = new SockJS('/ws');
  cwStomp = Stomp.over(socket);
  cwStomp.connect({}, (frame) => {
    console.log('[cw] connected', frame);
    // subscribe to private user queue for replies
    cwStomp.subscribe('/user/queue/replies', (msg) => {
      console.log('[cw] received raw reply', msg);
      const m = JSON.parse(msg.body);
      if (m.type === 'TYPING') {
        document.getElementById('cw-typing').style.display = 'block';
        return;
      }
      document.getElementById('cw-typing').style.display = 'none';
      appendCwMessage(m);
    });
  }, (err) => { console.error('cw connect err', err); });
}

function appendCwMessage(m) {
  const body = document.getElementById('cw-messages');
  const el = document.createElement('div');
  el.className = 'message ' + (m.sender === 'AI' ? 'ai' : 'user');

  let contentHtml = '';
  if (m.type === 'REDIRECT') {
    contentHtml = `<div class="content">${escapeHtml(m.content)}</div>`;
  } else if (m.type === 'URL') {
    contentHtml = `<div class="content"><a href="${escapeHtml(m.content)}" target="_blank">Xem kết quả tìm kiếm</a></div>`;
  } else {
    contentHtml = `<div class="content">${escapeHtml(m.content)}</div>`;
  }

  el.innerHTML = contentHtml;
  body.appendChild(el);
  body.scrollTop = body.scrollHeight;
}

function cwSend() {
  const contentEl = document.getElementById('cw-msg');
  const content = contentEl.value.trim();
  if (!content) return;
  const payload = { sender: 'User', content, type: 'CHAT', askAi: true };
  // show user message locally
  appendCwMessage(payload);
  document.getElementById('cw-typing').style.display = 'block';
  if (!cwStomp) {
    console.log('[cw] not connected: connecting then sending');
    cwConnect();
    // wait briefly and then send; this is a simple retry
    setTimeout(() => {
      if (cwStomp) {
        console.log('[cw] sending after connect', payload);
        cwStomp.send('/app/chat', {}, JSON.stringify(payload));
    } else {
      console.error('[cw] failed to connect to send message');
    }
    }, 500);
  } else {
    console.log('[cw] sending', payload);
    cwStomp.send('/app/chat', {}, JSON.stringify(payload));
  }
  contentEl.value = '';
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/[&"'<>]/g, function (c) { return {'&':'&amp;','"':'&quot;','\'':'&#39;','<':'&lt;','>':'&gt;'}[c]; });
}

// UI toggle
window.addEventListener('load', () => {
  document.getElementById('chat-toggle').addEventListener('click', () => {
    console.log('[cw] Toggle clicked');
    const widget = document.getElementById('chat-widget');
    widget.classList.toggle('chat-widget-closed');
    const panel = document.getElementById('chat-panel');
    const open = !widget.classList.contains('chat-widget-closed');
    console.log('[cw] Panel open:', open);
    panel.setAttribute('aria-hidden', !open);
    if (open && !cwStomp) cwConnect();
  });
  document.getElementById('chat-close').addEventListener('click', () => {
    console.log('[cw] Close clicked');
    const widget = document.getElementById('chat-widget');
    widget.classList.add('chat-widget-closed');
    document.getElementById('chat-panel').setAttribute('aria-hidden', 'true');
  });
});
