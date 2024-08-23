import org.json.simple.JSONObject;
import java.io.*;
import java.net.Socket;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Client class
 */
public class Client {
    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String username;

    /**
     * constructor
     * @param socket Socket
     * @param username String
     */
    public Client(Socket socket, String username){
        this.socket = socket;
        this.username = username;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        } catch (IOException e) {
            close();
        }
    }

    /**
     * close everything
     */
    private void close(){
        try {
            if (bufferedWriter != null)
                bufferedWriter.close();
            if (bufferedReader != null)
                bufferedReader.close();
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * give data for making a group and make its json object
     * @param in Scanner
     * @throws IOException
     */
    private void createGroup(Scanner in) throws IOException {
        System.out.println("group name: ");
        String groupName = in.nextLine();
        System.out.println("description: ");
        String description = in.nextLine();
        System.out.println("n: ");
        int n = in.nextInt();
        in.nextLine();
        JSONObject JSONGroup = new JSONObject();
        JSONGroup.put("create_group",null);
        JSONGroup.put("group_name",groupName);
        JSONGroup.put("creator",username);
        JSONGroup.put("created_at",Instant.now().getEpochSecond());
        JSONGroup.put("description",description);
        JSONGroup.put("members", username + "/");
        JSONGroup.put("n hours", n);
        JSONGroup.writeJSONString(bufferedWriter);
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    /**
     * convert a string in form "yyyy-MM-dd HH:mm:ss" to seconds
     * @param s String
     * @return Long
     * @throws ParseException ParseException
     */
    private Long convertStringToSeconds(String s) throws ParseException {
        LocalDateTime date = LocalDateTime.parse(s,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()/1000L;
    }

    private boolean checkDateTimeFormat(String str){
        // DateTime(YYYY-MM-DD HH:MM:SS)
        String regex
                = "^([0-9]{4})-(01|02|03|04|05|06|07|08|09|10|11|12|)"
                + "-([0-3][0-9])\\s([0-1][0-9]|[2][0-3]):([0-5][0-9])"
                + ":([0-5][0-9])$";

        Pattern p = Pattern.compile(regex);

        if (str == null) {
            return false;
        }
        Matcher m = p.matcher(str);
        return m.matches();
    }
    /**
     * gives info of a message snd send it to server
     * @param in Scanner
     * @throws IOException IOException
     * @throws ParseException ParseException
     */
    private void findMessage(Scanner in) throws IOException, ParseException {
        System.out.println("group name: ");
        String groupName = in.nextLine();
        System.out.println("sender name: ");
        String sender = in.nextLine();
        System.out.println("timestamp(yyyy-MM-dd HH:mm:ss): ");
        String time = in.nextLine();
        if (!checkDateTimeFormat(time)){
            System.out.println("Invalid date time format");
            return;
        }
        JSONObject JSONFindMessage = new JSONObject();

        JSONFindMessage.put("find",null);
        JSONFindMessage.put("group_name",groupName);
        JSONFindMessage.put("sender",sender);
        JSONFindMessage.put("time",convertStringToSeconds(time));
        JSONFindMessage.writeJSONString(bufferedWriter);
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    /**
     * gives a message snd send it to server
     * @param message String
     * @throws IOException IOException
     */
    private void handleMessage(String message) throws IOException {
        JSONObject JSONMessage = new JSONObject();
        JSONMessage.put("sent_at", Instant.now().getEpochSecond());
        JSONMessage.put("sender",username);
        JSONMessage.put("groupName",message.substring(message.indexOf("<")+1,message.indexOf(">")));
        JSONMessage.put("text",message.substring(message.indexOf(">")+2));
        JSONMessage.writeJSONString(bufferedWriter);
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    /**
     * write
     * @param s String
     * @throws IOException IOException
     */
    private void write(String s) throws IOException {
        bufferedWriter.write(s);
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    /**
     * write a message in console and send it to clienthandler
     */
    private void sendMessageToClientHandler(){
        try {
            write(username);

            Scanner in = new Scanner(System.in);
            while (socket.isConnected()){
                String message = in.nextLine();

                if (message.equals("#exit")){
                    close();
                    System.exit(0);
                }
                else if (message.startsWith("#list") ||
                        message.startsWith("#join ") ||
                        message.startsWith("#leave ") ||
                        message.startsWith("#load ")){
                    write(message);
                }
                else if (message.equals("#find")){
                    findMessage(in);
                }
                else if (message.equals("#create-group"))
                    createGroup(in);
                else if (message.startsWith("<") && message.contains(">"))
                    handleMessage(message);
                else
                    System.out.println("Invalid");
            }
        }catch (IOException e) {
            close();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    /**
     * print received messages in console
     */
    private void receiveMessages(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;

                while (socket.isConnected()){
                    try {
                        message = bufferedReader.readLine();

                        System.out.println(message);
                    }catch (IOException e) {
                        close();
                    }
                }
            }
        }).start();
    }

    /**
     * main method
     * @param args String[]
     */
    public static void main(String[] args) throws IOException {
        Scanner in = new Scanner(System.in);
        System.out.print("Enter your username: ");
        String username = in.nextLine();
        Socket socket = new Socket("localhost", 8000);
        Client client = new Client(socket, username);
        welcomeMessage(username);
        client.receiveMessages();
        client.sendMessageToClientHandler();
    }

    private static void welcomeMessage(String username){
        System.out.println("\u001B[36mHi " + username);
        System.out.println("whenever you want to exit type #exit \n" +
                "to see all groups type #list\n" +
                "to join to a group type #join groupName\n" +
                "to leave from a group type #leave groupName\n" +
                "to load history messages of a group type #load groupName\n" +
                "to find a message type #find and then fill in the requested information\n" +
                "to create a group type #create-group and then fill in the requested information\n" +
                "to send a message type the group name in <> and then your message like <group1> Hello \u001B[0m");
    }
}