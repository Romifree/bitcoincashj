/*
 * Copyright 2013 Google Inc.
 * Copyright 2015 Andreas Schildbach
 * Copyright 2018 the bitcoincashj-cash developers
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
 *
 * This file has been modified by the bitcoincashj-cash developers for the bitcoincashj-cash project.
 * The original file was from the bitcoincashj project (https://github.com/bitcoincashj/bitcoincashj).
 */

package org.bitcoincashj.params;

import org.bitcoincashj.core.Sha256Hash;
import org.bitcoincashj.core.Utils;

import java.math.BigInteger;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the main production network on which people trade goods and services.
 */
public class MainNetParams extends AbstractBitcoinNetParams {
    public static final int MAINNET_MAJORITY_WINDOW = 1000;
    public static final int MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED = 950;
    public static final int MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 750;

    public MainNetParams() {
        super();
        targetTimespan = TARGET_TIMESPAN;
        maxTarget = Utils.decodeCompactBits(0x1d00ffffL);
        dumpedPrivateKeyHeader = 128;
        addressHeader = 0;
        p2shHeader = 5;
        acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
        port = 8333;
        packetMagic = 0xe3e1f3e8L;
        defaultPeerCount = 8;
        bip32HeaderP2PKHpub = 0x0488b21e; // The 4 byte header that serializes in base58 to "xpub".
        bip32HeaderP2PKHpriv = 0x0488ade4; // The 4 byte header that serializes in base58 to "xprv"

        majorityEnforceBlockUpgrade = MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = MAINNET_MAJORITY_WINDOW;

        genesisBlock.setDifficultyTarget(0x1d00ffffL);
        genesisBlock.setTime(1231006505L);
        genesisBlock.setNonce(2083236893);
        id = ID_MAINNET;
        spendableCoinbaseDepth = 100;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"),
                genesisHash);

        checkpoints.put(91722, Sha256Hash.wrap("00000000000271a2dc26e7667f8419f2e15416dc6955e5a6c6cdf3f2574dd08e"));
        checkpoints.put(91812, Sha256Hash.wrap("00000000000af0aed4792b1acee3d966af36cf5def14935db8de83d6f9306f2f"));
        checkpoints.put(91842, Sha256Hash.wrap("00000000000a4d0a398161ffc163c503763b1f4360639393e0e4c8e300e0caec"));
        checkpoints.put(91880, Sha256Hash.wrap("00000000000743f190a18c5577a3c2d2a1f610ae9601ac046a38084ccb7cd721"));
        checkpoints.put(200000, Sha256Hash.wrap("000000000000034a7dedef4a161fa058a2d67a173a90155f3a2fe6fc132e0ebf"));
        checkpoints.put(478559, Sha256Hash.wrap("000000000000000000651ef99cb9fcbe0dadde1d424bd9f15ff20136191a5eec")); //August 1, 2017
        checkpoints.put(504031, Sha256Hash.wrap("0000000000000000011ebf65b60d0a3de80b8175be709d653b4c1a1beeb6ab9c")); //November 13, 2017
        checkpoints.put(530359, Sha256Hash.wrap("0000000000000000011ada8bd08f46074f44a8f155396f43e38acf9501c49103")); //May 15, 2018
        checkpoints.put(556767, Sha256Hash.wrap("0000000000000000004626ff6e3b936941d341c5932ece4357eeccac44e6d56c")); //November 15, 2018
        checkpoints.put(582680, Sha256Hash.wrap("000000000000000001b4b8e36aec7d4f9671a47872cb9a74dc16ca398c7dcc18")); //May 15, 2019
        checkpoints.put(609136, Sha256Hash.wrap("000000000000000000b48bb207faac5ac655c313e41ac909322eaa694f5bc5b1")); //November 15, 2019
        checkpoints.put(635259, Sha256Hash.wrap("00000000000000000033dfef1fc2d6a5d5520b078c55193a9bf498c5b27530f7")); //May 15, 2020
        checkpoints.put(661648, Sha256Hash.wrap("0000000000000000029e471c41818d24b8b74c911071c4ef0b4a0509f9b5a8ce")); //November 15, 2020

