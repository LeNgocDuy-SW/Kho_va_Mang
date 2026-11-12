// ==========================================================================
// MULTI-CLIENT CLOUD CHAT - VANILLA JAVASCRIPT CLIENT (HYBRID SSE & REST)
// ==========================================================================

const state = {
    currentUser: null,
    currentRole: "USER",
    activeRoom: "Friends & Family",
    unlockedGroups: new Set(["Friends & Family"]),
    pendingJoinGroup: null,
    groups: [],
    onlineUsers: [],
    eventSource: null,
    isRegisterMode: false,
    renderedMessages: new Set() // Dùng để chống trùng lặp tin nhắn
};

// ==========================================================================
// 1. XỬ LÝ ĐĂNG NHẬP & ĐĂNG KÝ (NEUMORPHIC AUTH SCREEN)
// ==========================================================================

function toggleAuthMode() {
    state.isRegisterMode = !state.isRegisterMode;
    const title = document.getElementById("authTitle");
    const subtitle = document.getElementById("authSubtitle");
    const btnText = document.getElementById("btnAuthText");
    const roleRow = document.getElementById("roleSelectRow");
    const rememberRow = document.getElementById("rememberRow");
    const prompt = document.getElementById("togglePrompt");
    const actionLink = document.getElementById("toggleActionLink");

    if (state.isRegisterMode) {
        title.innerText = "Create Account";
        subtitle.innerText = "Sign up to get started";
        btnText.innerText = "Sign Up";
        roleRow.style.display = "flex";
        rememberRow.style.display = "none";
        prompt.innerText = "Already have an account?";
        actionLink.innerHTML = "<b>Sign in</b>";
    } else {
        title.innerText = "Welcome Back";
        subtitle.innerText = "Sign in to continue";
        btnText.innerText = "Login";
        roleRow.style.display = "none";
        rememberRow.style.display = "flex";
        prompt.innerText = "Don't have an account?";
        actionLink.innerHTML = "<b>Sign up</b>";
    }
}

function togglePasswordVisibility() {
    const passInput = document.getElementById("authPassword");
    passInput.type = passInput.type === "password" ? "text" : "password";
}

async function handleAuthSubmit(e) {
    e.preventDefault();
    const user = document.getElementById("authUsername").value.trim();
    const pass = document.getElementById("authPassword").value.trim();
    const role = document.getElementById("authRole").value;
    const statusEl = document.getElementById("authStatus");

    if (!user || !pass) return;

    statusEl.innerText = "⏳ Đang kết nối máy chủ...";
    statusEl.style.color = "#2563eb";

    try {
        if (state.isRegisterMode) {
            const res = await fetch("/api/register", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username: user, password: pass, role: role })
            });
            const data = await res.json();
            if (data.success) {
                alert("Đăng ký thành công! Hãy đăng nhập ngay.");
                toggleAuthMode();
                document.getElementById("authPassword").value = "";
            } else {
                statusEl.innerText = "❌ " + (data.message || "Đăng ký thất bại");
                statusEl.style.color = "#ef4444";
            }
        } else {
            const res = await fetch("/api/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username: user, password: pass })
            });
            const data = await res.json();
            if (data.success) {
                state.currentUser = data.username;
                state.currentRole = data.role || "USER";
                enterChatScreen();
            } else {
                statusEl.innerText = "❌ " + (data.message || "Đăng nhập thất bại");
                statusEl.style.color = "#ef4444";
            }
        }
    } catch (err) {
        statusEl.innerText = "❌ Không thể kết nối tới Server. Hãy đảm bảo Server đang bật!";
        statusEl.style.color = "#ef4444";
    }
}

function enterChatScreen() {
    document.getElementById("authScreen").style.display = "none";
    document.getElementById("chatScreen").style.display = "flex";

    // Cập nhật Profile
    document.getElementById("myDisplayName").innerText = state.currentUser;
    document.getElementById("myAvatar").innerText = state.currentUser.substring(0, 1).toUpperCase();

    // Khởi tạo luồng SSE Real-time
    initEventSource();

    // Nạp danh sách nhóm và lịch sử chat mặc định
    loadGroups();
    loadHistory(state.activeRoom);

    // Kích hoạt đồng bộ dự phòng mỗi 3 giây
    setInterval(() => {
        if (state.currentUser && state.activeRoom) {
            syncLatestMessages(state.activeRoom);
        }
    }, 3000);
}

