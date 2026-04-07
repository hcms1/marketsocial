const state = {
  currentUser: null,
  selectedUser: null,
  profile: null,
  products: [],
  myProducts: [],
  posts: [],
  myPosts: [],
  users: [],
  managedUsers: [],
  productDraftImages: [],
  activeView: "overview",
  unreadConversations: [],
  seenUnreadMessageIds: new Set(),
  notificationPollHandle: null,
  notificationsPrimed: false,
  defaultDocumentTitle: document.title,
};

const NOTIFICATION_POLL_INTERVAL_MS = 15000;

const authPanel = document.getElementById("authPanel");
const appPanel = document.getElementById("appPanel");
const authMessage = document.getElementById("authMessage");
const appMessage = document.getElementById("appMessage");
const loginForm = document.getElementById("loginForm");
const registerForm = document.getElementById("registerForm");
const showLogin = document.getElementById("showLogin");
const showRegister = document.getElementById("showRegister");
const logoutButton = document.getElementById("logoutButton");
const currentUserLabel = document.getElementById("currentUserLabel");
const messagesUnreadBadge = document.getElementById("messagesUnreadBadge");
const usersList = document.getElementById("usersList");
const conversationTitle = document.getElementById("conversationTitle");
const messagesList = document.getElementById("messagesList");
const messageForm = document.getElementById("messageForm");
const messageInput = document.getElementById("messageInput");
const productForm = document.getElementById("productForm");
const cancelEditButton = document.getElementById("cancelEditButton");
const profileForm = document.getElementById("profileForm");
const passwordForm = document.getElementById("passwordForm");
const deleteAccountForm = document.getElementById("deleteAccountForm");
const postForm = document.getElementById("postForm");
const adminUserForm = document.getElementById("adminUserForm");
const registerAccountType = document.getElementById("registerAccountType");
const profileAccountType = document.getElementById("profileAccountType");
const sellerPostSection = document.getElementById("sellerPostSection");
const browserOverviewNote = document.getElementById("browserOverviewNote");
const adminUserCreationSection = document.getElementById("adminUserCreationSection");
const adminUserManagementSection = document.getElementById("adminUserManagementSection");
const adminUsersList = document.getElementById("adminUsersList");
const productImagesUpload = document.getElementById("productImagesUpload");
const productImagePreview = document.getElementById("productImagePreview");
const productImageStatus = document.getElementById("productImageStatus");
const postImageUpload = document.getElementById("postImageUpload");
const postImageStatus = document.getElementById("postImageStatus");
const notificationToasts = document.getElementById("notificationToasts");

const registerEmailInput = document.getElementById("registerEmail");
const registerEmailCheck = document.getElementById("registerEmailNotificationsEnabled");
const profileEmailInput = document.getElementById("profileEmail");
const profileEmailCheck = document.getElementById("profileEmailNotificationsEnabled");

const viewMap = {
  overview: document.getElementById("viewOverview"),
  marketplace: document.getElementById("viewMarketplace"),
  sell: document.getElementById("viewSell"),
  messages: document.getElementById("viewMessages"),
  profile: document.getElementById("viewProfile"),
};

const navButtons = {
  overview: document.getElementById("navOverview"),
  marketplace: document.getElementById("navMarketplace"),
  sell: document.getElementById("navSell"),
  messages: document.getElementById("navMessages"),
  profile: document.getElementById("navProfile"),
};

showLogin.addEventListener("click", () => toggleAuthTab("login"));
showRegister.addEventListener("click", () => toggleAuthTab("register"));
loginForm.addEventListener("submit", handleLogin);
registerForm.addEventListener("submit", handleRegister);
registerEmailInput.addEventListener("input", () => {
  const hasEmail = !!registerEmailInput.value.trim();
  registerEmailCheck.disabled = !hasEmail;
  if (!hasEmail) {
    registerEmailCheck.checked = false;
  }
});

logoutButton.addEventListener("click", handleLogout);
messageForm.addEventListener("submit", handleSendMessage);
productForm.addEventListener("submit", handleSaveProduct);
cancelEditButton.addEventListener("click", resetProductForm);
profileForm.addEventListener("submit", handleSaveProfile);
profileEmailInput.addEventListener("input", () => {
  const hasEmail = !!profileEmailInput.value.trim();
  profileEmailCheck.disabled = !hasEmail;
  if (!hasEmail) {
    profileEmailCheck.checked = false;
  }
});

passwordForm.addEventListener("submit", handleChangePassword);
deleteAccountForm.addEventListener("submit", handleDeleteAccount);
postForm.addEventListener("submit", handleSavePost);
adminUserForm.addEventListener("submit", handleCreateUser);
productImagesUpload.addEventListener("change", handleProductImageSelection);
postImageUpload.addEventListener("change", handlePostImageSelection);
document.addEventListener("visibilitychange", handleVisibilityChange);
window.addEventListener("focus", handleWindowFocus);

Object.entries(navButtons).forEach(([view, button]) => {
  button.addEventListener("click", () => switchView(view));
});

boot();

async function boot() {
  registerEmailCheck.disabled = !registerEmailInput.value.trim();
  const me = await fetchJson("/api/auth/me", { suppressError: true });
  if (!me || !me.user) {
    showAuth();
    return;
  }

  state.currentUser = me.user;
  showApp();
  await loadAppData();
  startNotificationPolling();
}

