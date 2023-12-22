import java.nio.channels.SocketChannel;

public class Client {
    private final SocketChannel socketChannel;
    private String nick;
    private String state;
    private String room;

    public Client(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        state = "init";
        nick = " ";
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

    public String getRoom() {
        return room;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public void setRoom(String room) {
        this.room = room;
    }
}

