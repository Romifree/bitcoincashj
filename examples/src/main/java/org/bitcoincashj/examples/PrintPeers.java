/*
 * Copyright 2011 John Sample.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoincashj.examples;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.bitcoincashj.core.NetworkParameters;
import org.bitcoincashj.core.Peer;
import org.bitcoincashj.core.PeerAddress;
import org.bitcoincashj.core.VersionMessage;
import org.bitcoincashj.core.listeners.PeerConnectedEventListener;
import org.bitcoincashj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoincashj.net.NioClientManager;
import org.bitcoincashj.net.discovery.DnsDiscovery;
import org.bitcoincashj.net.discovery.PeerDiscoveryException;
import org.bitcoincashj.params.MainNetParams;
import org.bitcoincashj.utils.BriefLogFormatter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Prints a list of IP addresses obtained from DNS.
 */
public class PrintPeers {
    private static List<InetSocketAddress> dnsPeers;

    private static void printElapsed(long start) {
        long now = System.currentTimeMillis();
        System.out.println(String.format("Took %.2f seconds", (now - start) / 1000.0));
    }

    private static void printPeers(List<InetSocketAddress> addresses) {
        for (InetSocketAddress address : addresses) {
            String hostAddress = address.getAddress().getHostAddress();
            System.out.println(String.format("%s:%d", hostAddress, address.getPort()));
        }
    }

    private static void printDNS() throws PeerDiscoveryException {
        long start = System.currentTimeMillis();
        DnsDiscovery dns = new DnsDiscovery(MainNetParams.get());
        dnsPeers = dns.getPeers(0, 10, TimeUnit.SECONDS);
        printPeers(dnsPeers);
        printElapsed(start);
    }

    public static void main(String[] args) throws Exception {
        BriefLogFormatter.init();
        System.out.println("=== DNS ===");
        printDNS();
        System.out.println("=== Version/chain heights ===");

        ArrayList<InetAddress> addrs = new ArrayList<>();
        for (InetSocketAddress peer : dnsPeers) addrs.add(peer.getAddress());
        System.out.println("Scanning " + addrs.size() + " peers:");

        final NetworkParameters params = MainNetParams.get();
        final Object lock = new Object();
        final long[] bestHeight = new long[1];

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        NioClientManager clientManager = new NioClientManager();
        for (final InetAddress addr : addrs) {
            InetSocketAddress address = new InetSocketAddress(addr, params.getPort());
            final Peer peer = new Peer(params, new VersionMessage(params, 0),
                    new PeerAddress(params, address), null);
            final SettableFuture<Void> future = SettableFuture.create();
            // Once the connection has completed version handshaking ...
            peer.addConnectedEventListener((p, peerCount) -> {
                // Check the chain height it claims to have.
                VersionMessage ver = peer.getPeerVersionMessage();
                long nodeHeight = ver.bestHeight;
                synchronized (lock) {
                    long diff = bestHeight[0] - nodeHeight;
                    if (diff > 0) {
                        System.out.println("Node is behind by " + diff + " blocks: " + addr);
                    } else if (diff == 0) {
                        System.out.println("Node " + addr + " has " + nodeHeight + " blocks");
                        bestHeight[0] = nodeHeight;
                    } else if (diff < 0) {
                        System.out.println("Node is ahead by " + Math.abs(diff) + " blocks: " + addr);
                        bestHeight[0] = nodeHeight;
                    }
                }
                // Now finish the future and close the connection
                future.set(null);
                peer.close();
            });
            peer.addDisconnectedEventListener((p, peerCount) -> {
                if (!future.isDone())
                    System.out.println("Failed to talk to " + addr);
                future.set(null);
            });
            clientManager.openConnection(address, peer);
            futures.add(future);
        }
        // Wait for every tried connection to finish.
        Futures.successfulAsList(futures).get();
    }
}
