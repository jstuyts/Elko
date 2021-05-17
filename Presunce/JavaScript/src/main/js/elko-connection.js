// https://github.com/umdjs/umd/blob/master/templates/commonjsStrict.js
(function (root, factory) {
    if (typeof define === 'function' && define.amd) {
        // AMD. Register as an anonymous module.
        define(['exports'], factory);
    } else if (typeof exports === 'object' && typeof exports.nodeName !== 'string') {
        // CommonJS
        factory(exports);
    } else {
        // Browser globals
        factory((root.elkoConnection = {}));
    }
}(typeof self !== 'undefined' ? self : this, function (exports) {
    // The connection constructor produces a connection object.
    // It takes 3 parameters:
    //      root        The root session url.
    //      receiver    The function to be called with incoming messages.
    //                  It is passed the message object.
    //      error       The function to be called when an error occurs. It is
    //                  passed an object that comes either from the server or
    //                  from yuc.asyncRequest.
    // The returned connection object has two methods:
    //      send
    //      disconnect
    let connection = function (root, receiver, error) {
        let hold = true,        // Should outgoing messages be held in the queue?
            queue = [],         // The outgoing message queue.
            sessionid,          // The session id.
            sseqnum = 1,        // The select sequence number.
            xseqnum = 1;        // The xmit sequence number.
        let disconnected = false;

        if (root.indexOf("http://") !== 0) {
            root = "http://" + root;
        }

// The ask function is used to give the server the opportunity to push
// messages.  Each message will cause the invocation of the receiver function.

        function ask() {
            if (!disconnected) {
                fetch(root + '/select/' + sessionid + '/' + sseqnum, {
                    method: "GET",
                }).then(function (response) {
                    sseqnum = null;
                    response.text().then(
                        function (responseBody) {
                            let data = JSON.parse(responseBody), i;
                            if (data) {
                                sseqnum = data.seqnum;
                                if (data.msgs && data.msgs.length) {
                                    for (i = 0; i < data.msgs.length; i += 1) {
                                        receiver(data.msgs[i]);
                                    }
                                }
                                data = null;
                            }
                            if (sseqnum) {
                                if (sseqnum > 0) {
                                    setTimeout(ask, 0);
                                }
                            } else {
                                error(response);
                            }
                        });
                }, error);
            }
        }

// The post function delivers the message queue to the server.

        function post(url, success) {
            hold = true;
            let postBody = queue.join('\n');
            queue = [];
            fetch(url, {method: "POST", headers: {"Content-Type": "text/plain"}, body: postBody}).then(success, error);
        }

// The send function transmits the queue to the server. Its success function
// will call send recursively if the queue filled up again during the
// transmission.

        function send() {
            post(root + '/xmit/' + sessionid + '/' + xseqnum, function (respsonse) {
                xseqnum = null;
                respsonse.text().then(function (responseText) {
                    const data = JSON.parse(responseText);
                    xseqnum = data && data.seqnum;
                    if (xseqnum) {
                        hold = false;
                        if (queue.length) {
                            send();
                        }
                    } else {
                        error(respsonse);
                    }
                });
            });
        }

// Start by sending a connect request.

        function connect() {
            fetch(root + '/connect/' + new Date().getTime(), {method: "GET"}).then(
                function (response) {
                    response.text().then(function (responseText) {
                        const data = JSON.parse(responseText);
                        sessionid = data && data.sessionid;
                        sseqnum = 1;
                        xseqnum = 1;
                        if (sessionid) {
                            ask();
                            hold = false;
                            if (queue.length) {
                                send();
                            }
                        } else {
                            error(response);
                        }
                    })
                }, error);
        }

        connect();

// Return the connection object containing the send and disconnect methods.

        return {

// Send the message object to the server. It will be held in the queue until
// the xmit channel is ready.

            send: function (message) {
                if (!sessionid) {
                    //connect();
                }
                if (message) {
                    if (typeof message !== 'string') {
                        message = JSON.stringify(message);
                    }
                    queue.push(message);
                    if (!hold) {
                        setTimeout(send, 0);
                    }
                }
            },

// Disconnect from the server. Send any queued messages. The disconnection
// request is ignored if we are not currently connected.

            disconnect: function () {
                if (sessionid) {
                    post(root + '/disconnect/' + sessionid, function () {
                    });
                    sessionid = null;
                }
                disconnected = true;
            }
        };
    };

    exports.connection = connection;
}));
