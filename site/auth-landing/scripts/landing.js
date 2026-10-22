(function () {
  var header = document.getElementById("site-header");
  if (header) {
    window.addEventListener(
      "scroll",
      function () {
        header.classList.toggle("is-scrolled", window.scrollY > 8);
      },
      { passive: true },
    );
  }

  var gallery = document.querySelector("[data-gallery]");
  if (!gallery) return;

  var track = gallery.querySelector(".gallery-track");
  var slides = gallery.querySelectorAll(".gallery-slide");
  var prev = gallery.querySelector(".gallery-prev");
  var next = gallery.querySelector(".gallery-next");
  var dotsHost = gallery.querySelector(".gallery-dots");
  var captionEl = gallery.querySelector(".gallery-caption");
  var index = 0;
  var count = slides.length;

  slides.forEach(function (_, i) {
    var dot = document.createElement("button");
    dot.type = "button";
    dot.className = "gallery-dot";
    dot.setAttribute("role", "tab");
    dot.setAttribute("aria-label", "Screenshot " + (i + 1));
    dot.addEventListener("click", function () {
      go(i);
    });
    dotsHost.appendChild(dot);
  });

  var dots = dotsHost.querySelectorAll(".gallery-dot");

  function captionFor(i) {
    return slides[i].getAttribute("data-caption") || "";
  }

  function go(i) {
    index = (i + count) % count;
    track.style.transform = "translateX(-" + index * 100 + "%)";
    dots.forEach(function (dot, j) {
      dot.setAttribute("aria-selected", j === index ? "true" : "false");
    });
    if (captionEl) captionEl.textContent = captionFor(index);
  }

  if (prev) prev.addEventListener("click", function () {
    go(index - 1);
  });
  if (next) next.addEventListener("click", function () {
    go(index + 1);
  });

  gallery.addEventListener("keydown", function (e) {
    if (e.key === "ArrowLeft") {
      e.preventDefault();
      go(index - 1);
    } else if (e.key === "ArrowRight") {
      e.preventDefault();
      go(index + 1);
    }
  });

  var touchStartX = null;
  gallery.addEventListener(
    "touchstart",
    function (e) {
      touchStartX = e.changedTouches[0].clientX;
    },
    { passive: true },
  );
  gallery.addEventListener(
    "touchend",
    function (e) {
      if (touchStartX == null) return;
      var dx = e.changedTouches[0].clientX - touchStartX;
      if (Math.abs(dx) > 40) go(dx < 0 ? index + 1 : index - 1);
      touchStartX = null;
    },
    { passive: true },
  );

  go(0);
})();