// ==========================================================================
// 2. SERVER-SENT EVENTS (SSE) - THỜI GIAN THỰC ĐỒNG BỘ DESKTOP & WEB
// ==========================================================================

function initEventSource() {
    if (state.eventSource) {
        try { state.eventSource.close(); } catch (e) {}
    }

    state.eventSource = new EventSource(`/api/events?user=${encodeURIComponent(state.currentUser)}`);

    state.eventSource.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data);
            handleIncomingEvent(data);
        } catch (e) {
            // Heartbeat hoặc comment bỏ qua
        }
    };

    state.eventSource.onerror = (err) => {
        // SSE tự động kết nối lại khi có lỗi
    };
}

function handleIncomingEvent(data) {
    switch (data.type) {
        case "NEW_MSG":
            if (data.groupName === state.activeRoom) {
                // Nếu là tin nhắn của chính mình đã render thì không render lại
                const sig = `${data.sender}|${data.content}|${data.timestamp}`;
                if (data.sender === state.currentUser && state.renderedMessages.has(sig)) {
                    return;
                }
                state.renderedMessages.add(sig);
                appendMessageBubble(data.sender, data.role, data.timestamp, data.content, data.sender === state.currentUser);
            }
            break;
        case "NEW_PRIVATE_MSG":
            const privSig = `${data.sender}|${data.content}|${data.timestamp}`;
            if (!state.renderedMessages.has(privSig)) {
                state.renderedMessages.add(privSig);
                appendMessageBubble(data.sender, "USER", data.timestamp, `[🔒 Tin riêng]: ${data.content}`, data.sender === state.currentUser);
            }
            break;
        case "SYSTEM_MSG":
            appendSystemNotice(data.content);
            break;
        case "USER_LIST":
            state.onlineUsers = data.users || [];
            renderMembersList();
            break;
        case "GROUP_LIST":
            state.groups = data.groups || [];
            renderGroupsList();
            break;
    }
}

// ==========================================================================
// 3. QUẢN LÝ NHÓM & MÃ PIN BẢO MẬT (GROUP CODE / PIN)
// ==========================================================================

async function loadGroups() {
    try {
        const res = await fetch("/api/groups");
        const data = await res.json();
        if (data.success) {
            state.groups = data.groups || [];
            renderGroupsList();
        }
    } catch (e) {
        console.error("Lỗi tải danh sách nhóm:", e);
    }
}

function renderGroupsList() {
    const container = document.getElementById("groupsContainer");
    container.innerHTML = "";

    state.groups.forEach(g => {
        if (g.name === "Friends & Family") return;

        const item = document.createElement("div");
        item.className = `group-item ${state.activeRoom === g.name ? "active" : ""}`;
        item.onclick = () => selectGroup(g.name);

        const initial = g.name.substring(0, 1).toUpperCase();
        item.innerHTML = `
            <div class="group-icon-circle">${initial}</div>
            <div class="group-meta">
                <span class="group-name">${escapeHtml(g.name)}</span>
                <span class="group-desc">${g.memberCount} Members • 🔐 Mã PIN</span>
            </div>
        `;
        container.appendChild(item);
    });
}

function selectGroup(groupName) {
    if (!state.unlockedGroups.has(groupName)) {
        state.pendingJoinGroup = groupName;
        document.getElementById("joinGroupDesc").innerText = `Nhóm [${groupName}] được bảo vệ. Vui lòng nhập mã PIN nhóm để tham gia trò chuyện.`;
        document.getElementById("joinGroupInputCode").value = "";
        openModal("modalJoinGroup");
        return;
    }

    state.activeRoom = groupName;
    document.getElementById("favCard").classList.toggle("active", groupName === "Friends & Family");
    document.getElementById("activeChatTitle").innerText = groupName;
    document.getElementById("rightGroupTitle").innerText = groupName;

    state.renderedMessages.clear();
    loadHistory(groupName);
    renderGroupsList();
}

