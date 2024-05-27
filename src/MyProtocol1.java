

import client.*;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.*;

import sun.misc.Signal;
import sun.misc.SignalHandler;

import javax.swing.plaf.synth.SynthOptionPaneUI;

/**
 * This is just some example code to show you how to interact
 * with the server using the provided client and two queues.
 * Feel free to modify this code in any way you like!
 */

public class MyProtocol1{

    // The host to connect to. Set this to localhost when using the audio interface tool.
    private static String SERVER_IP = "netsys.ewi.utwente.nl"; //"127.0.0.1";
    // The port to connect to. 8954 for the simulation server.
    private static int SERVER_PORT = 8954;
    // The frequency to use.
    private static int frequency = 3993;


    // View the simulator at https://netsys.ewi.utwente.nl/integrationproject/
    // The token you received for your frequency range
    String token = "java-34-1TFRGJXZBVMDS6KOI4";


    private static final long SLOT_DURATION = 100; // For example, 100 milliseconds

    private static final long HEARTBEAT_INTERVAL = 60 * 1000; // 30 seconds in milliseconds
    private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();


    // Add a variable to track the current slot number
    private int currentSlot;
    public List<Integer> nodes = new ArrayList<>();
    public List<Integer> neigh_nodes = new ArrayList<>();

    private boolean initialized = false;
    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;
    private Map<Integer, Integer> dynamicAddressMap = new HashMap<>(); // Dynamic address assignment map
    private Map<Integer, Integer> routingTable = new HashMap<>();
    private Map<Integer, Boolean> reachableNodes = new HashMap<>(); // Track reachable nodes
    private List<Integer> acknowledgedNeighbors = new ArrayList<>();
    //int id = assignDynamicAddress(); // Assign dynamic address to new node
    //int id = (int) (Math.random() * 255);
    static int id;

    private static final int MAX_ID = 255; // Maximum ID value
    private static Set<Integer> usedIds = new HashSet<>(); // Set to track used IDs
    private boolean starting_flag = true;






    public MyProtocol1(String server_ip, int server_port, int frequency){


        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();


        new Client(SERVER_IP, SERVER_PORT, frequency, token, receivedQueue, sendingQueue); // Give the client the Queues to use




        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!

        if(starting_flag){
            Message msg1;

            MyProtocol1.id = MyProtocol1.assignDynamicId();
            ByteBuffer toSend1 = ByteBuffer.allocate( 2 ); // copy data without newline / returns
            toSend1.put((byte) id);




            msg1 = new Message(MessageType.DATA_SHORT, toSend1);
            try{
                sendingQueue.put(msg1);
            }catch (InterruptedException e){
                System.exit(2);
            }
            starting_flag = false;
        }
        scheduler.scheduleAtFixedRate(new HeartbeatTask(), HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);




        // handle sending from stdin from this thread.
        try{
            ByteBuffer temp = ByteBuffer.allocate(1024);
            int read = 0;
            int new_line_offset = 0;
            while(true){
                read = System.in.read(temp.array()); // Get data from stdin, hit enter to send!
                if(read > 0){
                    if (temp.get(read-1) == '\n' || temp.get(read-1) == '\r' ) new_line_offset = 1; //Check if last char is a return or newline so we can strip it
                    if (read > 1 && (temp.get(read-2) == '\n' || temp.get(read-2) == '\r') ) new_line_offset = 2; //Check if second to last char is a return or newline so we can strip it

                    if (temp.get(0) == '~') {
                        String command = "";
                        for(int i = 1; i < read - new_line_offset ; i ++){
                            command += (char)temp.get(i);
                        }
                        if(command.equals("shownodes")){
                            System.out.println("Currently know nodes: " + reachableNodes);
                            System.out.println("NEAR NODES:" + nodes);
                            //reachableNodes.clear();
                            //nodes.clear();

                        } else if (command.equals("asktable")){
                            reachableNodes.clear();
                            nodes.clear();
                            ByteBuffer toSend = ByteBuffer.allocate( 2 +  reachableNodes.size() + 2); // copy data without newline / returns
                            toSend.put((byte) 0b00000001);
                            toSend.put((byte) id);
                            toSend.put((byte)reachableNodes.size());
                            //System.out.println(reachableNodes);
                            for (int node : reachableNodes.keySet()) {
                                toSend.put((byte) node);
                            }
                            toSend.put((byte) id);


                            //toSend.put((byte) (read - new_line_offset + 3));
                            //toSend.put( temp.array(), 0, read-new_line_offset ); // enter data without newline / returns
                            Message msg;
                            msg = new Message(MessageType.DATA, toSend);
                            sendingQueue.put(msg);

                        }else{
                            System.out.println( command + " is an invalid command");
                        }
                    } else{
                        ByteBuffer toSend = ByteBuffer.allocate(read-new_line_offset + 2 +  nodes.size() + 4); // copy data without newline / returns
                        toSend.put((byte) 0b00000000);
                        toSend.put((byte) id);
                        toSend.put((byte)nodes.size());
                        for (int node : nodes) {
                            toSend.put((byte) node);
                        }

                        toSend.put((byte) (read - new_line_offset + 3));
                        toSend.put( temp.array(), 0, read-new_line_offset ); // enter data without newline / returns
                        toSend.put((byte)0b00000000);
                        toSend.put((byte) id);

                        dynamicAddressMap.put(id, id); // Assign address to the node

                        Message msg;
                        if( (read-new_line_offset) > 2 ){
                            msg = new Message(MessageType.DATA, toSend);
                        } else {
                            msg = new Message(MessageType.DATA_SHORT, toSend);
                        }

                        sendingQueue.put(msg);
                    }

                }


                //updateReachableNodes(); // Update reachable nodes
                //removeUnreachableNodes1(); // Remove addresses of unreachable nodes
                //Thread.sleep(5000); // Check every 5 seconds
            }
        } catch (InterruptedException e){
            System.exit(2);
        } catch (IOException e){
            System.exit(2);
        }
    }

