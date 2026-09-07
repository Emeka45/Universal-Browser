const seen = new Set();
const mediaPattern = /\.(m3u8|mpd|mp4|webm|mov|m4v|3gp|mkv|mp3|m4a|ogg|oga)(?:[?#]|$)/i;

async function sendMedia(details, title, kind) {
  const url = details.url || "";
  if (!url || !mediaPattern.test(url) || seen.has(url)) return;
  seen.add(url);
  try {
    const cookies = await browser.cookies.getAll({url});
    const cookieHeader = cookies.map(c => `${c.name}=${c.value}`).join("; ");
    await browser.runtime.sendNativeMessage("browser", {
      type:"media-playable", url, title:title || "Network media", kind:kind || "network", cookies:cookieHeader
    });
  } catch (_) {}
}

browser.webRequest.onBeforeRequest.addListener(details => {
  sendMedia(details, "Network media", "network");
}, {urls:["<all_urls>"]});

browser.runtime.onMessage.addListener(message => {
  if (!message || message.type !== "media-candidate" || !message.url) return;
  sendMedia({url: message.url}, message.title || "Media", message.kind || "dom");
});
