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

import com.kumuluz.ee.common.ConfigExtension;
import com.kumuluz.ee.common.config.EeConfig;
import com.kumuluz.ee.common.dependencies.EeExtensionDef;
import com.kumuluz.ee.common.dependencies.EeExtensionGroup;
import com.kumuluz.ee.common.wrapper.KumuluzServerWrapper;
import com.kumuluz.ee.configuration.ConfigurationSource;
import com.kumuluz.ee.logs.LogManager;
import com.kumuluz.ee.logs.Logger;

/**
 * KumuluzEE framework extension for adding Consul configuration source in configuration util.
 *
 * @author Jan Meznarič, Urban Malc
 */
@EeExtensionDef(name = "Consul", group = EeExtensionGroup.CONFIG)
public class ConsulConfigExtension implements ConfigExtension {

    private static final Logger log = LogManager.getLogger(ConsulConfigExtension.class.getName());

    private ConfigurationSource configurationSource;

    @Override
    public void init(KumuluzServerWrapper kumuluzServerWrapper, EeConfig eeConfig) {
        log.info("Initialising Consul configuration source.");
        configurationSource = new ConsulConfigurationSource(eeConfig);
    }

    @Override
    public void load() {
    }

    @Override
    public ConfigurationSource getConfigurationSource() {
        return configurationSource;
    }
}
