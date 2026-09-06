const seen = new Set();
const mediaPattern = /\.(m3u8|mpd|mp4|webm|mov|m4v|3gp|mkv|mp3|m4a|ogg|oga)(?:[?#]|$)/i;
browser.webRequest.onBeforeRequest.addListener(details => {
  const url = details.url || "";
  if (!mediaPattern.test(url) || seen.has(url)) return;
  seen.add(url);
  browser.runtime.sendNativeMessage("browser", {type:"media-playable", url, title:"Network media", kind:"network"}).catch(() => {});
}, {urls:["<all_urls>"]});
