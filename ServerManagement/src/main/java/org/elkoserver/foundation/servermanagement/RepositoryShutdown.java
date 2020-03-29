package org.elkoserver.foundation.servermanagement;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.UTF_8;

public class RepositoryShutdown {

    public static void main(String[] arguments) throws IOException, InterruptedException {
        if (arguments.length != 3) {
            printUsage();
        } else {
            shutDownServer(arguments[0], parseInt(arguments[1]), arguments[2]);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: RepositoryShutdown <host address> <port number> <password>");
    }

    private static void shutDownServer(String hostAddress, int portNumber, String password) throws IOException, InterruptedException {
        try (Socket socket = new Socket(hostAddress, portNumber)) {
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(("{to:\"admin\", op:\"auth\", auth:{mode:\"password\", code:\"" + password + "\"}}\n\n").getBytes(UTF_8));
            Thread.sleep(1_000L);
            outputStream.write(("{to:\"admin\", op:\"shutdown\", kill:false}\n\n").getBytes(UTF_8));
            Thread.sleep(1_000L);
        }
    }
}
