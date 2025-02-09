new WebSocket("ws://localhost:%s/").onmessage = function(o) {
    "reload" === o.data && window.location.reload()
};