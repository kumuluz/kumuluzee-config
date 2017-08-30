/*
 *  Copyright (c) 2014-2017 Kumuluz and/or its affiliates
 *  and other contributors as indicated by the @author tags and
 *  the contributor list.
 *
 *  Licensed under the MIT License (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://opensource.org/licenses/MIT
 *
 *  The software is provided "AS IS", WITHOUT WARRANTY OF ANY KIND, express or
 *  implied, including but not limited to the warranties of merchantability,
 *  fitness for a particular purpose and noninfringement. in no event shall the
 *  authors or copyright holders be liable for any claim, damages or other
 *  liability, whether in an action of contract, tort or otherwise, arising from,
 *  out of or in connection with the software or the use or other dealings in the
 *  software. See the License for the specific language governing permissions and
 *  limitations under the License.
*/

package com.kumuluz.ee.config.etcd;

import com.kumuluz.ee.common.config.EeConfig;
import com.kumuluz.ee.config.utils.InitializationUtils;
import com.kumuluz.ee.config.utils.ParseUtils;
import com.kumuluz.ee.configuration.ConfigurationSource;
import com.kumuluz.ee.configuration.utils.ConfigurationDispatcher;
import com.kumuluz.ee.configuration.utils.ConfigurationUtil;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import mousio.client.retry.RetryOnce;
import mousio.client.retry.RetryWithExponentialBackOff;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.EtcdSecurityContext;
import mousio.etcd4j.promises.EtcdResponsePromise;
import mousio.etcd4j.responses.EtcdAuthenticationException;
import mousio.etcd4j.responses.EtcdErrorCode;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;

import javax.net.ssl.SSLException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Util class for getting and setting configuration properties for etcd API v2.
 *
 * @author Jan Meznarič
 */
public class Etcd2ConfigurationSource implements ConfigurationSource {

    private static final Logger log = Logger.getLogger(Etcd2ConfigurationSource.class.getName());

    private EtcdClient etcd;
    private ConfigurationDispatcher configurationDispatcher;
    private String namespace;
    private int startRetryDelay;
    private int maxRetryDelay;

    private EeConfig eeConfig;

    public Etcd2ConfigurationSource(EeConfig eeConfig) {
        this.eeConfig = eeConfig;
    }

    @Override
    public void init(ConfigurationDispatcher configurationDispatcher) {

        this.configurationDispatcher = configurationDispatcher;

        ConfigurationUtil configurationUtil = ConfigurationUtil.getInstance();
        // get namespace
        this.namespace = InitializationUtils.getNamespace(eeConfig, configurationUtil, "etcd");
        log.info("Using namespace: " + this.namespace);

        // get user credentials
        String etcdUsername = configurationUtil.get("kumuluzee.config.etcd.username").orElse(null);
        String etcdPassword = configurationUtil.get("kumuluzee.config.etcd.password").orElse(null);

        // get CA certificate
        String cert = configurationUtil.get("kumuluzee.config.etcd.ca").orElse(null);
        SslContext sslContext = null;
        if (cert != null) {

            cert = cert.replaceAll("\\s+", "").replace("-----BEGINCERTIFICATE-----", "")
                    .replace("-----ENDCERTIFICATE-----", "");

            byte[] decoded = Base64.getDecoder().decode(cert);

            try {
                X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(decoded));

                sslContext = SslContextBuilder.forClient().trustManager(certificate).build();

            } catch (CertificateException e) {
                log.severe("Certificate exception: " + e.toString());
            } catch (SSLException e) {
                log.severe("SSL exception: " + e.toString());
            }

        }

        // initialize security context
        EtcdSecurityContext etcdSecurityContext = null;
        if (etcdUsername != null && !etcdUsername.isEmpty() && etcdPassword != null && !etcdPassword.isEmpty()) {
            if (sslContext != null) {
                etcdSecurityContext = new EtcdSecurityContext(sslContext, etcdUsername, etcdPassword);
            } else {
                etcdSecurityContext = new EtcdSecurityContext(etcdUsername, etcdPassword);
            }
        } else if (sslContext != null) {
            etcdSecurityContext = new EtcdSecurityContext(sslContext);
        }

