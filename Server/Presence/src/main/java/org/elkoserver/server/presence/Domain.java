package org.elkoserver.server.presence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class Domain {
    private static int theNextIndex = 0;
    private static ArrayList<Domain> theDomains = new ArrayList<>();

    private int myIndex;
    private String myName;
    private Map<String, PresenceActor> mySubscribers;

    Domain(String name) {
        myIndex = theNextIndex++;
        myName = name;
        mySubscribers = new HashMap<>();
        theDomains.add(myIndex, this);
    }

    static Domain domain(int index) {
        return theDomains.get(index);
    }

    int index() {
        return myIndex;
    }

    static int maxIndex() {
        return theNextIndex;
    }

    String name() {
        return myName;
    }

    PresenceActor subscriber(String context) {
        return mySubscribers.get(context);
    }

    void addSubscriber(String context, PresenceActor client) {
        mySubscribers.put(context, client);
    }

    void removeClient(PresenceActor client) {
        mySubscribers.values().removeIf(subscriber -> subscriber == client);
    }

    void removeSubscriber(String context) {
        mySubscribers.remove(context);
    }
}
