package com.fdeight.socketchat;

import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Консольный многопользовательский чат.
 * Клиент
 */
public class Client {

    private static final String IP = "127.0.0.1";//"localhost";
    private static final int PORT = Server.PORT;
    private static final SimpleDateFormat DATE_FORMAT = Server.DATE_FORMAT;

    private final String ip; // ip адрес клиента
    private final int port; // порт соединения

    private Socket socket = null;
    private BufferedReader in = null; // поток чтения из сокета
    private BufferedWriter out = null; // поток записи в сокет
    private BufferedReader inputUser = null; // поток чтения с консоли
    private String nickname = null; // имя клиента

    /**
     * для создания необходимо принять адрес и номер порта
     *
     * @param ip   ip адрес клиента
     * @param port порт соединения
     */
    private Client(final String ip, final int port) {
        this.ip = ip;
        this.port = port;
    }

    private void startClient() {
        try {
            socket = new Socket(this.ip, this.port);
        } catch (final IOException e) {
            System.err.println("Socket failed");
            return;
        }

        try {
            inputUser = new BufferedReader(new InputStreamReader(System.in));
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        } catch (final IOException e) {
            downService();
            return;
        }

        System.out.println(String.format("Client started, ip: %s, port: %d", ip, port));
        pressNickname(); // перед началом необходимо спросить имя
        new ReadMsg().start(); // нить читающая сообщения из сокета в бесконечном цикле
        new WriteMsg().start(); // нить пишущая сообщения в сокет приходящие с консоли в бесконечном цикле
    }

    /**
     * отсылка одного сообщения клиенту
     *
     * @param message сообщение
     */
    private void send(final String message) throws IOException {
        out.write(message + "\n");
        out.flush();
    }

    private String formatMessage(final String message) {
        final Date date = new Date();
        final String strTime = DATE_FORMAT.format(date);
        return String.format("[%s] %s: %s", strTime, nickname, message);
    }

    private String formatCommandMessage(final String message) {
        return formatMessage(message) + " [command]";
    }

    /**
     * просьба ввести имя,
     * и отсылка эхо с приветствием на сервер
     */
    private void pressNickname() {
        System.out.print("Press your nick: ");
        try {
            nickname = inputUser.readLine();
            send(nickname);
        } catch (final IOException ignored) {
        }
    }

    /**
     * закрытие сокета
     */
    private void downService() {
        try {
            if (!socket.isClosed()) {
                socket.close();
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            }
        } catch (final IOException ignored) {
        }
    }

    // нить чтения сообщений с сервера
    private class ReadMsg extends Thread {
        @Override
        public void run() {
            String message;
            try {
                while (true) {
                    message = in.readLine(); // ждем сообщения с сервера
                    if (Server.Command.STOP_CLIENT.equalCommand(message)) {
                        downService();
                        break; // нить чтения данных из консоли по этой команде прекращает работу сама
                    } else if (Server.Command.STOP_CLIENT_FROM_SERVER.equalCommand(message)
                            || Server.Command.STOP_ALL_CLIENTS.equalCommand(message)
                            || Server.Command.STOP_SERVER.equalCommand(message)) {
                        downService();
                        System.exit(0);
                    }
                    System.out.println(message); // пишем сообщение с сервера на консоль
                }
            } catch (final IOException e) {
                downService();
            }
        }
    }

    // нить отправляющая сообщения приходящие с консоли на сервер
    public class WriteMsg extends Thread {

        @Override
        public void run() {
            while (true) {
                final String message;
                try {
                    message = inputUser.readLine();
                    if (Server.Command.isCommandMessage(message)) {
                        send(formatCommandMessage(message));
                        send(message);
                        if (Server.Command.STOP_CLIENT.equalCommand(message)) {
                            downService();
                            break;
                        }
                    } else {
                        send(formatMessage(message));
                    }
                } catch (final IOException e) {
                    downService();
                }
            }
        }
    }

    public static void main(final String[] args) {
        final Client client = new Client(IP, PORT);
        client.startClient();
    }
}
