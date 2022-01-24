package edu.ufl.cise.message;

import java.util.LinkedList;
import java.util.List;

public class Utility {

    public static byte[] convertToByteArray(int i) {
        byte[] result = new byte[4];
        result[0] = (byte) ((i >> 24) & 0xFF);
        result[1] = (byte) ((i >> 16) & 0xFF);
        result[2] = (byte) ((i >> 8) & 0xFF);
        result[3] = (byte) (i & 0xFF);
        return result;
    }

    public static int convertToInt(byte[] bytes) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (3 - i) * 8;
            value += (bytes[i] & 0xFF) << shift;
        }
        return value;
    }

    public static void deleteMap(int pieceIDInt) {
        synchronized (MessageHandler.class) {
            List<Integer> nullList = new LinkedList<>();
            for (Integer ID : MessageHandler.currentPeerInterestingTracker.keySet()) {
                if (MessageHandler.currentPeerInterestingTracker.get(ID).contains(pieceIDInt)) {
                    MessageHandler.currentPeerInterestingTracker.get(ID).remove(Integer.valueOf(pieceIDInt));
                }
                if (MessageHandler.currentPeerInterestingTracker.get(ID).size() == 0) {
                    nullList.add(ID);
                }
            }
            for (Integer ID : nullList) {
                MessageHandler.currentPeerInterestingTracker.remove(ID);
            }
        }
    }

    public static synchronized void clearMap() {
        for (Integer id : MessageHandler.otherPeerInterestingTracker.keySet()) {
            MessageHandler.otherPeerInterestingTracker.put(id, 0);
        }
    }
}
