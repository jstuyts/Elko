if (typeof ELKO === 'undefined') {
    ELKO = {};
}

/**
 * Create and return a new session object.
 */
ELKO.session = function() {

    const trace = ELKO.trace;

    /** Mapping from ref strings to dispatchable objects. */
    let myObjectTable = {};

    /*
      Types are described by type definition objects.  A type definition
      object has the following mandatory properties:

      type: STRING   unique type tag labelling the type
      make: FUNCTION  new object initialization function
      prototype: OBJECT  prototype for objects of the type

      The make() function takes one parameter, an object descriptor, which
      typically is the object descriptor provided in a 'make' message from the
      server.  The type definition object may also carry any other properties
      its creator wishes, which will be available to the make() function.  The
      make() function should create and return a new instance of the given
      object type initialized according to the object descriptor provided.

      By convention, function properties with names of the form op_<FOO> will
      be automatically added to the object as handlers for messages with names
      of the form <FOO>, e.g., "op_delete" is the handler for the "delete"
      message.  These should all be functions of a single parameter, which will
      be the decoded message object received.
    */

    /** Mapping from type tags to type definition objects. */
    const myTypeTable = {};

    /** Our connection to the server. */
    let myConnection = null;

    /** The user object associated with the current session & context. */
    let myUser = null;

    /** The context associtated with the current session */
    let myContext = null;

    // noinspection JSUnusedGlobalSymbols
    /** The session itself, which will ultimately be returned. */
    const mySession = {
        addType: addType,
        getType: function (type) {
            return myTypeTable[type];
        },
        addObject: addObject,
        getObject: function (ref) {
            return myObjectTable[ref];
        },
        removeObject: removeObject,
        getUser: function () {
            return myUser;
        },
        getContext: function () {
            return myContext;
        },

        /**
         * Send a message to the server on this session's connection.
         *
         * @param msg  The message to send.
         */
        send: function (msg) {
            myConnection.send(msg);
        },

        /**
         * Establish this session's connection to the server.
         *
         * @param root  The root URL of the server.
         */
        connect: function (root) {
            myConnection =
                ELKO.connection(root, handleMessage, handleError);
        },

        /**
         * Make a server connection and attempt to enter a context.
         *
         * @param root  The root URL of the server
         * @param context  The ref of the context you want to enter
         * @param userinfo  User information object
         * @param template  Optional ref of a template to generate the context
         */
        connectToContext: function (root, context, userinfo, template) {
            this.connect(root);
            const msg = {
                to: 'session',
                op: 'entercontext',
                context: context
            };
            if (template) {
                msg.ctmpl = template
            }
            let okToGo = true;
            if (userinfo.auth) {
                msg.auth = userinfo.auth;
            }
            if (userinfo.name) {
                msg.name = userinfo.name;
            }
            if (userinfo.user) {
                msg.user = userinfo.user;
            } else if (userinfo.utag || userinfo.uparam) {
                if (userinfo.utag && userinfo.uparam) {
                    msg.utag = userinfo.utag;
                    msg.uparam = userinfo.uparam;
                } else {
                    okToGo = false;
                    if (userinfo.utag) {
                        trace.error("user info is missing 'uparam'");
                    } else {
                        trace.error("user info is missing 'utag'");
                    }
                }
            }
            if (okToGo) {
                myConnection.send(msg);
            }
        },

        /**
         * Make a server connection and attempt to enter a context, by making a
         * reservation through a director.
         *
         * @param root  The root URL of the director
         * @param context  The ref of the context you want to enter
         * @param userinfo  User information object
         * @param template  Optional ref of a template to generate the context
         */
        connectToContextViaDirector: function (root, context, userinfo,
                                               template) {
            const session = this;
            // noinspection JSUnusedGlobalSymbols
            addObject({
                ref: "director",
                op_reserve: function (msg) {
                    session.disconnect();
                    if (msg.deny) {
                        trace.error("reservation failure: " + msg.deny);
                    } else {
                        userinfo.auth = msg.reservation;
                        session.connectToContext(msg.hostport, context,
                            userinfo, template);
                    }
                }
            });

            this.connect(root);
            myConnection.send({
                to: 'director',
                op: 'auth'
            });
            myConnection.send({
                to: 'director',
                op: 'reserve',
                protocol: 'http',
                context: context
            });
        },

        /**
         * Terminate this session's connection to the server.
         */
        disconnect: function () {
            myConnection.disconnect();
            delete myConnection;
            myConnection = null;
            myObjectTable = {};
            addObject(sessionObject);
            addObject(errorObject);
        }
    };

    /**
     * Handle a message that was sent by the server, by dispatching it to the
     * appropriate object.
     *
     * @param msg  The message that was received from the server.
     */
    function handleMessage(msg) {
        try {
            const obj = myObjectTable[msg.to];
            const op = 'op_' + msg.op;
            if (obj) {
                if (obj[op]) {
                    obj[op](msg);
                } else {
                    trace.error("Server sent message to '" + msg.to +
                                "' with unsupported op '" + msg.op + "'");
                }
            } else {
                trace.error("Server sent message to unknown object '" +
                            msg.to + "'");
            }
        } catch (err) {
            trace.error("Error '" + err + "' handling message: " + msg);
        }
    }

    /**
     * Handle an error event on the server connection.
     *
     * @param msg  Human readable string describing the problem.
     * @param task  Tag string indicating what the connection was trying to do
     * @param error  Tag string indicating the particular error encountered
     */
    function handleError(msg, task, error) {
        trace.error("Server connection error: '" + msg + "', task=" + task +
                    ", error=" + JSON.stringify(error));
    }

    /**
     * Add a type to the type table.
     *
     * @param typeDef  A type descriptor object.
     */
    function addType(typeDef) {
        myTypeTable[typeDef.type] = typeDef;
    }

    /**
     * Add an object to the object table.  This table is used for object lookup
     * and message dispatch.
     *
     * @param obj  The object to add.  It is expected to have a property 'ref'
     *    that is the object's reference string and optionally message handler
     *    functions with names of the form 'op_<FOO>' as described above.
     */
    function addObject(obj) {
        myObjectTable[obj.ref] = obj;
    }
    
    /**
     * Remove an object from the object table.
     *
     * @param obj  The object to remove.  This function is most useful if the
     *    given object is actually in the table.
     */
    function removeObject(obj) {
        delete myObjectTable[obj.ref];
    }

    // noinspection JSUnusedGlobalSymbols
    /**
     * Prototype for the basic object types: context, user and item.  It
     * provides handlers for the universally understood messages 'make' and
     * 'delete'.
     */
    const theBasicObjectPrototype = {
        session: mySession,
        op_make: function (msg) {
            const desc = msg.obj;
            if (!desc.ref) {
                trace.error("Make with no 'ref' for new object: " + msg);
            } else if (myObjectTable[desc.ref]) {
                trace.error("Make for preexisting object '" + desc.ref + "'");
            } else {
                let newObj = null;
                if (desc.type) {
                    const typeDef = myTypeTable[desc.type];
                    if (typeDef) {
                        newObj = typeDef.make(desc);
                    } else {
                        trace.warning("Make specifies unknown object type '" +
                            desc.type + "' (ignored)");
                    }
                } else {
                    trace.warning("Make with no type for new object: " + msg);
                }
                if (newObj === null) {
                    newObj = makeobj(theBasicObjectPrototype);
                    for (let p in desc) {
                        if (desc.hasOwnProperty(p)) {
                            newObj[p] = desc[p];
                        }
                    }
                }
                newObj.ref = desc.ref;
                newObj.container = this;
                if (this.contents) {
                    this.contents.push(newObj);
                } else {
                    this.contents = [newObj];
                }
                if (msg.you) {
                    myUser = newObj;
                }
                if (desc.type === 'context') {
                    myContext = newObj;
                }
                addObject(newObj);
                for (let i in newObj.mods) {
                    const mod = newObj.mods[i];
                    if (mod.onMake) {
                        mod.onMake();
                    }
                }
                if (newObj.onMake) {
                    newObj.onMake();
                }
            }
        },
        op_delete: function () {
            let i;
            const container = this.container;
            const contents = container.contents;
            for (i = 0; i < contents.length; ++i) {
                if (contents[i] === this) {
                    contents.splice(i, 1);
                    break;
                }
            }
            for (i in this.mods) {
                const mod = this.mods[i];
                if (mod.onDelete) {
                    mod.onDelete();
                }
            }
            if (this.onDelete) {
                this.onDelete();
            }
            removeObject(this);
        }
    };

    function makeobj(proto) {
        function empty() { }
        empty.prototype = proto;
        return new empty();
    }

    /* Define the 'session' object, always present in the environment. */
    const sessionObject = makeobj(theBasicObjectPrototype);
    sessionObject.ref = "session";
    sessionObject.op_exit = function(msg) {
        if (msg.why) {
            trace.warning("Session closed by server: " + msg.why);
        } else {
            trace.warning(JSON.stringify(msg));
        }
        myConnection.disconnect();
    };
    addObject(sessionObject);

    /* Define the 'error' object, always present in the environment. */
    // noinspection JSUnusedGlobalSymbols
    const errorObject = {
        ref: "error",
        op_debug: function(msg) {
            trace.error("Server debug message: " + msg.msg);
        }
    };
    addObject(errorObject);

    /**
     * Common functionality for the creating the basic objects: context, user,
     * and item.  Sets the prototype, creates and attaches mods, and adds
     * wrapper message handlers to dispatch to mod message handlers.
     *
     * @param obj  Initial object to which the base properties will be added
     * @param desc  Object descriptor, as received from the server.
     * @param proto  Prototype for the new object to inherit from
     */
    function makeBasicObject(obj, desc, proto) {
        let p;
        const result = makeobj(proto);
        for (p in obj) {
            if (obj.hasOwnProperty(p)) {
                result[p] = obj[p];
            }
        }
        let mod, i;
        if (desc.mods) {
            result.mods = [];
            for (i = 0; i < desc.mods.length; ++i) {
                const modDesc = desc.mods[i];
                const modType = myTypeTable[modDesc.type];
                if (modType) {
                    if (modType.make) {
                        mod = modType.make(modDesc);
                    } else {
                        mod = makeobj(modType.prototype);
                        for (p in modDesc) {
                            if (p !== "type" && modDesc.hasOwnProperty(p)) {
                                mod[p] = modDesc[p];
                            }
                        }
                    }
                    result.mods[modDesc.type] = mod;
                    mod.object = result;
                    for (p in modType.prototype) {
                        const elem = modType.prototype[p];
                        if (elem instanceof Function &&
                            p.substr(0,3) === 'op_') {
                            result[p] = wrapHandler(mod, mod[p]);
                        }
                    }
                }
            }
        }
        return result;
    }

    function wrapHandler(mod, handler) {
        return function(msg) { return handler.call(mod, msg); }
    }

    /**
     * Create a context object.
     *
     * @param desc  Object descriptor, as received from the server.
     */
    function makeContext(desc) {
        const ctx = {
            ref: desc.ref,
            name: desc.name || desc.ref
        };
        return makeBasicObject(ctx, desc, this.prototype);
    }

    const ctxproto = makeobj(theBasicObjectPrototype);
    ctxproto.op_ready = function() { };
    ctxproto.op_exit = function() { };
    addType({type: 'context', make: makeContext, prototype: ctxproto});

    /**
     * Create a user object.
     *
     * @param desc  Object descriptor, as received from the server.
     */
    function makeUser(desc) {
        const user = {
            ref: desc.ref,
            name: desc.name || desc.ref,
            pos: desc.pos || {x: 0, y: 0}
        };
        return makeBasicObject(user, desc, this.prototype);
    }

    const userproto = makeobj(theBasicObjectPrototype);
    // noinspection JSUnusedGlobalSymbols
    userproto.op_ready = function (msg) { };
    addType({type: 'user', make: makeUser, prototype: userproto});

    /**
     * Create an item object.
     *
     * @param desc  Object descriptor, as received from the server.
     */
    function makeItem(desc) {
        const item = {
            ref: desc.ref,
            name: desc.name || desc.ref,
            pos: desc.pos || {x: 0, y: 0},
            cont: desc.cont,
            portable: desc.portable
        };
        return makeBasicObject(item, desc, this.prototype);
    }

    const itemproto = makeobj(theBasicObjectPrototype);
    addType({type: 'item', make: makeItem, prototype: itemproto});

    return mySession;
};
