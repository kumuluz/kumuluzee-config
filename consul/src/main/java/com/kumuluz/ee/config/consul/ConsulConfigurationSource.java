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
package com.kumuluz.ee.config.consul;

import com.kumuluz.ee.common.config.EeConfig;
import com.kumuluz.ee.config.utils.InitializationUtils;
import com.kumuluz.ee.config.utils.ParseUtils;
import com.kumuluz.ee.configuration.ConfigurationSource;
import com.kumuluz.ee.configuration.utils.ConfigurationDispatcher;
import com.kumuluz.ee.configuration.utils.ConfigurationUtil;
import com.orbitz.consul.Consul;
import com.orbitz.consul.ConsulException;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.async.ConsulResponseCallback;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.kv.Value;
import com.orbitz.consul.option.QueryOptions;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Util class for getting and setting configuration properties for Consul Key-Value store.
 *
 * @author Urban Malc
 * @author Jan Meznarič
 * @since 1.0.0
 */
public class ConsulConfigurationSource implements ConfigurationSource {

    private static final Logger log = Logger.getLogger(ConsulConfigurationSource.class.getName());

    // Specifies wait parameter, passed to Consul when initializing watches.
    // Consul ends connection (sends current state), when wait time is reached.
    // After that, the watch is reestablished.
    private static final int CONSUL_WATCH_WAIT_SECONDS = 120;

    private ConfigurationDispatcher configurationDispatcher;

    private Consul consul;
    private KeyValueClient kvClient;

    private String namespace;

    private int startRetryDelay;
    private int maxRetryDelay;

    private EeConfig eeConfig;

    public ConsulConfigurationSource(EeConfig eeConfig) {
        this.eeConfig = eeConfig;
    }


    @Override
    public void init(ConfigurationDispatcher configurationDispatcher) {

        ConfigurationUtil configurationUtil = ConfigurationUtil.getInstance();

        this.namespace = InitializationUtils.getNamespace(this.eeConfig, configurationUtil, "consul");
        log.info("Using namespace: " + this.namespace);

        // get retry delays
        startRetryDelay = InitializationUtils.getStartRetryDelayMs(configurationUtil, "consul");
        maxRetryDelay = InitializationUtils.getMaxRetryDelayMs(configurationUtil, "consul");

        this.configurationDispatcher = configurationDispatcher;

        URL consulAgentUrl = null;
        try {
            consulAgentUrl = new URL(configurationUtil.get("kumuluzee.config.consul.agent").orElse
                    ("http://localhost:8500"));
        } catch (MalformedURLException e) {
            log.warning("Provided Consul Agent URL is not valid. Defaulting to http://localhost:8500");
            try {
                consulAgentUrl = new URL("http://localhost:8500");
            } catch (MalformedURLException e1) {
                e1.printStackTrace();
            }
        }
        log.info("Connecting to Consul Agent at: " + consulAgentUrl.toString());

        // withReadTimeoutMillis: Sets read timeout on underlying library (okhttp).
        // timeout is calculated by using Consul formula for maximum waiting time with added time (1s) for connection
        // delays. For formula and more details, see: https://www.consul.io/api/index.html#blocking-queries
        consul = Consul.builder()
                .withUrl(consulAgentUrl).withPing(false)
                .withReadTimeoutMillis(CONSUL_WATCH_WAIT_SECONDS * 1000 + (CONSUL_WATCH_WAIT_SECONDS * 1000) / 16 +
                        1000)
                .build();

        boolean pingSuccessful = false;
        try {
            consul.agentClient().ping();
            pingSuccessful = true;
        } catch (ConsulException e) {
            log.severe("Cannot ping Consul agent: " + e.getLocalizedMessage());
        }

        kvClient = consul.keyValueClient();

        if (pingSuccessful) {
            log.info("Consul configuration source successfully initialised.");
        } else {
            log.warning("Consul configuration source initialized, but Consul agent inaccessible. " +
                    "Configuration source may not work as expected.");
        }
    }

    @Override
    public Optional<String> get(@Nonnull String key) {

        key = this.namespace + "/" + parseKeyNameForConsul(key);

        Optional<String> value = Optional.empty();

        try {
            value = kvClient.getValueAsString(key);
        } catch (ConsulException e) {
            log.severe("Consul exception: " + e.getLocalizedMessage());
        }

        return value;

    }

    @Override
    public Optional<Boolean> getBoolean(@Nonnull String key) {
        return ParseUtils.parseOptionalStringToOptionalBoolean(get(key));
    }

    @Override
    public Optional<Integer> getInteger(@Nonnull String key) {
        return ParseUtils.parseOptionalStringToOptionalInteger(get(key));
    }

    @Override
    public Optional<Long> getLong(@Nonnull String key) {
        return ParseUtils.parseOptionalStringToOptionalLong(get(key));
    }

    @Override
    public Optional<Double> getDouble(@Nonnull String key) {
        return ParseUtils.parseOptionalStringToOptionalDouble(get(key));
    }

    @Override
    public Optional<Float> getFloat(@Nonnull String key) {
        return ParseUtils.parseOptionalStringToOptionalFloat(get(key));
    }

    @Override
    public Optional<Integer> getListSize(String key) {

        // get directory
        key = namespace + "/" + parseKeyNameForConsul(key);
        List<Value> values = null;

        try {
            values = kvClient.getValues(key);
        } catch (ConsulException e) {
            log.severe("Consul exception: " + e.getLocalizedMessage());
        }

        if (values != null && !values.isEmpty()) {
            // get array indexes
            List<Integer> arrayIndexes = new ArrayList<>();
            for (Value v : values) {
                String nodeKey = v.getKey();
                try {
                    arrayIndexes.add(Integer.parseInt(nodeKey.substring(key.length() + 2, nodeKey.length() - 1)));
                } catch (NumberFormatException e) {
                }

            }
            Collections.sort(arrayIndexes);

            // check if indexes represent an array (are continuous)
            int listSize = 0;
            for (Integer i : arrayIndexes) {
                if (i == listSize) {
                    listSize += 1;
                } else {
                    break;
                }
            }

            if (listSize > 0) {
                return Optional.of(listSize);
            }
        }


        return Optional.empty();

    }

