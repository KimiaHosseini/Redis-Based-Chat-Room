import redis.clients.jedis.Jedis;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Server class
 */
public class Server {
    private ServerSocket serverSocket;
    private Jedis messages;
    private Jedis groups;

    /**
     * constructor
     * @param serverSocket ServerSocket
     */
    public Server(ServerSocket serverSocket){
        this.serverSocket = serverSocket;
        messages = new Jedis("localhost",6379);
        groups = new Jedis("localhost",6379);
        groups.select(1);
    }

    /**
     * do server's task
     */
    private void startServer(){
        try {
            while (!serverSocket.isClosed()){
                //wait to connect to a client
                Socket socket = serverSocket.accept();
                System.out.println("New user connected");

                //start this client's work on a thread
                ClientHandler clientHandler = new ClientHandler(socket, messages, groups);
                Thread thread = new Thread(clientHandler);
                thread.start();
            }
        } catch (IOException e) {
            closeServer();
        }
    }

    /**
     * close server socket
     */
    private void closeServer(){
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * main method
     * @param args String[]
     * @throws IOException when can not make a serverSocket
     */
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8000);
        Server server = new Server(serverSocket);
        server.startServer();
    }
}