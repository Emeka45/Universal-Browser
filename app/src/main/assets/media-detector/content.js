(() => {
  const sent = new Set();
  const send = (url, title, kind) => {
    if (!url || sent.has(url)) return;
    const lower = String(url).toLowerCase();
    const media = /\.(mp4|webm|mov|m4v|3gp|mkv|mp3|m4a|ogg|oga|m3u8|mpd)(?:[?#]|$)/i.test(lower) || /\.m3u8|\.mpd|video\//i.test(lower);
    if (!media) return;
    sent.add(url);
    browser.runtime.sendMessage({type:"media-candidate", url, title: title || document.title || "Media", kind});
  };
  const inspect = () => {
    document.querySelectorAll("video,audio,source,track").forEach(el => {
      send(el.currentSrc || el.src || el.getAttribute("src"), document.title, el.tagName.toLowerCase());
    });
    document.querySelectorAll("a[href]").forEach(a => send(a.href, document.title, "link"));
  };
  inspect();
  new MutationObserver(inspect).observe(document.documentElement || document, {subtree:true, childList:true, attributes:true, attributeFilter:["src"]});
  setInterval(inspect, 4000);
})();
