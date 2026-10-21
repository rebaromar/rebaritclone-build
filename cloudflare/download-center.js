/* REBARIT CLONE download center for apk.rebarit.store. */

const REPOSITORY = "rebaromar/rebaritclone-build";
const RELEASES_API = `https://api.github.com/repos/${REPOSITORY}/releases/latest`;

export default {
  async fetch(request) {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return Response.json({ ok: true, service: "REBARIT CLONE downloads" });
    }

    const release = await getLatestRelease();
    if (release instanceof Response) return release;

    if (url.pathname === "/update.json") {
      return Response.json(release.manifest, {
        headers: {
          "Cache-Control": "no-store",
          "Access-Control-Allow-Origin": "*",
        },
      });
    }

    if (url.pathname === "/apk" || url.pathname === "/download") {
      return Response.redirect(release.apkUrl, 302);
    }

    if (url.pathname !== "/") {
      return new Response("Not Found", { status: 404 });
    }

    const version = escapeHtml(release.manifest.versionName);
    const notes = escapeHtml(release.manifest.notes || "دوایین وەشانی REBARIT CLONE ئامادەیە.");
    const download = `${url.origin}/apk`;
    const qr = `https://quickchart.io/qr?size=320&margin=2&text=${encodeURIComponent(download)}`;

    return new Response(page(version, notes, download, qr), {
      headers: {
        "Content-Type": "text/html; charset=UTF-8",
        "Cache-Control": "no-store, no-cache, must-revalidate",
        "X-Content-Type-Options": "nosniff",
        "X-Frame-Options": "DENY",
      },
    });
  },
};

async function getLatestRelease() {
  try {
    const response = await fetch(RELEASES_API, {
      headers: {
        Accept: "application/vnd.github+json",
        "User-Agent": "REBARIT-CLONE-Download-Center",
        "X-GitHub-Api-Version": "2022-11-28",
      },
      cf: { cacheTtl: 60, cacheEverything: true },
    });
    if (!response.ok) throw new Error(`GitHub releases API returned ${response.status}`);

    const release = await response.json();
    const apk = release.assets?.find((asset) => asset.name.endsWith(".apk"));
    if (!apk?.browser_download_url) throw new Error("Latest release has no APK asset");

    const versionName = String(release.tag_name || "").replace(/^v/, "");
    const codeLine = String(release.body || "").match(/^Version-Code:\s*(\d+)\s*$/m);
    const versionCode = codeLine ? Number(codeLine[1]) : dottedVersionCode(versionName);

    return {
      apkUrl: apk.browser_download_url,
      manifest: {
        versionCode,
        versionName,
        notes: String(release.body || "").replace(/^Version-Code:.*$/m, "").trim(),
        apkUrl: apk.browser_download_url,
      },
    };
  } catch (error) {
    return new Response("Latest APK is temporarily unavailable. Please try again later.", {
      status: 503,
      headers: { "Cache-Control": "no-store", "Retry-After": "60" },
    });
  }
}

function dottedVersionCode(version) {
  const match = version.match(/^(\d+)\.(\d+)$/);
  return match ? Number(match[1]) * 10 + Number(match[2]) : 1;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function page(version, notes, download, qr) {
  return `<!doctype html>
<html lang="ckb" dir="rtl">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
  <meta name="theme-color" content="#071018">
  <title>REBARIT CLONE — Download</title>
  <style>
    *{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;padding:20px;background:radial-gradient(circle at 50% -10%,#17334a,#071018 48%,#050b10);color:#f7fafc;font-family:system-ui,-apple-system,"Noto Sans Arabic",Arial,sans-serif}.container{width:min(100%,520px)}.logo{width:76px;height:76px;margin:0 auto 14px;border-radius:23px;display:grid;place-items:center;background:linear-gradient(145deg,#f7df83,#b99636);color:#101820;font-size:34px;font-weight:950}h1{text-align:center;margin:0;font-size:28px;letter-spacing:.4px}.subtitle{text-align:center;color:#9db0be;margin:7px 0 20px;font-size:14px}.card{background:linear-gradient(180deg,rgba(16,30,41,.97),rgba(11,21,29,.97));border:1px solid #223443;border-radius:24px;padding:22px;box-shadow:0 22px 70px #0008}.live{display:flex;justify-content:center;align-items:center;gap:8px;color:#c8d4dc;font-size:13px}.dot{width:8px;height:8px;border-radius:50%;background:#35d07f;box-shadow:0 0 14px #35d07f}.version{text-align:center;color:#f4d76e;font-size:36px;font-weight:900;margin:8px 0}.notes{text-align:center;color:#9db0be;font-size:14px;line-height:1.8;margin:0 0 18px;white-space:pre-line}.download{display:flex;justify-content:center;align-items:center;width:100%;padding:16px;border-radius:16px;background:#f4d76e;color:#111820;font-size:17px;font-weight:900;text-decoration:none}.line{height:1px;background:#223443;margin:22px 0}.qr{text-align:center}.qr img{display:block;width:185px;height:185px;margin:0 auto 12px;padding:9px;border-radius:17px;background:white}.qr-title{font-weight:800}.qr-info{color:#9db0be;font-size:12px;line-height:1.7;margin-top:5px}.security{text-align:center;color:#7f929f;font-size:12px;margin-top:18px}footer{text-align:center;color:#617481;font-size:11px;margin-top:16px}@media(max-width:420px){body{padding:16px}.card{padding:18px}.version{font-size:31px}.qr img{width:170px;height:170px}}
  </style>
</head>
<body>
  <main class="container">
    <div class="logo">R</div>
    <h1>REBARIT CLONE</h1>
    <div class="subtitle">Fast &amp; Secure Android Data Transfer</div>
    <section class="card">
      <div class="live"><span class="dot"></span>وەشانی بەردەست</div>
      <div class="version">${version}</div>
      <div class="notes">${notes}</div>
      <a class="download" href="${download}">⬇️ دابەزاندنی APK بۆ ئەندرۆید</a>
      <div class="line"></div>
      <div class="qr"><img src="${qr}" alt="REBARIT CLONE QR Code"><div class="qr-title">QR Code ـەکە Scan بکە</div><div class="qr-info">بۆ دابەزاندنی ڕاستەوخۆی دوایین وەشانی REBARIT CLONE</div></div>
      <div class="security">✓ Direct latest build&nbsp; • &nbsp;✓ Auto Update</div>
    </section>
    <footer>© REBARIT CLONE</footer>
  </main>
</body>
</html>`;
}
