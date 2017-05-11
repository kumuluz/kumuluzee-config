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

package com.kumuluz.ee.config;

import com.kumuluz.ee.common.Extension;
import com.kumuluz.ee.common.config.EeConfig;
import com.kumuluz.ee.common.dependencies.EeExtensionDef;
import com.kumuluz.ee.common.dependencies.EeExtensionType;
import com.kumuluz.ee.common.wrapper.KumuluzServerWrapper;
import com.kumuluz.ee.configuration.ConfigurationSource;

import java.util.List;
import java.util.Optional;

/**
 * KumuluzEE framework extension for adding etcd configuration source in configuration util.
 *
 * @author Jan Meznarič
 */
@EeExtensionDef(name = "etcd configuration source for API v2", type = EeExtensionType.CONFIG)
public class Etcd2ConfigExtension implements Extension {

    ConfigurationSource configurationSource;

    @Override
    public void init(KumuluzServerWrapper kumuluzServerWrapper, EeConfig eeConfig) {

        configurationSource = Etcd2ConfigurationSource.getInstance();

    }

    @Override
    public void load() {

    }

    @Override
    public <T> Optional<T> getProperty(Class<T> aClass) {

        if (aClass.equals(ConfigurationSource.class) && configurationSource != null) {
            return Optional.of((T) configurationSource);
        }

        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getProperty(Class<T> aClass, String s) {
        return null;
    }

    @Override
    public <T> Optional<List<T>> getProperties(Class<T> aClass) {
        return null;
    }

    @Override
    public <T> Optional<List<T>> getProperties(Class<T> aClass, String s) {
        return null;
    }
}
