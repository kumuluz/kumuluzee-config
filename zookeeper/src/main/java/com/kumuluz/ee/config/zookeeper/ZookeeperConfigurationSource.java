/*
 *  Copyright (c) 2014-2020 Kumuluz and/or its affiliates
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
package com.kumuluz.ee.config.zookeeper;

import com.kumuluz.ee.common.config.EeConfig;
import com.kumuluz.ee.config.utils.InitializationUtils;
import com.kumuluz.ee.config.utils.ParseUtils;
import com.kumuluz.ee.configuration.ConfigurationSource;
import com.kumuluz.ee.configuration.utils.ConfigurationDecoderUtils;
import com.kumuluz.ee.configuration.utils.ConfigurationDispatcher;
import com.kumuluz.ee.configuration.utils.ConfigurationUtil;
import org.apache.zookeeper.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Util class for getting and setting configuration properties for Zookeeper API.
 *
 * @author Miha Jamsek
 * @since 1.3.0
 */
public class ZookeeperConfigurationSource implements ConfigurationSource {
    
    private static final Logger log = Logger.getLogger(ZookeeperConfigurationSource.class.getName());
    
    private String namespace;
    
    private ConfigurationDispatcher configurationDispatcher;
    private EeConfig eeConfig;
    private ZooKeeper zooKeeper;
    private CountDownLatch connectionSignal = new CountDownLatch(0);
    
    public ZookeeperConfigurationSource(EeConfig eeConfig) {
        this.eeConfig = eeConfig;
    }
    
    @Override
    public void init(ConfigurationDispatcher configurationDispatcher) {
        this.configurationDispatcher = configurationDispatcher;
        ConfigurationUtil configurationUtil = ConfigurationUtil.getInstance();
        
        // get namespace
        this.namespace = InitializationUtils.getNamespace(eeConfig, configurationUtil, "zookeeper");
        log.log(Level.INFO, "Using namespace: {0}", this.namespace);
        
        // get hosts
        String zookeeperHosts = configurationUtil.get("kumuluzee.config.zookeeper.hosts").orElse(null);
        if (zookeeperHosts != null && !zookeeperHosts.isEmpty()) {
            
            verifyHosts(zookeeperHosts);
            
            try {
                this.zooKeeper = new ZooKeeper(zookeeperHosts, 2000, watchedEvent -> {
                    if (watchedEvent.getState() == Watcher.Event.KeeperState.SyncConnected) {
                        connectionSignal.countDown();
                    }
                });
                connectionSignal.await();
            } catch (InterruptedException | IOException e) {
                log.severe("Error initializing Zookeeper! Host is unreacheable.");
            }
        } else {
            log.severe("No Zookeeper server hosts provided. Specify hosts with configuration key" +
                "kumuluzee.config.zookeeper.hosts in format " +
                "192.168.99.100:2181,192.168.99.101:2182,192.168.99.102:2183");
        }
    }
    
