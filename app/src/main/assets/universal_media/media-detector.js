(() => {
  const sent = new Map();
  const mediaExt = /\.(mp4|webm|mov|m4v|3gp|mkv|mp3|m4a|ogg|oga|m3u8|mpd)(?:$|[?#])/i;

  function cleanUrl(value) {
    if (!value || value.startsWith('blob:') || value.startsWith('data:')) return null;
    try { return new URL(value, location.href).href; } catch (_) { return null; }
  }

  function candidateFromVideo(video) {
    const urls = [];
    const direct = cleanUrl(video.currentSrc || video.src);
    if (direct) urls.push(direct);
    video.querySelectorAll('source[src]').forEach(source => {
      const u = cleanUrl(source.src);
      if (u) urls.push(u);
    });
    return urls.map(url => ({
      url,
      kind: /\.(m3u8|mpd)(?:$|[?#])/i.test(url) ? 'stream' : 'video',
      width: video.videoWidth || video.clientWidth || 0,
      height: video.videoHeight || video.clientHeight || 0,
      duration: Number.isFinite(video.duration) ? video.duration : 0,
      title: document.title || location.hostname
    }));
  }

  function performanceCandidates() {
    try {
      return performance.getEntriesByType('resource')
        .map(entry => cleanUrl(entry.name))
        .filter(url => url && mediaExt.test(url))
        .map(url => ({ url, kind: /\.(m3u8|mpd)(?:$|[?#])/i.test(url) ? 'stream' : 'video', width: 0, height: 0, duration: 0, title: document.title || location.hostname }));
    } catch (_) { return []; }
  }

  function report(force) {
    const candidates = [];
    document.querySelectorAll('video, audio').forEach(media => candidates.push(...candidateFromVideo(media)));
    candidates.push(...performanceCandidates());
    const unique = [];
    for (const item of candidates) {
      if (!item.url || unique.some(x => x.url === item.url)) continue;
      unique.push(item);
    }
    const fresh = unique.filter(item => force || !sent.has(item.url));
    fresh.forEach(item => sent.set(item.url, Date.now()));
    if (fresh.length) {
      browser.runtime.sendNativeMessage('browser', {
        type: 'media_detected',
        pageUrl: location.href,
        pageTitle: document.title || location.hostname,
        videos: fresh.slice(0, 20)
      });
    }
  }

  document.addEventListener('play', event => {
    if (event.target instanceof HTMLMediaElement) report(true);
  }, true);
  new MutationObserver(() => report(false)).observe(document.documentElement || document, { childList: true, subtree: true, attributes: true, attributeFilter: ['src'] });
  setTimeout(() => report(false), 1200);
  setInterval(() => report(false), 5000);
})();
