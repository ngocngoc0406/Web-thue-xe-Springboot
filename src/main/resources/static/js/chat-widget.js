let cwStomp = null;
const CW_STORAGE_KEY = 'mioto_chat_widget_history_' + (window.miotoUserId || 'guest');

let cwConnecting = false;
let cwQueue = [];

function cwConnect(callback) {
    if (cwStomp && cwStomp.connected) {
        if (callback) callback();
        return;
    }
    if (cwConnecting) {
        if (callback) cwQueue.push(callback);
        return;
    }
    cwConnecting = true;
    if (callback) cwQueue.push(callback);

    const socket = new SockJS('/ws');
    cwStomp = Stomp.over(socket);
    cwStomp.debug = null;
    cwStomp.connect({}, (frame) => {
        cwConnecting = false;
        while (cwQueue.length > 0) {
            const cb = cwQueue.shift();
            try { cb(); } catch(e) { console.error(e); }
        }
        cwStomp.subscribe('/user/queue/replies', (msg) => {
            const m = JSON.parse(msg.body);
            if (m.type === 'TYPING') {
                showCwTyping();
                return;
            }
            hideCwTyping();
            appendCwMessage(m, true);
        });
    }, (err) => {
        cwConnecting = false;
        console.error('cw connect err', err);
        setTimeout(() => cwConnect(), 5000);
    });
}

function showCwTyping() {
    const body = document.getElementById('cw-messages');
    if (!body || document.getElementById('cw-typing-bubble')) return;

    const el = document.createElement('div');
    el.id = 'cw-typing-bubble';
    el.className = 'message ai typing';
    el.innerHTML = `
        <div class="msg-avatar"><i class="fa-solid fa-robot"></i></div>
        <div class="content">
            <div class="typing-dots"><span></span><span></span><span></span></div>
        </div>
    `;
    body.appendChild(el);
    cwScroll();
}

function hideCwTyping() {
    const el = document.getElementById('cw-typing-bubble');
    if (el) el.remove();
}

function appendCwMessage(m, save = false) {
    const body = document.getElementById('cw-messages');
    if (!body) return;

    const el = document.createElement('div');
    const isAi = m.sender === 'AI';
    el.className = 'message ' + (isAi ? 'ai' : 'user');

    const avatarIcon = isAi ? 'fa-robot' : 'fa-user';
    const timestamp = m.timestamp ? formatCwTime(m.timestamp) : formatCwTime(new Date().toISOString());

    let contentHtml = `<div class="msg-avatar"><i class="fa-solid ${avatarIcon}"></i></div><div style="flex:1">`;
    
    // Message Content
    let innerContent = '';
    let isTypewriter = false;

    if (m.type === 'CAR_LIST' && m.data) {
        innerContent = `<div class="content message-content">${m.content || 'Gợi ý cho bạn:'}</div>`;
        innerContent += `<div class="car-cards-container" style="display:flex; gap:12px; overflow-x:auto; padding:10px 0; -webkit-overflow-scrolling: touch;">`;
        m.data.forEach(car => {
            const avatarPath = car.avatarCar ? ('/uploads/' + car.avatarCar) : '/images/car-placeholder.png';
            const carLink = `/car-detail/${car.idCar}/${encodeURIComponent(car.nameCar)}`;
            innerContent += `
                <div class="car-card" onclick="window.location.href='${carLink}'" style="flex:0 0 160px; background:white; border-radius:12px; overflow:hidden; border:1px solid rgba(0,0,0,0.05); box-shadow:0 4px 12px rgba(0,0,0,0.08); cursor:pointer; transition: transform 0.2s ease;">
                    <img src="${avatarPath}" style="width:100%; height:90px; object-fit:cover;" onerror="this.src='/images/car-placeholder.png'">
                    <div style="padding:10px;">
                        <div style="font-size:12px; font-weight:700; margin-bottom:4px; color:#1a472a; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${car.nameCar}</div>
                        <div style="font-size:13px; color:#2d5a3d; font-weight:800;">${new Intl.NumberFormat('vi-VN').format(car.price || 0)}đ<span style="font-size:9px; color:#666; font-weight:400;">/ngày</span></div>
                    </div>
                </div>
            `;
        });
        innerContent += `</div>`;
    } else if (m.type === 'URL') {
        innerContent = `<div class="content message-content"><a href="${escapeHtml(m.content)}" target="_blank" style="color:white; font-weight:700;">Xem kết quả tìm kiếm <i class="fa-solid fa-arrow-up-right-from-square"></i></a></div>`;
    } else {
        if (isAi && save && typeof marked !== 'undefined') {
            isTypewriter = true;
            innerContent = `<div class="content message-content markdown-body">...</div>`;
        } else if (isAi && typeof marked !== 'undefined') {
            innerContent = `<div class="content message-content markdown-body">${marked.parse(m.content || '')}</div>`;
        } else {
            innerContent = `<div class="content message-content">${escapeHtml(m.content)}</div>`;
        }
    }

    contentHtml += innerContent + `<span class="msg-time">${timestamp}</span></div>`;
    el.innerHTML = contentHtml;
    body.appendChild(el);
    cwScroll();

    if (isTypewriter && isAi) {
        cwTypewriter(el.querySelector('.message-content'), m.content);
    }

    if (save) saveCwMessage(m);
}

