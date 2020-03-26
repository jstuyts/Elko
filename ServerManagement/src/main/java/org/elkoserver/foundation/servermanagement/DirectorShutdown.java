package org.elkoserver.foundation.servermanagement;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static java.lang.Integer.parseInt;

public class DirectorShutdown {

    public static void main(String[] arguments) throws IOException, InterruptedException {
        if (arguments.length != 2) {
            printUsage();
        } else {
            shutDownServer(arguments[0], parseInt(arguments[1]));
        }
    }

    private static void printUsage() {
        System.out.println("Usage: DirectorShutdown <host address> <port number>");
    }

    private static void shutDownServer(String hostAddress, int portNumber) throws IOException, InterruptedException {
        try (Socket socket = new Socket(hostAddress, portNumber)) {
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(("{to:\"admin\", op:\"auth\", auth:{mode:\"open\"}}\n\n").getBytes(StandardCharsets.UTF_8));
            Thread.sleep(1_000L);
            outputStream.write(("{to:\"admin\", op:\"shutdown\", director:true, kill:false}\n\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