async function loadAppData() {
  await Promise.all([
    loadProfile(),
    loadProducts(),
    loadPosts(),
    loadUsers(),
    loadManagedUsers(),
    loadNotificationState({ suppressPopups: true }),
  ]);
  renderOverview();
  renderMarketplace();
  renderMyProducts();
  renderProfileForm();
  renderNotificationBadges();
}

function toggleAuthTab(mode) {
  const loginActive = mode === "login";
  loginForm.classList.toggle("hidden", !loginActive);
  registerForm.classList.toggle("hidden", loginActive);
  showLogin.classList.toggle("active", loginActive);
  showRegister.classList.toggle("active", !loginActive);
  authMessage.textContent = "";
}

async function handleLogin(event) {
  event.preventDefault();
  authMessage.textContent = "";

  const formData = new URLSearchParams();
  formData.set("username", document.getElementById("loginUsername").value.trim().toLowerCase());
  formData.set("password", document.getElementById("loginPassword").value);

  const response = await fetch("/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: formData.toString(),
  });

  if (!response.ok) {
    authMessage.textContent = "Login failed. Check your username and password.";
    return;
  }

  const me = await fetchJson("/api/auth/me");
  state.currentUser = me.user;
  loginForm.reset();
  showApp();
  await loadAppData();
  startNotificationPolling();
}

async function handleRegister(event) {
  event.preventDefault();
  authMessage.textContent = "";

  const payload = {
    username: document.getElementById("registerUsername").value.trim().toLowerCase(),
    password: document.getElementById("registerPassword").value,
    accountType: registerAccountType.value,
    email: document.getElementById("registerEmail").value.trim().toLowerCase(),
    emailNotificationsEnabled: document.getElementById("registerEmailNotificationsEnabled").checked,
  };

  const registered = await fetchJson("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(payload),
  });

  if (!registered) {
    return;
  }

  authMessage.textContent = registered.role === "ADMIN"
    ? "Admin account created. Sign in with your new credentials."
    : "Account created. Sign in with your new credentials.";
  registerForm.reset();
  registerEmailCheck.disabled = true;
  toggleAuthTab("login");
}

async function handleLogout() {
  await fetch("/api/auth/logout", { method: "POST" });
  state.currentUser = null;
  state.selectedUser = null;
  state.profile = null;
  state.products = [];
  state.myProducts = [];
  state.posts = [];
  state.myPosts = [];
  state.managedUsers = [];
  state.unreadConversations = [];
  state.seenUnreadMessageIds = new Set();
  state.notificationsPrimed = false;
  document.title = state.defaultDocumentTitle;
  usersList.innerHTML = "";
  adminUsersList.innerHTML = "";
  stopNotificationPolling();
  showAuth();
}

async function loadProfile() {
  const profile = await fetchJson("/api/profiles/me");
  if (profile) {
    state.profile = profile;
    if (state.currentUser) {
      state.currentUser.displayName = profile.displayName;
      state.currentUser.city = profile.city;
      state.currentUser.role = profile.role;
      state.currentUser.email = profile.email;
      state.currentUser.emailNotificationsEnabled = profile.emailNotificationsEnabled;
    }
  }
}

async function loadNotificationState({ suppressPopups = false } = {}) {
  const payload = await fetchJson("/api/messages/notifications", { suppressError: true });
  if (!payload) {
    return;
  }

  state.unreadConversations = payload.conversations || [];
  syncUnreadCountsIntoUsers();
  renderNotificationBadges();
  if (!suppressPopups) {
    showUnreadPopups();
  } else {
    primeSeenUnreadMessages();
  }
}

async function loadProducts() {
  const [products, myProducts] = await Promise.all([
    fetchJson("/api/products"),
    fetchJson("/api/products/mine"),
  ]);

  if (products) {
    state.products = products;
  }
  if (myProducts) {
    state.myProducts = myProducts;
  }
}

async function loadPosts() {
  const [posts, myPosts] = await Promise.all([
    fetchJson("/api/posts"),
    fetchJson("/api/posts/mine"),
  ]);

  if (posts) {
    state.posts = posts;
  }
  if (myPosts) {
    state.myPosts = myPosts;
  }
}

async function loadUsers() {
  const users = await fetchJson("/api/users");
  if (!users) {
    return;
  }

  state.users = users;
  usersList.innerHTML = "";
  if (users.length === 0) {
    usersList.innerHTML = '<p class="empty-note">No other users found.</p>';
    state.selectedUser = null;
    messageForm.classList.add("hidden");
    return;
  }

  users.forEach((user) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "user-chip";
    button.dataset.username = user.username;
    const unreadBadge = user.unreadCount > 0
      ? `<span class="badge">${user.unreadCount}</span>`
      : "";
    button.innerHTML = `
      <span class="user-chip-head">
        <strong>${escapeHtml(user.displayName || user.username)}</strong>
        ${unreadBadge}
      </span>
      <span>${escapeHtml(user.city || "No city set")}</span>
    `;
    button.addEventListener("click", async () => {
      selectUser(user.username);
      switchView("messages");
    });
    usersList.appendChild(button);
  });

  if (!state.selectedUser) {
    state.selectedUser = users[0];
    selectUser(users[0].username);
  }
  if (state.activeView === "messages" && state.selectedUser) {
    await loadConversation();
  }
}

async function loadManagedUsers() {
  if (!currentUserIsAdmin()) {
    state.managedUsers = [];
    if (adminUsersList) {
      adminUsersList.innerHTML = "";
    }
    return;
  }

  const managedUsers = await fetchJson("/api/users/manage");
  if (managedUsers) {
    state.managedUsers = managedUsers;
  }
}

