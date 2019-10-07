# KumuluzEE Config
[![Build Status](https://img.shields.io/travis/kumuluz/kumuluzee-config/master.svg?style=flat)](https://travis-ci.org/kumuluz/kumuluzee-config)

> Configuration extension for the KumuluzEE microservice framework with support for etcd and Consul configuration 
servers.

KumuluzEE Config is an open-source configuration management project for the KumuluzEE framework. It extends basic 
configuration framework which is a part of KumuluzEE framework, described 
[here](https://github.com/kumuluz/kumuluzee/wiki/Configuration). It provides support for additional configuration 
sources in addition to environment variables, system properties and configuration files. 

KumuluzEE Config follows the idea of a unified configuration API for the framework and provides additional
configuration sources which can be utilised with a standard KumuluzEE configuration interface. 

KumuluzEE Config has been designed to support modularity with pluggable configuration sources. Currently, etcd and 
Consul key-value stores are supported to act as configuration servers. In the future, other data stores and 
configuration servers will be supported too (contributions are welcome).

Project supports KumuluzEE version 2.4.0 or higher.

## Usage
KumuluzEE defines interfaces for common configuration management features and three basic configuration sources; 
environment variables, system properties and configuration files. To include configuration sources from this project
you need to include a dependency to an implementation library. 

You can include etcd implementation by adding the following dependency:

```xml
<dependency>
   <artifactId>kumuluzee-config-etcd</artifactId>
   <groupId>com.kumuluz.ee.config</groupId>
   <version>${kumuluzee-config.version}</version>
</dependency>
```

Currently, only API v2 is supported. Future releases will support API v3 in a form of a new KumuluzEE Config module.

You can include Consul implementation by adding the following dependency:

```xml
<dependency>
   <artifactId>kumuluzee-config-consul</artifactId>
   <groupId>com.kumuluz.ee.config</groupId>
   <version>${kumuluzee-config.version}</version>
</dependency>
```

Note that currently, only one configuration server implementation (etcd or Consul) can be added to a single project.
Adding both of them may result in unexpected behaviour.

**Configuring etcd**

To connect to an etcd cluster, an odd number of etcd hosts must be specified with configuration key `kumuluzee.config
.etcd.hosts` in format 
`'http://192.168.99.100:2379,http://192.168.99.101:2379,http://192.168.99.102:2379'`.

Etcd can be configured to support user authentication and client-to-server transport security with HTTPS. To access 
authentication-enabled etcd host, username and password have to be defined with configuration keys 
`kumuluzee.config.etcd.username` and `kumuluzee.config.etcd.password`. To enable transport security, follow 
https://coreos.com/etcd/docs/latest/op-guide/security.html To access HTTPS-enabled etcd host, PEM certificate string
have to be defined with configuration key `kumuluzee.config.etcd.ca`.

Sample configuration file: 

```yaml
kumuluzee:
  config:
    start-retry-delay-ms: 500
    max-retry-delay-ms: 900000
    etcd:
      hosts: http://192.168.99.100:2379,http://192.168.99.101:2379,http://192.168.99.102:2379
      username: root
      password: admin
      ca: -----BEGIN CERTIFICATE-----
          MIIDDjCCAfagAwIBAgIUZzEIr206GOYqlxHLWtUUEu2ztvcwDQYJKoZIhvcNAQEL
          BQAwDTELMAkGA1UEAxMCQ0EwHhcNMTcwNDEwMDcyMDAwWhcNMjIwNDA5MDcyMDAw
          WjANMQswCQYDVQQDEwJDQTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEB
          AMKAeFREzc3wjOCQ8RlbnTJmD0PUls4HS6lV/xlRKbsNwqC3rxpoSp7lDoVy6MNr
          vX+7ZiyL05bkhWfF6Vzqqy6BVc6ock+nsIQyn1mXaTYDftue2z142KpjPLsj9YbP
          r2C5fmQk3rigQER95nT4gX3SleFENrnsmJU8bOt59b33uaYv6WLKUCInADITsQAN
          O8LiQ4scRwQXMFq0xORWdno9xPoRZOKMi5p+mIN0cGl9/+ComuqIcWomjKkWYK58
          Qhsy9jSaFYo6INMKLAjnmu5qY2Z7Hpf6iaVjgCayO8IXBWegspCTtZWZKOCpbO4A
          w3iH1eCz6VaG3F9FC1yWlh0CAwEAdaNmMGQwDgYDVR0PAQH/BAQDAgEGMBIGA1Ud
          EwEB/wQIMAYBAf8CAQIwHQYDeoklfBYEFBG6m7kZljsfFK2MTnQ5RWdM+mnDMB8G
          A1UdIwQYMBaAFBG6m7kZljsfFK2MTnQ5RWdM+mnDMA0GCSqGSIb3DQEBCwUAA4IB
          AQAT3tRmXGqt8Uh3Va0+Rlm4MDzcFsD7aO77tJuELCDC4cOCeROCEtYNJGm33MFe
          buxwaZ+zAneg5a1DtDkdjMZ6N+CVkMBTDWWm8cuo6Dm3HKWr+Rtd6Z8LwOq/X40C
          CHyowEYlYZSAof9rOHwn0rt8zgUSmZV6z9PXwFajwE2nEU7wlglYXtuLqBNzUYeN
          wYNnVFjMYtsWKgi/3nCegXastYGqoDpnAT25CsExrRuxAQw5i5WJU5RJwNsOPod5
          6X2Iz/EV5flbWti5OcoxLr3pfaCueLa71E+mPDKlWB55BXdNyHyS248msZC7UD2I
          Opyz239QjRq2HRMl+i7C0e6O
          -----END CERTIFICATE-----
```

**Configuring Consul**

By default, KumuluzEE Config Consul automatically connects to the local agent at http://localhost:8500. This behaviour 
can be overridden by specifying agent URL with configuration key `kumuluzee.config.consul.agent`.

**Configuration source priorities**

Included source acts as any other configuration source. It has the third highest priority, which means that properties 
from etcd override properties from configuration files and can be overwritten with properties from environmental 
variables and system properties.

**Configuration properties inside etcd**

Configuration properties are stored in etcd key/value store. 

Key names are automatically parsed from KumuluzEE to etcd format (e.g. `environments.dev.name` -> 
`environments/dev/name`).

Configuration properties are in etcd stored in a dedicated namespace, which is automatically generated from 
configuration keys `kumuluzee.env.name`, `kumuluzee.name` and `kumuluzee.version`. Example: `kumuluzee.env.name: dev`,
`kumuluzee.name: customer-service`, `kumuluzee.version: 1.2.3` is
mapped to namespace `environments/dev/services/customer-service/1.2.3/config`. If `kumuluzee.env.name` or
`kumuluzee.version` keys are not specified, defaults are used (`dev` and `1.0.0`). If `kumuluzee.name` is not
specified, namespace `environments/<environment>/services/config` is used. Automatic namespace generation can be
overwritten with key `kumuluzee.config.namespace`. Example:
`kumuluzee.config.namespace: environments/dev/services/config`.

Namespace is used as a first part of the key used for etcd key/value store. Example: with set `kumuluzee.env.name: dev`, 
field `port` from example bellow is in etcd stored in key `/environments/dev/services/test-service/config/port`.

Lists can be stored in etcd in dedicated directories with key names as indexes [0], [1], [2], ...

The following list in yaml

```yml
sample-list:
   - first
   - second
   - third 
```

can be stored with the following etcd keys:

```
environments/dev/services/customer-service/1.2.3/config/sample-list/[0]=first
environments/dev/services/customer-service/1.2.3/config/sample-list/[1]=second
environments/dev/services/customer-service/1.2.3/config/sample-list/[2]=third
```

**Configuration properties inside Consul**

Configuration properties in Consul are stored in a similar way as in etcd.

Since Consul uses the same format as etcd, key names are parsed in similar fashion.

KumuluzEE Config Consul also stores keys in automatically generated dedicated namespaces.

For more details, see section above.

**Retrieving configuration properties**

Configuration can be retrieved the same way as in basic configuration framework. 

Configuration properties can be accessed with `ConfigurationUtil` class. Example:

```java
String keyValue = ConfigurationUtil.getInstance().get("key-name");
```

Configuration properties can be injected into a CDI bean with annotations `@ConfigBundle` and `@ConfigValue`. Example:

```java
@ApplicationScoped
@ConfigBundle("test-service.config")
public class ConfigPropertiesExample {
    
    @ConfigValue("port")
    private Boolean servicePort;
    
    // getter and setter methods
}
```

**Watches**

Since configuration properties in etcd and Consul can be updated during microservice runtime, they have to be
dynamically updated inside the running microservices. This behaviour can be enabled with watches.

Watches can be enabled with annotation parameter `@ConfigValue(watch = true)` or by subscribing to key changes.

If watch is enabled on a field, its value will be dynamically updated on any change in configuration source, as long 
as new value is of a proper type. For example, if value in configuration store, linked to an integer field, is changed 
to a non-integer value, field value will not be updated. Example of enabling watch with annotation:

```java
@ApplicationScoped
@ConfigBundle("test-service.config")
public class ConfigPropertiesExample {
    
    @ConfigValue(watch = true)
    private Boolean servicePort;
    
    // getter and setter methods
}
```

Subscribing to key changes is done with an instance of `ConfigurationUtil` class. Example:

```java
String watchedKey = "maintenance";

ConfigurationUtil.getInstance().subscribe(watchedKey, (String key, String value) -> {

    if (watchedKey.equals(key)) {

        if ("true".equals(value.toLowerCase())) {
            log.info("Maintenence mode enabled.");
        } else {
            log.info("Maintenence mode disabled.");
        }

    }

});
```

If the key is not present in configuration server, a value from other configuration sources is returned. Similarly, if
the key is deleted from configuration server, a value from other configuration sources is returned.

**Retry delays**

Etcd and Consul implementations support retry delays on watch connection errors. Since they use increasing exponential
delay, two parameters need to be specified:

- `kumuluzee.config.start-retry-delay-ms`, which sets the retry delay duration in ms on first error - default: 500
- `kumuluzee.config.max-retry-delay-ms`, which sets the maximum delay duration in ms on consecutive errors -
default: 900000 (15 min)


**Build the microservice**

Ensure you have JDK 8 (or newer), Maven 3.2.1 (or newer) and Git installed.
    
Build the config library with command:

```bash
    mvn install
```
    
Build archives are located in the modules respected folder `target` and local repository `.m2`.

**Run the microservice**

Use the following command to run the sample from Windows CMD:
```
java -cp target/classes;target/dependency/* com.kumuluz.ee.EeApplication 
```

## Changelog

Recent changes can be viewed on Github on the [Releases Page](https://github.com/kumuluz/kumuluzee-config/releases)

## Contribute

See the [contributing docs](https://github.com/kumuluz/kumuluzee-config/blob/master/CONTRIBUTING.md)

When submitting an issue, please follow the 
[guidelines](https://github.com/kumuluz/kumuluzee-config/blob/master/CONTRIBUTING.md#bugs).

When submitting a bugfix, write a test that exposes the bug and fails before applying your fix. Submit the test 
alongside the fix.

When submitting a new feature, add tests that cover the feature.

## License

MIT
