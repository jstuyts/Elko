package org.elkoserver.foundation.servermanagement;

import java.io.IOException;
import java.net.Socket;

import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ContextShutdown {

    public static final String NO_PASSWORD_INDICATOR = "-";

    public static void main(String[] arguments) throws IOException, InterruptedException {
        if (arguments.length != 3) {
            printUsage();
        } else {
            shutDownServer(arguments[0], parseInt(arguments[1]), arguments[2]);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: ContextShutdown <host address> <port number> <password, - = no password>");
    }

    private static void shutDownServer(String hostAddress, int portNumber, String password) throws IOException, InterruptedException {
        try (Socket socket = new Socket(hostAddress, portNumber)) {
            socket.getOutputStream().write(("{to:\"session\", op:\"shutdown\"" +
                    (password.equals(NO_PASSWORD_INDICATOR) ? "" : ", password:\"" + password + "\"") +
                    ", kill:false}\n\n").getBytes(UTF_8));
            Thread.sleep(1_000L);
        }
    }
}
