import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer {
    private static final ByteBuffer receiveBuffer = ByteBuffer.allocate(16384);
    private static final ByteBuffer sendBuffer = ByteBuffer.allocate(16384);
    private static final Charset charset = StandardCharsets.UTF_8;
    private static final CharsetDecoder charsetDecoder = charset.newDecoder();
    private static final ArrayList<Client> connectedClients = new ArrayList<>();
    private static final ArrayList<Room> rooms = new ArrayList<>();

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);

        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);

            ServerSocket serverSocket = serverSocketChannel.socket();
            InetSocketAddress socketAddress = new InetSocketAddress(port);
            serverSocket.bind(socketAddress);

            Selector selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Listening on port " + port);

            while (true) {
                int selectedActivity = selector.select();

                if (selectedActivity == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                for (SelectionKey selectedKey : selectedKeys) {
                    if (selectedKey.isAcceptable()) {
                        Socket acceptedSocket = serverSocket.accept();
                        System.out.println("Got connection from " + acceptedSocket);

                        SocketChannel acceptedSocketChannel = acceptedSocket.getChannel();
                        acceptedSocketChannel.configureBlocking(false);

                        acceptedSocketChannel.register(selector, SelectionKey.OP_READ);
                        connectedClients.add(new Client(acceptedSocketChannel));
                    } else if (selectedKey.isReadable()) {
                        SocketChannel socketChannel = null;

                        try {
                            socketChannel = (SocketChannel) selectedKey.channel();
                            int clientIdx = getClientIndex(socketChannel);
                            boolean connectionState = processInput(clientIdx);

                            if (!connectionState) {
                                selectedKey.cancel();
                                Socket socket = null;

                                try {
                                    socket = socketChannel.socket();

                                    int removedClientIdx = getClientIndex(socketChannel);

                                    if (connectedClients.get(removedClientIdx).getState().equals("inside")) {
                                        String oldRoom = connectedClients.get(removedClientIdx).getRoom();
                                        String message = "LEFT " + connectedClients.get(removedClientIdx).getNick();

                                        rooms.get(getRoomIndex(oldRoom)).leaveRoom(connectedClients.get(removedClientIdx));

                                        for (Client client : rooms.get(getRoomIndex(oldRoom)).getClients()) {
                                            sendMessage(message, client.getSocketChannel());
                                        }
                                    }

                                    connectedClients.remove(removedClientIdx);

                                    System.out.println("Closing connection to " + socket);
                                    socket.close();
                                } catch (IOException e) {
                                    System.err.println("Error closing socket " + socket + ": " + e);
                                }
                            }
                        } catch (IOException e) {
                            selectedKey.cancel();
                            try {
                                socketChannel.close();

                            } catch (IOException ex) {
                                System.err.println("Error: " + ex);
                            }

                            System.out.println("Closed " + socketChannel);
                        }
                    }
                }

                selectedKeys.clear();
            }
        } catch (IOException e) {
            System.err.println("Error " + e);
        }
    }

    private static boolean processInput(int clientIdx) throws IOException {
        receiveBuffer.clear();
        connectedClients.get(clientIdx).getSocketChannel().read(receiveBuffer);
        receiveBuffer.flip();

        if (receiveBuffer.limit() == 0) {
            return false;
        }

        String input = charsetDecoder.decode(receiveBuffer).toString();

        String curMessage = connectedClients.get(clientIdx).getCurMessage();
        connectedClients.get(clientIdx).setCurMessage(curMessage + input);

        if (!input.endsWith("\n")) {
            return true;
        }

        input = connectedClients.get(clientIdx).getCurMessage();
        connectedClients.get(clientIdx).setCurMessage("");

        String[] inputs = input.split("\n");

        for (int i = 0; i < inputs.length; i++) {
            input = inputs[i];
            if (input.charAt(0) == '/') {
                if (input.length() > 1 && input.charAt(1) == '/') {
                    String message = input.substring(1);
                    processMessage(clientIdx, message);
                } else {
                    String command = input.split(" ")[0];
                    if (command.equals("/nick")) {
                        processNick(clientIdx, input);
                    } else if (command.equals("/join")) {
                        processJoin(clientIdx, input);
                    } else if (command.equals("/leave")) {
                        processLeave(clientIdx, input);
                    } else if (command.equals("/bye")) {
                        processBye(clientIdx, input);
                    } else if (command.equals("/priv")) {
                        processPriv(clientIdx, input);
                    } else {
                        sendError(clientIdx);
                    }
                }
            } else {
                processMessage(clientIdx, input);
            }
        }

        return true;
    }

    private static void processNick(int clientIdx, String input) throws IOException {
        String[] args = input.split(" ");
        if (args.length != 2) {
            sendError(clientIdx);
            return;
        }


        if (!connectedClients.isEmpty()) {
            for (Client client : connectedClients) {
                if (client.getNick().equals(args[1])) {
                    sendError(clientIdx);
                    return;
                }
            }
        }


        String oldNick = "";
        if (!connectedClients.get(clientIdx).getState().equals("init")) {
            oldNick = connectedClients.get(clientIdx).getNick();
        }


        connectedClients.get(clientIdx).setNick(args[1]);
        if (connectedClients.get(clientIdx).getState().equals("init")) {
            connectedClients.get(clientIdx).setState("outside");
        }

        sendOk(clientIdx);

        if (connectedClients.get(clientIdx).getState().equals("inside")) {
            ArrayList<Client> roomClients = rooms.get(getRoomIndex(connectedClients.get(clientIdx).getRoom())).getClients();
            String message = "NEWNICK " + oldNick + " " + connectedClients.get(clientIdx).getNick();
            for (Client client : roomClients) {
                if (!client.getNick().equals(connectedClients.get(clientIdx).getNick())) {
                    sendMessage(message, client.getSocketChannel());
                }
            }
        }
    }

    private static void processJoin(int clientIdx, String input) throws IOException {
        if (connectedClients.get(clientIdx).getState().equals("init")) {
            sendError(clientIdx);
            return;
        }

        String[] args = input.split(" ");
        if (args.length != 2) {
            sendError(clientIdx);
            return;
        }

        String oldRoom = connectedClients.get(clientIdx).getRoom();

        int roomIdx = getRoomIndex(args[1]);
        if (roomIdx == -1) {
            Room createdRoom = new Room(args[1]);
            createdRoom.joinRoom(connectedClients.get(clientIdx));
            rooms.add(createdRoom);
            connectedClients.get(clientIdx).setRoom(args[1]);
            sendOk(clientIdx);
        } else {
            rooms.get(roomIdx).joinRoom(connectedClients.get(clientIdx));
            connectedClients.get(clientIdx).setRoom(args[1]);
            sendOk(clientIdx);
            String message = "JOINED " + connectedClients.get(clientIdx).getNick();
            for (Client client : rooms.get(roomIdx).getClients()) {
                if (!client.getNick().equals(connectedClients.get(clientIdx).getNick())) {
                    sendMessage(message, client.getSocketChannel());
                }
            }
        }

        if (connectedClients.get(clientIdx).getState().equals("inside")) {
            String message = "LEFT " + connectedClients.get(clientIdx).getNick();
            rooms.get(getRoomIndex(oldRoom)).leaveRoom(connectedClients.get(clientIdx));
            for (Client client : rooms.get(getRoomIndex(oldRoom)).getClients()) {
                sendMessage(message, client.getSocketChannel());
            }
        } else {
            connectedClients.get(clientIdx).setState("inside");
        }
    }

    private static void processLeave(int clientIdx, String input) throws IOException {
        if (!connectedClients.get(clientIdx).getState().equals("inside")) {
            sendError(clientIdx);
            return;
        }

        String[] args = input.split(" ");
        if (args.length != 1) {
            sendError(clientIdx);
            return;
        }

        String roomName = connectedClients.get(clientIdx).getRoom();
        rooms.get(getRoomIndex(roomName)).leaveRoom(connectedClients.get(clientIdx));
        connectedClients.get(clientIdx).setRoom(" ");
        sendOk(clientIdx);

        String message = "LEFT " + connectedClients.get(clientIdx).getNick();
        for (Client client : rooms.get(getRoomIndex(roomName)).getClients()) {
            sendMessage(message, client.getSocketChannel());
        }

        connectedClients.get(clientIdx).setState("outside");
    }

    private static void processBye(int clientIdx, String input) throws IOException {
        String[] args = input.split(" ");
        if (args.length != 1) {
            sendError(clientIdx);
            return;
        }

        Client client = connectedClients.get(clientIdx);
        connectedClients.remove(clientIdx);
        String message = "BYE";
        sendMessage(message, client.getSocketChannel());
        if (client.getState().equals("inside")) {
            String roomName = client.getRoom();
            Room room = rooms.get(getRoomIndex(roomName));
            room.getClients().remove(client);
            message = "LEFT " + client.getNick();
            for (Client c : room.getClients()) {
                sendMessage(message, c.getSocketChannel());
            }
        }

        Socket socket = client.getSocketChannel().socket();
        System.out.println("Closing connection to " + socket);
        socket.close();
    }

    private static void processPriv(int clientIdx, String input) throws IOException {
        String[] args = input.split(" ");
        if (args.length != 3){
            sendError(clientIdx);
            return;
        }

        if (connectedClients.get(clientIdx).getState().equals("init")) {
            sendError(clientIdx);
            return;
        }

        Client receiverClient = null;
        for (Client client : connectedClients) {
            if (client.getNick().equals(args[1])) {
                receiverClient = client;
                break;
            }
        }

        if (receiverClient == null) {
            sendError(clientIdx);
            return;
        }

        sendOk(clientIdx);

        int messageStartIndex = args[0].length() + args[1].length() + 2;
        String message = "PRIVATE " + connectedClients.get(clientIdx).getNick() + " " + input.substring(messageStartIndex);
        sendMessage(message, receiverClient.getSocketChannel());
    }

    private static void processMessage(int clientIdx, String input) throws IOException {
        if (!connectedClients.get(clientIdx).getState().equals("inside")) {
            sendError(clientIdx);
            return;
        }

        String roomName = connectedClients.get(clientIdx).getRoom();
        Room room = rooms.get(getRoomIndex(roomName));
        String message = "MESSAGE " + connectedClients.get(clientIdx).getNick() + " " + input;
        for (Client client : room.getClients()) {
            sendMessage(message, client.getSocketChannel());
        }
    }

    private static void sendError(int clientIdx) throws IOException {
        sendBuffer.clear();
        sendBuffer.put(charset.encode("ERROR\n"));
        sendBuffer.flip();
        while (sendBuffer.hasRemaining()) {
            connectedClients.get(clientIdx).getSocketChannel().write(sendBuffer);
        }
    }

    private static void sendOk(int clientIdx) throws IOException {
        sendBuffer.clear();
        sendBuffer.put(charset.encode("OK\n"));
        sendBuffer.flip();
        while (sendBuffer.hasRemaining()) {
            connectedClients.get(clientIdx).getSocketChannel().write(sendBuffer);
        }
    }

    private static void sendMessage(String message, SocketChannel socketChannel) throws IOException {
        sendBuffer.clear();
        sendBuffer.put(charset.encode(message + "\n"));
        sendBuffer.flip();
        while (sendBuffer.hasRemaining()) {
            socketChannel.write(sendBuffer);
        }
    }

    private static int getClientIndex(SocketChannel socketChannel) {
        for (int i = 0; i < connectedClients.size(); i++) {
            if (connectedClients.get(i).getSocketChannel() == socketChannel) {
                return i;
            }
        }

         return -1;
    }

    private static int getRoomIndex(String name) {
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }
}

