/**
 * Shared render core for browser (studio.js) and Node (scripts/store-screenshots/compose.mjs).
 */

export const BRAND = {
  forest: "#1B4332",
  forestLight: "#2D6A4F",
  mint: "#95D5B2",
  dawn: "#D8F3DC",
  cream: "#F8F9FA",
  ink: "#1A1C1E",
  darkBg: "#101412",
};

export const PRESETS = {
  ios_67: {
    id: "ios_67",
    label: "App Store — iPhone 6.7\"",
    platform: "apple",
    width: 1290,
    height: 2796,
    kind: "phone",
  },
  ios_65: {
    id: "ios_65",
    label: "App Store — iPhone 6.5\"",
    platform: "apple",
    width: 1284,
    height: 2778,
    kind: "phone",
  },
  play_phone: {
    id: "play_phone",
    label: "Google Play — phone (9:16)",
    platform: "google",
    width: 1080,
    height: 1920,
    kind: "phone",
  },
  play_feature: {
    id: "play_feature",
    label: "Google Play — feature graphic",
    platform: "google",
    width: 1024,
    height: 500,
    kind: "feature",
  },
};

export const DEFAULT_SLIDES = [
  {
    title: "Protect your morning",
    subtitle: "Hold notifications until you finish your goal",
    image: null,
  },
  {
    title: "One calm digest",
    subtitle: "Alarms and bypass apps still get through",
    image: null,
  },
  {
    title: "Choose your goal",
    subtitle: "Bible chapter, focus timer, or steps",
    image: null,
  },
  {
    title: "Start in peace",
    subtitle: "Optional alarm and emergency bypass",
    image: null,
  },
];

export const TEMPLATES = {
  forest_dawn: {
    id: "forest_dawn",
    label: "Forest dawn (light)",
    titleColor: BRAND.forest,
    subtitleColor: "#3d5a48",
    drawBg(ctx, w, h, slideIndex, slideCount) {
      const g = ctx.createLinearGradient(0, 0, w, h);
      g.addColorStop(0, BRAND.dawn);
      g.addColorStop(0.55, BRAND.cream);
      g.addColorStop(1, "#e8f5e9");
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, w, h);
      if (slideCount > 1) {
        drawPanoramicBand(ctx, w, h, slideIndex, slideCount, BRAND.mint, 0.22);
      }
    },
  },
  forest_dark: {
    id: "forest_dark",
    label: "Forest night (dark)",
    titleColor: BRAND.dawn,
    subtitleColor: BRAND.mint,
    drawBg(ctx, w, h, slideIndex, slideCount) {
      const g = ctx.createLinearGradient(0, 0, w * 0.2, h);
      g.addColorStop(0, "#1a2e24");
      g.addColorStop(0.5, BRAND.darkBg);
      g.addColorStop(1, "#0a0c0b");
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, w, h);
      if (slideCount > 1) {
        drawPanoramicBand(ctx, w, h, slideIndex, slideCount, BRAND.forestLight, 0.35);
      }
    },
  },
  mint_gradient: {
    id: "mint_gradient",
    label: "Mint gradient",
    titleColor: "#ffffff",
    subtitleColor: "rgba(255,255,255,0.85)",
    drawBg(ctx, w, h, slideIndex, slideCount) {
      const g = ctx.createLinearGradient(0, 0, w, h * 0.9);
      g.addColorStop(0, BRAND.forestLight);
      g.addColorStop(0.45, BRAND.forest);
      g.addColorStop(1, "#0d2818");
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, w, h);
      if (slideCount > 1) {
        drawPanoramicBand(ctx, w, h, slideIndex, slideCount, BRAND.mint, 0.18);
      }
    },
  },
};

export function defaultProject(overrides = {}) {
  return {
    slides: DEFAULT_SLIDES.map((s) => ({ ...s })),
    presetId: "ios_67",
    templateId: "forest_dawn",
    showDeviceFrame: true,
    panoramic: true,
    ...overrides,
  };
}

