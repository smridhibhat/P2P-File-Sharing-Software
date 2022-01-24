package edu.ufl.cise.process;

import edu.ufl.cise.logger.Logger;
import edu.ufl.cise.model.Peer;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class PeerListener extends Thread {
    private List<Peer> peerInfo;
    private int peerId;

    public PeerListener(int peerId, List<Peer> peerInfo) {
        this.peerInfo = peerInfo;
        this.peerId = peerId;
    }

    public void run() {
        int clientNum = 0;
        try {
            for (Peer peer: peerInfo) {
                if (peer.getPeerId() >= peerId) {
                    break;
                }
                Socket connection = new Socket(peer.getPeerHost(), peer.getPeerPort());
                new RunPeer(connection, peerId, peer).start();
                Logger.attemptConnection(peer.getPeerId());
                clientNum += 1;
            }
            clientNum = peerInfo.size() - clientNum - 1;
            int sPort = 0;
            for (Peer peer : peerInfo) {
                if (peer.getPeerId() == peerId) {
                    sPort = peer.getPeerPort();
                    break;
                }
            }
            ServerSocket listener = new ServerSocket(sPort);
            while (true) {
                if (clientNum == 0)
                    break;
                new RunPeer(listener.accept(), peerId).start();
                clientNum -= 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