function selectUser(username) {
  highlightSelectedUser(username);
  state.selectedUser = state.users.find((user) => user.username === username) || null;
}

async function loadConversation() {
  if (!state.selectedUser) {
    messagesList.textContent = "Pick a user to load messages.";
    messagesList.classList.add("empty-state");
    messageForm.classList.add("hidden");
    return;
  }

  conversationTitle.textContent = state.selectedUser.displayName || state.selectedUser.username;
  messageForm.classList.remove("hidden");

  const messages = await fetchJson(`/api/messages/${encodeURIComponent(state.selectedUser.username)}`);
  if (messages) {
    renderMessages(messages);
    await loadUsers();
    await loadNotificationState({ suppressPopups: true });
  }
}

function renderMessages(messages) {
  messagesList.innerHTML = "";

  if (messages.length === 0) {
    messagesList.textContent = "No messages yet. Start the conversation.";
    messagesList.classList.add("empty-state");
    return;
  }

  messagesList.classList.remove("empty-state");
  messages.forEach((message) => {
    const item = document.createElement("article");
    const own = message.senderUsername === state.currentUser.username;
    item.className = `message-bubble ${own ? "own" : ""}`;
    item.innerHTML = `
      <p class="message-meta">${escapeHtml(message.senderUsername)} • ${formatDate(message.timestamp)}</p>
      <p>${escapeHtml(message.content)}</p>
    `;
    messagesList.appendChild(item);
  });

  messagesList.scrollTop = messagesList.scrollHeight;
}

async function handleSendMessage(event) {
  event.preventDefault();
  appMessage.textContent = "";

  if (!state.selectedUser) {
    appMessage.textContent = "Choose a user first.";
    return;
  }

  const content = messageInput.value.trim();
  if (!content) {
    return;
  }

  const message = await fetchJson("/api/messages", {
    method: "POST",
    body: JSON.stringify({
      toUsername: state.selectedUser.username,
      content,
    }),
  });

  if (!message) {
    return;
  }

  messageInput.value = "";
  await loadConversation();
  await loadNotificationState({ suppressPopups: true });
}

async function handleSaveProduct(event) {
  event.preventDefault();
  appMessage.textContent = "";
  setButtonBusy(event.submitter, true, productForm);

  const productId = document.getElementById("productId").value;
  const uploadedImages = await uploadSelectedFiles(productImagesUpload.files, {
    statusElement: productImageStatus,
    emptyMessage: "Upload up to 6 images directly from your device.",
    inputElement: productImagesUpload,
  });
  if (uploadedImages === null) {
    setButtonBusy(event.submitter, false, productForm);
    return;
  }
  state.productDraftImages = [...state.productDraftImages, ...uploadedImages].slice(0, 6);
  renderProductImagePreview();

  const payload = {
    title: document.getElementById("productTitle").value.trim(),
    category: document.getElementById("productCategory").value.trim(),
    price: Number(document.getElementById("productPrice").value),
    description: document.getElementById("productDescription").value.trim(),
    images: state.productDraftImages,
  };

  const url = productId ? `/api/products/${productId}` : "/api/products";
  const method = productId ? "PUT" : "POST";
  const response = await fetchJson(url, {
    method,
    body: JSON.stringify(payload),
  });

  if (!response) {
    setButtonBusy(event.submitter, false, productForm);
    return;
  }

  resetProductForm();
  await loadProducts();
  renderOverview();
  renderMarketplace();
  renderMyProducts();
  appMessage.textContent = productId ? "Listing updated." : "Listing created.";
  setButtonBusy(event.submitter, false, productForm);
}

async function handleSavePost(event) {
  event.preventDefault();
  appMessage.textContent = "";
  setButtonBusy(event.submitter, true, postForm);

  const uploadedImages = await uploadSelectedFiles(postImageUpload.files, {
    statusElement: postImageStatus,
    emptyMessage: "Upload a photo directly from your device.",
    maxFiles: 1,
    inputElement: postImageUpload,
  });
  if (uploadedImages === null) {
    setButtonBusy(event.submitter, false, postForm);
    return;
  }

  const payload = {
    content: document.getElementById("postContent").value.trim(),
    imageUrl: uploadedImages[0] || "",
  };

  const post = await fetchJson("/api/posts", {
    method: "POST",
    body: JSON.stringify(payload),
  });

  if (!post) {
    setButtonBusy(event.submitter, false, postForm);
    return;
  }

  postForm.reset();
  postImageStatus.textContent = "Upload a photo directly from your device.";
  await loadPosts();
  renderOverview();
  appMessage.textContent = "Post published.";
  setButtonBusy(event.submitter, false, postForm);
}

async function handleDeletePost(postId) {
  const response = await fetch(`/api/posts/${postId}`, { method: "DELETE" });
  if (!response.ok) {
    appMessage.textContent = "Could not delete post.";
    return;
  }

  await loadPosts();
  renderOverview();
  appMessage.textContent = "Post deleted.";
}

async function handleDeleteProduct(productId) {
  const response = await fetch(`/api/products/${productId}`, { method: "DELETE" });
  if (!response.ok) {
    appMessage.textContent = "Could not delete listing.";
    return;
  }

  resetProductForm();
  await loadProducts();
  renderOverview();
  renderMarketplace();
  renderMyProducts();
  appMessage.textContent = "Listing deleted.";
}

