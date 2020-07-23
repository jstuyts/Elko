# Elko Chat Basic Example

## Introduction

This is a minimal example that should give you an idea of what messages are sent when between the server and the connected clients.

It provides a single chat room where you can chat as one of three users.

## Required Tools

Required tools for running:

* JDK 11: [Oracle](https://www.oracle.com/java/technologies/javase-downloads.html), [Adopt OpenJDK](https://adoptopenjdk.net/)
* [MongoDB](https://www.mongodb.com/) or [Vagrant](https://www.vagrantup.com/)

## Code 

The code for this example can be found in class `SimpleChat` and folder `web`.

### Server-Side

Note that most code of [`SimpleChat`](../../Example/src/main/kotlin/com/example/game/mods/SimpleChat.kt) is not used in this example: pushing a URL and private chat are not supported in the web client. So the only interesting functions are `say(User?, String?)` and `msgSay(Referenceable?, Referenceable?, String?)` in [`SayMessage`](../../Example/src/main/kotlin/com/example/game/mods/SayMessage.kt).

### Client-Side

The client-side code is in [`web/index.html`](web/index.html). Look at how the session (`getObject("session")`), context (`ctxType`) and user (`userType`) types are extended for this specific application.

Generic Elko code can be found in the separate JavaScript files in project `:Presence:JavaScript`.

## Security

See the [security document](SECURITY.md).

## Running the Example

### MongoDB

A MongoDB instance is required. You can run one yourself or use the provided [Vagrant](https://www.vagrantup.com/) machine in `<project root>/Run/services/MongoDB`.

The Vagrant machine uses box [`generic/debian10`](https://app.vagrantup.com/generic/boxes/debian10), which is available for multiple Vagrant hypervisor providers. Port forwarding is configured, so port 127.0.0.1:27017 can be used to access MongoDB, except when using Hyper-V. See below for Hyper-V instructions.

You have to set the MongoDB IP address and port in `gradle.properties`. For example:

````properties
mongodbHostAndPort=127.0.0.1:27017
````

Note for Hyper-V users: You have to copy the IP address of the machine printed by Vagrant to `gradle.properties` each time the machine is started, as Vagrant does not support port forwarding on Hyper-V and the IP address of the machine can be different each time.

### Initialize the Database

A number of objects need to be present in the database for the example to work. You can initialize the database using the following command:

````shell script
./gradlew initializeChatBasicDatabase 
````

### Data

The data in the database is not changed after initialization. No data is added or updated by this example.

## Start the Servers

The example needs 2 servers: the Elko context and a web server for the web resources. Both can be started by running:

````shell script
./gradlew startChatBasicAll 
````

## Open the Web Page

Open the web page in multiple browsers. It should work on all modern (mobile) browsers (if not, please [create an issue](https://github.com/jstuyts/Elko/issues/new)): `http://<hostname>:8080/`. If the hostname does not resolve to an IP address, use an IP address instead.

The following URL will work on the computer running the servers: http://127.0.0.1:8080/

### Logging in

The login page shows 2 fields:

- Server: The URL of the context. The hostname or IP address is based on the URL the page is loaded from, so the URL should work without changes.
- User: The identifier of 1 of the 3 users: `u-Bob`, `u-Alice` or `u-Charlie`. Note that a user can be active in at most 1 browser. If a user logs in to another browser, they are logged out in the current browser.

### Chatting

Simply type what you want to say in the text field and click _speak_ or press enter. Your messages and those of others will appear below. Note that only messages that are sent while you are logged in are shown; there is no chat history.

The messages can be deleted by clicking _clear_. And you can return to the login screen by clicking _log out_.

## Logs

The logs of the context server can be found in folder `logs`.

Use the development tools (JavaScript console and network traffic) of your browser to see what the web client is doing. 

## Stop the Servers

Both servers can be stopped by running:

````shell script
./gradlew stopChatBasicAll 
````
