package edu.ufl.cise.message;

import edu.ufl.cise.logger.Logger;
import edu.ufl.cise.model.MessageType;
import edu.ufl.cise.process.PeerProcess;
import edu.ufl.cise.process.RunPeer;
import edu.ufl.cise.utils.IntervalScheduler;
import edu.ufl.cise.utils.CommonConfigParser;
import edu.ufl.cise.utils.FileParser;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MessageHandler {
    public static int peerId;
    public static Set<Integer> unchokedNeighbors = new HashSet<>();
    public static Set<Integer> optimallyUnchokedNeighbor = new HashSet<>();
    public boolean firstInterestedFlag = false;
    public IntervalScheduler unChokeIntervalScheduler;
    public IntervalScheduler optimalUnChokeIntervalScheduler;
    public static Map<Integer, RunPeer> RunPeerMapping = new ConcurrentHashMap<>();
    public static Map<Integer, List<Integer>> currentPeerInterestingTracker = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> otherPeerInterestingTracker = new ConcurrentHashMap<>();

    public MessageHandler(int peerId) {
        MessageHandler.peerId = peerId;
    }

    public void constructPeerMap(int peerId, RunPeer runPeer) {
        RunPeerMapping.put(peerId, runPeer);
    }

    public void sendRequestHaveMsg(int type, int index, ObjectOutputStream out) {
        byte[] result = new byte[9];
        byte[] length = Utility.convertToByteArray(4);
        byte[] indexByteArray = Utility.convertToByteArray(index);
        System.arraycopy(length, 0, result, 0, 4);
        result[4] = Utility.convertToByteArray(type)[3];
        System.arraycopy(indexByteArray, 0, result, 5, 4);
        SendMessage.pushMessage(result, out);
    }

    public void sendPiece(byte[] pieceIndex, ObjectOutputStream out) {
        byte[] pieceContent = FileParser.getPiece(Utility.convertToInt(pieceIndex));
        int pieceSize = pieceContent.length;
        byte[] msg = new byte[5 + 4 + pieceSize];
        byte[] msgLength = Utility.convertToByteArray(5 + pieceSize);
        byte type = Utility.convertToByteArray(MessageType.piece.getMessageTypeValue())[3];
        System.arraycopy(msgLength, 0, msg, 0, 4);
        msg[4] = type;
        System.arraycopy(pieceIndex, 0, msg, 5, 4);
        System.arraycopy(pieceContent, 0, msg, 9, pieceSize);
        SendMessage.pushMessage(msg, out);
    }

    public void interestOrNot(byte[] message, ObjectOutputStream out, int peerID) {
        boolean flag;
        flag = false;
        for (int i = 0; i < PeerProcess.field.length; i++) {
            if (message[i] == 1 && PeerProcess.field[i] == 0) {
                SendMessage.sendMessage(MessageType.interested.getMessageTypeValue(), out);
                selectRandomPiece(peerID, out);
                flag = true;
                break;
            }
        }
        if (!flag) {
            SendMessage.sendMessage(MessageType.not_interested.getMessageTypeValue(), out);
        }
    }

    public List<Integer> findInterestingPieces(int peerId) {
        List<Integer> result = new LinkedList<>();
        byte[] peerList = PeerProcess.peersBitfields.get(peerId);
        for (int i = 0; i < PeerProcess.field.length; i++) {
            if (PeerProcess.field[i] == 0 && peerList[i] == 1) {
                result.add(i);
            }
        }
        if (result.size() != 0) {
            return result;
        }
        return null;
    }

    public void selectRandomPiece(int peerId, ObjectOutputStream out) {
        synchronized (MessageHandler.class) {
            Random rand = new Random();
            while (currentPeerInterestingTracker.containsKey(peerId)) {
                int index = rand.nextInt(currentPeerInterestingTracker.get(peerId).size());
                if (PeerProcess.field[currentPeerInterestingTracker.get(peerId).get(index)] == 0) {
                    PeerProcess.field[currentPeerInterestingTracker.get(peerId).get(index)] = 2;
                    sendRequestHaveMsg(MessageType.request.getMessageTypeValue(), currentPeerInterestingTracker.get(peerId).get(index), out);
                    Utility.deleteMap(currentPeerInterestingTracker.get(peerId).get(index));
                    break;
                }
            }
        }
    }

    public void handleActualMsg(ObjectInputStream in, ObjectOutputStream out, int peerID) {
        byte[] peerBitField;
        while (true) {
            if (peerId == PeerProcess.firstPeerID && PeerProcess.peersToProcess.size() == 0) {
                for (Integer key : RunPeerMapping.keySet())
                    SendMessage.sendMessage(MessageType.all_finish.getMessageTypeValue(), RunPeerMapping.get(key).getOutputStream());
                Logger.applicationExit();
                System.exit(0);
            }

            try {
                byte[] message = (byte[]) in.readObject();
                int type = message[4];
                if(type==MessageType.piece.getMessageTypeValue()){
                    synchronized (MessageHandler.class) {
                        if (otherPeerInterestingTracker.containsKey(peerID)) {
                            otherPeerInterestingTracker.put(peerID, otherPeerInterestingTracker.get(peerID) + 1);
                        } else {
                            otherPeerInterestingTracker.put(peerID, 1);
                        }
                        byte[] pieceID = new byte[4];
                        System.arraycopy(message, 5, pieceID, 0, 4);
                        int pieceIDInt = Utility.convertToInt(pieceID);
                        PeerProcess.field[pieceIDInt] = 1;
                        Utility.deleteMap(pieceIDInt);
                        System.arraycopy(message, 9, FileParser.fileFragments[pieceIDInt], 0, Math.min(message.length - 9, FileParser.fileFragments[pieceIDInt].length));
                        sendRequestHaveMsg(MessageType.have.getMessageTypeValue(), pieceIDInt, out);
                        for (Integer key : PeerProcess.peersBitfields.keySet()) {
                            peerBitField = PeerProcess.peersBitfields.get(key);
                            interestOrNot(peerBitField, RunPeerMapping.get(key).getOutputStream(), peerID);
                        }
                        int sum = 0;
                        for (byte a : PeerProcess.field) {
                            if (a == 1) {
                                sum += a;
                            }
                        }
                        Logger.downloadPiece(peerID,peerID,sum);
                        boolean combineFlag = true;
                        for (byte b : PeerProcess.field) {
                            if (b == 0 || b == 2) {
                                combineFlag = false;
                            }
                        }
                        if (combineFlag) {
                            Logger.downloadComplete();
                            FileParser.defragment(peerId);
                            SendMessage.sendMessage(MessageType.finish.getMessageTypeValue(), RunPeerMapping.get(PeerProcess.firstPeerID).getOutputStream());
                        }
                    }
                }
                else if(type==MessageType.bitfield.getMessageTypeValue()){
                    synchronized (MessageHandler.class) {
                        for (byte b : message) {
                            System.out.print(b + " ");
                        }
                        System.out.println();
                        byte[] peerField = new byte[PeerProcess.field.length];
                        System.arraycopy(message, 5, peerField, 0, PeerProcess.field.length);
                        PeerProcess.peersBitfields.put(peerID, peerField);
                        List<Integer> interestingPiecesList = findInterestingPieces(peerID);
                        if (interestingPiecesList != null) {
                            currentPeerInterestingTracker.put(peerID, interestingPiecesList);
                        }
                        if (currentPeerInterestingTracker.containsKey(peerID)) {
                            SendMessage.sendMessage(MessageType.interested.getMessageTypeValue(), out);
                            selectRandomPiece(peerID, out);
                        } else {
                            SendMessage.sendMessage(MessageType.not_interested.getMessageTypeValue(), out);
                        }
                    }
                }
                else if(type==MessageType.interested.getMessageTypeValue()){
                    Logger.receiveInterestedMessage(peerID);
                    if (!otherPeerInterestingTracker.containsKey(peerID)) {
                        otherPeerInterestingTracker.put(peerID, 0);
                    }
                    synchronized (MessageHandler.class) {
                        if (!firstInterestedFlag) {
                            Timer time = new Timer();
                            Date now = new Date();
                            unChokeIntervalScheduler = new IntervalScheduler("PreferredNeighbor", this);
                            optimalUnChokeIntervalScheduler = new IntervalScheduler("OptimisticNeighbor", this);
                            time.schedule(unChokeIntervalScheduler, now, 1000 * CommonConfigParser.getUnchokingInterval());
                            time.schedule(optimalUnChokeIntervalScheduler, now, 1000 * CommonConfigParser.getOptimisticUnchokingInterval());
                            firstInterestedFlag = true;
                        }
                    }
                }
                else if(type==MessageType.not_interested.getMessageTypeValue()){
                    Logger.receiveNotInterestedMessage(peerID);
                    otherPeerInterestingTracker.remove(peerID);
                }
                else if(type==MessageType.request.getMessageTypeValue()){
                    while (true) {
                        if (unchokedNeighbors.contains(peerID) || optimallyUnchokedNeighbor.contains(peerID)) {
                            break;
                        }
                    }
                    byte[] requestPieceID = new byte[4];
                    System.arraycopy(message, 5, requestPieceID, 0, 4);
                    sendPiece(requestPieceID, out);
                }
                else if(type==MessageType.have.getMessageTypeValue()){
                    synchronized (MessageHandler.class) {
                        byte[] havePieceID = new byte[4];
                        System.arraycopy(message, 5, havePieceID, 0, 4);
                        if (!PeerProcess.peersBitfields.containsKey(peerID)) {
                            PeerProcess.peersBitfields.put(peerID, new byte[FileParser.getTotalPieces()]);
                        }
                        peerBitField = PeerProcess.peersBitfields.get(peerID);
                        peerBitField[Utility.convertToInt(havePieceID)] = 1;
                        Logger.receiveHaveMessage(peerID,Utility.convertToInt(havePieceID));
                        interestOrNot(PeerProcess.peersBitfields.get(peerID), out, peerID);
                    }
                }
                else if(type==MessageType.choke.getMessageTypeValue())
                    Logger.choked(peerID);
                else if(type==MessageType.unchoke.getMessageTypeValue())
                    Logger.unchoked(peerID);
                else if(type==MessageType.finish.getMessageTypeValue())
                    PeerProcess.peersToProcess.remove(Integer.valueOf(peerID));
                else if(type==MessageType.all_finish.getMessageTypeValue()) {
                    Logger.applicationExit();
                    System.exit(0);
                }
                else
                    System.out.println("Message Type is not recognizable");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void selectPreferredNeighbors() {
        if (otherPeerInterestingTracker != null && otherPeerInterestingTracker.size() > 0) {
            boolean flag = true;
            for (int i = 0; i < PeerProcess.field.length; i++) {
                if (PeerProcess.field[i] == 0) {
                    flag = false;
                    break;
                }
            }
            if (!flag) {
                compareDownloadRate();
            } else {
                ArrayList<Integer> neighbors = new ArrayList<>(otherPeerInterestingTracker.keySet());
                ArrayList<Integer> arr=new ArrayList<>();
                int neighborsSize = neighbors.size();
                for (int i = 0; i < CommonConfigParser.getNumberOfPreferredNeighbours(); i++) {
                    if (i < neighborsSize) {
                        Random r = new Random();
                        int uc = r.nextInt(neighbors.size());
                        int ID = neighbors.get(uc);
                        arr.add(ID);
                        if (!unchokedNeighbors.contains(ID))
                            SendMessage.sendMessage(MessageType.unchoke.getMessageTypeValue(), RunPeerMapping.get(ID).getOutputStream());
                        neighbors.remove(uc);
                    }
                }
                for (Integer i : neighbors) {
                    SendMessage.sendMessage(MessageType.choke.getMessageTypeValue(), RunPeerMapping.get(i).getOutputStream());
                }
                if (!unchokedNeighbors.isEmpty()) {
                    unchokedNeighbors.clear();
                }

                Logger.changePreferredNeighbours(arr);

                for (int i1 : arr) {
                    unchokedNeighbors.add(i1);
                }
            }
            Utility.clearMap();
        }
    }

    public synchronized void compareDownloadRate() {
        HashMap<Integer, Double> downloadRates = new HashMap<>();
        for (Map.Entry<Integer, Integer> peer : otherPeerInterestingTracker.entrySet()) {
            double dr = (peer.getValue() * CommonConfigParser.getPieceSize() * 1.0) / CommonConfigParser.getUnchokingInterval();
            downloadRates.put(peer.getKey(), dr);
        }
        List<Map.Entry<Integer, Double>> list = new ArrayList<>(downloadRates.entrySet());
        list.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));
        for (Map.Entry<Integer, Double> mapping : list) {
            System.out.println(mapping.getKey() + ": " + mapping.getValue());
        }
        int[] arr = new int[CommonConfigParser.getNumberOfPreferredNeighbours()];
        int num = 0;
        List<Map.Entry<Integer, Double>> deleteUnChokeList = new LinkedList<>();
        for (Map.Entry<Integer, Double> mapping : list) {
            if (num < arr.length) {
                arr[num] = mapping.getKey();
                if (!unchokedNeighbors.contains(mapping.getKey()))
                    SendMessage.sendMessage(MessageType.unchoke.getMessageTypeValue(), RunPeerMapping.get(mapping.getKey()).getOutputStream());
                deleteUnChokeList.add(mapping);
                num++;
            } else {
                break;
            }
        }
        for (Map.Entry<Integer, Double> integerDoubleEntry : deleteUnChokeList) {
            list.remove(integerDoubleEntry);
        }
        if (list.size() > 0) {
            for (Map.Entry<Integer, Double> mapping : list) {
                SendMessage.sendMessage(MessageType.choke.getMessageTypeValue(), RunPeerMapping.get(mapping.getKey()).getOutputStream());
            }
        }
        if (!unchokedNeighbors.isEmpty()) {
            unchokedNeighbors.clear();
        }
        for (int i1 : arr) {
            unchokedNeighbors.add(i1);
        }
    }

    public synchronized void selectOptimisticPeer() {
        if (otherPeerInterestingTracker != null && otherPeerInterestingTracker.size() > 0) {
            List<Integer> optimalPeers = new ArrayList<>();
            for (int peerID : otherPeerInterestingTracker.keySet()) {
                if (!unchokedNeighbors.contains(peerID)) {
                    optimalPeers.add(peerID);
                }
            }
            if (optimalPeers.size() > 0) {
                PeerProcess.optimalPeer = optimalPeers.get(new Random().nextInt(optimalPeers.size()));
                SendMessage.sendMessage(MessageType.unchoke.getMessageTypeValue(), RunPeerMapping.get(PeerProcess.optimalPeer).getOutputStream());
                if (!optimallyUnchokedNeighbor.isEmpty()) {
                    optimallyUnchokedNeighbor.clear();
                }
                optimallyUnchokedNeighbor.add(PeerProcess.optimalPeer);
                Logger.changeOptimisticallyUnchokedNeighbour(PeerProcess.optimalPeer);
            }
        }
    }

    public static void sendBitField(ObjectOutputStream out) {
        if (!isEmptyBitField()) {
            return;
        }
        byte[] result = new byte[PeerProcess.field.length + 5];
        byte[] fieldLength = Utility.convertToByteArray(PeerProcess.field.length);
        byte[] type = Utility.convertToByteArray(MessageType.bitfield.getMessageTypeValue());

        for (int i = 0; i < result.length; i++) {
            if (i < 4) {
                result[i] = fieldLength[i];
            } else if (i == 4) {
                result[i] = type[3];
            } else {
                result[i] = PeerProcess.field[i - 5];
            }
        }
        SendMessage.pushMessage(result, out);
    }

    public static boolean isEmptyBitField() {
        boolean result = false;
        for (byte b : PeerProcess.field) {
            if (b == 1) {
                result = true;
                break;
            }
        }
        return result;
    }

}
