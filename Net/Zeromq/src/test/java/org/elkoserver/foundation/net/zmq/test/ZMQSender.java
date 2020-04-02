package org.elkoserver.foundation.net.zmq.test;

import org.elkoserver.foundation.net.NetAddr;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import static java.nio.charset.Charset.defaultCharset;
import static java.nio.charset.StandardCharsets.UTF_16;

class ZMQSender {
    public static void main(String[] args) {
        BufferedReader in =
            new BufferedReader(new InputStreamReader(System.in, defaultCharset()));

        String host = args[0];
        boolean push = true;
        if (host.startsWith("PUSH:")) {
            host = "tcp://" + host.substring(5);
        } else if (host.startsWith("PUB:")) {
            push = false;
            try {
                NetAddr parsedAddr = new NetAddr(host.substring(4));
                host = "tcp://*:" + parsedAddr.getPort();
            } catch (IOException e) {
                System.out.println("problem setting up ZMQ connection with " +
                                   host + ": " + e);
                return;
            }
        } else {
            host = "tcp://" + host;
        }
        

        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket socket;
        if (push) {
            socket = context.socket(SocketType.PUSH);
            System.out.println("PUSH to server at " + host);
            socket.connect(host);
        } else {
            socket = context.socket(SocketType.PUB);
            System.out.println("PUB at " + host);
            socket.bind(host);
        }

        StringBuilder msg = null;
        while (true) {
            try {
                String line = in.readLine();
                
                if (line == null) {
                    break;
                } else if (line.equals("")) {
                    if (msg != null) {
                        msg.append(" ");
                        byte[] msgBytes = msg.toString().getBytes(UTF_16);
                        msgBytes[msgBytes.length - 1] = 0;
                        socket.send(msgBytes, 0);
                        msg = null;
                    }
                } else if (msg == null) {
                    msg = new StringBuilder(line);
                } else {
                    msg.append("\n").append(line);
                }
            } catch (IOException e) {
                break;
            }
        }
    }
}
