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
    const download = `${url.origin}/apk`;

    return new Response(page(version, download), {
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

function page(version, download) {
  return `<!doctype html>
<html lang="ckb" dir="rtl">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
  <meta name="theme-color" content="#071018">
  <title>REBARIT CLONE — Download</title>
  <style>
    *{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;padding:20px;background:radial-gradient(circle at 50% -10%,#17334a,#071018 48%,#050b10);color:#f7fafc;font-family:system-ui,-apple-system,"Noto Sans Arabic",Arial,sans-serif}.container{width:min(100%,440px)}h1{text-align:center;margin:0 0 24px;font-size:27px;letter-spacing:.3px}.card{background:linear-gradient(180deg,rgba(16,30,41,.97),rgba(11,21,29,.97));border:1px solid #223443;border-radius:24px;padding:24px;box-shadow:0 22px 70px #0008}.version{text-align:center;color:#f4d76e;font-size:28px;font-weight:900;margin:0 0 22px}.download{display:flex;justify-content:center;align-items:center;width:100%;padding:17px;border-radius:16px;background:#f4d76e;color:#111820;font-size:17px;font-weight:900;text-decoration:none}.download:active{transform:scale(.985)}@media(max-width:420px){body{padding:16px}.card{padding:20px}}
  </style>
</head>
<body>
  <main class="container">
    <h1>REBARIT CLONE</h1>
    <section class="card">
      <div class="version">وەشانی ${version}</div>
      <a class="download" href="${download}">دابەزاندنی APK</a>
    </section>
  </main>
</body>
</html>`;
}
