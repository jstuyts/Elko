package org.elkoserver.server.context.test;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.AdminObject;
import org.elkoserver.server.context.InternalActor;
import org.elkoserver.util.trace.TraceFactory;

class TestInternalObject extends AdminObject {
    /**
     * JSON-driven constructor.
     */
    @JSONMethod
    public TestInternalObject() {
        super();
    }

    @JSONMethod({ "arg" })
    public void boom(InternalActor from, String arg)
            throws MessageHandlerException
    {
        JSONLiteral response = new JSONLiteral(this, "bah");
        response.addParameter("arg", arg);
        response.finish();
        from.send(response);
    }

    @JSONMethod({ "arg" })
    public void superboom(InternalActor from, String arg)
            throws MessageHandlerException
    {
        from.ensureAuthorized();
        JSONLiteral response = new JSONLiteral(this, "superbah");
        response.addParameter("arg", arg);
        response.finish();
        from.send(response);
    }
}