function drawPanoramicBand(ctx, w, h, slideIndex, slideCount, color, alpha) {
  const bandH = h * 0.12;
  const y = h * 0.38;
  const offsetX = -slideIndex * w;
  ctx.save();
  ctx.globalAlpha = alpha;
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.rect(0, y, w, bandH);
  ctx.clip();
  for (let i = 0; i < slideCount; i++) {
    const x = offsetX + i * w + w * 0.1;
    ctx.beginPath();
    ctx.ellipse(x + w * 0.4, y + bandH * 0.5, w * 0.55, bandH * 0.9, 0, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.restore();
}

function titleFitsOneLine(ctx, title, maxWidth, titleSize, fontStack) {
  ctx.font = `bold ${titleSize}px ${fontStack}`;
  return ctx.measureText(title.trim()).width <= maxWidth;
}

/** Shrink title until it fits on a single line (store listing convention). */
function fitTitleOneLine(ctx, title, maxWidth, titleSize, fontStack, minSize = 44) {
  let size = titleSize;
  while (size > minSize && !titleFitsOneLine(ctx, title, maxWidth, size, fontStack)) {
    size = Math.round(size * 0.94);
  }
  return size;
}

function measureMarketingBlockHeight(ctx, slide, maxWidth, titleSize, subSize, fontStack) {
  const titleH = Math.round(titleSize * 1.1);

  ctx.font = `600 ${subSize}px ${fontStack}`;
  const subLines = wrapText(ctx, slide.subtitle, maxWidth).slice(0, 3);
  const subH =
    (subLines.length > 0 ? Math.round(subSize * 0.45) : 0) +
    subLines.length * Math.round(subSize * 1.22);

  return Math.round(titleSize * 0.35) + titleH + subH;
}

function fitMarketingTypography(ctx, slide, w, padX, marketingBandH, titleScale, subtitleScale, fontStack) {
  const maxWidth = w - padX * 2;
  const budget = marketingBandH * 0.9;
  let titleSize = fitTitleOneLine(
    ctx,
    slide.title,
    maxWidth,
    Math.round(w * titleScale),
    fontStack,
  );
  let subSize = Math.round(w * subtitleScale);

  for (let i = 0; i < 12; i++) {
    const blockH = measureMarketingBlockHeight(ctx, slide, maxWidth, titleSize, subSize, fontStack);
    if (blockH <= budget) break;
    titleSize = fitTitleOneLine(
      ctx,
      slide.title,
      maxWidth,
      Math.round(titleSize * 0.94),
      fontStack,
    );
    subSize = Math.round(subSize * 0.94);
  }
  return { titleSize, subSize };
}

function wrapText(ctx, text, maxWidth) {
  const words = text.split(/\s+/);
  const lines = [];
  let line = "";
  for (const word of words) {
    const test = line ? `${line} ${word}` : word;
    if (ctx.measureText(test).width > maxWidth && line) {
      lines.push(line);
      line = word;
    } else {
      line = test;
    }
  }
  if (line) lines.push(line);
  return lines;
}

function roundRect(ctx, x, y, w, h, r) {
  const rr = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + rr, y);
  ctx.arcTo(x + w, y, x + w, y + h, rr);
  ctx.arcTo(x + w, y + h, x, y + h, rr);
  ctx.arcTo(x, y + h, x, y, rr);
  ctx.arcTo(x, y, x + w, y, rr);
  ctx.closePath();
}

function drawCoverImage(ctx, img, x, y, w, h) {
  const ir = img.width / img.height;
  const tr = w / h;
  let sw;
  let sh;
  let sx;
  let sy;
  if (ir > tr) {
    sh = img.height;
    sw = sh * tr;
    sx = (img.width - sw) / 2;
    sy = 0;
  } else {
    sw = img.width;
    sh = sw / tr;
    sx = 0;
    sy = (img.height - sh) / 2;
  }
  ctx.drawImage(img, sx, sy, sw, sh, x, y, w, h);
}

function drawPhoneFrame(ctx, x, y, pw, ph, screenshot) {
  const radius = pw * 0.11;
  const bezel = pw * 0.025;
  const innerX = x + bezel;
  const innerY = y + bezel;
  const innerW = pw - bezel * 2;
  const innerH = ph - bezel * 2;
  const innerR = radius * 0.85;

  ctx.save();
  ctx.shadowColor = "rgba(0,0,0,0.35)";
  ctx.shadowBlur = pw * 0.04;
  ctx.shadowOffsetY = pw * 0.02;
  roundRect(ctx, x, y, pw, ph, radius);
  ctx.fillStyle = "#1c1c1e";
  ctx.fill();
  ctx.shadowColor = "transparent";

  roundRect(ctx, innerX, innerY, innerW, innerH, innerR);
  ctx.save();
  ctx.clip();
  if (screenshot) {
    drawCoverImage(ctx, screenshot, innerX, innerY, innerW, innerH);
  } else {
    ctx.fillStyle = "#2a2f34";
    ctx.fillRect(innerX, innerY, innerW, innerH);
    ctx.fillStyle = "rgba(255,255,255,0.35)";
    ctx.font = `500 ${innerW * 0.06}px system-ui, sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText("Drop screenshot", innerX + innerW / 2, innerY + innerH / 2);
  }
  ctx.restore();

  const notchW = pw * 0.32;
  const notchH = ph * 0.028;
  const notchX = x + (pw - notchW) / 2;
  const notchY = y + bezel * 0.6;
  roundRect(ctx, notchX, notchY, notchW, notchH, notchH * 0.45);
  ctx.fillStyle = "#0d0d0f";
  ctx.fill();

  ctx.restore();
}

function drawFeatureGraphic(ctx, w, h, slide, t) {
  const pad = w * 0.06;
  const iconSize = h * 0.62;
  const iconX = pad;
  const iconY = (h - iconSize) / 2;
  roundRect(ctx, iconX, iconY, iconSize, iconSize, iconSize * 0.22);
  const g = ctx.createLinearGradient(iconX, iconY, iconX + iconSize, iconY + iconSize);
  g.addColorStop(0, BRAND.forestLight);
  g.addColorStop(1, BRAND.forest);
  ctx.fillStyle = g;
  ctx.fill();
  ctx.fillStyle = BRAND.mint;
  ctx.font = `700 ${iconSize * 0.38}px system-ui, sans-serif`;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText("MG", iconX + iconSize / 2, iconY + iconSize / 2);

  const textX = iconX + iconSize + pad * 0.85;
  const textW = w - textX - pad;
  ctx.textAlign = "left";
  ctx.fillStyle = t.titleColor;
  ctx.font = `700 ${h * 0.22}px system-ui, sans-serif`;
  ctx.fillText("Morning Guard", textX, h * 0.38, textW);
  ctx.fillStyle = t.subtitleColor;
  ctx.font = `500 ${h * 0.13}px system-ui, sans-serif`;
  const lines = wrapText(ctx, slide.subtitle || slide.title, textW);
  let y = h * 0.52;
  for (const line of lines.slice(0, 2)) {
    ctx.fillText(line, textX, y, textW);
    y += h * 0.16;
  }
}

/**
 * @param {HTMLCanvasElement | import('canvas').Canvas} canvas
 * @param {number} slideIndex
 * @param {ReturnType<typeof defaultProject>} project
 */
export function renderSlideToCanvas(canvas, slideIndex, project) {
  const p = PRESETS[project.presetId] ?? PRESETS.ios_67;
  const t = TEMPLATES[project.templateId] ?? TEMPLATES.forest_dawn;
  const slide = project.slides[slideIndex];
  if (!slide) return;

  canvas.width = p.width;
  canvas.height = p.height;
  const ctx = canvas.getContext("2d");
  const w = p.width;
  const h = p.height;

  t.drawBg(
    ctx,
    w,
    h,
    project.panoramic ? slideIndex : 0,
    project.panoramic ? project.slides.length : 1,
  );

  if (p.kind === "feature") {
    drawFeatureGraphic(ctx, w, h, slide, t);
    return;
  }

  const padX = w * 0.07;
  const marketingBandH = h * (project.marketingBandRatio ?? 0.3);
  const fontStack = "Helvetica Neue, Helvetica, Arial, sans-serif";
  const { titleSize, subSize } = fitMarketingTypography(
    ctx,
    slide,
    w,
    padX,
    marketingBandH,
    project.titleScale ?? 0.138,
    project.subtitleScale ?? 0.068,
    fontStack,
  );
  let textY = Math.round(h * 0.06);
  ctx.textAlign = "center";
  ctx.textBaseline = "top";
  ctx.fillStyle = t.titleColor;
  ctx.font = `bold ${titleSize}px ${fontStack}`;
  const titleLine = slide.title.trim();
  ctx.save();
  ctx.shadowColor = "rgba(0, 0, 0, 0.18)";
  ctx.shadowBlur = Math.max(4, titleSize * 0.08);
  ctx.shadowOffsetY = Math.max(2, titleSize * 0.04);
  ctx.fillText(titleLine, w / 2, textY);
  ctx.restore();
  textY += Math.round(titleSize * 1.1);

  textY += Math.round(subSize * 0.45);
  ctx.fillStyle = t.subtitleColor;
  ctx.font = `600 ${subSize}px ${fontStack}`;
  const subLines = wrapText(ctx, slide.subtitle, w - padX * 2).slice(0, 3);
  for (const line of subLines) {
    ctx.fillText(line, w / 2, textY);
    textY += Math.round(subSize * 1.22);
  }

  const topTextBottom = Math.min(h * 0.34, Math.max(marketingBandH, textY + subSize * 0.35));
  const bottomPad = h * 0.045;
  const availH = h - topTextBottom - bottomPad;
  const phoneAspect = 9 / 19.5;
  let phoneH = availH;
  let phoneW = phoneH * phoneAspect;
  const maxPhoneW = w * 0.8;
  if (phoneW > maxPhoneW) {
    phoneW = maxPhoneW;
    phoneH = phoneW / phoneAspect;
  }
  const phoneX = (w - phoneW) / 2;
  const phoneY = topTextBottom + (availH - phoneH) / 2;

  if (project.showDeviceFrame) {
    drawPhoneFrame(ctx, phoneX, phoneY, phoneW, phoneH, slide.image);
  } else if (slide.image) {
    roundRect(ctx, phoneX, phoneY, phoneW, phoneH, phoneW * 0.08);
    ctx.save();
    ctx.clip();
    drawCoverImage(ctx, slide.image, phoneX, phoneY, phoneW, phoneH);
    ctx.restore();
  }
}