function startEditProduct(product) {
  document.getElementById("productId").value = product.id;
  document.getElementById("productTitle").value = product.title;
  document.getElementById("productCategory").value = product.category || "";
  document.getElementById("productPrice").value = product.price;
  document.getElementById("productDescription").value = product.description || "";
  state.productDraftImages = [...(product.images || [])];
  renderProductImagePreview();
  document.getElementById("listingFormTitle").textContent = "Edit listing";
  cancelEditButton.classList.remove("hidden");
  switchView("sell");
}

function resetProductForm() {
  productForm.reset();
  document.getElementById("productId").value = "";
  document.getElementById("listingFormTitle").textContent = "New listing";
  cancelEditButton.classList.add("hidden");
  state.productDraftImages = [];
  productImageStatus.textContent = "Upload up to 6 images directly from your device.";
  postImageStatus.textContent = "Upload a photo directly from your device.";
  renderProductImagePreview();
}

async function handleSaveProfile(event) {
  event.preventDefault();

  const payload = {
    accountType: profileAccountType.value,
    displayName: document.getElementById("profileDisplayName").value.trim(),
    city: document.getElementById("profileCity").value.trim(),
    bio: document.getElementById("profileBio").value.trim(),
    email: document.getElementById("profileEmail").value.trim().toLowerCase(),
    emailNotificationsEnabled: document.getElementById("profileEmailNotificationsEnabled").checked,
  };

  const profile = await fetchJson("/api/profiles/me", {
    method: "PUT",
    body: JSON.stringify(payload),
  });

  if (!profile) {
    return;
  }

  state.profile = profile;
  state.currentUser.displayName = profile.displayName;
  state.currentUser.city = profile.city;
  state.currentUser.role = profile.role;
  state.currentUser.email = profile.email;
  state.currentUser.emailNotificationsEnabled = profile.emailNotificationsEnabled;
  renderOverview();
  renderMarketplace();
  renderMyProducts();
  renderProfileForm();
  applyRoleVisibility();
  appMessage.textContent = "Profile updated.";
}

async function handleChangePassword(event) {
  event.preventDefault();
  appMessage.textContent = "";

  const response = await fetch("/api/profiles/me/password", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      currentPassword: document.getElementById("currentPassword").value,
      newPassword: document.getElementById("newPassword").value,
    }),
  });

  if (!response.ok) {
    appMessage.textContent = await readErrorMessage(response);
    return;
  }

  passwordForm.reset();
  appMessage.textContent = "Password updated.";
}

async function handleDeleteAccount(event) {
  event.preventDefault();
  appMessage.textContent = "";

  const confirmed = window.confirm("Delete your account permanently? This cannot be undone.");
  if (!confirmed) {
    return;
  }

  const response = await fetch("/api/profiles/me", {
    method: "DELETE",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      password: document.getElementById("deleteAccountPassword").value,
    }),
  });

  if (!response.ok) {
    appMessage.textContent = await readErrorMessage(response);
    return;
  }

  await handleLogout();
  authMessage.textContent = "Account deleted.";
}

async function handleCreateUser(event) {
  event.preventDefault();
  appMessage.textContent = "";

  const payload = {
    username: document.getElementById("adminCreateUsername").value.trim().toLowerCase(),
    password: document.getElementById("adminCreatePassword").value,
    displayName: document.getElementById("adminCreateDisplayName").value.trim(),
    role: document.getElementById("adminCreateRole").value,
    email: document.getElementById("adminCreateEmail").value.trim().toLowerCase(),
    emailNotificationsEnabled: document.getElementById("adminCreateEmailNotificationsEnabled").checked,
    city: document.getElementById("adminCreateCity").value.trim(),
    bio: document.getElementById("adminCreateBio").value.trim(),
  };

  const createdUser = await fetchJson("/api/users", {
    method: "POST",
    body: JSON.stringify(payload),
  });

  if (!createdUser) {
    return;
  }

  adminUserForm.reset();
  await loadManagedUsers();
  await loadUsers();
  renderAdminUsers();
  appMessage.textContent = `User ${createdUser.username} created.`;
}

