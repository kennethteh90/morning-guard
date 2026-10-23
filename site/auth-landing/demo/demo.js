const SCENES = [
  { id: "home-locked", title: "Home — gate locked", note: "Morning goal CTA, shields card" },
  { id: "goal-bible", title: "Morning goal — Bible", note: "Verses, highlights, progress" },
  { id: "picker", title: "Book & chapter", note: "Dual scroll panels" },
  { id: "peace", title: "Home — peace break", note: "Shields stay on (iOS)" },
  { id: "highlights", title: "Saved highlights", note: "Notes + colors" },
  { id: "stats", title: "Streak & stats", note: "History list" },
  { id: "settings", title: "Settings", note: "Window, backup, notes" },
  { id: "onboarding", title: "Onboarding", note: "Apps + routine" },
];

let index = 0;

function el(id) {
  return document.getElementById(id);
}

function renderSceneList() {
  const list = el("sceneList");
  list.innerHTML = "";
  SCENES.forEach((scene, i) => {
    const li = document.createElement("li");
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = i === index ? "active" : "";
    btn.innerHTML = `<strong>${scene.title}</strong><span>${scene.note}</span>`;
    btn.addEventListener("click", () => goTo(i));
    li.appendChild(btn);
    list.appendChild(li);
  });
}

function syncSceneLabels(scene) {
  el("sceneLabel").textContent = scene.title;
  el("sceneNote").textContent = scene.note;
  const fsLabel = el("sceneLabelFs");
  const fsNote = el("sceneNoteFs");
  if (fsLabel) fsLabel.textContent = scene.title;
  if (fsNote) fsNote.textContent = scene.note;
}

function goTo(i) {
  index = (i + SCENES.length) % SCENES.length;
  const scene = SCENES[index];
  document.querySelectorAll(".screen").forEach((node) => {
    node.classList.toggle("active", node.dataset.screen === scene.id);
  });
  syncSceneLabels(scene);
  renderSceneList();
  history.replaceState(null, "", `#${scene.id}`);
}

function togglePresenter() {
  const shell = el("demoShell");
  if (shell.classList.contains("fullscreen")) setFullscreen(false);
  shell.classList.toggle("presenter");
  el("btnPresenter").textContent = shell.classList.contains("presenter")
    ? "Exit presenter"
    : "Presenter mode";
}

function updateFullscreenUi() {
  const shell = el("demoShell");
  const on = shell.classList.contains("fullscreen");
  const btn = el("btnFullscreen");
  if (btn) btn.textContent = on ? "Exit fullscreen" : "Fullscreen demo";
  const top = el("fullscreenTop");
  if (top) top.hidden = !on;
}

function setFullscreen(on) {
  const shell = el("demoShell");
  const entering = on ?? !shell.classList.contains("fullscreen");
  if (entering && shell.classList.contains("presenter")) {
    shell.classList.remove("presenter");
    el("btnPresenter").textContent = "Presenter mode";
  }
  shell.classList.toggle("fullscreen", entering);
  document.body.classList.toggle("demo-fullscreen-active", entering);
  updateFullscreenUi();
  if (entering && shell.requestFullscreen) {
    shell.requestFullscreen().catch(() => {});
  } else if (!entering && document.fullscreenElement === shell) {
    document.exitFullscreen().catch(() => {});
  }
}

function toggleFullscreen() {
  setFullscreen(!el("demoShell").classList.contains("fullscreen"));
}

function initFromHash() {
  const hash = location.hash.replace("#", "");
  const i = SCENES.findIndex((s) => s.id === hash);
  if (i >= 0) index = i;
}

document.addEventListener("DOMContentLoaded", () => {
  const shell = el("demoShell");
  initFromHash();
  renderSceneList();
  goTo(index);
  updateFullscreenUi();

  el("btnPrev").addEventListener("click", () => goTo(index - 1));
  el("btnNext").addEventListener("click", () => goTo(index + 1));
  el("btnPresenter").addEventListener("click", togglePresenter);
  el("btnFullscreen").addEventListener("click", toggleFullscreen);
  el("btnExitFullscreen").addEventListener("click", () => setFullscreen(false));

  document.addEventListener("fullscreenchange", () => {
    const shell = el("demoShell");
    if (!document.fullscreenElement && shell.classList.contains("fullscreen")) {
      shell.classList.remove("fullscreen");
      document.body.classList.remove("demo-fullscreen-active");
      updateFullscreenUi();
    }
  });

  let touchStartX = 0;
  let touchStartY = 0;
  shell.addEventListener(
    "touchstart",
    (e) => {
      if (!el("demoShell").classList.contains("fullscreen")) return;
      const t = e.touches[0];
      if (!t) return;
      touchStartX = t.clientX;
      touchStartY = t.clientY;
    },
    { passive: true },
  );
  shell.addEventListener(
    "touchend",
    (e) => {
      if (!el("demoShell").classList.contains("fullscreen")) return;
      const t = e.changedTouches[0];
      const dx = t.clientX - touchStartX;
      const dy = t.clientY - touchStartY;
      if (Math.abs(dx) < 48 || Math.abs(dx) < Math.abs(dy)) return;
      if (dx < 0) goTo(index + 1);
      else goTo(index - 1);
    },
    { passive: true },
  );

  document.addEventListener("keydown", (e) => {
    if (e.key === "ArrowRight" || e.key === " ") {
      e.preventDefault();
      goTo(index + 1);
    }
    if (e.key === "ArrowLeft") {
      e.preventDefault();
      goTo(index - 1);
    }
    if (e.key === "p" && !e.metaKey && !e.ctrlKey) togglePresenter();
  });
});