    @Override
    public Optional<List<String>> getMapKeys(String key) {

        key = this.namespace + "/" + parseKeyNameForConsul(key);

        Set<String> mapKeys = new HashSet();

        try {
            for (String mapKey : kvClient.getKeys(key)) {
                String[] splittedKey = mapKey.split("/");
                mapKeys.add(splittedKey[splittedKey.length - 1]);
            }
        } catch (ConsulException e) {
            log.severe("Consul exception: " + e.getLocalizedMessage());
        }

        if (mapKeys != null && !mapKeys.isEmpty()) {
            return Optional.of(new ArrayList<>(mapKeys));
        }

        return Optional.empty();
    }

    @Override
    public void watch(String key) {

        String fullKey = this.namespace + "/" + parseKeyNameForConsul(key);

        log.info("Initializing watch for key: " + fullKey);

        ConsulResponseCallback<List<Value>> callback = new ConsulResponseCallback<List<Value>>() {

            AtomicReference<BigInteger> index = new AtomicReference<>(new BigInteger("0"));

            int currentRetryDelay = startRetryDelay;

            // If value we're watching is not present in Consul, onComplete fires on every change in KV store.
            // This is used, so we only notify once, if key was deleted.
            boolean previouslyDeleted = false;

            @Override
            public void onComplete(ConsulResponse<List<Value>> consulResponse) {
                // successful request, reset delay
                currentRetryDelay = startRetryDelay;

                if (index.get() != null && !index.get().equals(consulResponse.getIndex())) {
                    if (consulResponse.getResponse() != null && !consulResponse.getResponse().isEmpty()) {

                        for (Value v : consulResponse.getResponse()) {

                            Optional<String> valueOpt = v.getValueAsString();
                            String newKey = v.getKey();

                            if (valueOpt.isPresent() && configurationDispatcher != null) {
                                log.info("Consul watch callback for key " + parseKeyNameFromConsul(newKey) +
                                        " invoked. " + "New value: " + valueOpt.get());
                                configurationDispatcher.notifyChange(parseKeyNameFromConsul(newKey), valueOpt.get());
                                previouslyDeleted = false;
                            } else {
                                log.info("Consul watch callback for key " + parseKeyNameFromConsul(newKey) +
                                        " invoked. No value present, fallback to other configuration sources.");
                                ConfigurationUtil configurationUtil = ConfigurationUtil.getInstance();
                                String fallbackConfig = configurationUtil.get(parseKeyNameFromConsul(newKey)).orElse
                                        (null);
                                if (fallbackConfig != null) {
                                    configurationDispatcher.notifyChange(parseKeyNameFromConsul(newKey),
                                            fallbackConfig);
                                }
                            }
                        }
                    } else if (!previouslyDeleted) {
                        log.info("Consul watch callback for key " + fullKey +
                                " invoked. No value present, fallback to other configuration sources.");
                        ConfigurationUtil configurationUtil = ConfigurationUtil.getInstance();
                        String fallbackConfig = configurationUtil.get(key).orElse(null);
                        if (fallbackConfig != null) {
                            configurationDispatcher.notifyChange(key, fallbackConfig);
                        }
                        previouslyDeleted = true;
                    }
                }

                index.set(consulResponse.getIndex());

                watch();
            }

            void watch() {
                kvClient.getValues(fullKey, QueryOptions.blockSeconds(CONSUL_WATCH_WAIT_SECONDS, index.get()).build(),
                        this);
            }

            @Override
            public void onFailure(Throwable throwable) {
                if (throwable instanceof ConnectException) {
                    try {
                        Thread.sleep(currentRetryDelay);
                    } catch (InterruptedException ignored) {
                    }

                    // exponential increase, limited by maxRetryDelay
                    currentRetryDelay *= 2;
                    if (currentRetryDelay > maxRetryDelay) {
                        currentRetryDelay = maxRetryDelay;
                    }
                } else {
                    log.severe("Watch error: " + throwable.getLocalizedMessage());
                }

                watch();
            }
        };

        kvClient.getValues(fullKey, QueryOptions.blockSeconds(CONSUL_WATCH_WAIT_SECONDS, new BigInteger("0")).build(),
                callback);


    }

    @Override
    public void set(@Nonnull String key, @Nonnull String value) {
        kvClient.putValue(parseKeyNameForConsul(key), value);
    }

    @Override
    public void set(@Nonnull String key, @Nonnull Boolean value) {
        set(key, value.toString());
    }

    @Override
    public void set(@Nonnull String key, @Nonnull Integer value) {
        set(key, value.toString());
    }

    @Override
    public void set(@Nonnull String key, @Nonnull Double value) {
        set(key, value.toString());
    }

    @Override
    public void set(@Nonnull String key, @Nonnull Float value) {
        set(key, value.toString());
    }

    @Override
    public Integer getOrdinal() {
        return getInteger(CONFIG_ORDINAL).orElse(110);
    }

    private String parseKeyNameFromConsul(String key) {
        return key.substring(this.namespace.length() + 1).replace("/", ".").replace(".[", "[");
    }

    private String parseKeyNameForConsul(String key) {

        return key.replace("[", ".[").replace(".", "/");

    }
}
