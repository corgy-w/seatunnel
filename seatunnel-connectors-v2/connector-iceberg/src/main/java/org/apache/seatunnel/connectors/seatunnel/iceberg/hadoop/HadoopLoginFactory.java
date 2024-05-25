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

package org.apache.seatunnel.connectors.seatunnel.iceberg.hadoop;

import org.apache.seatunnel.connectors.seatunnel.iceberg.exception.IcebergConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.iceberg.exception.IcebergConnectorException;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.PrivilegedExceptionAction;

import static org.apache.hadoop.security.authorize.ProxyServers.refresh;

@Slf4j
public class HadoopLoginFactory {

    /**
     * Objects returned after Kerberos authentication are authenticated by kerberos. If new objects
     * are created inside the returned objects that require Kerberos authentication when used, the
     * new objects will not be authenticated by kerberos *
     */
    public static <T> T loginWithKerberos(
            Configuration configuration,
            String krb5FilePath,
            String kerberosPrincipal,
            String kerberosKeytabPath,
            LoginFunction<T> action)
            throws IOException, InterruptedException {
        if (!configuration.get("hadoop.security.authentication").equals("kerberos")) {
            throw new IllegalArgumentException("hadoop.security.authentication must be kerberos");
        }
        // Use global lock to avoid multiple threads to execute setConfiguration at the same time
        synchronized (UserGroupInformation.class) {
            if (StringUtils.isNotEmpty(krb5FilePath)) {
                System.setProperty("java.security.krb5.conf", krb5FilePath);
            }
            // init configuration
            UserGroupInformation.setConfiguration(configuration);
            UserGroupInformation userGroupInformation =
                    UserGroupInformation.loginUserFromKeytabAndReturnUGI(
                            kerberosPrincipal, kerberosKeytabPath);
            T result =
                    userGroupInformation.doAs(
                            (PrivilegedExceptionAction<T>)
                                    () -> action.run(configuration, userGroupInformation));
            return result;
        }
    }

    /**
     * All operations under this classloader will use the user who has been authenticated using
     * Kerberos after authentication
     *
     * @param configuration
     * @param principal
     * @param keytabPath
     */
    public static void doKerberosAuthentication(
            Configuration configuration, String principal, String keytabPath) {
        if (StringUtils.isBlank(principal) || StringUtils.isBlank(keytabPath)) {
            log.warn(
                    "Principal [{}] or keytabPath [{}] is empty, it will skip kerberos authentication",
                    principal,
                    keytabPath);
        } else {
            configuration.set("hadoop.security.authentication", "kerberos");
            UserGroupInformation.setConfiguration(configuration);
            try {
                log.info(
                        "Start Kerberos authentication using principal {} and keytab {}",
                        principal,
                        keytabPath);
                refresh();
                UserGroupInformation.loginUserFromKeytab(principal, keytabPath);
                log.info("Kerberos authentication successful");
            } catch (IOException e) {
                throw new IcebergConnectorException(
                        IcebergConnectorErrorCode.KERBEROS_LOGIN_FAILED, e);
            }
        }
    }

    /** Login with remote user, and do the given action after login successfully. */
    public static <T> T loginWithRemoteUser(
            Configuration configuration, String remoteUser, LoginFunction<T> action)
            throws Exception {

        // Use global lock to avoid multiple threads to execute setConfiguration at the same time
        synchronized (UserGroupInformation.class) {
            // init configuration
            UserGroupInformation userGroupInformation =
                    UserGroupInformation.createRemoteUser(remoteUser);
            T result =
                    userGroupInformation.doAs(
                            (PrivilegedExceptionAction<T>)
                                    () -> action.run(configuration, userGroupInformation));
            return result;
        }
    }

    public interface LoginFunction<T> {

        T run(Configuration configuration, UserGroupInformation userGroupInformation)
                throws Exception;
    }
}
