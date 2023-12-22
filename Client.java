import java.nio.channels.SocketChannel;

public class Client {
    private final SocketChannel socketChannel;
    private String nick;
    private String state;

    public Client(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        state = "init";
        nick = null;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public String getNick() {
        return nick;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }
}
