import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer {
    private static final ByteBuffer receivedBuffer = ByteBuffer.allocate(16384);
    private static final Charset charset = StandardCharsets.UTF_8;
    private static final CharsetDecoder charsetDecoder = charset.newDecoder();
    private static final ArrayList<Client> connectedClients = new ArrayList<>();

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
                            boolean connectionState;
                            int clientIdx = getClientIndex(socketChannel);
                            if (connectedClients.get(clientIdx).getNick() == null) {
                                connectionState = processNick(clientIdx);
                            } else {
                                connectionState = processInput(clientIdx);
                            }

                            if (!connectionState) {
                                selectedKey.cancel();
                                Socket socket = null;

                                try {
                                    socket = socketChannel.socket();
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
                                connectedClients.remove(getClientIndex(socketChannel));
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
        receivedBuffer.clear();
        connectedClients.get(clientIdx).getSocketChannel().read(receivedBuffer);
        receivedBuffer.flip();

        if (receivedBuffer.limit() == 0) {
            return false;
        }

        // deal with input

        String message = connectedClients.get(clientIdx).getNick()  + ": " + charsetDecoder.decode(receivedBuffer);

        for (Client client : connectedClients) {
            receivedBuffer.clear();
            receivedBuffer.put(charset.encode(message));
            receivedBuffer.flip();
            while (receivedBuffer.hasRemaining()) {
                client.getSocketChannel().write(receivedBuffer);
            }
        }

        return true;
    }

    private static boolean processNick(int clientIdx) throws IOException {
        receivedBuffer.clear();
        connectedClients.get(clientIdx).getSocketChannel().read(receivedBuffer);
        receivedBuffer.flip();

        if (receivedBuffer.limit() == 0) {
            return false;
        }

        String nick = charsetDecoder.decode(receivedBuffer).toString();
        nick = nick.replace("\r", "").replace("\n", "");

        connectedClients.get(clientIdx).setNick(nick);

        return true;
    }

    private static int getClientIndex(SocketChannel socketChannel) {
        for (int i = 0; i < connectedClients.size(); i++) {
            if (connectedClients.get(i).getSocketChannel() == socketChannel) {
                return i;
            }
        }

         return -1;
    }
}