    @Override
    public Optional<String> get(String key) {
        
        key = "/" + namespace + parseKeyNameForZookeeper(key);
        
        try {
            byte[] bytes = zooKeeper.getData(key, null, null);
            if (bytes != null) {
                return Optional.of(new String(bytes));
            } else {
                return Optional.empty();
            }
        } catch (KeeperException.NoNodeException e) {
            return Optional.empty();
        } catch (KeeperException | InterruptedException e) {
            log.log(Level.SEVERE, "Error retrieving key {0}!", key);
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
        key = "/" + namespace + parseKeyNameForZookeeper(key);
        
        try {
            List<String> children = zooKeeper.getChildren(key, false);
            return Optional.of(children.size());
        } catch (KeeperException.NoNodeException e) {
            return Optional.empty();
        } catch (KeeperException | InterruptedException e) {
            log.log(Level.SEVERE, "Error retrieving key {0}!", key);
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<List<String>> getMapKeys(String key) {
        key = "/" + namespace + parseKeyNameForZookeeper(key);
        
        try {
            List<String> children = zooKeeper.getChildren(key, false);
            return Optional.of(children);
        } catch (KeeperException.NoNodeException e) {
            return Optional.empty();
        } catch (KeeperException | InterruptedException e) {
            log.log(Level.SEVERE, "Error retrieving key {0}!", key);
            return Optional.empty();
        }
    }
    
    @Override
    public void watch(String key) {
        String fullKey = "/" + namespace + parseKeyNameForZookeeper(key);
        
        if (zooKeeper != null) {
            log.log(Level.INFO, "Initializing watch for key: {0}", fullKey);
            try {
                
                String newValue = null;
                try {
                    byte[] newValueBytes = zooKeeper.getData(fullKey, watchedEvent -> {
                        if (watchedEvent.getType().equals(Watcher.Event.EventType.NodeDataChanged)) {
                            watch(key);
                        }
                    }, null);
                    newValue = new String(newValueBytes);
                } catch (KeeperException.NoNodeException ignored) {
                    // ignore non-existing nodes
                }
                
                log.log(Level.INFO, "Value changed. Key: {0} New value: {1}",
                    new String[]{parseKeyNameFromZookeeper(fullKey), newValue});
                
                if (configurationDispatcher != null) {
                    if (newValue != null) {
                        configurationDispatcher.notifyChange(
                            parseKeyNameFromZookeeper(fullKey),
                            ConfigurationDecoderUtils.decodeConfigValueIfEncoded(
                                parseKeyNameFromZookeeper(fullKey),
                                newValue
                            )
                        );
                    } else {
                        ConfigurationUtil
                            .getInstance()
                            .get(parseKeyNameFromZookeeper(fullKey))
                            .ifPresent(fallbackConfig -> configurationDispatcher.notifyChange(key, fallbackConfig));
                    }
                }
            } catch (KeeperException.NoNodeException ignored) {
                // ignore non-existing nodes
            } catch (InterruptedException e) {
                log.log(Level.SEVERE, "Transaction for watch {0} was interrupted!", key);
            } catch (KeeperException e) {
                log.log(Level.SEVERE, "Unknown Zookeeper exception. Message: {0}", e.getMessage());
            }
        }
    }
    
    @Override
    public void set(String key, String value) {
        key = "/" + namespace + parseKeyNameForZookeeper(key);
        
        if (zooKeeper != null) {
            try {
                List<String> pathParts = getNodePaths(key);
                for (int i = 0; i < pathParts.size(); i++) {
                    String part = pathParts.get(i);
                    // Recursively create path to given node
                    if (i != pathParts.size() - 1) {
                        if (zooKeeper.exists(part, false) == null) {
                            zooKeeper.create(part, null,
                                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                                CreateMode.PERSISTENT);
                        }
                    } else {
                        // if last node, then create data node
                        zooKeeper.create(part, value.getBytes(),
                            ZooDefs.Ids.OPEN_ACL_UNSAFE,
                            CreateMode.PERSISTENT);
                    }
                }
            } catch (KeeperException | InterruptedException e) {
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
    
    @Override
    public Integer getOrdinal() {
        return getInteger(CONFIG_ORDINAL).orElse(110);
    }
    
    private String parseKeyNameFromZookeeper(String key) {
        return key.substring(this.namespace.length() + 2).replace("/", ".").replace(".[", "[");
    }
    
    /**
     * Parses Kumuluzee key to Zookeeper key
     *
     * @param key dot-separated string: <code>val1.val2.val3</code>
     * @return slash-separated string: <code>/val1/val2/val3</code>
     */
    private String parseKeyNameForZookeeper(String key) {
        key = key.replace("[", ".[");
        String[] splittedKey = key.split("\\.");
        
        StringBuilder parsedKey = new StringBuilder();
        for (String s : splittedKey) {
            parsedKey.append("/").append(s);
        }
        
        return parsedKey.toString();
    }
    
    /**
     * Returns cumulative list of node paths
     *
     * @param key dot-separated string: <code>val1.val2.val3</code>
     * @return list of cumulative node paths: <code>[/val1, /val1/val2, /val1/val2/val3]</code>
     */
    private List<String> getNodePaths(String key) {
        List<String> listOfNodePaths = new ArrayList<>();
        String zookeeperKey = parseKeyNameForZookeeper(key);
        
        List<String> partsList = Arrays.stream(zookeeperKey.split("/"))
            .filter(part -> !part.isEmpty())
            .collect(Collectors.toList());
        
        for (int i = 0; i < partsList.size(); i++) {
            listOfNodePaths.add(joinKeys(partsList, i));
        }
        return listOfNodePaths;
    }
    
    private String joinKeys(List<String> parts, int end) {
        if (end >= parts.size()) {
            throw new ArrayIndexOutOfBoundsException("end index cannot be larger than array size!");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= end; i++) {
            sb.append("/").append(parts.get(i));
        }
        return sb.toString();
    }
    
    private void verifyHosts(String hosts) throws IllegalArgumentException {
        String[] hostsArray = hosts.split(",");
        
        if (hostsArray.length % 2 == 0) {
            log.warning("Using an odd number of Zookeeper hosts is recommended. See Zookeeper documentation.");
        }
        
        for (String host : hostsArray) {
            try {
                new URI(host);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid Zookeeper host '" + host + "' specified!");
            }
        }
    }
}