        dnsSeeds = new String[]{
                "seed-bch.bitcoinforks.org",
                "seed.bchd.cash",
                "btccash-seeder.bitcoinunlimited.info",
                "seed.flowee.cash",
                "bchseed.c3-soft.com",
                "dnsseed.electroncash.de",
                "seed.bch.loping.net"
        };

        httpSeeds = null;
        addrSeeds = new String[] {
                "2.37.183.149",
                "3.135.187.10",
                "3.210.96.9",
                "5.103.137.146",
                "5.189.153.133",
                "5.39.122.17",
                "5.44.97.110",
                "5.56.40.1",
                "5.9.29.54",
                "13.125.221.9",
                "13.230.85.140",
                "13.90.141.13",
                "14.161.3.136",
                "18.156.62.142",
                "18.223.164.123",
                "23.152.0.196",
                "24.192.60.220",
                "31.220.56.195",
                "31.25.241.224",
                "34.225.107.142",
                "34.243.19.193",
                "34.244.188.218",
                "34.92.81.124",
                "35.193.218.253",
                "35.208.109.181",
                "35.209.3.16",
                "35.240.221.90",
                "37.123.153.205",
                "37.221.209.222",
                "38.143.66.118",
                "38.87.54.163",
                "39.105.126.134",
                "39.97.177.55",
                "45.151.125.24",
                "45.32.79.103",
                "45.77.12.99",
                "45.89.16.17",
                "46.103.251.160",
                "46.165.221.209",
                "46.166.142.52",
                "47.221.138.240",
                "47.254.128.149",
                "47.62.61.88",
                "47.94.39.87",
                "50.39.245.26",
                "51.15.106.21",
                "52.168.133.140",
                "52.203.66.52",
                "52.246.166.192",
                "52.71.89.175",
                "52.82.19.10",
                "54.152.182.25",
                "59.110.4.119",
                "60.249.215.224",
                "61.124.151.159",
                "61.18.151.142",
                "62.210.110.181",
                "62.42.138.162",
                "66.151.242.154",
                "66.187.65.6",
                "66.42.74.250",
                "66.96.199.249",
                "67.239.3.146",
                "68.183.76.215",
                "70.67.146.45",
                "71.139.127.73",
                "71.67.65.230",
                "71.84.210.207",
                "71.93.32.247",
                "72.183.35.18",
                "72.196.128.193",
                "76.84.79.211",
                "77.120.122.114",
                "78.34.126.50",
                "78.97.206.149",
                "79.55.30.190",
                "80.179.226.48",
                "80.195.243.45",
                "81.169.251.124",
                "82.117.166.77",
                "82.200.205.30",
                "82.221.107.216",
                "83.221.211.116",
                "84.213.188.64",
                "84.44.234.220",
                "85.10.201.15",
                "85.148.14.160",
                "85.209.240.92",
                "85.64.35.122",
                "87.144.183.22",
                "88.198.33.214",
                "88.208.3.195",
                "89.132.239.57",
                "89.160.113.174",
                "89.163.148.121",
                "89.179.247.236",
                "89.40.7.97",
                "89.7.25.196",
                "91.121.163.195",
                "91.197.44.144",
                "92.42.104.13",
                "93.104.208.119",
                "93.238.101.168",
                "93.90.193.195",
                "94.130.16.96",
                "94.19.73.106",
                "94.244.97.244",
                "94.247.134.186",
                "95.216.12.173",
                "95.63.229.41",
                "96.126.117.5",
                "100.11.132.241",
                "101.92.39.116",
                "102.68.86.49",
                "103.230.185.4",
                "104.154.189.24",
                "104.196.255.91",
                "104.238.131.116",
                "106.167.197.33",
                "107.172.9.209",
                "107.174.70.250",
                "107.191.117.175",
                "108.173.48.152",
                "109.145.47.158",
                "109.195.150.106",
                "111.90.151.48",
                "117.20.65.54",
                "119.3.248.108",
                "121.199.59.128",
                "121.254.175.27",
                "134.209.81.251",
                "136.144.215.219",
                "138.201.222.217",
                "138.68.249.47",
                "139.162.21.80",
                "142.68.82.226",
                "144.76.44.57",
                "149.210.238.31",
                "152.136.23.15",
                "155.138.192.228",
                "158.177.186.55",
                "162.220.47.150",
                "162.242.168.55",
                "162.62.21.182",
                "167.172.41.140",
                "168.235.72.196",
                "170.52.71.98",
                "172.104.34.214",
                "172.249.77.148",
                "172.93.133.119",
                "173.208.203.82",
                "173.212.193.23",
                "173.79.129.114",
                "173.94.50.107",
                "174.141.196.204",
                "175.176.142.110",
                "176.223.137.37",
                "177.38.215.9",
                "178.218.111.250",
                "178.59.34.4",
                "178.63.88.4",
                "179.218.90.235",
                "183.111.234.221",
                "185.107.80.1",
                "185.25.60.199",
                "185.26.28.171",
                "185.50.37.171",
                "185.81.164.38",
                "188.20.184.122",
                "188.226.150.166",
                "188.40.93.205",
                "188.66.26.61",
                "189.211.146.140",
                "190.2.130.27",
                "192.187.121.43",
                "192.30.89.142",
                "192.81.220.210",
                "193.135.10.215",
                "193.138.218.67",
                "193.169.244.189",
                "193.29.187.78",
                "194.14.246.205",
                "194.149.90.19",
                "194.99.21.135",
                "195.122.150.173",
                "195.154.168.129",
                "195.181.240.229",
                "198.204.229.34",
                "198.27.83.210",
                "198.50.179.86",
                "203.173.116.34",
                "206.124.149.67",
                "209.160.33.233",
                "210.242.27.237",
                "211.32.117.106",
                "211.43.8.168",
                "211.63.212.26",
                "212.32.230.219",
                "213.202.212.78",
                "213.232.124.121",
                "216.108.236.180",
                "217.117.29.71",
                "217.160.169.226",
                "217.217.55.14",
                "217.31.166.123",
                "[2001:470:8f9e:944:5054:ff:fed7:c164]",
                "[2001:67c:2b8c:1:225:90ff:fe54:5306]",
                "[2604:180:f4::61]",
                "[2604:e880:0:15:ec4:7aff:fe4a:40e1]",
                "[2a00:1768:2001:27::a1cb]",
                "[2a00:7c80:0:10b::4ad9]",
                "[2a01:5f0:beef:5:0:3:0:1]",
                "[2a01:7a0:2:137a::9]",
                "[2a02:4780:1:1::1:884a]",
                "[2a02:7b40:b0df:8925::1]",
                "[2a02:a311:8143:8c00::4]",
                "[2a03:1b20:1:f410:40::acef]",
                "[2a04:2180:1:11:f000::16]",
                "[2a0a:51c0:0:136::4]",
                "[2c0f:f598:6::54f:c0fe]"
        };

        // Aug, 1 hard fork
        uahfHeight = 478559;
        // Nov, 13 hard fork
        daaUpdateHeight = 504031;
        cashAddrPrefix = "bitcoincash";
        simpleledgerPrefix = "simpleledger";

        asertReferenceBlockBits = 0x1804dafe;
        asertReferenceBlockHeight = BigInteger.valueOf(661647L);
        asertReferenceBlockAncestorTime = BigInteger.valueOf(1605447844L);
        asertUpdateTime = 1605441600L;
        asertHalfLife = 2L * 24L * 60L * 60L;
        allowMinDifficultyBlocks = false;
        maxBlockSize = 32 * 1000 * 1000;
        maxBlockSigops = maxBlockSize / 50;
    }

    private static MainNetParams instance;

    public static synchronized MainNetParams get() {
        if (instance == null) {
            instance = new MainNetParams();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_MAINNET;
    }
}