async function handleAdminSaveUser(userId) {
  appMessage.textContent = "";
  const prefix = `managed-${userId}`;
  const payload = {
    displayName: document.getElementById(`${prefix}-displayName`).value.trim(),
    role: document.getElementById(`${prefix}-role`).value,
    email: document.getElementById(`${prefix}-email`).value.trim().toLowerCase(),
    emailNotificationsEnabled: document.getElementById(`${prefix}-emailNotificationsEnabled`).checked,
    city: document.getElementById(`${prefix}-city`).value.trim(),
    bio: document.getElementById(`${prefix}-bio`).value.trim(),
  };

  const updated = await fetchJson(`/api/users/${userId}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });

  if (!updated) {
    return;
  }

  await refreshAccountData();
  appMessage.textContent = `Updated ${updated.username}.`;
}

async function handleAdminResetPassword(userId) {
  appMessage.textContent = "";
  const input = document.getElementById(`managed-${userId}-password`);
  const newPassword = input.value;
  if (!newPassword) {
    appMessage.textContent = "Enter a new password first.";
    return;
  }

  const response = await fetch(`/api/users/${userId}/password`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ newPassword }),
  });

  if (!response.ok) {
    appMessage.textContent = await readErrorMessage(response);
    return;
  }

  input.value = "";
  appMessage.textContent = "Password reset.";
}

async function handleAdminDeleteUser(userId) {
  appMessage.textContent = "";
  const confirmed = window.confirm("Delete this user and all their account data?");
  if (!confirmed) {
    return;
  }

  const response = await fetch(`/api/users/${userId}`, {
    method: "DELETE",
  });

  if (!response.ok) {
    appMessage.textContent = await readErrorMessage(response);
    return;
  }

  await refreshAccountData();
  appMessage.textContent = "User deleted.";
}

async function refreshAccountData() {
  await loadProfile();
  await Promise.all([
    loadProducts(),
    loadPosts(),
    loadUsers(),
    loadManagedUsers(),
  ]);
  renderOverview();
  renderMarketplace();
  renderMyProducts();
  renderProfileForm();
  renderAdminUsers();
  applyRoleVisibility();
}

function renderOverview() {
  if (!state.profile) {
    return;
  }

  document.getElementById("overviewDisplayName").textContent = state.profile.displayName || state.profile.username;
  document.getElementById("overviewBio").textContent = state.profile.bio || "No bio added yet.";
  document.getElementById("overviewRole").textContent = formatRole(state.profile.role);
  document.getElementById("overviewProductCount").textContent = String(state.myProducts.length);
  sellerPostSection.classList.toggle("hidden", !currentUserCanSell());
  browserOverviewNote.classList.toggle("hidden", currentUserCanSell());

  renderPostList(document.getElementById("overviewPosts"), state.posts);
  renderProductList(document.getElementById("overviewProducts"), state.products.slice(0, 4), {
    showSeller: true,
    allowSellerView: true,
  });
}

function renderMarketplace() {
  renderProductList(document.getElementById("marketplaceProducts"), state.products, {
    showSeller: true,
    allowSellerView: true,
  });
}

function renderMyProducts() {
  const container = document.getElementById("myProducts");
  if (!currentUserCanSell()) {
    container.innerHTML = '<div class="empty-note">Switch your account to seller to create and manage listings.</div>';
    resetProductForm();
    return;
  }

  renderProductList(container, state.myProducts, {
    editable: true,
  });
}

function renderProductList(container, products, options = {}) {
  container.innerHTML = "";

  if (!products.length) {
    container.innerHTML = '<div class="empty-note">Nothing to show yet.</div>';
    return;
  }

  products.forEach((product) => {
    const card = document.createElement("article");
    card.className = "product-card";
    const image = product.images && product.images.length > 0
      ? `<img src="${escapeAttribute(product.images[0])}" alt="${escapeAttribute(product.title)}" class="product-image">`
      : '<div class="product-image placeholder">No image</div>';

    const sellerLine = options.showSeller
      ? `<p class="meta-line">Seller: ${escapeHtml(product.sellerDisplayName || product.sellerUsername)}</p>`
      : "";

    const actions = [];
    if (options.allowSellerView) {
      actions.push(`<button type="button" class="secondary" data-seller="${escapeAttribute(product.sellerUsername)}">View Seller</button>`);
    }
    if (options.editable) {
      actions.push(`<button type="button" class="secondary" data-edit="${product.id}">Edit</button>`);
      actions.push(`<button type="button" class="secondary" data-delete="${product.id}">Delete</button>`);
    }
    if (!options.editable && product.sellerUsername !== state.currentUser.username) {
      actions.push(`<button type="button" data-message="${escapeAttribute(product.sellerUsername)}">Message Seller</button>`);
    }

    card.innerHTML = `
      ${image}
      <div class="product-copy">
        <p class="eyebrow">${escapeHtml(product.category || "General")}</p>
        <h4>${escapeHtml(product.title)}</h4>
        <p class="price">£${Number(product.price).toFixed(2)}</p>
        <p class="muted">${escapeHtml(product.description || "No description provided.")}</p>
        ${sellerLine}
        <p class="meta-line">${formatDate(product.createdAt)}</p>
        <div class="inline-actions">${actions.join("")}</div>
      </div>
    `;

    card.querySelectorAll("[data-seller]").forEach((button) => {
      button.addEventListener("click", async () => {
        await loadSellerProfile(button.dataset.seller);
        switchView("overview");
      });
    });
    card.querySelectorAll("[data-message]").forEach((button) => {
      button.addEventListener("click", async () => {
        const user = state.users.find((entry) => entry.username === button.dataset.message);
        if (user) {
          highlightSelectedUser(user.username);
          state.selectedUser = user;
          switchView("messages");
        }
      });
    });
    card.querySelectorAll("[data-edit]").forEach((button) => {
      button.addEventListener("click", () => {
        const productToEdit = state.myProducts.find((item) => String(item.id) === button.dataset.edit);
        if (productToEdit) {
          startEditProduct(productToEdit);
        }
      });
    });
    card.querySelectorAll("[data-delete]").forEach((button) => {
      button.addEventListener("click", () => handleDeleteProduct(button.dataset.delete));
    });

    container.appendChild(card);
  });
}

function renderPostList(container, posts) {
  container.innerHTML = "";

  if (!posts.length) {
    container.innerHTML = '<div class="empty-note">No posts yet.</div>';
    return;
  }

  posts.slice(0, 8).forEach((post) => {
    const article = document.createElement("article");
    article.className = "feed-card";

    const image = post.imageUrl
      ? `<img src="${escapeAttribute(post.imageUrl)}" alt="Post image" class="feed-image">`
      : "";

    const actions = [];
    if (post.authorUsername !== state.currentUser.username) {
      actions.push(`<button type="button" data-message="${escapeAttribute(post.authorUsername)}">Message</button>`);
      actions.push(`<button type="button" class="secondary" data-seller="${escapeAttribute(post.authorUsername)}">View Profile</button>`);
    } else {
      actions.push(`<button type="button" class="secondary" data-delete-post="${post.id}">Delete</button>`);
    }

    article.innerHTML = `
      <div class="feed-head">
        <div>
          <h4>${escapeHtml(post.authorDisplayName || post.authorUsername)}</h4>
          <p class="meta-line">@${escapeHtml(post.authorUsername)}${post.authorCity ? ` • ${escapeHtml(post.authorCity)}` : ""}</p>
        </div>
        <p class="meta-line">${formatDate(post.createdAt)}</p>
      </div>
      <p class="feed-copy">${escapeHtml(post.content)}</p>
      ${image}
      <div class="inline-actions">${actions.join("")}</div>
    `;

    article.querySelectorAll("[data-message]").forEach((button) => {
      button.addEventListener("click", async () => {
        const user = state.users.find((entry) => entry.username === button.dataset.message);
        if (user) {
          selectUser(user.username);
          switchView("messages");
        }
      });
    });
    article.querySelectorAll("[data-seller]").forEach((button) => {
      button.addEventListener("click", async () => {
        await loadSellerProfile(button.dataset.seller);
        switchView("overview");
      });
    });
    article.querySelectorAll("[data-delete-post]").forEach((button) => {
      button.addEventListener("click", () => handleDeletePost(button.dataset.deletePost));
    });

    container.appendChild(article);
  });
}

async function loadSellerProfile(username) {
  const profile = await fetchJson(`/api/profiles/${encodeURIComponent(username)}`);
  if (!profile) {
    return;
  }

  document.getElementById("sellerSpotlightTitle").textContent = profile.displayName || profile.username;
  const productsMarkup = profile.products.length
    ? profile.products.map((product) => `<li>${escapeHtml(product.title)} • £${Number(product.price).toFixed(2)}</li>`).join("")
    : "<li>No listings yet.</li>";

  document.getElementById("sellerSpotlight").classList.remove("empty-state");
  document.getElementById("sellerSpotlight").innerHTML = `
    <div class="profile-card">
      <h4>${escapeHtml(profile.displayName || profile.username)}</h4>
      <p class="meta-line">@${escapeHtml(profile.username)}</p>
      <p class="muted">${escapeHtml(profile.bio || "No bio added yet.")}</p>
      <p class="meta-line">City: ${escapeHtml(profile.city || "No city set")}</p>
      <p class="meta-line">Listings: ${profile.productCount}</p>
      <ul class="simple-list">${productsMarkup}</ul>
    </div>
  `;
}

function renderProfileForm() {
  if (!state.profile) {
    return;
  }

  profileAccountType.value = state.profile.role === "SELLER" ? "SELLER" : "USER";
  document.getElementById("profileDisplayName").value = state.profile.displayName || "";
  document.getElementById("profileCity").value = state.profile.city || "";
  document.getElementById("profileBio").value = state.profile.bio || "";
  profileEmailInput.value = state.profile.email || "";
  const hasEmail = !!profileEmailInput.value.trim();
  profileEmailCheck.disabled = !hasEmail;
  profileEmailCheck.checked = hasEmail && !!state.profile.emailNotificationsEnabled;
  document.getElementById("profilePreviewTitle").textContent = state.profile.displayName || state.profile.username;
  document.getElementById("profilePreview").innerHTML = `
    <h4>${escapeHtml(state.profile.displayName || state.profile.username)}</h4>
    <p class="meta-line">@${escapeHtml(state.profile.username)}</p>
    <p class="meta-line">Account type: ${escapeHtml(formatRole(state.profile.role))}</p>
    <p class="meta-line">Message email: ${escapeHtml(state.profile.email || "Not set")}</p>
    <p class="meta-line">Email alerts: ${state.profile.emailNotificationsEnabled ? "On" : "Off"}</p>
    <p class="muted">${escapeHtml(state.profile.bio || "No bio added yet.")}</p>
    <p class="meta-line">City: ${escapeHtml(state.profile.city || "No city set")}</p>
    <p class="meta-line">Listings: ${state.myProducts.length}</p>
  `;
  currentUserLabel.textContent = `${state.profile.displayName || state.profile.username} • ${formatRole(state.profile.role)}`;
  adminUserCreationSection.classList.toggle("hidden", !currentUserIsAdmin());
  adminUserManagementSection.classList.toggle("hidden", !currentUserIsAdmin());
  renderAdminUsers();
}

function renderAdminUsers() {
  if (!currentUserIsAdmin()) {
    adminUsersList.innerHTML = "";
    return;
  }

  if (!state.managedUsers.length) {
    adminUsersList.innerHTML = '<div class="empty-note">No users found yet.</div>';
    return;
  }

  adminUsersList.innerHTML = "";
  state.managedUsers.forEach((user) => {
    const card = document.createElement("article");
    card.className = "admin-user-card";
    const prefix = `managed-${user.id}`;
    const selfNote = user.currentUser ? '<p class="meta-line">This is your account.</p>' : "";

    card.innerHTML = `
      <div class="admin-user-head">
        <div>
          <h4>${escapeHtml(user.displayName || user.username)}</h4>
          <p class="meta-line">@${escapeHtml(user.username)} • ${escapeHtml(formatRole(user.role))}</p>
          ${selfNote}
        </div>
      </div>
      <div class="admin-user-grid">
        <label>
          Display name
          <input id="${prefix}-displayName" value="${escapeAttribute(user.displayName || "")}">
        </label>
        <label>
          Role
          <select id="${prefix}-role">
            <option value="USER"${user.role === "USER" ? " selected" : ""}>Browser / Buyer</option>
            <option value="SELLER"${user.role === "SELLER" ? " selected" : ""}>Seller</option>
            <option value="ADMIN"${user.role === "ADMIN" ? " selected" : ""}>Admin</option>
          </select>
        </label>
        <label>
          Email
          <input id="${prefix}-email" type="email" value="${escapeAttribute(user.email || "")}">
        </label>
        <label class="checkbox-row">
          <input id="${prefix}-emailNotificationsEnabled" type="checkbox"${user.emailNotificationsEnabled ? " checked" : ""}>
          <span>Email on new messages</span>
        </label>
        <label>
          City
          <input id="${prefix}-city" value="${escapeAttribute(user.city || "")}">
        </label>
        <label>
          Bio
          <textarea id="${prefix}-bio" rows="3">${escapeHtml(user.bio || "")}</textarea>
        </label>
        <label>
          Reset password
          <input id="${prefix}-password" type="password" placeholder="New password">
        </label>
      </div>
      <div class="inline-actions">
        <button type="button" data-admin-save="${user.id}">Save User</button>
        <button type="button" class="secondary" data-admin-password="${user.id}">Reset Password</button>
        <button type="button" class="danger-button" data-admin-delete="${user.id}" ${user.currentUser ? "disabled" : ""}>Delete User</button>
      </div>
    `;

    card.querySelector(`[data-admin-save="${user.id}"]`).addEventListener("click", () => handleAdminSaveUser(user.id));
    card.querySelector(`[data-admin-password="${user.id}"]`).addEventListener("click", () => handleAdminResetPassword(user.id));
    card.querySelector(`[data-admin-delete="${user.id}"]`).addEventListener("click", () => handleAdminDeleteUser(user.id));
    adminUsersList.appendChild(card);
  });
}

function handleProductImageSelection() {
  const count = productImagesUpload.files.length;
  productImageStatus.textContent = count
    ? `${count} image${count === 1 ? "" : "s"} selected. They will upload when you save the listing.`
    : "Upload up to 6 images directly from your device.";
}

function handlePostImageSelection() {
  const file = postImageUpload.files[0];
  postImageStatus.textContent = file
    ? `${file.name} selected. It will upload when you publish the post.`
    : "Upload a photo directly from your device.";
}

function renderProductImagePreview() {
  productImagePreview.innerHTML = "";

  if (!state.productDraftImages.length) {
    productImagePreview.innerHTML = '<div class="empty-note compact-empty">No images added yet.</div>';
    return;
  }

  state.productDraftImages.forEach((imageUrl, index) => {
    const card = document.createElement("article");
    card.className = "image-preview-card";
    card.innerHTML = `
      <img src="${escapeAttribute(imageUrl)}" alt="Listing image ${index + 1}" class="image-preview-thumb">
      <button type="button" class="secondary" data-remove-image="${index}">Remove</button>
    `;
    card.querySelector("[data-remove-image]").addEventListener("click", () => {
      state.productDraftImages = state.productDraftImages.filter((_, imageIndex) => imageIndex !== index);
      renderProductImagePreview();
    });
    productImagePreview.appendChild(card);
  });
}

async function uploadSelectedFiles(fileList, options = {}) {
  const {
    statusElement,
    emptyMessage = "",
    maxFiles = 6,
    inputElement = null,
  } = options;

  const files = Array.from(fileList || []);
  if (files.length === 0) {
    return [];
  }
  if (files.length > maxFiles) {
    if (statusElement) {
      statusElement.textContent = `You can upload up to ${maxFiles} image${maxFiles === 1 ? "" : "s"} at once.`;
    }
    return null;
  }

  const formData = new FormData();
  files.forEach((file) => formData.append("files", file));

  if (statusElement) {
    statusElement.textContent = `Uploading ${files.length} image${files.length === 1 ? "" : "s"}...`;
  }

  const response = await fetch("/api/media", {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    if (statusElement) {
      statusElement.textContent = message;
    }
    return null;
  }

  const payload = await response.json();
  if (statusElement) {
    statusElement.textContent = emptyMessage;
  }
  if (inputElement) {
    inputElement.value = "";
  }
  return payload.urls || [];
}

function setButtonBusy(button, busy, form = null) {
  if (!button) {
    return;
  }
  if (!button.dataset.originalLabel) {
    button.dataset.originalLabel = button.textContent;
  }
  button.disabled = busy;
  button.textContent = busy ? "Working..." : button.dataset.originalLabel;
  if (form) {
    form.querySelectorAll("button, input, textarea, select").forEach((element) => {
      if (element === button) {
        return;
      }
      element.disabled = busy;
    });
  }
}

function switchView(view) {
  state.activeView = view;
  Object.entries(viewMap).forEach(([name, element]) => {
    element.classList.toggle("hidden", name !== view);
  });
  Object.entries(navButtons).forEach(([name, button]) => {
    button.classList.toggle("active", name === view);
  });
  appMessage.textContent = "";
  if (view === "messages" && state.selectedUser) {
    loadConversation();
  }
}

function startNotificationPolling() {
  stopNotificationPolling();
  state.notificationPollHandle = window.setInterval(() => {
    if (!state.currentUser) {
      return;
    }
    loadNotificationState();
  }, NOTIFICATION_POLL_INTERVAL_MS);
}

function stopNotificationPolling() {
  if (state.notificationPollHandle) {
    window.clearInterval(state.notificationPollHandle);
    state.notificationPollHandle = null;
  }
}

function handleVisibilityChange() {
  if (document.visibilityState === "visible") {
    refreshNotificationsSoon({ suppressPopups: true });
  }
}

function handleWindowFocus() {
  refreshNotificationsSoon({ suppressPopups: true });
}

function showAuth() {
  authPanel.classList.remove("hidden");
  appPanel.classList.add("hidden");
  currentUserLabel.textContent = "";
}

function showApp() {
  authPanel.classList.add("hidden");
  appPanel.classList.remove("hidden");
  currentUserLabel.textContent = `${state.currentUser.displayName || state.currentUser.username} • ${formatRole(state.currentUser.role)}`;
  applyRoleVisibility();
  switchView("overview");
}

function syncUnreadCountsIntoUsers() {
  const unreadByUsername = new Map(
    state.unreadConversations.map((conversation) => [conversation.username, conversation.unreadCount]),
  );
  state.users = state.users.map((user) => ({
    ...user,
    unreadCount: unreadByUsername.get(user.username) || 0,
  }));
}

function renderNotificationBadges() {
  const totalUnread = state.unreadConversations.reduce((sum, conversation) => sum + conversation.unreadCount, 0);
  messagesUnreadBadge.textContent = String(totalUnread);
  messagesUnreadBadge.classList.toggle("hidden", totalUnread === 0);
  navButtons.messages.classList.toggle("has-unread", totalUnread > 0);
  document.title = totalUnread > 0
    ? `(${totalUnread}) ${state.defaultDocumentTitle}`
    : state.defaultDocumentTitle;
}

function primeSeenUnreadMessages() {
  state.seenUnreadMessageIds = new Set(
    state.unreadConversations.map((conversation) => String(conversation.latestMessageId)),
  );
  state.notificationsPrimed = true;
}

function showUnreadPopups() {
  const currentIds = new Set();
  state.unreadConversations.forEach((conversation) => {
    const messageId = String(conversation.latestMessageId);
    currentIds.add(messageId);

    if (!state.notificationsPrimed) {
      return;
    }
    if (state.seenUnreadMessageIds.has(messageId)) {
      return;
    }

    if (state.activeView === "messages"
        && state.selectedUser
        && state.selectedUser.username === conversation.username) {
      loadConversation();
      return;
    }

    showNotificationToast(conversation);
  });

  state.seenUnreadMessageIds = currentIds;
  state.notificationsPrimed = true;
}

function showNotificationToast(conversation) {
  const toast = document.createElement("button");
  toast.type = "button";
  toast.className = "toast-card";
  toast.innerHTML = `
    <strong>New message from ${escapeHtml(conversation.displayName || conversation.username)}</strong>
    <span>${escapeHtml(conversation.preview || "Open Messages to reply.")}</span>
  `;
  toast.addEventListener("click", async () => {
    toast.remove();
    selectUser(conversation.username);
    switchView("messages");
  });
  notificationToasts.appendChild(toast);
  window.setTimeout(() => toast.remove(), 6000);
}

function refreshNotificationsSoon({ suppressPopups = false } = {}) {
  if (!state.currentUser) {
    return;
  }
  window.setTimeout(() => {
    loadNotificationState({ suppressPopups });
  }, 0);
}

function applyRoleVisibility() {
  const canSell = currentUserCanSell();
  navButtons.sell.classList.toggle("hidden", !canSell);
  productForm.classList.toggle("hidden", !canSell);
  adminUserCreationSection.classList.toggle("hidden", !currentUserIsAdmin());
  adminUserManagementSection.classList.toggle("hidden", !currentUserIsAdmin());

  if (!canSell && state.activeView === "sell") {
    switchView("overview");
  }
}

async function fetchJson(url, options = {}) {
  const { suppressError = false, ...fetchOptions } = options;
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...(fetchOptions.headers || {}),
    },
    ...fetchOptions,
  });

  if (!response.ok) {
    if (response.status === 401 && suppressError) {
      return null;
    }

    const errorMessage = await readErrorMessage(response);
    const target = state.currentUser ? appMessage : authMessage;
    target.textContent = errorMessage || "Request failed.";

    if (response.status === 401) {
      showAuth();
    }

    return null;
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

async function readErrorMessage(response) {
  const contentType = response.headers.get("Content-Type") || "";
  if (contentType.includes("application/json")) {
    const payload = await response.json();
    if (Array.isArray(payload.details) && payload.details.length > 0) {
      return payload.details.join("\n");
    }
    return payload.message || payload.error || "Request failed.";
  }

  const text = await response.text();
  return text || "Request failed.";
}

function formatDate(value) {
  if (!value) {
    return "Just now";
  }
  return new Date(value).toLocaleString();
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function escapeAttribute(value) {
  return escapeHtml(value);
}

function highlightSelectedUser(username) {
  usersList.querySelectorAll(".user-chip").forEach((chip) => {
    chip.classList.toggle("selected", chip.dataset.username === username);
  });
}

function currentUserCanSell() {
  return state.currentUser && ["SELLER", "ADMIN"].includes(state.currentUser.role);
}

function currentUserIsAdmin() {
  return state.currentUser && state.currentUser.role === "ADMIN";
}

function formatRole(role) {
  if (role === "SELLER") {
    return "Seller";
  }
  if (role === "ADMIN") {
    return "Admin";
  }
  return "Browser / Buyer";
}