async function submitJoinGroup() {
    const code = document.getElementById("joinGroupInputCode").value.trim();
    if (!code) {
        alert("Vui lòng nhập mã PIN nhóm!");
        return;
    }

    try {
        const res = await fetch("/api/join_group", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                groupName: state.pendingJoinGroup,
                groupCode: code,
                username: state.currentUser
            })
        });
        const data = await res.json();
        if (data.success) {
            state.unlockedGroups.add(state.pendingJoinGroup);
            closeModal("modalJoinGroup");
            selectGroup(state.pendingJoinGroup);
        } else {
            alert(data.message || "Mã PIN nhóm không chính xác!");
        }
    } catch (e) {
        alert("Lỗi khi tham gia nhóm.");
    }
}

async function submitCreateGroup() {
    const name = document.getElementById("newGroupName").value.trim();
    const code = document.getElementById("newGroupCode").value.trim();
    if (!name || !code) {
        alert("Vui lòng điền đầy đủ tên nhóm và mã PIN!");
        return;
    }

    try {
        const res = await fetch("/api/create_group", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                groupName: name,
                groupCode: code,
                creator: state.currentUser
            })
        });
        const data = await res.json();
        if (data.success) {
            state.unlockedGroups.add(name);
            closeModal("modalCreateGroup");
            document.getElementById("newGroupName").value = "";
            document.getElementById("newGroupCode").value = "";
            alert(`Tạo nhóm thành công!\nTên: ${name}\nMã PIN: ${code}\n(Hãy chia sẻ mã PIN này cho bạn bè để cùng vào nhóm)`);
            loadGroups();
            selectGroup(name);
        } else {
            alert(data.message || "Lỗi tạo nhóm.");
        }
    } catch (e) {
        alert("Lỗi khi tạo nhóm mới.");
    }
}

// ==========================================================================
// 4. LỊCH SỬ TIN NHẮN & GỬI TIN NHẮN (HIỂN THỊ TỨC THÌ 0MS)
// ==========================================================================

async function loadHistory(groupName) {
    const container = document.getElementById("messagesContainer");
    container.innerHTML = "";

    try {
        const res = await fetch(`/api/history?group=${encodeURIComponent(groupName)}`);
        const data = await res.json();
        if (data.success && data.messages) {
            data.messages.forEach(m => {
                const sig = `${m.sender}|${m.content}|${m.time}`;
                state.renderedMessages.add(sig);
                appendMessageBubble(m.sender, m.role, m.time, m.content, m.sender === state.currentUser);
            });
        }
    } catch (e) {
        console.error("Lỗi tải lịch sử chat:", e);
    }
}

async function syncLatestMessages(groupName) {
    try {
        const res = await fetch(`/api/history?group=${encodeURIComponent(groupName)}`);
        const data = await res.json();
        if (data.success && data.messages) {
            data.messages.forEach(m => {
                const sig = `${m.sender}|${m.content}|${m.time}`;
                if (!state.renderedMessages.has(sig)) {
                    state.renderedMessages.add(sig);
                    appendMessageBubble(m.sender, m.role, m.time, m.content, m.sender === state.currentUser);
                }
            });
        }
    } catch (ignored) {}
}

async function sendActiveMessage() {
    const input = document.getElementById("txtChatMessage");
    const content = input.value.trim();
    if (!content) return;

    input.value = "";
    input.focus();

    try {
        const res = await fetch("/api/send", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                sender: state.currentUser,
                role: state.currentRole,
                groupName: state.activeRoom,
                content: content,
                isPrivate: "false"
            })
        });
        const data = await res.json();
        if (data.success) {
            // HIỂN THỊ NGAY BONG BÓNG TIN NHẮN CỦA MÌNH TỨC THÌ (0ms)
            const timeStr = data.time || (new Date()).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
            const sig = `${state.currentUser}|${content}|${timeStr}`;
            state.renderedMessages.add(sig);
            appendMessageBubble(state.currentUser, state.currentRole, timeStr, content, true);
        } else {
            alert(data.message || "Không thể gửi tin nhắn.");
        }
    } catch (e) {
        alert("Lỗi kết nối khi gửi tin nhắn.");
    }
}

function handleInputKeyDown(e) {
    if (e.key === "Enter") {
        sendActiveMessage();
    }
}

