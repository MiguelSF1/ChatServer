import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class Server {
    private static final ByteBuffer receivedBuffer = ByteBuffer.allocate(16384);
    private static final Charset charset = StandardCharsets.UTF_8;
    private static final CharsetDecoder charsetDecoder = charset.newDecoder();
    private static final ArrayList<SocketChannel> connectedClients = new ArrayList<>();

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
                        connectedClients.add(acceptedSocketChannel);
                    } else if (selectedKey.isReadable()) {
                        SocketChannel socketChannel = null;

                        try {
                            socketChannel = (SocketChannel) selectedKey.channel();
                            boolean connectionState = processInput(socketChannel);

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
                                connectedClients.remove(socketChannel);
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

    private static boolean processInput(SocketChannel socketChannel) throws IOException {
        receivedBuffer.clear();
        socketChannel.read(receivedBuffer);
        receivedBuffer.flip();

        if (receivedBuffer.limit() == 0) {
            return false;
        }

        String message = charsetDecoder.decode(receivedBuffer).toString();

        for (SocketChannel client : connectedClients) {
            System.out.println(client.socket().getRemoteSocketAddress());
            receivedBuffer.clear();
            receivedBuffer.put(charset.encode(message));
            receivedBuffer.flip();
            client.write(receivedBuffer);
        }

        return true;
    }
}

