import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.io.*;
import java.net.Socket;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;



/**
 * clientHandlerClass implements runnable
 */
public class ClientHandler implements Runnable{
    //fields
    private static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String username;
    private Jedis messages;
    private Jedis groups;
    /**
     * constructor
     * @param socket Socket
     */
    public ClientHandler(Socket socket,Jedis messages, Jedis groups){
        this.socket = socket;
        this.messages = messages;
        this.groups = groups;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.username = bufferedReader.readLine();
            clientHandlers.add(this);
        } catch (IOException e) {
            close();
        }
    }

    /**
     * close everything
     */
    private void close(){
        removeClientHandler();
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
     * gives time in long and returns it in String with format "[yyyy-MM-dd HH:mm:ss]"
     * @param seconds Long
     * @return String
     */
    private String time(long seconds){
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("[yyyy-MM-dd HH:mm:ss]");
        LocalDateTime date = Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).toLocalDateTime();
        return dtf.format(date);
    }

    /**
     * print last n hour messages in a group
     * @param selectedGroup String
     */
    private void loadLastNHourMessages(String selectedGroup){
        long now = Instant.now().getEpochSecond();
        long from = now - Integer.parseInt(groups.hget(selectedGroup,"n hours"))* 3600L;

        Set<String> set = messages.keys("*"+selectedGroup);
        System.out.println(set);
        TreeSet<String> sorted = new TreeSet<>(set);
        System.out.println(sorted);
        for (String key : sorted) {
            String text = messages.get(key);
            String[] temp = key.split("-");
            String time = temp[0];
            String sender = temp[1];
            if(Long.parseLong(time) > from){
                showMessage("\u001B[34m" + formatMsg(time, sender, selectedGroup, text) + "\u001B[0m");
            }
        }

    }

    /**
     * gives a group name and leave from that group
     * @param messageFromClient String
     */
    private void leaveGroup(String messageFromClient){
        String selectedGroup = messageFromClient.substring(7);
        System.out.println(selectedGroup);
        if(!groupExists(selectedGroup)){
            showMessage("\u001B[31m" + "Invalid group name" + "\u001B[0m");
            return;
        }
        else if (!isInGroup(selectedGroup)){
            showMessage("\u001B[31m" +"You are not in this group" + "\u001B[0m");
            return;
        }
        String temp = groups.hget(selectedGroup, "members");
        temp = temp.replace(username + "/","");
        groups.hset(selectedGroup,"members",temp);

        broadCastMessage("\u001B[36m" + username + " left " + selectedGroup+ "\u001B[0m",selectedGroup );
    }

    /**
     * gives a group name and join to that group
     * @param messageFromClient String
     */
    private void joinGroup(String messageFromClient){
        String selectedGroup = messageFromClient.substring(6);
        if(!groupExists(selectedGroup)){
            showMessage("\u001B[31m" + "Invalid group name"+ "\u001B[0m");
            return;
        }
        else if (isInGroup(selectedGroup)){
            showMessage("\u001B[31m" + "You are in this group"+ "\u001B[0m");
            return;
        }
        String temp = groups.hget(selectedGroup, "members");
        temp = temp + username + "/";
        groups.hset(selectedGroup,"members",temp);

        broadCastMessage("\u001B[36m" + username + " joined to " + selectedGroup+ "\u001B[0m",selectedGroup );

        loadLastNHourMessages(selectedGroup);
    }

    /**
     * gives a json object and save its data to groups
     * @param jsonObject JSONObject
     */
    private void createGroup(JSONObject jsonObject){
        String groupKey = jsonObject.get("group_name").toString();
        if (groupExists(groupKey)){
            showMessage("\u001B[31m" + "Invalid group name" + "\u001B[0m");
            return;
        }
        groups.hset(groupKey,"creator",jsonObject.get("creator").toString());
        groups.hset(groupKey,"created_at",time((long)jsonObject.get("created_at")));
        groups.hset(groupKey,"description",jsonObject.get("description").toString());
        groups.hset(groupKey,"members",jsonObject.get("members").toString());
        groups.hset(groupKey,"n hours",jsonObject.get("n hours").toString());
    }

    /**
     * find a message by its key : groupName-sender-time
     * @param jsonObject JSONObject
     */
    private void findMessage(JSONObject jsonObject){
        Pipeline pipeline = messages.pipelined();
        String groupName = jsonObject.get("group_name").toString();
        String messageKey = jsonObject.get("time").toString() + "-" + jsonObject.get("sender").toString() + "-" + groupName;
        System.out.println("/" + groupName + "/");
        if (!groupExists(groupName)){
            showMessage("\u001B[31m" + "Invalid group name" + "\u001B[0m");
            return;
        }
        if (!isInGroup(groupName)){
            showMessage("\u001B[31m" + "You are not in this group" + "\u001B[0m");
            return;
        }
        Response<String> response = pipeline.get(messageKey);
        pipeline.sync();
        String text = response.get();
        if (text == null){
            showMessage("\u001B[31m" + "Invalid info" + "\u001B[0m");
            return;
        }
        showMessage("\u001B[34m" + text + "\u001B[0m");
    }

    /**
     * send message to a group
     * check group name is valid
     * check the user is in this group
     * broadcast the message
     * @param jsonObject JSONObject
     * @param messageFromClient String
     * @return int
     */
    private int sendMessage(JSONObject jsonObject, String messageFromClient){
        String groupName = jsonObject.get("groupName").toString();
        if(!groupExists(groupName)) {
            showMessage("\u001B[31m" + "Invalid group name" + "\u001B[0m");
            return 1;
        }

        else if (!isInGroup(groupName)) {
            showMessage("\u001B[31m" + "You are not in this group" + "\u001B[0m");
            return 1;
        }


        String sender = jsonObject.get("sender").toString();
        String time = jsonObject.get("sent_at").toString();
        String text = jsonObject.get("text").toString();
        String messageKey = time + "-" + sender + "-" + groupName;
        messages.set(messageKey,text);


        messageFromClient = "\u001B[36m" + formatMsg(time,sender,groupName,text) + "\u001B[0m";
        if(!broadCastMessage(messageFromClient, groupName)){
            close();
            return 0;
        }
        return 1;
    }

    /**
     * run method
     * reads messages requests from client and send response
     */
    @Override
    public void run() {
        String messageFromClient;
        while (socket.isConnected()){
            try {
                messageFromClient = bufferedReader.readLine();
                if (messageFromClient == null) {
                    close();
                    break;
                }

                if (messageFromClient.startsWith("#list")){
                    System.out.println("list client handler");
                    for (String key : groups.keys("*")) {
                        showMessage(key + groups.hgetAll(key).toString());
                    }
                    continue;
                }

                else if (messageFromClient.startsWith("#join")){
                    joinGroup(messageFromClient);
                    continue;
                }
                else if (messageFromClient.startsWith("#leave")){
                    leaveGroup(messageFromClient);
                    continue;
                }

                else if (messageFromClient.startsWith("#load ")){
                    String groupName = messageFromClient.substring(6);
                    if (!groupExists(groupName) || !isInGroup(groupName))
                        showMessage("Invalid group name");
                    else
                        loadLastNHourMessages(groupName);
                    continue;
                }

                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(messageFromClient);

                if (jsonObject.containsKey("create_group"))
                    createGroup(jsonObject);

                else if (jsonObject.containsKey("find"))
                    findMessage(jsonObject);

                else if(sendMessage(jsonObject,messageFromClient) == 0)
                    break;

            } catch (IOException e) {
                close();
                break;
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * getter for username
     * @return String
     */
    public String getUsername() {
        return username;
    }

    /**
     * format a message
     * @param time String
     * @param sender String
     * @param groupName String
     * @param txt String
     * @return String
     */
    private String formatMsg(String time, String sender, String groupName, String txt){
        return "[" + time(Long.parseLong(time)) + "] " + sender + " (" + groupName + "): " + "\"" + txt + "\"";
    }

    /**
     * this method gives a message and send it to all clientHandlers except itself
     * @param message String
     * @return false when client wants to exit
     */
    private boolean broadCastMessage(String message, String groupName){
        if (message.endsWith("#exit"))
            return false;
        for (ClientHandler clientHandler : clientHandlers) {
            try {
                if (!clientHandler.getUsername().equals(username) && clientHandler.isInGroup(groupName)) {
                    clientHandler.bufferedWriter.write(message);
                    clientHandler.bufferedWriter.newLine();
                    clientHandler.bufferedWriter.flush();
                }
            } catch (IOException e) {
                close();
            }
        }
        return true;
    }

    /**
     * this method gives a message and send it to all clientHandlers except itself
     * @param message String
     */
    private void showMessage(String message){
        try {
            this.bufferedWriter.write(message);
            this.bufferedWriter.newLine();
            this.bufferedWriter.flush();
        } catch (IOException e) {
            close();
        }
    }

    /**
     * remove a client and notify other
     */
    public void removeClientHandler(){
        clientHandlers.remove(this);
    }

    /**
     * check if this user is in the given group
     * @param groupName String
     * @return boolean
     */
    private boolean isInGroup(String groupName){
        return groups.hget(groupName, "members").contains(username + "/");
    }

    /**
     * check if the given group name exists or no
     * @param groupName String
     * @return boolean
     */
    private boolean groupExists(String groupName){
        return groups.exists(groupName);
    }
}