        // get etcd host names
        String etcdUrls = configurationUtil.get("kumuluzee.config.etcd.hosts").orElse(null);
        if (etcdUrls != null && !etcdUrls.isEmpty()) {

            String[] splittedEtcdUrls = etcdUrls.split(",");
            URI[] etcdHosts = new URI[splittedEtcdUrls.length];
            for (int i = 0; i < etcdHosts.length; i++) {
                etcdHosts[0] = URI.create(splittedEtcdUrls[0]);
            }

            if (etcdHosts.length % 2 == 0) {
                log.warning("Using an odd number of etcd hosts is recommended. See etcd documentation.");
            }

            if (etcdSecurityContext != null) {

                etcd = new EtcdClient(etcdSecurityContext, etcdHosts);

            } else {

                etcd = new EtcdClient(etcdHosts);

            }

            etcd.setRetryHandler(new RetryOnce(0));

            // get retry dellays
            startRetryDelay = InitializationUtils.getStartRetryDelayMs(configurationUtil, "etcd");
            maxRetryDelay = InitializationUtils.getMaxRetryDelayMs(configurationUtil, "etcd");

            log.info("etcd2 configuration source successfully initialised.");

        } else {
            log.severe("No etcd server hosts provided. Specify hosts with configuration key" +
                    "kumuluzee.config.etcd.hosts in format " +
                    "http://192.168.99.100:2379,http://192.168.99.101:2379,http://192.168.99.102:2379");
        }

    }

    @Override
    public Optional<String> get(String key) {

        key = namespace + "/" + parseKeyNameForEtcd(key);

        String value = null;

        if (etcd != null) {
            try {
                value = etcd.get(key).send().get().getNode().getValue();
            } catch (IOException e) {
                log.severe("IO Exception. Cannot read given key: " + e + " Key: " + key);
            } catch (EtcdException e) {
                log.info("etcd: " + e + " Key: " + key);
            } catch (EtcdAuthenticationException e) {
                log.severe("Etcd authentication exception. Cannot read given key: " + e + " Key: " + key);
            } catch (TimeoutException e) {
                log.severe("Timeout exception. Cannot read given key time: " + e + " Key: " + key);
            }

            if (value != null) {
                return Optional.of(value);
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Boolean> getBoolean(String key) {
        return ParseUtils.parseOptionalStringToOptionalBoolean(get(key));
    }

    @Override
    public Optional<Integer> getInteger(String key) {
        return ParseUtils.parseOptionalStringToOptionalInteger(get(key));
    }

    @Override
    public Optional<Long> getLong(String key) {
        return ParseUtils.parseOptionalStringToOptionalLong(get(key));
    }

    @Override
    public Optional<Double> getDouble(String key) {
        return ParseUtils.parseOptionalStringToOptionalDouble(get(key));
    }

    @Override
    public Optional<Float> getFloat(String key) {
        return ParseUtils.parseOptionalStringToOptionalFloat(get(key));
    }


    @Override
    public Optional<Integer> getListSize(String key) {
        return Optional.empty();
    }

    @Override
    public Optional<List<String>> getMapKeys(String key) {

        key = namespace + "." + key;

        if (etcd != null) {

            List<EtcdKeysResponse.EtcdNode> nodes = null;
            try {
                nodes = etcd.getDir(parseKeyNameForEtcd(key)).send().get().getNode().getNodes();
            } catch (IOException e) {
                log.severe("IO Exception. Cannot read given key: " + e + " Key: " + key);
            } catch (EtcdException e) {
                log.info("etcd: " + e + " Key: " + key);
            } catch (EtcdAuthenticationException e) {
                log.severe("Etcd authentication exception. Cannot read given key: " + e + " Key: " + key);
            } catch (TimeoutException e) {
                log.severe("Timeout exception. Cannot read given key time: " + e + " Key: " + key);
            }

            Set<String> mapKeys = new HashSet<>();
            if (nodes != null) {
                for (EtcdKeysResponse.EtcdNode node : nodes) {
                    String[] splittedKey = node.getKey().split("/");
                    mapKeys.add(splittedKey[splittedKey.length - 1]);
                }
            }

            if (mapKeys.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(new ArrayList<>(mapKeys));
            }
        } else {
            return Optional.empty();
        }
    }

    public void watch(String key) {

        String fullKey = namespace + "/" + parseKeyNameForEtcd(key);

        if (etcd != null) {
            log.info("Initializing watch for key: " + fullKey);
            try {
                EtcdResponsePromise<EtcdKeysResponse> responsePromise = etcd.getDir(fullKey).recursive()
                        .setRetryPolicy(new RetryWithExponentialBackOff(startRetryDelay, -1, maxRetryDelay))
                        .waitForChange().send();

                responsePromise.addListener(promise -> {

                    Throwable t = promise.getException();
                    if (t instanceof EtcdException) {
                        if (((EtcdException) t).isErrorCode(EtcdErrorCode.NodeExist)) {
                            log.severe("Exception in etcd promise: " + ((EtcdException) t).etcdMessage);
                        }
                    }

                    EtcdKeysResponse response = null;
                    try {
                        response = promise.get();
                        if (response != null) {
                            String newValue = response.node.value;
                            String newKey = response.node.key;
                            log.info("Value changed. Key: " + parseKeyNameFromEtcd(newKey) + " New value: " + newValue);

                            if (configurationDispatcher != null) {
                                if (newValue != null) {
                                    configurationDispatcher.notifyChange(parseKeyNameFromEtcd(newKey), newValue);
                                } else {
                                    ConfigurationUtil configurationUtil = ConfigurationUtil.getInstance();
                                    String fallbackConfig = configurationUtil.get(key).orElse(null);
                                    if (fallbackConfig != null) {
                                        configurationDispatcher.notifyChange(key, fallbackConfig);
                                    }
                                }
                            }
                        }

                        watch(key);

                    } catch (Exception e) {
                        log.severe("Exception retrieving key value in watch. Exception: " + e.toString());
                    }
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void set(String key, String value) {

        if (etcd != null) {
            try {
                EtcdKeysResponse response = etcd.put(parseKeyNameForEtcd(key), value).send().get();

                if (!response.getNode().getValue().equals(value)) {
                    log.severe("Error: value was not set.");
                }

            } catch (IOException | EtcdException | EtcdAuthenticationException | TimeoutException e) {
                log.severe("Cannot set key: " + e);
            }
        }
    }

    @Override
    public void set(String key, Boolean value) {
        set(key, value.toString());
    }

    @Override
    public void set(String key, Integer value) {
        set(key, value.toString());
    }

    @Override
    public void set(String key, Double value) {
        set(key, value.toString());
    }

    @Override
    public void set(String key, Float value) {
        set(key, value.toString());
    }

    private String parseKeyNameForEtcd(String key) {

        key = key.replace("[", ".[");
        String[] splittedKey = key.split("\\.");

        StringBuilder parsedKey = new StringBuilder();
        for (String s : splittedKey) {
            try {
                parsedKey.append(URLEncoder.encode(s, "UTF-8")).append("/");
            } catch (UnsupportedEncodingException e) {
                log.severe("UTF-8 encoding not supported.");
            }
        }

        return parsedKey.deleteCharAt(parsedKey.length() - 1).toString();

    }

    private String parseKeyNameFromEtcd(String key) {
        return key.substring(this.namespace.length() + 2).replace("/", ".");
    }

    public String getNamespace() {
        return this.namespace;
    }
}
