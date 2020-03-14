package com.kumuluz.ee.config.zookeeper;

import com.kumuluz.ee.common.ConfigExtension;
import com.kumuluz.ee.common.config.EeConfig;
import com.kumuluz.ee.common.dependencies.EeExtensionDef;
import com.kumuluz.ee.common.dependencies.EeExtensionGroup;
import com.kumuluz.ee.common.wrapper.KumuluzServerWrapper;
import com.kumuluz.ee.configuration.ConfigurationSource;

import java.util.logging.Logger;

/**
 * KumuluzEE framework extension for adding Zookeeper configuration source in configuration util.
 *
 * @author Miha Jamsek
 * @since 1.3.0
 */
@EeExtensionDef(name = "Zookeeper", group = EeExtensionGroup.CONFIG)
public class ZookeeperConfigExtension implements ConfigExtension {
    
    private static final Logger log = Logger.getLogger(ZookeeperConfigExtension.class.getName());
    
    private ConfigurationSource configurationSource;
    
    @Override
    public ConfigurationSource getConfigurationSource() {
        return configurationSource;
    }
    
    @Override
    public void load() {
    
    }
    
    @Override
    public void init(KumuluzServerWrapper kumuluzServerWrapper, EeConfig eeConfig) {
        log.info("Initializing Zookeeper configuration source.");
        configurationSource = new ZookeeperConfigurationSource(eeConfig);
    }
}
