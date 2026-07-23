/**
 * Streamlabs Chat Box → ChatBet bridge forwarder
 * ----------------------------------------------
 * Paste into Streamlabs Dashboard → All Widgets → Chat Box →
 * Enable Custom HTML/CSS → JS tab (or append to existing custom JS).
 *
 * Point BRIDGE at the machine running stream_bet_bridge.py (default 8765).
 * If OBS runs on the same PC as the bridge, 127.0.0.1 is fine.
 */

(function () {
  var BRIDGE = "http://127.0.0.1:8765/ingest";

  function post(payload) {
    try {
      fetch(BRIDGE, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
        mode: "cors",
        keepalive: true,
      }).catch(function () {});
    } catch (e) {}
  }

  // Streamlabs fires custom events; common pattern is listening on the document
  // or overriding the message render path. This hook covers the usual widget API.
  document.addEventListener("onEventReceived", function (obj) {
    try {
      var data = obj && obj.detail ? obj.detail : obj;
      if (!data) return;

      // Chat message
      if (data.listener === "message" || data.type === "message" || data.message) {
        var msg = data.event || data.message || data;
        var user =
          (msg.nick || msg.name || msg.displayName || msg.from || msg.user || "").toString();
        var text = (msg.text || msg.message || msg.msg || "").toString();
        var platform = (msg.platform || data.platform || "twitch").toString();
        if (!user && !text) return;
        post({
          platform: platform,
          user: user || "unknown",
          message: text,
          timestamp: Date.now(),
          event: "message",
        });
      }

      // Some widget builds emit join notices as system messages — treat as message;
      // presence is primarily inferred server-side from first chat + idle timeout.
    } catch (e) {}
  });

  // Fallback: MutationObserver on chat DOM (widget-dependent selectors)
  try {
    var root = document.querySelector("#log") || document.body;
    var seen = new Set();
    var obs = new MutationObserver(function (mutations) {
      mutations.forEach(function (m) {
        m.addedNodes.forEach(function (node) {
          if (!node || node.nodeType !== 1) return;
          var el = node;
          var from = el.getAttribute && el.getAttribute("data-from");
          var textEl = el.querySelector && (el.querySelector(".message") || el.querySelector(".chat-message"));
          var nameEl = el.querySelector && (el.querySelector(".name") || el.querySelector(".chat-author"));
          var user = from || (nameEl ? nameEl.textContent : "");
          var text = textEl ? textEl.textContent : el.textContent || "";
          user = (user || "").trim();
          text = (text || "").trim();
          if (!user || !text) return;
          var key = user + "|" + text.slice(0, 80);
          if (seen.has(key)) return;
          seen.add(key);
          if (seen.size > 500) seen.clear();
          post({
            platform: "twitch",
            user: user,
            message: text,
            timestamp: Date.now(),
            event: "message",
          });
        });
      });
    });
    obs.observe(root, { childList: true, subtree: true });
  } catch (e) {}
})();
