/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.engine.server.utils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.conn.util.InetAddressUtils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/** NetUtils */
@Slf4j
public class NetUtils {

    private NetUtils() {
        throw new UnsupportedOperationException("Construct NetUtils");
    }

    private static final String LOCAL_IP = "127.0.0.1";

    public static List<String> getAllIp() {
        List<NetworkInterface> suitableNetworkInterface = findSuitableNetworkInterface();
        List<InetAddress> suitableInetAddress = findSuitableInetAddress(suitableNetworkInterface);
        List<String> ipList = new ArrayList<>();
        suitableInetAddress.forEach(
                inetAddress -> {
                    ipList.add(inetAddress.getHostAddress());
                });
        if (!ipList.contains(LOCAL_IP)) {
            ipList.add(LOCAL_IP);
        }
        return ipList;
    }

    private static InetAddress normalizeV6Address(Inet6Address address) {
        String addr = address.getHostAddress();
        int i = addr.lastIndexOf('%');
        if (i > 0) {
            try {
                return InetAddress.getByName(addr.substring(0, i) + '%' + address.getScopeId());
            } catch (UnknownHostException e) {
                log.debug("Unknown IPV6 address: ", e);
            }
        }
        return address;
    }

    protected static boolean isValidV4Address(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        String name = address.getHostAddress();
        return (name != null
                && InetAddressUtils.isIPv4Address(name)
                && !address.isAnyLocalAddress()
                && !address.isLoopbackAddress());
    }

    protected static boolean isValidV6Address(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        String name = address.getHostAddress();
        return (name != null
                && InetAddressUtils.isIPv6Address(name)
                && !address.isAnyLocalAddress()
                && !address.isLoopbackAddress());
    }

    /**
     * Check if an ipv6 address
     *
     * @return true if it is reachable
     */
    private static boolean isPreferIPV6Address() {
        return Boolean.getBoolean("java.net.preferIPv6Addresses");
    }

    private static boolean isPreferIPV4Address() {
        return Boolean.getBoolean("java.net.preferIPv4Addresses");
    }

    /** Get the suitable {@link NetworkInterface} */
    private static List<NetworkInterface> findSuitableNetworkInterface() {

        // Find all network interfaces
        List<NetworkInterface> networkInterfaces = Collections.emptyList();
        try {
            networkInterfaces = getAllNetworkInterfaces();
        } catch (SocketException e) {
            log.warn("ValidNetworkInterfaces exception", e);
        }

        // Filter the loopback/virtual/ network interfaces
        List<NetworkInterface> validNetworkInterfaces =
                networkInterfaces.stream()
                        .filter(
                                networkInterface -> {
                                    try {
                                        return !(networkInterface == null
                                                || networkInterface.isLoopback()
                                                || networkInterface.isVirtual()
                                                || !networkInterface.isUp());
                                    } catch (SocketException e) {
                                        log.warn("ValidNetworkInterfaces exception", e);
                                        return false;
                                    }
                                })
                        .collect(Collectors.toList());
        return validNetworkInterfaces;
    }

    /** Get the suitable {@link InetAddress} */
    private static List<InetAddress> findSuitableInetAddress(
            List<NetworkInterface> networkInterfaces) {
        if (CollectionUtils.isEmpty(networkInterfaces)) {
            return Collections.emptyList();
        }
        List<InetAddress> allInetAddresses = new LinkedList<>();
        for (NetworkInterface networkInterface : networkInterfaces) {
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                allInetAddresses.add(addresses.nextElement());
            }
        }
        // Get prefer addresses
        List<InetAddress> preferInetAddress = new ArrayList<>();
        if (!isPreferIPV6Address() && !isPreferIPV4Address()) {
            // no prefer, will use all addresses
            preferInetAddress.addAll(getIpv4Addresses(allInetAddresses));
            preferInetAddress.addAll(getIpv6Addresses(allInetAddresses));
        }
        if (isPreferIPV4Address()) {
            preferInetAddress.addAll(getIpv4Addresses(allInetAddresses));
        }
        if (isPreferIPV6Address()) {
            preferInetAddress.addAll(getIpv6Addresses(allInetAddresses));
        }
        // Get reachable addresses
        return preferInetAddress.stream()
                .filter(
                        inetAddress -> {
                            try {
                                return inetAddress.isReachable(100);
                            } catch (IOException e) {
                                log.warn("InetAddress isReachable exception", e);
                                return false;
                            }
                        })
                .collect(Collectors.toList());
    }

    private static List<InetAddress> getIpv4Addresses(List<InetAddress> allInetAddresses) {
        if (CollectionUtils.isEmpty(allInetAddresses)) {
            return Collections.emptyList();
        }
        List<InetAddress> validIpv4Addresses = new ArrayList<>();
        for (InetAddress inetAddress : allInetAddresses) {
            if (isValidV4Address(inetAddress)) {
                validIpv4Addresses.add(inetAddress);
            }
        }
        return validIpv4Addresses;
    }

    private static List<InetAddress> getIpv6Addresses(List<InetAddress> allInetAddresses) {
        if (CollectionUtils.isEmpty(allInetAddresses)) {
            return Collections.emptyList();
        }
        List<InetAddress> validIpv6Addresses = new ArrayList<>();
        for (InetAddress inetAddress : allInetAddresses) {
            if (!isValidV6Address(inetAddress)) {
                continue;
            }
            Inet6Address v6Address = (Inet6Address) inetAddress;
            InetAddress normalizedV6Address = normalizeV6Address(v6Address);
            validIpv6Addresses.add(normalizedV6Address);
        }
        return validIpv6Addresses;
    }

    private static List<NetworkInterface> getAllNetworkInterfaces() throws SocketException {
        List<NetworkInterface> validNetworkInterfaces = new LinkedList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            log.info("Found NetworkInterface: {}", networkInterface);
            validNetworkInterfaces.add(networkInterface);
        }
        return validNetworkInterfaces;
    }

    private static List<NetworkInterface> findInnerAddressNetWorkInterface(
            List<NetworkInterface> validNetworkInterfaces) {
        if (CollectionUtils.isEmpty(validNetworkInterfaces)) {
            return Collections.emptyList();
        }

        List<NetworkInterface> innerNetworkInterfaces = new ArrayList<>();
        for (NetworkInterface ni : validNetworkInterfaces) {
            Enumeration<InetAddress> address = ni.getInetAddresses();
            while (address.hasMoreElements()) {
                InetAddress ip = address.nextElement();
                if (ip.isSiteLocalAddress() && !ip.isLoopbackAddress()) {
                    innerNetworkInterfaces.add(ni);
                }
            }
        }
        return innerNetworkInterfaces;
    }
}