function appendMessageBubble(sender, role, time, content, isMine) {
    const container = document.getElementById("messagesContainer");
    const row = document.createElement("div");
    row.className = `message-row ${isMine ? "mine" : "other"}`;

    const initial = sender.substring(0, 1).toUpperCase();
    row.innerHTML = `
        <div class="msg-avatar" style="${isMine ? 'background:#3b82f6;' : 'background:#94a3b8;'}">${initial}</div>
        <div class="msg-content-wrap">
            ${!isMine ? `<div class="msg-sender-name">${escapeHtml(sender)} (${role})</div>` : ''}
            <div class="msg-bubble">${escapeHtml(content)}</div>
            <div class="msg-meta-row">${time || ''}</div>
        </div>
    `;

    container.appendChild(row);
    container.scrollTop = container.scrollHeight;
}

function appendSystemNotice(text) {
    const container = document.getElementById("messagesContainer");
    const div = document.createElement("div");
    div.style.textAlign = "center";
    div.style.fontSize = "12px";
    div.style.color = "#64748b";
    div.style.margin = "8px 0";
    div.innerHTML = `<i>📢 ${escapeHtml(text)}</i>`;
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
}

// ==========================================================================
// 5. DANH SÁCH THÀNH VIÊN ONLINE
// ==========================================================================

function renderMembersList() {
    const container = document.getElementById("membersContainer");
    const label = document.getElementById("membersCountLabel");
    const subtitle = document.getElementById("activeChatSubtitle");
    const favTag = document.getElementById("favOnlineTag");

    container.innerHTML = "";
    const count = Math.max(1, state.onlineUsers.length);
    label.innerText = `Members (${count})`;
    subtitle.innerText = `🟢 ${count} Online`;
    favTag.innerText = `🟢 ${count} Online`;

    state.onlineUsers.forEach(u => {
        const item = document.createElement("div");
        item.className = "member-item";
        const initial = u.username.substring(0, 1).toUpperCase();
        const isOnline = u.status !== "MUTED";

        item.innerHTML = `
            <div class="member-avatar">${initial}</div>
            <div class="member-name">${escapeHtml(u.username)} ${u.username === state.currentUser ? "(Bạn)" : ""}</div>
            <div class="status-dot ${isOnline ? 'online' : 'offline'}"></div>
        `;
        container.appendChild(item);
    });
}

// ==========================================================================
// 6. MODALS & UTILITIES
// ==========================================================================

function openModal(id) {
    document.getElementById(id).style.display = "flex";
}

function closeModal(id) {
    document.getElementById(id).style.display = "none";
}

function openCreateGroupModal() {
    openModal("modalCreateGroup");
}

function showServerInfoModal() {
    alert("Máy chủ Chat Cloud đang chạy tại cổng Web 8080 và Socket TCP 5000.\nBạn có thể chia sẻ link mạng LAN hoặc dùng Cloudflare Tunnel để bạn bè truy cập!");
}

function showOptionsMenu() {
    const pass = prompt("Nhập mật khẩu mới nếu muốn đổi:");
    if (pass && pass.trim()) {
        alert("Đã gửi yêu cầu đổi mật khẩu!");
    }
}

function openEmojiPicker() {
    const input = document.getElementById("txtChatMessage");
    input.value += "😊";
    input.focus();
}

function filterSidebarGroups() {
    const kw = document.getElementById("txtSearchSidebar").value.toLowerCase();
    const items = document.querySelectorAll(".group-item");
    items.forEach(it => {
        const name = it.querySelector(".group-name").innerText.toLowerCase();
        it.style.display = name.includes(kw) ? "flex" : "none";
    });
}

function doLogout() {
    if (confirm("Bạn có muốn đăng xuất khỏi Web Chat?")) {
        if (state.eventSource) {
            try { state.eventSource.close(); } catch (e) {}
        }
        location.reload();
    }
}

function escapeHtml(str) {
    if (!str) return "";
    return str.replace(/&/g, "&amp;")
              .replace(/</g, "&lt;")
              .replace(/>/g, "&gt;")
              .replace(/"/g, "&quot;")
              .replace(/'/g, "&#039;");
}
