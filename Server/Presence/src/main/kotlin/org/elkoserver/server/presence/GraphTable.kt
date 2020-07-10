package org.elkoserver.server.presence

import org.elkoserver.foundation.json.JsonMethod

internal class GraphTable @JsonMethod("graphs") constructor(val graphs: Array<GraphDesc>)
