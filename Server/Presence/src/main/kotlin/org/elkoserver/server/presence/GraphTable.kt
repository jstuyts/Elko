package org.elkoserver.server.presence

import org.elkoserver.foundation.json.JSONMethod

internal class GraphTable @JSONMethod("graphs") constructor(val graphs: Array<GraphDesc>)