function cwTypewriter(element, text) {
    if (!text) return;
    let i = 0;
    element.innerHTML = '';
    const interval = setInterval(() => {
        if (i < text.length) {
            element.innerHTML = typeof marked !== 'undefined' ? marked.parse(text.substring(0, i + 1)) : escapeHtml(text.substring(0, i + 1));
            i++;
            cwScroll();
        } else {
            clearInterval(interval);
        }
    }, 10);
}

function cwScroll() {
    const body = document.getElementById('cw-messages');
    if (body) {
        body.scrollTo({
            top: body.scrollHeight,
            behavior: 'smooth'
        });
    }
}

function formatCwTime(isoString) {
    const date = new Date(isoString);
    return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

function getPageContext() {
    const ctx = {
        url: window.location.href,
        title: document.title
    };
    // Extract car info if on detail page
    const carTitle = document.querySelector('.premium-title-main');
    if (carTitle) ctx.currentCarName = carTitle.textContent.trim();
    
    const carPrice = document.getElementById('price-booking');
    if (carPrice) ctx.currentCarPrice = carPrice.textContent.trim();
    
    const carId = document.getElementById('current-car-id');
    if (carId) ctx.currentCarId = carId.value;

    return ctx;
}

function saveCwMessage(m) {
    let history = JSON.parse(localStorage.getItem(CW_STORAGE_KEY) || '[]');
    history.push(m);
    if (history.length > 50) history.shift();
    localStorage.setItem(CW_STORAGE_KEY, JSON.stringify(history));
}

function cwClearHistory() {
    if (confirm('Bạn có muốn xóa lịch sử trò chuyện này không?')) {
        localStorage.removeItem(CW_STORAGE_KEY);
        location.reload(); 
    }
}

function loadCwHistory() {
    const historyString = localStorage.getItem(CW_STORAGE_KEY);
    if (!historyString) return;
    const history = JSON.parse(historyString);
    const body = document.getElementById('cw-messages');
    if (body) body.innerHTML = ''; 
    history.forEach(m => appendCwMessage(m, false));
}

function cwSend() {
    const contentEl = document.getElementById('cw-msg');
    if (!contentEl) return;
    const content = contentEl.value.trim();
    if (!content) return;

    const payload = {
        sender: 'User',
        content: content,
        type: 'CHAT',
        askAi: true,
        timestamp: new Date().toISOString(),
        metadata: getPageContext()
    };

    appendCwMessage(payload, true);
    contentEl.value = '';
    contentEl.style.height = 'auto'; // Reset height

    const sendFn = () => {
        if (cwStomp && cwStomp.connected) {
            cwStomp.send('/app/chat', {}, JSON.stringify(payload));
        }
    };

    if (cwStomp && cwStomp.connected) {
        sendFn();
    } else {
        cwConnect(sendFn);
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&"'<>]/g, function (c) { return { '&': '&amp;', '"': '&quot;', '\'': '&#39;', '<': '&lt;', '>': '&gt;' }[c]; });
}

function cwQuickSend(msg) {
    const input = document.getElementById('cw-msg');
    if (input) {
        input.value = msg;
        input.dispatchEvent(new Event('input')); // Trigger resize
        cwSend();
    }
}

window.addEventListener('load', () => {
    // Set initial welcome time
    const welcomeTime = document.getElementById('cw-welcome-time');
    if (welcomeTime) {
        welcomeTime.textContent = formatCwTime(new Date().toISOString());
    }

    loadCwHistory();
    
    const toggleBtn = document.getElementById('chat-toggle');
    if (toggleBtn) {
        toggleBtn.addEventListener('click', () => {
            const widget = document.getElementById('chat-widget');
            if (!widget) return;
            const isClosed = widget.classList.contains('chat-widget-closed');
            widget.classList.toggle('chat-widget-closed');
            const panel = document.getElementById('chat-panel');
            if (panel) panel.setAttribute('aria-hidden', !isClosed);
            if (isClosed && (!cwStomp || !cwStomp.connected)) cwConnect();
            if (isClosed) setTimeout(cwScroll, 100);
        });
    }

    const closeBtn = document.getElementById('chat-close');
    if (closeBtn) {
        closeBtn.addEventListener('click', () => {
            const widget = document.getElementById('chat-widget');
            if (widget) {
                widget.classList.add('chat-widget-closed');
                const panel = document.getElementById('chat-panel');
                if (panel) panel.setAttribute('aria-hidden', 'true');
            }
        });
    }

    const clearBtn = document.getElementById('chat-clear');
    if (clearBtn) {
        clearBtn.addEventListener('click', cwClearHistory);
    }

    const msgInput = document.getElementById('cw-msg');
    if (msgInput) {
        // Auto-expand textarea
        msgInput.addEventListener('input', function() {
            this.style.height = 'auto';
            this.style.height = (this.scrollHeight) + 'px';
        });

        // Enter to send
        msgInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                cwSend();
            }
        });
    }
});