    private static int assignDynamicId() {
        Scanner scanner = new Scanner(System.in);
        do {
            System.out.print("Enter a unique ID for the node (0 - 255): ");
            id = scanner.nextInt();
            if (id < 0 || id > MAX_ID) {
                System.out.println("Invalid ID. Please enter a number between 0 and 255.");
            } else if (usedIds.contains(id)) {
                System.out.println("ID " + id + " is already taken. Please choose another ID.");
            } else {
                usedIds.add(id);
                System.out.println("ID " + id + " assigned to the node.");
                return id;
            }
        } while (true);
    }

    private class HeartbeatTask implements Runnable {
        @Override
        public void run() {
            try {
                System.out.println("HEARTBEAT");
                // Clear nodes before sending heartbeat
                reachableNodes.clear();
                nodes.clear();
                ByteBuffer toSend = ByteBuffer.allocate( 2 +  reachableNodes.size() + 2); // copy data without newline / returns
                toSend.put((byte) 0b00000001);
                toSend.put((byte) id);
                toSend.put((byte)reachableNodes.size());
                //System.out.println(reachableNodes);
                for (int node : reachableNodes.keySet()) {
                    toSend.put((byte) node);
                }
                toSend.put((byte) id);


                //toSend.put((byte) (read - new_line_offset + 3));
                //toSend.put( temp.array(), 0, read-new_line_offset ); // enter data without newline / returns
                Message msg;
                msg = new Message(MessageType.DATA, toSend);
                /*
                Random random = new Random();
                currentSlot = random.nextInt(101); // Random integer between 0 and 100 (inclusive)
                while (true){
                    currentSlot++;

                    // Broadcast presence or handle other messages only at the beginning of a slot
                    if (currentSlot % SLOT_DURATION == 0 && receivedQueue.take().getType() == MessageType.FREE) {
                        break;
                    }

                    // Sleep for a short duration to simulate slot duration
                    Thread.sleep(SLOT_DURATION);
                }

                 */
                sendingQueue.put(msg);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }



    public static void main(String args[]) {
        //System.out.println("args"+args);
        if(args.length > 0){
            frequency = Integer.parseInt(args[0]);
        }

        new MyProtocol(SERVER_IP, SERVER_PORT, frequency);
    }

    private class receiveThread extends Thread {
        private BlockingQueue<Message> receivedQueue;

        public receiveThread(BlockingQueue<Message> receivedQueue){
            super();
            this.receivedQueue = receivedQueue;
        }

        public void printByteBuffer(ByteBuffer bytes, int bytesLength){
            for(int i=0; i<bytesLength; i++){
                System.out.print( Byte.toString( bytes.get(i) )+" " );
            }
            System.out.println();
        }


        public void run(){
            while(true && starting_flag){
                try {
                    Message m = receivedQueue.take();
                    if (m.getType() == MessageType.DATA_SHORT) {
                        if ((int) m.getData().get(0) != 0) {
                            usedIds.add((int) m.getData().get(0));
                        }
                    }
                }  catch (InterruptedException e){
                    System.err.println("Failed to take from queue: "+e);
                }
            }
            while(true ) {
                try{
                    Message m = receivedQueue.take();


                    /*
                    if(m.getData() != null && ((int) m.getData().get(0) == 0) && ((int) m.getData().get(2 + (int) m.getData().get(2) + m.getData().get(3+m.getData().get(2)) ))==(byte) id ){
                        System.out.println("ALREADY SAW MESSAGE(kinda acknowledgement)");
                        if(!nodes.contains((int) m.getData().get(1))) nodes.add((int) m.getData().get(1));
                        reachableNodes.put((int)m.getData().get(1),true);
                        continue;
                    }

                     */
                    if(m.getData() != null && ((int) m.getData().get(0) == 0)  ){
                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data
                        boolean flag = false;
                        System.out.println("index"+1 + (int) m.getData().get(2) +(int) m.getData().get(3+m.getData().get(2))+"data:"+(int) m.getData().get(1 + (int) m.getData().get(2) +(int) m.getData().get(3+m.getData().get(2))));
                        //int starting =(int) m.getData().get(1 + (int) m.getData().get(2) +(int) m.getData().get(3+m.getData().get(2)))+2 + (int) m.getData().get(2) +(int) m.getData().get(3+m.getData().get(2));
                        //int end = 2 + (int) m.getData().get(2) +(int) m.getData().get(3+m.getData().get(2)) + (int) m.getData().get(3+m.getData().get(2));
                        int starting = 2 + (int) m.getData().get(2) +(int) m.getData().get(3+m.getData().get(2));
                        int end = starting + m.getData().get(starting-1);
                        //int end = (int) m.getData().get(1 + (int) m.getData().get(2) +(int) m.getData().get(3+m.getData().get(2)))+starting;
                        System.out.println("starting:" + starting);
                        for(int i = starting;i<end+1;i++){
                            //System.out.println("----"+(int)m.getData().get(i));
                            if((int)m.getData().get(i) == (byte)id){
                                System.out.println("ALREADY SAW MESSAGE(kinda acknowledgement)");
                                flag = true;
                            }
                        }
                        if(flag) continue;

                    }
                    if(m.getData() != null && ((int) m.getData().get(0) != 0) && (byte) id!=(int) m.getData().get(1)){

                        if((int) m.getData().get(0) == 10 || (int) m.getData().get(0) == 10){//we shouldnt include itself
                            System.out.println("REMOVING-1");
                            continue;
                        }


                    }
                    if(m.getData() != null && m.getType() == MessageType.DATA&& ((int) m.getData().get(0) != 0) && !nodes.contains((int)m.getData().get(1)) && (byte) id!=(int) m.getData().get(1)){
                        nodes.add((int) m.getData().get(1));
                        System.out.println("ADDING:"+(int) m.getData().get(1));
                    }


                    if(m.getData() != null && ((int) m.getData().get(0) == 0) && !nodes.contains((int)m.getData().get(1)) && (byte) id!=(int) m.getData().get(1)){
                        nodes.add((int) m.getData().get(1));
                        reachableNodes.put((int)m.getData().get(1),true);
                        //System.out.println((int) m.getData().get(1));
                    }
                    //System.out.println("MY ID:"+id+"bit:"+(byte) id);
                    //System.out.println("reachable1:"+reachableNodes);
                    if (m.getType() == MessageType.BUSY){
                        System.out.println("BUSY");
                    } else if (m.getType() == MessageType.FREE){
                        System.out.println("FREE");

                    } else if (m.getType() == MessageType.DATA){
                        System.out.print("DATA BEFORE: ");
                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data
                        if(m.getData().get(0) == 0b00000000){
                            int length = m.getData().get(3+m.getData().get(2));

                            for(int i = 3;i< 3+ m.getData().get(2);i++){
                                neigh_nodes.add((int) m.getData().get(i));
                            }
                            String text = "";

                            for(int i = 4+m.getData().get(2); i < length+1+m.getData().get(2); i++){
                                text += (char) m.getData().get(i);
                            }
                            System.out.println("which gives message : " + text);

                            for(int node : nodes){
                                //System.out.println("NODDE:" + node);
                                if(!neigh_nodes.contains(node) && node != id ){//check here && node != (int) m.getData().get(0)
                                    //System.out.println("node:"+node+"id:"+id);
                                    ByteBuffer toSend = ByteBuffer.allocate(length + 3 + nodes.size() + neigh_nodes.size()); // Allocate space for data length and node ID

                                    toSend.put((byte) 0b00000000);
                                    toSend.put((byte) id); //
                                    toSend.put((byte)nodes.size());
                                    for (int node1 : nodes) {
                                        toSend.put((byte) node1);
                                    }
                                    toSend.put((byte) length); // Data length
                                    for (int i = 4 + m.getData().get(2); i < 1 + m.getData().get(2) + length; i++) {
                                        toSend.put( m.getData().get(i)); //the actual message
                                    }
                                    toSend.put((byte)(neigh_nodes.size()));
                                    for (int node2 : neigh_nodes) {
                                        toSend.put((byte) node2);
                                    }
                                    toSend.put( m.getData().get(1));
                                    Random random = new Random();
                                    currentSlot = random.nextInt(101); // Random integer between 0 and 100 (inclusive)
                                    while (true){
                                        currentSlot++;

                                        // Broadcast presence or handle other messages only at the beginning of a slot
                                        if (currentSlot % SLOT_DURATION == 0 && receivedQueue.take().getType() == MessageType.FREE) {
                                            break;
                                        }

                                        // Sleep for a short duration to simulate slot duration
                                        Thread.sleep(SLOT_DURATION);
                                    }

                                    //toSend.put(m.getData().array(), 2 +nodes.size()+ m.getData().get(1), length); // Copy the data from the received message
                                    Message msg = new Message(MessageType.DATA, toSend);
                                    sendingQueue.put(msg); // Send the message
                                    System.out.print("Transmitted Message: ");

                                    printByteBuffer( msg.getData(), msg.getData().capacity() ); //Just print the data
                                    System.out.println("AAAAAAAA");
                                    break;
                                /*
                                ByteBuffer toSendAck = ByteBuffer.allocate(2); // copy data without newline / returns
                                toSendAck.put((byte) id);
                                toSendAck.put((byte) 0b10000000);
                                Message msgAck = new Message(MessageType.DATA_SHORT, toSendAck);
                                sendingQueue.put(msgAck);

                                 */
                                }
                            }
                        }
                        else{
                            int count = m.getData().get(0);
                            boolean flag1 = false;
                            for(int i = 1;i<3+(int)m.getData().get(2);i++){
                                if(i!=2) {
                                    System.out.println("XXXXXX"+reachableNodes);
                                    System.out.println("reachable canditate:" + (int) m.getData().get(i) + "i:" + i);
                                    if (!reachableNodes.containsKey((int )m.getData().get(i)) && (byte) id!=(int) m.getData().get(i) || (reachableNodes.containsKey((int) m.getData().get(i)) && !reachableNodes.get((int) m.getData().get(i)))) { // it should be (int) m.getdata.get(i) but this way works only
                                        System.out.println("WE DONT HAVE IT");
                                        reachableNodes.put((int) m.getData().get(i), true);
                                        count = 1;
                                    } else if(reachableNodes.containsKey((int) m.getData().get(i)) || (reachableNodes.containsKey((int) m.getData().get(i)) && !reachableNodes.get((int) m.getData().get(i)))) {
                                        System.out.println("WE  HAVE IT");
                                        count++;
                                    }

                                    if (count == 10) { // it might be better if it six
                                        System.out.println("REMOVING-3");
                                        break;
                                    }
                                }

                            }
                            if(count > 9) {
                                System.out.println("REMOVING-2");
                                continue;
                            }
                            //for(int i = 0; i<3;i++) {


                            ByteBuffer toSend = ByteBuffer.allocate(2 + reachableNodes.size() + 2); // copy data without newline / returns
                            toSend.put((byte) count);
                            toSend.put((byte) id);
                            toSend.put((byte) reachableNodes.size());
                            //System.out.println("reachable2:"+reachableNodes);
                            for (int node : reachableNodes.keySet()) {
                                toSend.put((byte) node);
                            }
                            toSend.put(m.getData().get(3 + (int) m.getData().get(2)));
                            Random random = new Random();
                            currentSlot = random.nextInt(101); // Random integer between 0 and 100 (inclusive)
                            while (true) {
                                currentSlot++;

                                // Broadcast presence or handle other messages only at the beginning of a slot
                                if (currentSlot % SLOT_DURATION == 0 && receivedQueue.take().getType() == MessageType.FREE) {
                                    break;
                                }

                                // Sleep for a short duration to simulate slot duration
                                Thread.sleep(SLOT_DURATION);
                            }
                            //Thread.sleep((long) (3000));//Math.random() * 1000 +

                            //toSend.put((byte) (read - new_line_offset + 3));
                            //toSend.put( temp.array(), 0, read-new_line_offset ); // enter data without newline / returns
                            Message msg;
                            msg = new Message(MessageType.DATA, toSend);
                            sendingQueue.put(msg);
                            System.out.print("DATA AFTER: ");
                            printByteBuffer(msg.getData(), msg.getData().capacity()); //Just print the data

                        }
                        //if(flag1) continue;



                    }else if (m.getType() == MessageType.DATA_SHORT){
                        System.out.print("DATA_SHORT: ");
                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data
                        if((int)m.getData().get(0) != 0){
                            usedIds.add((int)m.getData().get(0));
                        }
                        /*
                        int count = m.getData().get(1);
                        if(!reachableNodes.containsKey((int)m.getData().get(0))){
                            reachableNodes.put((int)m.getData().get(0) ,true);
                            count=0;
                        }
                        else count++;
                        if(count == 4){
                            continue;
                        }
                        ByteBuffer toSend = ByteBuffer.allocate( 2);
                        toSend.put( m.getData().get(0));
                        toSend.put((byte) count);
                        Message msg = new Message(MessageType.DATA_SHORT, toSend);
                        sendingQueue.put(msg);

                         */


                    } else if (m.getType() == MessageType.DONE_SENDING){
                        System.out.println("DONE_SENDING");
                    } else if (m.getType() == MessageType.HELLO){
                        System.out.println("HELLO");
                    } else if (m.getType() == MessageType.SENDING){
                        System.out.println("SENDING");
                    } else if (m.getType() == MessageType.END){
                        System.out.println("END");
                        System.exit(0);
                    } else if (m.getType() == MessageType.TOKEN_ACCEPTED){
                        System.out.println("Token Valid!");
                    } else if (m.getType() == MessageType.TOKEN_REJECTED){
                        System.out.println("Token Rejected!");
                    } else if (m.getType() == MessageType.ACK) {
                        // Process acknowledgment
                        int acknowledgedNodeId = m.getData().get(0);
                        acknowledgedNeighbors.add(acknowledgedNodeId);
                        System.out.println("Received ACK from node " + acknowledgedNodeId);
                    }else if (m.getType() == MessageType.PRESENCE) {
                        System.out.println("PRESENCE");
                        nodes.add((int) m.getData().get(0));
                        System.out.println(nodes);
                    }
                } catch (InterruptedException e){
                    System.err.println("Failed to take from queue: "+e);
                }
            }
        }
    }
}





