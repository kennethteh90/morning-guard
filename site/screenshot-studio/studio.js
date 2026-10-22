import {
  DEFAULT_SLIDES,
  PRESETS,
  TEMPLATES,
  defaultProject,
  renderSlideToCanvas,
} from "./studio-core.js";

const state = defaultProject({
  slides: DEFAULT_SLIDES.map((s) => ({ ...s })),
});

const previewCanvas = document.getElementById("previewCanvas");
const exportLog = document.getElementById("exportLog");

function $(id) {
  return document.getElementById(id);
}

function preset() {
  return PRESETS[state.presetId];
}

function activeSlide() {
  return state.slides[state.activeIndex];
}

function refreshPreview() {
  const p = preset();
  renderSlideToCanvas(previewCanvas, state.activeIndex, state);
  $("canvasMeta").textContent = `${p.label} · ${p.width}×${p.height}px · slide ${state.activeIndex + 1} of ${state.slides.length}`;
}

function rebuildSlideList() {
  const ul = $("slideList");
  ul.innerHTML = "";
  state.slides.forEach((slide, i) => {
    const li = document.createElement("li");
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = i === state.activeIndex ? "active" : "";
    btn.innerHTML = `<strong>Slide ${i + 1}</strong><span class="meta">${escapeHtml(slide.title)}</span>`;
    btn.addEventListener("click", () => {
      state.activeIndex = i;
      syncFormFromSlide();
      rebuildSlideList();
      refreshPreview();
    });
    li.appendChild(btn);
    ul.appendChild(li);
  });
}

function escapeHtml(s) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function syncFormFromSlide() {
  const slide = activeSlide();
  $("slideTitle").value = slide.title;
  $("slideSubtitle").value = slide.subtitle;
}

function applyFormToSlide() {
  const slide = activeSlide();
  slide.title = $("slideTitle").value.trim() || "Title";
  slide.subtitle = $("slideSubtitle").value.trim() || "";
  rebuildSlideList();
  refreshPreview();
}

function loadImageFile(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      resolve(img);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("Could not load image"));
    };
    img.src = url;
  });
}

function logExport(msg) {
  const line = document.createElement("div");
  line.textContent = msg;
  exportLog.prepend(line);
}

function downloadBlob(blob, filename) {
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
}

async function exportSlide(index) {
  const off = document.createElement("canvas");
  renderSlideToCanvas(off, index, state);
  return new Promise((resolve) => {
    off.toBlob((blob) => {
      if (!blob) return resolve(null);
      const p = preset();
      const name = `morning-guard_${p.id}_slide-${String(index + 1).padStart(2, "0")}.png`;
      downloadBlob(blob, name);
      logExport(`Saved ${name}`);
      resolve(blob);
    }, "image/png");
  });
}

async function exportAll() {
  exportLog.innerHTML = "";
  for (let i = 0; i < state.slides.length; i++) {
    await exportSlide(i);
    await new Promise((r) => setTimeout(r, 120));
  }
  logExport(`Done — ${state.slides.length} files (${preset().label}).`);
}

function initControls() {
  const presetSelect = $("presetSelect");
  for (const p of Object.values(PRESETS)) {
    const opt = document.createElement("option");
    opt.value = p.id;
    opt.textContent = p.label;
    presetSelect.appendChild(opt);
  }
  presetSelect.value = state.presetId;
  presetSelect.addEventListener("change", () => {
    state.presetId = presetSelect.value;
    refreshPreview();
  });

  const templateSelect = $("templateSelect");
  for (const t of Object.values(TEMPLATES)) {
    const opt = document.createElement("option");
    opt.value = t.id;
    opt.textContent = t.label;
    templateSelect.appendChild(opt);
  }
  templateSelect.value = state.templateId;
  templateSelect.addEventListener("change", () => {
    state.templateId = templateSelect.value;
    refreshPreview();
  });

  $("showFrame").checked = state.showDeviceFrame;
  $("showFrame").addEventListener("change", (e) => {
    state.showDeviceFrame = e.target.checked;
    refreshPreview();
  });

  $("panoramic").checked = state.panoramic;
  $("panoramic").addEventListener("change", (e) => {
    state.panoramic = e.target.checked;
    refreshPreview();
  });

  $("slideTitle").addEventListener("input", applyFormToSlide);
  $("slideSubtitle").addEventListener("input", applyFormToSlide);

  $("btnAddSlide").addEventListener("click", () => {
    state.slides.push({
      title: "New slide",
      subtitle: "Subtitle",
      image: null,
    });
    state.activeIndex = state.slides.length - 1;
    syncFormFromSlide();
    rebuildSlideList();
    refreshPreview();
  });

  $("btnRemoveSlide").addEventListener("click", () => {
    if (state.slides.length <= 1) return;
    state.slides.splice(state.activeIndex, 1);
    state.activeIndex = Math.min(state.activeIndex, state.slides.length - 1);
    syncFormFromSlide();
    rebuildSlideList();
    refreshPreview();
  });

  $("btnExportCurrent").addEventListener("click", () => exportSlide(state.activeIndex));
  $("btnExportAll").addEventListener("click", exportAll);

  $("screenshotInput").addEventListener("change", async (e) => {
    const files = Array.from(e.target.files || []);
    if (!files.length) return;
    let idx = state.activeIndex;
    for (const file of files) {
      if (!file.type.startsWith("image/")) continue;
      try {
        const img = await loadImageFile(file);
        if (idx >= state.slides.length) {
          state.slides.push({
            title: `Slide ${idx + 1}`,
            subtitle: "",
            image: img,
          });
        } else {
          state.slides[idx].image = img;
        }
        idx += 1;
      } catch {
        logExport(`Failed: ${file.name}`);
      }
    }
    state.activeIndex = Math.min(state.activeIndex, state.slides.length - 1);
    syncFormFromSlide();
    rebuildSlideList();
    refreshPreview();
    e.target.value = "";
  });

  $("btnResetCopy").addEventListener("click", () => {
    state.slides = DEFAULT_SLIDES.map((s) => ({ ...s, image: null }));
    state.activeIndex = 0;
    syncFormFromSlide();
    rebuildSlideList();
    refreshPreview();
  });
}

state.activeIndex = 0;
initControls();
rebuildSlideList();
syncFormFromSlide();
refreshPreview();
