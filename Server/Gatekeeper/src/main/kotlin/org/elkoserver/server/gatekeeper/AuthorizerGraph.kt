package org.elkoserver.server.gatekeeper

interface AuthorizerGraph {
    fun authorizer(): Authorizer
}